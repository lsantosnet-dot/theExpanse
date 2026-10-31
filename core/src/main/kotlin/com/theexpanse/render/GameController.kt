package com.theexpanse.render

import com.theexpanse.core.AutopilotMode
import com.theexpanse.core.Simulation
import com.theexpanse.core.Status

/** O que desenhar neste quadro e onde há botões. */
class Frame(val prims: List<Prim>, val buttons: List<Button>)

/**
 * Liga a simulação à interface: relógio de animação, vista atual, botões (toque e segurar) e a cena do quadro.
 * Quem hospeda (Android, preview) chama [update] a cada quadro, [frame] para desenhar e [press]/[release] nos toques.
 */
class GameController(val sim: Simulation = Simulation()) {
    var view = ViewMode.COCKPIT
    var animTime = 0.0
        private set
    private val held = HashSet<String>()
    private var buttons: List<Button> = emptyList()

    fun update(realDt: Double) {
        animTime += realDt
        sim.advance(realDt)
    }

    /** Avança só a animação (efeitos), sem mexer no tempo simulado. */
    fun animate(dt: Double) {
        animTime += dt
    }

    fun hit(x: Float, y: Float): Button? = buttons.lastOrNull { it.contains(x, y) }

    fun press(id: String) {
        when (id) {
            "turn_l" -> { held.add(id); sim.manualTurn = 1 }
            "turn_r" -> { held.add(id); sim.manualTurn = -1 }
            "engine" -> { held.add(id); sim.manualEngine = true }
            "thr_down" -> sim.adjustThrust(-1)
            "thr_up" -> sim.adjustThrust(1)
            "ap_intercept" -> sim.toggleAutopilot(AutopilotMode.INTERCEPT)
            "ap_pro" -> sim.toggleAutopilot(AutopilotMode.PROGRADE)
            "ap_retro" -> sim.toggleAutopilot(AutopilotMode.RETROGRADE)
            "ap_stop" -> sim.setAutopilot(AutopilotMode.STOP_SPIN)
            "view" -> view = if (view == ViewMode.COCKPIT) ViewMode.EXTERNAL else ViewMode.COCKPIT
            "target" -> sim.cycleTarget()
            "reset" -> { held.clear(); sim.manualTurn = 0; sim.manualEngine = false; sim.reset() }
            else -> if (id.startsWith("warp_")) sim.setWarp(id.removePrefix("warp_").toInt())
        }
    }

    fun release(id: String) {
        held.remove(id)
        when (id) {
            "turn_l", "turn_r" -> sim.manualTurn = when {
                "turn_l" in held -> 1
                "turn_r" in held -> -1
                else -> 0
            }
            "engine" -> sim.manualEngine = false
        }
    }

    fun frame(): Frame {
        val d = Draw()
        when (view) {
            ViewMode.COCKPIT -> CockpitView.draw(d, sim, animTime)
            ViewMode.EXTERNAL -> ExternalView.draw(d, sim, animTime)
        }
        Hud.drawTop(d, sim, view)
        val list = ArrayList<Button>()
        list.addAll(Hud.drawPanel(d, sim, view, held))
        if (sim.status != Status.FLYING) list.addAll(Hud.drawEnd(d, sim))
        buttons = list
        return Frame(d.prims, list)
    }
}
