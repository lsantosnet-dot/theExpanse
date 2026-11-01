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

    private fun report(id: String, pct: Int): Result = fly(id, pct).also { println("POUSO $id @$pct%: $it") }

    @Test fun landsOnMars() { val r = report("marte", 100); assertEquals("marte", r.landed); assertEquals(Status.DOCKED, r.status) }
    @Test fun landsOnTheMoon() { val r = report("lua", 100); assertEquals("lua", r.landed) }
    @Test fun landsOnCeres() { val r = report("ceres", 100); assertEquals("ceres", r.landed) }
    @Test fun landsOnEarthThroughTheAtmosphere() { val r = report("terra", 100); assertEquals("terra", r.landed) }
    @Test fun cannotLandOnAGasGiant() { val r = report("jupiter", 100); assertNull(r.landed); assertEquals(Status.CRASHED, r.status) }

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
        sim.setThrust(100)
        assertTrue(sim.canTakeOff(mars))
        sim.setThrust(30)
        assertTrue(!sim.canTakeOff(mars))
        assertNotNull(mars.def.atmoScale.takeIf { it > 0 })
    }
}
