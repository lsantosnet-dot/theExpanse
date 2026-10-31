package com.theexpanse.core

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** Vetor 2D em metros (o plano orbital). Ângulos em radianos, 0 = eixo +x, positivo = anti-horário. */
data class Vec2(val x: Double, val y: Double) {
    operator fun plus(o: Vec2) = Vec2(x + o.x, y + o.y)
    operator fun minus(o: Vec2) = Vec2(x - o.x, y - o.y)
    operator fun times(k: Double) = Vec2(x * k, y * k)
    operator fun div(k: Double) = Vec2(x / k, y / k)
    operator fun unaryMinus() = Vec2(-x, -y)

    infix fun dot(o: Vec2) = x * o.x + y * o.y

    val length: Double get() = hypot(x, y)
    val angle: Double get() = atan2(y, x)

    fun unit(): Vec2 {
        val l = length
        return if (l == 0.0) ZERO else Vec2(x / l, y / l)
    }

    companion object {
        val ZERO = Vec2(0.0, 0.0)
        fun fromAngle(a: Double) = Vec2(cos(a), sin(a))
    }
}

/** Leva o ângulo para (-π, π]. */
fun wrapAngle(a: Double): Double {
    var r = (a + PI) % (2 * PI)
    if (r <= 0.0) r += 2 * PI
    return r - PI
}
