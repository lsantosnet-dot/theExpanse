package com.theexpanse.content

import com.theexpanse.core.AutopilotMode
import com.theexpanse.core.Config
import com.theexpanse.core.Simulation
import com.theexpanse.core.Status
import com.theexpanse.core.World
import kotlin.math.sqrt

class ValidationResult(val errors: List<String>, val warnings: List<String>) {
    val ok: Boolean get() = errors.isEmpty()
}

/**
 * Confere um pacote antes de aceitá-lo: referências, órbitas, física (a nave consegue chegar?) e, principalmente,
 * se o mistério tem solução: simula as regras de forma abstrata e procura finais, pistas e objetos inalcançáveis.
 */
object Validator {
    fun validate(pack: ContentPackage, physics: Boolean = true): ValidationResult {
        val v = Run(pack)
        v.references()
        if (v.errors.isEmpty()) v.reachability()
        if (v.errors.isEmpty() && physics) v.physics()
        return ValidationResult(v.errors, v.warnings)
    }

    private class Run(val p: ContentPackage) {
        val errors = ArrayList<String>()
        val warnings = ArrayList<String>()

        fun err(s: String) { errors.add(s) }

        // ---------- referências ----------

        private fun unique(kind: String, ids: List<String>) {
            val seen = HashSet<String>()
            for (id in ids) {
                if (id.isEmpty()) err("$kind sem id.")
                else if (!seen.add(id)) err("$kind com id repetido: '$id'.")
            }
        }

        private fun need(kind: String, id: String, exists: Boolean, where: String) {
            if (!exists) err("$where: $kind '$id' não existe.")
        }

