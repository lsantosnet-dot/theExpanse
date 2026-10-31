package com.theexpanse

import com.theexpanse.content.Builtin
import com.theexpanse.content.Validator
import com.theexpanse.core.AutopilotMode
import com.theexpanse.core.Config
import com.theexpanse.core.Simulation
import com.theexpanse.core.Status
import com.theexpanse.core.World
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SolarTest {
    private val pack = Builtin.pack("rota_do_gelo")
    private val world = World(pack)

    @Test
    fun solarSystemPackIsValid() {
        val v = Validator.validate(pack)
        println("rota_do_gelo: erros=${v.errors} avisos=${v.warnings}")
        assertTrue(v.ok, v.errors.toString())
    }

    @Test
    fun keplerOrbitsMatchRealDistancesAndPeriods() {
        val earth = world.body("terra")!!
        val mars = world.body("marte")!!
        // distância ao Sol entre o periélio e o afélio
        val r = earth.position(0.0).length
        assertTrue(r in 1.4e11..1.6e11, "r=$r")
        // período da Terra ≈ 365,25 dias
        assertEquals(365.25 * 86400, earth.period, 0.01 * 365.25 * 86400)
        assertEquals(687.0 * 86400, mars.period, 0.01 * 687.0 * 86400)
        // órbita elíptica: a distância de Marte varia entre a(1-e) e a(1+e)
        var min = Double.MAX_VALUE; var max = 0.0
        var t = 0.0
        while (t < mars.period) { val d = mars.position(t).length; min = minOf(min, d); max = maxOf(max, d); t += 86400.0 * 5 }
        assertEquals(mars.orbitRadius * (1 - 0.0934), min, 0.01 * mars.orbitRadius)
        assertEquals(mars.orbitRadius * (1 + 0.0934), max, 0.01 * mars.orbitRadius)
        // velocidade orbital da Terra ≈ 29,8 km/s
        assertEquals(29_800.0, earth.velocity(0.0).length, 1_500.0)
    }

    @Test
    fun moonOrbitsEarthAndMovesWithIt() {
        val earth = world.body("terra")!!
        val moon = world.body("lua")!!
        for (t in listOf(0.0, 5e5, 2e6)) {
            val d = (moon.position(t) - earth.position(t)).length
            assertTrue(d in 3.5e8..4.1e8, "distância Terra-Lua $d")
        }
        // a Lua acompanha a Terra: velocidade relativa ao Sol ≈ a da Terra ± 1 km/s
        assertEquals(earth.velocity(0.0).length, moon.velocity(0.0).length, 1_300.0)
    }

    @Test
    fun gravityOfThePlanetsPullsTheShip() {
        val earth = world.body("terra")!!
        val p = earth.position(0.0) + com.theexpanse.core.Vec2(7.0e6, 0.0) // 629 km de altitude
        val g = world.gravity(p, 0.0)
        val expected = 3.986004418e14 / (7.0e6 * 7.0e6)
        assertEquals(expected, g.length, expected * 0.05)
    }

    @Test
    fun shipStartsOrbitingCeresStationAndStaysThere() {
        val sim = Simulation(world)
        val ceres = world.body("ceres")!!
        val d0 = (sim.ship.pos - ceres.position(0.0)).length
        assertTrue(d0 in 1.0e6..2.5e6, "d0=$d0")
        repeat(2000) { sim.step(Config.SUBSTEP) }
        val d1 = (sim.ship.pos - ceres.position(sim.time)).length
        assertTrue(d1 < 4e6, "a nave deveria seguir perto de Ceres: $d1")
    }

    @Test
    fun collidingWithAPlanetDestroysTheShip() {
        val sim = Simulation(world)
        val mars = world.body("marte")!!
        sim.ship.pos = mars.position(0.0) + com.theexpanse.core.Vec2(3.0e6, 0.0)
        sim.ship.vel = mars.velocity(0.0)
        sim.step(0.14)
        assertEquals(Status.CRASHED, sim.status)
        assertEquals("Marte", sim.crashedInto)
    }

    @Test
    fun autopilotFliesFromCeresToMarsStationWithinFuel() {
        val sim = Simulation(world)
        sim.setThrust(50)
        sim.setAutopilot(AutopilotMode.INTERCEPT)
        val d0 = sim.distanceTo()
        var steps = 0L
        while (sim.status == Status.FLYING && sim.time < 3.0e6) {
            sim.step(if (sim.distanceTo() > 2e7) 0.28 else Config.SUBSTEP)
            steps++
        }
        println("Ceres→Ares: ${(d0 / 1e9).toInt()} milhões de km, ${(sim.time / 86400).let { "%.1f".format(it) }} dias, estado ${sim.status}, ${steps} passos, combustível ${(sim.ship.fuel / 1000).toInt()} t")
        assertEquals(Status.DOCKED, sim.status)
        assertEquals("arsenal_ares", sim.dockedAt?.id)
    }

    @Test
    fun cruiseScaleMakesAFewDaysTakeAFewMinutes() {
        assertEquals(1440.0, world.warpLevels[3])
        val days = 5.0
        assertEquals(5.0, days * 86_400 / world.warpLevels[3] / 60.0, 1e-9)
    }
}
