package com.theexpanse.content

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull

class DecodeResult(val pack: ContentPackage?, val errors: List<String>)

/** Lê o JSON de um pacote. Todo erro cita o caminho do trecho, para a pessoa (ou a IA) corrigir. */
object Decoder {
    /** Munição da nave do jogador quando o pacote militar não diz quanto: dez mísseis e dez despistadores. */
    const val MIL_MISSILES = 10
    const val MIL_DECOYS = 10

    const val FORMAT = 1

    fun decode(text: String): DecodeResult {
        val root = try {
            Json.parseToJsonElement(text)
        } catch (e: Exception) {
            return DecodeResult(null, listOf("JSON inválido: ${e.message?.lineSequence()?.firstOrNull() ?: "erro de sintaxe"}"))
        }
        val obj = root as? JsonObject ?: return DecodeResult(null, listOf("O arquivo deve ser um objeto JSON { ... }."))
        val errors = ArrayList<String>()
        val pack = Reader(errors).pack(Node(obj, "pacote", errors))
        return DecodeResult(if (errors.isEmpty()) pack else null, errors)
    }

    private class Node(val obj: JsonObject, val path: String, private val errors: MutableList<String>) {
        fun err(msg: String) { errors.add("$path: $msg") }
        fun has(k: String) = obj[k].let { it != null && it !is JsonNull }

        fun only(allowed: Set<String>) {
            for (k in obj.keys) if (k !in allowed) err("campo desconhecido '$k' (campos válidos: ${allowed.sorted().joinToString(", ")})")
        }

        private fun prim(key: String, required: Boolean): JsonPrimitive? {
            val e = obj[key]
            if (e == null || e is JsonNull) { if (required) err("falta o campo '$key'"); return null }
            return e as? JsonPrimitive ?: run { err("'$key' tem o tipo errado"); null }
        }

        fun str(key: String, required: Boolean = true): String? {
            val p = prim(key, required) ?: return null
            if (!p.isString) { err("'$key' deve ser texto"); return null }
            return p.content
        }

        fun num(key: String, required: Boolean = true): Double? {
            val p = prim(key, required) ?: return null
            val v = if (p.isString) null else p.doubleOrNull
            if (v == null) { err("'$key' deve ser um número"); return null }
            return v
        }

        fun int(key: String, required: Boolean = true): Int? = num(key, required)?.let {
            if (it != Math.floor(it)) { err("'$key' deve ser um número inteiro"); null } else it.toInt()
        }

        fun bool(key: String, default: Boolean): Boolean {
            val p = prim(key, false) ?: return default
            return p.booleanOrNull ?: run { err("'$key' deve ser verdadeiro ou falso"); default }
        }

        fun child(key: String, required: Boolean = true): Node? {
            val e = obj[key]
            if (e == null || e is JsonNull) { if (required) err("falta o campo '$key'"); return null }
            val o = e as? JsonObject ?: run { err("'$key' deve ser um objeto { ... }"); return null }
            return Node(o, "$path.$key", errors)
        }

        fun nodes(key: String, required: Boolean = false): List<Node> {
            val e = obj[key]
            if (e == null || e is JsonNull) { if (required) err("falta a lista '$key'"); return emptyList() }
            val a = e as? JsonArray ?: run { err("'$key' deve ser uma lista [ ... ]"); return emptyList() }
            return a.mapIndexedNotNull { i, el ->
                (el as? JsonObject)?.let { Node(it, "$path.$key[$i]", errors) } ?: run { errors.add("$path.$key[$i]: deve ser um objeto { ... }"); null }
            }
        }

        fun strings(key: String, required: Boolean = false): List<String> {
            val e = obj[key]
            if (e == null || e is JsonNull) { if (required) err("falta a lista '$key'"); return emptyList() }
            val a = e as? JsonArray ?: run { err("'$key' deve ser uma lista de textos"); return emptyList() }
            return a.mapIndexedNotNull { i, el ->
                val p = el as? JsonPrimitive
                if (p == null || !p.isString) { errors.add("$path.$key[$i]: deve ser texto"); null } else p.content
            }
        }
    }