        fun references() {
            unique("facção", p.factions.map { it.id }); unique("corpo", p.bodies.map { it.id }); unique("objeto", p.objects.map { it.id })
            unique("fato", p.facts.map { it.id }); unique("personagem", p.characters.map { it.id }); unique("diálogo", p.dialogues.map { it.id })
            unique("regra", p.rules.map { it.id }); unique("dedução", p.deductions.map { it.id }); unique("missão", p.missions.map { it.id })
            unique("suspeito", p.suspects.map { it.id }); unique("acusação", p.accusations.map { it.id }); unique("final", p.endings.map { it.id })
            if (p.factions.isEmpty()) err("O pacote precisa de ao menos uma facção.")
            if (p.bodies.isEmpty()) err("O pacote precisa de ao menos um corpo (estação).")
            if (p.endings.isEmpty()) err("O pacote precisa de ao menos um final.")
            if (p.planet.gm <= 0 || p.planet.radius <= 0) err("planeta: 'gm' e 'raio' devem ser positivos.")
            if (p.role.kind == RoleKind.MILITARY) {
                val f = p.role.factionId ?: ""
                need("facção", f, p.faction(f) != null, "papel.faccao")
                if (p.ship.missiles <= 0) warnings.add("O jogador é militar mas 'nave.misseis' é 0: ele não poderá disparar.")
            }

            for (b in p.bodies) {
                val w = "corpo '${b.id}'"
                need("facção", b.factionId, p.faction(b.factionId) != null, w)
                val parent = b.parentId?.let { pid -> p.body(pid).also { if (it == null) err("$w: pai '$pid' não existe.") } }
                if (b.parentId != null) {
                    var hops = 0
                    var cur: BodyDef? = b
                    while (cur?.parentId != null && hops++ < 20) cur = p.body(cur.parentId!!)
                    if (hops >= 20) err("$w: a cadeia de 'pai' é circular.")
                }
                val central = if (parent == null) p.planet.radius else parent.radius
                if (b.orbitRadius < central * 1.05 || b.orbitRadius <= 0.0) err("$w: órbita de ${b.orbitRadius.toLong()} m fica dentro ou colada no corpo ${if (parent == null) "central" else "'${parent.id}'"} (raio ${central.toLong()} m).")
                if (b.eccentricity < 0.0 || b.eccentricity >= 0.9) err("$w: 'excentricidade' deve ficar entre 0 e 0,9.")
                if (b.kind == BodyKind.SHIP && b.dockable) err("$w: uma nave não pode ser atracável (use tipo estacao ou destroco).")
                if (b.kind == BodyKind.PLANET) {
                    if (b.radius <= 0.0) err("$w: um planeta precisa de 'raio' positivo.")
                    if (b.dockable) err("$w: planetas não são atracáveis (a nave se destrói ao encostar).")
                }
                if (b.defense < 0.0 || b.defense > 1.0) err("$w: 'defesa' deve ficar entre 0 e 1.")
            }
            unique("bloco", p.blocks.map { it.id })
            for (b in p.blocks) effects(b.effects, "bloco '${b.id}'.efeitos")
            for (k in p.reputation.keys) need("facção", k, p.faction(k) != null, "reputacao")
            val s = p.ship
            need("corpo", s.startNear, p.body(s.startNear) != null, "nave.perto")
            if (s.targetId != null) need("corpo", s.targetId, p.body(s.targetId) != null, "nave.alvo")
            if (s.startAltitude < 1000) err("nave.altitude deve ser de pelo menos 1000 m.")
            if (s.dryMass <= 0 || s.fuelMass <= 0) err("nave: 'massaSeca' e 'combustivel' devem ser positivos.")
            for (c in p.characters) {
                val w = "personagem '${c.id}'"
                c.factionId?.let { need("facção", it, p.faction(it) != null, w) }
                c.location?.let { need("corpo", it, p.body(it) != null, "$w.local") }
                c.dialogue?.let { need("diálogo", it, p.dialogue(it) != null, "$w.dialogo") }
                c.condition?.let { cond(it, "$w.se") }
            }
            for (d in p.dialogues) {
                val w = "diálogo '${d.id}'"
                d.characterId?.let { need("personagem", it, p.character(it) != null, "$w.personagem") }
                for ((i, c) in d.choices.withIndex()) {
                    c.condition?.let { cond(it, "$w.escolhas[$i].se") }
                    effects(c.effects, "$w.escolhas[$i].efeitos")
                    c.next?.let { need("diálogo", it, p.dialogue(it) != null, "$w.escolhas[$i].proximo") }
                }
            }
            for (r in p.rules) {
                val w = "regra '${r.id}'"
                when (val t = r.trigger) {
                    is Trigger.Dock -> {
                        need("corpo", t.place, p.body(t.place) != null, "$w.quando.local")
                        p.body(t.place)?.let { if (!it.dockable) err("$w: '${t.place}' não é atracável (marque \"atracavel\": true).") }
                    }
                    is Trigger.Distance -> {
                        if (t.from != "jogador") need("corpo", t.from, p.body(t.from) != null, "$w.quando.de")
                        need("corpo", t.to, p.body(t.to) != null, "$w.quando.ate")
                    }
                    is Trigger.DialogueEnd -> need("diálogo", t.dialogue, p.dialogue(t.dialogue) != null, "$w.quando.dialogo")
                    is Trigger.FactRevealed -> need("fato", t.fact, p.fact(t.fact) != null, "$w.quando.fato")
                    is Trigger.Approach -> need("corpo", t.body, p.body(t.body) != null, "$w.quando.corpo")
                    is Trigger.Boarded -> t.body?.let { need("corpo", it, p.body(it) != null, "$w.quando.corpo") }
                    is Trigger.BoardingResult -> t.body?.let { need("corpo", it, p.body(it) != null, "$w.quando.corpo") }
                    is Trigger.Reputation -> need("facção", t.faction, p.faction(t.faction) != null, "$w.quando.faccao")
                    is Trigger.ObjectGained -> need("objeto", t.obj, p.obj(t.obj) != null, "$w.quando.objeto")
                    is Trigger.DeductionMade -> need("dedução", t.id, p.deductions.any { it.id == t.id }, "$w.quando.id")
                    is Trigger.MissionChanged -> need("missão", t.id, p.missions.any { it.id == t.id }, "$w.quando.id")
                    is Trigger.BodyDestroyed -> {
                        need("corpo", t.body, p.body(t.body) != null, "$w.quando.corpo")
                        p.body(t.body)?.let { if (it.kind != BodyKind.SHIP) err("$w: só naves podem ser destruídas ('${t.body}' não é uma nave).") }
                    }
                    else -> {}
                }
                r.condition?.let { cond(it, "$w.se") }
                effects(r.effects, "$w.entao")
            }
            for (d in p.deductions) {
                val w = "dedução '${d.id}'"
                if (d.requires.isEmpty()) err("$w: 'precisa' não pode ser vazio.")
                for (f in d.requires) need("fato", f, p.fact(f) != null, "$w.precisa")
                effects(d.effects, "$w.efeitos")
            }
            for (a in p.accusations) {
                val w = "acusação '${a.id}'"
                need("suspeito", a.suspectId, p.suspects.any { it.id == a.suspectId }, "$w.suspeito")
                a.condition?.let { cond(it, "$w.se") }
                effects(a.effects, "$w.efeitos")
            }
            if (p.accusations.isNotEmpty() && p.suspects.isEmpty()) err("Há acusações, mas nenhum suspeito.")
        }

