package com.theexpanse.render

import com.theexpanse.content.AiMode
import com.theexpanse.content.BodyKind
import com.theexpanse.core.Body
import com.theexpanse.core.Owner
import com.theexpanse.core.Simulation
import com.theexpanse.core.Vec2
import com.theexpanse.core.wrapAngle
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Cabine: olha na direção do nariz (mais o giro da câmera), com campo de visão de 100° (ou menos, com zoom). O
 * plano orbital é o horizonte. A queima enche a cabine de luz azul e faz tudo vibrar; a virada varre as estrelas;
 * mísseis, despistadores, explosões e naves hostis aparecem na janela.
 */
object CockpitView {
    private const val FOV = 1.75f // rad (100°)
    private const val HORIZON = 118f

    fun draw(d: Draw, sim: Simulation, t: Double, cam: Camera) {
        val w = Layout.W
        val h = Layout.SCENE_H
        val ship = sim.ship
        val ppr = w / FOV * cam.zoom.toFloat()
        val view = ship.angle + cam.yaw
        fun sx(bearing: Double): Float = w / 2 - bearing.toFloat() * ppr
        val half = (w / 2 / ppr).toDouble()
        val intensity = ship.burnFraction.toFloat()

        d.rect(0f, 0f, w, h, fill = Palette.BG)
        d.clip(0f, 0f, w, h)

        // vibração do motor e do impacto
        val hs = Effects.hitShake(sim)
        d.ox = (sin(t * 47) * (intensity * 1.4 + hs)).toFloat()
        d.oy = (sin(t * 61 + 1) * (intensity * 1.4 + hs)).toFloat()

        // estrelas na esfera celeste: azimute fixo no mundo; ao girar viram riscos (desfoque de movimento)
        val streak = (abs(ship.omega) * ppr / 30.0).toFloat()
        val sign = if (ship.omega > 0) 1f else -1f
        for (i in 0 until 170) {
            val az = (Fx.hash(i * 3) * 2 * PI - PI)
            val el = (Fx.hash(i * 3 + 1) - 0.5f) * 0.56f
            val x = sx(wrapAngle(az - view))
            val y = HORIZON - el * ppr
            if (x < -4 || x > w + 4 || y < -4 || y > h + 4) continue
            val size = 0.6f + Fx.hash(i * 3 + 2) * 0.9f
            if (streak > 1.5f) d.line(x, y, x + streak * sign, y, 0xFFC9D3E6.toInt().alpha(0.6f), size * 1.6f)
            else d.circle(x, y, size, fill = 0xFFC9D3E6.toInt().alpha(0.75f))
        }

        // planeta
        val toPlanet = -ship.pos
        val dPlanet = toPlanet.length
        run {
            val b = wrapAngle(toPlanet.angle - view)
            val rho = asin((sim.world.radius / dPlanet).coerceAtMost(1.0))
            val r = (rho * ppr).toFloat().coerceAtMost(2000f)
            if (abs(b) - rho < half + 0.1) {
                val x = sx(b)
                Planet.draw(d, x, HORIZON, r)
                val ly = (HORIZON + r + 14f).coerceAtMost(h - 52f)
                d.text(x.coerceIn(150f, w - 150f), ly, sim.world.planetName + " · " + Fmt.distance(dPlanet - sim.world.radius), Palette.AMBER_SOFT, 10f, Align.CENTER)
            }
        }

        // estações e naves, do mais longe ao mais perto
        for (b in sim.bodies.filter { sim.alive(it) }.sortedByDescending { sim.distanceTo(it) }) drawBody(d, sim, b, view, ppr, half, t)

        // despistadores, mísseis e explosões
        for (f in sim.flares) {
            val rel = f.pos - ship.pos
            val b = wrapAngle(rel.angle - view)
            if (abs(b) > half + 0.1) continue
            val x = sx(b)
            val tw = 0.7f + 0.3f * sin(t * 25 + f.born).toFloat()
            d.glow(x, HORIZON, 22f, 0xFFFFE9B0.toInt().alpha(0.6f * tw))
            d.circle(x, HORIZON, 2.4f, fill = Palette.PLUME_CORE.alpha(tw))
        }
        for (m in sim.missiles) {
            val rel = m.pos - ship.pos
            val b = wrapAngle(rel.angle - view)
            if (abs(b) > half + 0.1) continue
            val x = sx(b)
            val color = if (m.owner == Owner.ENEMY) 0xFFFF5A4A.toInt() else Palette.AMBER
            // rastro: onde o míssil estava 0,7 s antes, no referencial da nave
            val back = rel - (m.vel - ship.vel) * 0.7
            val bx = sx(wrapAngle(back.angle - view)).coerceIn(x - 60f, x + 60f)
            d.line(x, HORIZON, bx, HORIZON, color.alpha(0.5f), 2f)
            d.glow(x, HORIZON, 14f, color.alpha(0.8f))
            d.circle(x, HORIZON, 2.6f, fill = Palette.PLUME_CORE)
            if (m.owner == Owner.ENEMY && m.targetId == "jogador") d.text(x + 10f, HORIZON - 10f, "MÍSSIL " + Fmt.distance(rel.length), Palette.RED_SOFT, 9f, bold = true)
        }
        for (bl in sim.blasts) {
            val rel = bl.pos - ship.pos
            if (rel.length < 1.0) continue
            val b = wrapAngle(rel.angle - view)
            if (abs(b) > half + 0.1) continue
            Fx.blast(d, sx(b), HORIZON, (sim.time - bl.time).toFloat(), bl.kind, 1.0f)
        }

        // prógrado e retrógrado (relativos ao alvo)
        val vRel = sim.relativeVelocity()
        if (vRel.length > 0.5 && sim.alive(sim.target)) {
            marker(d, wrapAngle(vRel.angle - view), ppr, half, true)
            marker(d, wrapAngle(vRel.angle + PI - view), ppr, half, false)
        }

        d.ox = 0f
        d.oy = 0f

        // luz azul do motor dentro da cabine
        if (intensity > 0.02f) {
            val pulse = 0.85f + 0.15f * sin(t * 30).toFloat()
            d.glow(w / 2, h + 40f, 460f, Palette.PLUME_MID.alpha(0.42f * intensity * pulse))
            d.rect(0f, h - 34f, w, 34f, fill = Palette.PLUME_MID.alpha(0.12f * intensity * pulse))
        }

        // moldura da janela
        val frame = 0xFF0B0F17.toInt()
        val edge = 0xFF1F2A3A.toInt()
        d.poly(floatArrayOf(0f, 0f, 118f, 0f, 62f, h, 0f, h), fill = frame, stroke = edge, strokeW = 2f)
        d.poly(floatArrayOf(w, 0f, w - 118f, 0f, w - 62f, h, w, h), fill = frame, stroke = edge, strokeW = 2f)
        d.rect(0f, 0f, w, 7f, fill = frame)
        if (intensity > 0.02f) {
            val tint = Palette.PLUME_MID.alpha(0.14f * intensity)
            d.poly(floatArrayOf(0f, 0f, 118f, 0f, 62f, h, 0f, h), fill = tint)
            d.poly(floatArrayOf(w, 0f, w - 118f, 0f, w - 62f, h, w, h), fill = tint)
        }

        // alerta de míssil: borda vermelha pulsando
        if (sim.incoming().isNotEmpty()) {
            val pulse = 0.5f + 0.5f * sin(t * 12).toFloat()
            d.rect(2f, 2f, w - 4f, h - 4f, stroke = Palette.RED.alpha(0.35f + 0.4f * pulse), strokeW = 4f)
        }

        // propulsores de manobra: lampejos nos cantos da janela
        val a = ship.turnAlpha
        if (abs(a) > 1e-6) {
            if (a > 0) Fx.rcsPuff(d, w - 100f, 46f, 1f, 0f, 22f, t, 3)
            else Fx.rcsPuff(d, 100f, 46f, -1f, 0f, 22f, t, 4)
        }

        drawRearCamera(d, sim, t, intensity)
        if (!cam.centered) {
            val deg = Math.toDegrees(cam.yaw).toInt()
            val txt = if (abs(deg) < 1) "ZOOM ×" + Fmt.dec(cam.zoom, 1) else "OLHANDO " + abs(deg) + "° " + (if (deg > 0) "À ESQUERDA" else "À DIREITA") + (if (abs(cam.zoom - 1.0) > 0.05) " · ×" + Fmt.dec(cam.zoom, 1) else "")
            d.text(w / 2, 34f, txt, Palette.AMBER, 9f, Align.CENTER, bold = true)
        }
        Effects.hitFlash(d, sim, w, h)
        d.unclip()
    }