    private class Reader(val errors: MutableList<String>) {
        fun pack(n: Node): ContentPackage? {
            n.only(setOf("formato", "id", "titulo", "resumo", "autor", "papel", "escalaTempo", "planeta", "facoes", "corpos", "nave", "creditos", "reputacao",
                "objetos", "fatos", "personagens", "dialogos", "regras", "deducoes", "missoes", "suspeitos", "acusacoes", "tentativasAcusacao", "finais", "noticias", "blocos", "emboscadas"))
            val format = n.int("formato")
            if (format != null && format != FORMAT) n.err("formato $format não suportado (este jogo lê o formato $FORMAT)")
            val id = n.str("id") ?: ""
            if (id.isNotEmpty() && !ID.matches(id)) n.err("'id' deve ter só letras minúsculas, números e _ (ex.: meu_pacote)")
            val title = n.str("titulo") ?: ""
            val summary = n.str("resumo", false) ?: ""
            val author = n.str("autor", false) ?: ""
            val role = n.child("papel", false)?.let { rn ->
                rn.only(setOf("tipo", "faccao"))
                val key = rn.str("tipo") ?: "mercador"
                val kind = RoleKind.values().firstOrNull { it.key == key } ?: run { rn.err("'tipo' deve ser mercador ou militar"); RoleKind.MERCHANT }
                val fac = rn.str("faccao", false)
                if (kind == RoleKind.MILITARY && fac == null) rn.err("o papel militar precisa de 'faccao'")
                RoleDef(kind, fac)
            } ?: RoleDef(RoleKind.MERCHANT, null)
            val timeScale = n.num("escalaTempo", false) ?: 1440.0
            if (timeScale < 10.0 || timeScale > 100_000.0) n.err("'escalaTempo' deve ficar entre 10 e 100000 (1440 = um dia por minuto real)")
            val planet = n.child("planeta", false)?.let { pn ->
                pn.only(setOf("nome", "gm", "raio", "tipo"))
                val t = pn.str("tipo", false) ?: "planeta"
                if (t != "planeta" && t != "estrela") pn.err("'tipo' deve ser planeta ou estrela")
                PlanetDef(pn.str("nome") ?: "Planeta", pn.num("gm") ?: 1e17, pn.num("raio") ?: 6e7, t == "estrela")
            } ?: PlanetDef("Aurélia", 1.0e17, 6.0e7)

            val factions = n.nodes("facoes", true).map { fn ->
                fn.only(setOf("id", "nome", "cor", "estilo"))
                val styleKey = fn.str("estilo", false) ?: "generico"
                val style = HullStyle.values().firstOrNull { it.key == styleKey } ?: run { fn.err("'estilo' deve ser terrano, marciano, cinturao ou generico"); HullStyle.GENERIC }
                FactionDef(fn.str("id") ?: "", fn.str("nome") ?: "", color(fn, fn.str("cor") ?: "#8A95A8"), style)
            }
            val bodies = n.nodes("corpos", true).map { bn ->
                bn.only(setOf("id", "nome", "curto", "tipo", "faccao", "raioOrbita", "fase", "tamanho", "atracavel", "misseis", "despistadores", "chanceDespistar",
                    "pai", "excentricidade", "argPeriapse", "gm", "raio", "cor", "oculto", "defesa", "chanceInterceptar", "aceleracaoMax", "pousavel", "escalaAtmosfera", "velTerminalAtmosfera"))
                val kindKey = bn.str("tipo") ?: "estacao"
                val kind = BodyKind.values().firstOrNull { it.key == kindKey } ?: run {
                    bn.err("'tipo' deve ser estacao, nave, destroco ou planeta"); BodyKind.STATION
                }
                val name = bn.str("nome") ?: ""
                BodyDef(
                    bn.str("id") ?: "", name, bn.str("curto", false) ?: name.substringBefore(' '), bn.str("faccao") ?: "",
                    kind, bn.num("raioOrbita") ?: 1e8, bn.num("fase", false) ?: 0.0,
                    bn.num("tamanho", false) ?: if (kind == BodyKind.STATION) 150.0 else 90.0,
                    bn.bool("atracavel", kind == BodyKind.STATION || kind == BodyKind.WRECK),
                    bn.int("misseis", false) ?: 4, bn.int("despistadores", false) ?: 2, bn.num("chanceDespistar", false) ?: 0.4,
                    bn.str("pai", false), bn.num("excentricidade", false) ?: 0.0, bn.num("argPeriapse", false) ?: 0.0,
                    bn.num("gm", false) ?: 0.0, bn.num("raio", false) ?: 0.0, bn.str("cor", false)?.let { color(bn, it) },
                    bn.bool("oculto", false), bn.num("defesa", false) ?: 0.4, bn.num("chanceInterceptar", false) ?: 0.5,
                    bn.num("aceleracaoMax", false) ?: 3.0,
                    bn.bool("pousavel", kind == BodyKind.PLANET),
                    bn.num("escalaAtmosfera", false) ?: 0.0, bn.num("velTerminalAtmosfera", false) ?: 0.0,
                )
            }
            val ship = n.child("nave")?.let { sn ->
                sn.only(setOf("nome", "massaSeca", "carga", "combustivel", "nomeCarga", "perto", "altitude", "alvo", "casco", "misseis", "despistadores", "chanceDespistar", "aceleracaoMax", "velocidadeExaustao"))
                val mil = role.kind == RoleKind.MILITARY
                ShipDef(
                    sn.str("nome", false) ?: "Nave", sn.num("massaSeca", false) ?: 240_000.0, sn.num("carga", false) ?: 40_000.0,
                    sn.num("combustivel", false) ?: 32_000.0, sn.str("perto") ?: "", sn.num("altitude", false) ?: 40_000.0,
                    sn.str("alvo", false), sn.str("nomeCarga", false) ?: "a carga",
                    sn.num("casco", false) ?: if (mil) 220.0 else 100.0, sn.int("misseis", false) ?: if (mil) MIL_MISSILES else 0,
                    sn.int("despistadores", false) ?: if (mil) MIL_DECOYS else 4, sn.num("chanceDespistar", false) ?: if (mil) 0.4 else 0.5,
                    sn.num("aceleracaoMax", false) ?: 6.0, sn.num("velocidadeExaustao", false) ?: 8.0e5,
                )
            } ?: ShipDef("Nave", 240_000.0, 40_000.0, 32_000.0, "", 40_000.0, null, "a carga")
            val credits = n.int("creditos", false) ?: 0
            val reputation = n.child("reputacao", false)?.let { rn ->
                rn.obj.keys.associateWith { k -> rn.int(k) ?: 0 }
            } ?: emptyMap()

            val objects = n.nodes("objetos").map { o -> o.only(setOf("id", "nome", "massa")); ObjectDef(o.str("id") ?: "", o.str("nome") ?: "", o.num("massa", false) ?: 0.0) }
            val facts = n.nodes("fatos").map { o -> o.only(setOf("id", "texto")); FactDef(o.str("id") ?: "", o.str("texto") ?: "") }
            val characters = n.nodes("personagens").map { c ->
                c.only(setOf("id", "nome", "papel", "faccao", "local", "dialogo", "se"))
                CharacterDef(c.str("id") ?: "", c.str("nome") ?: "", c.str("papel", false) ?: "", c.str("faccao", false),
                    c.str("local", false), c.str("dialogo", false), c.child("se", false)?.let { cond(it) })
            }
            val dialogues = n.nodes("dialogos").map { d ->
                d.only(setOf("id", "personagem", "texto", "escolhas"))
                DialogueDef(d.str("id") ?: "", d.str("personagem", false), d.str("texto") ?: "", d.nodes("escolhas").map { c ->
                    c.only(setOf("texto", "detalhe", "se", "bloqueio", "efeitos", "proximo"))
                    ChoiceDef(c.str("texto") ?: "", c.str("detalhe", false), c.child("se", false)?.let { cond(it) },
                        c.str("bloqueio", false), c.nodes("efeitos").mapNotNull { effect(it) }, c.str("proximo", false))
                })
            }
            val rules = n.nodes("regras").map { r ->
                r.only(setOf("id", "quando", "se", "entao", "repetir"))
                val trig = r.child("quando")?.let { trigger(it) } ?: Trigger.Start
                RuleDef(r.str("id") ?: "", trig, r.child("se", false)?.let { cond(it) }, r.nodes("entao").mapNotNull { effect(it) }, !r.bool("repetir", false))
            }
            val deductions = n.nodes("deducoes").map { d ->
                d.only(setOf("id", "precisa", "conclusao", "efeitos", "automatica"))
                DeductionDef(d.str("id") ?: "", d.strings("precisa", true), d.str("conclusao") ?: "", d.nodes("efeitos").mapNotNull { effect(it) }, d.bool("automatica", false))
            }
            val missions = n.nodes("missoes").map { m ->
                m.only(setOf("id", "titulo", "texto", "inicial"))
                MissionDef(m.str("id") ?: "", m.str("titulo") ?: "", m.str("texto", false) ?: "", m.bool("inicial", false))
            }
            val suspects = n.nodes("suspeitos").map { s -> s.only(setOf("id", "nome", "texto")); SuspectDef(s.str("id") ?: "", s.str("nome") ?: "", s.str("texto", false) ?: "") }
            val accusations = n.nodes("acusacoes").map { a ->
                a.only(setOf("id", "suspeito", "se", "efeitos"))
                AccusationDef(a.str("id") ?: "", a.str("suspeito") ?: "", a.child("se", false)?.let { cond(it) }, a.nodes("efeitos").mapNotNull { effect(it) })
            }
            val endings = n.nodes("finais", true).map { e ->
                e.only(setOf("id", "titulo", "manchete", "tipo"))
                val kindKey = e.str("tipo", false) ?: "neutro"
                val kind = EndingKind.values().firstOrNull { it.key == kindKey } ?: run { e.err("'tipo' deve ser vitoria, derrota ou neutro"); EndingKind.NEUTRAL }
                EndingDef(e.str("id") ?: "", e.str("titulo") ?: "", e.str("manchete") ?: "", kind)
            }
            val attempts = n.int("tentativasAcusacao", false) ?: DEFAULT_ACCUSATIONS
            if (attempts < 1 || attempts > 9) n.err("'tentativasAcusacao' deve ficar entre 1 e 9 (1 = uma acusação encerra o caso)")
            val news = n.strings("noticias")
            val blocks = n.nodes("blocos").map { b -> b.only(setOf("id", "efeitos")); BlockDef(b.str("id") ?: "", b.nodes("efeitos").mapNotNull { effect(it) }) }
            val ambushes = n.nodes("emboscadas").map { a ->
                a.only(setOf("id", "nave", "faccao", "chance", "perto", "raio", "alvo", "comportamento", "distancia", "intervalo", "repetir", "mensagem", "efeitos"))
                val key = a.str("comportamento", false) ?: "abordar"
                val mode = listOf(AiMode.BOARD, AiMode.ATTACK, AiMode.PURSUE).firstOrNull { it.key == key } ?: run { a.err("'comportamento' da emboscada deve ser abordar, atacar ou perseguir"); AiMode.BOARD }
                AmbushDef(
                    a.str("id") ?: "", a.str("nave") ?: "", a.str("faccao") ?: "", a.num("chance", false) ?: 0.3,
                    a.str("perto", false), a.num("raio", false) ?: 1e9, a.str("alvo", false),
                    mode, a.num("distancia", false) ?: 25_000.0, a.num("intervalo", false) ?: 21_600.0, a.bool("repetir", false),
                    a.str("mensagem", false), a.nodes("efeitos", false).mapNotNull { effect(it) },
                )
            }
            if (errors.isNotEmpty()) return null
            return ContentPackage(format ?: FORMAT, id, title, summary, author, role, timeScale, planet, factions, bodies, ship, credits, reputation,
                objects, facts, characters, dialogues, rules, deductions, missions, suspects, accusations, endings, news, blocks, attempts.coerceIn(1, 9), ambushes)
        }

