package com.theexpanse.render

import com.theexpanse.content.BodyKind
import com.theexpanse.content.EndingKind
import com.theexpanse.core.Body
import com.theexpanse.core.Status
import com.theexpanse.game.GameSession
import com.theexpanse.game.RouteOutcome
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

private fun background(d: Draw) = d.rect(0f, 0f, Layout.W, Layout.H, fill = Palette.BG)

/** Início: campanhas instaladas, importar e exportar template. */
object HomeScreen {
    fun draw(d: Draw, c: AppController): List<Button> {
        background(d)
        val ui = Buttons(d)
        d.text(16f, 34f, "THE EXPANSE", Palette.AMBER, 22f, bold = true)
        d.text(16f, 52f, "Física real, política e mistério. Escolha uma campanha.", Palette.MUTED, 10f)

        val perPage = 3
        val pages = ((c.entries.size + perPage - 1) / perPage).coerceAtLeast(1)
        c.homePage = c.homePage.coerceIn(0, pages - 1)
        for ((i, e) in c.entries.drop(c.homePage * perPage).take(perPage).withIndex()) {
            val y = 66f + i * 94f
            Ui.panel(d, 16f, y, 548f, 86f)
            d.text(28f, y + 22f, e.pack.title, Palette.INK, 14f, bold = true)
            Ui.paragraph(d, 28f, y + 38f, e.pack.summary, 10f, Palette.MUTED, 330f, 3, 12f)
            var px = 28f
            px += Ui.pill(d, px, y + 66f, if (e.builtin) "incluída" else "importada", if (e.builtin) Palette.AMBER else Palette.GREEN)
            val role = if (e.pack.role.kind == com.theexpanse.content.RoleKind.MILITARY) "militar · " + (e.pack.faction(e.pack.role.factionId ?: "")?.name ?: "") else "mercador"
            px += Ui.pill(d, px, y + 66f, role, if (e.pack.role.kind == com.theexpanse.content.RoleKind.MILITARY) Palette.RED_SOFT else Palette.GREEN)
            px += Ui.pill(d, px, y + 66f, "${e.pack.endings.size} finais", Palette.MUTED)
            if (e.pack.facts.isNotEmpty() && px < 330f) px += Ui.pill(d, px, y + 66f, "${e.pack.facts.size} pistas", 0xFF9ADCF0.toInt())
            ui.add("home_play:${e.pack.id}", 380f, y + 10f, 90f, 34f, if (e.hasSave) "NOVO JOGO" else "JOGAR", active = !e.hasSave)
            if (e.hasSave) {
                ui.add("home_resume:${e.pack.id}", 476f, y + 10f, 80f, 34f, "CONTINUAR", active = true)
                ui.add("home_saves:${e.pack.id}", 380f, y + 50f, 90f, 28f, "SAVES · ${e.saveCount}")
            }
            if (!e.builtin) ui.add("home_del:${e.pack.id}", 520f, y + 50f, 36f, 28f, "✕")
        }
        if (pages > 1) {
            ui.add("home_prev", 16f, 352f, 44f, 30f, "◀", enabled = c.homePage > 0)
            ui.add("home_next", 66f, 352f, 44f, 30f, "▶", enabled = c.homePage < pages - 1)
            d.text(120f, 372f, "${c.homePage + 1}/$pages", Palette.MUTED, 10f)
        }
        c.banner?.let { d.text(300f, 372f, Ui.clip(it, 10f, 264f), Palette.GREEN, 10f) }

        Ui.panel(d, 580f, 66f, 248f, 304f)
        d.text(592f, 88f, "CRIAR UMA HISTÓRIA NOVA", Palette.AMBER, 10f, bold = true)
        val steps = listOf(
            "Exporte o template: guia, vocabulário e um exemplo completo.",
            "Peça a uma IA uma história e salve o JSON que ela gerar.",
            "Importe aqui. O jogo confere se o mistério tem solução.",
        )
        var y = 108f
        for ((i, s) in steps.withIndex()) {
            d.circle(600f, y - 3f, 9f, fill = Palette.AMBER_DIM)
            d.text(600f, y + 1f, "${i + 1}", Palette.AMBER, 10f, Align.CENTER, bold = true)
            y = Ui.paragraph(d, 616f, y, s, 10f, Palette.INK, 204f, 3, 12f) + 12f
        }
        ui.add("home_export", 592f, 262f, 224f, 40f, "EXPORTAR TEMPLATE")
        ui.add("home_import", 592f, 312f, 224f, 46f, "IMPORTAR PACOTE JSON", active = true)
        return ui.list
    }

    fun drawErrors(d: Draw, c: AppController): List<Button> {
        background(d)
        val ui = Buttons(d)
        Ui.title(d, "PACOTE RECUSADO", c.errorsTitle)
        val perPage = 5
        val pages = ((c.errors.size + perPage - 1) / perPage).coerceAtLeast(1)
        c.errorsPage = c.errorsPage.coerceIn(0, pages - 1)
        var y = 66f
        for (e in c.errors.drop(c.errorsPage * perPage).take(perPage)) {
            val lines = Ui.wrap(e, 11f, 690f)
            val h = lines.size.coerceAtMost(3) * 14f + 14f
            Ui.panel(d, 16f, y, 720f, h, Palette.PANEL, Palette.RED.alpha(0.6f))
            Ui.paragraph(d, 28f, y + 17f, "✕ $e", 11f, Palette.RED_SOFT, 696f, 3, 14f)
            y += h + 6f
        }
        d.text(16f, 372f, "${c.errors.size} erro(s) · página ${c.errorsPage + 1}/$pages. Copie e devolva à IA para corrigir.", Palette.MUTED, 10f)
        ui.add("home_errors_up", 752f, 66f, 76f, 36f, "▲", enabled = c.errorsPage > 0)
        ui.add("home_errors_down", 752f, 108f, 76f, 36f, "▼", enabled = c.errorsPage < pages - 1)
        ui.add("home_errors_copy", 752f, 270f, 76f, 50f, "COPIAR\nERROS", active = true)
        ui.add("home_errors_close", 752f, 330f, 76f, 44f, "FECHAR")
        return ui.list
    }
}

