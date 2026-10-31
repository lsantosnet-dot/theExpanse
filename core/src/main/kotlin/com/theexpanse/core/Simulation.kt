package com.theexpanse.core

import com.theexpanse.content.AiMode
import com.theexpanse.content.BodyKind
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

enum class Status { FLYING, DOCKED, CRASHED, DESTROYED }

class Prediction(val points: List<Vec2>, val closestDistance: Double, val closestTime: Double)

/** Corpo que saiu da órbita analítica e passou a ser integrado (perseguidor, atacante ou à deriva). */
class Dynamic(var pos: Vec2, var vel: Vec2, var mode: AiMode, var route: List<String> = emptyList(), var leg: Int = 0) {
    /** Direção em que a proa aponta: para o jogador quando persegue, senão para onde voa. */
    fun heading(player: Vec2): Double = if (mode.facesPlayer || vel.length < 1e-6) (player - pos).angle else vel.angle
}

/**
 * Simulação pura: nave com inércia e gravidade, corpos em órbita analítica, perseguidores integrados, mísseis,
 * despistadores, piloto automático e tempo acelerado. Nada aqui conhece Android, tela ou relógio de parede.
 */
class Simulation(val world: World, seed: Long = 1L) {
    var time = 0.0
        private set
    val ship = Ship(world.ship.dryMass, world.ship.fuelMass, world.ship.cargoMass, world.ship.hull, world.ship.missiles, world.ship.decoys, world.ship.maxAccel, world.ship.exhaustVelocity)
    var status = Status.FLYING
        private set
    var dockedAt: Body? = null
        private set

    var targetIndex = 0
        private set
    var autopilot = AutopilotMode.OFF
        private set
    var warpIndex = 0
        private set
    var thrustPct = Config.DEFAULT_THRUST_PCT
        private set

    /** Piloto de velocidade: velocidade relativa desejada (nave menos [velocityRef]), ou null quando não há comando. */
    var desiredRelVel: Vec2? = null
        private set
    var velocityRef: Body? = null
        private set

    /** Comandos manuais (mantidos pelo controle de entrada). */
    var manualTurn = 0
    var manualEngine = false

    /** Fator de tempo realmente aplicado no último quadro. */
    var effectiveWarp = 1.0
        private set

    val dynamics = LinkedHashMap<String, Dynamic>()
    val missiles = ArrayList<Missile>()
    val flares = ArrayList<Flare>()
    val blasts = ArrayList<Blast>()
    val destroyed = HashSet<String>()
    val hidden = HashSet<String>()
    private val boarded = HashSet<String>()
    var crashedInto: String? = null
        private set
    private val arms = HashMap<String, NpcArms>()
    private val events = ArrayList<SimEvent>()
    private var nextId = 1
    private var rng = seed

    private var dockBlock: Body? = null
    private var predictionCache: Prediction? = null
    private var predictionTime = Double.NaN

    val bodies: List<Body> get() = world.bodies
    val target: Body get() = bodies[targetIndex]
    val warpLevels: DoubleArray get() = world.warpLevels

    init {
        reset()
    }

    fun seedRandom(seed: Long) { rng = seed }

    private fun rand(): Double {
        rng += -7046029254386353131L
        var z = rng
        z = (z xor (z ushr 30)) * -4658895280553007687L
        z = (z xor (z ushr 27)) * -7723592293110705685L
        z = z xor (z ushr 31)
        return (z ushr 11).toDouble() / (1L shl 53).toDouble()
    }

    fun reset() {
        time = 0.0
        status = Status.FLYING
        dockedAt = null
        dockBlock = null
        autopilot = AutopilotMode.OFF
        desiredRelVel = null
        velocityRef = null
        warpIndex = 0
        thrustPct = Config.DEFAULT_THRUST_PCT
        manualTurn = 0
        manualEngine = false
        effectiveWarp = 1.0
        dynamics.clear(); missiles.clear(); flares.clear(); blasts.clear(); destroyed.clear(); arms.clear(); events.clear(); boarded.clear()
        hidden.clear(); for (b in bodies) if (b.def.hidden) hidden.add(b.id)
        crashedInto = null
        predictionCache = null
        val setup = world.ship
        targetIndex = bodies.indexOfFirst { it.id == setup.targetId }.takeIf { it >= 0 } ?: 0
        val s = ship
        val near = world.body(setup.startNear) ?: bodies.first()
        val nearPos = near.position(0.0)
        val parentPos = near.parent?.position(0.0) ?: Vec2.ZERO
        val radial = (nearPos - parentPos).unit()
        s.pos = nearPos + radial * setup.startAltitude
        s.vel = near.velocity(0.0) // acompanha o corpo de partida
        s.angle = s.vel.angle
        s.omega = 0.0
        s.fuel = s.fuelCapacity
        s.cargo = setup.cargoMass
        s.extra = 0.0
        s.hull = s.hullMax
        s.missiles = s.missilesMax
        s.decoys = s.decoysMax
        s.burnAccel = 0.0
        s.turnAlpha = 0.0
    }

    /** Eventos acumulados desde a última chamada (atracou, caiu, foi atingido, abateu uma nave...). */
    fun drainEvents(): List<SimEvent> {
        if (events.isEmpty()) return emptyList()
        val out = ArrayList(events)
        events.clear()
        return out
    }

    // ---- posições (analíticas ou integradas) ----

    fun alive(b: Body) = b.id !in destroyed && b.id !in hidden
    fun position(b: Body): Vec2 = dynamics[b.id]?.pos ?: b.position(time)
    fun velocity(b: Body): Vec2 = dynamics[b.id]?.vel ?: b.velocity(time)

    private fun positionAt(b: Body, t: Double): Vec2 {
        val d = dynamics[b.id] ?: return b.position(t)
        return d.pos + d.vel * (t - time)
    }

    fun relativePosition(b: Body = target): Vec2 = position(b) - ship.pos
    fun relativeVelocity(b: Body = target): Vec2 = ship.vel - velocity(b)
    fun distanceTo(b: Body = target): Double = relativePosition(b).length