    private fun drawBody(d: Draw, sim: Simulation, b: Body, view: Double, ppr: Float, half: Double, t: Double) {
        val rel = sim.relativePosition(b)
        val dist = rel.length
        if (dist < 1.0) return
        val bearing = wrapAngle(rel.angle - view)
        val x = Layout.W / 2 - bearing.toFloat() * ppr
        val isTarget = b === sim.target
        val color = b.faction.argb
        var r: Float
        if (b.kind == BodyKind.SHIP) {
            if (abs(bearing) > half + 0.3) return
            val len = (110.0 * sqrt(b.size / 90.0) * sqrt(25_000.0 / (dist + 25_000.0))).toFloat().coerceIn(8f, 150f) * (ppr / (Layout.W / FOV)).coerceAtMost(2f)
            val dyn = sim.dynamics[b.id]
            val heading = if (dyn != null && dyn.mode != AiMode.ORBIT) (sim.ship.pos - dyn.pos).angle else sim.velocity(b).angle
            profile(d, x, HORIZON, len, color, sin(wrapAngle(heading - rel.angle)), dyn != null && dyn.mode != AiMode.ORBIT, t)
            r = len * 0.5f
            if (dyn?.mode == AiMode.ATTACK) d.text(x, HORIZON - r - 30f, "HOSTIL", Palette.RED_SOFT, 9f, Align.CENTER, bold = true)
        } else {
            if (abs(bearing) > half + 0.1) return
            r = (b.size / 2 / dist * ppr).toFloat().coerceAtLeast(2.5f)
            d.glow(x, HORIZON, 14f + r, color.alpha(0.35f))
            when (b.kind) {
                BodyKind.STATION -> d.rect(x - r, HORIZON - r, r * 2, r * 2, fill = color)
                else -> d.poly(floatArrayOf(x - r, HORIZON, x, HORIZON - r, x + r, HORIZON, x, HORIZON + r), fill = color.alpha(0.8f))
            }
        }
        if (isTarget) {
            val k = r + 9f
            val c = Palette.AMBER
            d.line(x - k, HORIZON - k, x - k + 7, HORIZON - k, c, 1.5f); d.line(x - k, HORIZON - k, x - k, HORIZON - k + 7, c, 1.5f)
            d.line(x + k, HORIZON - k, x + k - 7, HORIZON - k, c, 1.5f); d.line(x + k, HORIZON - k, x + k, HORIZON - k + 7, c, 1.5f)
            d.line(x - k, HORIZON + k, x - k + 7, HORIZON + k, c, 1.5f); d.line(x - k, HORIZON + k, x - k, HORIZON + k - 7, c, 1.5f)
            d.line(x + k, HORIZON + k, x + k - 7, HORIZON + k, c, 1.5f); d.line(x + k, HORIZON + k, x + k, HORIZON + k - 7, c, 1.5f)
        }
        val ty = if (isTarget) HORIZON + r + 24f else HORIZON - r - 24f
        d.text(x, ty, if (isTarget) "[ ${b.name} ]" else b.name, color, 10f, Align.CENTER, bold = isTarget)
        d.text(x, ty + 11f, Fmt.distance(dist), Palette.INK, 9f, Align.CENTER)
    }

