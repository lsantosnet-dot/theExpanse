package com.theexpanse

import com.theexpanse.content.Builtin
import com.theexpanse.core.AutopilotMode
import com.theexpanse.core.Simulation
import com.theexpanse.core.Status
import com.theexpanse.core.Vec2
import com.theexpanse.core.World
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AvoidanceTest {
    /** Nave do lado oposto ao Helena, com o planeta Aurélia no meio do caminho. */
    private fun behindThePlanet(pct: Int, dist: Double = 300_000_000.0): Simulation {
        val sim = Simulation(World(Builtin.pack("albatroz")), 1L)
        val helena = sim.world.body("lab_helena")!!
        sim.selectTarget(sim.bodies.indexOf(helena))
        val hp = helena.position(0.0)
        sim.ship.pos = -hp.unit() * dist
        sim.ship.vel = Vec2.ZERO
        sim.ship.angle = (hp - sim.ship.pos).angle
        sim.setThrust(pct)
        sim.toggleAutopilot(AutopilotMode.INTERCEPT)
        sim.setWarp(4)
        return sim
    }

    private fun fly(sim: Simulation, hours: Int): Double {
        var minR = Double.MAX_VALUE
        var frames = 0
        while (frames < 30 * 3600 * hours && sim.status == Status.FLYING) {
            sim.advance(1.0 / 30)
            minR = minOf(minR, sim.ship.pos.length)
            frames++
        }
        return minR
    }

    @Test
    fun interceptGoesAroundThePlanetInsteadOfThroughIt() {
        for (pct in listOf(30, 50, 100)) {
            val sim = behindThePlanet(pct)
            val minR = fly(sim, 3)
            println("pct=$pct status=${sim.status} crashed=${sim.crashedInto} minR=${(minR / 1e3).toInt()} km t=${(sim.time / 3600).toInt()} h")
            assertTrue(sim.status != Status.CRASHED, "empuxo $pct%: bateu em ${sim.crashedInto}")
            assertTrue(minR > sim.world.radius * 1.05, "passou a ${minR / 1e3} km do centro")
        }
    }

    @Test
    fun theDetourStillReachesTheStation() {
        val sim = behindThePlanet(50)
        fly(sim, 6)
        assertEquals(Status.DOCKED, sim.status, "status ${sim.status}")
        assertEquals("lab_helena", sim.dockedAt?.id)
    }

    @Test
    fun noDetourWhenThePathIsClear() {
        val sim = Simulation(World(Builtin.pack("albatroz")), 1L)
        val helena = sim.world.body("lab_helena")!!
        val hp = helena.position(0.0)
        val side = Vec2(-hp.unit().y, hp.unit().x)
        val t = com.theexpanse.core.Autopilot.threat(sim.world, 0.0, side * 300_000_000.0, hp.unit() * 20_000.0, hp)
        assertNull(t, "a nave cai de lado e passa longe do planeta")
    }

    @Test
    fun warnsWhenTheCurrentCourseHitsThePlanet() {
        val sim = Simulation(World(Builtin.pack("albatroz")), 1L)
        sim.ship.pos = Vec2(300_000_000.0, 0.0)
        sim.ship.vel = Vec2(-30_000.0, 0.0)
        val w = sim.collisionWarning()
        assertNotNull(w)
        assertEquals("Aurélia", w.first)
        assertTrue(w.second in 6_000.0..9_000.0, "tempo ${w.second}")
        // passando de lado, bem longe, não há aviso
        sim.ship.pos = Vec2(300_000_000.0, 400_000_000.0)
        assertNull(sim.collisionWarning())
    }

    @Test
    fun orbitingShipGetsNoFalseWarning() {
        val sim = Simulation(World(Builtin.pack("albatroz")), 1L)
        assertNull(sim.collisionWarning(), "a órbita inicial não colide")
    }
}

class CollisionWarpTest {
    @Test
    fun timeSlowsDownEarlyOnACollisionCourse() {
        val sim = Simulation(World(Builtin.pack("albatroz")), 1L)
        sim.ship.pos = Vec2(450_000_000.0, 0.0)
        sim.ship.vel = Vec2(-57_000.0, 0.0)
        sim.setWarp(4)
        sim.advance(1.0 / 30)
        // ~2,2 h até o impacto: o tempo não pode seguir em ×7.200
        assertTrue(sim.effectiveWarp < 200.0, "efetivo ×${sim.effectiveWarp}")
        // passando longe, o tempo corre livre (limitado só pelos outros tetos)
        sim.ship.pos = Vec2(450_000_000.0, 900_000_000.0)
        sim.advance(1.0 / 30)
        assertTrue(sim.effectiveWarp > 1000.0, "efetivo ×${sim.effectiveWarp}")
    }
}

class CollisionHudTest {
    @Test
    fun hudShowsTheWarningWhileOnACollisionCourse() {
        val c = com.theexpanse.render.AppController(com.theexpanse.game.MemoryStorage(), seed = { 1L })
        c.press("home_play:albatroz")
        val sim = c.session!!.sim
        sim.ship.pos = Vec2(450_000_000.0, 0.0)
        sim.ship.vel = Vec2(-57_000.0, 0.0)
        val texts = c.frame().prims.filterIsInstance<com.theexpanse.render.Txt>().map { it.text }
        assertTrue(texts.any { it.startsWith("ROTA COLIDE COM AURÉLIA") }, "textos: ${texts.filter { it.contains("ROTA") }}")
        sim.ship.vel = Vec2(-57_000.0, 40_000.0)
        sim.ship.pos = Vec2(450_000_000.0, 900_000_000.0)
        val after = c.frame().prims.filterIsInstance<com.theexpanse.render.Txt>().map { it.text }
        assertTrue(after.none { it.startsWith("ROTA COLIDE") })
    }
}
