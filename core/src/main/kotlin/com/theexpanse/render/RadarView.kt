package com.theexpanse.render

import com.theexpanse.content.BodyKind
import com.theexpanse.core.AutopilotMode
import com.theexpanse.core.Body
import com.theexpanse.core.Config
import com.theexpanse.core.FireResult
import com.theexpanse.core.Owner
import com.theexpanse.core.Simulation
import com.theexpanse.core.Status
import com.theexpanse.core.Vec2
import com.theexpanse.game.GameSession
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/** Estado do radar tático que vive entre quadros: zoom, arrasto do vetor e o que foi desenhado (para o toque). */
class RadarState {
    var zoom = 2
    var dragging = false
    var moved = false
    var downX = 0f
    var downY = 0f

    /** Geometria do último quadro: pixels por metro e a posição da alça da velocidade desejada. */
    var k = 1.0
    var handleOn = false
    var handleX = 0f
    var handleY = 0f

    class Hit(val id: String, val x: Float, val y: Float)
    val hits = ArrayList<Hit>()
}

/**
 * Radar tático: vista de cima, norte para cima, com a nave no centro. Mostra naves, mísseis (com o tempo até o impacto),
 * despistadores e explosões, e deixa pilotar pelo vetor de velocidade: a seta mostra onde a nave estará daqui a
 * [HORIZON] segundos em relação ao alvo, e arrastar a nave define a velocidade desejada (o piloto automático gira e queima).
 * Só no papel militar.
 */
object RadarView {
    const val AREA_W = 632f
    const val CX = 316f
    const val CY = 200f

    /** Raio do zoom na tela (px) e comprimento máximo da seta de velocidade. */
    private const val R_PX = 178f
    private const val VEC_PX_MAX = 150f

    /** A seta de velocidade é o deslocamento que a nave faria neste tempo (s). */
    const val HORIZON = 100.0
    val ZOOMS_KM = doubleArrayOf(20.0, 50.0, 100.0, 200.0)
    private val RINGS = arrayOf(intArrayOf(5, 10, 15, 20), intArrayOf(10, 25, 50), intArrayOf(25, 50, 100), intArrayOf(50, 100, 150, 200))

    private const val PX = 640f
    private const val PW = 196f
    private val GRID = 0xFF22324A.toInt()
    private val SHIP_COLOR = Palette.PLUME_HOT

    fun scale(r: RadarState): Double = R_PX / (ZOOMS_KM[r.zoom] * 1000.0)

    private fun isHostile(sim: Simulation, b: Body) = b.kind == BodyKind.SHIP && b.faction.id != sim.world.role.factionId

    private fun sx(x: Double, k: Double) = (CX + x * k).toFloat()
    private fun sy(y: Double, k: Double) = (CY - y * k).toFloat()

    /** Ponto no contorno da área do radar na direção de (px, py), mais o vetor unitário até lá. */
    private fun edgePoint(px: Float, py: Float): FloatArray {
        val dx = px - CX
        val dy = py - CY
        val l = hypot(dx, dy).coerceAtLeast(1f)
        val ux = dx / l
        val uy = dy / l
        val sx = if (abs(ux) < 1e-4f) 1e9f else ((if (ux > 0) AREA_W - 16f else 16f) - CX) / ux
        val sy = if (abs(uy) < 1e-4f) 1e9f else ((if (uy > 0) Layout.H - 16f else 16f) - CY) / uy
        val s = min(sx, sy)
        return floatArrayOf(CX + ux * s, CY + uy * s, ux, uy)
    }

    private fun arrow(d: Draw, x1: Float, y1: Float, x2: Float, y2: Float, color: Int, w: Float, dash: Float = 0f) {
        d.line(x1, y1, x2, y2, color, w, dash)
        val l = hypot(x2 - x1, y2 - y1)
        if (l < 6f) return
        val ux = (x2 - x1) / l
        val uy = (y2 - y1) / l
        d.poly(floatArrayOf(x2, y2, x2 - ux * 9 + uy * 4.5f, y2 - uy * 9 - ux * 4.5f, x2 - ux * 9 - uy * 4.5f, y2 - uy * 9 + ux * 4.5f), fill = color)
    }

