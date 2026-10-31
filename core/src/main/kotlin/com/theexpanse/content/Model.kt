package com.theexpanse.content

/** Tipos de corpo no mapa. Estação e destroço podem permitir atracação; nave é só um ponto móvel. */
enum class BodyKind(val key: String) { STATION("estacao"), SHIP("nave"), WRECK("destroco") }

class FactionDef(val id: String, val name: String, val argb: Int)

class PlanetDef(val name: String, val gm: Double, val radius: Double)

class BodyDef(
    val id: String, val name: String, val shortName: String, val factionId: String,
    val kind: BodyKind, val orbitRadius: Double, val phase: Double, val size: Double, val dockable: Boolean,
)

class ShipDef(
    val name: String, val dryMass: Double, val cargoMass: Double, val fuelMass: Double,
    val startNear: String, val startAltitude: Double, val targetId: String?, val cargoName: String,
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
class SuspectDef(val id: String, val name: String, val text: String)
class AccusationDef(val id: String, val suspectId: String, val condition: Cond?, val effects: List<Effect>)

enum class EndingKind(val key: String) { WIN("vitoria"), LOSS("derrota"), NEUTRAL("neutro") }
class EndingDef(val id: String, val title: String, val headline: String, val kind: EndingKind)

class ContentPackage(
    val format: Int, val id: String, val title: String, val summary: String, val author: String,
    val planet: PlanetDef, val factions: List<FactionDef>, val bodies: List<BodyDef>, val ship: ShipDef,
    val credits: Int, val reputation: Map<String, Int>,
    val objects: List<ObjectDef>, val facts: List<FactDef>, val characters: List<CharacterDef>,
    val dialogues: List<DialogueDef>, val rules: List<RuleDef>, val deductions: List<DeductionDef>,
    val missions: List<MissionDef>, val suspects: List<SuspectDef>, val accusations: List<AccusationDef>,
    val endings: List<EndingDef>, val news: List<String>,
) {
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
}

sealed interface Cond {
    class HasObject(val obj: String) : Cond
    class Knows(val fact: String) : Cond
    class Flag(val name: String, val value: Boolean) : Cond
    class Rep(val faction: String, val min: Int?, val max: Int?) : Cond
    class Credits(val min: Int) : Cond
    class Deduced(val id: String) : Cond
    object HasCargo : Cond
    class MissionIs(val id: String, val state: String) : Cond
    class All(val items: List<Cond>) : Cond
    class Any(val items: List<Cond>) : Cond
    class Not(val item: Cond) : Cond
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
    class Ai(val body: String, val mode: AiMode) : Effect
    class SetTarget(val body: String) : Effect
    object LoseCargo : Effect
    class Mission(val id: String, val state: String) : Effect
    class Roll(val chance: Chance, val success: List<Effect>, val failure: List<Effect>) : Effect
}

enum class AiMode(val key: String) { PURSUE("perseguir"), ORBIT("orbitar") }