/** Mapa tático: vista de cima com grade, órbitas, previsão e escolha de alvo por toque; serve do Cálice ao Sistema Solar. */
object MapScreen {
    private const val MW = 554f
    val CENTER_LABELS = listOf("CENTRO: ORIGEM", "CENTRO: NAVE", "CENTRO: ALVO", "CENTRO: NAVE+ALVO")

    private fun nice(m: Double): Double {
        val p = 10.0.pow(floor(log10(m)))
        val f = m / p
        return p * when { f < 1.5 -> 1.0; f < 3.5 -> 2.0; f < 7.5 -> 5.0; else -> 10.0 }
    }

    /** Metros por pixel no zoom 0 (mostra o sistema todo). */
    fun baseScale(g: GameSession): Double = g.world.extent * 1.05 / (Layout.H / 2f)

    /** Ajusta o zoom e o centro para mostrar a nave e o alvo juntos. */
    fun fit(c: AppController, g: GameSession) {
        val d = g.sim.distanceTo().coerceAtLeast(2000.0)
        val need = d * 2.3 / MW
        c.mapZoom = (ln(baseScale(g) / need) / ln(2.0)).roundToInt().coerceIn(-1, 26)
        c.mapCenter = 3
    }

    fun draw(d: Draw, c: AppController, g: GameSession): List<Button> {
        background(d)
        val ui = Buttons(d)
        val sim = g.sim
        val w = g.world
        val mpp = baseScale(g) / 2.0.pow(c.mapZoom)
        val tgt = sim.target
        val center = when (c.mapCenter) {
            1 -> sim.ship.pos
            2 -> sim.position(tgt)
            3 -> (sim.ship.pos + sim.position(tgt)) / 2.0
            else -> com.theexpanse.core.Vec2.ZERO
        }
        val cx = MW / 2
        val cy = Layout.H / 2
        fun sx(x: Double) = (cx + (x - center.x) / mpp).toFloat()
        fun sy(y: Double) = (cy - (y - center.y) / mpp).toFloat()

        d.clip(0f, 0f, MW, Layout.H)
        // grade
        val step = nice(mpp * 70)
        var gx = floor((center.x - cx * mpp) / step) * step
        var guard = 0
        while (gx < center.x + cx * mpp && guard++ < 60) { val x = sx(gx); d.line(x, 0f, x, Layout.H, 0xFF111A28.toInt(), 1f); gx += step }
        var gy = floor((center.y - cy * mpp) / step) * step
        guard = 0
        while (gy < center.y + cy * mpp && guard++ < 60) { val y = sy(gy); d.line(0f, y, MW, y, 0xFF111A28.toInt(), 1f); gy += step }

        // órbitas pontilhadas (elípticas, em torno do corpo pai)
        for (b in w.bodies) {
            if (!sim.alive(b)) continue
            val par = b.parent?.position(sim.time) ?: com.theexpanse.core.Vec2.ZERO
            val a = b.orbitRadius
            val e = b.def.eccentricity
            val rpx = (a / mpp).toFloat()
            if (rpx < 3f || rpx > 6000f) continue
            val n = (rpx * 0.8f).toInt().coerceIn(48, 260)
            val cw = cos(b.def.periapsis)
            val sw = sin(b.def.periapsis)
            val bb = a * sqrt(1 - e * e)
            for (k in 0 until n) {
                val ea = 2 * PI * k / n
                val px = a * (cos(ea) - e)
                val py = bb * sin(ea)
                val x = sx(par.x + px * cw - py * sw)
                val y = sy(par.y + px * sw + py * cw)
                if (x < -4 || x > MW + 4 || y < -4 || y > Layout.H + 4) continue
                d.circle(x, y, 0.9f, fill = b.argb.alpha(if (b.kind == BodyKind.PLANET) 0.35f else 0.55f))
            }
        }
        // corpo central
        val pr = (w.radius / mpp).toFloat().coerceAtLeast(5f)
        if (w.star) Planet.drawStar(d, sx(0.0), sy(0.0), pr.coerceAtMost(40f))
        else d.circle(sx(0.0), sy(0.0), pr, fill = 0xFFF3D9A8.toInt(), stroke = 0xFFFFE6BE.toInt())
        d.text(sx(0.0), sy(0.0) + pr.coerceAtMost(40f) + 12f, w.planetName, 0xFFF3D9A8.toInt(), 10f, Align.CENTER, bold = true)

        // previsão sem empuxo
        val pred = sim.prediction()
        for (p in pred.points) d.circle(sx(p.x), sy(p.y), 1.2f, fill = Palette.AMBER.alpha(0.85f))

        // linha até o alvo
        if (sim.alive(tgt)) {
            val tp = sim.position(tgt)
            d.line(sx(sim.ship.pos.x), sy(sim.ship.pos.y), sx(tp.x), sy(tp.y), Palette.AMBER.alpha(0.5f), 1f, dash = 5f)
        }

        // corpos
        for (b in w.bodies) {
            if (!sim.alive(b)) continue
            val p = sim.position(b)
            val x = sx(p.x)
            val y = sy(p.y)
            if (x < -30 || x > MW + 30 || y < -30 || y > Layout.H + 30) continue
            val color = b.argb
            when (b.kind) {
                BodyKind.STATION -> d.rect(x - 6f, y - 6f, 12f, 12f, fill = color)
                BodyKind.WRECK -> d.poly(floatArrayOf(x - 7f, y, x, y - 7f, x + 7f, y, x, y + 7f), fill = color.alpha(0.8f))
                BodyKind.SHIP -> d.poly(floatArrayOf(x - 6f, y + 5f, x, y - 7f, x + 6f, y + 5f), fill = color)
                BodyKind.PLANET -> d.circle(x, y, (b.def.radius / mpp).toFloat().coerceIn(4f, 40f), fill = color)
            }
            if (b === tgt) d.circle(x, y, 11f, stroke = Palette.AMBER, strokeW = 1.5f)
            if (b === tgt || b.parent == null || b.kind == BodyKind.SHIP || b.orbitRadius / mpp >= 14.0) d.text(x + 10f, if (b.kind == BodyKind.SHIP) y + 16f else y - 8f, b.shortName, color, 10f, bold = b === tgt)
            ui.add("pick:${b.id}", x - 18f, y - 18f, 36f, 36f, "", hidden = true)
        }

        // nave com marcadores de prógrado e retrógrado
        val s = sim.ship
        val shx = sx(s.pos.x)
        val shy = sy(s.pos.y)
        d.poly(placePoints(floatArrayOf(10f, 0f, -6f, -6f, -3f, 0f, -6f, 6f), shx, shy, s.angle), fill = Palette.INK, stroke = Palette.AMBER)
        d.text(shx + 10f, shy + 16f, "VOCÊ", Palette.INK, 9f)
        val vr = sim.relativeVelocity()
        if (vr.length > 0.5) {
            val a = vr.angle
            val px = shx + cos(a).toFloat() * 30f
            val py = shy - sin(a).toFloat() * 30f
            d.circle(px, py, 6f, stroke = Palette.GREEN, strokeW = 1.5f)
            val rx = shx - cos(a).toFloat() * 30f
            val ry = shy + sin(a).toFloat() * 30f
            d.line(rx - 5f, ry - 5f, rx + 5f, ry + 5f, Palette.RED, 1.6f); d.line(rx - 5f, ry + 5f, rx + 5f, ry - 5f, Palette.RED, 1.6f)
        }
        // barra de escala
        val barM = nice(mpp * 90)
        val barPx = (barM / mpp).toFloat()
        d.line(20f, 366f, 20f + barPx, 366f, Palette.MUTED, 2f)
        d.text(20f, 358f, Fmt.distance(barM), Palette.MUTED, 9f)
        d.unclip()

        ui.add("map_zoom_in", 500f, 300f, 44f, 36f, "+")
        ui.add("map_zoom_out", 500f, 342f, 44f, 36f, "−")
        ui.add("map_center", 12f, 10f, 134f, 30f, CENTER_LABELS[c.mapCenter])
        ui.add("map_fit", 150f, 10f, 70f, 30f, "AJUSTAR")
        d.text(228f, 30f, "toque num corpo para escolher o alvo", Palette.MUTED, 9f)

        // painel do alvo
        Ui.panel(d, MW, 0f, Layout.W - MW, Layout.H, Palette.PANEL, Palette.PANEL_EDGE)
        d.text(MW + 14f, 24f, "ALVO SELECIONADO", Palette.MUTED, 9f)
        d.text(MW + 14f, 46f, Ui.clip(tgt.name, 16f, 262f), Palette.INK, 16f, bold = true)
        d.text(MW + 14f, 62f, tgt.faction.name + " · " + when (tgt.kind) { BodyKind.STATION -> "estação"; BodyKind.WRECK -> "destroços"; BodyKind.SHIP -> "nave"; BodyKind.PLANET -> "planeta ou lua" }, tgt.argb, 10f)
        val rows = listOf(
            "distância" to Fmt.distance(sim.distanceTo()),
            "vel. relativa" to Fmt.speed(sim.relativeVelocity().length),
            "aproximação" to Fmt.speed(sim.approachSpeed()),
            "sem motor" to Fmt.distance(pred.closestDistance),
            "em" to Fmt.duration(pred.closestTime),
            "Δv restante" to Fmt.speed(s.deltaV),
        )
        var y = 92f
        for ((k, v) in rows) { d.text(MW + 14f, y, k, Palette.MUTED, 10f); d.text(MW + 276f, y, v, Palette.INK, 11f, Align.RIGHT, bold = true); y += 17f }
        ui.add("open_route", MW + 14f, 258f, 262f, 44f, "PLANEJAR ROTA", active = true)
        ui.back("back", "FECHAR MAPA")
        return ui.list
    }
}

