package com.theexpanse.render

import com.theexpanse.content.BodyKind
import com.theexpanse.core.Blast
import com.theexpanse.core.BlastKind
import com.theexpanse.core.Body
import com.theexpanse.core.Owner
import com.theexpanse.core.Simulation
import com.theexpanse.core.Vec2
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Câmera externa vista de cima: o eixo da tela aponta da nave para o alvo (mais o giro da câmera), então a nave
 * gira na tela quando vira. Escala em duas zonas: até 60 m em escala real; além disso, log-radial (preserva a
 * direção e cabe tudo). Mostra cruzadores, mísseis, despistadores e explosões.
 */
object ExternalView {
    private const val CX = 260f
    private const val CY = 122f
    private const val NEAR_LIMIT = 60.0
    private const val PX_PER_M = 4.4f

    /** Coeficiente da escala logarítmica: a maior distância do sistema ainda cabe na tela. */
    private fun kLog(extent: Double): Double = (240.0 / ln((extent / NEAR_LIMIT).coerceAtLeast(10.0))).coerceAtMost(14.0)

    private fun radial(dist: Double, z: Float, k: Double): Float =
        (if (dist <= NEAR_LIMIT) (dist * PX_PER_M).toFloat() else (NEAR_LIMIT * PX_PER_M + k * ln(dist / NEAR_LIMIT)).toFloat()) * z

    private fun map(rel: Vec2, axis: Double, z: Float, k: Double): FloatArray {
        val ang = rel.angle - axis
        val s = radial(rel.length, z, k)
        return floatArrayOf(CX + cos(ang).toFloat() * s, CY - sin(ang).toFloat() * s)
    }

    fun draw(d: Draw, sim: Simulation, t: Double, cam: Camera) {
        val w = Layout.W
        val h = Layout.SCENE_H
        val z = cam.zoom.toFloat()
        val ship = sim.ship
        val k = kLog(sim.world.extent)
        d.rect(0f, 0f, w, h, fill = Palette.BG)
        d.clip(0f, 0f, w, h)

        val shake = Effects.hitShake(sim)
        d.ox = (sin(t * 71) * shake).toFloat()
        d.oy = (sin(t * 83 + 2) * shake).toFloat()

        val tgt = sim.target
        val axis = (if (sim.alive(tgt)) sim.relativePosition().angle else ship.angle) + cam.yaw

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
            val p = map(toPlanet, axis, z, k)
            val r = (sim.world.radius / dPlanet * 260.0).toFloat().coerceIn(18f, 150f) * z.coerceAtMost(1.6f)
            if (p[0] > -r && p[0] < w + r && p[1] > -r && p[1] < h + r) {
                if (sim.world.star) Planet.drawStar(d, p[0], p[1], r * 0.6f) else Planet.draw(d, p[0], p[1], r)
                d.text(p[0], p[1] + r + 13f, sim.world.planetName, Palette.AMBER_SOFT, 10f, Align.CENTER)
                d.text(p[0], p[1] + r + 24f, Fmt.distance(dPlanet - sim.world.radius) + " da superfície", Palette.MUTED, 9f, Align.CENTER)
            }
        }

        // linha até o alvo
        if (sim.alive(tgt)) {
            val tp = map(sim.relativePosition(), axis, z, k)
            d.line(CX, CY, tp[0], tp[1], Palette.AMBER.alpha(0.5f), 1f, dash = 5f)
        }

        // corpos, do mais longe ao mais perto
        for (b in sim.bodies.filter { sim.alive(it) }.sortedByDescending { sim.distanceTo(it) }) {
            drawBody(d, b, sim, axis, z, k, t, b === tgt)
        }

        // despistadores
        for (f in sim.flares) {
            val p = map(f.pos - ship.pos, axis, z, k)
            val tw = 0.7f + 0.3f * sin(t * 25 + f.born).toFloat()
            d.glow(p[0], p[1], 20f, 0xFFFFE9B0.toInt().alpha(0.55f * tw))
            d.circle(p[0], p[1], 2.6f, fill = Palette.PLUME_CORE.alpha(tw))
        }
        // mísseis
        for (m in sim.missiles) {
            val rel = m.pos - ship.pos
            val p = map(rel, axis, z, k)
            val v = m.vel - ship.vel
            val a = (if (v.length > 5.0) v.angle else (-rel).angle) - axis
            val color = if (m.owner == Owner.ENEMY) 0xFFFF5A4A.toInt() else Palette.AMBER
            Fx.missile(d, p[0], p[1], cos(a).toFloat(), -sin(a).toFloat(), color, t, 1.4f)
            if (m.owner == Owner.ENEMY && m.targetId == "jogador") d.text(p[0] + 12f, p[1] - 8f, "MÍSSIL " + Fmt.distance(rel.length), Palette.RED_SOFT, 9f, bold = true)
        }