    /** Velocidade de aproximação (positiva = fechando). */
    fun approachSpeed(b: Body = target): Double {
        val rel = relativePosition(b)
        val d = rel.length
        if (d < 1.0) return 0.0
        return relativeVelocity(b) dot (rel / d)
    }

    val accelLimit: Double get() = ship.maxAccel * thrustPct / 100.0

    /** Distância para frear até parar em relação ao alvo, com o empuxo escolhido. */
    fun brakingDistance(b: Body = target): Double {
        val v = max(0.0, approachSpeed(b))
        return v * v / (2 * accelLimit)
    }

    /** Alerta "inverter agora": manual, aproximando rápido e já na distância de frenagem (com folga). */
    fun flipAlert(): Boolean {
        if (status != Status.FLYING || autopilot != AutopilotMode.OFF) return false
        val v = approachSpeed()
        if (v <= 50.0) return false
        return distanceTo() < 1.15 * v * v / (2 * 0.85 * accelLimit)
    }

    /** Segundos até a distância em que é preciso inverter, ou null. */
    fun secondsToFlip(): Double? {
        val v = approachSpeed()
        if (v <= 50.0) return null
        val trigger = 1.15 * v * v / (2 * 0.85 * accelLimit)
        val gap = distanceTo() - trigger
        return if (gap > 0) gap / v else 0.0
    }

    // ---- comandos ----

    fun setAutopilot(mode: AutopilotMode) { autopilot = mode }

    /** Engaja o piloto de velocidade: mantém a velocidade relativa a [ref] igual a [desired] (a nave gira e queima sozinha). */
    fun commandVelocity(desired: Vec2, ref: Body = target) {
        desiredRelVel = desired
        velocityRef = ref
        autopilot = AutopilotMode.VELOCITY
    }

    /** Solta o piloto de velocidade (a nave segue em deriva com a velocidade que tiver). */
    fun releaseVelocity() {
        if (autopilot == AutopilotMode.VELOCITY) autopilot = AutopilotMode.OFF
    }

    /** Há combate à vista: um míssil em voo (de qualquer lado) ou uma nave hostil armada por perto. */
    fun battleAlert(): Boolean {
        if (status != Status.FLYING) return false
        if (missiles.isNotEmpty()) return true
        return dynamics.any { (id, d) -> id !in destroyed && threatens(d) && (d.pos - ship.pos).length < Config.RADAR_ALERT_RANGE }
    }

    /** Posição e velocidade do alvo de um míssil (a nave, um despistador ou um corpo), ou null se já não existe. */
    fun missileTarget(m: Missile): Pair<Vec2, Vec2>? = when {
        m.targetId == "jogador" -> ship.pos to ship.vel
        m.targetId.length > 1 && m.targetId[0] == 'd' && m.targetId.drop(1).all { it.isDigit() } -> flares.firstOrNull { it.id == m.targetId }?.let { it.pos to it.vel }
        else -> world.body(m.targetId)?.takeIf { alive(it) }?.let { position(it) to velocity(it) }
    }

    /** Segundos até o míssil [m] chegar ao alvo (considera que ele ainda acelera ao longo da linha de visada), ou null. */
    fun missileEta(m: Missile): Double? {
        val (tp, tv) = missileTarget(m) ?: return null
        val rel = tp - m.pos
        val d = rel.length
        val closing = max((m.vel - tv) dot (rel / max(d, 1.0)), 0.0)
        val a = Config.MISSILE_ACCEL * 0.6
        return (-closing + sqrt(closing * closing + 2 * a * d)) / a
    }
    fun toggleAutopilot(mode: AutopilotMode) { autopilot = if (autopilot == mode) AutopilotMode.OFF else mode }
    fun setWarp(index: Int) { warpIndex = index.coerceIn(0, warpLevels.size - 1) }

    fun cycleTarget() {
        for (i in 1..bodies.size) {
            val idx = (targetIndex + i) % bodies.size
            if (alive(bodies[idx])) { targetIndex = idx; break }
        }
        predictionCache = null
    }

    fun selectTarget(index: Int) {
        targetIndex = index.coerceIn(0, bodies.size - 1)
        predictionCache = null
    }

    fun selectTarget(b: Body) = selectTarget(bodies.indexOf(b))

    /** Aviso: se nada mudar, a nave bate neste corpo, em tantos segundos de jogo. */
    fun collisionWarning(): Pair<String, Double>? =
        if (status != Status.FLYING) null else Autopilot.collisionCourse(world, time, ship.pos, ship.vel)?.let { (o, eta) -> o.name to eta }

    fun adjustThrust(deltaSteps: Int) { thrustPct = (thrustPct + 10 * deltaSteps).coerceIn(10, 100) }

    fun setThrust(pct: Int) { thrustPct = (pct / 10 * 10).coerceIn(10, 100) }

    /** Muda o comportamento de um corpo: perseguir/atacar (passa a ser integrado) ou orbitar (deriva sem empuxo). */
    fun setAi(b: Body, mode: AiMode, route: List<String> = emptyList()) {
        if (!alive(b)) return
        val d = dynamics[b.id]
        if (d == null) {
            if (mode != AiMode.ORBIT) dynamics[b.id] = Dynamic(b.position(time), b.velocity(time), mode, route)
        } else {
            d.mode = mode
            if (mode == AiMode.PATROL) { d.route = route; d.leg = 0 }
        }
    }

    /** Ronda e fuga não ameaçam o jogador: não contam como perseguição. */
    private fun threatens(d: Dynamic) = d.mode != AiMode.ORBIT && d.mode != AiMode.PATROL && d.mode != AiMode.FLEE

    /** Quem limita o tempo acelerado: ameaças e fugitivos próximos; patrulhas só quando estão perto do jogador. */
    private fun limitsWarp(d: Dynamic) = when (d.mode) {
        AiMode.ORBIT -> false
        AiMode.PATROL, AiMode.FLEE -> (d.pos - ship.pos).length < Config.AI_WARP_RADIUS
        else -> true
    }

