package com.theexpanse

import com.theexpanse.core.AutopilotMode
import com.theexpanse.core.Status
import com.theexpanse.game.MemoryStorage
import com.theexpanse.render.AppController
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LandingTest {
    private data class Result(val status: Status, val landed: String?, val crashed: String?, val seconds: Double, val fuelPct: Double, val speed: Double)

    private fun fly(bodyId: String, pct: Int): Result {
        val c = AppController(MemoryStorage(), seed = { 1L })
        c.press("home_play:rota_do_gelo")
        val sim = c.session!!.sim
        sim.selectTarget(sim.bodies.indexOf(sim.world.body(bodyId)!!))
        sim.setThrust(pct)
        sim.setAutopilot(AutopilotMode.INTERCEPT)
        val t0 = sim.time
        val f0 = sim.ship.fuel
        var n = 0
        var last = 0.0
        while (sim.status == Status.FLYING && n++ < 40_000_000) {
            val d = sim.distanceTo()
            sim.step(when { d > 2e10 -> 0.7; d > 2e9 -> 0.5; d > 2e7 -> 0.28; else -> 0.14 })
            if (d < 1e8) last = (sim.ship.vel - sim.velocity(sim.target)).length
        }
        return Result(sim.status, sim.landedOn?.id, sim.crashedInto, sim.time - t0, (f0 - sim.ship.fuel) / sim.ship.fuelCapacity * 100, last)
    }

    private fun report(id: String, pct: Int): Result = fly(id, pct).also { println("POUSO $id @$pct%: $it"); if (it.landed != null) assertTrue(it.speed < (if (id == "terra" || id == "marte") 12.0 else 5.0), "toque a ${it.speed} m/s em $id @$pct%") }

    @Test fun landsOnMars() { val r = report("marte", 100); assertEquals("marte", r.landed); assertEquals(Status.DOCKED, r.status) }
    @Test fun landsOnTheMoon() { val r = report("lua", 100); assertEquals("lua", r.landed) }
    @Test fun landsOnCeres() { val r = report("ceres", 100); assertEquals("ceres", r.landed) }
    @Test fun landsOnEarthThroughTheAtmosphere() { val r = report("terra", 100); assertEquals("terra", r.landed) }
    @Test fun cannotLandOnAGasGiant() { val r = report("jupiter", 100); assertNull(r.landed); assertEquals(Status.CRASHED, r.status) }

    @Test fun landsOnTheEarthTakesOffAndReachesTheMoon() {
        val c = AppController(MemoryStorage(), seed = { 1L })
        c.press("home_play:rota_do_gelo")
        val sim = c.session!!.sim
        val earth = sim.world.body("terra")!!
        sim.selectTarget(earth)
        sim.setThrust(100)
        sim.setAutopilot(AutopilotMode.INTERCEPT)
        var n = 0
        while (sim.status == Status.FLYING && n++ < 40_000_000) sim.step(sim.distanceTo().let { d -> if (d > 2e9) 0.5 else if (d > 2e7) 0.28 else 0.14 })
        assertEquals("terra", sim.landedOn?.id, "pousou na Terra")
        assertTrue(sim.canTakeOff(earth))
        c.session!!.undock()
        assertEquals(Status.FLYING, sim.status)
        sim.selectTarget(sim.world.body("lua")!!)
        sim.setThrust(100)
        sim.setAutopilot(AutopilotMode.INTERCEPT)
        n = 0
        while (sim.status == Status.FLYING && n++ < 40_000_000) sim.step(sim.distanceTo().let { d -> if (d > 2e9) 0.5 else if (d > 2e7) 0.28 else 0.14 })
        println("TERRA->LUA: ${sim.status} em ${sim.landedOn?.name} crash=${sim.crashedInto} t=${sim.time}")
        assertEquals("lua", sim.landedOn?.id, "decolou da Terra e pousou na Lua")
    }

    @Test fun landsAtTheDefaultThrust() {
        for (id in listOf("marte", "lua", "ceres", "terra")) assertEquals(id, report(id, 50).landed, "pouso em $id a 50%")
    }

    @Test
    fun takeoffNeedsThrustAboveSurfaceGravity() {
        val c = AppController(MemoryStorage(), seed = { 1L })
        c.press("home_play:rota_do_gelo")
        val sim = c.session!!.sim
        val mars = sim.world.body("marte")!!
        assertTrue(sim.surfaceGravity(mars) in 3.6..3.8)
        val earth = sim.world.body("terra")!!
        assertTrue(sim.surfaceGravity(earth) in 9.8..9.85)
        assertEquals(14.0, sim.ship.maxAccel, 1e-9)
        assertEquals(30, sim.takeoffThrust(mars), "Marte: 14 × 30% = 4,2 > 3,78")
        assertEquals(80, sim.takeoffThrust(earth), "Terra: 14 × 80% = 11,2 > 10,02 (70% = 9,8 não basta)")
        assertNotNull(mars.def.atmoScale.takeIf { it > 0 })
    }
}