        // vetor de velocidade relativa ao alvo
        val vRel = sim.relativeVelocity()
        val speed = vRel.length
        if (speed > 0.5 && sim.alive(tgt)) {
            val psi = vRel.angle - axis
            val len = (26.0 + 16.0 * log10(1.0 + speed)).toFloat() * z.coerceAtMost(1.5f)
            val ex = CX + cos(psi).toFloat() * len
            val ey = CY - sin(psi).toFloat() * len
            d.line(CX, CY, ex, ey, Palette.GREEN, 2.2f)
            val ux = cos(psi).toFloat()
            val uy = -sin(psi).toFloat()
            d.poly(floatArrayOf(ex, ey, ex - ux * 9 + uy * 4.5f, ey - uy * 9 - ux * 4.5f, ex - ux * 9 - uy * 4.5f, ey - uy * 9 + ux * 4.5f), fill = Palette.GREEN)
            d.text(ex + 6f, ey - 4f, "v " + Fmt.speed(speed), Palette.GREEN, 9f)
        }

        drawPlayer(d, sim, t, axis, z)

        // explosões
        for (b in sim.blasts) {
            val p = map(b.pos - ship.pos, axis, z, k)
            Fx.blast(d, p[0], p[1], (sim.time - b.time).toFloat(), b.kind, 1.2f * z.coerceAtMost(1.5f))
        }

