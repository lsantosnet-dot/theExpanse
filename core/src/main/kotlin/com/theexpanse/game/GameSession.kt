package com.theexpanse.game

import com.theexpanse.content.ChoiceDef
import com.theexpanse.content.Chance
import com.theexpanse.content.Cond
import com.theexpanse.content.ContentPackage
import com.theexpanse.content.DialogueDef
import com.theexpanse.content.Effect
import com.theexpanse.content.EndingDef
import com.theexpanse.content.RuleDef
import com.theexpanse.content.Trigger
import com.theexpanse.core.Body
import com.theexpanse.core.FireResult
import com.theexpanse.core.Owner
import com.theexpanse.core.SimEvent
import com.theexpanse.core.Simulation
import com.theexpanse.core.Status
import com.theexpanse.core.World
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put


class Message(val text: String, val at: Double)

/** Uma escolha como o jogador a vê. */
class ChoiceView(val index: Int, val text: String, val detail: String?, val enabled: Boolean, val blocked: String?)

sealed interface GameEvent {
    object Start : GameEvent
    class Dock(val body: Body) : GameEvent
    object Destroyed : GameEvent
    class DialogueEnd(val id: String) : GameEvent
    class FactRevealed(val id: String) : GameEvent
    class BodyDestroyed(val id: String) : GameEvent
}

/** Estado da história: o que o jogador sabe, tem, fez e quanto vale cada coisa. */
class StoryState(pack: ContentPackage, seed: Long) {
    val facts = LinkedHashSet<String>()
    val objects = LinkedHashSet<String>()
    val flags = HashMap<String, Boolean>()
    val deduced = LinkedHashSet<String>()
    val missions = LinkedHashMap<String, String>()
    val news = ArrayList<String>()
    val reputation = HashMap<String, Int>()
    val startReputation = HashMap<String, Int>()
    var credits = pack.credits
    val fired = HashSet<String>()
    val armed = HashMap<String, Boolean>()
    var ending: String? = null
    var continued = false
    var dialogue: String? = null
    val pendingDialogues = ArrayList<String>()
    var rng: Long = seed

    init {
        for (f in pack.factions) {
            val v = pack.reputation[f.id] ?: 0
            reputation[f.id] = v
            startReputation[f.id] = v
        }
        for (m in pack.missions) if (m.initial) missions[m.id] = "ativa"
        news.addAll(pack.news)
    }

    fun nextDouble(): Double {
        rng += -7046029254386353131L
        var z = rng
        z = (z xor (z ushr 30)) * -4658895280553007687L
        z = (z xor (z ushr 27)) * -7723592293110705685L
        z = z xor (z ushr 31)
        return (z ushr 11).toDouble() / (1L shl 53).toDouble()
    }
}

/** Liga a simulação física ao motor de história: avalia gatilhos, executa efeitos e conduz diálogos. */
class GameSession(val pack: ContentPackage, private val seed: Long = System.nanoTime()) {
    val world = World(pack)
    val sim = Simulation(world, seed xor 0x5DEECE66DL)
    var story = StoryState(pack, seed)
        private set

    /** Relógio de parede acumulado (para o registro de mensagens sumir sozinho). */
    var clock = 0.0
        private set
    val messages = ArrayList<Message>()
    private val queue = ArrayDeque<GameEvent>()

    init {
        queue.add(GameEvent.Start)
        pump()
    }

    fun restart() {
        sim.reset()
        sim.seedRandom(seed xor 0x5DEECE66DL)
        story = StoryState(pack, seed)
        messages.clear()
        queue.clear()
        queue.add(GameEvent.Start)
        pump()
    }

    val ended: Boolean get() = story.ending != null
    val ending: EndingDef? get() = story.ending?.let { pack.ending(it) }

    /** O jogo está em pausa: diálogo aberto ou final ainda não dispensado. */
    val paused: Boolean get() = story.dialogue != null || (ended && !story.continued)

