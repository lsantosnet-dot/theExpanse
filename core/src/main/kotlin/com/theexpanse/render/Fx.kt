package com.theexpanse.render

import com.theexpanse.core.BlastKind
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Efeitos animados, todos função pura do tempo de animação [t] (sem estado entre quadros). */
object Fx {
    /** Ruído determinístico em [0, 1). */
    fun hash(i: Int): Float {
        var h = i * -1640531535 // 0x9E3779B1
        h = h xor (h ushr 15)
        h *= 0x2C1B3C6D
        h = h xor (h ushr 12)
        h *= 0x297A2D39
        h = h xor (h ushr 15)
        return (h ushr 8) / 16777216f
    }

    /**
     * Jato de energia azul: camadas em forma de gota (azul → branco) com cintilação e partículas que
     * correm para trás e se apagam. [dx],[dy] é a direção de saída (unitária, na tela).
     */
    fun plume(d: Draw, x: Float, y: Float, dx: Float, dy: Float, length: Float, width: Float, intensity: Float, t: Double) {
        if (intensity < 0.02f) return
        val flick = 1f + 0.07f * sin(t * 55).toFloat() + 0.04f * sin(t * 91 + 1).toFloat()
        val len = length * intensity * flick
        val px = -dy
        val py = dx
        d.glow(x, y, width * 2.4f * (0.5f + 0.5f * intensity), Palette.PLUME_MID.alpha(0.55f * intensity))
        val layers = arrayOf(
            floatArrayOf(1.00f, 1.00f, 0.28f),
            floatArrayOf(0.78f, 0.72f, 0.42f),
            floatArrayOf(0.55f, 0.46f, 0.62f),
            floatArrayOf(0.34f, 0.26f, 0.92f),
        )
        val colors = intArrayOf(Palette.PLUME_OUTER, Palette.PLUME_MID, Palette.PLUME_HOT, Palette.PLUME_CORE)
        for ((i, l) in layers.withIndex()) {
            val ll = len * l[0]
            val hw = width * 0.5f * l[1] * (0.85f + 0.15f * intensity)
            val pts = floatArrayOf(
                x + px * hw, y + py * hw,
                x + dx * ll * 0.30f + px * hw * 1.15f, y + dy * ll * 0.30f + py * hw * 1.15f,
                x + dx * ll, y + dy * ll,
                x + dx * ll * 0.30f - px * hw * 1.15f, y + dy * ll * 0.30f - py * hw * 1.15f,
                x - px * hw, y - py * hw,
            )
            d.poly(pts, fill = colors[i].alpha(l[2] * intensity))
        }
        val n = 18
        for (i in 0 until n) {
            val ph = ((t * 2.2 + i.toDouble() / n + hash(i) * 0.25) % 1.0).toFloat()
            val dist = ph * len * 1.3f
            val lateral = (hash(i + 100) - 0.5f) * width * (0.3f + ph * 0.9f)
            val r = (1f - ph) * width * 0.16f + 0.8f
            val c = if (i % 3 == 0) Palette.PLUME_CORE else if (i % 3 == 1) Palette.PLUME_HOT else Palette.PLUME_MID
            d.circle(x + dx * dist + px * lateral, y + dy * dist + py * lateral, r, c.alpha((1f - ph) * 0.9f * intensity))
        }
    }

    /** Sopro curto de propulsor de manobra. */
    fun rcsPuff(d: Draw, x: Float, y: Float, dx: Float, dy: Float, size: Float, t: Double, seed: Int) {
        val f = 0.55f + 0.45f * sin(t * 40 + seed).toFloat()
        val px = -dy
        val py = dx
        val l = size * (0.7f + 0.5f * f)
        val hw = size * 0.22f
        d.glow(x + dx * l * 0.3f, y + dy * l * 0.3f, size * 0.9f, Palette.PLUME_HOT.alpha(0.5f * f))
        d.poly(
            floatArrayOf(x + px * hw, y + py * hw, x + dx * l, y + dy * l, x - px * hw, y - py * hw),
            fill = Palette.PLUME_CORE.alpha(0.85f * f),
        )
    }

    /** Anel radial de energia (câmera traseira): partículas que saem do bocal e crescem. */
    fun radialBurst(d: Draw, cx: Float, cy: Float, radius: Float, intensity: Float, t: Double) {
        if (intensity < 0.02f) return
        d.glow(cx, cy, radius * 0.9f, Palette.PLUME_MID.alpha(0.55f * intensity))
        val n = 34
        for (i in 0 until n) {
            val ph = ((t * 1.9 + i.toDouble() / n + hash(i + 7) * 0.2) % 1.0).toFloat()
            val ang = hash(i + 300) * 2 * PI.toFloat()
            val r0 = radius * (0.14f + 0.86f * ph)
            val r1 = r0 + 3f + ph * 9f
            val c = if (i % 2 == 0) Palette.PLUME_HOT else Palette.PLUME_MID
            d.line(cx + cos(ang) * r0, cy + sin(ang) * r0, cx + cos(ang) * r1, cy + sin(ang) * r1, c.alpha((1f - ph) * 0.95f * intensity), 1.2f + ph * 1.4f)
        }
        d.glow(cx, cy, radius * 0.45f * (0.5f + 0.5f * intensity), Palette.PLUME_CORE.alpha(0.85f * intensity))
    }