    private fun ring(d: Draw, rPx: Float, color: Int, dashed: Boolean) {
        if (rPx < 4f || rPx > 2500f) return
        if (!dashed) { d.circle(CX, CY, rPx, stroke = color); return }
        val n = (rPx * 0.35f).toInt().coerceIn(48, 240) / 2 * 2
        for (i in 0 until n step 2) {
            val a1 = 2 * PI * i / n
            val a2 = 2 * PI * (i + 1) / n
            val x1 = CX + rPx * cos(a1).toFloat(); val y1 = CY - rPx * sin(a1).toFloat()
            val x2 = CX + rPx * cos(a2).toFloat(); val y2 = CY - rPx * sin(a2).toFloat()
            if ((x1 < -20f && x2 < -20f) || (x1 > AREA_W + 20f && x2 > AREA_W + 20f) || (y1 < -20f && y2 < -20f) || (y1 > Layout.H + 20f && y2 > Layout.H + 20f)) continue
            d.line(x1, y1, x2, y2, color, 1.2f)
        }
    }

    private fun bracket(d: Draw, x: Float, y: Float, s: Float, color: Int, w: Float = 1.6f) {
        val a = s * 0.45f
        d.line(x - s, y - s, x - s + a, y - s, color, w); d.line(x - s, y - s, x - s, y - s + a, color, w)
        d.line(x + s, y - s, x + s - a, y - s, color, w); d.line(x + s, y - s, x + s, y - s + a, color, w)
        d.line(x - s, y + s, x - s + a, y + s, color, w); d.line(x - s, y + s, x - s, y + s - a, color, w)
        d.line(x + s, y + s, x + s - a, y + s, color, w); d.line(x + s, y + s, x + s, y + s - a, color, w)
    }