/** Plano de rota: tempo e combustível reais do piloto automático em três níveis de empuxo. */
object RouteScreen {
    fun draw(d: Draw, c: AppController, g: GameSession): List<Button> {
        background(d)
        val ui = Buttons(d)
        val tgt = g.sim.target
        val job = c.routeJob
        Ui.title(d, "PLANO DE ROTA", "Destino: ${tgt.name} · ${Fmt.distance(g.sim.distanceTo())} · simulado com o piloto automático de verdade")
        val opts = job?.options ?: emptyList()
        for ((i, o) in opts.withIndex()) {
            val x = 16f + i * 272f
            val sel = i == c.routeSelected
            val pct = job!!.pcts[i]
            d.rect(x, 74f, 260f, 190f, fill = if (sel) 0xFF1A1608.toInt() else Palette.PANEL, stroke = if (sel) Palette.AMBER else Palette.PANEL_EDGE, strokeW = if (sel) 2f else 1f, radius = 6f)
            d.text(x + 14f, 100f, "EMPUXO $pct%", if (sel) Palette.AMBER else Palette.INK, 16f, bold = true)
            d.text(x + 14f, 116f, Fmt.dec(g.sim.ship.maxAccel * pct / 100, 1) + " m/s² de aceleração", Palette.MUTED, 10f)
            var y = 142f
            if (o == null) {
                d.text(x + 14f, y, "calculando… ${(job.progress(i) * 100).toInt()}%", Palette.AMBER_SOFT, 12f, bold = true)
                d.rect(x + 14f, y + 14f, 232f, 6f, fill = Palette.BUTTON, radius = 3f)
                d.rect(x + 14f, y + 14f, 232f * job.progress(i), 6f, fill = Palette.AMBER, radius = 3f)
                ui.add("route_sel:$i", x, 74f, 260f, 190f, "", hidden = true)
                continue
            }
            when (o.outcome) {
                RouteOutcome.DOCKS -> {
                    d.text(x + 14f, y, "viagem: " + Fmt.duration(o.seconds), Palette.INK, 12f, bold = true); y += 20f
                    d.text(x + 14f, y, "combustível: ${Fmt.int(o.fuelPct)}% (${Fmt.int(o.fuelUsed)} kg)", Palette.INK, 11f); y += 20f
                    d.text(x + 14f, y, "tempo real: ~" + Fmt.duration(g.sim.realSecondsFor(o.seconds, g.world.timeScale)), Palette.AMBER_SOFT, 11f); y += 20f
                    d.text(x + 14f, y, "chegada: atraca", Palette.GREEN, 11f)
                    val note = when {
                        o.fuelPct > 85 -> "Quase todo o combustível."
                        pct <= 20 -> "Econômica e lenta."
                        else -> ""
                    }
                    if (note.isNotEmpty()) Ui.paragraph(d, x + 14f, y + 20f, note, 10f, Palette.AMBER_SOFT, 230f, 2)
                }
                RouteOutcome.CRASHES -> { d.text(x + 14f, y, "colide com um corpo", Palette.RED_SOFT, 12f, bold = true); Ui.paragraph(d, x + 14f, y + 20f, "O piloto automático não evita o planeta nesta rota.", 10f, Palette.MUTED, 230f, 3) }
                RouteOutcome.TIMEOUT -> { d.text(x + 14f, y, "não chega no prazo", Palette.RED_SOFT, 12f, bold = true); Ui.paragraph(d, x + 14f, y + 20f, "Falta combustível ou o alvo está longe demais.", 10f, Palette.MUTED, 230f, 3) }
                RouteOutcome.NOT_DOCKABLE -> Ui.paragraph(d, x + 14f, y, "Este alvo não é atracável: pilote à mão ou use o piloto de interceptação.", 10f, Palette.MUTED, 230f, 4)
            }
            ui.add("route_sel:$i", x, 74f, 260f, 190f, "", hidden = true)
        }
        Ui.paragraph(d, 16f, 290f, "As órbitas se movem durante a viagem: o piloto automático mira onde o destino estará. Mais empuxo chega antes, mas gasta mais combustível.", 10f, Palette.MUTED, 560f, 3)
        val opt = opts.getOrNull(c.routeSelected)
        ui.add("route_go", 600f, 300f, 228f, 50f, "CONFIRMAR E PARTIR", active = true, enabled = opt != null && opt.outcome == RouteOutcome.DOCKS)
        ui.add("back", 16f, 330f, 120f, 40f, "CANCELAR")
        return ui.list
    }
}

