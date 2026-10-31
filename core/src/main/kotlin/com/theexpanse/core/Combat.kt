package com.theexpanse.core



enum class Owner { PLAYER, ENEMY }

/** Míssil guiado. [targetId] é "jogador", o id de um corpo ou o id de um despistador. */
class Missile(val id: Int, val owner: Owner, var pos: Vec2, var vel: Vec2, var targetId: String, val launcher: String) {
    var age = 0.0
    /** Instante (idade) em que um despistador o engana, sorteado no lançamento; null = não será enganado. */
    var decoyAt: Double? = null
}

/** Despistador lançado: um ponto quente que flutua e atrai mísseis. */
class Flare(val id: String, var pos: Vec2, var vel: Vec2, val born: Double)

enum class BlastKind { SHIP_HIT, KILL, FLARE, LAUNCH, FIZZLE }

/** Explosão para animar; guarda o instante de simulação em que aconteceu. */
class Blast(val pos: Vec2, val time: Double, val kind: BlastKind)

sealed interface SimEvent {
    class Docked(val body: Body) : SimEvent
    object Crashed : SimEvent
    class ShipDestroyed(val byMissile: Boolean) : SimEvent
    class BodyDestroyed(val id: String) : SimEvent
    class Hit(val damage: Double) : SimEvent
    class Launched(val owner: Owner, val launcher: String) : SimEvent
    class DecoyResult(val diverted: Int, val total: Int) : SimEvent
}

enum class FireResult { FIRED, NOT_MILITARY, NO_AMMO, NO_TARGET, OUT_OF_RANGE, FRIENDLY }

/** Estado de armas de uma nave controlada pelo computador. */
class NpcArms(var missiles: Int, var decoys: Int, var cooldown: Double)

/** Aceleração de um míssil por navegação proporcional, com compensação da gravidade. */
fun missileGuidance(world: World, pos: Vec2, vel: Vec2, tPos: Vec2, tVel: Vec2): Vec2 {
    val r = tPos - pos
    val d = r.length
    if (d < 1.0) return Vec2.ZERO
    val vr = tVel - vel
    val rhat = r / d
    val closing = -(r dot vr) / d
    val omega = (r.x * vr.y - r.y * vr.x) / (d * d)
    val perp = Vec2(-rhat.y, rhat.x)
    var a = perp * (4.0 * maxOf(closing, 100.0) * omega) + rhat * (Config.MISSILE_ACCEL * 0.6) - (world.gravity(pos) - world.gravity(tPos))
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

