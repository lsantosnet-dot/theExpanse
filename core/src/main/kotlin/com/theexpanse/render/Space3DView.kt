package com.theexpanse.render

import com.theexpanse.content.AiMode
import com.theexpanse.content.BodyKind
import com.theexpanse.core.Body
import com.theexpanse.core.Owner
import com.theexpanse.core.Simulation
import com.theexpanse.core.Vec2
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Vista 3D: câmera em perspectiva que gira em volta da nave (yaw e pitch), com chão de grade no plano orbital,
 * estrelas no infinito e corpos colocados na direção real, a uma distância visual comprimida (escala em duas
 * zonas: real até 60 m, logarítmica depois). A simulação é plana; a 3D é só a forma de olhar para ela.
 */
object Space3DView {
    private const val F = 430f
    private const val CX = 422f
    private const val CY = 122f

    private class Cam(val cx: Double, val cy: Double, val cz: Double, val r: DoubleArray, val u: DoubleArray, val f: DoubleArray) {
        /** Projeta um ponto do mundo visual; devolve (x, y, profundidade) ou null se estiver atrás da câmera. */
        fun project(x: Double, y: Double, z: Double): FloatArray? {
            val vx = x - cx
            val vy = y - cy
            val vz = z - cz
            val zc = vx * f[0] + vy * f[1] + vz * f[2]
            if (zc < 8.0) return null
            val sx = CX + F * (vx * r[0] + vy * r[1] + vz * r[2]) / zc
            val sy = CY - F * (vx * u[0] + vy * u[1] + vz * u[2]) / zc
            return floatArrayOf(sx.toFloat(), sy.toFloat(), zc.toFloat())
        }

        /** Direção no infinito (estrelas). */
        fun projectDir(x: Double, y: Double, z: Double): FloatArray? {
            val zc = x * f[0] + y * f[1] + z * f[2]
            if (zc < 0.08) return null
            return floatArrayOf((CX + F * (x * r[0] + y * r[1] + z * r[2]) / zc).toFloat(), (CY - F * (x * u[0] + y * u[1] + z * u[2]) / zc).toFloat(), zc.toFloat())
        }
    }

    /** Distância visual (unidades) de um corpo real a [dist] metros. */
    private fun vr(dist: Double, kv: Double): Double = if (dist <= 60.0) dist * 0.5 else 30.0 + kv * ln(dist / 60.0)

    private fun matAt(cam: Cam, q: Vec2, phi: Double, unit: Double): Mat? {
        val p0 = cam.project(q.x, q.y, 0.0) ?: return null
        val px = cam.project(q.x + cos(phi) * unit, q.y + sin(phi) * unit, 0.0) ?: return null
        val py = cam.project(q.x - sin(phi) * unit, q.y + cos(phi) * unit, 0.0) ?: return null
        return Mat(px[0] - p0[0], py[0] - p0[0], px[1] - p0[1], py[1] - p0[1])
    }

