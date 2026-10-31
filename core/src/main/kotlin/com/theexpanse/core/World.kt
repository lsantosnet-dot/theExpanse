package com.theexpanse.core

import com.theexpanse.content.BodyDef
import com.theexpanse.content.BodyKind
import com.theexpanse.content.ContentPackage
import com.theexpanse.content.FactionDef
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Corpo em órbita circular analítica ao redor da origem (o planeta): posição exata em qualquer t, sem integrar. */
class Body(val def: BodyDef, val faction: FactionDef, gm: Double) {
    val id: String get() = def.id
    val name: String get() = def.name
    val shortName: String get() = def.shortName
    val kind: BodyKind get() = def.kind
    val dockable: Boolean get() = def.dockable
    val size: Double get() = def.size
    val orbitRadius: Double get() = def.orbitRadius

    /** Movimento médio (rad/s). */
    val meanMotion: Double = sqrt(gm / (orbitRadius * orbitRadius * orbitRadius))
    val period: Double = 2 * PI / meanMotion

    fun position(t: Double): Vec2 {
        val a = def.phase + meanMotion * t
        return Vec2(orbitRadius * cos(a), orbitRadius * sin(a))
    }

    fun velocity(t: Double): Vec2 {
        val a = def.phase + meanMotion * t
        val v = orbitRadius * meanMotion
        return Vec2(-v * sin(a), v * cos(a))
    }
}

/** O sistema físico de uma campanha: planeta, corpos e a nave inicial, tudo vindo do pacote. */
class World(val pack: ContentPackage) {
    val planetName: String = pack.planet.name
    val gm: Double = pack.planet.gm
    val radius: Double = pack.planet.radius
    val bodies: List<Body> = pack.bodies.map { d -> Body(d, pack.faction(d.factionId)!!, gm) }
    val ship = pack.ship

    fun body(id: String): Body? = bodies.firstOrNull { it.id == id }

    /** Gravidade do planeta no ponto [p]. */
    fun gravity(p: Vec2): Vec2 {
        val r = p.length
        return p * (-gm / (r * r * r))
    }
}