    /** Há alguma nave perseguindo ou atacando o jogador. */
    fun isPursued(): Boolean = dynamics.any { (id, d) -> threatens(d) && id !in destroyed }

    fun isUnderAttack(): Boolean = dynamics.any { (id, d) -> d.mode == AiMode.ATTACK && id !in destroyed }

    /** Mísseis inimigos em voo contra o jogador. */
    fun incoming(): List<Missile> = missiles.filter { it.owner == Owner.ENEMY && it.targetId == "jogador" }

    /** Segundos até o impacto do míssil mais próximo, ou null. Considera que ele ainda acelera ao longo da linha de visada. */
    fun impactEta(): Double? = incoming().minOfOrNull { m ->
        val rel = ship.pos - m.pos
        val d = rel.length
        val closing = max((m.vel - ship.vel) dot (rel / max(d, 1.0)), 0.0)
        val a = Config.MISSILE_ACCEL * 0.6
        (-closing + sqrt(closing * closing + 2 * a * d)) / a
    }

    /** Sai da estação: a nave fica a 25 km, na mesma velocidade, sem atracar de novo até se afastar. */
    fun undock() {
        val b = dockedAt ?: return
        val pos = position(b)
        val radial = pos.unit()
        ship.pos = pos + radial * 25_000.0
        ship.vel = velocity(b)
        ship.angle = radial.angle
        ship.omega = 0.0
        status = Status.FLYING
        dockBlock = b
        dockedAt = null
        autopilot = AutopilotMode.OFF
        predictionCache = null
    }

    // ---- armas do jogador ----

    /** Pode disparar agora? Devolve o motivo se não. */
    fun canFire(): FireResult {
        if (!world.isMilitary) return FireResult.NOT_MILITARY
        if (ship.missiles <= 0) return FireResult.NO_AMMO
        val t = target
        if (t.kind != BodyKind.SHIP || !alive(t)) return FireResult.NO_TARGET
        if (t.faction.id == world.role.factionId) return FireResult.FRIENDLY
        if (distanceTo(t) > Config.MISSILE_RANGE) return FireResult.OUT_OF_RANGE
        return FireResult.FIRED
    }

    fun fireMissile(): FireResult {
        val r = canFire()
        if (r != FireResult.FIRED || status != Status.FLYING) return r
        launchAt("jogador", ship.pos, ship.vel + Vec2.fromAngle(ship.angle) * 0.0, target)
        ship.missiles--
        return FireResult.FIRED
    }

    // ---- abordagem ----

    fun canBoard(): BoardCheck {
        if (!world.isMilitary) return BoardCheck.NOT_MILITARY
        val t = target
        if (t.kind != BodyKind.SHIP || !alive(t)) return BoardCheck.NO_TARGET
        if (t.faction.id == world.role.factionId) return BoardCheck.FRIENDLY
        if (distanceTo(t) > Config.BOARD_RANGE) return BoardCheck.TOO_FAR
        if (relativeVelocity(t).length > Config.BOARD_SPEED) return BoardCheck.TOO_FAST
        return BoardCheck.OK
    }

    /** Tenta abordar o alvo. Sucesso: a nave é capturada; falha: o jogador sofre dano e o alvo reage. */
    fun boardTarget(): BoardCheck {
        val c = canBoard()
        if (c != BoardCheck.OK || status != Status.FLYING) return c
        val t = target
        val ok = rand() >= t.def.defense
        if (ok) {
            destroyed.add(t.id)
            dynamics.remove(t.id)
            blasts.add(Blast(position(t), time, BlastKind.FLARE))
            cycleTarget()
        } else {
            ship.hull = max(1.0, ship.hull - 25.0)
            setAi(t, AiMode.ATTACK)
            blasts.add(Blast(ship.pos, time, BlastKind.SHIP_HIT))
        }
        events.add(SimEvent.BoardingResult(t.id, ok))
        return BoardCheck.OK
    }

    // ---- corpos escondidos e dano ----

    /** A nave surge a [distance] m do jogador, na mesma velocidade, numa direção sorteada à frente. */
    fun ambush(b: Body, distance: Double) {
        hidden.remove(b.id)
        destroyed.remove(b.id)
        val dir = Vec2.fromAngle(ship.angle + (rand() - 0.5) * 1.2)
        dynamics[b.id] = Dynamic(ship.pos + dir * distance, ship.vel, AiMode.ORBIT)
    }

    fun reveal(b: Body) { hidden.remove(b.id) }
    fun conceal(b: Body) { hidden.add(b.id) }

    /** Remove uma nave do jogo sem explosão (some do mapa). */
    fun destroyBody(b: Body, explode: Boolean) {
        if (b.id in destroyed) return
        if (explode) blasts.add(Blast(position(b), time, BlastKind.KILL))
        destroyed.add(b.id)
        dynamics.remove(b.id)
        if (target === b) cycleTarget()
        events.add(SimEvent.BodyDestroyed(b.id))
    }

    /**
     * Um despistador sai de lado, perpendicular à linha de visada do míssil [from], a [Config.DECOY_EJECT_SPEED] m/s em
     * relação a quem o lançou, e segue em linha reta: o míssil enganado vai atrás dele e se afasta da nave.
     */
    private fun ejectFlare(pos: Vec2, vel: Vec2, from: Vec2, side: Int): Flare {
        val los = (pos - from).unit()
        val sideways = Vec2(-los.y, los.x) * side.toDouble()
        return Flare("d${nextId++}", pos, vel + sideways * Config.DECOY_EJECT_SPEED, time)
    }

    /** Lança um despistador contra os mísseis que perseguem a nave. Cada míssil é sorteado à parte. */
    fun deployDecoy(): Boolean {
        if (status != Status.FLYING || ship.decoys <= 0) return false
        val tracking = incoming()
        if (tracking.isEmpty()) return false
        ship.decoys--
        val threat = tracking.minByOrNull { (it.pos - ship.pos).length }!!
        val flare = ejectFlare(ship.pos, ship.vel, threat.pos, if (ship.decoys % 2 == 0) 1 else -1)
        flares.add(flare)
        val p = world.ship.decoyChance
        var ok = 0
        for (m in tracking) if (rand() < p) { m.targetId = flare.id; ok++ }
        blasts.add(Blast(ship.pos, time, BlastKind.FLARE))
        events.add(SimEvent.DecoyResult(ok, tracking.size))
        return true
    }