        private fun cond(c: Cond, w: String) {
            when (c) {
                is Cond.HasObject -> need("objeto", c.obj, p.obj(c.obj) != null, w)
                is Cond.Knows -> need("fato", c.fact, p.fact(c.fact) != null, w)
                is Cond.Rep -> need("facção", c.faction, p.faction(c.faction) != null, w)
                is Cond.Deduced -> need("dedução", c.id, p.deductions.any { it.id == c.id }, w)
                is Cond.MissionIs -> need("missão", c.id, p.missions.any { it.id == c.id }, w)
                is Cond.All -> c.items.forEach { cond(it, w) }
                is Cond.Any -> c.items.forEach { cond(it, w) }
                is Cond.Not -> cond(c.item, w)
                is Cond.DockedAt -> c.place?.let { need("corpo", it, p.body(it) != null, w) }
                is Cond.BodyAlive -> need("corpo", c.body, p.body(c.body) != null, w)
                is Cond.TargetIs -> need("corpo", c.body, p.body(c.body) != null, w)
                is Cond.Distance -> { if (c.from != "jogador") need("corpo", c.from, p.body(c.from) != null, w); need("corpo", c.to, p.body(c.to) != null, w) }
                else -> {}
            }
        }

        private fun effects(list: List<Effect>, w: String) {
            for ((i, e) in list.withIndex()) {
                val at = "$w[$i]"
                when (e) {
                    is Effect.Rep -> need("facção", e.faction, p.faction(e.faction) != null, at)
                    is Effect.GiveObject -> need("objeto", e.obj, p.obj(e.obj) != null, at)
                    is Effect.TakeObject -> need("objeto", e.obj, p.obj(e.obj) != null, at)
                    is Effect.Reveal -> need("fato", e.fact, p.fact(e.fact) != null, at)
                    is Effect.OpenDialogue -> need("diálogo", e.id, p.dialogue(e.id) != null, at)
                    is Effect.End -> need("final", e.id, p.ending(e.id) != null, at)
                    is Effect.Ai -> {
                        need("corpo", e.body, p.body(e.body) != null, at)
                        p.body(e.body)?.let { if (it.kind != BodyKind.SHIP) err("$at: só naves têm comportamento ('${e.body}' não é uma nave).") }
                        for (r in e.route) need("corpo da rota", r, p.body(r) != null, at)
                    }
                    is Effect.If -> { cond(e.cond, "$at.condicao"); effects(e.then, "$at.entao"); effects(e.otherwise, "$at.senao") }
                    is Effect.Run -> need("bloco", e.block, p.block(e.block) != null, at)
                    is Effect.Schedule -> need("bloco", e.block, p.block(e.block) != null, at)
                    is Effect.Ambush -> {
                        need("corpo", e.body, p.body(e.body) != null, at)
                        p.body(e.body)?.let { if (it.kind != BodyKind.SHIP) err("$at: só naves podem emboscar ('${e.body}' não é uma nave).") }
                    }
                    is Effect.RevealBody -> need("corpo", e.body, p.body(e.body) != null, at)
                    is Effect.ConcealBody -> need("corpo", e.body, p.body(e.body) != null, at)
                    is Effect.DestroyBody -> need("corpo", e.body, p.body(e.body) != null, at)
                    is Effect.SetTarget -> need("corpo", e.body, p.body(e.body) != null, at)
                    is Effect.Mission -> need("missão", e.id, p.missions.any { it.id == e.id }, at)
                    is Effect.Roll -> {
                        e.chance.faction?.let { need("facção", it, p.faction(it) != null, "$at.chance") }
                        effects(e.success, "$at.sucesso"); effects(e.failure, "$at.falha")
                    }
                    else -> {}
                }
            }
        }