    fun update(realDt: Double) {
        clock += realDt
        if (paused) return
        sim.advance(realDt)
        drainSim()
        pump()
    }

    /** Avança a simulação em passos fixos (para testes e planejamento), sem relógio de parede. */
    fun stepFixed(h: Double) {
        if (paused) return
        sim.step(h)
        drainSim()
        pump()
    }

    /** Converte os eventos da simulação em mensagens e em eventos de história. */
    private fun drainSim() {
        for (e in sim.drainEvents()) when (e) {
            is SimEvent.Docked -> queue.add(GameEvent.Dock(e.body))
            SimEvent.Crashed -> queue.add(GameEvent.Destroyed)
            is SimEvent.ShipDestroyed -> { addMessage("A nave foi destruída."); queue.add(GameEvent.Destroyed) }
            is SimEvent.BodyDestroyed -> {
                addMessage("${world.body(e.id)?.name ?: e.id} foi destruída.")
                queue.add(GameEvent.BodyDestroyed(e.id))
            }
            is SimEvent.Hit -> addMessage("IMPACTO! Casco em ${sim.ship.hull.toInt() * 100 / sim.ship.hullMax.toInt().coerceAtLeast(1)}%.")
            is SimEvent.Launched ->
                if (e.owner == Owner.ENEMY) addMessage("ALERTA: míssil lançado por ${world.body(e.launcher)?.name ?: e.launcher}!")
                else addMessage("Míssil disparado.")
            is SimEvent.DecoyResult ->
                addMessage(if (e.diverted == e.total) "Despistador funcionou: ${e.diverted} míssil(is) desviado(s)!"
                else if (e.diverted == 0) "O despistador falhou! O míssil continua vindo."
                else "Despistador desviou ${e.diverted} de ${e.total} mísseis.")
        }
    }

    /** Dispara um míssil no alvo selecionado (só no papel militar). */
    fun fire() {
        when (sim.fireMissile()) {
            FireResult.FIRED -> {}
            FireResult.NOT_MILITARY -> addMessage("Você é um mercador: sem armas.")
            FireResult.NO_AMMO -> addMessage("Sem mísseis.")
            FireResult.NO_TARGET -> addMessage("Selecione uma nave como alvo.")
            FireResult.OUT_OF_RANGE -> addMessage("Alvo fora do alcance (150 km).")
            FireResult.FRIENDLY -> addMessage("Fogo amigo bloqueado.")
        }
        drainSim()
        pump()
    }

    /** Lança um despistador contra os mísseis que vêm na sua direção. */
    fun decoy() {
        if (!sim.deployDecoy()) addMessage(if (sim.ship.decoys <= 0) "Sem despistadores." else "Nenhum míssil a despistar.")
        drainSim()
    }

    // ---- gatilhos ----

    private fun pump() {
        var guard = 0
        while (guard++ < 1000) {
            val ev = queue.removeFirstOrNull()
            if (ev != null) {
                for (r in pack.rules) if (matches(r.trigger, ev)) tryFire(r, true)
                continue
            }
            var fired = false
            for (r in pack.rules) if (pollRule(r)) fired = true
            if (!fired) break
        }
        runAutoDeductions()
    }

    private fun matches(t: Trigger, ev: GameEvent): Boolean = when (t) {
        Trigger.Start -> ev is GameEvent.Start
        is Trigger.Dock -> ev is GameEvent.Dock && ev.body.id == t.place
        Trigger.Destroyed -> ev is GameEvent.Destroyed
        is Trigger.BodyDestroyed -> ev is GameEvent.BodyDestroyed && ev.id == t.body
        is Trigger.DialogueEnd -> ev is GameEvent.DialogueEnd && ev.id == t.dialogue
        is Trigger.FactRevealed -> ev is GameEvent.FactRevealed && ev.id == t.fact
        else -> false
    }