object DialogueScreen {
    fun draw(d: Draw, g: GameSession): List<Button> {
        background(d)
        val ui = Buttons(d)
        val dlg = g.dialogue ?: return ui.list
        val ch = dlg.characterId?.let { g.pack.character(it) }
        val faction = ch?.factionId?.let { g.pack.faction(it) }
        val accent = faction?.argb ?: Palette.MUTED

        d.rect(0f, 0f, 230f, Layout.H, fill = 0xFF0B1018.toInt())
        d.glow(115f, 150f, 150f, accent.alpha(0.18f))
        d.poly(floatArrayOf(34f, 330f, 52f, 250f, 115f, 228f, 178f, 250f, 196f, 330f), fill = accent.alpha(0.35f), stroke = accent.alpha(0.6f))
        d.circle(115f, 168f, 46f, fill = 0xFF5A4436.toInt(), stroke = accent.alpha(0.7f), strokeW = 1.5f)
        d.poly(floatArrayOf(66f, 160f, 80f, 120f, 150f, 120f, 164f, 160f, 150f, 138f, 80f, 138f), fill = 0xFF1A1412.toInt())
        d.rect(0f, 330f, 230f, 60f, fill = Palette.PANEL)
        d.text(14f, 350f, ch?.name ?: "Rádio", Palette.INK, 15f, bold = true)
        d.text(14f, 365f, Ui.clip(ch?.role ?: "", 9f, 206f), Palette.MUTED, 9f)
        if (faction != null) d.text(14f, 379f, faction.name, accent, 9f)

        d.text(250f, 26f, (if (ch != null) ch.name.uppercase() else "RÁDIO") + " · SIMULAÇÃO PAUSADA", Palette.AMBER, 10f, bold = true)
        val size = if (dlg.text.length > 260) 11f else 12.5f
        var y = Ui.paragraph(d, 250f, 50f, dlg.text, size, Palette.INK, 570f, 7, size * 1.38f)
        y = y.coerceAtLeast(120f) + 6f

        val choices = g.choices()
        val avail = 380f - y
        val gap = 6f
        val each = ((avail - gap * (choices.size - 1)) / choices.size.coerceAtLeast(1)).coerceAtMost(54f)
        for ((i, cv) in choices.withIndex()) {
            val by = y + i * (each + gap)
            val label = if (cv.index >= 0) "${i + 1}. ${cv.text}" else cv.text
            ui.add("choice:${cv.index}", 250f, by, 574f, each, label, enabled = cv.enabled, sub = if (cv.enabled) cv.detail else cv.blocked, left = true, accent = if (cv.enabled && cv.index >= 0 && i == 0) 0 else 0)
        }
        return ui.list
    }
}

