package com.theexpanse.core

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

enum class Status { FLYING, DOCKED, CRASHED }

class Prediction(val points: List<Vec2>, val closestDistance: Double, val closestTime: Double)

/**
 * Simulação pura: nave com inércia e gravidade, corpos em órbita analítica, piloto automático e tempo acelerado.
 * Nada aqui conhece Android, tela ou relógio de parede: quem chama decide quanto tempo real passou.
 */
class Simulation(val bodies: List<Body> = Calice.bodies) {
    var time = 0.0
        private set
    val ship = Ship()
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

    private var predictionCache: Prediction? = null
    private var predictionTime = Double.NaN

    val target: Body get() = bodies[targetIndex]

    init {
        reset()
    }

    fun reset() {
        time = 0.0
        status = Status.FLYING
        dockedAt = null
        autopilot = AutopilotMode.OFF
        warpIndex = 0
        thrustPct = Config.DEFAULT_THRUST_PCT
        targetIndex = 0
        manualTurn = 0
        manualEngine = false
        effectiveWarp = 1.0
        predictionCache = null
        val s = ship
        val dockPos = Calice.dock.position(0.0)
        val radial = dockPos.unit()
        s.pos = dockPos + radial * 40_000.0
        val r = s.pos.length
        val v = kotlin.math.sqrt(Config.GM / r)
        // prograde = sentido anti-horário, perpendicular ao raio
        s.vel = Vec2(-radial.y, radial.x) * v
        s.angle = s.vel.angle
        s.omega = 0.0
        s.fuel = Config.FUEL_MASS
        s.cargo = Config.CARGO_MASS
        s.burnAccel = 0.0
        s.turnAlpha = 0.0
    }

    // ---- comandos ----

    fun setAutopilot(mode: AutopilotMode) {
        autopilot = mode
    }

    fun toggleAutopilot(mode: AutopilotMode) {
        autopilot = if (autopilot == mode) AutopilotMode.OFF else mode
    }

    fun setWarp(index: Int) {
        warpIndex = index.coerceIn(0, Config.WARP_FACTORS.size - 1)
    }

    fun cycleTarget() {
        targetIndex = (targetIndex + 1) % bodies.size
        predictionCache = null
    }

    fun selectTarget(index: Int) {
        targetIndex = index.coerceIn(0, bodies.size - 1)
        predictionCache = null
    }

    fun adjustThrust(deltaSteps: Int) {
        thrustPct = (thrustPct + 10 * deltaSteps).coerceIn(10, 100)
    }

    // ---- leituras ----

    /** Aceleração máxima com o empuxo escolhido. */
    val accelLimit: Double get() = Config.MAX_ACCEL * thrustPct / 100.0

    fun relativePosition(b: Body = target): Vec2 = b.position(time) - ship.pos
    fun relativeVelocity(b: Body = target): Vec2 = ship.vel - b.velocity(time)
    fun distanceTo(b: Body = target): Double = relativePosition(b).length

    /** Velocidade de aproximação (positiva = fechando). */
    fun approachSpeed(b: Body = target): Double {
        val rel = relativePosition(b)
        val d = rel.length
        if (d < 1.0) return 0.0
        return relativeVelocity(b) dot (rel / d)
    }

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

    /** Segundos até a distância em que é preciso inverter (para a contagem "inverter em"), ou null. */
    fun secondsToFlip(): Double? {
        val v = approachSpeed()
        if (v <= 50.0) return null
        val trigger = 1.15 * v * v / (2 * 0.85 * accelLimit)
        val gap = distanceTo() - trigger
        return if (gap > 0) gap / v else 0.0
    }

    // ---- avanço do tempo ----

    private fun warpCap(): Double {
        var cap = Double.MAX_VALUE
        val rel = relativePosition()
        val d = rel.length
        val approach = approachSpeed()
        if (approach > 0.0 && d > 1.0) cap = min(cap, max(1.0, d / approach / 15.0))
        val r = ship.pos.length
        val radialSpeed = (ship.pos dot ship.vel) / r
        if (radialSpeed < 0.0) cap = min(cap, max(1.0, (r - Config.PLANET_RADIUS) / -radialSpeed / 15.0))
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
        var simDt = realDt * effectiveWarp
        simDt = min(simDt, Config.MAX_SUBSTEPS_PER_FRAME * Config.SUBSTEP)
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
        val tgtPos = tgt.position(time)
        val tgtVel = tgt.velocity(time)

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
                val cmd = Autopilot.intercept(s, tgtPos, tgtVel, thrustPct)
                desired = cmd.desiredAngle
                burn = cmd.burn
                hold = desired == null
            }
            AutopilotMode.PROGRADE, AutopilotMode.RETROGRADE -> {
                val vRel = s.vel - tgtVel
                if (vRel.length > 0.01) {
                    desired = vRel.angle + if (autopilot == AutopilotMode.RETROGRADE) PI else 0.0
                }
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

        val a = gravityAt(s.pos) + Vec2.fromAngle(s.angle) * accel
        s.vel = s.vel + a * h
        s.pos = s.pos + s.vel * h
        time += h

        checkEvents()
    }

    private fun checkEvents() {
        val s = ship
        if (s.pos.length < Config.PLANET_RADIUS) {
            status = Status.CRASHED
            s.burnAccel = 0.0
            return
        }
        val t = target
        if (t.kind == BodyKind.STATION && distanceTo(t) < Config.DOCK_DISTANCE && relativeVelocity(t).length < Config.DOCK_SPEED) {
            status = Status.DOCKED
            dockedAt = t
            s.fuel = Config.FUEL_MASS
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
                vel = vel + gravityAt(pos) * h
                pos = pos + vel * h
                t += h
            }
            pts.add(pos)
            val dist = (target.position(t) - pos).length
            if (dist < best) {
                best = dist
                bestT = t - time
            }
            if (pos.length < Config.PLANET_RADIUS) break
        }
        return Prediction(pts, best, bestT)
    }
}
