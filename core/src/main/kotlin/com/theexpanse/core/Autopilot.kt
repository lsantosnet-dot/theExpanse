package com.theexpanse.core

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sqrt

enum class AutopilotMode(val label: String) {
    OFF("MANUAL"),
    INTERCEPT("INTERCEPTAR"),
    PROGRADE("PRÓ"),
    RETROGRADE("RETRO"),
    STOP_SPIN("PARAR GIRO"),
    VELOCITY("VELOCIDADE"),
}

/** Comando do piloto automático: rumo desejado (null = manter) e aceleração do motor. */
class AutopilotCommand(val desiredAngle: Double?, val burn: Double)

object Autopilot {
    /** Só avisa de colisões previstas para as próximas horas de jogo: mais longe que isso a reta já não é confiável. */
    const val WARNING_HORIZON = 12 * 3600.0

    /** Aceleração angular que leva o rumo ao desejado com giro suave. */
    fun attitudeAlpha(angle: Double, omega: Double, desired: Double): Double {
        val e = wrapAngle(desired - angle)
        val wDes = (sign(e) * sqrt(2 * Config.ALPHA * 0.6 * abs(e))).coerceIn(-Config.OMEGA_MAX, Config.OMEGA_MAX)
        return ((wDes - omega) * 4).coerceIn(-Config.ALPHA, Config.ALPHA)
    }

    /** Alpha para zerar o giro. */
    fun spinDownAlpha(omega: Double): Double = ((0.0 - omega) * 4).coerceIn(-Config.ALPHA, Config.ALPHA)

    /**
     * Aceleração desejada por orientação de ponto de encontro: chega a uma velocidade de aproximação que ainda
     * permite frear em 85% de [aLim], parando a [stopDist] do alvo, e compensa a gravidade diferencial.
     */
    fun rendezvous(world: World, t: Double, pos: Vec2, vel: Vec2, tPos: Vec2, tVel: Vec2, aLim: Double, stopDist: Double, vCap: Double): Vec2? {
        val rel = tPos - pos
        val d = rel.length
        if (d < 1.0) return null
        val u = rel / d
        val vRel = vel - tVel
        val vDes = min(sqrt(2 * 0.85 * aLim * max(0.0, d - stopDist)) * 0.9, vCap)
        return (u * vDes - vRel) * 0.6 - (world.gravity(pos, t) - world.gravity(tPos, t))
    }

    /** Um corpo que destrói a nave se ela encostar: o planeta central ou um corpo sólido. */
    class Obstacle(val name: String, val pos: Vec2, val vel: Vec2, val radius: Double, val gm: Double)

    fun obstacles(world: World, t: Double): List<Obstacle> {
        val list = ArrayList<Obstacle>()
        list.add(Obstacle(world.planetName, Vec2.ZERO, Vec2.ZERO, world.radius, world.gm))
        for (b in world.solid) list.add(Obstacle(b.name, b.position(t), b.velocity(t), b.def.radius, b.def.gm))
        return list
    }

    /** Uma passagem perigosa: o corpo, o sentido tangencial para ganhar momento angular e a aceleração lateral que falta. */
    class Threat(val obstacle: Obstacle, val direction: Vec2, val accelNeeded: Double)