        // ---------- alcançabilidade do mistério ----------

        private val facts = HashSet<String>()
        private val objects = HashSet<String>()
        private val flags = HashSet<String>()
        private val deduced = HashSet<String>()
        private val dialogues = HashSet<String>()
        private val endings = HashSet<String>()
        private var depth = 0

        private fun sat(c: Cond): Boolean = when (c) {
            is Cond.HasObject -> c.obj in objects
            is Cond.Knows -> c.fact in facts
            is Cond.Flag -> !c.value || c.name in flags
            is Cond.Deduced -> c.id in deduced
            is Cond.All -> c.items.all { sat(it) }
            is Cond.Any -> c.items.any { sat(it) }
            else -> true
        }

        private fun why(c: Cond): String? = when (c) {
            is Cond.HasObject -> if (c.obj in objects) null else "nada dá o objeto '${c.obj}'"
            is Cond.Knows -> if (c.fact in facts) null else "nenhuma regra ou diálogo alcançável revela a pista '${c.fact}'"
            is Cond.Flag -> if (!c.value || c.name in flags) null else "nenhum efeito liga a marca '${c.name}'"
            is Cond.Deduced -> if (c.id in deduced) null else "a dedução '${c.id}' nunca é alcançável"
            is Cond.All -> c.items.firstNotNullOfOrNull { why(it) }
            is Cond.Any -> if (c.items.any { sat(it) }) null else c.items.mapNotNull { why(it) }.joinToString(" e ")
            else -> null
        }

        private fun triggerReachable(t: Trigger): Boolean = when (t) {
            is Trigger.DialogueEnd -> t.dialogue in dialogues
            is Trigger.FactRevealed -> t.fact in facts
            is Trigger.ObjectGained -> t.obj in objects
            is Trigger.DeductionMade -> t.id in deduced
            else -> true
        }

        private fun apply(list: List<Effect>): Boolean {
            var changed = false
            for (e in list) when (e) {
                is Effect.Reveal -> changed = facts.add(e.fact) || changed
                is Effect.GiveObject -> changed = objects.add(e.obj) || changed
                is Effect.SetFlag -> if (e.value) changed = flags.add(e.name) || changed
                is Effect.OpenDialogue -> changed = dialogues.add(e.id) || changed
                is Effect.End -> changed = endings.add(e.id) || changed
                is Effect.Roll -> { changed = apply(e.success) || changed; changed = apply(e.failure) || changed }
                is Effect.If -> { changed = apply(e.then) || changed; changed = apply(e.otherwise) || changed }
                is Effect.Run -> p.block(e.block)?.let { b -> if (depth < 6) { depth++; changed = apply(b.effects) || changed; depth-- } }
                is Effect.Schedule -> p.block(e.block)?.let { b -> if (depth < 6) { depth++; changed = apply(b.effects) || changed; depth-- } }
                else -> {}
            }
            return changed
        }

