package com.theexpanse

import com.theexpanse.core.AutopilotMode
import com.theexpanse.core.Calice
import com.theexpanse.core.Config
import com.theexpanse.core.Simulation
import com.theexpanse.core.Status
import com.theexpanse.core.Vec2
import com.theexpanse.core.gravityAt
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SimulationTest {
    /** Voa com o piloto automático até o fim (pausas do rádio não existem aqui) e devolve os números da viagem. */
    private class Flight(val sim: Simulation, val dockTime: Double, val minCenterDistance: Double, val fuelUsed: Double)

    private fun autoFlight(thrustPct: Int): Flight {
        val sim = Simulation()
        sim.adjustThrust((thrustPct - Config.DEFAULT_THRUST_PCT) / 10)
        sim.setAutopilot(AutopilotMode.INTERCEPT)
        var minR = Double.MAX_VALUE
        var minFuel = Config.FUEL_MASS
        while (sim.status == Status.FLYING && sim.time < 200_000.0) {
            sim.step(Config.SUBSTEP)
            minR = minOf(minR, sim.ship.pos.length)
            minFuel = minOf(minFuel, sim.ship.fuel)
        }
        return Flight(sim, sim.time, minR, Config.FUEL_MASS - minFuel)
    }

    @Test
    fun initialOrbitIsCircularAtStationAltitude() {
        val sim = Simulation()
        val r = sim.ship.pos.length
        val expected = Calice.dock.orbitRadius + 40_000.0
        assertEquals(expected, r, 1.0)
        assertEquals(sqrt(Config.GM / r), sim.ship.vel.length, 1e-6)
        // velocidade perpendicular ao raio
        assertEquals(0.0, sim.ship.pos.unit() dot sim.ship.vel.unit(), 1e-9)
    }

    @Test
    fun circularOrbitConservesRadiusOverAnOrbit() {
        val sim = Simulation()
        val r0 = sim.ship.pos.length
        val period = 2 * Math.PI * sqrt(r0 * r0 * r0 / Config.GM)
        var maxDev = 0.0
        while (sim.time < period) {
            sim.step(Config.SUBSTEP)
            maxDev = maxOf(maxDev, abs(sim.ship.pos.length - r0) / r0)
        }
        assertTrue(maxDev < 1e-3, "desvio de raio ${maxDev}")
    }

    @Test
    fun fuelFollowsTheRocketEquation() {
        val sim = Simulation()
        sim.setAutopilot(AutopilotMode.OFF)
        val dvBefore = sim.ship.deltaV
        // queima manual, alinhado ao prograde: Δv obtido ≈ Δv gasto
        sim.manualEngine = true
        val v0 = sim.ship.vel.length
        val m0 = sim.ship.mass
        for (i in 0 until 2000) sim.step(Config.SUBSTEP)
        val m1 = sim.ship.mass
        val spent = Config.EXHAUST_VELOCITY * ln(m0 / m1)
        assertTrue(spent > 100.0)
        assertEquals(dvBefore - spent, sim.ship.deltaV, 1.0)
        assertTrue(sim.ship.vel.length > v0)
    }

    @Test
    fun gravityPointsToThePlanet() {
        val g = gravityAt(Vec2(3.2e8, 0.0))
        assertTrue(g.x < 0.0)
        assertEquals(Config.GM / (3.2e8 * 3.2e8), g.length, 1e-9)
    }

    @Test
    fun dockingRefuelsAndStopsTheClock() {
        val f = autoFlight(50)
        assertEquals(Status.DOCKED, f.sim.status)
        assertEquals(Config.FUEL_MASS, f.sim.ship.fuel, 1e-6)
    }

    @Test
    fun autopilotReachesPortKauaAtEachThrust() {
        val expected = mapOf(50 to 27_100.0, 100 to 21_200.0, 20 to 41_500.0)
        for ((pct, target) in expected) {
            val f = autoFlight(pct)
            println("empuxo $pct%: atraca em ${f.dockTime.toInt()} s (esperado ~${target.toInt()}), " +
                "distância mínima ao centro de Aurélia ${(f.minCenterDistance / 1000).toInt()} km, " +
                "vel. relativa final ${f.sim.relativeVelocity(Calice.port).length.toInt()} m/s, " +
                "combustível gasto ${f.fuelUsed.toInt()} kg")
            assertEquals(Status.DOCKED, f.sim.status, "empuxo $pct%")
            assertTrue(f.minCenterDistance > Config.PLANET_RADIUS, "colidiu com o planeta a $pct%")
            assertTrue(f.sim.relativeVelocity(Calice.port).length <= Config.DOCK_SPEED, "velocidade final a $pct%")
            assertTrue(abs(f.dockTime - target) / target <= 0.05, "tempo a $pct%: ${f.dockTime} vs $target")
        }
    }

    @Test
    fun planetClearanceAt50PercentMatchesPrototype() {
        val f = autoFlight(50)
        assertTrue(abs(f.minCenterDistance - 265_000_000.0) / 265_000_000.0 <= 0.05, "distância mínima ${f.minCenterDistance}")
    }

    @Test
    fun manualFlipThenBurnBrakesTheShip() {
        val sim = Simulation()
        sim.setAutopilot(AutopilotMode.PROGRADE)
        repeat(3000) { sim.step(Config.SUBSTEP) }
        sim.manualEngine = true
        repeat(600) { sim.step(Config.SUBSTEP) }
        val fast = sim.relativeVelocity().length
        sim.manualEngine = false
        sim.setAutopilot(AutopilotMode.RETROGRADE)
        var guard = 0
        // RETRO continua acompanhando o vetor de velocidade, então basta o rumo estar alinhado
        while (guard++ < 20_000 && abs(com.theexpanse.core.wrapAngle(sim.ship.angle - (sim.relativeVelocity().angle + Math.PI))) > 0.01) sim.step(Config.SUBSTEP)
        assertTrue(guard < 20_000, "a virada não terminou")
        sim.manualEngine = true
        repeat(600) { sim.step(Config.SUBSTEP) }
        assertTrue(sim.relativeVelocity().length < fast, "queimar de costas deve frear: antes $fast, depois ${sim.relativeVelocity().length}")
    }

    @Test
    fun predictionFindsClosestApproach() {
        val sim = Simulation()
        val p = sim.prediction()
        assertTrue(p.points.size > 100)
        assertTrue(p.closestDistance < sim.distanceTo())
    }
}
