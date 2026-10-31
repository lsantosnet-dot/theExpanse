package com.theexpanse.core

/** Constantes de jogo comuns a todas as campanhas, em unidades SI. O planeta vem do pacote. */
object Config {
    /** Velocidade de exaustão do propelente (m/s). */
    const val EXHAUST_VELOCITY = 8.0e5

    /** Aceleração com empuxo 100% (m/s²): o motor ajusta a vazão para a massa do momento. */
    const val MAX_ACCEL = 6.0
    const val DEFAULT_THRUST_PCT = 50

    /** Aceleração angular e velocidade angular máxima (rad/s² e rad/s). */
    const val ALPHA = 0.5
    const val OMEGA_MAX = 0.8

    /** Passo máximo de integração (s de tempo simulado). */
    const val SUBSTEP = 0.14
    const val MAX_SUBSTEPS_PER_FRAME = 900

    /** Atracação: distância e velocidade relativa máximas. */
    const val DOCK_DISTANCE = 20_000.0
    const val DOCK_SPEED = 20.0

    /** Perseguidor: aceleração máxima e distância de parada (m). */
    const val PURSUER_ACCEL = 3.0
    const val PURSUER_STOP = 20_000.0
    const val PURSUIT_WARP_CAP = 30.0

    val WARP_FACTORS = doubleArrayOf(1.0, 10.0, 100.0, 1000.0, 5000.0)
}