    /**
     * Confere, com a trajetória atual (energia e momento angular conservados), se a nave vai raspar ou bater em algum
     * corpo sólido. O periápsis mínimo é o raio do corpo + 20% + 100 km. Devolve a manobra lateral que falta: empurrar
     * perpendicularmente à linha até o corpo aumenta o momento angular até a passagem ficar segura.
     */
    fun threat(world: World, t: Double, pos: Vec2, vel: Vec2, tPos: Vec2): Threat? {
        var worst: Threat? = null
        for (o in obstacles(world, t)) {
            val dT = (tPos - o.pos).length
            if (dT < o.radius * 4.0) continue // o alvo orbita perto do corpo: a frenagem da aproximação cuida disso
            val r = pos - o.pos
            val dist = r.length
            val vr = vel - o.vel
            val speed = vr.length
            val rp = o.radius * 1.2 + 100_000.0
            if (speed < 1.0 || dist <= rp || (r dot vr) >= 0.0) continue
            val cross = r.x * vr.y - r.y * vr.x
            val vPeri2 = speed * speed + if (o.gm > 0.0) 2 * o.gm * (1.0 / rp - 1.0 / dist) else 0.0
            val lReq = rp * sqrt(vPeri2)
            if (abs(cross) >= lReq) continue
            val vRadial = -(r dot vr) / dist
            val vFall = sqrt(vRadial * vRadial + (vPeri2 - speed * speed).coerceAtLeast(0.0))
            val tc = max(1.0, (dist - rp) / max(1.0, 0.5 * (vRadial + vFall)))
            val dvNeeded = (lReq - abs(cross)) / dist
            val tangent = Vec2(-r.y, r.x) / dist
            val dir = if (cross >= 0.0) tangent else -tangent
            val aReq = dvNeeded * 1.3 / tc
            if (worst == null || aReq > worst.accelNeeded) worst = Threat(o, dir, aReq)
        }
        return worst
    }

    /** Aviso de colisão na trajetória atual (reta, com a curvatura da gravidade): o corpo e o tempo até o impacto. */
    fun collisionCourse(world: World, t: Double, pos: Vec2, vel: Vec2): Pair<Obstacle, Double>? {
        var best: Pair<Obstacle, Double>? = null
        for (o in obstacles(world, t)) {
            val r = pos - o.pos
            val vr = vel - o.vel
            val speed = vr.length
            if (speed < 1.0 || (r dot vr) >= 0.0) continue
            // momento angular × momento angular mínimo para raspar a superfície (conservação de energia e de momento angular)
            val l = abs(r.x * vr.y - r.y * vr.x)
            val vSurf2 = speed * speed + if (o.gm > 0.0) 2 * o.gm * (1.0 / o.radius - 1.0 / r.length) else 0.0
            if (r.length <= o.radius || l >= o.radius * sqrt(vSurf2)) continue
            val eta = (r.length - o.radius) / speed
            if (eta > WARNING_HORIZON) continue
            if (best == null || eta < best.second) best = Pair(o, eta)
        }
        return best
    }

    /**
     * Piloto de velocidade: leva a velocidade relativa [vRel] (nave menos referência) até [desired]. Pede a aceleração
     * que fecha a diferença em [Config.VELOCITY_TAU] segundos e cancela a gravidade diferencial [gravDiff] (a da nave
     * menos a da referência), gira para ela e só queima quando já está apontando para lá.
     */
    fun velocity(ship: Ship, vRel: Vec2, desired: Vec2, aLim: Double, gravDiff: Vec2, tau: Double = Config.VELOCITY_TAU): AutopilotCommand {
        val a = (desired - vRel) / tau - gravDiff
        val mag = a.length
        if (mag < Config.VELOCITY_DEADBAND) return AutopilotCommand(null, 0.0)
        val err = abs(wrapAngle(a.angle - ship.angle))
        val f = ((0.45 - err) / 0.35).coerceIn(0.0, 1.0)
        return AutopilotCommand(a.angle, min(aLim, mag) * f)
    }

    /** Interceptação da nave do jogador, limitada pelo Δv que sobra para não gastar mais combustível do que existe. */
    fun intercept(world: World, t: Double, ship: Ship, targetPos: Vec2, targetVel: Vec2, thrustPct: Int, stopDist: Double = 3000.0): AutopilotCommand {
        val aLim = ship.maxAccel * thrustPct / 100.0
        val vRel = ship.vel - targetVel
        val vCap = max(1500.0, 0.42 * (ship.deltaV + vRel.length))
        // saindo da superfície (ou de perto dela): sobe na vertical até passar da zona do corpo, para não mergulhar no chão
        climbOut(world, t, ship, targetPos, aLim)?.let { return it }
        // desvio de corpos sólidos: tem prioridade sobre a interceptação, e some assim que a passagem fica segura
        // (só age quando a aceleração lateral necessária já é uma fração relevante da do motor: corpos distantes ficam de fora)
        val danger = threat(world, t, ship.pos, ship.vel, targetPos)?.takeIf { it.accelNeeded >= 0.002 * aLim }
        if (danger != null) {
            val err = abs(wrapAngle(danger.direction.angle - ship.angle))
            val f = ((0.45 - err) / 0.35).coerceIn(0.0, 1.0)
            val a = min(aLim, max(0.3 * aLim, 1.5 * danger.accelNeeded))
            return AutopilotCommand(danger.direction.angle, a * f)
        }
        val aCmd = rendezvous(world, t, ship.pos, ship.vel, targetPos, targetVel, aLim, stopDist, vCap) ?: return AutopilotCommand(null, 0.0)
        val mag = aCmd.length
        if (mag <= 0.03) return AutopilotCommand(null, 0.0)
        val desired = aCmd.angle
        val err = abs(wrapAngle(desired - ship.angle))
        val f = ((0.45 - err) / 0.35).coerceIn(0.0, 1.0)
        return AutopilotCommand(desired, min(mag, aLim) * f)
    }

