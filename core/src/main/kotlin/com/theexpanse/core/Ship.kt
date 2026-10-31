package com.theexpanse.core

import kotlin.math.ln

/** Estado da nave do jogador. Só a simulação escreve aqui. */
class Ship {
    var pos: Vec2 = Vec2.ZERO
    var vel: Vec2 = Vec2.ZERO

    /** Rumo do nariz (rad) e giro (rad/s). */
    var angle: Double = 0.0
    var omega: Double = 0.0
    var fuel: Double = Config.FUEL_MASS
    var cargo: Double = Config.CARGO_MASS

    /** Telemetria do último passo, para efeitos: aceleração do motor (m/s²) e aceleração angular (rad/s²). */
    var burnAccel: Double = 0.0
    var turnAlpha: Double = 0.0

    val mass: Double get() = Config.DRY_MASS + cargo + fuel

    /** Δv restante pela equação do foguete. */
    val deltaV: Double get() = Config.EXHAUST_VELOCITY * ln(mass / (Config.DRY_MASS + cargo))

    /** Fração de empuxo em uso no último passo (0..1), para o jato e a vibração. */
    val burnFraction: Double get() = (burnAccel / Config.MAX_ACCEL).coerceIn(0.0, 1.0)
}
