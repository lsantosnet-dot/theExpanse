package com.theexpanse.render

import com.theexpanse.core.AutopilotMode
import com.theexpanse.core.FireResult
import com.theexpanse.core.Simulation
import com.theexpanse.core.Status
import com.theexpanse.game.EtaEstimator
import com.theexpanse.game.GameSession
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

enum class ViewMode(val label: String) { COCKPIT("CABINE"), EXTERNAL("CÂMERA EXTERNA"), SPACE3D("VISTA 3D"), RADAR("RADAR TÁTICO");
    /** A vista seguinte no ciclo do botão VER. O radar tem botão próprio e não entra no ciclo. */
    fun next(): ViewMode {
        var n = values()[(ordinal + 1) % values().size]
        if (n == RADAR) n = values()[(n.ordinal + 1) % values().size]
        return n
    }
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

    fun drawTop(d: Draw, game: GameSession, mode: ViewMode, t: Double) {
        val sim = game.sim
        val s = sim.ship
        d.rect(8f, 8f, 244f, 64f, fill = Palette.PANEL.alpha(0.93f), stroke = Palette.PANEL_EDGE)
        d.text(16f, 22f, "T+ " + Fmt.clock(sim.time), Palette.MUTED, 10f)
        val warp = sim.warpLevels[sim.warpIndex]
        val eff = sim.effectiveWarp
        val warpText = "TEMPO ×${Fmt.int(warp)}" + if (eff < warp - 0.5) " · efetivo ×${Fmt.dec(eff, 1)}" else ""
        d.text(16f, 35f, warpText, Palette.AMBER, 10f)
        val apColor = if (sim.autopilot == AutopilotMode.OFF) Palette.INK else Palette.AMBER
        d.text(16f, 49f, "PILOTO ${sim.autopilot.label} · ${flightStatus(sim)}", apColor, 10f)
        val hullPct = (s.hull / s.hullMax * 100).toInt()
        val hullColor = if (hullPct > 60) Palette.INK else if (hullPct > 30) Palette.AMBER else Palette.RED_SOFT
        val arms = if (sim.world.isMilitary) " · MÍSSEIS ${s.missiles}" else ""
        d.text(16f, 63f, "CASCO $hullPct%$arms · DESPIST. ${s.decoys}", hullColor, 10f)

        d.text(Layout.W / 2, 20f, mode.label, Palette.MUTED, 9f, Align.CENTER)
        val eta = sim.impactEta()
        if (eta != null) {
            val pulse = 0.55f + 0.45f * sin(t * 10).toFloat()
            d.rect(Layout.W / 2 - 150f, 26f, 300f, 26f, fill = Palette.RED.alpha(0.18f + 0.15f * pulse), stroke = Palette.RED, radius = 3f)
            d.text(Layout.W / 2, 44f, "ALERTA DE MÍSSIL · IMPACTO EM " + Fmt.duration(eta), Palette.RED_SOFT, 12f, Align.CENTER, bold = true)
        } else if (sim.flipAlert()) {
            val sec = sim.secondsToFlip() ?: 0.0
            val msg = if (sec <= 0.5) "INVERTER AGORA" else "INVERTER EM " + Fmt.clock(sec)
            d.rect(Layout.W / 2 - 110f, 26f, 220f, 24f, fill = Palette.RED.alpha(0.18f), stroke = Palette.RED, radius = 3f)
            d.text(Layout.W / 2, 43f, msg, Palette.RED_SOFT, 12f, Align.CENTER, bold = true)
        } else sim.collisionWarning()?.let { (name, eta) ->
            val auto = sim.autopilot == AutopilotMode.INTERCEPT
            val pulse = 0.55f + 0.45f * sin(t * 6).toFloat()
            d.rect(Layout.W / 2 - 185f, 26f, 370f, 26f, fill = Palette.RED.alpha(0.12f + 0.12f * pulse), stroke = Palette.RED, radius = 3f)
            d.text(Layout.W / 2, 43f, Ui.clip("ROTA COLIDE COM ${name.uppercase()} · EM ${Fmt.duration(eta)}" + if (auto) " · DESVIANDO" else "", 11f, 360f), Palette.RED_SOFT, 11f, Align.CENTER, bold = true)
        }
        if (s.fuel <= 0.0) d.text(Layout.W / 2, 66f, "SEM COMBUSTÍVEL", Palette.RED_SOFT, 11f, Align.CENTER, bold = true)

        game.activeMission?.let { d.text(16f, 86f, Ui.clip("Missão: " + it.title, 10f, 330f), Palette.AMBER_SOFT, 10f) }
        // registro de mensagens: some sozinho depois de alguns segundos
        var y = Layout.SCENE_H - 14f
        for (m in game.messages.asReversed()) {
            val age = game.clock - m.at
            if (age > 9.0) break
            val fade = if (age > 6.0) ((9.0 - age) / 3.0).toFloat() else 1f
            d.text(16f, y, Ui.clip(m.text, 10f, 600f), Palette.AMBER_SOFT.alpha(fade), 10f)
            y -= 13f
            if (y < Layout.SCENE_H - 14f - 13f * 3) break
        }
    }