        private fun mentionsEnd(list: List<Effect>, id: String, hops: Int = 0): Boolean = list.any {
            it is Effect.End && it.id == id || it is Effect.Roll && (mentionsEnd(it.success, id, hops) || mentionsEnd(it.failure, id, hops)) ||
                it is Effect.If && (mentionsEnd(it.then, id, hops) || mentionsEnd(it.otherwise, id, hops)) ||
                (hops < 5 && it is Effect.Run && (p.block(it.block)?.let { b -> mentionsEnd(b.effects, id, hops + 1) } ?: false)) ||
                (hops < 5 && it is Effect.Schedule && (p.block(it.block)?.let { b -> mentionsEnd(b.effects, id, hops + 1) } ?: false))
        }

        fun reachability() {
            var changed = true
            var guard = 0
            while (changed && guard++ < 200) {
                changed = false
                for (r in p.rules) if (triggerReachable(r.trigger) && (r.condition == null || sat(r.condition))) changed = apply(r.effects) || changed
                for (c in p.characters) if (c.location != null && c.dialogue != null && (c.condition == null || sat(c.condition))) changed = dialogues.add(c.dialogue) || changed
                for (d in p.deductions) if (facts.containsAll(d.requires)) { changed = deduced.add(d.id) || changed; changed = apply(d.effects) || changed }
                for (a in p.accusations) if (a.condition == null || sat(a.condition)) changed = apply(a.effects) || changed
                for (id in dialogues.toList()) {
                    val d = p.dialogue(id) ?: continue
                    for (c in d.choices) if (c.condition == null || sat(c.condition)) {
                        changed = apply(c.effects) || changed
                        c.next?.let { changed = dialogues.add(it) || changed }
                    }
                }
            }

            val usedFacts = HashSet<String>(); val usedObjects = HashSet<String>(); val usedDeductions = HashSet<String>()
            fun scan(c: Cond?) {
                when (c) {
                    is Cond.Knows -> usedFacts.add(c.fact)
                    is Cond.HasObject -> usedObjects.add(c.obj)
                    is Cond.Deduced -> usedDeductions.add(c.id)
                    is Cond.All -> c.items.forEach { scan(it) }
                    is Cond.Any -> c.items.forEach { scan(it) }
                    is Cond.Not -> scan(c.item)
                    else -> {}
                }
            }
            p.rules.forEach { scan(it.condition) }; p.accusations.forEach { scan(it.condition) }
            p.dialogues.forEach { d -> d.choices.forEach { scan(it.condition) } }; p.characters.forEach { scan(it.condition) }
            p.deductions.forEach { usedFacts.addAll(it.requires) }

            for (e in p.endings) if (e.id !in endings) {
                val reasons = ArrayList<String>()
                for (r in p.rules) if (mentionsEnd(r.effects, e.id)) {
                    val why = when {
                        !triggerReachable(r.trigger) -> "o gatilho nunca acontece (${trigName(r.trigger)})"
                        r.condition != null -> why(r.condition)
                        else -> null
                    }
                    reasons.add("a regra '${r.id}' exige: ${why ?: "?"}")
                }
                for (a in p.accusations) if (mentionsEnd(a.effects, e.id)) reasons.add("a acusação '${a.id}' exige: ${a.condition?.let { why(it) } ?: "?"}")
                for (d in p.dialogues) for ((i, c) in d.choices.withIndex()) if (mentionsEnd(c.effects, e.id)) {
                    reasons.add(if (d.id !in dialogues) "a escolha ${i + 1} do diálogo '${d.id}' nunca aparece (o diálogo nunca abre)" else "a escolha ${i + 1} do diálogo '${d.id}' exige: ${c.condition?.let { why(it) } ?: "?"}")
                }
                val txt = if (reasons.isEmpty()) "nenhuma regra, escolha ou acusação encerra a partida com ele." else reasons.joinToString("; ") + "."
                err("O final '${e.id}' nunca é alcançável: $txt")
            }
            for (f in p.facts) if (f.id !in facts) {
                val msg = "A pista '${f.id}' nunca é revelada por nenhuma regra ou diálogo alcançável."
                if (f.id in usedFacts) err(msg) else warnings.add(msg)
            }
            for (o in p.objects) if (o.id !in objects) {
                val msg = "O objeto '${o.id}' nunca é entregue por nenhum efeito alcançável."
                if (o.id in usedObjects) err(msg) else warnings.add(msg)
            }
            for (d in p.deductions) if (d.id !in deduced) {
                val msg = "A dedução '${d.id}' nunca é alcançável (faltam pistas)."
                if (d.id in usedDeductions) err(msg) else warnings.add(msg)
            }
            for (d in p.dialogues) if (d.id !in dialogues && !p.rules.any { r -> r.trigger is Trigger.DialogueEnd && (r.trigger as Trigger.DialogueEnd).dialogue == d.id }) {
                warnings.add("O diálogo '${d.id}' nunca é aberto.")
            }
            if (p.endings.none { it.kind == EndingKind.WIN && it.id in endings }) err("Nenhum final de vitória é alcançável.")
        }

