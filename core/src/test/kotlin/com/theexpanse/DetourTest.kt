package com.theexpanse

import com.theexpanse.core.AutopilotMode
import com.theexpanse.core.Status
import com.theexpanse.core.Vec2
import com.theexpanse.game.MemoryStorage
import com.theexpanse.render.AppController
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DetourTest {
    /** Vindo de Titã, a estação de Ganimedes pode ficar atrás da lua e de Júpiter: a rota reta atravessa um corpo. */
    @Test
    fun interceptReachesAStationBehindAMoonAndAGiantPlanet() {
        // a nave padrão da Rota do Gelo não tem Δv para Saturno → Júpiter: uma cópia do pacote com mais combustível
        val text = com.theexpanse.content.Builtin.text("rota_do_gelo")!!.replace("\"combustivel\": 90000", "\"combustivel\": 200000")
        val pack = com.theexpanse.content.Decoder.decode(text).pack!!
        val sim = com.theexpanse.core.Simulation(com.theexpanse.core.World(pack), 1L)
        sim.planning = true
        val titan = sim.world.body("titan")!!
        val radial = titan.position(sim.time).unit()
        sim.ship.pos = titan.position(sim.time) + radial * (titan.def.radius + 2_000_000.0)
        sim.ship.vel = titan.velocity(sim.time)
        sim.selectTarget(sim.world.body("entreposto_ganimedes")!!)
        sim.setThrust(100)
        sim.setAutopilot(AutopilotMode.INTERCEPT)
        var n = 0
        while (sim.status == Status.FLYING && n++ < 3_000_000) sim.step(com.theexpanse.game.PlanSteps.forDistance(sim.distanceTo()))
        assertEquals(Status.DOCKED, sim.status, "bateu em ${sim.crashedInto}")
        assertEquals("entreposto_ganimedes", sim.dockedAt?.id)
    }

    @Test
    fun detourPointIsNullWhenTheLineIsClear() {
        val c = AppController(MemoryStorage(), seed = { 1L })
        c.press("home_play:rota_do_gelo")
        val w = c.session!!.sim.world
        // Terra e Lua lado a lado, longe de qualquer obstáculo no caminho
        val a = w.body("terra")!!.position(0.0) + Vec2(1e9, 0.0)
        val b = w.body("terra")!!.position(0.0) + Vec2(2e9, 0.0)
        assertTrue(com.theexpanse.core.Autopilot.detourAround(w, 0.0, a, b) == null)
    }
}