    /** Botões sobre a cena: câmera giratória, zoom e a ação de combate do papel (míssil ou despistador). */
    fun drawSceneButtons(d: Draw, game: GameSession, cam: Camera, held: Set<String>, t: Double, mode: ViewMode = ViewMode.COCKPIT): List<Button> {
        val ui = Buttons(d)
        val sim = game.sim
        val y = 204f
        ui.add("cam_l", 636f, y, 34f, 30f, "⟲", ButtonKind.HOLD, pressed = "cam_l" in held)
        ui.add("cam_reset", 674f, y, 34f, 30f, "●", active = !cam.centered)
        ui.add("cam_r", 712f, y, 34f, 30f, "⟳", ButtonKind.HOLD, pressed = "cam_r" in held)
        ui.add("zoom_out", 756f, y, 34f, 30f, "−")
        ui.add("zoom_in", 794f, y, 34f, 30f, "+")
        if (mode == ViewMode.SPACE3D) {
            ui.add("cam_u", 756f, y - 36f, 34f, 30f, "▲", ButtonKind.HOLD, pressed = "cam_u" in held)
            ui.add("cam_d", 794f, y - 36f, 34f, 30f, "▼", ButtonKind.HOLD, pressed = "cam_d" in held)
        }
        if (sim.world.isMilitary) {
            val r = sim.canFire()
            val ready = r == FireResult.FIRED && sim.status == Status.FLYING
            val sub = when (r) {
                FireResult.NO_AMMO -> "sem mísseis"
                FireResult.NO_TARGET -> "alvo: nave"
                FireResult.OUT_OF_RANGE -> "fora de alcance"
                FireResult.FRIENDLY -> "alvo amigo"
                else -> null
            }
            ui.add("fire", 440f, y - 2f, 124f, 34f, "MÍSSIL ×${sim.ship.missiles}" + (if (sub != null) "\n$sub" else ""), enabled = ready, accent = if (ready) Palette.RED else 0)
            val bc = sim.canBoard()
            ui.add("board", 568f, y - 2f, 58f, 34f, "ABORDAR", enabled = bc == com.theexpanse.core.BoardCheck.OK && sim.status == Status.FLYING, accent = if (bc == com.theexpanse.core.BoardCheck.OK) Palette.GREEN else 0)
            // radar tático: pisca quando há míssil em voo (de qualquer lado) ou nave hostil armada por perto
            val alert = sim.battleAlert()
            ui.add("radar", 330f, y - 2f, 104f, 34f, "RADAR", active = alert && sin(t * 8) > 0, accent = if (alert) Palette.AMBER else 0)
            // despistador: o militar também pode ser atacado por mísseis; o botão fica acima de MÍSSIL/ABORDAR
            val incoming = sim.incoming().isNotEmpty()
            val ok = incoming && sim.ship.decoys > 0 && sim.status == Status.FLYING
            val pulse = incoming && sin(t * 12) > 0
            ui.add("decoy", 440f, y - 40f, 186f, 34f, "DESPISTADOR ×${sim.ship.decoys}", enabled = ok, active = pulse, accent = if (ok) Palette.AMBER else 0)
        } else {
            val incoming = sim.incoming().isNotEmpty()
            val ok = incoming && sim.ship.decoys > 0 && sim.status == Status.FLYING
            val pulse = incoming && sin(t * 12) > 0
            ui.add("decoy", 440f, y - 2f, 186f, 34f, "DESPISTADOR ×${sim.ship.decoys}", enabled = ok, active = pulse, accent = if (ok) Palette.AMBER else 0)
        }
        return ui.list
    }