    fun draw(d: Draw, sim: Simulation, t: Double, cam: Camera) {
        val w = Layout.W
        val h = Layout.SCENE_H
        val ship = sim.ship
        d.rect(0f, 0f, w, h, fill = Palette.BG)
        d.clip(0f, 0f, w, h)
        val shake = Effects.hitShake(sim)
        d.ox = (sin(t * 71) * shake).toFloat()
        d.oy = (sin(t * 83 + 2) * shake).toFloat()

        val theta = ship.angle + cam.yaw
        val pitch = cam.pitch
        val dist = 250.0 / cam.zoom
        val cp = cos(pitch); val sp = sin(pitch); val ct = cos(theta); val st = sin(theta)
        val c = Cam(-dist * cp * ct, -dist * cp * st, dist * sp,
            doubleArrayOf(st, -ct, 0.0), doubleArrayOf(sp * ct, sp * st, cp), doubleArrayOf(cp * ct, cp * st, -sp))
        val kv = 350.0 / ln((sim.world.extent / 60.0).coerceAtLeast(10.0))

        // estrelas no infinito
        for (i in 0 until 170) {
            val az = Fx.hash(i * 3) * 2 * PI
            val el = (Fx.hash(i * 3 + 1) - 0.5) * 2.6
            val p = c.projectDir(cos(el) * cos(az), cos(el) * sin(az), sin(el)) ?: continue
            if (p[0] < -4 || p[0] > w + 4 || p[1] < -4 || p[1] > h + 4) continue
            d.circle(p[0], p[1], 0.6f + Fx.hash(i * 3 + 2) * 0.9f, fill = 0xFFC9D3E6.toInt().alpha(0.7f))
        }

        // chão: anéis e raios no plano orbital (profundidade)
        val grid = Palette.AMBER.alpha(0.13f)
        for (ring in 1..5) {
            val r = ring * 70.0
            var prev: FloatArray? = null
            for (k in 0..64) {
                val a = 2 * PI * k / 64
                val p = c.project(cos(a) * r, sin(a) * r, 0.0)
                if (p != null && prev != null) d.line(prev[0], prev[1], p[0], p[1], grid, 1f)
                prev = p
            }
        }
        for (k in 0 until 12) {
            val a = 2 * PI * k / 12
            val p0 = c.project(0.0, 0.0, 0.0)
            val p1 = c.project(cos(a) * 350.0, sin(a) * 350.0, 0.0)
            if (p0 != null && p1 != null) d.line(p0[0], p0[1], p1[0], p1[1], grid, 1f)
        }

        // corpo central
        val toCenter = -ship.pos
        val dc = toCenter.length
        run {
            val q = toCenter.unit() * vr(dc, kv)
            val p = c.project(q.x, q.y, 0.0) ?: return@run
            val rr = (sim.world.radius / dc * 500.0).toFloat().coerceIn(10f, 120f) * (F / p[2]) / 1.7f
            if (sim.world.star) Planet.drawStar(d, p[0], p[1], rr * 0.6f) else Planet.draw(d, p[0], p[1], rr)
            d.text(p[0], p[1] + rr + 13f, sim.world.planetName, Palette.AMBER_SOFT, 10f, Align.CENTER)
        }

        // corpos, do mais longe ao mais perto da câmera
        data class Item(val b: Body, val q: Vec2, val z: Float)
        val items = ArrayList<Item>()
        for (b in sim.bodies) {
            if (!sim.alive(b)) continue
            val rel = sim.relativePosition(b)
            if (rel.length < 1.0) continue
            val q = rel.unit() * vr(rel.length, kv)
            val p = c.project(q.x, q.y, 0.0) ?: continue
            items.add(Item(b, q, p[2]))
        }
        val tgt = sim.target
        // raio até o alvo
        run {
            val rel = sim.relativePosition(tgt)
            if (sim.alive(tgt) && rel.length > 1.0) {
                val q = rel.unit() * vr(rel.length, kv)
                val p0 = c.project(0.0, 0.0, 0.0)
                val p1 = c.project(q.x, q.y, 0.0)
                if (p0 != null && p1 != null) d.line(p0[0], p0[1], p1[0], p1[1], Palette.AMBER.alpha(0.55f), 1f, dash = 5f)
            }
        }
        for (it in items.sortedByDescending { it.z }) drawBody(d, sim, c, it.b, it.q, t, it.b === tgt)

        // despistadores, mísseis, explosões
        for (f in sim.flares) {
            val rel = f.pos - ship.pos
            val q = rel.unit() * vr(rel.length, kv)
            val p = c.project(q.x, q.y, 0.0) ?: continue
            val tw = 0.7f + 0.3f * sin(t * 25 + f.born).toFloat()
            d.glow(p[0], p[1], 20f, 0xFFFFE9B0.toInt().alpha(0.55f * tw))
            d.circle(p[0], p[1], 2.6f, fill = Palette.PLUME_CORE.alpha(tw))
        }
        for (m in sim.missiles) {
            val rel = m.pos - ship.pos
            if (rel.length < 1.0) continue
            val q = rel.unit() * vr(rel.length, kv)
            val p = c.project(q.x, q.y, 0.0) ?: continue
            val v = (m.vel - ship.vel).let { if (it.length > 5.0) it.unit() else (-rel).unit() }
            val p2 = c.project(q.x + v.x * 5, q.y + v.y * 5, 0.0) ?: continue
            val len = sqrt((p2[0] - p[0]) * (p2[0] - p[0]) + (p2[1] - p[1]) * (p2[1] - p[1])).coerceAtLeast(0.001f)
            val color = if (m.owner == Owner.ENEMY) 0xFFFF5A4A.toInt() else Palette.AMBER
            Fx.missile(d, p[0], p[1], (p2[0] - p[0]) / len, (p2[1] - p[1]) / len, color, t, 1.4f)
            if (m.owner == Owner.ENEMY && m.targetId == "jogador") d.text(p[0] + 12f, p[1] - 8f, "MÍSSIL " + Fmt.distance(rel.length), Palette.RED_SOFT, 9f, bold = true)
        }
        for (b in sim.blasts) {
            val rel = b.pos - ship.pos
            val q = if (rel.length < 1.0) Vec2.ZERO else rel.unit() * vr(rel.length, kv)
            val p = c.project(q.x, q.y, 0.0) ?: continue
            Fx.blast(d, p[0], p[1], (sim.time - b.time).toFloat(), b.kind, 1.2f * (F / p[2]) / 1.7f)
        }

        // a nave do jogador, no centro
        val fac = sim.world.playerFaction
        val phi = ship.angle
        val thrust = ship.burnFraction.toFloat()
        if (sim.world.isMilitary && fac != null) {
            val m = matAt(c, Vec2.ZERO, phi, 0.42)
            if (m != null) Models.cruiser(d, fac.style, fac.argb, c.project(0.0, 0.0, 0.0)!![0], c.project(0.0, 0.0, 0.0)!![1], m, thrust, ship.turnAlpha, t)
        } else {
            val m = matAt(c, Vec2.ZERO, phi, 0.5)
            if (m != null) Models.freighter(d, c.project(0.0, 0.0, 0.0)!![0], c.project(0.0, 0.0, 0.0)!![1], m, thrust, ship.turnAlpha, t)
        }

        // vetor de velocidade relativa ao alvo
        val vRel = sim.relativeVelocity()
        if (vRel.length > 0.5 && sim.alive(tgt)) {
            val u = vRel.unit()
            val p0 = c.project(0.0, 0.0, 0.0)
            val p1 = c.project(u.x * 45, u.y * 45, 0.0)
            if (p0 != null && p1 != null) {
                d.line(p0[0], p0[1], p1[0], p1[1], Palette.GREEN, 2.2f)
                d.circle(p1[0], p1[1], 3f, fill = Palette.GREEN)
                d.text(p1[0] + 6f, p1[1] - 4f, "v " + Fmt.speed(vRel.length), Palette.GREEN, 9f)
            }
        }

        d.ox = 0f; d.oy = 0f
        val deg = Math.toDegrees(cam.yaw).toInt()
        d.text(w / 2, 34f, "3D · giro ${deg}° · inclinação ${Math.toDegrees(cam.pitch).toInt()}° · ×${Fmt.dec(cam.zoom, 1)}", Palette.MUTED, 9f, Align.CENTER)
        Effects.hitFlash(d, sim, w, h)
        d.unclip()
    }