    /** O primeiro disparo de cada nave sai em momentos diferentes (4–11 s), para vários atacantes não dispararem juntos. */
    private fun armsOf(b: Body) = arms.getOrPut(b.id) { NpcArms(b.def.missiles, b.def.decoys, 4.0 + (b.id.hashCode().toLong() and 0x7fffffffL) % 8) }

    // ---- avanço do tempo ----

    private fun warpCap(): Double {
        var cap = Double.MAX_VALUE
        if (target.let { alive(it) }) {
            val d = distanceTo()
            val approach = approachSpeed()
            if (approach > 0.0 && d > 1.0) cap = min(cap, max(1.0, d / approach / 15.0))
        }
        val r = ship.pos.length
        val radialSpeed = (ship.pos dot ship.vel) / r
        if (radialSpeed < 0.0) cap = min(cap, max(1.0, (r - world.radius) / -radialSpeed / 15.0))
        for (b in world.solid) {
            val rel = b.position(time) - ship.pos
            val dist = rel.length
            val closing = (ship.vel - b.velocity(time)) dot (rel / max(dist, 1.0))
            if (closing > 0.0) cap = min(cap, max(1.0, (dist - b.def.radius) / closing / 15.0))
        }
        for ((id, dyn) in dynamics) {
            if (!limitsWarp(dyn) || id in destroyed) continue
            val b = world.body(id) ?: continue
            val rel = position(b) - ship.pos
            val dist = rel.length
            val closing = (ship.vel - velocity(b)) dot rel.unit()
            if (closing > 0 && dist > 1) cap = min(cap, max(1.0, dist / closing / 15.0))
            cap = min(cap, hostileCap(dyn.mode, dist))
        }
        // rota de colisão com um corpo: o tempo desacelera cedo, para dar pelo menos ~1 min reais de reação
        Autopilot.collisionCourse(world, time, ship.pos, ship.vel)?.let { (_, eta) -> cap = min(cap, max(1.0, eta / Config.COLLISION_REACTION)) }
        if (missiles.isNotEmpty()) cap = 1.0
        if (manualTurn != 0 || manualEngine) cap = min(cap, 10.0)
        return cap
    }

    /**
     * Teto imposto por uma nave hostil a [dist] metros: o teto fixo (×60 atacando, ×120 perseguindo) vale até
     * [Config.HOSTILE_CAP_RANGE]; mais longe cresce na proporção da distância, e o relógio só freia quando ela chega perto.
     */
    private fun hostileCap(mode: AiMode, dist: Double): Double {
        val base = if (mode == AiMode.ATTACK) Config.ATTACK_WARP_CAP else Config.PURSUIT_WARP_CAP
        return base * max(1.0, dist / Config.HOSTILE_CAP_RANGE)
    }

    /** Teto do fator de tempo que não depende da aproximação: perseguidores e atacantes (pela distância), mísseis e comando manual. */
    fun staticWarpCap(): Double {
        var cap = Double.MAX_VALUE
        for ((id, dyn) in dynamics) {
            if (!limitsWarp(dyn) || id in destroyed) continue
            val b = world.body(id) ?: continue
            cap = min(cap, hostileCap(dyn.mode, (position(b) - ship.pos).length))
        }
        if (missiles.isNotEmpty()) cap = 1.0
        if (manualTurn != 0 || manualEngine) cap = min(cap, 10.0)
        return cap
    }

    /** O fator de tempo que o jogo consegue sustentar agora: o nível escolhido, limitado pelos tetos fixos e pelo que o aparelho aguenta. */
    fun plannedWarp(level: Double = warpLevels[warpIndex], fps: Double = 30.0): Double =
        min(level, min(staticWarpCap(), Config.MAX_SUBSTEPS_PER_FRAME * Config.SUBSTEP * fps)).coerceAtLeast(1.0)

    /**
     * Quantos segundos de relógio real levam [simSeconds] de jogo, contando os tetos de verdade: o fator fixo
     * ([plannedWarp]) e o limite de aproximação (tempo até chegar ÷ 15), que vai freando o relógio no fim. Na frenagem
     * o tempo que resta é cerca de 1,5 vez o "tempo até chegar", daí a constante de 22 s (medida em voos completos).
     */
    fun realSecondsFor(simSeconds: Double, level: Double = warpLevels[warpIndex], fps: Double = 30.0): Double {
        if (simSeconds <= 0.0) return 0.0
        val w = plannedWarp(level, fps)
        var r = simSeconds
        var t = 0.0
        val k = 22.0
        if (r > k * w) { t += (r - k * w) / w; r = k * w }
        if (r > k) { t += k * kotlin.math.ln(r / k); r = k }
        return t + r
    }

    /** Avança [realDt] segundos de relógio, aplicando o fator de tempo (limitado por segurança). */
    fun advance(realDt: Double) {
        if (status != Status.FLYING) {
            effectiveWarp = 1.0
            return
        }
        effectiveWarp = min(warpLevels[warpIndex], warpCap())
        val simDt = min(realDt * effectiveWarp, Config.MAX_SUBSTEPS_PER_FRAME * Config.SUBSTEP)
        val steps = ceil(simDt / Config.SUBSTEP).toInt().coerceIn(1, Config.MAX_SUBSTEPS_PER_FRAME)
        val h = simDt / steps
        for (i in 0 until steps) {
            step(h)
            if (status != Status.FLYING) break
        }
    }

