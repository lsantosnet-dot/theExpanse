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

class PreflightTest {
    private fun controllerBehindPlanet(speed: Double, distKm: Double): com.theexpanse.render.AppController {
        val c = com.theexpanse.render.AppController(com.theexpanse.game.MemoryStorage(), seed = { 1L })
        c.press("home_play:albatroz")
        val sim = c.session!!.sim
        val helena = sim.world.body("lab_helena")!!
        sim.selectTarget(sim.bodies.indexOf(helena))
        val dir = -helena.position(sim.time).unit()
        sim.ship.pos = dir * (distKm * 1000.0)
        sim.ship.vel = -dir * speed
        sim.ship.angle = (helena.position(sim.time) - sim.ship.pos).angle
        return c
    }

    private fun check(c: com.theexpanse.render.AppController) {
        c.press("ap_intercept")
        assertNotNull(c.preflight)
        var frames = 0
        while (c.preflight != null && frames++ < 20000) c.update(1.0 / 60)
    }

    @Test
    fun viableRouteEngagesTheAutopilotWithoutAsking() {
        val c = controllerBehindPlanet(0.0, 300_000.0)
        check(c)
        assertNull(c.verdict)
        assertEquals(AutopilotMode.INTERCEPT, c.session!!.sim.autopilot)
        assertNotNull(c.banner)
        assertTrue(c.banner!!.startsWith("Rota verificada"), c.banner)
    }

    @Test
    fun unviableRouteWarnsAndDoesNotEngageUntilConfirmed() {
        val c = controllerBehindPlanet(57_000.0, 250_000.0)
        val sim = c.session!!.sim
        val t0 = sim.time
        check(c)
        val v = c.verdict
        assertNotNull(v, "esperava aviso de rota inviável")
        println("veredito: ${v.reason} | ${v.detail} | alternativa=${v.alternative?.thrustPct}")
        assertTrue(v.reason.contains("AURÉLIA"), v.reason)
        assertEquals(AutopilotMode.OFF, sim.autopilot, "não engaja sozinho")
        // enquanto o aviso está aberto, o tempo de jogo não corre
        c.update(1.0)
        assertEquals(t0, sim.time, 1e-9)
        // o aviso aparece na tela, com os botões
        val ids = c.frame().buttons.map { it.id }
        assertTrue("pf_cancel" in ids && "pf_force" in ids, "botões: $ids")
        // cancelar fecha o aviso e o tempo volta a correr
        c.press("pf_cancel")
        assertNull(c.verdict)
        assertEquals(AutopilotMode.OFF, sim.autopilot)
        c.update(1.0)
        assertTrue(sim.time > t0)
    }

    @Test
    fun forcingEngagesAnyway() {
        val c = controllerBehindPlanet(57_000.0, 250_000.0)
        check(c)
        assertNotNull(c.verdict)
        c.press("pf_force")
        assertNull(c.verdict)
        assertEquals(AutopilotMode.INTERCEPT, c.session!!.sim.autopilot)
    }

    @Test
    fun cancelDuringTheCheckDropsIt() {
        val c = controllerBehindPlanet(0.0, 300_000.0)
        c.press("ap_intercept")
        assertNotNull(c.preflight)
        c.press("pf_cancel")
        assertNull(c.preflight)
        assertEquals(AutopilotMode.OFF, c.session!!.sim.autopilot)
    }

    @Test
    fun pressingInterceptAgainTurnsItOff() {
        val c = controllerBehindPlanet(0.0, 300_000.0)
        check(c)
        assertEquals(AutopilotMode.INTERCEPT, c.session!!.sim.autopilot)
        c.press("ap_intercept")
        assertEquals(AutopilotMode.OFF, c.session!!.sim.autopilot)
    }
}

class StationRetargetTest {
    private fun iceRun(): com.theexpanse.render.AppController {
        val c = com.theexpanse.render.AppController(com.theexpanse.game.MemoryStorage(), seed = { 1L })
        c.press("home_play:rota_do_gelo")
        val sim = c.session!!.sim
        sim.selectTarget(sim.bodies.indexOf(sim.world.body("marte")!!))
        return c
    }

    @Test
    fun interceptOnALandablePlanetKeepsThePlanetAsTarget() {
        val c = iceRun()
        val sim = c.session!!.sim
        c.press("ap_intercept")
        assertEquals("marte", sim.target.id)
        assertNotNull(c.preflight)
    }

    @Test
    fun crashVerdictOffersTheStationAndRetargets() {
        val c = iceRun()
        val sim = c.session!!.sim
        val st = sim.world.body("arsenal_ares")!!
        c.verdict = com.theexpanse.render.Verdict("A ROTA TERMINA EM COLISÃO COM MARTE", "x", null, st)
        c.press("pf_station")
        assertEquals("arsenal_ares", sim.target.id)
        assertNull(c.verdict)
        assertNotNull(c.preflight)
    }
}