        d.ox = 0f; d.oy = 0f
        Effects.hitFlash(d, sim, w, h)
        d.unclip()
    }

    private fun headingOf(sim: Simulation, b: Body): Double {
        val dyn = sim.dynamics[b.id]
        return if (dyn != null && dyn.mode != com.theexpanse.content.AiMode.ORBIT) dyn.heading(sim.ship.pos) else sim.velocity(b).angle
    }

    private fun drawBody(d: Draw, b: Body, sim: Simulation, axis: Double, z: Float, k: Double, t: Double, isTarget: Boolean) {
        val rel = sim.relativePosition(b)
        val dist = rel.length
        val p = map(rel, axis, z, k)
        val x = p[0]
        val y = p[1]
        val color = b.argb
        var r: Float
        when (b.kind) {
            BodyKind.PLANET -> {
                r = (b.def.radius / dist * 260.0).toFloat().coerceIn(5f, 90f) * z.coerceAtMost(1.6f)
                Planet.drawGeneric(d, x, y, r, color, gas = b.def.radius > 2e7)
            }
            BodyKind.SHIP -> {
                val len = (190.0 * sqrt(b.size / 90.0) * sqrt(3000.0 / (dist + 3000.0))).toFloat().coerceIn(14f, 260f) * z.coerceAtMost(1.6f)
                val scale = len / 140f
                val phi = headingOf(sim, b) - axis
                val dyn = sim.dynamics[b.id]
                val attacking = dyn != null && dyn.mode != com.theexpanse.content.AiMode.ORBIT
                val launch = sim.blasts.any { it.kind == BlastKind.LAUNCH && (it.pos - sim.position(b)).length < 5.0 && sim.time - it.time < 0.6 }
                Models.cruiser(d, b.faction.style, color, x, y, Mat.rot(phi, scale), if (attacking) 0.6f else 0f, 0.0, t, launching = if (launch) 1f else 0f)
                r = len * 0.5f
                if (attacking && sim.dynamics[b.id]?.mode == com.theexpanse.content.AiMode.ATTACK) d.text(x, y - r - 16f, "HOSTIL", Palette.RED_SOFT, 9f, Align.CENTER, bold = true)
            }
            BodyKind.WRECK -> {
                r = (190.0 * sqrt(b.size / 90.0) * sqrt(3000.0 / (dist + 3000.0))).toFloat().coerceIn(12f, 150f) * z.coerceAtMost(1.6f) * 0.5f
                d.glow(x, y, r * 1.8f, color.alpha(0.12f))
                Models.wreck(d, color, x, y, Mat.rot(t * 0.15, r * 2f / 110f), t)
            }
            BodyKind.STATION -> {
                r = (330.0 * (b.size / dist)).toFloat().coerceIn(9f, 110f) * z.coerceAtMost(1.6f)
                d.glow(x, y, r * 1.8f, color.alpha(0.18f))
                if (r > 18f) {
                    d.circle(x, y, r, stroke = 0xFF8C949F.toInt(), strokeW = (r * 0.12f).coerceAtLeast(2f))
                    for (k in 0 until 4) {
                        val a = k * PI.toFloat() / 2 + 0.4f + (t * 0.25).toFloat()
                        d.line(x, y, x + cos(a) * r, y + sin(a) * r, 0xFF6E7682.toInt(), 2f)
                    }
                    d.circle(x, y, r * 0.28f, fill = 0xFFA9B0BA.toInt(), stroke = color, strokeW = 1.5f)
                    for (k in 0 until 6) {
                        val a = k * PI.toFloat() / 3 + (t * 0.25).toFloat()
                        d.circle(x + cos(a) * r, y + sin(a) * r, 1.8f, fill = 0xFFF2D27A.toInt())
                    }
                } else d.rect(x - r * 0.55f, y - r * 0.55f, r * 1.1f, r * 1.1f, fill = color)
            }
        }
        if (isTarget) {
            val k = r + 6f
            val c = Palette.AMBER
            d.line(x - k, y - k, x - k + 8, y - k, c, 1.5f); d.line(x - k, y - k, x - k, y - k + 8, c, 1.5f)
            d.line(x + k, y - k, x + k - 8, y - k, c, 1.5f); d.line(x + k, y - k, x + k, y - k + 8, c, 1.5f)
            d.line(x - k, y + k, x - k + 8, y + k, c, 1.5f); d.line(x - k, y + k, x - k, y + k - 8, c, 1.5f)
            d.line(x + k, y + k, x + k - 8, y + k, c, 1.5f); d.line(x + k, y + k, x + k, y + k - 8, c, 1.5f)
        }
        if (!(isTarget || b.parent == null || b.kind == BodyKind.SHIP || dist < 5e7)) return
        val labelY = (if (isTarget) y + r + 14f else y - r - 22f).coerceIn(12f, Layout.SCENE_H - 24f)
        d.text(x, labelY, if (isTarget) "[ ${b.name} ]" else b.name, color, 10f, Align.CENTER, bold = isTarget)
        d.text(x, labelY + 11f, Fmt.distance(dist), Palette.INK, 9f, Align.CENTER)
    }

    private fun drawPlayer(d: Draw, sim: Simulation, t: Double, axis: Double, z: Float) {
        val ship = sim.ship
        val phi = ship.angle - axis
        val thrust = ship.burnFraction.toFloat()
        val fac = sim.world.playerFaction
        if (sim.world.isMilitary && fac != null) {
            Models.cruiser(d, fac.style, fac.argb, CX, CY, Mat.rot(phi, 0.92f * z), thrust, ship.turnAlpha, t,
                launching = if (sim.blasts.any { it.kind == BlastKind.LAUNCH && (it.pos - ship.pos).length < 5.0 && sim.time - it.time < 0.6 }) 1f else 0f)
        } else {
            Models.freighter(d, CX, CY, Mat.rot(phi, z), thrust, ship.turnAlpha, t)
        }
    }
}

/** Efeitos de tela comuns às duas vistas. */
object Effects {
    private fun lastHit(sim: Simulation): Blast? = sim.blasts.lastOrNull { it.kind == BlastKind.SHIP_HIT && (sim.ship.pos - it.pos).length < 1e5 }

    /** Amplitude da vibração logo após um impacto no jogador. */
    fun hitShake(sim: Simulation): Float {
        val b = lastHit(sim) ?: return 0f
        val age = (sim.time - b.time).toFloat()
        return if (age in 0f..0.9f) 7f * (1f - age / 0.9f) else 0f
    }

    /** Clarão vermelho e aviso quando um míssil acerta a nave. */
    fun hitFlash(d: Draw, sim: Simulation, w: Float, h: Float) {
        val b = lastHit(sim) ?: return
        val age = (sim.time - b.time).toFloat()
        if (age < 0f || age > 1.2f) return
        val a = (1f - age / 1.2f)
        d.rect(0f, 0f, w, h, fill = 0xFFFF3A2A.toInt().alpha(0.38f * a))
        d.text(w / 2, h / 2 + 4f, "IMPACTO", Palette.PLUME_CORE.alpha(a), 26f, Align.CENTER, bold = true)
    }
}
