package com.theexpanse

import com.theexpanse.core.AutopilotMode
import com.theexpanse.core.Config
import com.theexpanse.core.Status
import com.theexpanse.game.MemoryStorage
import com.theexpanse.game.RouteOutcome
import com.theexpanse.render.AppController
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** O planejamento de rota é uma estimativa rápida: precisa dar o mesmo desfecho da simulação precisa e ser leve no celular. */
class PlanSpeedTest {
    private fun session(pack: String): com.theexpanse.core.Simulation {
        val c = AppController(MemoryStorage(), seed = { 1L })
        c.press("home_play:$pack")
        c.session!!.choose(1)
        return c.session!!.sim
    }

    /** Referência: passos de 0,28 a 0,7 s, atitude física, sem o modo de estimativa. */
    private fun precise(sim: com.theexpanse.core.Simulation, target: String, pct: Int): Pair<RouteOutcome, Double> {
        val c = sim.copy()
        c.planning = false
        c.selectTarget(c.world.body(target)!!)
        c.setThrust(pct); c.setAutopilot(AutopilotMode.INTERCEPT)
        val t0 = c.time
        var n = 0
        while (c.status == Status.FLYING && n++ < 20_000_000 && c.time - t0 < 4e6) {
            val d = c.distanceTo()
            c.step(when { d > 2e10 -> 0.7; d > 2e9 -> 0.5; d > 2e7 -> 0.28; else -> Config.SUBSTEP })
        }
        val outcome = when (c.status) { Status.DOCKED -> RouteOutcome.DOCKS; Status.CRASHED, Status.DESTROYED -> RouteOutcome.CRASHES; else -> RouteOutcome.TIMEOUT }
        return outcome to (c.time - t0)
    }

    private fun check(pack: String, target: String, pcts: List<Int>) {
        val sim = session(pack)
        sim.selectTarget(sim.world.body(target)!!)
        val t0 = System.nanoTime()
        val job = com.theexpanse.game.RoutePlanJob(sim, pcts, simulateUndockable = true)
        while (!job.advance(5_000_000)) { /* continua */ }
        val plan = job.options.filterNotNull()
        val ms = (System.nanoTime() - t0) / 1e6
        println("PLANO $pack→$target: ${plan.map { "${it.thrustPct}% ${it.outcome} ${it.seconds.toLong()} s" }} em ${ms.toLong()} ms")
        assertTrue(ms < 4000, "planejar $target levou $ms ms")
        for (o in plan) {
            val (outcome, seconds) = precise(sim, target, o.thrustPct)
            assertEquals(outcome, o.outcome, "$target @${o.thrustPct}%: desfecho")
            assertTrue(Math.abs(o.seconds - seconds) / seconds < 0.03, "$target @${o.thrustPct}%: ${o.seconds} s contra ${seconds} s")
        }
    }

    @Test fun ceresToAres() = check("rota_do_gelo", "arsenal_ares", listOf(20, 50, 100))
    @Test fun ceresToMarsLanding() = check("rota_do_gelo", "marte", listOf(50, 100))
    @Test fun ceresToJupiterCrashes() = check("rota_do_gelo", "jupiter", listOf(50))
    @Test fun earthToMoonLanding() = check("base_lunar", "lua", listOf(20, 50, 100))
    @Test fun earthToEarthLanding() = check("base_lunar", "terra", listOf(50, 100))
}
