package com.theexpanse.content

/** Tipos de corpo no mapa. Estação e destroço podem permitir atracação; nave é só um ponto móvel. */
enum class BodyKind(val key: String) { STATION("estacao"), SHIP("nave"), WRECK("destroco"), PLANET("planeta") }

/** Desenho do casco dos cruzadores de uma facção. */
enum class HullStyle(val key: String) { TERRAN("terrano"), MARTIAN("marciano"), BELT("cinturao"), GENERIC("generico") }

class FactionDef(val id: String, val name: String, val argb: Int, val style: HullStyle = HullStyle.GENERIC)

/** O papel do jogador, fixo na campanha inteira: mercador independente ou comandante de um cruzador de uma facção. */
enum class RoleKind(val key: String) { MERCHANT("mercador"), MILITARY("militar") }

class RoleDef(val kind: RoleKind, val factionId: String?)

/** O corpo central do sistema (um planeta fictício ou o Sol). [star] desenha uma estrela em vez de um planeta. */
class PlanetDef(val name: String, val gm: Double, val radius: Double, val star: Boolean = false)

class BodyDef(
    val id: String, val name: String, val shortName: String, val factionId: String,
    val kind: BodyKind, val orbitRadius: Double, val phase: Double, val size: Double, val dockable: Boolean,
    /** Armas de uma nave hostil: mísseis, despistadores e a chance de cada despistador funcionar. */
    val missiles: Int = 4, val decoys: Int = 2, val decoyChance: Double = 0.4,
    /** Órbita kepleriana (2D) em torno do [parentId] (ou do corpo central): excentricidade e argumento do periapse. */
    val parentId: String? = null, val eccentricity: Double = 0.0, val periapsis: Double = 0.0,
    /** Massa (GM) e raio do corpo: planetas e luas puxam a nave e destroem quem encosta. */
    val gm: Double = 0.0, val radius: Double = 0.0, val colorArgb: Int? = null,
    /** Escondido até um efeito revelá-lo; resistência a abordagens (0 a 1); chance de uma escolta interceptar um míssil. */
    val hidden: Boolean = false, val defense: Double = 0.4, val interceptChance: Double = 0.5,
    /** Aceleração máxima de uma nave controlada pelo computador (m/s²). */
    val maxAccel: Double = 3.0,
)

class ShipDef(
    val name: String, val dryMass: Double, val cargoMass: Double, val fuelMass: Double,
    val startNear: String, val startAltitude: Double, val targetId: String?, val cargoName: String,
    val hull: Double = 100.0, val missiles: Int = 0, val decoys: Int = 4, val decoyChance: Double = 0.5,
    val maxAccel: Double = 6.0, val exhaustVelocity: Double = 8.0e5,
)

class ObjectDef(val id: String, val name: String, val mass: Double)
class FactDef(val id: String, val text: String)

class CharacterDef(
    val id: String, val name: String, val role: String, val factionId: String?,
    val location: String?, val dialogue: String?, val condition: Cond?,
)

class DialogueDef(val id: String, val characterId: String?, val text: String, val choices: List<ChoiceDef>)

class ChoiceDef(
    val text: String, val detail: String?, val condition: Cond?, val blockedText: String?,
    val effects: List<Effect>, val next: String?,
)

class RuleDef(val id: String, val trigger: Trigger, val condition: Cond?, val effects: List<Effect>, val once: Boolean)
class DeductionDef(val id: String, val requires: List<String>, val text: String, val effects: List<Effect>, val auto: Boolean)
class MissionDef(val id: String, val title: String, val text: String, val initial: Boolean)
/** Sequência de efeitos nomeada: pode ser executada ou agendada por id (e por isso sobrevive ao salvamento). */
class BlockDef(val id: String, val effects: List<Effect>)
class SuspectDef(val id: String, val name: String, val text: String)
class AccusationDef(val id: String, val suspectId: String, val condition: Cond?, val effects: List<Effect>)

enum class EndingKind(val key: String) { WIN("vitoria"), LOSS("derrota"), NEUTRAL("neutro") }
class EndingDef(val id: String, val title: String, val headline: String, val kind: EndingKind)

class ContentPackage(
    val format: Int, val id: String, val title: String, val summary: String, val author: String,
    val role: RoleDef, val timeScale: Double,
    val planet: PlanetDef, val factions: List<FactionDef>, val bodies: List<BodyDef>, val ship: ShipDef,
    val credits: Int, val reputation: Map<String, Int>,
    val objects: List<ObjectDef>, val facts: List<FactDef>, val characters: List<CharacterDef>,
    val dialogues: List<DialogueDef>, val rules: List<RuleDef>, val deductions: List<DeductionDef>,
    val missions: List<MissionDef>, val suspects: List<SuspectDef>, val accusations: List<AccusationDef>,
    val endings: List<EndingDef>, val news: List<String>, val blocks: List<BlockDef> = emptyList(),
) {
    fun block(id: String) = blocks.firstOrNull { it.id == id }
    fun faction(id: String) = factions.firstOrNull { it.id == id }
    fun body(id: String) = bodies.firstOrNull { it.id == id }
    fun dialogue(id: String) = dialogues.firstOrNull { it.id == id }
    fun ending(id: String) = endings.firstOrNull { it.id == id }
    fun obj(id: String) = objects.firstOrNull { it.id == id }
    fun fact(id: String) = facts.firstOrNull { it.id == id }
    fun character(id: String) = characters.firstOrNull { it.id == id }
}

