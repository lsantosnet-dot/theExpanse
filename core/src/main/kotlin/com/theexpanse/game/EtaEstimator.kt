package com.theexpanse.game

import com.theexpanse.content.AiMode
import com.theexpanse.core.AutopilotMode
import com.theexpanse.core.Config
import com.theexpanse.core.Simulation
import com.theexpanse.core.Status

/**
 * Estima em segundo plano o tempo até chegar ao alvo, rodando o piloto automático numa cópia da simulação em
 * pequenos lotes por quadro (sem travar). Entre uma estimativa e outra, a contagem regressiva é contínua.
 */
class EtaEstimator {
    enum class Kind { NONE, ARRIVAL, CLOSEST, FAR }

    var kind = Kind.NONE
        private set
    private var seconds = 0.0
    private var startedAt = 0.0
    private var key = ""
    private var lastStart = -100.0
    private var work: Simulation? = null
    private var workT0 = 0.0
    private var workSteps = 0L

    /** Segundos restantes até a chegada (ou menor aproximação), já descontado o tempo que passou. */
    fun remaining(sim: Simulation): Double? = when (kind) {
        Kind.ARRIVAL, Kind.CLOSEST -> (seconds - (sim.time - startedAt)).coerceAtLeast(0.0)
        else -> null
    }

    val calculating: Boolean get() = work != null

    fun update(sim: Simulation, clock: Double, budgetSteps: Int = 20_000) {
        val k = "${sim.target.id}|${sim.thrustPct}|${sim.status}|${sim.autopilot}|${sim.manualEngine}|${sim.manualTurn != 0}"
        if (sim.status != Status.FLYING || !sim.alive(sim.target)) {
            kind = Kind.NONE; work = null; key = k
            return
        }
        // refaz a cada 2 s reais (depois que a estimativa anterior terminou), para acompanhar as manobras; enquanto calcula, o número antigo continua contando
        if (k != key || (work == null && clock - lastStart > REFRESH)) {
            key = k
            lastStart = clock
            if (!sim.target.dockable && !sim.target.landable) {
                val p = sim.prediction()
                kind = Kind.CLOSEST; seconds = p.closestTime; startedAt = sim.time; work = null
                return
            }
            val c = sim.copy()
            c.missiles.clear()
            for (d in c.dynamics.values) if (d.mode == AiMode.ATTACK) d.mode = AiMode.PURSUE
            c.setThrust(sim.thrustPct)
            c.setAutopilot(AutopilotMode.INTERCEPT)
            work = c
            workT0 = c.time
            workSteps = 0
            limitCache = 0.0
        }
        val c = work ?: return
        var n = 0
        val limit = limitFor(c)
        while (n++ < budgetSteps && c.status == Status.FLYING && c.time - workT0 < limit) c.step(if (c.distanceTo() > 2e7) 0.28 else Config.SUBSTEP)
        if (c.status == Status.DOCKED) {
            kind = Kind.ARRIVAL; seconds = c.time - workT0; startedAt = workT0; work = null
        } else if (c.status != Status.FLYING || c.time - workT0 >= limit) {
            kind = Kind.FAR; work = null
        }
    }

    private var limitCache = 0.0

    companion object {
        const val REFRESH = 2.0
    }

    private fun limitFor(c: Simulation): Double {
        if (limitCache == 0.0) limitCache = maxOf(90_000.0, 4.0 * 2.0 * kotlin.math.sqrt(c.distanceTo() / (c.accelLimit * 0.85)))
        return limitCache
    }
}
