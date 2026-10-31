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
    const val PATROL_STOP = 50_000.0
    const val PATROL_ARRIVE_SPEED = 200.0
    /** Distância do jogador abaixo da qual uma patrulha ou fuga limita o tempo acelerado. */
    const val AI_WARP_RADIUS = 1_000_000.0
    /** Segundos reais que o jogador deve ter, no mínimo, entre o aviso de colisão e o impacto. */
    const val COLLISION_REACTION = 60.0
    const val PURSUIT_WARP_CAP = 120.0
    const val ATTACK_WARP_CAP = 60.0
    /** Até esta distância (m) de uma nave hostil vale o teto fixo; além dela o teto cresce na proporção da distância. */
    const val HOSTILE_CAP_RANGE = 200_000.0

    /** Combate: mísseis guiados, despistadores e dano. */
    const val MISSILE_ACCEL = 80.0
    const val MISSILE_LIFE = 70.0
    const val MISSILE_FUZE = 600.0
    const val MISSILE_RANGE = 150_000.0
    const val ENEMY_FIRE_RANGE = 80_000.0
    const val ENEMY_COOLDOWN = 30.0
    /** Máximo de mísseis inimigos em voo contra o jogador, e quanto cada atacante extra alonga o intervalo de tiro. */
    const val MAX_INCOMING = 3
    const val ATTACKER_CROWDING = 0.5
    const val ATTACK_STOP = 40_000.0
    const val ESCORT_DISTANCE = 2_500.0
    const val ESCORT_INTERCEPT_RANGE = 8_000.0
    const val BOARD_STOP = 150.0
    const val BOARD_RANGE = 1_000.0
    const val BOARD_SPEED = 12.0
    const val MISSILE_DAMAGE_MIN = 45.0
    const val MISSILE_DAMAGE_MAX = 80.0
}