    /** Silhueta lateral de um cruzador: comprimento proporcional ao seno do ângulo entre o rumo dele e a linha de visada. */
    private fun profile(d: Draw, x: Float, y: Float, len: Float, color: Int, s: Double, thrusting: Boolean, t: Double) {
        val face = abs(s).toFloat()
        val l = len * (0.18f + 0.82f * face)
        val hgt = len * 0.14f
        val dir = if (s > 0) -1f else 1f // nariz para a esquerda quando s > 0
        val hull = mix(0xFFB8C0CC.toInt(), color, 0.3f)
        d.glow(x, y, len * 0.9f, color.alpha(0.16f))
        d.poly(floatArrayOf(x - dir * l * 0.5f, y - hgt * 0.7f, x + dir * l * 0.25f, y - hgt * 0.7f, x + dir * l * 0.5f, y, x + dir * l * 0.25f, y + hgt * 0.7f, x - dir * l * 0.5f, y + hgt * 0.7f), fill = hull, stroke = Palette.HULL_EDGE)
        d.rect(x - l * 0.15f, y - hgt * 1.25f, l * 0.3f, hgt * 0.55f, fill = color.alpha(0.9f))
        d.rect(x - l * 0.5f, y - hgt * 0.15f, l, hgt * 0.3f, fill = color.alpha(0.8f))
        if (thrusting && face < 0.9f) d.glow(x - dir * l * 0.5f, y, len * 0.22f, Palette.PLUME_HOT.alpha(0.9f))
        if (sin(t * 5.0) > 0.4) d.circle(x, y - hgt * 0.9f, 1.4f, fill = Palette.RED)
    }