    fun draw(d: Draw, game: GameSession, r: RadarState, t: Double, held: Set<String>): List<Button> {
        val sim = game.sim
        val ship = sim.ship
        val k = scale(r)
        r.k = k
        r.hits.clear()
        r.handleOn = false
        d.rect(0f, 0f, Layout.W, Layout.H, fill = Palette.BG)
        d.clip(0f, 0f, AREA_W, Layout.H)

        // --- grade e anéis ---
        d.line(0f, CY, AREA_W, CY, GRID.alpha(0.7f))
        d.line(CX, 0f, CX, Layout.H, GRID.alpha(0.7f))
        for (km in RINGS[r.zoom]) {
            val rr = (km * 1000.0 * k).toFloat()
            d.circle(CX, CY, rr, stroke = GRID)
            d.text(CX + rr * 0.7071f + 4f, CY + rr * 0.7071f + 11f, "$km km", Palette.MUTED.alpha(0.8f), 9f)
        }
        val fireR = (Config.ENEMY_FIRE_RANGE * k).toFloat()
        val shotR = (Config.MISSILE_RANGE * k).toFloat()
        ring(d, fireR, Palette.RED.alpha(0.75f), true)
        ring(d, shotR, Palette.GREEN.alpha(0.75f), true)
        fun ringLabel(rr: Float, text: String, color: Int, ang: Double) {
            val x = CX + rr * cos(ang).toFloat()
            val y = CY - rr * sin(ang).toFloat()
            if (x in 6f..AREA_W - 90f && y in 12f..Layout.H - 6f) d.text(x + 4f, y, text, color.alpha(0.9f), 9f)
        }
        ringLabel(fireR, "alcance deles", Palette.RED, 1.2)
        ringLabel(shotR, "seu alcance", Palette.GREEN, 0.55)

        // --- corpos ---
        val tgt = sim.target
        val canFire = sim.canFire()
        for (b in sim.bodies) {
            if (!sim.alive(b) || b.kind == BodyKind.PLANET) continue
            val rel = sim.relativePosition(b)
            val px = sx(rel.x, k)
            val py = sy(rel.y, k)
            val hostile = isHostile(sim, b)
            val color = if (b.kind == BodyKind.SHIP) (if (hostile) Palette.RED else Palette.GREEN) else b.argb
            val inside = px in 10f..AREA_W - 10f && py in 10f..Layout.H - 10f
            val isTarget = b === tgt
            if (inside) {
                if (b.kind == BodyKind.SHIP) d.poly(floatArrayOf(px, py - 6.5f, px + 5.5f, py, px, py + 6.5f, px - 5.5f, py), fill = color.alpha(if (hostile) 1f else 0.8f))
                else d.rect(px - 4f, py - 4f, 8f, 8f, fill = color.alpha(0.35f), stroke = color)
                if (b.kind == BodyKind.SHIP || isTarget) {
                    d.text(px + 12f, py - 4f, b.shortName.uppercase(), Palette.INK, 10f, bold = true)
                    d.text(px + 12f, py + 8f, Fmt.distance(rel.length), Palette.MUTED, 9f)
                }
                r.hits.add(RadarState.Hit(b.id, px, py))
            } else if (hostile) {
                val e = edgePoint(px, py)
                val ux = e[2]; val uy = e[3]
                d.poly(floatArrayOf(e[0] + ux * 8f, e[1] + uy * 8f, e[0] - ux * 4f - uy * 5.5f, e[1] - uy * 4f + ux * 5.5f, e[0] - ux * 4f + uy * 5.5f, e[1] - uy * 4f - ux * 5.5f), fill = color)
                val label = b.shortName.uppercase() + " " + Fmt.distance(rel.length)
                if (e[0] > CX) d.text(e[0] - 12f, e[1] + 3f, label, color, 9f, Align.RIGHT, bold = true)
                else d.text(e[0] + 12f, e[1] + 3f, label, color, 9f, bold = true)
                r.hits.add(RadarState.Hit(b.id, e[0], e[1]))
            }
            if (isTarget && inside) {
                val ready = canFire == FireResult.FIRED
                bracket(d, px, py, 13f, if (ready) Palette.RED_SOFT else Palette.AMBER)
                d.text(px, py + 26f, if (ready) "TRAVADO" else "ALVO", if (ready) Palette.RED_SOFT else Palette.AMBER, 9f, Align.CENTER, bold = true)
            }
        }

        // --- vetor de velocidade relativa ao alvo ---
        val engaged = sim.autopilot == AutopilotMode.VELOCITY
        val ref = (if (engaged) sim.velocityRef else null) ?: tgt
        val vRel = ship.vel - sim.velocity(ref)
        val vk = HORIZON * k
        run {
            var ex = (CX + vRel.x * vk).toFloat()
            var ey = (CY - vRel.y * vk).toFloat()
            val len = hypot(ex - CX, ey - CY)
            val maxLen = 172f
            val over = len > maxLen
            if (over) { ex = CX + (ex - CX) * maxLen / len; ey = CY + (ey - CY) * maxLen / len }
            if (len > 3f) {
                arrow(d, CX, CY, ex, ey, Palette.GREEN, 2.2f)
                val right = ex >= CX
                val label = "v " + Fmt.speed(vRel.length) + (if (over) " »" else "") + " · " + ref.shortName
                d.text(if (right) ex + 8f else ex - 8f, ey - 5f, Ui.clip(label, 9f, 150f), Palette.GREEN, 9f, if (right) Align.LEFT else Align.RIGHT)
            }
        }
        val want = sim.desiredRelVel
        if (engaged && want != null) {
            val hx = (CX + want.x * vk).toFloat()
            val hy = (CY - want.y * vk).toFloat()
            d.line(CX, CY, hx, hy, Palette.AMBER, 1.6f, 5f)
            d.circle(hx, hy, 9f, fill = Palette.AMBER.alpha(0.22f), stroke = Palette.AMBER, strokeW = 2f)
            d.text(hx + 13f, hy + 12f, "meta " + Fmt.speed(want.length), Palette.AMBER, 9f, bold = true)
            r.handleOn = true; r.handleX = hx; r.handleY = hy
        }

        // --- despistadores, mísseis, explosões ---
        for (f in sim.flares) {
            val rel = f.pos - ship.pos
            val px = sx(rel.x, k); val py = sy(rel.y, k)
            val rv = f.vel - ship.vel
            d.line(px, py, sx(rel.x - rv.x * 4.0, k), sy(rel.y - rv.y * 4.0, k), 0xFFFFE9B0.toInt().alpha(0.5f), 1.2f)
            val tw = 0.7f + 0.3f * sin(t * 25 + f.born).toFloat()
            d.glow(px, py, 14f, 0xFFFFE9B0.toInt().alpha(0.55f * tw))
            d.circle(px, py, 2.4f, fill = Palette.PLUME_CORE.alpha(tw))
        }
        for (m in sim.missiles) {
            val rel = m.pos - ship.pos
            val px = sx(rel.x, k); val py = sy(rel.y, k)
            val toMe = m.owner == Owner.ENEMY && m.targetId == "jogador"
            val color = when {
                m.owner == Owner.PLAYER -> Palette.GREEN
                toMe -> Palette.RED
                else -> Palette.RED_SOFT.alpha(0.65f)
            }
            sim.missileTarget(m)?.let { (tp, _) ->
                val trel = tp - ship.pos
                d.line(px, py, sx(trel.x, k), sy(trel.y, k), color.alpha(0.45f), 1f, 4f)
            }
            val v = m.vel - ship.vel
            val vl = v.length
            if (vl > 1.0) d.line(px, py, px - (v.x / vl).toFloat() * 16f, py + (v.y / vl).toFloat() * 16f, color, 2f)
            d.circle(px, py, 3.2f, fill = color)
            val eta = sim.missileEta(m)
            val tag = (if (m.owner == Owner.PLAYER) "S" else "A") + m.id
            if (eta != null && px in 0f..AREA_W && py in 0f..Layout.H) d.text(px + 7f, py - 7f, tag + " " + Fmt.duration(eta), color, 9f, bold = true)
        }
        for (b in sim.blasts) {
            val rel = b.pos - ship.pos
            Fx.blast(d, sx(rel.x, k), sy(rel.y, k), (sim.time - b.time).toFloat(), b.kind, 0.7f)
        }

        // --- a nave ---
        val body = placePoints(floatArrayOf(11f, 0f, -7f, 6.5f, -3.5f, 0f, -7f, -6.5f), CX, CY, ship.angle)
        if (ship.burnAccel > 0.01) d.glow(CX - cos(ship.angle).toFloat() * 9f, CY + sin(ship.angle).toFloat() * 9f, 11f, Palette.PLUME_MID.alpha(0.8f))
        d.poly(body, fill = SHIP_COLOR, stroke = Palette.PLUME_CORE.alpha(0.7f))
        d.text(CX + 12f, CY + 17f, "VOCÊ", SHIP_COLOR, 9f, bold = true)

        Effects.hitFlash(d, sim, AREA_W, Layout.H)
        d.unclip()

        // --- textos sobre o radar ---
        d.text(12f, 16f, "RADAR TÁTICO · ${ZOOMS_KM[r.zoom].toInt()} km", Palette.MUTED, 10f)
        val eta = sim.impactEta()
        if (eta != null) {
            val pulse = 0.55f + 0.45f * sin(t * 10).toFloat()
            d.rect(CX - 150f, 8f, 300f, 26f, fill = Palette.RED.alpha(0.18f + 0.15f * pulse), stroke = Palette.RED, radius = 3f)
            d.text(CX, 26f, "ALERTA DE MÍSSIL · IMPACTO EM " + Fmt.duration(eta), Palette.RED_SOFT, 12f, Align.CENTER, bold = true)
        }
        var my = Layout.H - 38f
        for (m in game.messages.asReversed()) {
            val age = game.clock - m.at
            if (age > 9.0) break
            val fade = if (age > 6.0) ((9.0 - age) / 3.0).toFloat() else 1f
            d.text(12f, my, Ui.clip(m.text, 10f, 600f), Palette.AMBER_SOFT.alpha(fade), 10f)
            my -= 12f
            if (my < Layout.H - 38f - 12f * 3) break
        }
        val hull = (ship.hull / ship.hullMax * 100).toInt()
        d.text(12f, Layout.H - 22f, "CASCO $hull% · MÍSSEIS ${ship.missiles} · DESPIST. ${ship.decoys} · PILOTO ${sim.autopilot.label}",
            if (hull > 60) Palette.INK else if (hull > 30) Palette.AMBER else Palette.RED_SOFT, 10f)
        d.text(12f, Layout.H - 9f, "Arraste a nave: a seta mostra onde você estará em ${HORIZON.toInt()} s em relação ao alvo.", Palette.MUTED.alpha(0.75f), 9f)

        val buttons = ArrayList<Button>(panel(d, sim, r, t, ref))
        buttons.addAll(Hud.drawStranded(d, game))
        buttons.addAll(Hud.drawCrash(d, sim))
        return buttons
    }