    /** Um passo de integração semi-implícita (v += a·dt; x += v·dt). */
    fun step(h: Double) {
        if (status != Status.FLYING) return
        val s = ship
        val tgt = target
        val tgtPos = position(tgt)
        val tgtVel = velocity(tgt)

        if (manualTurn != 0 || manualEngine) autopilot = AutopilotMode.OFF
        if (autopilot == AutopilotMode.VELOCITY && (desiredRelVel == null || velocityRef?.let { alive(it) } != true)) autopilot = AutopilotMode.OFF

        var alpha = 0.0
        var burn = 0.0
        var desired: Double? = null
        var hold = false
        when (autopilot) {
            AutopilotMode.OFF -> {
                alpha = manualTurn * Config.ALPHA
                if (manualEngine) burn = accelLimit
            }
            AutopilotMode.INTERCEPT -> {
                val cmd = Autopilot.intercept(world, time, s, tgtPos, tgtVel, thrustPct)
                desired = cmd.desiredAngle
                burn = cmd.burn
                hold = desired == null
            }
            AutopilotMode.PROGRADE, AutopilotMode.RETROGRADE -> {
                val vRel = s.vel - tgtVel
                if (vRel.length > 0.01) desired = vRel.angle + if (autopilot == AutopilotMode.RETROGRADE) PI else 0.0
            }
            AutopilotMode.STOP_SPIN -> alpha = Autopilot.spinDownAlpha(s.omega)
            AutopilotMode.VELOCITY -> {
                val ref = velocityRef!!
                val gDiff = world.gravity(s.pos, time) - world.gravity(position(ref), time)
                val cmd = Autopilot.velocity(s, s.vel - velocity(ref), desiredRelVel!!, accelLimit, gDiff)
                desired = cmd.desiredAngle
                burn = cmd.burn
                hold = desired == null
            }
        }
        if (autopilot != AutopilotMode.OFF && autopilot != AutopilotMode.STOP_SPIN) {
            if (desired != null) alpha = Autopilot.attitudeAlpha(s.angle, s.omega, desired)
            else if (hold) alpha = Autopilot.spinDownAlpha(s.omega)
        }

        // combustível: dm = m·a/ve·dt, sem passar do que existe
        var accel = burn
        if (accel > 0.0) {
            if (s.fuel <= 0.0) accel = 0.0
            else {
                val dm = s.mass * accel / s.exhaustVelocity * h
                if (dm > s.fuel) accel *= s.fuel / dm
                s.fuel = max(0.0, s.fuel - s.mass * accel / s.exhaustVelocity * h)
            }
        }

        s.omega = (s.omega + alpha * h).coerceIn(-Config.OMEGA_MAX, Config.OMEGA_MAX)
        s.angle = wrapAngle(s.angle + s.omega * h)
        if (desired != null && abs(wrapAngle(desired - s.angle)) < 0.003 && abs(s.omega) < 0.003) {
            s.angle = desired
            s.omega = 0.0
            alpha = 0.0
        }
        if (autopilot == AutopilotMode.STOP_SPIN && abs(s.omega) < 0.003) {
            s.omega = 0.0
            autopilot = AutopilotMode.OFF
            alpha = 0.0
        }
        s.burnAccel = accel
        s.turnAlpha = alpha

        // mísseis e naves do computador veem a posição de começo do passo (a nave só se move depois), para que todos
        // avancem juntos; sem isso o alvo parece estar um passo adiante (km, nas velocidades orbitais)
        stepMissiles(h)
        stepDynamics(h)
        val a = world.gravity(s.pos, time) + Vec2.fromAngle(s.angle) * accel
        s.vel = s.vel + a * h
        s.pos = s.pos + s.vel * h
        time += h
        pruneEffects()
        checkEvents()
    }

    private fun stepDynamics(h: Double) {
        for ((id, d) in dynamics) {
            if (id in destroyed) continue
            var thrust = Vec2.ZERO
            if (d.mode != AiMode.ORBIT) {
                val accelMax = world.body(id)?.def?.maxAccel ?: Config.PURSUER_ACCEL
                when (d.mode) {
                    AiMode.FLEE -> thrust = (d.pos - ship.pos).unit() * accelMax
                    AiMode.PATROL -> thrust = patrolThrust(d, accelMax)
                    else -> {
                        val stop = when (d.mode) {
                            AiMode.ATTACK -> Config.ATTACK_STOP
                            AiMode.ESCORT -> Config.ESCORT_DISTANCE
                            AiMode.BOARD -> Config.BOARD_STOP
                            else -> Config.PURSUER_STOP
                        }
                        val cmd = Autopilot.rendezvous(world, time, d.pos, d.vel, ship.pos, ship.vel, accelMax, stop, 1e9)
                        if (cmd != null) {
                            val mag = cmd.length
                            thrust = if (mag > accelMax) cmd * (accelMax / mag) else cmd
                        }
                    }
                }
                when (d.mode) {
                    AiMode.ATTACK -> enemyFire(id, d, h)
                    AiMode.ESCORT -> allyFire(id, d, h)
                    AiMode.BOARD -> if (id !in boarded && (ship.pos - d.pos).length < Config.BOARD_RANGE && (ship.vel - d.vel).length < Config.BOARD_SPEED) {
                        boarded.add(id)
                        events.add(SimEvent.Boarded(id))
                    }
                    else -> {}
                }
            }
            d.vel = d.vel + (world.gravity(d.pos, time) + thrust) * h
            d.pos = d.pos + d.vel * h
        }
    }

    /** Patrulha: vai de corpo em corpo da rota, parando perto de cada um antes de seguir para o próximo. */
    private fun patrolThrust(d: Dynamic, accelMax: Double): Vec2 {
        if (d.route.isEmpty()) return Vec2.ZERO
        val wp = world.body(d.route[d.leg % d.route.size]) ?: run { d.leg++; return Vec2.ZERO }
        val stop = wp.def.radius + Config.PATROL_STOP
        val wpPos = position(wp)
        val wpVel = velocity(wp)
        if ((wpPos - d.pos).length < stop * 1.5 && (wpVel - d.vel).length < Config.PATROL_ARRIVE_SPEED) d.leg = (d.leg + 1) % d.route.size
        val cmd = Autopilot.rendezvous(world, time, d.pos, d.vel, wpPos, wpVel, accelMax, stop, 1e9) ?: return Vec2.ZERO
        return if (cmd.length > accelMax) cmd * (accelMax / cmd.length) else cmd
    }

