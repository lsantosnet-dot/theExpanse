package com.theexpanse.core

import com.theexpanse.content.AiMode
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

enum class Status { FLYING, DOCKED, CRASHED }

class Prediction(val points: List<Vec2>, val closestDistance: Double, val closestTime: Double)

/** Corpo que saiu da órbita analítica e passou a ser integrado (perseguidor ou à deriva). */
class Dynamic(var pos: Vec2, var vel: Vec2, var mode: AiMode)

/**
 * Simulação pura: nave com inércia e gravidade, corpos em órbita analítica, perseguidores integrados,
 * piloto automático e tempo acelerado. Nada aqui conhece Android, tela ou relógio de parede.
 */
class Simulation(val world: World) {
    var time = 0.0
        private set
    val ship = Ship(world.ship.dryMass, world.ship.fuelMass, world.ship.cargoMass)
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

    /** Comandos manuais (mantidos pelo controle de entrada). */
    var manualTurn = 0
    var manualEngine = false

    /** Fator de tempo realmente aplicado no último quadro. */
    var effectiveWarp = 1.0
        private set

    val dynamics = LinkedHashMap<String, Dynamic>()
    private var dockBlock: Body? = null
    private var predictionCache: Prediction? = null
    private var predictionTime = Double.NaN

    val bodies: List<Body> get() = world.bodies
    val target: Body get() = bodies[targetIndex]

    init {
        reset()
    }

    fun reset() {
        time = 0.0
        status = Status.FLYING
        dockedAt = null
        dockBlock = null
        autopilot = AutopilotMode.OFF
        warpIndex = 0
        thrustPct = Config.DEFAULT_THRUST_PCT
        manualTurn = 0
        manualEngine = false
        effectiveWarp = 1.0
        dynamics.clear()
        predictionCache = null
        val setup = world.ship
        targetIndex = bodies.indexOfFirst { it.id == setup.targetId }.takeIf { it >= 0 } ?: 0
        val s = ship
        val near = world.body(setup.startNear) ?: bodies.first()
        val nearPos = near.position(0.0)
        val radial = nearPos.unit()
        s.pos = nearPos + radial * setup.startAltitude
        s.vel = Vec2(-radial.y, radial.x) * sqrt(world.gm / s.pos.length) // prograde: anti-horário, tangente
        s.angle = s.vel.angle
        s.omega = 0.0
        s.fuel = s.fuelCapacity
        s.cargo = setup.cargoMass
        s.extra = 0.0
        s.burnAccel = 0.0
        s.turnAlpha = 0.0
    }

    // ---- posições (analíticas ou integradas) ----

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

    val accelLimit: Double get() = Config.MAX_ACCEL * thrustPct / 100.0

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
    fun toggleAutopilot(mode: AutopilotMode) { autopilot = if (autopilot == mode) AutopilotMode.OFF else mode }
    fun setWarp(index: Int) { warpIndex = index.coerceIn(0, Config.WARP_FACTORS.size - 1) }

    fun cycleTarget() {
        targetIndex = (targetIndex + 1) % bodies.size
        predictionCache = null
    }

    fun selectTarget(index: Int) {
        targetIndex = index.coerceIn(0, bodies.size - 1)
        predictionCache = null
    }

    fun selectTarget(b: Body) = selectTarget(bodies.indexOf(b))

    fun adjustThrust(deltaSteps: Int) { thrustPct = (thrustPct + 10 * deltaSteps).coerceIn(10, 100) }

    fun setThrust(pct: Int) { thrustPct = (pct / 10 * 10).coerceIn(10, 100) }

    /** Muda o comportamento de um corpo: perseguir (passa a ser integrado) ou orbitar (deriva sem empuxo). */
    fun setAi(b: Body, mode: AiMode) {
        val d = dynamics[b.id]
        if (d == null) {
            if (mode == AiMode.PURSUE) dynamics[b.id] = Dynamic(b.position(time), b.velocity(time), mode)
        } else d.mode = mode
    }

    fun isPursued(): Boolean = dynamics.values.any { it.mode == AiMode.PURSUE }

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

    // ---- avanço do tempo ----

    private fun warpCap(): Double {
        var cap = Double.MAX_VALUE
        val d = distanceTo()
        val approach = approachSpeed()
        if (approach > 0.0 && d > 1.0) cap = min(cap, max(1.0, d / approach / 15.0))
        val r = ship.pos.length
        val radialSpeed = (ship.pos dot ship.vel) / r
        if (radialSpeed < 0.0) cap = min(cap, max(1.0, (r - world.radius) / -radialSpeed / 15.0))
        for ((id, dyn) in dynamics) {
            if (dyn.mode != AiMode.PURSUE) continue
            val b = world.body(id) ?: continue
            val rel = position(b) - ship.pos
            val dist = rel.length
            val closing = (ship.vel - velocity(b)) dot rel.unit()
            if (closing > 0 && dist > 1) cap = min(cap, max(1.0, dist / closing / 15.0))
            cap = min(cap, Config.PURSUIT_WARP_CAP)
        }
        if (manualTurn != 0 || manualEngine) cap = min(cap, 10.0)
        return cap
    }

