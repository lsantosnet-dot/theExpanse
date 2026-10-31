package com.theexpanse.render

import com.theexpanse.core.Body
import com.theexpanse.core.BodyKind
import com.theexpanse.core.Calice
import com.theexpanse.core.Config
import com.theexpanse.core.Simulation
import com.theexpanse.core.wrapAngle
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.sin

/**
 * Cabine: olha na direção do nariz, com campo de visão de 100°. O plano orbital é o horizonte; o planeta e
 * as estações aparecem em seu rumo e tamanho angular. A queima enche a cabine de luz azul, faz tudo vibrar e
 * mostra o jato na câmera traseira; a virada varre as estrelas e acende os propulsores de manobra.
 */
object CockpitView {
    private const val FOV = 1.75f // rad (100°)
    private const val HORIZON = 118f

    private val PPR = Layout.W / FOV

    private fun screenX(bearing: Double): Float = Layout.W / 2 - bearing.toFloat() * PPR

    fun draw(d: Draw, sim: Simulation, t: Double) {
        val w = Layout.W
        val h = Layout.SCENE_H
        val ship = sim.ship
        val intensity = ship.burnFraction.toFloat()

        d.rect(0f, 0f, w, h, fill = Palette.BG)
        d.clip(0f, 0f, w, h)

        // vibração do motor
        d.ox = (sin(t * 47) * intensity * 1.4).toFloat()
        d.oy = (sin(t * 61 + 1) * intensity * 1.4).toFloat()

        // estrelas na esfera celeste: azimute fixo no mundo; ao girar viram riscos (desfoque de movimento)
        val streak = (abs(ship.omega) * PPR / 30.0).toFloat()
        val sign = if (ship.omega > 0) 1f else -1f
        for (i in 0 until 170) {
            val az = (Fx.hash(i * 3) * 2 * PI - PI)
            val el = (Fx.hash(i * 3 + 1) - 0.5f) * 0.56f
            val sx = screenX(wrapAngle(az - ship.angle))
            val sy = HORIZON - el * PPR
            if (sx < -4 || sx > w + 4 || sy < -4 || sy > h + 4) continue
            val size = 0.6f + Fx.hash(i * 3 + 2) * 0.9f
            if (streak > 1.5f) d.line(sx, sy, sx + streak * sign, sy, 0xFFC9D3E6.toInt().alpha(0.6f), size * 1.6f)
            else d.circle(sx, sy, size, fill = 0xFFC9D3E6.toInt().alpha(0.75f))
        }

        // planeta
        val toPlanet = -ship.pos
        val dPlanet = toPlanet.length
        run {
            val b = wrapAngle(toPlanet.angle - ship.angle)
            val rho = asin((Config.PLANET_RADIUS / dPlanet).coerceAtMost(1.0))
            val r = (rho * PPR).toFloat().coerceAtMost(2000f)
            if (abs(b) - rho < FOV / 2 + 0.1) {
                val x = screenX(b)
                Planet.draw(d, x, HORIZON, r)
                val ly = (HORIZON + r + 14f).coerceAtMost(h - 12f)
                d.text(x.coerceIn(150f, w - 150f), ly, Calice.PLANET_NAME + " · " + Fmt.distance(dPlanet - Config.PLANET_RADIUS), Palette.AMBER_SOFT, 10f, Align.CENTER)
            }
        }

        // estações e fragata
        for (b in sim.bodies) drawBody(d, sim, b)

        // prograde e retrógrado (relativos ao alvo)
        val vRel = sim.relativeVelocity()
        if (vRel.length > 0.5) {
            drawPrograde(d, wrapAngle(vRel.angle - ship.angle))
            drawRetro(d, wrapAngle(vRel.angle + PI - ship.angle))
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

        // propulsores de manobra: lampejos nos cantos da janela
        val a = ship.turnAlpha
        if (abs(a) > 1e-6) {
            if (a > 0) Fx.rcsPuff(d, w - 100f, 46f, 1f, 0f, 22f, t, 3)
            else Fx.rcsPuff(d, 100f, 46f, -1f, 0f, 22f, t, 4)
        }

        drawRearCamera(d, sim, t, intensity)
        d.unclip()
    }

    private fun drawBody(d: Draw, sim: Simulation, b: Body) {
        val ship = sim.ship
        val rel = sim.relativePosition(b)
        val dist = rel.length
        val bearing = wrapAngle(rel.angle - ship.angle)
        if (abs(bearing) > FOV / 2 + 0.1) return
        val x = screenX(bearing)
        val isTarget = b === sim.target
        val color = b.faction.argb
        val r = (b.size / 2 / dist * PPR).toFloat().coerceAtLeast(2.5f)
        d.glow(x, HORIZON, 14f + r, color.alpha(0.35f))
        if (b.kind == BodyKind.STATION) d.rect(x - r, HORIZON - r, r * 2, r * 2, fill = color)
        else d.poly(floatArrayOf(x - r, HORIZON + r, x, HORIZON - r, x + r, HORIZON + r), fill = color)
        if (isTarget) {
            val k = r + 9f
            val c = Palette.AMBER
            d.line(x - k, HORIZON - k, x - k + 7, HORIZON - k, c, 1.5f); d.line(x - k, HORIZON - k, x - k, HORIZON - k + 7, c, 1.5f)
            d.line(x + k, HORIZON - k, x + k - 7, HORIZON - k, c, 1.5f); d.line(x + k, HORIZON - k, x + k, HORIZON - k + 7, c, 1.5f)
            d.line(x - k, HORIZON + k, x - k + 7, HORIZON + k, c, 1.5f); d.line(x - k, HORIZON + k, x - k, HORIZON + k - 7, c, 1.5f)
            d.line(x + k, HORIZON + k, x + k - 7, HORIZON + k, c, 1.5f); d.line(x + k, HORIZON + k, x + k, HORIZON + k - 7, c, 1.5f)
        }
        // o alvo leva o rótulo embaixo; os outros, em cima (evita sobreposição de corpos no mesmo rumo)
        val ty = if (isTarget) HORIZON + r + 24f else HORIZON - r - 24f
        d.text(x, ty, if (isTarget) "[ ${b.name} ]" else b.name, color, 10f, Align.CENTER, bold = isTarget)
        d.text(x, ty + 11f, Fmt.distance(dist), Palette.INK, 9f, Align.CENTER)
    }

    private fun drawPrograde(d: Draw, bearing: Double) {
        if (abs(bearing) > FOV / 2) return
        val x = screenX(bearing)
        d.circle(x, HORIZON, 8f, stroke = Palette.GREEN, strokeW = 1.6f)
        d.circle(x, HORIZON, 1.6f, fill = Palette.GREEN)
        d.line(x, HORIZON - 13f, x, HORIZON - 9f, Palette.GREEN, 1.6f)
        d.line(x, HORIZON + 9f, x, HORIZON + 13f, Palette.GREEN, 1.6f)
        d.line(x - 13f, HORIZON, x - 9f, HORIZON, Palette.GREEN, 1.6f)
        d.line(x + 9f, HORIZON, x + 13f, HORIZON, Palette.GREEN, 1.6f)
        d.text(x + 14f, HORIZON - 12f, "PRÓ", Palette.GREEN, 9f)
    }

    private fun drawRetro(d: Draw, bearing: Double) {
        if (abs(bearing) > FOV / 2) return
        val x = screenX(bearing)
        d.line(x - 6f, HORIZON - 6f, x + 6f, HORIZON + 6f, Palette.RED, 1.8f)
        d.line(x - 6f, HORIZON + 6f, x + 6f, HORIZON - 6f, Palette.RED, 1.8f)
        d.text(x + 10f, HORIZON - 10f, "RETRO", Palette.RED, 9f)
    }

    /** Câmera traseira: o bocal visto de trás, com a energia azul saindo em círculos que crescem. */
    private fun drawRearCamera(d: Draw, sim: Simulation, t: Double, intensity: Float) {
        val x = Layout.W - 178f
        val y = 14f
        val w = 160f
        val h = 100f
        d.rect(x, y, w, h, fill = 0xFF04060A.toInt(), stroke = Palette.AMBER, strokeW = 1.2f)
        d.clip(x, y, w, h)
        for (i in 0 until 22) {
            d.circle(x + Fx.hash(i + 500) * w, y + Fx.hash(i + 600) * h, 0.7f, fill = 0xFFC9D3E6.toInt().alpha(0.5f))
        }
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