object StationScreen {
    fun draw(d: Draw, c: AppController, g: GameSession): List<Button> {
        background(d)
        val ui = Buttons(d)
        val b = g.sim.dockedAt ?: return ui.list
        d.rect(0f, 0f, 210f, Layout.H, fill = Palette.PANEL)
        d.line(210f, 0f, 210f, Layout.H, Palette.PANEL_EDGE, 1.5f)
        d.text(14f, 28f, Ui.clip(b.name, 13f, 190f), Palette.INK, 13f, bold = true)
        d.text(14f, 44f, b.faction.name, b.faction.argb, 10f)
        d.text(14f, 58f, "T+ " + Fmt.clock(g.sim.time) + " · atracado", Palette.MUTED, 9f)
        ui.add("open_notebook", 12f, 76f, 186f, 36f, "CADERNO · ${g.story.facts.size} " + (if (g.story.facts.size == 1) "pista" else "pistas"))
        ui.add("open_hold", 12f, 116f, 186f, 36f, "PORÃO E NAVE")
        ui.add("open_news", 12f, 156f, 186f, 36f, "NOTÍCIAS E FACÇÕES")
        ui.add("open_map", 12f, 196f, 186f, 36f, "MAPA")
        ui.add("open_route", 12f, 236f, 186f, 36f, "PLANEJAR ROTA")
        ui.add("menu", 12f, 276f, 186f, 36f, "MENU")
        ui.add("undock", 12f, 330f, 186f, 48f, "DESATRACAR", active = true)

        d.text(230f, 28f, "Estação", Palette.AMBER, 16f, bold = true)
        d.text(230f, 44f, "Combustível reabastecido · Δv " + Fmt.speed(g.sim.ship.deltaV), Palette.MUTED, 10f)
        val people = g.charactersAt(b)
        var y = 62f
        d.text(230f, y + 8f, "PESSOAS AQUI", Palette.MUTED, 9f)
        y += 16f
        if (people.isEmpty()) {
            d.text(230f, y + 14f, "Ninguém com quem falar por enquanto.", Palette.MUTED, 11f)
            y += 28f
        }
        for (p in people.take(3)) {
            Ui.panel(d, 230f, y, 594f, 56f)
            val col = p.factionId?.let { g.pack.faction(it)?.argb } ?: Palette.MUTED
            d.circle(256f, y + 28f, 17f, fill = col.alpha(0.3f), stroke = col)
            d.text(256f, y + 33f, p.name.take(1), Palette.INK, 14f, Align.CENTER, bold = true)
            d.text(284f, y + 24f, p.name, Palette.INK, 13f, bold = true)
            d.text(284f, y + 40f, p.role, Palette.MUTED, 10f)
            ui.add("talk:${p.id}", 720f, y + 10f, 94f, 36f, "FALAR", active = true)
            y += 62f
        }
        g.activeMission?.let {
            Ui.panel(d, 230f, y + 4f, 594f, 62f, 0xFF1A1608.toInt(), Palette.AMBER.alpha(0.5f))
            d.text(244f, y + 24f, "MISSÃO · " + it.title, Palette.AMBER, 10f, bold = true)
            Ui.paragraph(d, 244f, y + 40f, it.text, 10f, Palette.INK, 566f, 2, 12f)
        }
        return ui.list
    }
}