    /** Avança [realDt] segundos de relógio, aplicando o fator de tempo (limitado por segurança). */
    fun advance(realDt: Double) {
        if (status != Status.FLYING) {
            effectiveWarp = 1.0
            return
        }
        effectiveWarp = min(Config.WARP_FACTORS[warpIndex], warpCap())
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
                val cmd = Autopilot.intercept(world, s, tgtPos, tgtVel, thrustPct)
                desired = cmd.desiredAngle
                burn = cmd.burn
                hold = desired == null
            }
            AutopilotMode.PROGRADE, AutopilotMode.RETROGRADE -> {
                val vRel = s.vel - tgtVel
                if (vRel.length > 0.01) desired = vRel.angle + if (autopilot == AutopilotMode.RETROGRADE) PI else 0.0
            }
            AutopilotMode.STOP_SPIN -> alpha = Autopilot.spinDownAlpha(s.omega)
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
                val dm = s.mass * accel / Config.EXHAUST_VELOCITY * h
                if (dm > s.fuel) accel *= s.fuel / dm
                s.fuel = max(0.0, s.fuel - s.mass * accel / Config.EXHAUST_VELOCITY * h)
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

        val a = world.gravity(s.pos) + Vec2.fromAngle(s.angle) * accel
        s.vel = s.vel + a * h
        s.pos = s.pos + s.vel * h

        stepDynamics(h)
        time += h
        checkEvents()
    }

    private fun stepDynamics(h: Double) {
        for (d in dynamics.values) {
            var thrust = Vec2.ZERO
            if (d.mode == AiMode.PURSUE) {
                val cmd = Autopilot.rendezvous(world, d.pos, d.vel, ship.pos, ship.vel, Config.PURSUER_ACCEL, Config.PURSUER_STOP, 1e9)
                if (cmd != null) {
                    val mag = cmd.length
                    thrust = if (mag > Config.PURSUER_ACCEL) cmd * (Config.PURSUER_ACCEL / mag) else cmd
                }
            }
            d.vel = d.vel + (world.gravity(d.pos) + thrust) * h
            d.pos = d.pos + d.vel * h
        }
    }

    private fun checkEvents() {
        val s = ship
        if (s.pos.length < world.radius) {
            status = Status.CRASHED
            s.burnAccel = 0.0
            return
        }
        val b = dockBlock
        if (b != null && distanceTo(b) > Config.DOCK_DISTANCE * 1.5) dockBlock = null
        val t = target
        if (t.dockable && t !== dockBlock && distanceTo(t) < Config.DOCK_DISTANCE && relativeVelocity(t).length < Config.DOCK_SPEED) {
            status = Status.DOCKED
            dockedAt = t
            s.fuel = s.fuelCapacity
            s.burnAccel = 0.0
            s.turnAlpha = 0.0
            autopilot = AutopilotMode.OFF
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
                vel = vel + world.gravity(pos) * h
                pos = pos + vel * h
                t += h
            }
            pts.add(pos)
            val dist = (positionAt(target, t) - pos).length
            if (dist < best) {
                best = dist
                bestT = t - time
            }
            if (pos.length < world.radius) break
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
        val s = ship
        val o = from.ship
        s.pos = o.pos; s.vel = o.vel; s.angle = o.angle; s.omega = o.omega
        s.fuel = o.fuel; s.cargo = o.cargo; s.extra = o.extra; s.burnAccel = o.burnAccel; s.turnAlpha = o.turnAlpha
        dynamics.clear()
        for ((k, d) in from.dynamics) dynamics[k] = Dynamic(d.pos, d.vel, d.mode)
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
        put("dinamicos", buildJsonObject {
            for ((k, d) in dynamics) put(k, buildJsonObject { put("pos", vec(d.pos)); put("vel", vec(d.vel)); put("modo", d.mode.key) })
        })
    }

    fun fromJson(o: JsonObject) {
        time = o["tempo"]!!.jsonPrimitive.double
        status = Status.valueOf(o["estado"]!!.jsonPrimitive.content)
        dockedAt = world.body(o["atracadoEm"]!!.jsonPrimitive.content)
        dockBlock = world.body(o["bloqueioAtracar"]!!.jsonPrimitive.content)
        targetIndex = bodies.indexOfFirst { it.id == o["alvo"]!!.jsonPrimitive.content }.coerceAtLeast(0)
        autopilot = AutopilotMode.valueOf(o["piloto"]!!.jsonPrimitive.content)
        warpIndex = o["tempoIdx"]!!.jsonPrimitive.int
        thrustPct = o["empuxo"]!!.jsonPrimitive.int
        ship.pos = vec(o["pos"]!!.jsonArray); ship.vel = vec(o["vel"]!!.jsonArray)
        ship.angle = o["rumo"]!!.jsonPrimitive.double; ship.omega = o["giro"]!!.jsonPrimitive.double
        ship.fuel = o["combustivel"]!!.jsonPrimitive.double; ship.cargo = o["carga"]!!.jsonPrimitive.double; ship.extra = o["extra"]!!.jsonPrimitive.double
        dynamics.clear()
        for ((k, v) in o["dinamicos"]!!.jsonObject) {
            val d = v.jsonObject
            val mode = AiMode.values().first { it.key == d["modo"]!!.jsonPrimitive.content }
            dynamics[k] = Dynamic(vec(d["pos"]!!.jsonArray), vec(d["vel"]!!.jsonArray), mode)
        }
        ship.burnAccel = 0.0; ship.turnAlpha = 0.0
        manualTurn = 0; manualEngine = false
        predictionCache = null
    }
}
