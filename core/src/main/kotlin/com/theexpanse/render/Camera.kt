package com.theexpanse.render

import kotlin.math.PI

/** Câmera do jogador: giro em volta da nave (yaw, rad) e zoom. Vale para a cabine e para a câmera externa. */
class Camera {
    var yaw = 0.0
    var zoom = 1.0

    /** Inclinação da câmera 3D (rad): 0 = rente ao plano, ~1,5 = de cima. */
    var pitch = 0.55

    fun rotate(delta: Double) {
        yaw += delta
        while (yaw > PI) yaw -= 2 * PI
        while (yaw < -PI) yaw += 2 * PI
    }

    fun tilt(delta: Double) {
        pitch = (pitch + delta).coerceIn(0.06, 1.45)
    }

    fun zoomBy(factor: Double) {
        zoom = (zoom * factor).coerceIn(0.5, 3.5)
    }

    fun reset() {
        yaw = 0.0
        zoom = 1.0
        pitch = 0.55
    }

    val centered: Boolean get() = kotlin.math.abs(yaw) < 0.01 && kotlin.math.abs(zoom - 1.0) < 0.01 && kotlin.math.abs(pitch - 0.55) < 0.01
}