    /** Escolta: ataca naves hostis ao alcance com os próprios mísseis. */
    private fun allyFire(id: String, d: Dynamic, h: Double) {
        val b = world.body(id) ?: return
        val a = armsOf(b)
        a.cooldown -= h
        if (a.cooldown > 0 || a.missiles <= 0) return
        val foe = dynamics.entries
            .filter { (fid, fd) -> fid != id && fid !in destroyed && (fd.mode == AiMode.ATTACK || fd.mode == AiMode.BOARD) && (fd.pos - d.pos).length < Config.ENEMY_FIRE_RANGE }
            .minByOrNull { (it.value.pos - d.pos).length } ?: return
        val target = world.body(foe.key) ?: return
        a.missiles--
        a.cooldown = Config.ENEMY_COOLDOWN
        launchAt(id, d.pos, d.vel, target)
    }

    /** Lança um míssil amigo (do jogador ou de uma escolta) num corpo; o alvo pode sortear um despistador. */
    private fun launchAt(launcher: String, pos: Vec2, vel: Vec2, target: Body) {
        val m = Missile(nextId++, Owner.PLAYER, pos, vel + (position(target) - pos).unit() * 20.0, target.id, launcher)
        val a = armsOf(target)
        if (a.decoys > 0) {
            a.decoys--
            if (rand() < target.def.decoyChance) m.decoyAt = 0.5 * sqrt(2 * (position(target) - pos).length / Config.MISSILE_ACCEL) + 2.0
        }
        missiles.add(m)
        blasts.add(Blast(pos, time, BlastKind.LAUNCH))
        events.add(SimEvent.Launched(Owner.PLAYER, launcher))
    }

    /** Nave hostil em ataque: dispara quando está ao alcance, com intervalo entre os tiros. */
    private fun enemyFire(id: String, d: Dynamic, h: Double) {
        val b = world.body(id) ?: return
        val a = armsOf(b)
        a.cooldown -= h
        if (a.cooldown > 0 || a.missiles <= 0) return
        if ((ship.pos - d.pos).length > Config.ENEMY_FIRE_RANGE) return
        // equilíbrio: poucos mísseis ao mesmo tempo sobre o jogador, e o ritmo de fogo não cresce linearmente com o número de atacantes
        if (incoming().size >= Config.MAX_INCOMING) { a.cooldown = 1.0; return }
        val attackers = dynamics.count { (aid, ad) -> ad.mode == AiMode.ATTACK && aid !in destroyed }
        a.missiles--
        a.cooldown = Config.ENEMY_COOLDOWN * (1.0 + Config.ATTACKER_CROWDING * (attackers - 1).coerceAtLeast(0))
        missiles.add(Missile(nextId++, Owner.ENEMY, d.pos, d.vel, "jogador", id))
        blasts.add(Blast(d.pos, time, BlastKind.LAUNCH))
        events.add(SimEvent.Launched(Owner.ENEMY, id))
    }

    private fun stepMissiles(h: Double) {
        if (missiles.isEmpty() && flares.isEmpty()) return
        for (f in flares) {
            f.vel = f.vel + world.gravity(f.pos, time) * h
            f.pos = f.pos + f.vel * h
        }
        val it = missiles.iterator()
        while (it.hasNext()) {
            val m = it.next()
            m.age += h
            // o despistador sorteado no lançamento entra em ação
            val da = m.decoyAt
            if (da != null && m.age >= da && m.owner == Owner.PLAYER) {
                val victim = world.body(m.targetId)
                if (victim != null) {
                    val f = ejectFlare(position(victim), velocity(victim), m.pos, if (nextId % 2 == 0) 1 else -1)
                    flares.add(f)
                    blasts.add(Blast(f.pos, time, BlastKind.FLARE))
                    m.targetId = f.id
                    m.decoyAt = null
                }
            }
            if (m.age > Config.MISSILE_LIFE) {
                blasts.add(Blast(m.pos, time, BlastKind.FIZZLE))
                it.remove()
                continue
            }
            val tPos: Vec2
            val tVel: Vec2
            when {
                m.targetId == "jogador" -> { tPos = ship.pos; tVel = ship.vel }
                m.targetId.startsWith("d") -> {
                    val f = flares.firstOrNull { it.id == m.targetId }
                    if (f == null) { blasts.add(Blast(m.pos, time, BlastKind.FIZZLE)); it.remove(); continue }
                    tPos = f.pos; tVel = f.vel
                }
                else -> {
                    val b = world.body(m.targetId)
                    if (b == null || !alive(b)) { it.remove(); continue }
                    tPos = position(b); tVel = velocity(b)
                }
            }
            if (m.owner == Owner.ENEMY && m.targetId == "jogador" && !m.escortRolled) {
                val ally = dynamics.entries.firstOrNull { (eid, ed) -> ed.mode == AiMode.ESCORT && eid !in destroyed && (ed.pos - m.pos).length < Config.ESCORT_INTERCEPT_RANGE }
                if (ally != null) {
                    m.escortRolled = true
                    val chance = world.body(ally.key)?.def?.interceptChance ?: 0.5
                    if (rand() < chance) {
                        blasts.add(Blast(m.pos, time, BlastKind.INTERCEPT))
                        events.add(SimEvent.EscortIntercepted(ally.key))
                        it.remove()
                        continue
                    }
                }
            }
            val before = m.pos - tPos
            val thrust = missileGuidance(world, time, m.pos, m.vel, tPos, tVel)
            m.vel = m.vel + (world.gravity(m.pos, time) + thrust) * h
            m.pos = m.pos + m.vel * h
            val after = m.pos - (tPos + tVel * h)
            if (segmentMissDistance(before, after) < Config.MISSILE_FUZE) {
                impact(m, tPos)
                it.remove()
            }
        }
    }