object NotebookScreen {
    fun draw(d: Draw, c: AppController, g: GameSession): List<Button> {
        background(d)
        val ui = Buttons(d)
        val facts = g.story.facts.toList()
        Ui.title(d, "CADERNO DE INVESTIGAÇÃO", "${facts.size} de ${g.pack.facts.size} pistas · ${g.story.deduced.size} de ${g.pack.deductions.size} deduções")
        ui.back()
        val perPage = 5
        val pages = ((facts.size + perPage - 1) / perPage).coerceAtLeast(1)
        c.factPage = c.factPage.coerceIn(0, pages - 1)
        d.text(16f, 66f, "PISTAS (toque para selecionar até 3)", Palette.MUTED, 9f)
        if (facts.isEmpty()) d.text(16f, 92f, "Nenhuma pista ainda. Converse e visite os lugares.", Palette.MUTED, 10f)
        for ((i, id) in facts.drop(c.factPage * perPage).take(perPage).withIndex()) {
            val sel = id in c.selectedFacts
            ui.add("fact:$id", 16f, 72f + i * 56f, 300f, 52f, "", active = sel, hidden = true)
            d.rect(16f, 72f + i * 56f, 300f, 52f, fill = if (sel) 0xFF0F1E2A.toInt() else Palette.BUTTON, stroke = if (sel) 0xFF5CC8E8.toInt() else Palette.BUTTON_EDGE, radius = 6f)
            Ui.paragraph(d, 26f, 90f + i * 56f, g.pack.fact(id)?.text ?: id, 10f, Palette.INK, 282f, 3, 12f)
        }
        if (pages > 1) {
            ui.add("facts_up", 322f, 72f, 30f, 40f, "▲", enabled = c.factPage > 0)
            ui.add("facts_down", 322f, 116f, 30f, 40f, "▼", enabled = c.factPage < pages - 1)
        }

        d.text(364f, 66f, "DEDUZIR", Palette.MUTED, 9f)
        Ui.panel(d, 364f, 72f, 220f, 150f)
        var y = 90f
        if (c.selectedFacts.isEmpty()) y = Ui.paragraph(d, 376f, y, "Selecione pistas ao lado e combine-as.", 10f, Palette.MUTED, 196f, 3, 12f)
        for ((i, id) in c.selectedFacts.withIndex()) {
            if (i > 0) d.text(376f, y + 2f, "+", 0xFF5CC8E8.toInt(), 11f, bold = true).also { y += 12f }
            y = Ui.paragraph(d, 376f, y, g.pack.fact(id)?.text ?: id, 9f, 0xFF9ADCF0.toInt(), 196f, 2, 11f) + 2f
        }
        c.deduceText?.let { Ui.paragraph(d, 376f, 196f, it, 9f, Palette.AMBER_SOFT, 196f, 3, 11f) }
        ui.add("deduce", 364f, 230f, 220f, 40f, "DEDUZIR", active = c.selectedFacts.size >= 2, enabled = c.selectedFacts.size >= 2)
        d.text(364f, 290f, "DEDUÇÕES FEITAS", Palette.MUTED, 9f)
        var yy = 304f
        for (id in g.story.deduced.toList().takeLast(3)) {
            yy = Ui.paragraph(d, 364f, yy, "✓ " + (g.pack.deductions.firstOrNull { it.id == id }?.text ?: id), 9f, Palette.GREEN, 220f, 2, 11f) + 2f
        }

        if (g.pack.suspects.isNotEmpty()) {
            d.text(600f, 66f, "SUSPEITOS", Palette.MUTED, 9f)
            for ((i, s) in g.pack.suspects.take(4).withIndex()) {
                val out = s.id in g.story.refuted
                ui.add("suspect:${s.id}", 600f, 72f + i * 56f, 228f, 52f, if (out) "✗ " + s.name else s.name, active = !out && !g.ended && c.selectedSuspect == s.id,
                    sub = if (out) "descartado (acusação errada)" else s.text, left = true, enabled = !out && !g.ended)
            }
            val left = g.story.accusationsLeft
            if (g.ended) {
                // o caso já foi decidido por uma acusação (ou por outro final): sem isto o toque em ACUSAR parecia não fazer nada
                ui.add("accuse", 600f, 304f, 228f, 46f, "CASO ENCERRADO", enabled = false)
                d.text(600f, 362f, Ui.clip(g.ending?.title ?: "Fim", 9f, 228f), Palette.AMBER_SOFT, 9f)
                d.text(600f, 374f, Ui.clip("Reinicie pelo MENU para acusar de novo.", 9f, 228f), Palette.MUTED, 9f)
            } else {
                ui.add("accuse", 600f, 304f, 228f, 46f, "ACUSAR", active = c.selectedSuspect != null, enabled = c.selectedSuspect != null)
                val line1 = if (left == 1) "Última acusação: encerra o caso" else "Acusações restantes: $left de ${g.pack.accusationAttempts}"
                d.text(600f, 362f, Ui.clip(line1, 9f, 228f), if (left == 1) Palette.RED_SOFT else Palette.AMBER_SOFT, 9f)
                d.text(600f, 374f, Ui.clip("Errar gasta uma; certo encerra.", 9f, 228f), Palette.MUTED, 9f)
            }
        }
        return ui.list
    }
}

