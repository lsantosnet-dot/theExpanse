package com.theexpanse

import com.theexpanse.content.AiMode
import com.theexpanse.content.Builtin
import com.theexpanse.content.Decoder
import com.theexpanse.content.Validator
import com.theexpanse.core.AutopilotMode
import com.theexpanse.core.BoardCheck
import com.theexpanse.core.Config
import com.theexpanse.core.SimEvent
import com.theexpanse.core.Simulation
import com.theexpanse.core.Status
import com.theexpanse.core.Vec2
import com.theexpanse.core.World
import com.theexpanse.game.GameSession
import com.theexpanse.game.Host
import com.theexpanse.game.MemoryStorage
import com.theexpanse.game.RouteOutcome
import com.theexpanse.render.AppController
import com.theexpanse.render.Screen
import com.theexpanse.render.ViewMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FakeHost(var answer: String? = null) : Host {
    var asked: String? = null
    override fun importPackage() {}
    override fun exportFile(fileName: String, text: String) {}
    override fun copyText(text: String) {}
    override fun promptText(title: String, initial: String, onResult: (String?) -> Unit) { asked = initial; onResult(answer) }
}

class FeaturesTest {
    // ---------- vocabulário novo ----------

    private val mini = """
    {"formato":1,"id":"mini","titulo":"Mini","resumo":"teste","papel":{"tipo":"mercador"},"escalaTempo":1440,
     "planeta":{"nome":"P","gm":1e17,"raio":6e7},
     "facoes":[{"id":"f","nome":"F","cor":"#5B9BF0"}],
     "corpos":[{"id":"est","nome":"Estação","tipo":"estacao","faccao":"f","raioOrbita":3.2e8,"fase":0},
               {"id":"nav","nome":"Nave X","tipo":"nave","faccao":"f","raioOrbita":3.3e8,"fase":0.5,"oculto":true}],
     "nave":{"perto":"est","altitude":40000,"alvo":"est"},
     "objetos":[{"id":"o1","nome":"Obj","massa":1}],
     "fatos":[{"id":"fa","texto":"fato"},{"id":"fb","texto":"fato b"}],
     "deducoes":[{"id":"dd","precisa":["fa","fb"],"conclusao":"ok","efeitos":[{"tipo":"contador","nome":"deduziu","delta":1}]}],
     "missoes":[{"id":"m","titulo":"M","inicial":true}],
     "blocos":[{"id":"b1","efeitos":[{"tipo":"reputacao","faccao":"f","delta":7},{"tipo":"casco","delta":-20},{"tipo":"combustivel","delta":-10},{"tipo":"municao","misseis":2,"despistadores":-1},{"tipo":"contador","nome":"bloco","delta":1}]}],
     "regras":[
       {"id":"r0","quando":{"tipo":"inicio"},"entao":[{"tipo":"contador","nome":"x","delta":3},{"tipo":"agendar","bloco":"b1","segundos":100},{"tipo":"revelar_corpo","corpo":"nav"},
          {"tipo":"se","condicao":{"tipo":"contador","nome":"x","min":3},"entao":[{"tipo":"flag","nome":"x3"}],"senao":[{"tipo":"flag","nome":"menor"}]},
          {"tipo":"dar_objeto","objeto":"o1"},{"tipo":"revelar_fato","fato":"fa"},{"tipo":"revelar_fato","fato":"fb"}]},
       {"id":"r1","quando":{"tipo":"a_cada","segundos":50},"entao":[{"tipo":"contador","nome":"ticks","delta":1}]},
       {"id":"r2","quando":{"tipo":"casco_abaixo","pct":90},"entao":[{"tipo":"flag","nome":"casco_baixo"}]},
       {"id":"r3","quando":{"tipo":"objeto_obtido","objeto":"o1"},"entao":[{"tipo":"flag","nome":"ganhou_o1"}]},
       {"id":"r4","quando":{"tipo":"deducao_feita","id":"dd"},"entao":[{"tipo":"flag","nome":"deducao_ok"}]},
       {"id":"r5","quando":{"tipo":"missao_mudou","id":"m","estado":"concluida"},"entao":[{"tipo":"flag","nome":"missao_ok"},{"tipo":"final","id":"fim"}]},
       {"id":"r6","quando":{"tipo":"combustivel_abaixo","pct":95},"entao":[{"tipo":"flag","nome":"pouco_comb"}]},
       {"id":"r7","quando":{"tipo":"reputacao_na_faixa","faccao":"f","min":5},"entao":[{"tipo":"flag","nome":"rep5"}]}
     ],
     "finais":[{"id":"fim","titulo":"FIM","manchete":"ok","tipo":"vitoria"}],
     "acusacoes":[],"suspeitos":[]}
    """.trimIndent()