    private fun impact(m: Missile, at: Vec2) {
        when {
            m.targetId == "jogador" -> {
                val dmg = Config.MISSILE_DAMAGE_MIN + rand() * (Config.MISSILE_DAMAGE_MAX - Config.MISSILE_DAMAGE_MIN)
                ship.hull -= dmg
                blasts.add(Blast(ship.pos, time, BlastKind.SHIP_HIT))
                events.add(SimEvent.Hit(dmg))
                if (ship.hull <= 0.0) {
                    ship.hull = 0.0
                    status = Status.DESTROYED
                    ship.burnAccel = 0.0
                    events.add(SimEvent.ShipDestroyed(true))
                }
            }
            m.targetId.startsWith("d") -> {
                blasts.add(Blast(at, time, BlastKind.FLARE))
                flares.removeAll { it.id == m.targetId }
            }
            else -> {
                blasts.add(Blast(at, time, BlastKind.KILL))
                destroyed.add(m.targetId)
                dynamics.remove(m.targetId)
                if (world.body(m.targetId)?.let { it === target } == true) cycleTarget()
                events.add(SimEvent.BodyDestroyed(m.targetId))
            }
        }
    }

    private fun pruneEffects() {
        blasts.removeAll { time - it.time > 4.0 }
        flares.removeAll { time - it.born > 40.0 }
    }

    private fun checkEvents() {
        val s = ship
        if (status != Status.FLYING) return
        val hit = world.collision(s.pos, time)
        if (hit != null) {
            status = Status.CRASHED
            crashedInto = hit
            s.burnAccel = 0.0
            events.add(SimEvent.Crashed)
            return
        }
        val b = dockBlock
        if (b != null && distanceTo(b) > Config.DOCK_DISTANCE * 1.5) dockBlock = null
        val t = target
        if (alive(t) && t.dockable && t !== dockBlock && distanceTo(t) < Config.DOCK_DISTANCE && relativeVelocity(t).length < Config.DOCK_SPEED) {
            status = Status.DOCKED
            dockedAt = t
            s.fuel = s.fuelCapacity
            s.hull = s.hullMax
            s.missiles = s.missilesMax
            s.decoys = s.decoysMax
            s.burnAccel = 0.0
            s.turnAlpha = 0.0
            autopilot = AutopilotMode.OFF
            missiles.clear()
            events.add(SimEvent.Docked(t))
        }
    }

    // ---- previsão balística ----

    /** Trajetória sem empuxo (200 passos) e a menor aproximação ao alvo. Recalculada no máximo a cada 2 s simulados. */
    fun prediction(): Prediction {
        val cached = predictionCache
        if (cached != null && abs(time - predictionTime) < 2.0) return cached
        val p = computePrediction()
        predictionCache = p
        predictionTime = time
        return p
    }

    private fun computePrediction(): Prediction {
        val steps = 200
        val approach = approachSpeed()
        val d = distanceTo()
        val tti = if (approach > 1.0) d / approach else 6 * 3600.0
        val horizon = (tti * 1.3).coerceIn(15 * 60.0, 6 * 3600.0)
        val dt = horizon / steps
        var pos = ship.pos
        var vel = ship.vel
        var t = time
        val pts = ArrayList<Vec2>(steps + 1)
        pts.add(pos)
        var best = Double.MAX_VALUE
        var bestT = 0.0
        val sub = ceil(dt / 10.0).toInt().coerceAtLeast(1)
        val h = dt / sub
        for (i in 1..steps) {
            for (k in 0 until sub) {
                vel = vel + world.gravity(pos, t) * h
                pos = pos + vel * h
                t += h
            }
            pts.add(pos)
            val dist = (positionAt(target, t) - pos).length
            if (dist < best) {
                best = dist
                bestT = t - time
            }
            if (world.collision(pos, t) != null) break
        }
        return Prediction(pts, best, bestT)
    }

    // ---- cópia e salvamento ----

    fun copyState(from: Simulation) {
        time = from.time
        status = from.status
        dockedAt = from.dockedAt
        dockBlock = from.dockBlock
        targetIndex = from.targetIndex
        autopilot = from.autopilot
        warpIndex = from.warpIndex
        thrustPct = from.thrustPct
        manualTurn = 0
        manualEngine = false
        rng = from.rng
        nextId = from.nextId
        val s = ship
        val o = from.ship
        s.pos = o.pos; s.vel = o.vel; s.angle = o.angle; s.omega = o.omega
        s.fuel = o.fuel; s.cargo = o.cargo; s.extra = o.extra; s.burnAccel = o.burnAccel; s.turnAlpha = o.turnAlpha
        s.hull = o.hull; s.missiles = o.missiles; s.decoys = o.decoys
        dynamics.clear()
        for ((k, d) in from.dynamics) dynamics[k] = Dynamic(d.pos, d.vel, d.mode)
        missiles.clear()
        for (m in from.missiles) missiles.add(Missile(m.id, m.owner, m.pos, m.vel, m.targetId, m.launcher).also { it.age = m.age; it.decoyAt = m.decoyAt })
        flares.clear(); for (f in from.flares) flares.add(Flare(f.id, f.pos, f.vel, f.born))
        blasts.clear()
        destroyed.clear(); destroyed.addAll(from.destroyed)
        hidden.clear(); hidden.addAll(from.hidden); boarded.clear(); boarded.addAll(from.boarded); crashedInto = from.crashedInto
        arms.clear(); for ((k, a) in from.arms) arms[k] = NpcArms(a.missiles, a.decoys, a.cooldown)
        events.clear()
        predictionCache = null
    }

    /** Cópia independente, para planejar rotas sem mexer no jogo. */
    fun copy(): Simulation = Simulation(world).also { it.copyState(this) }

    private fun vec(v: Vec2) = JsonArray(listOf(JsonPrimitive(v.x), JsonPrimitive(v.y)))
    private fun vec(a: JsonArray) = Vec2(a[0].jsonPrimitive.double, a[1].jsonPrimitive.double)

