package com.theexpanse.render

import com.theexpanse.core.Body
import com.theexpanse.core.BodyKind
import com.theexpanse.core.Calice
import com.theexpanse.core.Config
import com.theexpanse.core.Simulation
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.sin

/**
 * Câmera externa vista de cima: o eixo da tela aponta da nave para o alvo, então a nave gira na tela quando
 * vira. Escala em duas zonas: até 60 m em escala real; além disso, log-radial (preserva a direção e cabe tudo).
 */
object ExternalView {
    private const val CX = 260f
    private const val CY = 122f
    private const val NEAR_LIMIT = 60.0
    private const val PX_PER_M = 4.4f

    private fun radial(dist: Double): Float =
        if (dist <= NEAR_LIMIT) (dist * PX_PER_M).toFloat() else (NEAR_LIMIT * PX_PER_M + 14.0 * ln(dist / NEAR_LIMIT)).toFloat()

    private val SHIP_HULL = floatArrayOf(-50f, -13f, 28f, -13f, 52f, -6f, 52f, 6f, 28f, 13f, -50f, 13f)
    private val SHIP_NOZZLE = floatArrayOf(-64f, -10f, -50f, -10f, -50f, 10f, -64f, 10f)
    private val SHIP_CAB = floatArrayOf(34f, -5f, 48f, -3f, 48f, 3f, 34f, 5f)

    fun draw(d: Draw, sim: Simulation, t: Double) {
        val w = Layout.W
        val h = Layout.SCENE_H
        d.rect(0f, 0f, w, h, fill = Palette.BG)
        d.clip(0f, 0f, w, h)

        val ship = sim.ship
        val axis = sim.relativePosition().angle

        // estrelas fixas no mundo: giram na tela quando a câmera gira
        for (i in 0 until 130) {
            val a = Fx.hash(i * 3) * 2 * PI.toFloat()
            val r = Fx.hash(i * 3 + 1) * 640f
            val sx = w / 2 + r * cos(a - axis).toFloat()
            val sy = h / 2 - r * sin(a - axis).toFloat()
            if (sx < 0 || sx > w || sy < 0 || sy > h) continue
            d.circle(sx, sy, 0.6f + Fx.hash(i * 3 + 2) * 0.9f, fill = 0xFFC9D3E6.toInt().alpha(0.7f))
        }

        // planeta
        val toPlanet = -ship.pos
        val dPlanet = toPlanet.length
        run {
            val ang = toPlanet.angle - axis
            val s = radial(dPlanet)
            val px = CX + cos(ang).toFloat() * s
            val py = CY - sin(ang).toFloat() * s
            val r = (Config.PLANET_RADIUS / dPlanet * 260.0).toFloat().coerceIn(18f, 150f)
            if (px > -r && px < w + r && py > -r && py < h + r) {
                Planet.draw(d, px, py, r)
                d.text(px, py + r + 13f, Calice.PLANET_NAME, Palette.AMBER_SOFT, 10f, Align.CENTER)
                d.text(px, py + r + 24f, Fmt.distance(dPlanet - Config.PLANET_RADIUS) + " da superfície", Palette.MUTED, 9f, Align.CENTER)
            }
        }

        // linha até o alvo e corpos
        val tgt = sim.target
        val tgtDist = sim.distanceTo()
        val tgtX = CX + radial(tgtDist)
        d.line(CX, CY, tgtX, CY, Palette.AMBER.alpha(0.5f), 1f, dash = 5f)

        for (b in sim.bodies) {
            val rel = sim.relativePosition(b)
            val dist = rel.length
            val ang = rel.angle - axis
            val s = radial(dist)
            val bx = CX + cos(ang).toFloat() * s
            val by = CY - sin(ang).toFloat() * s
            val isTarget = b === tgt
            drawBody(d, b, bx, by, dist, ang, sim, isTarget)
        }

        // vetor de velocidade relativa ao alvo
        val vRel = sim.relativeVelocity()
        val speed = vRel.length
        if (speed > 0.5) {
            val psi = vRel.angle - axis
            val len = (26.0 + 16.0 * log10(1.0 + speed)).toFloat()
            val ex = CX + cos(psi).toFloat() * len
            val ey = CY - sin(psi).toFloat() * len
            d.line(CX, CY, ex, ey, Palette.GREEN, 2.2f)
            val ux = cos(psi).toFloat()
            val uy = -sin(psi).toFloat()
            d.poly(floatArrayOf(ex, ey, ex - ux * 9 + uy * 4.5f, ey - uy * 9 - ux * 4.5f, ex - ux * 9 - uy * 4.5f, ey - uy * 9 + ux * 4.5f), fill = Palette.GREEN)
            d.text(ex + 6f, ey - 4f, "v " + Fmt.speed(speed), Palette.GREEN, 9f)
        }

        drawShip(d, sim, t, axis)
        d.unclip()
    }

