package com.theexpanse.core

/** Constantes físicas e de jogo, todas em unidades SI. */
object Config {
    /** Parâmetro gravitacional do planeta Aurélia (m³/s²). */
    const val GM = 1.0e17
    const val PLANET_RADIUS = 6.0e7

    /** Velocidade de exaustão do propelente (m/s). */
    const val EXHAUST_VELOCITY = 8.0e5

    /** Aceleração com empuxo 100% (m/s²), para a massa de qualquer momento: o motor ajusta a vazão. */
    const val MAX_ACCEL = 6.0
    const val DEFAULT_THRUST_PCT = 50

    /** Aceleração angular e velocidade angular máxima (rad/s² e rad/s). */
    const val ALPHA = 0.5
    const val OMEGA_MAX = 0.8

    const val DRY_MASS = 240_000.0
    const val CARGO_MASS = 40_000.0
    const val FUEL_MASS = 32_000.0

    /** Passo máximo de integração (s de tempo simulado). */
    const val SUBSTEP = 0.14
    const val MAX_SUBSTEPS_PER_FRAME = 900

    /** Atracação: distância e velocidade relativa máximas. */
    const val DOCK_DISTANCE = 20_000.0
    const val DOCK_SPEED = 20.0

    val WARP_FACTORS = doubleArrayOf(1.0, 10.0, 100.0, 1000.0, 5000.0)

    const val SHIP_LENGTH = 25.0
    const val STATION_SIZE = 150.0
    const val FRIGATE_SIZE = 90.0
}