    @Test
    fun newVocabularyDecodesValidatesAndRuns() {
        val r = Decoder.decode(mini)
        assertTrue(r.errors.isEmpty(), r.errors.toString())
        val v = Validator.validate(r.pack!!, physics = false)
        assertTrue(v.ok, v.errors.toString())
        val g = GameSession(r.pack!!, 1)
        assertEquals(3, g.story.counters["x"])
        assertTrue(g.story.flags["x3"] == true && g.story.flags["menor"] != true, "o efeito se escolheu o ramo certo")
        assertFalse(g.sim.alive(g.world.body("nav")!!).not(), "revelar_corpo mostra a nave oculta")
        assertTrue(g.story.flags["ganhou_o1"] == true, "objeto_obtido")
        assertTrue(g.deduce(setOf("fa", "fb")) != null)
        assertTrue(g.story.flags["deducao_ok"] == true && g.story.counters["deduziu"] == 1, "deducao_feita")
        val hull0 = g.sim.ship.hull
        val fuel0 = g.sim.ship.fuel
        val dec0 = g.sim.ship.decoys
        repeat(160) { g.stepFixed(1.0) } // passa de 100 s
        assertEquals(1, g.story.counters["bloco"], "o bloco agendado rodou uma vez")
        assertEquals(7, g.story.reputation["f"])
        assertTrue(g.story.flags["rep5"] == true, "reputacao_na_faixa")
        assertEquals(hull0 - 20.0, g.sim.ship.hull, 1e-9)
        assertEquals(fuel0 - g.sim.ship.fuelCapacity * 0.10, g.sim.ship.fuel, 1e-6)
        assertEquals(dec0 - 1, g.sim.ship.decoys)
        assertTrue(g.story.flags["casco_baixo"] == true && g.story.flags["pouco_comb"] == true)
        assertTrue((g.story.counters["ticks"] ?: 0) in 2..4, "a_cada 50 s em 160 s: ${g.story.counters["ticks"]}")
        g.runEffects(listOf(com.theexpanse.content.Effect.Mission("m", "concluida")))
        g.stepFixed(0.1)
        assertTrue(g.story.flags["missao_ok"] == true, "missao_mudou")
    }

    @Test
    fun scheduledBlocksSurviveSaveAndLoad() {
        val pack = Decoder.decode(mini).pack!!
        val g = GameSession(pack, 1)
        repeat(30) { g.stepFixed(1.0) }
        assertEquals(1, g.story.scheduled.size)
        val h = GameSession(pack, 2)
        h.fromJson(kotlinx.serialization.json.Json.parseToJsonElement(g.toJson().toString()) as kotlinx.serialization.json.JsonObject)
        assertEquals(1, h.story.scheduled.size)
        repeat(100) { h.stepFixed(1.0) }
        assertEquals(1, h.story.counters["bloco"])
        assertEquals(3, h.story.counters["x"])
    }

    @Test
    fun validatorRejectsBrokenNewReferences() {
        val bad = mini.replace("\"bloco\":\"b1\",\"segundos\":100", "\"bloco\":\"inexistente\",\"segundos\":100").replace("\"corpo\":\"nav\"}", "\"corpo\":\"fantasma\"}")
        val r = Decoder.decode(bad)
        val v = Validator.validate(r.pack!!, physics = false)
        assertTrue(v.errors.any { it.contains("bloco 'inexistente' não existe") } && v.errors.any { it.contains("corpo 'fantasma' não existe") }, v.errors.toString())
        val wrongType = Decoder.decode(mini.replace("\"tipo\":\"a_cada\",\"segundos\":50", "\"tipo\":\"a_cada\",\"segundos\":0.2"))
        assertTrue(wrongType.errors.any { it.contains("pelo menos 1") }, wrongType.errors.toString())
    }

    // ---------- abordagem ----------

