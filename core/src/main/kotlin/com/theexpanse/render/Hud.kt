package com.theexpanse.render

import com.theexpanse.core.AutopilotMode
import com.theexpanse.core.Config
import com.theexpanse.game.GameSession
import com.theexpanse.core.Simulation
import com.theexpanse.core.Status
import kotlin.math.PI
import kotlin.math.abs

enum class ViewMode(val label: String) { COCKPIT("CABINE"), EXTERNAL("CÂMERA EXTERNA") }

/** Textos e painel de controles (a parte de baixo da tela). */
object Hud {
    private fun flightStatus(sim: Simulation): String {
        val s = sim.ship
        return when {
            abs(s.turnAlpha) > 1e-6 || abs(s.omega) > 0.01 -> "virando"
            s.burnAccel > 0.01 -> {
                val v = sim.relativeVelocity()
                val braking = v.x * kotlin.math.cos(s.angle) + v.y * kotlin.math.sin(s.angle) < 0
                if (braking) "frenando" else "queimando"
            }
            else -> "em deriva"
        }
    }

    fun drawTop(d: Draw, game: GameSession, mode: ViewMode) {
        val sim = game.sim
        val s = sim.ship
        d.rect(8f, 8f, 232f, 50f, fill = Palette.PANEL.alpha(0.82f), stroke = Palette.PANEL_EDGE)
        d.text(16f, 22f, "T+ " + Fmt.clock(sim.time), Palette.MUTED, 10f)
        val warp = Config.WARP_FACTORS[sim.warpIndex]
        val eff = sim.effectiveWarp
        val warpText = "TEMPO ×${Fmt.int(warp)}" + if (eff < warp - 0.5) " · efetivo ×${Fmt.dec(eff, 1)}" else ""
        d.text(16f, 35f, warpText, Palette.AMBER, 10f)
        val apColor = if (sim.autopilot == AutopilotMode.OFF) Palette.INK else Palette.AMBER
        d.text(16f, 49f, "PILOTO ${sim.autopilot.label} · ${flightStatus(sim)}", apColor, 10f)

        d.text(Layout.W / 2, 20f, mode.label, Palette.MUTED, 9f, Align.CENTER)
        if (sim.flipAlert()) {
            val sec = sim.secondsToFlip() ?: 0.0
            val msg = if (sec <= 0.5) "INVERTER AGORA" else "INVERTER EM " + Fmt.clock(sec)
            d.rect(Layout.W / 2 - 110f, 26f, 220f, 24f, fill = Palette.RED.alpha(0.18f), stroke = Palette.RED, radius = 3f)
            d.text(Layout.W / 2, 43f, msg, Palette.RED_SOFT, 12f, Align.CENTER, bold = true)
        }
        if (s.fuel <= 0.0) d.text(Layout.W / 2, 62f, "SEM COMBUSTÍVEL", Palette.RED_SOFT, 11f, Align.CENTER, bold = true)

        game.activeMission?.let { d.text(16f, 72f, Ui.clip("Missão: " + it.title, 10f, 330f), Palette.AMBER_SOFT, 10f) }
        // registro de mensagens: some sozinho depois de alguns segundos
        var y = Layout.SCENE_H - 14f
        for (m in game.messages.asReversed()) {
            val age = game.clock - m.at
            if (age > 9.0) break
            val fade = if (age > 6.0) ((9.0 - age) / 3.0).toFloat() else 1f
            d.text(16f, y, Ui.clip(m.text, 10f, 560f), Palette.AMBER_SOFT.alpha(fade), 10f)
            y -= 13f
            if (y < Layout.SCENE_H - 14f - 13f * 3) break
        }
    }

    private fun readout(d: Draw, x: Float, y: Float, label: String, value: String, color: Int = Palette.INK) {
        d.text(x, y, label, Palette.MUTED, 9f)
        d.text(x, y + 15f, value, color, 14f, bold = true)
    }

