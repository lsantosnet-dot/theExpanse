package com.theexpanse.core

import com.theexpanse.content.BodyDef
import com.theexpanse.content.BodyKind
import com.theexpanse.content.ContentPackage
import com.theexpanse.content.FactionDef
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Corpo em órbita kepleriana (plana) em torno do corpo central ou de outro corpo (uma lua, uma estação). A posição
 * é analítica em qualquer t: resolve a equação de Kepler, sem integrar. Para órbitas circulares reduz ao movimento
 * uniforme de sempre.
 */
class Body(val def: BodyDef, val faction: FactionDef, val parent: Body?, parentGm: Double) {
    val id: String get() = def.id
    val name: String get() = def.name
    val shortName: String get() = def.shortName
    val kind: BodyKind get() = def.kind
    val dockable: Boolean get() = def.dockable
    val size: Double get() = def.size
    val orbitRadius: Double get() = def.orbitRadius
    val radius: Double get() = def.radius
    val argb: Int get() = def.colorArgb ?: faction.argb

    private val e = def.eccentricity
    private val w = def.periapsis

    /** Movimento médio (rad/s). */
    val meanMotion: Double = sqrt(parentGm / (orbitRadius * orbitRadius * orbitRadius))
    val period: Double = 2 * PI / meanMotion

    private var cacheT = Double.NaN
    private var cachePos = Vec2.ZERO
    private var cacheVel = Vec2.ZERO

    private fun solve(t: Double) {
        if (t == cacheT) return
        val m = def.phase + meanMotion * t
        var ea = m % (2 * PI)
        if (e > 0.0) repeat(8) { ea -= (ea - e * sin(ea) - (m % (2 * PI))) / (1 - e * cos(ea)) }
        val ce = cos(ea)
        val se = sin(ea)
        val b = sqrt(1 - e * e)
        val px = orbitRadius * (ce - e)
        val py = orbitRadius * b * se
        val k = orbitRadius * meanMotion / (1 - e * ce)
        val vx = -k * se
        val vy = k * b * ce
        val cw = cos(w)
        val sw = sin(w)
        var pos = Vec2(px * cw - py * sw, px * sw + py * cw)
        var vel = Vec2(vx * cw - vy * sw, vx * sw + vy * cw)
        if (parent != null) {
            pos += parent.position(t)
            vel += parent.velocity(t)
        }
        cacheT = t; cachePos = pos; cacheVel = vel
    }

    fun position(t: Double): Vec2 { solve(t); return cachePos }
    fun velocity(t: Double): Vec2 { solve(t); return cacheVel }
}

/** O sistema físico de uma campanha: corpo central, corpos em órbita e a nave inicial, tudo vindo do pacote. */
class World(val pack: ContentPackage) {
    val planetName: String = pack.planet.name
    val gm: Double = pack.planet.gm
    val radius: Double = pack.planet.radius
    val star: Boolean = pack.planet.star
    val ship = pack.ship
    val role = pack.role
    val timeScale: Double = pack.timeScale

    /** Níveis do relógio: ×1, ×10, ×100, a escala de cruzeiro da campanha e cinco vezes ela. */
    val warpLevels: DoubleArray = doubleArrayOf(1.0, 10.0, 100.0, pack.timeScale, pack.timeScale * 5)

    val isMilitary: Boolean get() = role.kind == com.theexpanse.content.RoleKind.MILITARY
    val playerFaction: FactionDef? get() = role.factionId?.let { pack.faction(it) }

    val bodies: List<Body>

    /** Corpos que puxam a nave (têm GM) e corpos sólidos que a destroem ao encostar (têm raio). */
    val massive: List<Body>
    val solid: List<Body>

    /** Distância máxima típica de um corpo ao centro (m): serve para escalar mapas e vistas. */
    val extent: Double

    init {
        val byId = HashMap<String, Body>()
        fun build(d: BodyDef): Body = byId.getOrPut(d.id) {
            val parent = d.parentId?.let { pid -> pack.body(pid)?.let { build(it) } }
            Body(d, pack.faction(d.factionId)!!, parent, parent?.def?.gm ?: gm)
        }
        bodies = pack.bodies.map { build(it) }
        massive = bodies.filter { it.def.gm > 0.0 }
        solid = bodies.filter { it.def.radius > 0.0 && it.kind == BodyKind.PLANET }
        fun apo(b: Body): Double = b.orbitRadius * (1 + b.def.eccentricity) + (b.parent?.let { apo(it) } ?: 0.0)
        extent = (bodies.maxOfOrNull { apo(it) } ?: radius * 10) * 1.15
    }

    fun body(id: String): Body? = bodies.firstOrNull { it.id == id }

    /** Gravidade no ponto [p] no instante [t]: o corpo central mais todos os corpos com massa. */
    fun gravity(p: Vec2, t: Double): Vec2 {
        val r = p.length
        var g = p * (-gm / (r * r * r))
        for (b in massive) {
            val d = b.position(t) - p
            val dist = maxOf(d.length, b.def.radius, 1.0)
            g += d * (b.def.gm / (dist * dist * dist))
        }
        return g
    }

    /** Algum corpo sólido (ou o central) engole este ponto? Devolve o nome do que foi atingido. */
    fun collision(p: Vec2, t: Double): String? {
        if (p.length < radius) return planetName
        for (b in solid) if ((p - b.position(t)).length < b.def.radius) return b.name
        return null
    }

}