    /** Altitude (acima da superfície) a partir da qual a nave já está fora da zona do corpo e pode tomar a rota. */
    fun clearAltitude(radius: Double): Double = radius * 0.2 + 100_000.0

    /**
     * Subida vertical: se a nave está baixo sobre um corpo sólido que não é o destino, o piloto sobe a uma velocidade
     * que cresce com a altitude, anulando a velocidade lateral. Devolve null quando não há o que subir.
     */
    fun climbOut(world: World, t: Double, ship: Ship, targetPos: Vec2, aLim: Double): AutopilotCommand? {
        for (b in world.solid) {
            if (!b.landable) continue
            val radius = b.def.radius
            val bp = b.position(t)
            if ((targetPos - bp).length < radius * 4.0) continue // o destino orbita perto do corpo: a aproximação cuida disso
            val r = ship.pos - bp
            val dist = max(r.length, 1.0)
            val alt = dist - radius
            if (alt >= clearAltitude(radius)) continue
            val up = r / dist
            val gLocal = world.gravity(ship.pos, t) - world.gravity(bp, t)
            val pull = max(0.0, -(gLocal dot up))
            val vUp = min(1500.0, 60.0 + 0.8 * sqrt(2 * max(aLim - pull, 0.1) * max(alt, 0.0)))
            return velocity(ship, ship.vel - b.velocity(t), up * vUp, aLim, gLocal, 0.5)
        }
        return null
    }

    /** Altitude do portão: a partir dela o piloto deixa a rota e começa a descida vertical. */
    fun landingGate(b: Body): Double = max(20_000.0, 0.03 * b.def.radius)

    /**
     * Pouso assistido num corpo sólido. Longe: segue a rota de interceptação até pairar a ~1,4 portões de altitude.
     * Perto: desce na vertical, anulando a velocidade lateral e seguindo um perfil de frenagem que usa 45% da
     * aceleração que sobra depois de vencer a gravidade local, até tocar a 2 m/s. Sem empuxo para vencer a gravidade
     * e com atmosfera, deixa o arrasto (paraquedas) fazer o trabalho.
     */
    fun land(world: World, t: Double, ship: Ship, b: Body, thrustPct: Int): AutopilotCommand {
        val aLim = ship.maxAccel * thrustPct / 100.0
        val bp = b.position(t)
        val bv = b.velocity(t)
        val radius = b.def.radius
        val r = ship.pos - bp
        val dist = max(r.length, 1.0)
        val alt = dist - radius
        val gate = landingGate(b)
        if (alt > gate * 1.5) return intercept(world, t, ship, bp, bv, thrustPct, radius + gate * 1.4)
        val up = r / dist
        val gLocal = world.gravity(ship.pos, t) - world.gravity(bp, t)
        val pull = max(0.0, -(gLocal dot up))
        val budget = aLim - pull
        if (budget <= 0.1 && b.def.atmoScale > 0.0) return AutopilotCommand((-(ship.vel - bv)).takeIf { it.length > 1.0 }?.angle, 0.0)
        val vDes = max(1.5, sqrt(2 * 0.45 * max(budget, 0.05) * max(alt, 0.0)))
        return velocity(ship, ship.vel - bv, up * -vDes, aLim, gLocal, 0.5)
    }
}