    fun toJson(): JsonObject = buildJsonObject {
        put("tempo", time)
        put("estado", status.name)
        put("atracadoEm", dockedAt?.id ?: "")
        put("bloqueioAtracar", dockBlock?.id ?: "")
        put("alvo", target.id)
        put("piloto", autopilot.name)
        put("tempoIdx", warpIndex)
        put("empuxo", thrustPct)
        put("pos", vec(ship.pos)); put("vel", vec(ship.vel))
        put("rumo", ship.angle); put("giro", ship.omega)
        put("combustivel", ship.fuel); put("carga", ship.cargo); put("extra", ship.extra)
        put("casco", ship.hull); put("misseis", ship.missiles); put("despistadores", ship.decoys)
        put("rng", rng); put("proximoId", nextId)
        put("dinamicos", buildJsonObject {
            for ((k, d) in dynamics) put(k, buildJsonObject { put("pos", vec(d.pos)); put("vel", vec(d.vel)); put("modo", d.mode.key); put("rota", JsonArray(d.route.map { JsonPrimitive(it) })); put("etapa", d.leg) })
        })
        put("destruidos", JsonArray(destroyed.map { JsonPrimitive(it) }))
        put("escondidos", JsonArray(hidden.map { JsonPrimitive(it) }))
        put("abordados", JsonArray(boarded.map { JsonPrimitive(it) }))
        put("colidiuCom", crashedInto ?: "")
        put("armas", buildJsonObject { for ((k, a) in arms) put(k, buildJsonObject { put("misseis", a.missiles); put("despistadores", a.decoys); put("espera", a.cooldown) }) })
        put("misseisEmVoo", JsonArray(missiles.map { m ->
            buildJsonObject {
                put("id", m.id); put("dono", m.owner.name); put("pos", vec(m.pos)); put("vel", vec(m.vel))
                put("alvo", m.targetId); put("origem", m.launcher); put("idade", m.age); put("despistaEm", m.decoyAt ?: -1.0)
            }
        }))
        put("chamarizes", JsonArray(flares.map { f -> buildJsonObject { put("id", f.id); put("pos", vec(f.pos)); put("vel", vec(f.vel)); put("nasceu", f.born) } }))
    }

    fun fromJson(o: JsonObject) {
        time = o["tempo"]!!.jsonPrimitive.double
        status = Status.valueOf(o["estado"]!!.jsonPrimitive.content)
        dockedAt = world.body(o["atracadoEm"]!!.jsonPrimitive.content)
        dockBlock = world.body(o["bloqueioAtracar"]!!.jsonPrimitive.content)
        targetIndex = bodies.indexOfFirst { it.id == o["alvo"]!!.jsonPrimitive.content }.coerceAtLeast(0)
        autopilot = AutopilotMode.valueOf(o["piloto"]!!.jsonPrimitive.content)
        warpIndex = o["tempoIdx"]!!.jsonPrimitive.int.coerceIn(0, warpLevels.size - 1)
        thrustPct = o["empuxo"]!!.jsonPrimitive.int
        ship.pos = vec(o["pos"]!!.jsonArray); ship.vel = vec(o["vel"]!!.jsonArray)
        ship.angle = o["rumo"]!!.jsonPrimitive.double; ship.omega = o["giro"]!!.jsonPrimitive.double
        ship.fuel = o["combustivel"]!!.jsonPrimitive.double; ship.cargo = o["carga"]!!.jsonPrimitive.double; ship.extra = o["extra"]!!.jsonPrimitive.double
        ship.hull = o["casco"]!!.jsonPrimitive.double; ship.missiles = o["misseis"]!!.jsonPrimitive.int; ship.decoys = o["despistadores"]!!.jsonPrimitive.int
        rng = o["rng"]!!.jsonPrimitive.long; nextId = o["proximoId"]!!.jsonPrimitive.int
        dynamics.clear()
        for ((k, v) in o["dinamicos"]!!.jsonObject) {
            val d = v.jsonObject
            val mode = AiMode.values().first { it.key == d["modo"]!!.jsonPrimitive.content }
            dynamics[k] = Dynamic(vec(d["pos"]!!.jsonArray), vec(d["vel"]!!.jsonArray), mode,
                d["rota"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList(), d["etapa"]?.jsonPrimitive?.int ?: 0)
        }
        destroyed.clear(); o["destruidos"]!!.jsonArray.forEach { destroyed.add(it.jsonPrimitive.content) }
        hidden.clear(); o["escondidos"]!!.jsonArray.forEach { hidden.add(it.jsonPrimitive.content) }
        boarded.clear(); o["abordados"]!!.jsonArray.forEach { boarded.add(it.jsonPrimitive.content) }
        crashedInto = o["colidiuCom"]!!.jsonPrimitive.content.ifEmpty { null }
        arms.clear()
        for ((k, v) in o["armas"]!!.jsonObject) { val a = v.jsonObject; arms[k] = NpcArms(a["misseis"]!!.jsonPrimitive.int, a["despistadores"]!!.jsonPrimitive.int, a["espera"]!!.jsonPrimitive.double) }
        missiles.clear()
        for (e in o["misseisEmVoo"]!!.jsonArray) {
            val m = e.jsonObject
            missiles.add(Missile(m["id"]!!.jsonPrimitive.int, Owner.valueOf(m["dono"]!!.jsonPrimitive.content), vec(m["pos"]!!.jsonArray), vec(m["vel"]!!.jsonArray), m["alvo"]!!.jsonPrimitive.content, m["origem"]!!.jsonPrimitive.content).also {
                it.age = m["idade"]!!.jsonPrimitive.double
                it.decoyAt = m["despistaEm"]!!.jsonPrimitive.double.takeIf { v -> v >= 0 }
            })
        }
        flares.clear()
        for (e in o["chamarizes"]!!.jsonArray) { val f = e.jsonObject; flares.add(Flare(f["id"]!!.jsonPrimitive.content, vec(f["pos"]!!.jsonArray), vec(f["vel"]!!.jsonArray), f["nasceu"]!!.jsonPrimitive.double)) }
        blasts.clear(); events.clear()
        ship.burnAccel = 0.0; ship.turnAlpha = 0.0
        manualTurn = 0; manualEngine = false
        predictionCache = null
    }
}