        private fun color(n: Node, hex: String): Int {
            val m = Regex("^#([0-9a-fA-F]{6})$").matchEntire(hex)
            if (m == null) { n.err("'cor' deve ser #RRGGBB"); return 0xFF8A95A8.toInt() }
            return (0xFF000000L or m.groupValues[1].toLong(16)).toInt()
        }

        private fun typeOf(n: Node, kind: String, specs: List<Spec>): Spec? {
            val t = n.str("tipo") ?: return null
            val spec = Vocabulary.spec(specs, t)
            if (spec == null) { n.err("$kind '$t' desconhecido (válidos: ${specs.joinToString(", ") { it.name }})"); return null }
            n.only(spec.keys)
            return spec
        }

        fun trigger(n: Node): Trigger? {
            val spec = typeOf(n, "gatilho", Vocabulary.triggers) ?: return null
            return when (spec.name) {
                "inicio" -> Trigger.Start
                "atracar" -> Trigger.Dock(n.str("local") ?: "")
                "distancia" -> {
                    val below = n.num("menorQue", false)
                    val above = n.num("maiorQue", false)
                    if (below == null && above == null) n.err("informe 'menorQue' ou 'maiorQue'")
                    Trigger.Distance(n.str("de") ?: "jogador", n.str("ate") ?: "", below, above)
                }
                "tempo" -> Trigger.Time(n.num("depoisDe") ?: 0.0)
                "fim_dialogo" -> Trigger.DialogueEnd(n.str("dialogo") ?: "")
                "fato_revelado" -> Trigger.FactRevealed(n.str("fato") ?: "")
                "aproximacao" -> Trigger.Approach(n.str("corpo") ?: "", n.num("distMenorQue") ?: 0.0, n.num("velMenorQue") ?: 0.0)
                "corpo_destruido" -> Trigger.BodyDestroyed(n.str("corpo") ?: "")
                "abordado" -> Trigger.Boarded(n.str("corpo", false))
                "abordagem" -> {
                    val r = n.str("resultado", false)
                    if (r != null && r != "sucesso" && r != "falha") n.err("'resultado' deve ser sucesso ou falha")
                    Trigger.BoardingResult(n.str("corpo", false), r?.let { it == "sucesso" })
                }
                "casco_abaixo" -> Trigger.HullBelow(n.num("pct") ?: 0.0)
                "combustivel_abaixo" -> Trigger.FuelBelow(n.num("pct") ?: 0.0)
                "a_cada" -> Trigger.Every((n.num("segundos") ?: 60.0).also { if (it < 1.0) n.err("'segundos' deve ser pelo menos 1") })
                "reputacao_na_faixa" -> {
                    val mn = n.int("min", false); val mx = n.int("max", false)
                    if (mn == null && mx == null) n.err("informe 'min' ou 'max'")
                    Trigger.Reputation(n.str("faccao") ?: "", mn, mx)
                }
                "objeto_obtido" -> Trigger.ObjectGained(n.str("objeto") ?: "")
                "deducao_feita" -> Trigger.DeductionMade(n.str("id") ?: "")
                "missao_mudou" -> Trigger.MissionChanged(n.str("id") ?: "", n.str("estado", false)?.also { st -> state(n, st) })
                "escolta_interceptou" -> Trigger.EscortIntercepted
                "missil_lancado" -> {
                    val by = n.str("por", false)
                    if (by != null && by != "inimigo" && by != "jogador") n.err("'por' deve ser inimigo ou jogador")
                    Trigger.MissileLaunched(by)
                }
                else -> Trigger.Destroyed
            }
        }