    private fun tryFire(r: RuleDef, event: Boolean): Boolean {
        if (r.once && r.id in story.fired) return false
        if (r.condition != null && !eval(r.condition)) return false
        story.fired.add(r.id)
        runEffects(r.effects)
        return true
    }

    /** Gatilhos de estado (distância, tempo, abordagem): disparam quando passam a valer. */
    private fun pollRule(r: RuleDef): Boolean {
        val t = r.trigger
        val active = when (t) {
            is Trigger.Time -> sim.time >= t.after
            is Trigger.Distance -> {
                val a = if (t.from == "jogador") sim.ship.pos else world.body(t.from)?.let { sim.position(it) }
                val b = world.body(t.to)?.let { sim.position(it) }
                if (a == null || b == null) false else {
                    val d = (a - b).length
                    (t.below == null || d < t.below) && (t.above == null || d > t.above)
                }
            }
            is Trigger.Approach -> world.body(t.body)?.let { sim.distanceTo(it) < t.distBelow && sim.relativeVelocity(it).length < t.speedBelow } ?: false
            else -> return false
        }
        val now = active && (r.condition == null || eval(r.condition))
        if (r.once) {
            if (now && r.id !in story.fired) {
                story.fired.add(r.id)
                runEffects(r.effects)
                return true
            }
            return false
        }
        val was = story.armed[r.id] ?: false
        story.armed[r.id] = now
        if (now && !was) {
            runEffects(r.effects)
            return true
        }
        return false
    }

    // ---- condições ----

    fun eval(c: Cond): Boolean = when (c) {
        is Cond.HasObject -> c.obj in story.objects
        is Cond.Knows -> c.fact in story.facts
        is Cond.Flag -> (story.flags[c.name] ?: false) == c.value
        is Cond.Rep -> (story.reputation[c.faction] ?: 0).let { v -> (c.min == null || v >= c.min) && (c.max == null || v <= c.max) }
        is Cond.Credits -> story.credits >= c.min
        is Cond.Deduced -> c.id in story.deduced
        Cond.HasCargo -> sim.ship.cargo > 0.0
        is Cond.Role -> world.role.kind == c.kind
        is Cond.MissionIs -> story.missions[c.id] == c.state
        is Cond.All -> c.items.all { eval(it) }
        is Cond.Any -> c.items.any { eval(it) }
        is Cond.Not -> !eval(c.item)
    }

    fun chanceOf(c: Chance): Double {
        val rep = c.faction?.let { story.reputation[it] } ?: 0
        return (c.base + rep * c.per).coerceIn(c.min, c.max)
    }

    // ---- efeitos ----

    fun addMessage(text: String) {
        messages.add(Message(text, clock))
        if (messages.size > 40) messages.removeAt(0)
    }

    fun runEffects(list: List<Effect>) {
        for (e in list) runEffect(e)
    }

    private fun runEffect(e: Effect) {
        when (e) {
            is Effect.Message -> addMessage(e.text)
            is Effect.Rep -> story.reputation[e.faction] = ((story.reputation[e.faction] ?: 0) + e.delta).coerceIn(-100, 100)
            is Effect.Credits -> story.credits += e.delta
            is Effect.GiveObject -> if (story.objects.add(e.obj)) {
                val o = pack.obj(e.obj)
                sim.ship.extra += o?.mass ?: 0.0
                addMessage("Você recebeu: ${o?.name ?: e.obj}.")
            }
            is Effect.TakeObject -> if (story.objects.remove(e.obj)) sim.ship.extra = (sim.ship.extra - (pack.obj(e.obj)?.mass ?: 0.0)).coerceAtLeast(0.0)
            is Effect.Reveal -> if (story.facts.add(e.fact)) {
                addMessage("Nova pista: ${pack.fact(e.fact)?.text ?: e.fact}")
                queue.add(GameEvent.FactRevealed(e.fact))
            }
            is Effect.SetFlag -> story.flags[e.name] = e.value
            is Effect.OpenDialogue -> openDialogue(e.id)
            is Effect.News -> story.news.add(0, e.text)
            is Effect.End -> if (story.ending == null) {
                story.ending = e.id
                pack.ending(e.id)?.let { story.news.add(0, it.headline) }
            }
            is Effect.Ai -> world.body(e.body)?.let { sim.setAi(it, e.mode) }
            is Effect.SetTarget -> world.body(e.body)?.let { sim.selectTarget(it) }
            Effect.LoseCargo -> {
                if (sim.ship.cargo > 0) addMessage("A carga foi apreendida.")
                sim.ship.cargo = 0.0
            }
            is Effect.Mission -> story.missions[e.id] = e.state
            is Effect.Roll -> {
                val ok = story.nextDouble() < chanceOf(e.chance)
                runEffects(if (ok) e.success else e.failure)
            }
        }
    }