object HoldScreen {
    fun draw(d: Draw, g: GameSession): List<Button> {
        background(d)
        val ui = Buttons(d)
        val s = g.sim.ship
        Ui.title(d, "PORÃO E NAVE", g.pack.ship.name)
        ui.back()
        Ui.panel(d, 16f, 60f, 290f, 300f)
        val rows = listOf(
            "massa total" to Fmt.dec(s.mass / 1000, 1) + " t",
            "massa seca" to Fmt.dec(s.dryMass / 1000, 1) + " t",
            "carga do contrato" to Fmt.dec(s.cargo / 1000, 1) + " t",
            "objetos" to Fmt.dec(s.extra, 1) + " kg",
            "combustível" to "${Fmt.int(s.fuel)} kg",
            "Δv restante" to Fmt.speed(s.deltaV),
            "créditos" to "${Fmt.int(g.story.credits.toDouble())} ¢",
        )
        var y = 84f
        for ((k, v) in rows) { d.text(30f, y, k, Palette.MUTED, 10f); d.text(292f, y, v, Palette.INK, 12f, Align.RIGHT, bold = true); y += 22f }
        val frac = (s.fuel / s.fuelCapacity).toFloat().coerceIn(0f, 1f)
        d.rect(30f, y + 4f, 262f, 8f, fill = Palette.BUTTON, radius = 4f)
        d.rect(30f, y + 4f, 262f * frac, 8f, fill = Palette.AMBER, radius = 4f)
        Ui.paragraph(d, 30f, y + 32f, "Cada tonelada a mais reduz a aceleração e o Δv disponível.", 9f, Palette.MUTED, 262f, 3, 11f)

        d.text(322f, 72f, "ITENS", Palette.MUTED, 9f)
        var iy = 78f
        if (s.cargo > 0) { Ui.panel(d, 322f, iy, 506f, 34f); d.text(334f, iy + 21f, g.pack.ship.cargoName, Palette.AMBER_SOFT, 11f); d.text(816f, iy + 21f, Fmt.dec(s.cargo / 1000, 1) + " t", Palette.MUTED, 10f, Align.RIGHT); iy += 40f }
        for (id in g.story.objects) {
            val o = g.pack.obj(id) ?: continue
            Ui.panel(d, 322f, iy, 506f, 34f, 0xFF0F1E2A.toInt(), 0xFF5CC8E8.toInt().alpha(0.5f))
            d.text(334f, iy + 21f, o.name, Palette.INK, 11f)
            d.text(816f, iy + 21f, Fmt.dec(o.mass, 1) + " kg · evidência", Palette.MUTED, 10f, Align.RIGHT)
            iy += 40f
            if (iy > 300f) break
        }
        if (iy == 78f) d.text(322f, iy + 16f, "Porão vazio.", Palette.MUTED, 11f)
        val m = g.pack.missions.filter { g.story.missions[it.id] != null }
        var my = 318f
        for (mm in m.take(2)) {
            val st = g.story.missions[mm.id] ?: ""
            val col = when (st) { "concluida" -> Palette.GREEN; "falhou" -> Palette.RED_SOFT; else -> Palette.AMBER }
            d.text(322f, my, (if (st == "ativa") "◆ " else if (st == "concluida") "✓ " else "✕ ") + Ui.clip(mm.title, 11f, 480f), col, 11f)
            my += 16f
        }
        return ui.list
    }
}

object NewsScreen {
    fun draw(d: Draw, c: AppController, g: GameSession): List<Button> {
        background(d)
        val ui = Buttons(d)
        Ui.title(d, "FACÇÕES E NOTÍCIAS")
        ui.back()
        d.text(16f, 64f, "REPUTAÇÃO", Palette.MUTED, 9f)
        var y = 70f
        for (f in g.pack.factions.take(5)) {
            val v = g.story.reputation[f.id] ?: 0
            Ui.panel(d, 16f, y, 360f, 52f)
            d.text(28f, y + 20f, f.name, f.argb, 12f, bold = true)
            d.text(364f, y + 20f, (if (v > 0) "+" else "") + v, Palette.INK, 12f, Align.RIGHT, bold = true)
            d.rect(28f, y + 30f, 336f, 8f, fill = Palette.BUTTON, radius = 4f)
            d.line(196f, y + 26f, 196f, y + 42f, Palette.MUTED, 1f)
            val w = 168f * abs(v) / 100f
            d.rect(if (v >= 0) 196f else 196f - w, y + 30f, w, 8f, fill = f.argb, radius = 4f)
            val delta = g.repDelta(f.id)
            if (delta != 0) d.text(28f, y + 48f, "desde o início: ${if (delta > 0) "+" else ""}$delta", Palette.MUTED, 8f)
            y += 58f
        }
        d.text(392f, 64f, "NOTÍCIAS DO SISTEMA", Palette.MUTED, 9f)
        val news = g.story.news
        val perPage = 5
        val pages = ((news.size + perPage - 1) / perPage).coerceAtLeast(1)
        c.newsPage = c.newsPage.coerceIn(0, pages - 1)
        var ny = 70f
        for ((i, n) in news.drop(c.newsPage * perPage).take(perPage).withIndex()) {
            val mine = c.newsPage == 0 && i == 0 && g.ended
            Ui.panel(d, 392f, ny, 380f, 54f, if (mine) 0xFF1A1608.toInt() else Palette.PANEL, if (mine) Palette.AMBER.alpha(0.6f) else Palette.PANEL_EDGE)
            Ui.paragraph(d, 404f, ny + 20f, n, 11f, Palette.INK, 358f, 3, 13f)
            ny += 60f
        }
        if (pages > 1) {
            ui.add("news_up", 780f, 70f, 48f, 40f, "▲", enabled = c.newsPage > 0)
            ui.add("news_down", 780f, 116f, 48f, 40f, "▼", enabled = c.newsPage < pages - 1)
        }
        return ui.list
    }
}