    private fun marker(d: Draw, bearing: Double, ppr: Float, half: Double, pro: Boolean) {
        if (abs(bearing) > half * 0.95) return
        val x = Layout.W / 2 - bearing.toFloat() * ppr
        if (pro) {
            d.circle(x, HORIZON, 8f, stroke = Palette.GREEN, strokeW = 1.6f)
            d.circle(x, HORIZON, 1.6f, fill = Palette.GREEN)
            d.line(x, HORIZON - 13f, x, HORIZON - 9f, Palette.GREEN, 1.6f)
            d.line(x, HORIZON + 9f, x, HORIZON + 13f, Palette.GREEN, 1.6f)
            d.line(x - 13f, HORIZON, x - 9f, HORIZON, Palette.GREEN, 1.6f)
            d.line(x + 9f, HORIZON, x + 13f, HORIZON, Palette.GREEN, 1.6f)
            d.text(x + 14f, HORIZON - 12f, "PRÓ", Palette.GREEN, 9f)
        } else {
            d.line(x - 6f, HORIZON - 6f, x + 6f, HORIZON + 6f, Palette.RED, 1.8f)
            d.line(x - 6f, HORIZON + 6f, x + 6f, HORIZON - 6f, Palette.RED, 1.8f)
            d.text(x + 10f, HORIZON - 10f, "RETRO", Palette.RED, 9f)
        }
    }

    /** Câmera traseira: o bocal visto de trás, com a energia azul saindo em riscos que crescem. */
    private fun drawRearCamera(d: Draw, sim: Simulation, t: Double, intensity: Float) {
        val x = Layout.W - 178f
        val y = 14f
        val w = 160f
        val h = 100f
        d.rect(x, y, w, h, fill = 0xFF04060A.toInt(), stroke = Palette.AMBER, strokeW = 1.2f)
        d.clip(x, y, w, h)
        for (i in 0 until 22) d.circle(x + Fx.hash(i + 500) * w, y + Fx.hash(i + 600) * h, 0.7f, fill = 0xFFC9D3E6.toInt().alpha(0.5f))
        val cx = x + w / 2
        val cy = y + h / 2 + 4f
        if (intensity < 0.02f) d.glow(cx, cy, 30f, Palette.PLUME_MID.alpha(0.16f))
        Fx.radialBurst(d, cx, cy, 58f, intensity, t)
        d.circle(cx, cy, 13f, fill = 0xFF1B2230.toInt(), stroke = 0xFF8C949F.toInt(), strokeW = 2.5f)
        d.circle(cx, cy, 6f, fill = if (intensity > 0.02f) Palette.PLUME_HOT.alpha(0.5f + 0.5f * intensity) else 0xFF0B0F17.toInt())
        d.unclip()
        d.rect(x, y, w, 15f, fill = 0xFF04060A.toInt().alpha(0.7f))
        d.text(x + 6f, y + 11f, "CÂM. TRASEIRA", Palette.AMBER, 9f)
        d.text(x + w - 6f, y + 11f, if (intensity > 0.02f) "MOTOR ${sim.thrustPct}%" else "APAGADO", if (intensity > 0.02f) Palette.PLUME_HOT else Palette.MUTED, 9f, Align.RIGHT)
    }

}