        fun cond(n: Node): Cond? {
            val spec = typeOf(n, "condição", Vocabulary.conditions) ?: return null
            return when (spec.name) {
                "tem_objeto" -> Cond.HasObject(n.str("objeto") ?: "")
                "sabe" -> Cond.Knows(n.str("fato") ?: "")
                "flag" -> Cond.Flag(n.str("nome") ?: "", n.bool("valor", true))
                "reputacao" -> {
                    val mn = n.int("min", false)
                    val mx = n.int("max", false)
                    if (mn == null && mx == null) n.err("informe 'min' ou 'max'")
                    Cond.Rep(n.str("faccao") ?: "", mn, mx)
                }
                "creditos" -> Cond.Credits(n.int("min") ?: 0)
                "deduziu" -> Cond.Deduced(n.str("id") ?: "")
                "tem_carga" -> Cond.HasCargo
                "papel" -> {
                    val v = n.str("valor") ?: ""
                    Cond.Role(RoleKind.values().firstOrNull { it.key == v } ?: run { n.err("'valor' deve ser mercador ou militar"); RoleKind.MERCHANT })
                }
                "missao" -> Cond.MissionIs(n.str("id") ?: "", state(n, n.str("estado") ?: ""))
                "contador" -> {
                    val mn = n.int("min", false); val mx = n.int("max", false)
                    if (mn == null && mx == null) n.err("informe 'min' ou 'max'")
                    Cond.Counter(n.str("nome") ?: "", mn, mx)
                }
                "casco" -> Cond.Hull(n.num("min", false), n.num("max", false)).also { if (it.min == null && it.max == null) n.err("informe 'min' ou 'max'") }
                "combustivel" -> Cond.Fuel(n.num("min", false), n.num("max", false)).also { if (it.min == null && it.max == null) n.err("informe 'min' ou 'max'") }
                "atracado" -> Cond.DockedAt(n.str("local", false))
                "corpo_vivo" -> Cond.BodyAlive(n.str("corpo") ?: "", n.bool("valor", true))
                "distancia" -> Cond.Distance(n.str("de") ?: "jogador", n.str("ate") ?: "", n.num("menorQue", false), n.num("maiorQue", false)).also { if (it.below == null && it.above == null) n.err("informe 'menorQue' ou 'maiorQue'") }
                "tempo_maior" -> Cond.TimeAfter(n.num("segundos") ?: 0.0)
                "aleatorio" -> Cond.Random((n.num("chance") ?: 0.5).also { if (it < 0.0 || it > 1.0) n.err("'chance' deve ficar entre 0 e 1") })
                "alvo" -> Cond.TargetIs(n.str("corpo") ?: "")
                "e" -> Cond.All(n.nodes("condicoes", true).mapNotNull { cond(it) })
                "ou" -> Cond.Any(n.nodes("condicoes", true).mapNotNull { cond(it) })
                else -> Cond.Not(n.child("condicao")?.let { cond(it) } ?: Cond.HasCargo)
            }
        }