object MenuScreen {
    fun draw(d: Draw): List<Button> {
        background(d)
        val ui = Buttons(d)
        Ui.title(d, "MENU")
        val items = listOf(
            "open_map" to "MAPA", "open_route" to "PLANO DE ROTA", "open_notebook" to "CADERNO", "open_hold" to "PORÃO E NAVE", "open_news" to "NOTÍCIAS",
            "save_new" to "SALVAR JOGO", "open_saves" to "SAVES", "open_home" to "CAMPANHAS", "restart" to "REINICIAR", "back" to "VOLTAR AO VOO",
        )
        for ((i, it) in items.withIndex()) {
            val x = 16f + (i % 5) * 164f
            val y = 70f + (i / 5) * 130f
            ui.add(it.first, x, y, 156f, 118f, it.second, active = it.first == "back" || it.first == "save_new")
        }
        return ui.list
    }
}

/** Salvamentos de uma campanha: carregar, renomear e apagar (com confirmação). */
object SavesScreen {
    fun draw(d: Draw, c: AppController): List<Button> {
        background(d)
        val ui = Buttons(d)
        val pid = c.savesPack ?: return ui.list
        val title = c.entries.firstOrNull { it.pack.id == pid }?.pack?.title ?: pid
        Ui.title(d, "SALVAMENTOS", title)
        ui.back()
        val list = c.saveList(pid)
        val perPage = 4
        val pages = ((list.size + perPage - 1) / perPage).coerceAtLeast(1)
        c.savesPage = c.savesPage.coerceIn(0, pages - 1)
        if (list.isEmpty()) d.text(16f, 90f, "Nenhum salvamento ainda. Use SALVAR JOGO no menu durante o voo.", Palette.MUTED, 11f)
        for ((i, s) in list.drop(c.savesPage * perPage).take(perPage).withIndex()) {
            val y = 62f + i * 78f
            Ui.panel(d, 16f, y, 726f, 70f)
            d.text(30f, y + 24f, Ui.clip(s.name, 14f, 330f), Palette.INK, 14f, bold = true)
            if (s.isAuto) Ui.pill(d, 360f, y + 12f, "automático", Palette.AMBER)
            d.text(30f, y + 42f, Ui.clip(s.summary, 10f, 520f), Palette.MUTED, 10f)
            d.text(30f, y + 58f, Fmt.dateTime(s.whenMs), Palette.MUTED, 9f)
            ui.add("save_load:${s.slot}", 520f, y + 8f, 100f, 54f, "CARREGAR", active = true)
            ui.add("save_rename:${s.slot}", 626f, y + 8f, 54f, 24f, "NOME")
            ui.add("save_del:${s.slot}", 626f, y + 38f, 54f, 24f, if (c.pendingDelete == s.slot) "SIM?" else "✕", accent = if (c.pendingDelete == s.slot) Palette.RED else 0)
        }
        if (pages > 1) {
            ui.add("saves_up", 752f, 62f, 76f, 36f, "▲", enabled = c.savesPage > 0)
            ui.add("saves_down", 752f, 104f, 76f, 36f, "▼", enabled = c.savesPage < pages - 1)
        }
        d.text(16f, 380f, "${list.size} salvamento(s) · toque em ✕ duas vezes para apagar", Palette.MUTED, 9f)
        return ui.list
    }
}

object EndScreen {
    fun draw(d: Draw, g: GameSession): List<Button> {
        background(d)
        val ui = Buttons(d)
        val e = g.ending
        val win = e?.kind == EndingKind.WIN
        val color = when (e?.kind) { EndingKind.WIN -> Palette.GREEN; EndingKind.NEUTRAL -> Palette.AMBER; else -> Palette.RED_SOFT }
        Ui.panel(d, 90f, 20f, 664f, 350f, Palette.PANEL, color.alpha(0.8f))
        d.text(112f, 56f, e?.title ?: "FIM", color, 20f, bold = true)
        Ui.paragraph(d, 112f, 80f, e?.headline ?: "", 12f, Palette.INK, 620f, 3, 16f)
        d.text(112f, 148f, "Tempo de missão: " + Fmt.clock(g.sim.time), Palette.INK, 11f)
        d.text(112f, 164f, "Créditos: ${Fmt.int(g.story.credits.toDouble())} ¢", Palette.INK, 11f)
        d.text(112f, 180f, "Combustível restante: ${Fmt.int(g.sim.ship.fuel / g.sim.ship.fuelCapacity * 100)}% (${Fmt.int(g.sim.ship.fuel)} kg)", Palette.INK, 11f)
        d.text(112f, 206f, "Reputação final", Palette.MUTED, 10f, bold = true)
        var y = 222f
        for (f in g.pack.factions.take(5)) {
            val v = g.story.reputation[f.id] ?: 0
            val delta = g.repDelta(f.id)
            d.text(112f, y, f.name, f.argb, 11f)
            d.text(330f, y, (if (v > 0) "+" else "") + v, Palette.INK, 11f, bold = true)
            d.text(370f, y, "(" + (if (delta > 0) "+" else "") + delta + ")", if (delta > 0) Palette.GREEN else if (delta < 0) Palette.RED_SOFT else Palette.MUTED, 11f)
            y += 16f
        }
        val crashed = g.sim.status == Status.CRASHED
        ui.add("restart", 112f, 316f, 190f, 40f, "JOGAR DE NOVO", active = true)
        ui.add("continue", 312f, 316f, 190f, 40f, "CONTINUAR VOANDO", enabled = !crashed)
        ui.add("open_home", 512f, 316f, 220f, 40f, "CAMPANHAS")
        return ui.list
    }
}