/** Gatilhos: quando uma regra é avaliada. */
sealed interface Trigger {
    object Start : Trigger
    class Dock(val place: String) : Trigger
    /** [from] é "jogador" ou o id de um corpo; dispara quando a distância cruza para dentro do limite. */
    class Distance(val from: String, val to: String, val below: Double?, val above: Double?) : Trigger
    class Time(val after: Double) : Trigger
    class DialogueEnd(val dialogue: String) : Trigger
    class FactRevealed(val fact: String) : Trigger
    class Approach(val body: String, val distBelow: Double, val speedBelow: Double) : Trigger
    object Destroyed : Trigger
    class BodyDestroyed(val body: String) : Trigger
    /** O jogador foi abordado por uma nave em modo abordar. */
    class Boarded(val body: String?) : Trigger
    /** O jogador tentou abordar uma nave; [success] null = qualquer resultado. */
    class BoardingResult(val body: String?, val success: Boolean?) : Trigger
    class HullBelow(val pct: Double) : Trigger
    class FuelBelow(val pct: Double) : Trigger
    /** Periódico: dispara a cada [seconds] de missão. */
    class Every(val seconds: Double) : Trigger
    class Reputation(val faction: String, val min: Int?, val max: Int?) : Trigger
    class ObjectGained(val obj: String) : Trigger
    class DeductionMade(val id: String) : Trigger
    class MissionChanged(val id: String, val state: String?) : Trigger
    object EscortIntercepted : Trigger
    /** Um míssil foi lançado: [by] é "inimigo", "jogador" ou null (qualquer). */
    class MissileLaunched(val by: String?) : Trigger
}

sealed interface Cond {
    class HasObject(val obj: String) : Cond
    class Knows(val fact: String) : Cond
    class Flag(val name: String, val value: Boolean) : Cond
    class Rep(val faction: String, val min: Int?, val max: Int?) : Cond
    class Credits(val min: Int) : Cond
    class Deduced(val id: String) : Cond
    object HasCargo : Cond
    class Role(val kind: RoleKind) : Cond
    class MissionIs(val id: String, val state: String) : Cond
    class All(val items: List<Cond>) : Cond
    class Any(val items: List<Cond>) : Cond
    class Not(val item: Cond) : Cond
    class Counter(val name: String, val min: Int?, val max: Int?) : Cond
    class Hull(val min: Double?, val max: Double?) : Cond
    class Fuel(val min: Double?, val max: Double?) : Cond
    class DockedAt(val place: String?) : Cond
    class BodyAlive(val body: String, val alive: Boolean) : Cond
    class Distance(val from: String, val to: String, val below: Double?, val above: Double?) : Cond
    class TimeAfter(val seconds: Double) : Cond
    class Random(val chance: Double) : Cond
    class TargetIs(val body: String) : Cond
}

/** Chance de sucesso de um sorteio: base + reputação × por, limitada a [min, max]. */
class Chance(val base: Double, val faction: String?, val per: Double, val min: Double, val max: Double)

sealed interface Effect {
    class Message(val text: String) : Effect
    class Rep(val faction: String, val delta: Int) : Effect
    class Credits(val delta: Int) : Effect
    class GiveObject(val obj: String) : Effect
    class TakeObject(val obj: String) : Effect
    class Reveal(val fact: String) : Effect
    class SetFlag(val name: String, val value: Boolean) : Effect
    class OpenDialogue(val id: String) : Effect
    class News(val text: String) : Effect
    class End(val id: String) : Effect
    class Ai(val body: String, val mode: AiMode, val route: List<String> = emptyList()) : Effect
    class SetTarget(val body: String) : Effect
    object LoseCargo : Effect
    class Mission(val id: String, val state: String) : Effect
    class Roll(val chance: Chance, val success: List<Effect>, val failure: List<Effect>) : Effect
    /** Contador numérico da história: soma [delta] ou, se [set] não for nulo, define o valor. */
    class Counter(val name: String, val delta: Int, val set: Int?) : Effect
    class If(val cond: Cond, val then: List<Effect>, val otherwise: List<Effect>) : Effect
    class Run(val block: String) : Effect
    class Schedule(val block: String, val seconds: Double) : Effect
    /** Faz uma nave surgir perto do jogador (a [distance] m, mesma velocidade). */
    class Ambush(val body: String, val distance: Double) : Effect
    class RevealBody(val body: String) : Effect
    class ConcealBody(val body: String) : Effect
    class DestroyBody(val body: String, val explode: Boolean) : Effect
    /** Muda o casco ou o combustível em % da capacidade. */
    class Hull(val deltaPct: Double) : Effect
    class Fuel(val deltaPct: Double) : Effect
    class Ammo(val missiles: Int, val decoys: Int) : Effect
}

/** Comportamento de uma nave: perseguir, atacar (persegue e dispara mísseis) ou orbitar (deriva sem empuxo). */
enum class AiMode(val key: String) {
    PURSUE("perseguir"), ATTACK("atacar"), ORBIT("orbitar"), ESCORT("escoltar"), BOARD("abordar"),
    /** Percorre, em ciclo, uma rota de corpos (`rota`). */
    PATROL("patrulhar"),
    /** Foge do jogador, acelerando no sentido oposto. */
    FLEE("fugir");

    /** Aponta a proa para o jogador (os demais voam na direção do próprio movimento). */
    val facesPlayer: Boolean get() = this == PURSUE || this == ATTACK || this == BOARD || this == ESCORT
}