    private fun panel(d: Draw, sim: Simulation, r: RadarState, t: Double, ref: Body): List<Button> {
        val ui = Buttons(d)
        val ship = sim.ship
        d.rect(AREA_W + 2f, 0f, Layout.W - AREA_W - 2f, Layout.H, fill = Palette.PANEL)
        d.line(AREA_W + 2f, 0f, AREA_W + 2f, Layout.H, Palette.PANEL_EDGE, 1.5f)

        ui.add("radar_exit", PX, 8f, 70f, 30f, "SAIR")
        ui.add("radar_zoom_out", PX + 77f, 8f, 56f, 30f, "−", enabled = r.zoom < ZOOMS_KM.size - 1)
        ui.add("radar_zoom_in", PX + 140f, 8f, 56f, 30f, "+", enabled = r.zoom > 0)

        val tgt = sim.target
        d.text(PX, 52f, "ALVO", Palette.MUTED, 9f)
        d.text(PX, 66f, Ui.clip(tgt.name.uppercase(), 12f, PW), tgt.faction.argb, 12f, bold = true)
        val appr = sim.approachSpeed()
        d.text(PX, 80f, Fmt.distance(sim.distanceTo()) + " · aprox. " + Fmt.speed(appr), if (appr > 0) Palette.GREEN else Palette.RED_SOFT, 9f)
        val (status, sc) = when (sim.canFire()) {
            FireResult.FIRED -> "TRAVADO · em alcance" to Palette.RED_SOFT
            FireResult.OUT_OF_RANGE -> "fora de alcance (${Fmt.int(Config.MISSILE_RANGE / 1000)} km)" to Palette.AMBER
            FireResult.NO_AMMO -> "sem mísseis" to Palette.RED_SOFT
            FireResult.FRIENDLY -> "aliado" to Palette.GREEN
            FireResult.NO_TARGET -> "não é uma nave" to Palette.MUTED
            FireResult.NOT_MILITARY -> "" to Palette.MUTED
        }
        d.text(PX, 93f, status, sc, 9f, bold = true)

        d.text(PX, 110f, "MÍSSEIS DELES A CAMINHO", Palette.MUTED, 9f)
        val inc = sim.incoming().mapNotNull { m -> sim.missileEta(m)?.let { m to it } }.sortedBy { it.second }.take(3)
        if (inc.isEmpty()) d.text(PX, 126f, "nenhum", Palette.GREEN, 10f)
        for ((i, p) in inc.withIndex()) {
            val (m, eta) = p
            val y = 120f + i * 20f
            val who = (sim.world.body(m.launcher)?.shortName ?: m.launcher).uppercase()
            d.text(PX, y + 8f, Ui.clip("A${m.id} · $who", 9f, 110f), Palette.INK, 9f)
            val urgent = eta < 12.0 && sin(t * 14) > 0
            d.text(PX + PW, y + 9f, Fmt.duration(eta), if (urgent) Palette.PLUME_CORE else Palette.RED, 11f, Align.RIGHT, bold = true)
            d.rect(PX, y + 13f, PW, 3f, fill = Palette.BUTTON)
            d.rect(PX, y + 13f, PW * (eta / Config.MISSILE_LIFE).toFloat().coerceIn(0f, 1f), 3f, fill = Palette.RED)
        }

        d.text(PX, 190f, "SEUS MÍSSEIS EM VOO", Palette.MUTED, 9f)
        val own = sim.missiles.filter { it.owner == Owner.PLAYER }.takeLast(3)
        if (own.isEmpty()) d.text(PX, 205f, "nenhum", Palette.MUTED, 10f)
        for ((i, m) in own.withIndex()) {
            val y = 200f + i * 17f
            val eta = sim.missileEta(m)
            val dest = if (m.targetId.length > 1 && m.targetId[0] == 'd' && m.targetId.drop(1).all { it.isDigit() }) "despistador" else (sim.world.body(m.targetId)?.shortName ?: m.targetId)
            d.text(PX, y + 8f, Ui.clip("S${m.id} → $dest", 9f, 120f), Palette.GREEN, 9f)
            d.text(PX + PW, y + 8f, if (eta != null) Fmt.duration(eta) else "—", Palette.GREEN, 10f, Align.RIGHT, bold = true)
        }

        d.text(PX, 258f, "VEL. REL. A " + Ui.clip(ref.shortName.uppercase(), 9f, 110f), Palette.MUTED, 9f)
        val vRel = ship.vel - sim.velocity(ref)
        d.text(PX, 272f, "atual " + Fmt.speed(vRel.length), Palette.GREEN, 11f, bold = true)
        val want = sim.desiredRelVel
        val engaged = sim.autopilot == AutopilotMode.VELOCITY && want != null
        d.text(PX + PW, 272f, if (engaged) "meta " + Fmt.speed(want!!.length) else "sem meta", if (engaged) Palette.AMBER else Palette.MUTED, 10f, Align.RIGHT, bold = engaged)
        ui.add("radar_zero", PX, 280f, 96f, 26f, "IGUALAR", active = engaged && want!!.length < 0.5)
        ui.add("radar_free", PX + 100f, 280f, 96f, 26f, "SOLTAR", enabled = engaged)

        val ready = sim.canFire() == FireResult.FIRED && sim.status == Status.FLYING
        val incoming = sim.incoming().isNotEmpty()
        val decoyOk = incoming && ship.decoys > 0 && sim.status == Status.FLYING
        ui.add("target", PX, 314f, 54f, 38f, "ALVO")
        ui.add("fire", PX + 57f, 314f, 62f, 38f, "MÍSSIL\n×${ship.missiles}", enabled = ready, accent = if (ready) Palette.RED else 0)
        ui.add("decoy", PX + 122f, 314f, 74f, 38f, "DESPIST.\n×${ship.decoys}" + Hud.oddsText(sim), enabled = decoyOk, active = decoyOk && sin(t * 12) > 0, accent = if (decoyOk) Palette.AMBER else 0)

        for (i in 0..2) {
            val f = sim.warpLevels[i]
            ui.add("warp_$i", PX + i * 66f, 358f, 64f, 26f, "×${Fmt.int(f)}", active = sim.warpIndex == i)
        }
        return ui.list
    }