    // ---- diálogos ----

    fun openDialogue(id: String) {
        if (pack.dialogue(id) == null) return
        if (story.dialogue == null) story.dialogue = id else story.pendingDialogues.add(id)
    }

    val dialogue: DialogueDef? get() = story.dialogue?.let { pack.dialogue(it) }

    private fun firstRoll(effects: List<Effect>): Effect.Roll? {
        for (e in effects) if (e is Effect.Roll) return e
        return null
    }

    fun choices(): List<ChoiceView> {
        val d = dialogue ?: return emptyList()
        if (d.choices.isEmpty()) return listOf(ChoiceView(-1, "Continuar", null, true, null))
        val out = ArrayList<ChoiceView>()
        for ((i, c) in d.choices.withIndex()) {
            val ok = c.condition == null || eval(c.condition)
            if (!ok && c.blockedText == null) continue
            val roll = firstRoll(c.effects)
            val detail = c.detail?.replace("{chance}", roll?.let { "${(chanceOf(it.chance) * 100).toInt()}%" } ?: "")
            out.add(ChoiceView(i, c.text, detail, ok, if (ok) null else c.blockedText))
        }
        return out
    }

    fun choose(index: Int) {
        val d = dialogue ?: return
        val choice: ChoiceDef? = d.choices.getOrNull(index)
        if (choice != null && choice.condition != null && !eval(choice.condition)) return
        story.dialogue = null
        if (choice != null) {
            runEffects(choice.effects)
            if (choice.next != null) openDialogue(choice.next)
        }
        queue.add(GameEvent.DialogueEnd(d.id))
        if (story.dialogue == null && story.pendingDialogues.isNotEmpty()) story.dialogue = story.pendingDialogues.removeAt(0)
        pump()
        if (story.dialogue == null && story.pendingDialogues.isNotEmpty()) story.dialogue = story.pendingDialogues.removeAt(0)
    }

    // ---- caderno, estação, porão ----

    /** Tenta combinar pistas. Devolve o texto da dedução, ou null se essas pistas não levam a nada. */
    fun deduce(selected: Set<String>): String? {
        val d = pack.deductions.firstOrNull { it.requires.toSet() == selected } ?: return null
        if (d.id in story.deduced) return d.text
        if (!story.facts.containsAll(d.requires)) return null
        story.deduced.add(d.id)
        addMessage("Dedução: ${d.text}")
        runEffects(d.effects)
        pump()
        return d.text
    }

    private fun runAutoDeductions() {
        for (d in pack.deductions) {
            if (d.auto && d.id !in story.deduced && story.facts.containsAll(d.requires)) {
                story.deduced.add(d.id)
                addMessage("Dedução: ${d.text}")
                runEffects(d.effects)
            }
        }
    }

    /** Acusa um suspeito. Vale a primeira acusação cujo `se` seja verdadeiro; devolve false se não há provas. */
    fun accuse(suspectId: String): Boolean {
        if (ended) {
            addMessage("O caso já foi encerrado.")
            return false
        }
        val a = pack.accusations.firstOrNull { it.suspectId == suspectId && (it.condition == null || eval(it.condition)) }
        if (a == null) {
            addMessage("Faltam provas para acusar.")
            return false
        }
        runEffects(a.effects)
        pump()
        return true
    }