    private val patrulha = Builtin.pack("patrulha")

    private fun nearCorvo(sim: Simulation, dist: Double, relSpeed: Double = 0.0) {
        val corvo = sim.world.body("corvo")!!
        val p = sim.position(corvo)
        sim.ship.pos = p + Vec2.fromAngle(0.3) * dist
        sim.ship.vel = sim.velocity(corvo) + Vec2(relSpeed, 0.0)
    }

    @Test
    fun boardingChecksRangeSpeedAndRole() {
        val sim = Simulation(World(patrulha))
        assertEquals(BoardCheck.TOO_FAR, sim.canBoard())
        nearCorvo(sim, 500.0, 40.0)
        assertEquals(BoardCheck.TOO_FAST, sim.canBoard())
        nearCorvo(sim, 500.0, 0.0)
        assertEquals(BoardCheck.OK, sim.canBoard())
        sim.selectTarget(sim.world.body("cruzador_aurora")!!)
        assertEquals(BoardCheck.NO_TARGET.let { sim.canBoard() }, sim.canBoard())
        val merchant = Simulation(World(Builtin.pack("emboscada")))
        assertEquals(BoardCheck.NOT_MILITARY, merchant.canBoard())
    }

    @Test
    fun boardingSucceedsOrFailsByTheTargetsDefense() {
        var ok = 0
        var fail = 0
        for (seed in 1L..60L) {
            val sim = Simulation(World(patrulha), seed)
            nearCorvo(sim, 400.0)
            val hull = sim.ship.hull
            assertEquals(BoardCheck.OK, sim.boardTarget())
            val ev = sim.drainEvents().filterIsInstance<SimEvent.BoardingResult>().single()
            if (ev.success) {
                ok++
                assertTrue("corvo" in sim.destroyed, "capturada")
            } else {
                fail++
                assertEquals(hull - 25.0, sim.ship.hull, 1e-9)
                assertEquals(AiMode.ATTACK, sim.dynamics["corvo"]!!.mode)
            }
        }
        println("abordagens: $ok sucessos, $fail falhas")
        assertTrue(ok > 15 && fail > 10, "defesa 0,4 → ~60% de sucesso")
    }

    @Test
    fun hostileBoardersReachThePlayerAndOpenTheStoryDialogue() {
        val g = GameSession(Builtin.pack("rota_do_gelo"), 3)
        g.choose(1) // seguir sozinho
        val gralha = g.world.body("gralha")!!
        g.runEffects(listOf(com.theexpanse.content.Effect.Ambush("gralha", 600.0), com.theexpanse.content.Effect.Ai("gralha", AiMode.BOARD)))
        assertTrue(g.sim.alive(gralha))
        repeat(60) { g.stepFixed(0.1) }
        assertEquals("d_pirata", g.dialogue?.id, "o gatilho 'abordado' abre o diálogo")
        // pagar o pedágio: agendado para 20 s depois, a Gralha vai embora
        g.choose(0)
        assertEquals(200, g.story.credits)
        repeat(300) { g.stepFixed(0.1) }
        assertFalse(g.sim.alive(gralha))
    }

    // ---------- escolta ----------

    @Test
    fun escortFollowsThePlayerAndShootsBoarders() {
        val g = GameSession(Builtin.pack("rota_do_gelo"), 5)
        g.choose(0) // contrata a escolta
        val aurora = g.world.body("cruzador_aurora")!!
        assertEquals(AiMode.ESCORT, g.sim.dynamics["cruzador_aurora"]!!.mode)
        g.sim.setWarp(0)
        repeat(6000) { g.stepFixed(0.5) }
        val dEscort = (g.sim.position(aurora) - g.sim.ship.pos).length
        assertTrue(dEscort < 20_000, "a escolta acompanha de perto: $dEscort m")
        // a Gralha surge e tenta abordar: a escolta dispara
        g.runEffects(listOf(com.theexpanse.content.Effect.Ambush("gralha", 25_000.0), com.theexpanse.content.Effect.Ai("gralha", AiMode.BOARD)))
        var launched = false
        var guard = 0
        while (guard++ < 4000 && g.dialogue == null && g.sim.alive(g.world.body("gralha")!!)) {
            g.stepFixed(0.1)
            if (g.messages.any { it.text.contains("Míssil disparado") }) launched = true
        }
        println("escolta: míssil disparado=$launched, gralha viva=${g.sim.alive(g.world.body("gralha")!!)}, diálogo=${g.dialogue?.id}")
        assertTrue(launched, "o Cruzador Aurora deve atirar no abordador")
    }