        private fun trigName(t: Trigger) = when (t) {
            is Trigger.DialogueEnd -> "fim do diálogo '${t.dialogue}', que nunca abre"
            is Trigger.FactRevealed -> "pista '${t.fact}' revelada"
            else -> "?"
        }

        // ---------- física ----------

        fun physics() {
            val world = World(p)
            val ship = Simulation(world).ship
            if (ship.deltaV < 2000.0) err("nave: Δv de ${ship.deltaV.toInt()} m/s é pequeno demais para viagens entre estações (mínimo 2000 m/s; aumente 'combustivel' ou reduza 'massaSeca').")
            val places = (p.rules.mapNotNull { (it.trigger as? Trigger.Dock)?.place } + p.characters.mapNotNull { it.location }).distinct()
            if (places.isEmpty()) return
            var reached = 0
            val failures = ArrayList<String>()
            for (place in places) {
                val sim = Simulation(world)
                sim.selectTarget(world.body(place)!!)
                val far = sim.distanceTo()
                if (far > 5e9) {
                    // escala do Sistema Solar: confere o Δv mínimo de uma viagem com inversão no meio, sem simular milhões de passos
                    val need = 2.0 * sqrt(0.85 * sim.ship.maxAccel * far) + sim.relativeVelocity().length
                    if (sim.ship.deltaV >= need * 1.15) reached++
                    else failures.add("o Δv da nave (${sim.ship.deltaV.toLong()} m/s) não basta para chegar a '$place' (preciso de ~${(need * 1.15).toLong()} m/s): aumente o combustível, a velocidade de exaustão ou reduza a carga.")
                    continue
                }
                sim.setThrust(100)
                sim.setAutopilot(AutopilotMode.INTERCEPT)
                val d0 = sim.distanceTo()
                val limit = maxOf(400_000.0, 4.0 * 2.0 * sqrt(d0 / (sim.accelLimit * 0.85)))
                while (sim.status == Status.FLYING && sim.time < limit) sim.step(if (sim.distanceTo() > 2e7) 0.28 else Config.SUBSTEP)
                if (sim.status == Status.DOCKED) reached++
                else failures.add("não consegui chegar a '$place' pelo piloto automático saindo do ponto inicial (${if (sim.status == Status.CRASHED) "colide com o planeta" else "passa do tempo limite ou falta combustível"}).")
            }
            if (reached == 0) failures.forEach { err(it) } else failures.forEach { warnings.add(it) }
        }
    }
}