    private fun readout(d: Draw, x: Float, y: Float, label: String, value: String, color: Int = Palette.INK) {
        d.text(x, y, label, Palette.MUTED, 9f)
        d.text(x, y + 15f, value, color, 14f, bold = true)
    }

    /** Desenha o painel e devolve os botões (para a entrada de toque). */
    fun drawPanel(d: Draw, sim: Simulation, mode: ViewMode, held: Set<String>, eta: EtaEstimator): List<Button> {
        val y0 = Layout.PANEL_Y
        d.rect(0f, y0, Layout.W, Layout.PANEL_H, fill = Palette.PANEL)
        d.line(0f, y0, Layout.W, y0, Palette.PANEL_EDGE, 1.5f)
        val s = sim.ship
        val ui = Buttons(d)

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
        readout(d, 244f, y0 + 14f, "VEL. RELATIVA", Fmt.speed(rel.length))
        readout(d, 404f, y0 + 14f, "APROXIMAÇÃO", Fmt.speed(approach), if (approach > 0) Palette.GREEN else Palette.RED_SOFT)
        readout(d, 244f, y0 + 46f, "ACELERAÇÃO", Fmt.dec(s.burnAccel / 9.80665, 2) + " g")
        readout(d, 404f, y0 + 46f, "DIST. DE FRENAGEM", Fmt.distance(sim.brakingDistance()))

        // chegada: tempo de jogo e tempo real na escala escolhida
        val rem = eta.remaining(sim)
        val planned = sim.plannedWarp()
        val (lab1, lab2) = when (eta.kind) {
            EtaEstimator.Kind.CLOSEST -> "MENOR DIST. EM" to "TEMPO REAL (×${Fmt.int(planned)})"
            else -> "CHEGADA EM" to "TEMPO REAL (×${Fmt.int(planned)})"
        }
        val v1: String
        val v2: String
        when {
            sim.status != Status.FLYING -> { v1 = "—"; v2 = "—" }
            rem != null -> { v1 = Fmt.duration(rem); v2 = Fmt.duration(sim.realSecondsFor(rem)) }
            eta.kind == EtaEstimator.Kind.FAR -> { v1 = "> 25 h"; v2 = "—" }
            else -> { v1 = "calculando…"; v2 = "…" }
        }
        readout(d, 244f, y0 + 78f, lab1, v1, Palette.AMBER)
        readout(d, 404f, y0 + 78f, lab2, v2, Palette.AMBER_SOFT)

        val frac = (s.fuel / s.fuelCapacity).toFloat().coerceIn(0f, 1f)
        d.text(244f, y0 + 114f, "COMBUSTÍVEL ${Fmt.int(frac * 100.0)}% · Δv ${Fmt.speed(s.deltaV)} · massa ${Fmt.dec(s.mass / 1000, 1)} t", Palette.MUTED, 9f)
        d.rect(244f, y0 + 118f, 300f, 6f, fill = Palette.BUTTON, radius = 3f)
        d.rect(244f, y0 + 118f, 300f * frac, 6f, fill = Palette.AMBER, radius = 3f)
        val tgt = sim.target
        d.text(244f, y0 + 140f, "ALVO ${tgt.name} · ${Fmt.distance(sim.distanceTo())}", tgt.faction.argb, 10f, bold = true)

        // --- direita: piloto automático, tempo e sistema ---
        val bx = 566f
        val w4 = 62f
        val modes = listOf(
            Triple("ap_intercept", "INTERC.", AutopilotMode.INTERCEPT),
            Triple("ap_pro", "PRÓ", AutopilotMode.PROGRADE),
            Triple("ap_retro", "RETRO", AutopilotMode.RETROGRADE),
            Triple("ap_stop", "PARAR", AutopilotMode.STOP_SPIN),
        )
        for ((i, m) in modes.withIndex()) ui.add(m.first, bx + i * (w4 + 4f), y0 + 12f, w4, 44f, m.second, active = sim.autopilot == m.third)
        for (i in sim.warpLevels.indices) {
            val f = sim.warpLevels[i]
            val label = if (f >= 1000) "×${Fmt.dec(f / 1000, if (f % 1000 == 0.0) 0 else 1)}k" else "×${Fmt.int(f)}"
            ui.add("warp_$i", bx + i * 53.6f, y0 + 62f, 50f, 30f, label, active = sim.warpIndex == i)
        }
        ui.add("view", bx, y0 + 100f, 86f, 38f, "VER " + when (mode.next()) { ViewMode.COCKPIT -> "CABINE"; ViewMode.EXTERNAL -> "EXTERNA"; ViewMode.SPACE3D -> "3D"; ViewMode.RADAR -> "RADAR" })
        ui.add("target", bx + 90f, y0 + 100f, 86f, 38f, "ALVO")
        ui.add("menu", bx + 180f, y0 + 100f, 84f, 38f, "MENU", accent = Palette.AMBER)
        return ui.list
    }

