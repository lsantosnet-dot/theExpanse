package com.theexpanse.core



enum class Owner { PLAYER, ENEMY }

/** Míssil guiado. [targetId] é "jogador", o id de um corpo ou o id de um despistador. */
class Missile(val id: Int, val owner: Owner, var pos: Vec2, var vel: Vec2, var targetId: String, val launcher: String) {
    var age = 0.0
    /** Instante (idade) em que um despistador o engana, sorteado no lançamento; null = não será enganado. */
    var decoyAt: Double? = null
    /** Já foi testado por uma escolta (uma chance de interceptação por míssil). */
    var escortRolled = false
}

/** Despistador lançado: um ponto quente que flutua e atrai mísseis. */
class Flare(val id: String, var pos: Vec2, var vel: Vec2, val born: Double)

enum class BlastKind { SHIP_HIT, KILL, FLARE, LAUNCH, FIZZLE, INTERCEPT }

/** Explosão para animar; guarda o instante de simulação em que aconteceu. */
class Blast(val pos: Vec2, val time: Double, val kind: BlastKind)

sealed interface SimEvent {
    class Docked(val body: Body) : SimEvent
    object Crashed : SimEvent
    class ShipDestroyed(val byMissile: Boolean) : SimEvent
    class BodyDestroyed(val id: String) : SimEvent
    class Hit(val damage: Double) : SimEvent
    class Launched(val owner: Owner, val launcher: String) : SimEvent
    /** [chance] é a média das chances de cada míssil no instante do lançamento. */
    class DecoyResult(val diverted: Int, val total: Int, val chance: Double = 0.0) : SimEvent
    /** Uma nave hostil em modo abordar chegou ao jogador. */
    class Boarded(val by: String) : SimEvent
    /** O jogador tentou abordar [id] (só no papel militar). */
    class BoardingResult(val id: String, val success: Boolean) : SimEvent
    class EscortIntercepted(val escort: String) : SimEvent
}

enum class FireResult { FIRED, NOT_MILITARY, NO_AMMO, NO_TARGET, OUT_OF_RANGE, FRIENDLY }

enum class BoardCheck { OK, NOT_MILITARY, NO_TARGET, TOO_FAR, TOO_FAST, FRIENDLY }

/** Estado de armas de uma nave controlada pelo computador. */
class NpcArms(var missiles: Int, var decoys: Int, var cooldown: Double)

/**
 * Quanto o momento do lançamento multiplica a chance base do despistador, conforme os segundos que faltam para o
 * míssil chegar. Cedo demais (míssil longe, o buscador ainda reencontra a nave): vale menos. Na janela de 20 a 45 s
 * o despistador e a nave ainda cabem juntos no campo do buscador e ele segue o mais quente: melhor chance. Em cima
 * da hora o míssil já não vê o despistador a tempo.
 */
fun decoyTiming(eta: Double): Double = when {
    eta <= 5.0 -> 0.3
    eta <= 15.0 -> 0.3 + (eta - 5.0) / 10.0 * 0.9
    eta <= 20.0 -> 1.2 + (eta - 15.0) / 5.0 * 0.4
    eta <= 45.0 -> 1.6
    eta <= 70.0 -> 1.6 - (eta - 45.0) / 25.0 * 0.7
    else -> 0.9
}

/** Aceleração de um míssil por navegação proporcional, com compensação da gravidade. */
fun missileGuidance(world: World, t: Double, pos: Vec2, vel: Vec2, tPos: Vec2, tVel: Vec2): Vec2 {
    val r = tPos - pos
    val d = r.length
    if (d < 1.0) return Vec2.ZERO
    val vr = tVel - vel
    val rhat = r / d
    val closing = -(r dot vr) / d
    val omega = (r.x * vr.y - r.y * vr.x) / (d * d)
    val perp = Vec2(-rhat.y, rhat.x)
    var a = perp * (4.0 * maxOf(closing, 100.0) * omega) + rhat * (Config.MISSILE_ACCEL * 0.6) - (world.gravity(pos, t) - world.gravity(tPos, t))
    val m = a.length
    if (m > Config.MISSILE_ACCEL) a = a * (Config.MISSILE_ACCEL / m)
    return a
}

/** Menor distância entre a origem e o segmento a→b (varredura contra "atravessar" o alvo num passo). */
fun segmentMissDistance(a: Vec2, b: Vec2): Double {
    val ab = b - a
    val l2 = ab dot ab
    if (l2 < 1e-9) return a.length
    val t = (-(a dot ab) / l2).coerceIn(0.0, 1.0)
    return (a + ab * t).length
}

