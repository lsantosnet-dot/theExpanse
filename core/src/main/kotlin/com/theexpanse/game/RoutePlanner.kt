package com.theexpanse.game

import com.theexpanse.core.AutopilotMode
import com.theexpanse.core.Config
import com.theexpanse.core.Simulation
import com.theexpanse.core.Status
import kotlin.math.sqrt

enum class RouteOutcome { DOCKS, CRASHES, TIMEOUT, NOT_DOCKABLE }

class RouteOption(
    val thrustPct: Int, val outcome: RouteOutcome, val seconds: Double, val fuelUsed: Double, val fuelCapacity: Double,
    /** Com [RouteOutcome.CRASHES]: o corpo atingido (null se a nave foi destruída por míssil). */
    val crashedInto: String? = null,
    /** O combustível acabou antes do fim da viagem. */
    val fuelOut: Boolean = false,
) {
    val fuelPct: Double get() = fuelUsed / fuelCapacity * 100.0
}

/**
 * Estima viagens rodando o piloto automático de verdade em cópias da simulação, em lotes de passos (para não travar
 * a tela nas viagens longas do Sistema Solar). Cada opção fica pronta quando a sua cópia termina.
 */
class RoutePlanJob(sim: Simulation, val pcts: List<Int> = listOf(20, 50, 100), private val minLimit: Double = 90_000.0, private val simulateUndockable: Boolean = false) {
    private class Run(val pct: Int, val c: Simulation, val t0: Double, val f0: Double, val limit: Double, val dockable: Boolean) {
        var arrived = false
        var minFuel = f0
        var result: RouteOption? = null
    }

    private val runs: List<Run> = pcts.map { pct ->
        val c = sim.copy()
        if (c.status == Status.DOCKED) c.undock()
        c.setThrust(pct)
        c.setAutopilot(AutopilotMode.INTERCEPT)
        val limit = maxOf(minLimit, 4.0 * 2.0 * sqrt(c.distanceTo() / (c.accelLimit * 0.85)))
        Run(pct, c, c.time, c.ship.fuel, limit, c.target.dockable).also {
            if (!it.dockable && !simulateUndockable) it.result = RouteOption(pct, RouteOutcome.NOT_DOCKABLE, 0.0, 0.0, c.ship.fuelCapacity)
        }
    }

    val options: List<RouteOption?> get() = runs.map { it.result }
    val done: Boolean get() = runs.all { it.result != null }

    /** Progresso (0..1) de uma opção. */
    fun progress(i: Int): Float {
        val r = runs[i]
        if (r.result != null) return 1f
        return ((r.c.time - r.t0) / r.limit).toFloat().coerceIn(0f, 0.99f)
    }

    /** Roda até [budget] passos no total; devolve true se terminou tudo. */
    fun advance(budget: Int): Boolean {
        var left = budget
        for (r in runs) {
            if (r.result != null) continue
            val c = r.c
            while (left > 0 && c.status == Status.FLYING && c.time - r.t0 < r.limit && !r.arrived) {
                c.step(if (c.distanceTo() > 2e7) 0.28 else Config.SUBSTEP)
                if (c.ship.fuel < r.minFuel) r.minFuel = c.ship.fuel
                // alvo que não atraca (nave, destroços): chegar perto e devagar conta como chegada
                if (!r.dockable && c.distanceTo() < Config.DOCK_DISTANCE && c.relativeVelocity().length < Config.DOCK_SPEED) r.arrived = true
                left--
            }
            if (c.status != Status.FLYING || c.time - r.t0 >= r.limit || r.arrived) {
                val outcome = when {
                    r.arrived -> RouteOutcome.DOCKS
                    c.status == Status.DOCKED -> RouteOutcome.DOCKS
                    c.status == Status.CRASHED || c.status == Status.DESTROYED -> RouteOutcome.CRASHES
                    else -> RouteOutcome.TIMEOUT
                }
                r.result = RouteOption(r.pct, outcome, c.time - r.t0, r.f0 - r.minFuel, c.ship.fuelCapacity, c.crashedInto, c.ship.fuel <= 0.0)
            }
            if (left <= 0) break
        }
        return done
    }
}

object RoutePlanner {
    /** Versão síncrona (testes e viagens curtas). */
    fun plan(sim: Simulation, pcts: List<Int> = listOf(20, 50, 100), minLimit: Double = 90_000.0): List<RouteOption> {
        val job = RoutePlanJob(sim, pcts, minLimit)
        while (!job.advance(5_000_000)) { /* continua */ }
        return job.options.filterNotNull()
    }
}
