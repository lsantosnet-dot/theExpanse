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
    fun rendezvous(world: World, pos: Vec2, vel: Vec2, tPos: Vec2, tVel: Vec2, aLim: Double, stopDist: Double, vCap: Double): Vec2? {
        val rel = tPos - pos
        val d = rel.length
        if (d < 1.0) return null
        val u = rel / d
        val vRel = vel - tVel
        val vDes = min(sqrt(2 * 0.85 * aLim * max(0.0, d - stopDist)) * 0.9, vCap)
        return (u * vDes - vRel) * 0.6 - (world.gravity(pos) - world.gravity(tPos))
    }

    /** Interceptação da nave do jogador, limitada pelo Δv que sobra para não gastar mais combustível do que existe. */
    fun intercept(world: World, ship: Ship, targetPos: Vec2, targetVel: Vec2, thrustPct: Int): AutopilotCommand {
        val aLim = Config.MAX_ACCEL * thrustPct / 100.0
        val vRel = ship.vel - targetVel
        val vCap = max(1500.0, 0.42 * (ship.deltaV + vRel.length))
        val aCmd = rendezvous(world, ship.pos, ship.vel, targetPos, targetVel, aLim, 3000.0, vCap) ?: return AutopilotCommand(null, 0.0)
        val mag = aCmd.length
        if (mag <= 0.03) return AutopilotCommand(null, 0.0)
        val desired = aCmd.angle
        val err = abs(wrapAngle(desired - ship.angle))
        val f = ((0.45 - err) / 0.35).coerceIn(0.0, 1.0)
        return AutopilotCommand(desired, min(mag, aLim) * f)
    }
}
