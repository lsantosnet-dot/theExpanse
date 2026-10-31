package com.theexpanse

import com.theexpanse.content.Builtin
import com.theexpanse.content.Template
import com.theexpanse.content.Validator
import com.theexpanse.core.Config
import com.theexpanse.core.Status
import com.theexpanse.game.MemoryStorage
import com.theexpanse.render.AppController
import com.theexpanse.render.Layout
import com.theexpanse.render.Screen
import com.theexpanse.render.ViewMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class UiTest {
    private fun controller(): AppController = AppController(MemoryStorage(), seed = { 1L })

    private fun AppController.tap(id: String) {
        val b = frame().buttons.firstOrNull { it.id == id } ?: error("botão '$id' não está na tela $screen")
        assertTrue(b.enabled, "botão $id desabilitado")
        assertTrue(hit(b.x + b.w / 2, b.y + b.h / 2)?.id == id || hit(b.x + b.w / 2, b.y + b.h / 2) != null)
        press(id)
        release(id)
    }

    @Test
    fun homeListsBuiltinCampaigns() {
        val c = controller()
        val f = c.frame()
        assertEquals(Screen.HOME, c.effective())
        assertTrue(f.buttons.any { it.id == "home_play:calice" } && f.buttons.any { it.id == "home_play:albatroz" })
        assertTrue(f.buttons.any { it.id == "home_import" } && f.buttons.any { it.id == "home_export" })
    }

    @Test
    fun everyScreenDrawsInsideTheLayoutAndHasWorkingButtons() {
        val c = controller()
        c.tap("home_play:albatroz")
        for (sc in listOf(Screen.FLIGHT, Screen.MAP, Screen.HOLD, Screen.NEWS, Screen.NOTEBOOK, Screen.MENU)) {
            c.screen = sc
            val f = c.frame()
            assertTrue(f.prims.isNotEmpty(), "$sc vazia")
            for (b in f.buttons) assertTrue(b.x >= 0 && b.y >= 0 && b.x + b.w <= Layout.W + 0.5f && b.y + b.h <= Layout.H + 0.5f, "botão ${b.id} fora da tela em $sc")
        }
        c.screen = Screen.FLIGHT
        c.view = ViewMode.EXTERNAL
        assertTrue(c.frame().prims.isNotEmpty())
    }

    @Test
    fun flightControlsHoldAndRelease() {
        val c = controller()
        c.tap("home_play:calice")
        val sim = c.session!!.sim
        c.press("turn_l"); c.press("engine")
        assertEquals(1, sim.manualTurn); assertTrue(sim.manualEngine)
        c.update(1.0 / 60)
        assertTrue(sim.ship.burnAccel > 0.0 && sim.ship.turnAlpha > 0.0)
        c.release("turn_l"); c.release("engine")
        assertEquals(0, sim.manualTurn); assertTrue(!sim.manualEngine)
        c.tap("ap_intercept")
        // a rota é verificada em segundo plano antes de engajar
        assertNotNull(c.preflight)
        var frames = 0
        while (c.preflight != null && frames++ < 5000) c.update(1.0 / 60)
        assertEquals("INTERCEPTAR", sim.autopilot.label)
        c.tap("warp_3")
        assertEquals(3, sim.warpIndex)
        c.tap("view")
        assertEquals(ViewMode.EXTERNAL, c.view)
    }

    @Test
    fun dialogueTakesOverAndChoicesWorkByTouch() {
        val c = controller()
        c.tap("home_play:calice")
        val g = c.session!!
        g.sim.setAutopilot(com.theexpanse.core.AutopilotMode.INTERCEPT)
        c.tap("warp_2")
        var guard = 0
        while (c.effective() != Screen.DIALOGUE && guard++ < 100_000) c.update(1.0 / 30)
        assertEquals(Screen.DIALOGUE, c.effective())
        val before = g.sim.time
        c.update(1.0)
        assertEquals(before, g.sim.time, "a simulação pausa durante o diálogo")
        val buttons = c.frame().buttons
        assertTrue(buttons.count { it.id.startsWith("choice:") } == 4)
        c.tap("choice:2")
        assertEquals(Screen.FLIGHT, c.effective())
        assertEquals(1, g.story.flags.count { it.key == "escolta" && it.value })
    }

    @Test
    fun dockingOpensTheStationAndUndockingReturnsToFlight() {
        val c = controller()
        c.tap("home_play:albatroz")
        val g = c.session!!
        g.sim.setThrust(100)
        g.sim.setAutopilot(com.theexpanse.core.AutopilotMode.INTERCEPT)
        var guard = 0
        while (g.sim.status != Status.DOCKED && guard++ < 400_000) g.stepFixed(Config.SUBSTEP)
        assertEquals(Screen.CINEMATIC, c.effective(), "a cena de atracação vem antes da estação")
        assertTrue(c.frame().buttons.any { it.id == "skip" })
        c.tap("skip")
        assertEquals(Screen.STATION, c.effective())
        assertTrue(c.frame().buttons.any { it.id == "undock" })
        c.tap("undock")
        assertEquals(Screen.CINEMATIC, c.effective(), "a saída também tem cena")
        assertEquals(Status.DOCKED, g.sim.status, "só desatraca no fim da cena")
        c.tap("skip")
        assertEquals(Screen.FLIGHT, c.effective())
        assertEquals(Status.FLYING, g.sim.status)
        // logo após desatracar a nave não atraca de novo
        repeat(100) { g.stepFixed(Config.SUBSTEP) }
        assertEquals(Status.FLYING, g.sim.status)
    }

    @Test
    fun routePlanScreenPlansAndConfirms() {
        val c = controller()
        c.tap("home_play:calice")
        c.screen = Screen.MENU
        c.tap("open_route")
        assertEquals(Screen.ROUTE, c.screen)
        assertEquals(3, c.routeOptions.size)
        assertTrue(c.routeOptions.all { it.outcome == com.theexpanse.game.RouteOutcome.DOCKS })
        val t = c.routeOptions.map { it.seconds }
        println("rota: " + c.routeOptions.joinToString { "${it.thrustPct}% ${it.seconds.toInt()} s, ${it.fuelUsed.toInt()} kg" })
        assertTrue(t[0] > t[1] && t[1] > t[2], "mais empuxo chega antes")
        assertTrue(Math.abs(t[1] - 27_100) / 27_100 <= 0.05)
        c.tap("route_sel:2")
        c.tap("route_go")
        assertEquals(Screen.FLIGHT, c.screen)
        assertEquals(100, c.session!!.sim.thrustPct)
        assertEquals("INTERCEPTAR", c.session!!.sim.autopilot.label)
    }

    @Test
    fun notebookDeducesAndAccuses() {
        val c = controller()
        c.tap("home_play:albatroz")
        val g = c.session!!
        for (f in listOf("f_codigo_marte", "f_codigo_roubado")) g.runEffects(listOf(com.theexpanse.content.Effect.Reveal(f)))
        c.screen = Screen.NOTEBOOK
        c.frame()
        c.tap("fact:f_codigo_marte"); c.tap("fact:f_codigo_roubado")
        c.tap("deduce")
        assertTrue("ded_armacao" in g.story.deduced)
        c.tap("suspect:s_marte")
        c.tap("accuse")
        assertEquals("guerra_injusta", g.ending?.id)
        assertEquals(Screen.END, c.effective())
        assertTrue(c.frame().buttons.any { it.id == "restart" } && c.frame().buttons.any { it.id == "continue" })
        c.tap("restart")
        assertEquals(Screen.FLIGHT, c.effective())
    }

    @Test
    fun importAcceptsValidPackagesAndRejectsBrokenOnes() {
        val c = controller()
        val good = Builtin.text("albatroz")!!.replace("\"id\": \"albatroz\"", "\"id\": \"albatroz_b\"").replace("O silêncio do Albatroz", "Albatroz B")
        assertTrue(c.importText("b.json", good))
        assertEquals(6, c.entries.size)
        assertTrue(c.storage.packages().containsKey("albatroz_b"))
        c.homePage = 1
        c.frame()
        assertTrue(c.frame().buttons.any { it.id == "home_del:albatroz_b" })

        assertTrue(!c.importText("calice2.json", Builtin.text("calice")!!), "id de campanha incluída é recusado")
        assertEquals(Screen.ERRORS, c.screen)
        assertTrue(c.frame().buttons.any { it.id == "home_errors_copy" })
        c.tap("home_errors_close")

        val broken = Builtin.text("albatroz")!!.replace("\"id\": \"albatroz\"", "\"id\": \"albatroz_c\"").replace("{\"tipo\": \"dar_objeto\", \"objeto\": \"caixa_preta\"}, ", "")
        assertTrue(!c.importText("c.json", broken))
        assertTrue(c.errors.any { it.contains("nunca é alcançável") })
        assertEquals(6, c.entries.size, "pacote inválido não é instalado")
        c.press("back")
        c.homePage = 1
        c.frame()
        c.tap("home_del:albatroz_b")
        assertEquals(5, c.entries.size)
    }

    @Test
    fun autosaveAndResume() {
        val c = controller()
        c.tap("home_play:albatroz")
        c.session!!.sim.setThrust(100)
        c.session!!.sim.setAutopilot(com.theexpanse.core.AutopilotMode.INTERCEPT)
        c.tap("warp_2")
        repeat(300) { c.update(1.0 / 30) }
        val t = c.session!!.sim.time
        assertTrue(t > 50)
        c.leaveToHome()
        assertTrue(c.storage.saves("albatroz").isNotEmpty())
        c.frame()
        c.tap("home_resume:albatroz")
        assertEquals(t, c.session!!.sim.time, 1e-9)
    }

    @Test
    fun dockingCinematicPlaysForItsDurationThenShowsTheStation() {
        val c = controller()
        c.tap("home_play:albatroz")
        val g = c.session!!
        g.sim.setThrust(100)
        g.sim.setAutopilot(com.theexpanse.core.AutopilotMode.INTERCEPT)
        var guard = 0
        while (g.sim.status != Status.DOCKED && guard++ < 400_000) g.stepFixed(Config.SUBSTEP)
        assertEquals(Screen.CINEMATIC, c.effective())
        val frames = ArrayList<Int>()
        var t = 0.0
        while (c.effective() == Screen.CINEMATIC && t < 20.0) { c.update(0.1); frames.add(c.frame().prims.size); t += 0.1 }
        assertTrue(t in 6.5..8.0, "duração ${t}s")
        assertTrue(frames.dropLast(1).all { it > 50 })
        assertEquals(Screen.STATION, c.effective())
    }

    @Test
    fun cameraRotatesByHoldDragAndZoom() {
        val c = controller()
        c.tap("home_play:calice")
        assertTrue(c.cam.centered)
        c.press("cam_l")
        repeat(30) { c.update(1.0 / 30) }
        c.release("cam_l")
        assertTrue(c.cam.yaw > 1.0, "yaw ${c.cam.yaw}")
        c.tap("cam_reset")
        assertTrue(c.cam.centered)
        c.drag(100f)
        assertTrue(c.cam.yaw > 0.3)
        c.tap("zoom_in"); c.tap("zoom_in")
        assertTrue(c.cam.zoom > 1.5)
        c.pinch(0.1f)
        assertTrue(c.cam.zoom >= 0.5)
        c.view = ViewMode.COCKPIT; assertTrue(c.frame().prims.isNotEmpty())
        c.view = ViewMode.EXTERNAL; assertTrue(c.frame().prims.isNotEmpty())
    }

    @Test
    fun combatButtonsFollowTheRole() {
        val merchant = controller()
        merchant.tap("home_play:calice")
        val mb = merchant.frame().buttons
        assertTrue(mb.any { it.id == "decoy" } && mb.none { it.id == "fire" })
        assertTrue(!mb.first { it.id == "decoy" }.enabled, "sem míssil por perto o despistador fica desabilitado")

        val soldier = controller()
        soldier.tap("home_play:patrulha")
        val sb = soldier.frame().buttons
        assertTrue(sb.any { it.id == "fire" } && sb.none { it.id == "decoy" })
        assertTrue(!sb.first { it.id == "fire" }.enabled, "alvo fora de alcance")
    }

    @Test
    fun arrivalEtaCountsDownAndMatchesTheRoutePlan() {
        val c = controller()
        c.tap("home_play:calice")
        c.session!!.sim.setThrust(50)
        repeat(120) { c.update(1.0 / 30) }
        val eta = c.eta.remaining(c.session!!.sim)
        assertNotNull(eta, "o cálculo termina em poucos quadros")
        assertTrue(Math.abs(eta!! - 27_100) / 27_100 <= 0.05, "ETA $eta")
        c.session!!.sim.setAutopilot(com.theexpanse.core.AutopilotMode.INTERCEPT)
        c.tap("warp_3")
        repeat(30) { c.update(1.0 / 30) }
        val later = c.eta.remaining(c.session!!.sim)!!
        assertTrue(later < eta, "a contagem regressiva diminui")
        val panel = c.frame()
        assertTrue(panel.prims.any { it is com.theexpanse.render.Txt && it.text.startsWith("CHEGADA") })
        assertTrue(panel.prims.any { it is com.theexpanse.render.Txt && it.text.startsWith("TEMPO REAL") })
    }

    @Test
    fun cruiseScaleTurnsDaysIntoMinutes() {
        val sim = com.theexpanse.core.Simulation(com.theexpanse.core.World(Builtin.pack("calice")))
        assertEquals(1440.0, sim.world.timeScale)
        assertEquals(1440.0, sim.warpLevels[3])
        // 5 dias de jogo na escala de cruzeiro = 5 minutos reais
        assertEquals(5.0 * 60.0, 5 * 86_400.0 / sim.warpLevels[3], 1e-6)
    }

    @Test
    fun realTimeEstimateMatchesWhatTheClockActuallyTakes() {
        val pack = Builtin.pack("calice")
        // sem limites extras: no nível de cruzeiro, medindo quadros de 1/30 s até atracar
        val sim = com.theexpanse.core.Simulation(com.theexpanse.core.World(pack))
        sim.setThrust(50); sim.setAutopilot(com.theexpanse.core.AutopilotMode.INTERCEPT); sim.setWarp(3)
        val eta = com.theexpanse.game.RoutePlanner.plan(sim, listOf(50)).single().seconds
        val predicted = sim.realSecondsFor(eta)
        var frames = 0
        while (sim.status == Status.FLYING && frames < 200_000) { sim.advance(1.0 / 30); frames++ }
        val actual = frames / 30.0
        println("Cálice a ×1.440: previsto ${predicted.toInt()} s reais, medido ${actual.toInt()} s")
        assertEquals(Status.DOCKED, sim.status)
        assertTrue(Math.abs(predicted - actual) / actual < 0.2, "previsto $predicted, medido $actual")

        // com um atacante longe, o teto cresce com a distância; perto, cai para ×60 (uma hora de jogo por minuto real)
        val capped = com.theexpanse.core.Simulation(com.theexpanse.core.World(pack))
        val frigate = capped.world.body("fragata_tribunal")!!
        capped.setAi(frigate, com.theexpanse.content.AiMode.ATTACK)
        capped.setWarp(3)
        val far = capped.distanceTo(frigate)
        assertEquals(minOf(1440.0, 60.0 * maxOf(1.0, far / Config.HOSTILE_CAP_RANGE)), capped.plannedWarp(), 1e-6)
        capped.dynamics[frigate.id]!!.pos = capped.ship.pos + com.theexpanse.core.Vec2(100_000.0, 0.0)
        assertEquals(60.0, capped.plannedWarp(), 1e-6)
        assertTrue(capped.realSecondsFor(8520.0) in 120.0..300.0, "2 h 22 min a ×60 levam uns 2-3 min reais")
    }

    @Test
    fun etaReactsToManeuversWithinAFewSeconds() {
        val c = controller()
        c.tap("home_play:calice")
        val sim = c.session!!.sim
        sim.setThrust(50)
        repeat(150) { c.update(1.0 / 30) }
        val before = c.eta.remaining(sim)!!
        // uma manobra: +3 km/s na direção do alvo
        val dir = sim.relativePosition().unit()
        sim.ship.vel = sim.ship.vel + dir * 3000.0
        var t = 0.0
        var after = before
        while (t < 6.0) { c.update(1.0 / 30); t += 1.0 / 30; after = c.eta.remaining(sim)!! }
        println("ETA antes ${before.toInt()} s, 6 s depois da manobra ${after.toInt()} s")
        assertTrue(after < before - 300, "a estimativa deveria cair bem mais que a contagem regressiva: $before → $after")
    }

    @Test
    fun strandedWithoutFuelCanCallRescue() {
        val c = controller()
        c.tap("home_play:albatroz")
        val g = c.session!!
        assertTrue(c.frame().buttons.none { it.id == "rescue" })
        g.sim.ship.fuel = 0.0
        assertTrue(g.stranded)
        val before = g.story.credits
        c.tap("rescue")
        assertTrue(g.sim.ship.fuel > 0.0 && !g.stranded)
        assertTrue(g.story.credits <= before)
        assertTrue(c.frame().buttons.none { it.id == "rescue" })
    }
}