        private fun state(n: Node, s: String): String {
            if (s !in setOf("ativa", "concluida", "falhou")) n.err("'estado' deve ser ativa, concluida ou falhou")
            return s
        }

        fun effect(n: Node): Effect? {
            val spec = typeOf(n, "efeito", Vocabulary.effects) ?: return null
            return when (spec.name) {
                "mensagem" -> Effect.Message(n.str("texto") ?: "")
                "reputacao" -> Effect.Rep(n.str("faccao") ?: "", n.int("delta") ?: 0)
                "creditos" -> Effect.Credits(n.int("delta") ?: 0)
                "dar_objeto" -> Effect.GiveObject(n.str("objeto") ?: "")
                "tirar_objeto" -> Effect.TakeObject(n.str("objeto") ?: "")
                "revelar_fato" -> Effect.Reveal(n.str("fato") ?: "")
                "flag" -> Effect.SetFlag(n.str("nome") ?: "", n.bool("valor", true))
                "dialogo" -> Effect.OpenDialogue(n.str("id") ?: "")
                "noticia" -> Effect.News(n.str("texto") ?: "")
                "final" -> Effect.End(n.str("id") ?: "")
                "ia" -> {
                    val key = n.str("comportamento") ?: ""
                    val mode = AiMode.values().firstOrNull { it.key == key } ?: run { n.err("'comportamento' deve ser perseguir, atacar, abordar, escoltar, patrulhar, fugir ou orbitar"); AiMode.ORBIT }
                    val route = n.strings("rota")
                    if (mode == AiMode.PATROL && route.size < 2) n.err("patrulhar exige 'rota' com ao menos 2 corpos")
                    Effect.Ai(n.str("corpo") ?: "", mode, route)
                }
                "alvo" -> Effect.SetTarget(n.str("corpo") ?: "")
                "contador" -> {
                    val v = n.int("valor", false)
                    val dl = n.int("delta", false)
                    if (v == null && dl == null) n.err("informe 'delta' ou 'valor'")
                    Effect.Counter(n.str("nome") ?: "", dl ?: 0, v)
                }
                "se" -> Effect.If(n.child("condicao")?.let { cond(it) } ?: Cond.HasCargo, n.nodes("entao").mapNotNull { effect(it) }, n.nodes("senao").mapNotNull { effect(it) })
                "executar" -> Effect.Run(n.str("bloco") ?: "")
                "agendar" -> Effect.Schedule(n.str("bloco") ?: "", (n.num("segundos") ?: 0.0).also { if (it < 0.0) n.err("'segundos' não pode ser negativo") })
                "emboscar" -> Effect.Ambush(n.str("corpo") ?: "", (n.num("distancia", false) ?: 20_000.0).also { if (it < 100.0) n.err("'distancia' deve ser pelo menos 100 m") })
                "revelar_corpo" -> Effect.RevealBody(n.str("corpo") ?: "")
                "ocultar_corpo" -> Effect.ConcealBody(n.str("corpo") ?: "")
                "destruir_corpo" -> Effect.DestroyBody(n.str("corpo") ?: "", n.bool("explosao", true))
                "casco" -> Effect.Hull(n.num("delta") ?: 0.0)
                "combustivel" -> Effect.Fuel(n.num("delta") ?: 0.0)
                "municao" -> Effect.Ammo(n.int("misseis", false) ?: 0, n.int("despistadores", false) ?: 0)
                "perder_carga" -> Effect.LoseCargo
                "missao" -> Effect.Mission(n.str("id") ?: "", state(n, n.str("estado") ?: ""))
                else -> {
                    val c = n.child("chance")
                    val chance = if (c == null) Chance(0.5, null, 0.0, 0.0, 1.0) else {
                        c.only(setOf("base", "faccao", "por", "min", "max"))
                        Chance(c.num("base") ?: 0.5, c.str("faccao", false), c.num("por", false) ?: 0.0, c.num("min", false) ?: 0.0, c.num("max", false) ?: 1.0)
                    }
                    Effect.Roll(chance, n.nodes("sucesso").mapNotNull { effect(it) }, n.nodes("falha").mapNotNull { effect(it) })
                }
            }
        }
    }

    private val ID = Regex("^[a-z0-9_]+$")
}
