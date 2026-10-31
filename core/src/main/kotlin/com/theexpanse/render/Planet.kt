package com.theexpanse.render

import kotlin.math.sqrt

/** Aurélia: esfera com bandas, brilho no limbo e luz vinda do alto à esquerda. */
object Planet {
    private val BASE = 0xFF8C4A2C.toInt()
    private val LIGHT = 0xFFE59A6C.toInt()
    private val BAND_DARK = 0xFFB4532F.toInt()
    private val BAND_PALE = 0xFFF0C9A0.toInt()

    /** Faixa horizontal da esfera entre as alturas relativas [y0, y1] (em múltiplos de r), recortada pelo círculo. */
    private fun slice(cx: Float, cy: Float, r: Float, y0: Float, y1: Float): FloatArray {
        val n = 7
        val pts = ArrayList<Float>()
        for (i in 0..n) {
            val y = (y0 + (y1 - y0) * i / n) * r
            val x = sqrt((r * r - y * y).coerceAtLeast(0f))
            pts.add(cx - x); pts.add(cy + y)
        }
        for (i in n downTo 0) {
            val y = (y0 + (y1 - y0) * i / n) * r
            val x = sqrt((r * r - y * y).coerceAtLeast(0f))
            pts.add(cx + x); pts.add(cy + y)
        }
        return pts.toFloatArray()
    }

    fun draw(d: Draw, cx: Float, cy: Float, r: Float) {
        if (r < 1.5f) {
            d.circle(cx, cy, 2.5f, fill = LIGHT)
            return
        }
        d.glow(cx, cy, r * 1.25f, LIGHT.alpha(0.16f))
        d.circle(cx, cy, r, fill = BASE)
        d.circle(cx - r * 0.14f, cy - r * 0.14f, r * 0.86f, fill = BAND_DARK.alpha(0.65f))
        d.circle(cx - r * 0.26f, cy - r * 0.26f, r * 0.5f, fill = LIGHT.alpha(0.30f))
        if (r > 8f) {
            d.poly(slice(cx, cy, r, -0.62f, -0.50f), fill = BAND_PALE.alpha(0.40f))
            d.poly(slice(cx, cy, r, -0.30f, -0.14f), fill = BAND_DARK.alpha(0.75f))
            d.poly(slice(cx, cy, r, 0.06f, 0.20f), fill = BAND_PALE.alpha(0.35f))
            d.poly(slice(cx, cy, r, 0.40f, 0.52f), fill = BAND_DARK.alpha(0.70f))
        }
        d.circle(cx, cy, r, stroke = 0xFFFFD2A8.toInt().alpha(0.35f), strokeW = 1f)
    }

    /** Planeta ou lua genérico: esfera sombreada na cor [argb], com faixas se for grande. */
    fun drawGeneric(d: Draw, cx: Float, cy: Float, r: Float, argb: Int, gas: Boolean = false) {
        if (r < 2.5f) {
            d.circle(cx, cy, 2.5f, fill = argb)
            return
        }
        val dark = mix(argb, 0xFF000000.toInt(), 0.55f)
        val light = mix(argb, 0xFFFFFFFF.toInt(), 0.35f)
        d.glow(cx, cy, r * 1.25f, argb.alpha(0.14f))
        d.circle(cx, cy, r, fill = dark)
        d.circle(cx - r * 0.12f, cy - r * 0.12f, r * 0.88f, fill = argb)
        d.circle(cx - r * 0.28f, cy - r * 0.28f, r * 0.45f, fill = light.alpha(0.35f))
        if (gas && r > 10f) {
            d.poly(slice(cx, cy, r, -0.45f, -0.33f), fill = dark.alpha(0.5f))
            d.poly(slice(cx, cy, r, -0.10f, 0.04f), fill = light.alpha(0.35f))
            d.poly(slice(cx, cy, r, 0.28f, 0.42f), fill = dark.alpha(0.5f))
        }
    }

    /** O Sol: disco branco-amarelo com coroa. */
    fun drawStar(d: Draw, cx: Float, cy: Float, r: Float) {
        d.glow(cx, cy, r * 3.2f, 0xFFFFC857.toInt().alpha(0.30f))
        d.glow(cx, cy, r * 1.8f, 0xFFFFE9A0.toInt().alpha(0.55f))
        d.circle(cx, cy, r.coerceAtLeast(3f), fill = 0xFFFFF4C8.toInt())
        d.circle(cx, cy, (r * 0.72f).coerceAtLeast(2f), fill = 0xFFFFFFFF.toInt().alpha(0.8f))
    }
}
