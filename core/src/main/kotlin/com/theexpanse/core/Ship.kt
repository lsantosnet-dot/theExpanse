package com.theexpanse.core

import kotlin.math.ln

/** Estado da nave do jogador. Só a simulação escreve aqui. */
class Ship(val dryMass: Double, val fuelCapacity: Double, cargo: Double, val hullMax: Double = 100.0, val missilesMax: Int = 0, val decoysMax: Int = 4) {
    var pos: Vec2 = Vec2.ZERO
    var vel: Vec2 = Vec2.ZERO

    /** Rumo do nariz (rad) e giro (rad/s). */
    var angle: Double = 0.0
    var omega: Double = 0.0
    var fuel: Double = fuelCapacity

    /** Integridade do casco e munição (mísseis e despistadores). */
    var hull: Double = hullMax
    var missiles: Int = missilesMax
    var decoys: Int = decoysMax

    /** Carga do contrato (kg). */
    var cargo: Double = cargo

    /** Massa de objetos carregados (kg), somada pela história. */
    var extra: Double = 0.0

    /** Telemetria do último passo, para efeitos: aceleração do motor (m/s²) e aceleração angular (rad/s²). */
    var burnAccel: Double = 0.0
    var turnAlpha: Double = 0.0

    val mass: Double get() = dryMass + cargo + extra + fuel

    /** Δv restante pela equação do foguete. */
    val deltaV: Double get() = Config.EXHAUST_VELOCITY * ln(mass / (dryMass + cargo + extra))

    /** Fração de empuxo em uso no último passo (0..1), para o jato e a vibração. */
    val burnFraction: Double get() = (burnAccel / Config.MAX_ACCEL).coerceIn(0.0, 1.0)
}
