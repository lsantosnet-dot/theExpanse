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
    /** Aceleração angular que leva o rumo ao desejado com giro suave (controle do protótipo). */
    fun attitudeAlpha(angle: Double, omega: Double, desired: Double): Double {
        val e = wrapAngle(desired - angle)
        val wDes = (sign(e) * sqrt(2 * Config.ALPHA * 0.6 * abs(e))).coerceIn(-Config.OMEGA_MAX, Config.OMEGA_MAX)
        return ((wDes - omega) * 4).coerceIn(-Config.ALPHA, Config.ALPHA)
    }

    /** Alpha para zerar o giro. */
    fun spinDownAlpha(omega: Double): Double = ((0.0 - omega) * 4).coerceIn(-Config.ALPHA, Config.ALPHA)

    /**
     * Interceptação por orientação de ponto de encontro: acelera até uma velocidade de aproximação que ainda
     * permite frear em 85% da aceleração disponível, limitada pelo Δv que sobra.
     */
    fun intercept(ship: Ship, targetPos: Vec2, targetVel: Vec2, thrustPct: Int): AutopilotCommand {
        val aLim = Config.MAX_ACCEL * thrustPct / 100.0
        val rel = targetPos - ship.pos
        val d = rel.length
        if (d < 1.0) return AutopilotCommand(null, 0.0)
        val u = rel / d
        val vRel = ship.vel - targetVel
        val vCap = max(1500.0, 0.42 * (ship.deltaV + vRel.length))
        val vDes = min(sqrt(2 * 0.85 * aLim * max(0.0, d - 3000.0)) * 0.9, vCap)
        val aCmd = (u * vDes - vRel) * 0.6 - (gravityAt(ship.pos) - gravityAt(targetPos))
        val mag = aCmd.length
        if (mag <= 0.03) return AutopilotCommand(null, 0.0)
        val desired = aCmd.angle
        val err = abs(wrapAngle(desired - ship.angle))
        val f = ((0.45 - err) / 0.35).coerceIn(0.0, 1.0)
        return AutopilotCommand(desired, min(mag, aLim) * f)
    }
}
