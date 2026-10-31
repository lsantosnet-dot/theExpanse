package com.theexpanse.game

import com.theexpanse.core.AutopilotMode
import com.theexpanse.core.Config
import com.theexpanse.core.Simulation
import com.theexpanse.core.Status

enum class RouteOutcome { DOCKS, CRASHES, TIMEOUT, NOT_DOCKABLE }

class RouteOption(val thrustPct: Int, val outcome: RouteOutcome, val seconds: Double, val fuelUsed: Double, val fuelCapacity: Double) {
    val fuelPct: Double get() = fuelUsed / fuelCapacity * 100.0
}

/** Estima viagens rodando o piloto automático de verdade numa cópia da simulação: tempo e combustível exatos. */
object RoutePlanner {
    fun plan(sim: Simulation, pcts: List<Int> = listOf(20, 50, 100), limit: Double = 90_000.0): List<RouteOption> {
        return pcts.map { pct ->
            val c = sim.copy()
            if (c.status == Status.DOCKED) c.undock()
            val cap = c.ship.fuelCapacity
            if (!c.target.dockable) return@map RouteOption(pct, RouteOutcome.NOT_DOCKABLE, 0.0, 0.0, cap)
            c.setThrust(pct)
            c.setAutopilot(AutopilotMode.INTERCEPT)
            val t0 = c.time
            val f0 = c.ship.fuel
            var minFuel = f0
            while (c.status == Status.FLYING && c.time - t0 < limit) {
                c.step(Config.SUBSTEP)
                if (c.ship.fuel < minFuel) minFuel = c.ship.fuel
            }
            val outcome = when (c.status) {
                Status.DOCKED -> RouteOutcome.DOCKS
                Status.CRASHED -> RouteOutcome.CRASHES
                else -> RouteOutcome.TIMEOUT
            }
            RouteOption(pct, outcome, c.time - t0, f0 - minFuel, cap)
        }
    }
}