    /** Aviso quando a nave cai ou é destruída e nenhuma regra do pacote encerra a partida. */
    /** Verificação da rota antes de engajar: barra de progresso enquanto roda e, se a rota não presta, o aviso com as opções. */
    fun drawPreflight(d: Draw, pre: Preflight?, verdict: Verdict?, t: Double): List<Button> {
        if (pre == null && verdict == null) return emptyList()
        val ui = Buttons(d)
        Ui.panel(d, 160f, 48f, 524f, 150f, Palette.PANEL.alpha(0.97f), if (verdict != null) Palette.RED else Palette.AMBER)
        if (pre != null) {
            d.text(422f, 80f, "VERIFICANDO A ROTA…", Palette.AMBER, 15f, Align.CENTER, bold = true)
            d.text(422f, 100f, "Simulando o piloto automático até ${pre.targetName}", Palette.MUTED, 10f, Align.CENTER)
            d.rect(220f, 118f, 404f, 10f, fill = Palette.BUTTON, stroke = Palette.BUTTON_EDGE, radius = 3f)
            d.rect(220f, 118f, 404f * pre.progress.coerceIn(0.02f, 1f), 10f, fill = Palette.AMBER, radius = 3f)
            ui.add("pf_cancel", 352f, 146f, 140f, 36f, "CANCELAR")
        } else if (verdict != null) {
            d.text(422f, 76f, Ui.clip("ROTA INVIÁVEL", 16f, 480f), Palette.RED_SOFT, 16f, Align.CENTER, bold = true)
            d.text(422f, 96f, Ui.clip(verdict.reason, 11f, 500f), Palette.INK, 11f, Align.CENTER, bold = true)
            Ui.paragraph(d, 190f, 114f, verdict.detail, 10f, Palette.MUTED, 470f, 2, 12f)
            val alt = verdict.alternative
            if (alt != null) {
                ui.add("pf_alt", 176f, 148f, 168f, 40f, "USAR ${alt.thrustPct}%\nchega em ${Fmt.duration(alt.seconds)}", active = true)
                ui.add("pf_cancel", 352f, 148f, 140f, 40f, "CANCELAR", active = true)
                ui.add("pf_force", 500f, 148f, 168f, 40f, "ENGAJAR\nMESMO ASSIM")
            } else {
                ui.add("pf_cancel", 232f, 148f, 190f, 40f, "CANCELAR", active = true)
                ui.add("pf_force", 430f, 148f, 190f, 40f, "ENGAJAR\nMESMO ASSIM")
            }
        }
        return ui.list
    }

    /** Sem combustível e à deriva: oferece o resgate, para o jogo não ficar travado. */
    fun drawStranded(d: Draw, game: GameSession): List<Button> {
        if (!game.stranded) return emptyList()
        val ui = Buttons(d)
        val fee = minOf(game.story.credits, GameSession.RESCUE_FEE)
        ui.add("rescue", Layout.W / 2 - 110f, 74f, 220f, 36f, "PEDIR RESGATE · -$fee ¢", active = true)
        return ui.list
    }

    fun drawCrash(d: Draw, sim: Simulation): List<Button> {
        if (sim.status != Status.CRASHED && sim.status != Status.DESTROYED) return emptyList()
        val ui = Buttons(d)
        Ui.panel(d, 222f, 40f, 400f, 130f, Palette.PANEL.alpha(0.96f), Palette.RED)
        d.text(422f, 76f, "NAVE DESTRUÍDA", Palette.RED_SOFT, 18f, Align.CENTER, bold = true)
        val why = if (sim.status == Status.DESTROYED) "Atingida por mísseis" else "Colisão com " + sim.world.planetName
        d.text(422f, 100f, why + " · T+ " + Fmt.clock(sim.time), Palette.INK, 11f, Align.CENTER)
        ui.add("restart", 342f, 118f, 160f, 40f, "JOGAR DE NOVO", active = true)
        return ui.list
    }
}