class LunarMissionTest {
    private fun fly(g: com.theexpanse.game.GameSession, id: String, pct: Int = 100) {
        g.sim.selectTarget(g.world.body(id)!!)
        g.sim.setThrust(pct)
        g.sim.setAutopilot(AutopilotMode.INTERCEPT)
        var n = 0
        while (g.sim.status == Status.FLYING && !g.ended && n++ < 4_000_000) g.stepFixed(com.theexpanse.core.Config.SUBSTEP)
    }

    @Test
    fun packIsValid() {
        val r = com.theexpanse.content.Decoder.decode(com.theexpanse.content.Builtin.text("base_lunar")!!)
        assertNotNull(r.pack, r.errors.toString())
        assertTrue(r.errors.isEmpty(), r.errors.toString())
    }

    @Test
    fun deliverOnTheMoonInTimeThenTakeOffAndReturn() {
        val g = com.theexpanse.game.GameSession(com.theexpanse.content.Builtin.pack("base_lunar"), 11)
        g.choose(0) // aceita o prazo de 12 h
        g.story.fired.add("e_vesper") // desliga a emboscada aleatória: o teste verifica a entrega, não a sorte
        fly(g, "lua")
        assertEquals("lua", g.sim.landedOn?.id)
        assertEquals(300 + 2400, g.story.credits, "bônus do prazo")
        assertEquals(0.0, g.sim.ship.cargo, 1e-9)
        // decolar e voltar à estação
        assertEquals(20, g.sim.takeoffThrust(g.sim.landedOn!!), "Lua: 14 × 20% = 2,8 > 1,65")
        g.undock()
        assertEquals(Status.FLYING, g.sim.status)
        fly(g, "estacao_meridiano")
        println("VOLTA: status=${g.sim.status} crash=${g.sim.crashedInto} docked=${g.sim.dockedAt?.id} fuel=${g.sim.ship.fuel} t=${g.sim.time} ap=${g.sim.autopilot} dist=${g.sim.distanceTo()}")
        assertTrue(g.ended, "final ao voltar")
        assertEquals("rapido", g.story.ending)
        assertEquals(300 + 2400 + 300, g.story.credits)
    }
}

class UiResponsivenessTest {
    @Test
    fun openingTheRoutePlanForAresDoesNotBlockTheUiThread() {
        val c = AppController(MemoryStorage(), seed = { 1L })
        c.press("home_play:rota_do_gelo")
        c.session!!.choose(1) // dispensa o diálogo da escolta
        val sim = c.session!!.sim
        sim.selectTarget(sim.world.body("arsenal_ares")!!)
        val t0 = System.nanoTime()
        c.press("open_route")
        val pressMs = (System.nanoTime() - t0) / 1e6
        assertTrue(pressMs < 250, "abrir o plano de rota levou $pressMs ms")
        var worst = 0.0
        var frames = 0
        while (c.routeJob?.done != true && frames++ < 200_000) {
            val s = System.nanoTime()
            c.update(1.0 / 60)
            worst = maxOf(worst, (System.nanoTime() - s) / 1e6)
        }
        println("ROTA ARES: ${frames} quadros, pior quadro $worst ms, opções=${c.routeOptions.map { it.outcome }}")
        assertTrue(c.routeJob?.done == true, "o plano terminou")
        assertTrue(worst < 120, "pior quadro: $worst ms")
        assertTrue(c.routeOptions.any { it.outcome == com.theexpanse.game.RouteOutcome.DOCKS }, "alguma opção atraca em Ares")
    }
}