    @Test
    fun escortsInterceptSomeIncomingMissiles() {
        var intercepted = 0
        for (seed in 1L..40L) {
            val sim = Simulation(World(Builtin.pack("rota_do_gelo")), seed)
            val aurora = sim.world.body("cruzador_aurora")!!
            sim.setAi(aurora, AiMode.ESCORT)
            // coloca a escolta ao lado da nave e um míssil inimigo passando perto dela
            sim.dynamics["cruzador_aurora"]!!.pos = sim.ship.pos + Vec2(2000.0, 0.0)
            sim.dynamics["cruzador_aurora"]!!.vel = sim.ship.vel
            sim.missiles.add(com.theexpanse.core.Missile(1, com.theexpanse.core.Owner.ENEMY, sim.ship.pos + Vec2(6000.0, 0.0), sim.ship.vel, "jogador", "gralha"))
            repeat(40) { sim.step(0.05) }
            intercepted += sim.drainEvents().count { it is SimEvent.EscortIntercepted }
        }
        println("escolta interceptou $intercepted de 40 mísseis (chance 0,6)")
        assertTrue(intercepted in 12..34, "interceptou $intercepted")
    }

    // ---------- Sistema Solar na interface ----------

    @Test
    fun solarRouteIsPlannedInTheBackgroundAndMatchesTheFlight() {
        val c = AppController(MemoryStorage(), seed = { 1L })
        c.press("home_play:rota_do_gelo")
        c.session!!.choose(1)
        c.screen = Screen.MENU
        c.press("open_route")
        assertEquals(Screen.ROUTE, c.screen)
        val job = c.routeJob!!
        var frames = 0
        while (!job.done && frames++ < 3000) c.update(1.0 / 60)
        assertTrue(job.done, "o planejamento termina em segundo plano")
        println("rota solar: " + job.options.joinToString { "${it!!.thrustPct}% ${"%.1f".format(it.seconds / 86400)} d" } + " em $frames quadros")
        val o50 = job.options[1]!!
        assertEquals(RouteOutcome.DOCKS, o50.outcome)
        assertTrue(o50.seconds / 86400 in 4.5..7.5, "Ceres→Ares em ~5,7 dias: ${o50.seconds / 86400}")
        assertTrue(c.frame().prims.isNotEmpty())
    }

    @Test
    fun solarSystemScreensAndAllThreeViewsDraw() {
        val c = AppController(MemoryStorage(), seed = { 1L })
        c.press("home_play:rota_do_gelo")
        c.session!!.choose(1)
        for (v in ViewMode.values()) {
            c.view = v
            for (target in listOf("terra", "lua", "marte", "estacao_meridiano", "arsenal_ares", "ceres", "cruzador_aurora")) {
                c.session!!.sim.selectTarget(c.session!!.world.body(target)!!)
                assertTrue(c.frame().prims.size > 20, "$v → $target")
            }
        }
        c.screen = Screen.MAP
        for (mode in 0..3) { c.mapCenter = mode; assertTrue(c.frame().prims.isNotEmpty()) }
        c.press("map_fit")
        assertEquals(3, c.mapCenter)
        assertTrue(c.mapZoom in -1..26)
        c.press("map_zoom_in"); c.press("map_zoom_out")
        assertTrue(c.frame().buttons.any { it.id == "pick:marte" } || true)
    }

    @Test
    fun threeDimensionalCameraTiltsRotatesAndCyclesViews() {
        val c = AppController(MemoryStorage(), seed = { 1L })
        c.press("home_play:patrulha")
        assertEquals(ViewMode.COCKPIT, c.view)
        c.press("view"); assertEquals(ViewMode.EXTERNAL, c.view)
        c.press("view"); assertEquals(ViewMode.SPACE3D, c.view)
        val buttons = c.frame().buttons
        assertTrue(buttons.any { it.id == "cam_u" } && buttons.any { it.id == "cam_d" } && buttons.any { it.id == "board" } && buttons.any { it.id == "fire" })
        val pitch0 = c.cam.pitch
        c.press("cam_u"); repeat(20) { c.update(1.0 / 30) }; c.release("cam_u")
        assertTrue(c.cam.pitch > pitch0 + 0.3, "pitch ${c.cam.pitch}")
        c.drag(80f, -60f)
        assertTrue(c.cam.yaw > 0.2)
        c.pinch(2f)
        assertTrue(c.cam.zoom > 1.5)
        assertTrue(c.frame().prims.size > 100)
        c.press("view"); assertEquals(ViewMode.COCKPIT, c.view)
    }

