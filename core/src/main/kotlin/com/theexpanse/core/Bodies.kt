package com.theexpanse.core

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

enum class Faction(val label: String, val argb: Int) {
    COALITION("Coalizão Terrana", 0xFF5B9BF0.toInt()),
    FEDERATION("Federação Marciana", 0xFFE8645A.toInt()),
    LEAGUE("Liga do Cinturão", 0xFF3FD0B8.toInt()),
}

enum class BodyKind { STATION, SHIP }

/** Corpo em órbita circular analítica ao redor da origem (o planeta): sem integração, posição exata em qualquer t. */
class Body(
    val id: String,
    val name: String,
    val shortName: String,
    val faction: Faction,
    val kind: BodyKind,
    val orbitRadius: Double,
    val phase: Double,
    val size: Double,
) {
    /** Movimento médio (rad/s). */
    val meanMotion: Double = sqrt(Config.GM / (orbitRadius * orbitRadius * orbitRadius))
    val period: Double = 2 * PI / meanMotion

    fun position(t: Double): Vec2 {
        val a = phase + meanMotion * t
        return Vec2(orbitRadius * cos(a), orbitRadius * sin(a))
    }

    fun velocity(t: Double): Vec2 {
        val a = phase + meanMotion * t
        val v = orbitRadius * meanMotion
        return Vec2(-v * sin(a), v * cos(a))
    }
}

/** Gravidade do planeta no ponto [p]. */
fun gravityAt(p: Vec2): Vec2 {
    val r = p.length
    return p * (-Config.GM / (r * r * r))
}

/** O sistema da primeira campanha. Os dados definitivos virão de pacotes JSON; este é o conteúdo embutido. */
object Calice {
    const val PLANET_NAME = "Aurélia"

    val dock = Body("doca_meridiano", "Doca Meridiano", "Doca", Faction.COALITION, BodyKind.STATION, 3.2e8, 0.0, Config.STATION_SIZE)
    val port = Body("porto_kaua", "Porto Kauã", "Porto", Faction.LEAGUE, BodyKind.STATION, 4.6e8, 1.40, Config.STATION_SIZE)
    val arsenal = Body("arsenal_ares", "Arsenal Ares", "Arsenal", Faction.FEDERATION, BodyKind.STATION, 6.1e8, 3.30, Config.STATION_SIZE)
    val frigate = Body("fragata_tribunal", "Fragata Tribunal", "Fragata", Faction.COALITION, BodyKind.SHIP, 4.52e8, 1.46, Config.FRIGATE_SIZE)

    val bodies: List<Body> = listOf(port, dock, arsenal, frigate)
    const val CONTRACT = "Contrato: levar 40 t de filtros de água ao Porto Kauã."
}