    /** Desenha o painel e devolve os botões (para a entrada de toque). */
    fun drawPanel(d: Draw, sim: Simulation, mode: ViewMode, held: Set<String>): List<Button> {
        val ui = Buttons(d)
        val y0 = Layout.PANEL_Y
        d.rect(0f, y0, Layout.W, Layout.PANEL_H, fill = Palette.PANEL)
        d.line(0f, y0, Layout.W, y0, Palette.PANEL_EDGE, 1.5f)
        val s = sim.ship

        // --- esquerda: giro e motor ---
        d.text(14f, y0 + 16f, "GIRAR", Palette.MUTED, 9f)
        ui.add("turn_l", 14f, y0 + 22f, 56f, 50f, "◀", ButtonKind.HOLD, pressed = "turn_l" in held)
        ui.add("turn_r", 76f, y0 + 22f, 56f, 50f, "▶", ButtonKind.HOLD, pressed = "turn_r" in held)
        ui.add("thr_down", 14f, y0 + 78f, 56f, 34f, "EMP −")
        ui.add("thr_up", 76f, y0 + 78f, 56f, 34f, "EMP +")
        val deg = ((s.angle * 180 / PI) % 360 + 360) % 360
        d.text(14f, y0 + 130f, "rumo ${Fmt.int(deg)}° · giro ${Fmt.dec(s.omega * 180 / PI, 1)}°/s", Palette.MUTED, 9f)
        d.text(142f, y0 + 16f, "EMPUXO ${sim.thrustPct}%", Palette.MUTED, 9f)
        d.rect(142f, y0 + 22f, 12f, 90f, fill = Palette.BUTTON, stroke = Palette.BUTTON_EDGE, radius = 2f)
        val fill = 90f * sim.thrustPct / 100f
        d.rect(142f, y0 + 22f + 90f - fill, 12f, fill, fill = Palette.AMBER, radius = 2f)
        ui.add("engine", 160f, y0 + 22f, 64f, 90f, "MOTOR", ButtonKind.HOLD, pressed = "engine" in held, accent = Palette.AMBER)

        // --- centro: leituras ---
        val rel = sim.relativeVelocity()
        val approach = sim.approachSpeed()
        readout(d, 244f, y0 + 16f, "VEL. RELATIVA", Fmt.speed(rel.length))
        readout(d, 404f, y0 + 16f, "APROXIMAÇÃO", Fmt.speed(approach), if (approach > 0) Palette.GREEN else Palette.RED_SOFT)
        readout(d, 244f, y0 + 52f, "ACELERAÇÃO", Fmt.dec(s.burnAccel / 9.80665, 2) + " g")
        readout(d, 404f, y0 + 52f, "DIST. DE FRENAGEM", Fmt.distance(sim.brakingDistance()))
        val frac = (s.fuel / s.fuelCapacity).toFloat().coerceIn(0f, 1f)
        d.text(244f, y0 + 92f, "COMBUSTÍVEL ${Fmt.int(frac * 100.0)}% · Δv ${Fmt.speed(s.deltaV)} · massa ${Fmt.dec(s.mass / 1000, 1)} t", Palette.MUTED, 9f)
        d.rect(244f, y0 + 97f, 300f, 7f, fill = Palette.BUTTON, radius = 3f)
        d.rect(244f, y0 + 97f, 300f * frac, 7f, fill = Palette.AMBER, radius = 3f)
        val tgt = sim.target
        d.text(244f, y0 + 124f, "ALVO ${tgt.name} · ${Fmt.distance(sim.distanceTo())}", tgt.faction.argb, 10f, bold = true)
        val p = sim.prediction()
        d.text(244f, y0 + 138f, "Sem motor: ${Fmt.distance(p.closestDistance)} em ${Fmt.clock(p.closestTime)}", Palette.MUTED, 9f)

        // --- direita: piloto automático, tempo e sistema ---
        val bx = 566f
        val w4 = 62f
        val modes = listOf(
            Triple("ap_intercept", "INTERC.", AutopilotMode.INTERCEPT),
            Triple("ap_pro", "PRÓ", AutopilotMode.PROGRADE),
            Triple("ap_retro", "RETRO", AutopilotMode.RETROGRADE),
            Triple("ap_stop", "PARAR", AutopilotMode.STOP_SPIN),
        )
        for ((i, m) in modes.withIndex()) {
            ui.add(m.first, bx + i * (w4 + 4f), y0 + 12f, w4, 44f, m.second, active = sim.autopilot == m.third)
        }
        for (i in Config.WARP_FACTORS.indices) {
            val f = Config.WARP_FACTORS[i]
            val label = if (f >= 1000) "×${Fmt.int(f / 1000)}k" else "×${Fmt.int(f)}"
            ui.add("warp_$i", bx + i * 53.6f, y0 + 62f, 50f, 30f, label, active = sim.warpIndex == i)
        }
        ui.add("view", bx, y0 + 100f, 86f, 38f, if (mode == ViewMode.COCKPIT) "VER EXTERNA" else "VER CABINE")
        ui.add("target", bx + 90f, y0 + 100f, 86f, 38f, "ALVO")
        ui.add("menu", bx + 180f, y0 + 100f, 84f, 38f, "MENU", accent = Palette.AMBER)
        return ui.list
    }

    /** Aviso quando a nave cai e nenhuma regra do pacote encerra a partida. */
    fun drawCrash(d: Draw, sim: Simulation): List<Button> {
        if (sim.status != Status.CRASHED) return emptyList()
        val ui = Buttons(d)
        Ui.panel(d, 222f, 40f, 400f, 130f, Palette.PANEL.alpha(0.96f), Palette.RED)
        d.text(422f, 76f, "NAVE DESTRUÍDA", Palette.RED_SOFT, 18f, Align.CENTER, bold = true)
        d.text(422f, 100f, "Colisão com " + sim.world.planetName + " · T+ " + Fmt.clock(sim.time), Palette.INK, 11f, Align.CENTER)
        ui.add("restart", 342f, 118f, 160f, 40f, "JOGAR DE NOVO", active = true)
        return ui.list
    }
}
