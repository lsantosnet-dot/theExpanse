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

    /** Interceptação da nave do jogador, limitada pelo Δv que sobra para não gastar mais combustível do que existe. */
    fun intercept(world: World, t: Double, ship: Ship, targetPos: Vec2, targetVel: Vec2, thrustPct: Int): AutopilotCommand {
        val aLim = ship.maxAccel * thrustPct / 100.0
        val vRel = ship.vel - targetVel
        val vCap = max(1500.0, 0.42 * (ship.deltaV + vRel.length))
        // desvio de corpos sólidos: tem prioridade sobre a interceptação, e some assim que a passagem fica segura
        // (só age quando a aceleração lateral necessária já é uma fração relevante da do motor: corpos distantes ficam de fora)
        val danger = threat(world, t, ship.pos, ship.vel, targetPos)?.takeIf { it.accelNeeded >= 0.002 * aLim }
        if (danger != null) {
            val err = abs(wrapAngle(danger.direction.angle - ship.angle))
            val f = ((0.45 - err) / 0.35).coerceIn(0.0, 1.0)
            val a = min(aLim, max(0.3 * aLim, 1.5 * danger.accelNeeded))
            return AutopilotCommand(danger.direction.angle, a * f)
        }
        val aCmd = rendezvous(world, t, ship.pos, ship.vel, targetPos, targetVel, aLim, 3000.0, vCap) ?: return AutopilotCommand(null, 0.0)
        val mag = aCmd.length
        if (mag <= 0.03) return AutopilotCommand(null, 0.0)
        val desired = aCmd.angle
        val err = abs(wrapAngle(desired - ship.angle))
        val f = ((0.45 - err) / 0.35).coerceIn(0.0, 1.0)
        return AutopilotCommand(desired, min(mag, aLim) * f)
    }
}