    // ---- toque ----

    /** Toque na tela do radar. Devolve true quando o radar o usa (arrastar a nave ou escolher um alvo). */
    fun touchDown(r: RadarState, sim: Simulation, x: Float, y: Float): Boolean {
        if (x >= AREA_W) return false
        if (sim.status != Status.FLYING) return true
        val onShip = hypot(x - CX, y - CY) < 30f
        val onHandle = r.handleOn && hypot(x - r.handleX, y - r.handleY) < 26f
        if (onShip || onHandle) {
            r.dragging = true; r.moved = false; r.downX = x; r.downY = y
            return true
        }
        var best: RadarState.Hit? = null
        var bd = 30f
        for (h in r.hits) {
            val dd = hypot(h.x - x, h.y - y)
            if (dd < bd) { bd = dd; best = h }
        }
        best?.let { h -> sim.world.body(h.id)?.let { sim.selectTarget(it) } }
        return true
    }

    /** Arrasto da nave: a posição do dedo, em relação à nave, vira a velocidade relativa desejada. */
    fun touchMove(r: RadarState, sim: Simulation, x: Float, y: Float) {
        if (!r.dragging || sim.status != Status.FLYING) return
        if (!r.moved && hypot(x - r.downX, y - r.downY) < 8f) return
        r.moved = true
        val v = Vec2((x - CX) / r.k / HORIZON, -(y - CY) / r.k / HORIZON)
        val cap = VEC_PX_MAX / r.k / HORIZON
        val want = if (v.length > cap) v.unit() * cap else v
        val ref = if (sim.autopilot == AutopilotMode.VELOCITY) (sim.velocityRef ?: sim.target) else sim.target
        sim.commandVelocity(want, ref)
    }

    fun touchUp(r: RadarState) {
        r.dragging = false
    }
}