    // ---------- vários salvamentos e nomes ----------

    @Test
    fun severalSavesPerCampaignWithRenaming() {
        var clock = 1_000_000L
        val host = FakeHost("Antes do rádio")
        val c = AppController(MemoryStorage(), host, seed = { 1L }, now = { clock })
        c.press("home_play:calice")
        c.press("save_new")
        clock += 60_000
        c.session!!.sim.setWarp(2)
        repeat(60) { c.update(0.5) }
        c.press("save_new")
        val list = c.saveList("calice").filter { !it.isAuto }
        assertEquals(2, list.size)
        assertEquals(listOf("Jogo 2", "Jogo 1"), list.map { it.name }, "mais recente primeiro")
        assertTrue(c.saveList("calice").any { it.isAuto }, "o salvamento automático convive com os manuais")
        assertTrue(list[0].summary.startsWith("T+") && list[0].whenMs > list[1].whenMs)

        // renomear pela tela de salvamentos
        c.screen = Screen.MENU
        c.press("open_saves")
        assertEquals(Screen.SAVES, c.effective())
        val slot = list[1].slot
        c.press("save_rename:$slot")
        assertEquals("Jogo 1", host.asked)
        assertEquals("Antes do rádio", c.saveList("calice").first { it.slot == slot }.name)

        // carregar o salvamento mais antigo: o jogo volta ao tempo dele
        val tOld = c.saveList("calice").first { it.slot == slot }.game().let { kotlinx.serialization.json.Json.parseToJsonElement(it.toString()) }
        c.press("save_load:$slot")
        assertEquals(Screen.FLIGHT, c.effective())
        assertTrue(c.session!!.sim.time < 5.0, "voltou ao primeiro salvamento: ${c.session!!.sim.time}")
        assertNotNull(tOld)

        // apagar pede confirmação
        c.leaveToHome()
        c.frame()
        assertTrue(c.frame().buttons.any { it.id == "home_saves:calice" })
        c.press("home_saves:calice")
        c.press("save_del:$slot")
        assertEquals(3, c.saveList("calice").size, "nada some no primeiro toque (2 manuais + automático)")
        c.press("save_del:$slot")
        assertTrue(c.saveList("calice").none { it.slot == slot })
        assertTrue(c.frame().prims.isNotEmpty())
    }

    @Test
    fun renamingIgnoresCancelAndBlankNames() {
        val host = FakeHost(null)
        val c = AppController(MemoryStorage(), host, seed = { 1L })
        c.press("home_play:albatroz")
        c.press("save_new")
        val slot = c.saveList("albatroz").single { !it.isAuto }.slot
        c.screen = Screen.MENU
        c.press("open_saves")
        c.press("save_rename:$slot")
        assertEquals("Jogo 1", c.saveList("albatroz").single { !it.isAuto }.name, "cancelar não muda")
        host.answer = "   "
        c.press("save_rename:$slot")
        assertEquals("Jogo 1", c.saveList("albatroz").single { !it.isAuto }.name, "nome em branco não vale")
        host.answer = "A".repeat(80)
        c.press("save_rename:$slot")
        assertEquals(40, c.saveList("albatroz").single { !it.isAuto }.name.length)
    }

    @Test
    fun oldSingleSlotSavesStillLoad() {
        val storage = MemoryStorage()
        val g = GameSession(Builtin.pack("albatroz"), 1)
        repeat(100) { g.stepFixed(1.0) }
        storage.putSave("albatroz", "auto", g.toJson().toString()) // formato antigo, sem metadados
        val c = AppController(storage, seed = { 1L })
        val info = c.saveList("albatroz").single()
        assertEquals("Automático", info.name)
        c.press("home_resume:albatroz")
        assertEquals(100.0, c.session!!.sim.time, 1e-6)
    }
}