    private fun headingOf(sim: Simulation, b: Body): Double {
        val dyn = sim.dynamics[b.id]
        return if (dyn != null && dyn.mode != AiMode.ORBIT) (sim.ship.pos - dyn.pos).angle else sim.velocity(b).angle
    }

    private fun drawBody(d: Draw, sim: Simulation, c: Cam, b: Body, q: Vec2, t: Double, isTarget: Boolean) {
        val rel = sim.relativePosition(b)
        val dist = rel.length
        val p = c.project(q.x, q.y, 0.0) ?: return
        val pf = F / p[2]
        val color = b.argb
        var r = 8f
        when (b.kind) {
            BodyKind.PLANET -> {
                r = ((b.def.radius / dist) * 500.0).toFloat().coerceIn(6f, 110f) * pf / 1.7f
                Planet.drawGeneric(d, p[0], p[1], r, color, gas = b.def.radius > 2e7)
            }
            BodyKind.STATION -> {
                val vs = (60.0 * sqrt(b.size / 150.0) * sqrt(3000.0 / (dist + 3000.0))).coerceIn(5.0, 40.0)
                val spin = t * 0.25
                var prev: FloatArray? = null
                var minX = p[0]; var maxX = p[0]
                for (k in 0..28) {
                    val a = 2 * PI * k / 28 + spin
                    val pp = c.project(q.x + cos(a) * vs, q.y + sin(a) * vs, 0.0)
                    if (pp != null && prev != null) d.line(prev[0], prev[1], pp[0], pp[1], 0xFF9AA2AD.toInt(), (vs * pf * 0.08f).toFloat().coerceIn(1.5f, 5f))
                    if (pp != null) { minX = minOf(minX, pp[0]); maxX = maxOf(maxX, pp[0]) }
                    prev = pp
                }
                for (k in 0 until 3) {
                    val a = k * PI / 3 + spin
                    val p1 = c.project(q.x + cos(a) * vs, q.y + sin(a) * vs, 0.0)
                    val p2 = c.project(q.x - cos(a) * vs, q.y - sin(a) * vs, 0.0)
                    if (p1 != null && p2 != null) d.line(p1[0], p1[1], p2[0], p2[1], 0xFF6E7682.toInt(), 1.5f)
                }
                d.circle(p[0], p[1], (vs * pf * 0.2).toFloat().coerceIn(2.5f, 14f), fill = 0xFFA9B0BA.toInt(), stroke = color, strokeW = 1.4f)
                r = (maxX - minX) / 2f
            }
            BodyKind.SHIP -> {
                val len = (150.0 * sqrt(b.size / 90.0) * sqrt(3000.0 / (dist + 3000.0))).coerceIn(14.0, 160.0)
                val m = matAt(c, q, headingOf(sim, b), len / 140.0 * 0.75)
                val dyn = sim.dynamics[b.id]
                val attacking = dyn != null && dyn.mode != AiMode.ORBIT
                if (m != null) {
                    Models.cruiser(d, b.faction.style, color, p[0], p[1], m, if (attacking) 0.6f else 0f, 0.0, t)
                    r = len.toFloat() * 0.375f * pf
                }
                when (dyn?.mode) {
                    AiMode.ATTACK -> d.text(p[0], p[1] - r - 16f, "HOSTIL", Palette.RED_SOFT, 9f, Align.CENTER, bold = true)
                    AiMode.ESCORT -> d.text(p[0], p[1] - r - 16f, "ESCOLTA", Palette.GREEN, 9f, Align.CENTER, bold = true)
                    AiMode.BOARD -> d.text(p[0], p[1] - r - 16f, "ABORDAGEM", Palette.RED_SOFT, 9f, Align.CENTER, bold = true)
                    else -> {}
                }
            }
            BodyKind.WRECK -> {
                val m = matAt(c, q, t * 0.15, 0.4)
                if (m != null) Models.wreck(d, color, p[0], p[1], m, t)
                r = 22f * pf / 1.7f
            }
        }
        if (isTarget) {
            val k = r.coerceIn(8f, 90f) + 6f
            val cc = Palette.AMBER
            d.line(p[0] - k, p[1] - k, p[0] - k + 8, p[1] - k, cc, 1.5f); d.line(p[0] - k, p[1] - k, p[0] - k, p[1] - k + 8, cc, 1.5f)
            d.line(p[0] + k, p[1] - k, p[0] + k - 8, p[1] - k, cc, 1.5f); d.line(p[0] + k, p[1] - k, p[0] + k, p[1] - k + 8, cc, 1.5f)
            d.line(p[0] - k, p[1] + k, p[0] - k + 8, p[1] + k, cc, 1.5f); d.line(p[0] - k, p[1] + k, p[0] - k, p[1] + k - 8, cc, 1.5f)
            d.line(p[0] + k, p[1] + k, p[0] + k - 8, p[1] + k, cc, 1.5f); d.line(p[0] + k, p[1] + k, p[0] + k, p[1] + k - 8, cc, 1.5f)
        }
        // corpos filhos (luas, estações) distantes empilhariam rótulos no mesmo ponto: só o alvo e os próximos ganham nome
        if (!(isTarget || b.parent == null || b.kind == BodyKind.SHIP || dist < 5e7)) return
        val ly = (p[1] + r.coerceIn(8f, 90f) + 16f).coerceIn(12f, Layout.SCENE_H - 24f)
        d.text(p[0], ly, if (isTarget) "[ ${b.name} ]" else b.name, color, 10f, Align.CENTER, bold = isTarget)
        d.text(p[0], ly + 11f, Fmt.distance(dist), Palette.INK, 9f, Align.CENTER)
    }
}