    private fun drawBody(d: Draw, b: Body, x: Float, y: Float, dist: Double, ang: Double, sim: Simulation, isTarget: Boolean) {
        val color = b.faction.argb
        val r = (330.0 * (Config.STATION_SIZE / dist)).toFloat().coerceIn(9f, 110f)
        if (b.kind == BodyKind.STATION) {
            d.glow(x, y, r * 1.8f, color.alpha(0.18f))
            if (r > 18f) {
                d.circle(x, y, r, stroke = 0xFF8C949F.toInt(), strokeW = (r * 0.12f).coerceAtLeast(2f))
                for (k in 0 until 4) {
                    val a = k * PI.toFloat() / 2 + 0.4f
                    d.line(x, y, x + cos(a) * r, y + sin(a) * r, 0xFF6E7682.toInt(), 2f)
                }
                d.circle(x, y, r * 0.28f, fill = 0xFFA9B0BA.toInt(), stroke = color, strokeW = 1.5f)
                for (k in 0 until 6) {
                    val a = k * PI.toFloat() / 3
                    d.circle(x + cos(a) * r, y + sin(a) * r, 1.8f, fill = 0xFFF2D27A.toInt())
                }
            } else {
                d.rect(x - r * 0.55f, y - r * 0.55f, r * 1.1f, r * 1.1f, fill = color)
            }
        } else {
            val l = r.coerceIn(8f, 40f)
            d.poly(placePoints(floatArrayOf(-l, -l * 0.35f, l * 0.7f, -l * 0.35f, l, 0f, l * 0.7f, l * 0.35f, -l, l * 0.35f), x, y, ang + PI), fill = color.alpha(0.85f), stroke = Palette.HULL_EDGE)
        }
        val labelY = if (isTarget) y + r + 12f else y - r - 22f
        if (isTarget) {
            val k = r + 6f
            val c = Palette.AMBER
            d.line(x - k, y - k, x - k + 8, y - k, c, 1.5f); d.line(x - k, y - k, x - k, y - k + 8, c, 1.5f)
            d.line(x + k, y - k, x + k - 8, y - k, c, 1.5f); d.line(x + k, y - k, x + k, y - k + 8, c, 1.5f)
            d.line(x - k, y + k, x - k + 8, y + k, c, 1.5f); d.line(x - k, y + k, x - k, y + k - 8, c, 1.5f)
            d.line(x + k, y + k, x + k - 8, y + k, c, 1.5f); d.line(x + k, y + k, x + k, y + k - 8, c, 1.5f)
        }
        d.text(x, labelY.coerceAtMost(Layout.SCENE_H - 22f), (if (isTarget) "[ ${b.name} ]" else b.name), color, 10f, Align.CENTER, bold = isTarget)
        d.text(x, labelY.coerceAtMost(Layout.SCENE_H - 22f) + 11f, Fmt.distance(dist), Palette.INK, 9f, Align.CENTER)
    }

    private fun drawShip(d: Draw, sim: Simulation, t: Double, axis: Double) {
        val ship = sim.ship
        val phi = ship.angle - axis
        val c = cos(phi).toFloat()
        val s = sin(phi).toFloat()
        // direção "para trás" do nariz, na tela
        val backX = -c
        val backY = s
        val noz = placePoints(floatArrayOf(-64f, 0f), CX, CY, phi)

        val intensity = ship.burnFraction.toFloat()
        Fx.plume(d, noz[0], noz[1], backX, backY, 130f, 26f, intensity, t)

        // asas/radiadores e carga
        d.poly(placePoints(floatArrayOf(-30f, -34f, -2f, -34f, 4f, -13f, -34f, -13f), CX, CY, phi), fill = 0xFF2D4C7A.toInt(), stroke = Palette.HULL_EDGE)
        d.poly(placePoints(floatArrayOf(-30f, 34f, -2f, 34f, 4f, 13f, -34f, 13f), CX, CY, phi), fill = 0xFF2D4C7A.toInt(), stroke = Palette.HULL_EDGE)
        d.poly(placePoints(SHIP_NOZZLE, CX, CY, phi), fill = 0xFF4F5762.toInt(), stroke = Palette.HULL_EDGE)
        d.poly(placePoints(SHIP_HULL, CX, CY, phi), fill = Palette.HULL, stroke = Palette.HULL_EDGE, strokeW = 1.2f)
        d.poly(placePoints(floatArrayOf(-34f, -8f, -4f, -8f, -4f, 8f, -34f, 8f), CX, CY, phi), fill = Palette.AMBER.alpha(0.9f))
        d.poly(placePoints(SHIP_CAB, CX, CY, phi), fill = 0xFF5CC8E8.toInt())
        // brilho azul no bocal quando queima
        if (intensity > 0.02f) d.glow(noz[0], noz[1], 14f, Palette.PLUME_HOT.alpha(0.9f * intensity))

        // propulsores de manobra: alpha > 0 (anti-horário) dispara o nariz para -y e a popa para +y (locais)
        val a = ship.turnAlpha
        if (kotlin.math.abs(a) > 1e-6) {
            val sg = if (a > 0) 1f else -1f
            val nose = placePoints(floatArrayOf(44f, -sg * 12f), CX, CY, phi)
            val tail = placePoints(floatArrayOf(-48f, sg * 14f), CX, CY, phi)
            val dirNose = placePoints(floatArrayOf(0f, -sg), 0f, 0f, phi)
            val dirTail = placePoints(floatArrayOf(0f, sg), 0f, 0f, phi)
            Fx.rcsPuff(d, nose[0], nose[1], dirNose[0], dirNose[1], 18f, t, 1)
            Fx.rcsPuff(d, tail[0], tail[1], dirTail[0], dirTail[1], 18f, t, 2)
        }
    }
}