    fun charactersAt(b: Body): List<com.theexpanse.content.CharacterDef> =
        pack.characters.filter { it.location == b.id && it.dialogue != null && (it.condition == null || eval(it.condition)) }

    fun talkTo(characterId: String) {
        pack.character(characterId)?.dialogue?.let { openDialogue(it) }
    }

    val activeMission get() = pack.missions.firstOrNull { story.missions[it.id] == "ativa" }

    fun undock() {
        sim.undock()
    }

    fun continueFlying() {
        if (sim.status == Status.CRASHED) return
        story.continued = true
    }

    // ---- salvamento ----

    fun toJson(): JsonObject = buildJsonObject {
        put("pacote", pack.id)
        put("sim", sim.toJson())
        put("relogio", clock)
        val s = story
        put("rng", s.rng)
        put("creditos", s.credits)
        put("final", s.ending ?: "")
        put("continuou", s.continued)
        put("dialogo", s.dialogue ?: "")
        fun arr(c: Collection<String>) = JsonArray(c.map { JsonPrimitive(it) })
        put("fatos", arr(s.facts)); put("objetos", arr(s.objects)); put("deduzidas", arr(s.deduced)); put("disparadas", arr(s.fired))
        put("noticias", arr(s.news)); put("dialogosPendentes", arr(s.pendingDialogues))
        put("flags", buildJsonObject { for ((k, v) in s.flags) put(k, v) })
        put("missoes", buildJsonObject { for ((k, v) in s.missions) put(k, v) })
        put("reputacao", buildJsonObject { for ((k, v) in s.reputation) put(k, v) })
        put("armadas", buildJsonObject { for ((k, v) in s.armed) put(k, v) })
        put("mensagens", JsonArray(messages.takeLast(12).map { JsonPrimitive(it.text) }))
    }

    fun fromJson(o: JsonObject) {
        require(o["pacote"]!!.jsonPrimitive.content == pack.id) { "o salvamento é de outro pacote" }
        sim.fromJson(o["sim"]!!.jsonObject)
        clock = o["relogio"]!!.jsonPrimitive.content.toDouble()
        val s = StoryState(pack, o["rng"]!!.jsonPrimitive.long)
        s.facts.clear(); s.objects.clear(); s.deduced.clear(); s.fired.clear(); s.news.clear(); s.missions.clear()
        s.credits = o["creditos"]!!.jsonPrimitive.int
        s.ending = o["final"]!!.jsonPrimitive.content.ifEmpty { null }
        s.continued = o["continuou"]!!.jsonPrimitive.boolean
        s.dialogue = o["dialogo"]!!.jsonPrimitive.content.ifEmpty { null }
        fun list(k: String) = o[k]!!.jsonArray.map { it.jsonPrimitive.content }
        s.facts.addAll(list("fatos")); s.objects.addAll(list("objetos")); s.deduced.addAll(list("deduzidas")); s.fired.addAll(list("disparadas"))
        s.news.addAll(list("noticias")); s.pendingDialogues.addAll(list("dialogosPendentes"))
        for ((k, v) in o["flags"]!!.jsonObject) s.flags[k] = v.jsonPrimitive.boolean
        for ((k, v) in o["missoes"]!!.jsonObject) s.missions[k] = v.jsonPrimitive.content
        for ((k, v) in o["reputacao"]!!.jsonObject) s.reputation[k] = v.jsonPrimitive.int
        for ((k, v) in o["armadas"]!!.jsonObject) s.armed[k] = v.jsonPrimitive.boolean
        story = s
        messages.clear()
        for (m in list("mensagens")) messages.add(Message(m, clock))
        queue.clear()
    }

    /** Estado final para a tela de encerramento. */
    fun repDelta(factionId: String): Int = (story.reputation[factionId] ?: 0) - (story.startReputation[factionId] ?: 0)

}
