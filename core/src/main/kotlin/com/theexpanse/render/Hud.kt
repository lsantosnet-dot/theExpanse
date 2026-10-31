package com.theexpanse.render

import com.theexpanse.core.AutopilotMode
import com.theexpanse.core.Config
import com.theexpanse.core.Simulation
import com.theexpanse.core.Status
import kotlin.math.PI
import kotlin.math.abs

enum class ViewMode(val label: String) { COCKPIT("CABINE"), EXTERNAL("CÂMERA EXTERNA") }

enum class ButtonKind { TAP, HOLD }

class Button(
    val id: String,
    val x: Float, val y: Float, val w: Float, val h: Float,
    val label: String,
    val kind: ButtonKind = ButtonKind.TAP,
    val active: Boolean = false,
    val pressed: Boolean = false,
    val accent: Int = 0,
) {
    fun contains(px: Float, py: Float) = px >= x && px <= x + w && py >= y && py <= y + h
}

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

    fun drawTop(d: Draw, sim: Simulation, mode: ViewMode) {
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
    }

    private fun readout(d: Draw, x: Float, y: Float, label: String, value: String, color: Int = Palette.INK) {
        d.text(x, y, label, Palette.MUTED, 9f)
        d.text(x, y + 15f, value, color, 14f, bold = true)
    }

    /** Desenha o painel e devolve os botões (para a entrada de toque). */
    fun drawPanel(d: Draw, sim: Simulation, mode: ViewMode, held: Set<String>): List<Button> {
        val y0 = Layout.PANEL_Y
        d.rect(0f, y0, Layout.W, Layout.PANEL_H, fill = Palette.PANEL)
        d.line(0f, y0, Layout.W, y0, Palette.PANEL_EDGE, 1.5f)
        val s = sim.ship
        val buttons = ArrayList<Button>()
        fun add(b: Button) { buttons.add(b) }

        // --- esquerda: giro e motor ---
        d.text(14f, y0 + 16f, "GIRAR", Palette.MUTED, 9f)
        add(Button("turn_l", 14f, y0 + 22f, 56f, 50f, "◀", ButtonKind.HOLD, pressed = "turn_l" in held))
        add(Button("turn_r", 76f, y0 + 22f, 56f, 50f, "▶", ButtonKind.HOLD, pressed = "turn_r" in held))
        add(Button("thr_down", 14f, y0 + 78f, 56f, 34f, "EMP −"))
        add(Button("thr_up", 76f, y0 + 78f, 56f, 34f, "EMP +"))
        val deg = ((s.angle * 180 / PI) % 360 + 360) % 360
        d.text(14f, y0 + 130f, "rumo ${Fmt.int(deg)}° · giro ${Fmt.dec(s.omega * 180 / PI, 1)}°/s", Palette.MUTED, 9f)
        d.text(142f, y0 + 16f, "EMPUXO ${sim.thrustPct}%", Palette.MUTED, 9f)
        d.rect(142f, y0 + 22f, 12f, 90f, fill = Palette.BUTTON, stroke = Palette.BUTTON_EDGE, radius = 2f)
        val fill = 90f * sim.thrustPct / 100f
        d.rect(142f, y0 + 22f + 90f - fill, 12f, fill, fill = Palette.AMBER, radius = 2f)
        add(Button("engine", 160f, y0 + 22f, 64f, 90f, "MOTOR", ButtonKind.HOLD, pressed = "engine" in held, accent = Palette.AMBER))

        // --- centro: leituras ---
        val rel = sim.relativeVelocity()
        val approach = sim.approachSpeed()
        readout(d, 244f, y0 + 16f, "VEL. RELATIVA", Fmt.speed(rel.length))
        readout(d, 404f, y0 + 16f, "APROXIMAÇÃO", Fmt.speed(approach), if (approach > 0) Palette.GREEN else Palette.RED_SOFT)
        readout(d, 244f, y0 + 52f, "ACELERAÇÃO", Fmt.dec(s.burnAccel / 9.80665, 2) + " g")
        readout(d, 404f, y0 + 52f, "DIST. DE FRENAGEM", Fmt.distance(sim.brakingDistance()))
        val frac = (s.fuel / Config.FUEL_MASS).toFloat().coerceIn(0f, 1f)
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
            add(Button(m.first, bx + i * (w4 + 4f), y0 + 12f, w4, 44f, m.second, active = sim.autopilot == m.third))
        }
        for (i in Config.WARP_FACTORS.indices) {
            val f = Config.WARP_FACTORS[i]
            val label = if (f >= 1000) "×${Fmt.int(f / 1000)}k" else "×${Fmt.int(f)}"
            add(Button("warp_$i", bx + i * 53.6f, y0 + 62f, 50f, 30f, label, active = sim.warpIndex == i))
        }
        add(Button("view", bx, y0 + 100f, 86f, 38f, if (mode == ViewMode.COCKPIT) "VER EXTERNA" else "VER CABINE"))
        add(Button("target", bx + 90f, y0 + 100f, 86f, 38f, "ALVO"))
        add(Button("reset", bx + 180f, y0 + 100f, 84f, 38f, "REINICIAR"))

        for (b in buttons) drawButton(d, b)
        return buttons
    }

    private fun drawButton(d: Draw, b: Button) {
        val lit = b.active || b.pressed
        val fill = when {
            b.pressed -> Palette.AMBER
            b.active -> Palette.AMBER_DIM
            else -> Palette.BUTTON
        }
        val edge = if (lit || b.accent != 0) Palette.AMBER else Palette.BUTTON_EDGE
        d.rect(b.x, b.y, b.w, b.h, fill = fill, stroke = edge, strokeW = if (lit) 1.6f else 1f, radius = 6f)
        val textColor = when {
            b.pressed -> 0xFF1A1206.toInt()
            b.active || b.accent != 0 -> Palette.AMBER
            else -> Palette.INK
        }
        val size = if (b.h > 60f) 13f else if (b.id.startsWith("warp")) 10f else 11f
        d.text(b.x + b.w / 2, b.y + b.h / 2 + size * 0.35f, b.label, textColor, size, Align.CENTER, bold = true)
    }

    /** Faixa central quando a partida acaba (atracou ou caiu). */
    fun drawEnd(d: Draw, sim: Simulation): List<Button> {
        if (sim.status == Status.FLYING) return emptyList()
        val x = 222f
        val y = 40f
        val w = 400f
        val h = 150f
        d.rect(x, y, w, h, fill = Palette.PANEL.alpha(0.95f), stroke = Palette.AMBER, strokeW = 1.6f, radius = 6f)
        val (title, line1, line2) = if (sim.status == Status.DOCKED) {
            Triple("ATRACADO · ${sim.dockedAt?.name ?: ""}", "T+ ${Fmt.clock(sim.time)} · combustível reabastecido", "Δv de volta: ${Fmt.speed(sim.ship.deltaV)}")
        } else {
            Triple("NAVE DESTRUÍDA", "Colisão com ${com.theexpanse.core.Calice.PLANET_NAME}", "T+ ${Fmt.clock(sim.time)}")
        }
        d.text(x + w / 2, y + 36f, title, if (sim.status == Status.DOCKED) Palette.AMBER else Palette.RED_SOFT, 18f, Align.CENTER, bold = true)
        d.text(x + w / 2, y + 62f, line1, Palette.INK, 11f, Align.CENTER)
        d.text(x + w / 2, y + 80f, line2, Palette.MUTED, 11f, Align.CENTER)
        val btn = Button("reset", x + w / 2 - 80f, y + 98f, 160f, 40f, "JOGAR DE NOVO", active = true)
        drawButton(d, btn)
        return listOf(btn)
    }
}