    /** Explosão animada por [age] (s de simulação desde que aconteceu). */
    fun blast(d: Draw, x: Float, y: Float, age: Float, kind: BlastKind, scale: Float = 1f) {
        val life = when (kind) { BlastKind.KILL -> 2.8f; BlastKind.SHIP_HIT -> 1.8f; BlastKind.FLARE -> 3.0f; BlastKind.LAUNCH -> 0.6f; BlastKind.FIZZLE -> 0.7f; BlastKind.INTERCEPT -> 1.2f }
        if (age < 0f || age > life) return
        val p = age / life
        val fade = (1f - p).coerceIn(0f, 1f)
        when (kind) {
            BlastKind.KILL, BlastKind.SHIP_HIT, BlastKind.INTERCEPT -> {
                val big = if (kind == BlastKind.KILL) 1.6f else if (kind == BlastKind.INTERCEPT) 0.6f else 1.0f
                val r = (18f + 70f * (1f - (1f - p) * (1f - p))) * big * scale
                d.glow(x, y, r * 2.2f, 0xFFFF8A2A.toInt().alpha(0.55f * fade))
                d.circle(x, y, r * 0.55f * (1f - p * 0.4f), fill = 0xFFFFD27A.toInt().alpha(0.9f * fade))
                d.circle(x, y, r * 0.28f, fill = Palette.PLUME_CORE.alpha(fade))
                d.circle(x, y, r, stroke = 0xFFFFB44A.toInt().alpha(0.8f * fade), strokeW = 2.5f * (1f - p) + 0.5f)
                for (i in 0 until 14) {
                    val a = hash(i + 40) * 2 * PI.toFloat()
                    val l0 = r * 0.5f
                    val l1 = r * (0.9f + hash(i + 80) * 0.9f)
                    d.line(x + cos(a) * l0, y + sin(a) * l0, x + cos(a) * l1, y + sin(a) * l1, 0xFFFFE2A0.toInt().alpha(0.9f * fade), 1.4f)
                }
            }
            BlastKind.FLARE -> {
                val r = (10f + 28f * p) * scale
                val tw = 0.7f + 0.3f * sin(age * 30).toFloat()
                d.glow(x, y, r * 2.4f, 0xFFFFE9B0.toInt().alpha(0.6f * fade * tw))
                d.circle(x, y, 3.2f * scale, fill = Palette.PLUME_CORE.alpha(fade))
                for (i in 0 until 6) {
                    val a = i * PI.toFloat() / 3 + age * 2f
                    d.line(x, y, x + cos(a) * r, y + sin(a) * r, 0xFFFFE9B0.toInt().alpha(0.8f * fade), 1.2f)
                }
            }
            BlastKind.LAUNCH -> {
                d.glow(x, y, 26f * scale * (0.5f + p), Palette.PLUME_CORE.alpha(0.8f * fade))
                d.circle(x, y, 4f * scale, fill = 0xFFFFD27A.toInt().alpha(fade))
            }
            BlastKind.FIZZLE -> d.glow(x, y, 14f * scale * (0.6f + p), 0xFFFFA860.toInt().alpha(0.5f * fade))
        }
    }

    /** Míssil pequeno com rastro e chama. [dx],[dy] é a direção de voo na tela. */
    fun missile(d: Draw, x: Float, y: Float, dx: Float, dy: Float, color: Int, t: Double, scale: Float = 1f) {
        val px = -dy
        val py = dx
        val l = 9f * scale
        val flick = 0.75f + 0.25f * sin(t * 60).toFloat()
        d.line(x - dx * 4f * scale, y - dy * 4f * scale, x - dx * 40f * scale, y - dy * 40f * scale, color.alpha(0.35f), 2.2f * scale)
        d.line(x - dx * 4f * scale, y - dy * 4f * scale, x - dx * 24f * scale, y - dy * 24f * scale, Palette.PLUME_HOT.alpha(0.7f * flick), 1.4f * scale)
        d.glow(x - dx * 5f * scale, y - dy * 5f * scale, 11f * scale, color.alpha(0.7f * flick))
        d.poly(floatArrayOf(x + dx * l, y + dy * l, x - dx * 4f * scale + px * 2.6f * scale, y - dy * 4f * scale + py * 2.6f * scale, x - dx * 4f * scale - px * 2.6f * scale, y - dy * 4f * scale - py * 2.6f * scale), fill = 0xFFE8ECF2.toInt(), stroke = color, strokeW = 0.8f)
    }
}
