package com.theexpanse.render

import com.theexpanse.content.HullStyle
import kotlin.math.abs

/**
 * Modelos de nave vistos de cima, com o nariz para +x (comprimento de ~130 unidades). [phi] é o ângulo na tela
 * (anti-horário), [scale] multiplica tudo. Cruzadores têm desenho próprio por facção e lançadores de mísseis.
 */
object Models {
    private fun hullTint(argb: Int, base: Int) = mix(base, argb, 0.35f)

    private class Pt(val x: Float, val y: Float)

    private fun place(local: FloatArray, cx: Float, cy: Float, phi: Double, scale: Float) = placePoints(local, cx, cy, phi, scale)

    private fun at(x: Float, y: Float, cx: Float, cy: Float, phi: Double, scale: Float): FloatArray = placePoints(floatArrayOf(x, y), cx, cy, phi, scale)

    /** Direção "para trás" do nariz na tela. */
    private fun back(phi: Double): FloatArray = placePoints(floatArrayOf(-1f, 0f), 0f, 0f, phi, 1f)

    /** Desenha os motores e o jato azul de [engines] (posições locais). */
    private fun engines(d: Draw, engines: List<Pt>, cx: Float, cy: Float, phi: Double, scale: Float, thrust: Float, t: Double, plumeLen: Float, plumeW: Float) {
        val b = back(phi)
        for (e in engines) {
            val p = at(e.x, e.y, cx, cy, phi, scale)
            Fx.plume(d, p[0], p[1], b[0], b[1], plumeLen * scale, plumeW * scale, thrust, t)
            if (thrust > 0.02f) d.glow(p[0], p[1], 7f * scale, Palette.PLUME_HOT.alpha(0.9f * thrust))
        }
    }

    private fun rcs(d: Draw, alpha: Double, noseX: Float, tailX: Float, half: Float, cx: Float, cy: Float, phi: Double, scale: Float, t: Double) {
        if (abs(alpha) < 1e-6) return
        val sg = if (alpha > 0) 1f else -1f
        val nose = at(noseX, -sg * half, cx, cy, phi, scale)
        val tail = at(tailX, sg * half, cx, cy, phi, scale)
        val dn = placePoints(floatArrayOf(0f, -sg), 0f, 0f, phi, 1f)
        val dt = placePoints(floatArrayOf(0f, sg), 0f, 0f, phi, 1f)
        Fx.rcsPuff(d, nose[0], nose[1], dn[0], dn[1], 17f * scale, t, 1)
        Fx.rcsPuff(d, tail[0], tail[1], dt[0], dt[1], 17f * scale, t, 2)
    }

    /** O cargueiro do jogador mercador. */
    fun freighter(d: Draw, cx: Float, cy: Float, phi: Double, scale: Float, thrust: Float, turnAlpha: Double, t: Double) {
        val back = back(phi)
        val noz = at(-64f, 0f, cx, cy, phi, scale)
        Fx.plume(d, noz[0], noz[1], back[0], back[1], 130f * scale, 26f * scale, thrust, t)
        d.poly(place(floatArrayOf(-30f, -34f, -2f, -34f, 4f, -13f, -34f, -13f), cx, cy, phi, scale), fill = 0xFF2D4C7A.toInt(), stroke = Palette.HULL_EDGE)
        d.poly(place(floatArrayOf(-30f, 34f, -2f, 34f, 4f, 13f, -34f, 13f), cx, cy, phi, scale), fill = 0xFF2D4C7A.toInt(), stroke = Palette.HULL_EDGE)
        d.poly(place(floatArrayOf(-64f, -10f, -50f, -10f, -50f, 10f, -64f, 10f), cx, cy, phi, scale), fill = 0xFF4F5762.toInt(), stroke = Palette.HULL_EDGE)
        d.poly(place(floatArrayOf(-50f, -13f, 28f, -13f, 52f, -6f, 52f, 6f, 28f, 13f, -50f, 13f), cx, cy, phi, scale), fill = Palette.HULL, stroke = Palette.HULL_EDGE, strokeW = 1.2f)
        d.poly(place(floatArrayOf(-34f, -8f, -4f, -8f, -4f, 8f, -34f, 8f), cx, cy, phi, scale), fill = Palette.AMBER.alpha(0.9f))
        d.poly(place(floatArrayOf(34f, -5f, 48f, -3f, 48f, 3f, 34f, 5f), cx, cy, phi, scale), fill = 0xFF5CC8E8.toInt())
        if (thrust > 0.02f) d.glow(noz[0], noz[1], 14f * scale, Palette.PLUME_HOT.alpha(0.9f * thrust))
        rcs(d, turnAlpha, 44f, -48f, 12f + 2f, cx, cy, phi, scale, t)
    }

    /** Cruzador estelar de uma facção. Comprimento ~140 unidades; lançadores de mísseis nas laterais. */
    fun cruiser(d: Draw, style: HullStyle, argb: Int, cx: Float, cy: Float, phi: Double, scale: Float, thrust: Float, turnAlpha: Double, t: Double, armed: Boolean = true, launching: Float = 0f) {
        when (style) {
            HullStyle.TERRAN -> terran(d, argb, cx, cy, phi, scale, thrust, t)
            HullStyle.MARTIAN -> martian(d, argb, cx, cy, phi, scale, thrust, t)
            HullStyle.BELT -> belt(d, argb, cx, cy, phi, scale, thrust, t)
            HullStyle.GENERIC -> generic(d, argb, cx, cy, phi, scale, thrust, t)
        }
        rcs(d, turnAlpha, 60f, -60f, 14f, cx, cy, phi, scale, t)
        if (launching > 0.02f) {
            val p = at(20f, 0f, cx, cy, phi, scale)
            d.glow(p[0], p[1], 26f * scale, Palette.PLUME_CORE.alpha(0.8f * launching))
        }
        // luzes de navegação piscando
        val blink = (kotlin.math.sin(t * 5.0) > 0.4)
        val l = at(-30f, -20f, cx, cy, phi, scale)
        val r = at(-30f, 20f, cx, cy, phi, scale)
        if (blink) { d.circle(l[0], l[1], 1.6f * scale.coerceAtLeast(0.6f), fill = Palette.RED); d.circle(r[0], r[1], 1.6f * scale.coerceAtLeast(0.6f), fill = Palette.GREEN) }
    }

    // Coalizão Terrana: casco longo em lâmina, torre de comando, quatro casulos de mísseis, três motores.
    private fun terran(d: Draw, argb: Int, cx: Float, cy: Float, phi: Double, s: Float, thrust: Float, t: Double) {
        val steel = hullTint(argb, 0xFFC9D2DE.toInt())
        val dark = hullTint(argb, 0xFF56606E.toInt())
        engines(d, listOf(Pt(-72f, -6f), Pt(-72f, 0f), Pt(-72f, 6f)), cx, cy, phi, s, thrust, t, 120f, 11f)
        // casulos de mísseis e pilones
        for (sg in listOf(-1f, 1f)) {
            d.poly(place(floatArrayOf(-34f, sg * 9f, -4f, sg * 9f, -14f, sg * 22f, -44f, sg * 22f), cx, cy, phi, s), fill = dark, stroke = Palette.HULL_EDGE)
            for (k in 0 until 2) {
                val x0 = -40f + k * 18f
                d.poly(place(floatArrayOf(x0, sg * 22f, x0 + 14f, sg * 22f, x0 + 14f, sg * 29f, x0, sg * 29f), cx, cy, phi, s), fill = 0xFF2A323E.toInt(), stroke = Palette.HULL_EDGE)
                d.poly(place(floatArrayOf(x0 + 14f, sg * 24f, x0 + 18f, sg * 25.5f, x0 + 14f, sg * 27f), cx, cy, phi, s), fill = Palette.RED)
            }
        }
        d.poly(place(floatArrayOf(-72f, -10f, 30f, -10f, 60f, -4f, 70f, 0f, 60f, 4f, 30f, 10f, -72f, 10f), cx, cy, phi, s), fill = steel, stroke = Palette.HULL_EDGE, strokeW = 1.2f)
        d.poly(place(floatArrayOf(-70f, -3f, 58f, -1f, 58f, 1f, -70f, 3f), cx, cy, phi, s), fill = dark)
        d.poly(place(floatArrayOf(-10f, -6f, 22f, -6f, 22f, 6f, -10f, 6f), cx, cy, phi, s), fill = argb.alpha(0.85f), stroke = Palette.HULL_EDGE)
        d.poly(place(floatArrayOf(6f, -4f, 18f, -3f, 18f, 3f, 6f, 4f), cx, cy, phi, s), fill = 0xFF5CC8E8.toInt())
        d.poly(place(floatArrayOf(-72f, -8f, -60f, -8f, -60f, 8f, -72f, 8f), cx, cy, phi, s), fill = 0xFF3A424E.toInt(), stroke = Palette.HULL_EDGE)
    }

    // Federação Marciana: cunha larga e angulosa, placas vermelhas, dois motores grandes, rack de mísseis dianteiro.
    private fun martian(d: Draw, argb: Int, cx: Float, cy: Float, phi: Double, s: Float, thrust: Float, t: Double) {
        val plate = hullTint(argb, 0xFFB9777A.toInt())
        val dark = hullTint(argb, 0xFF5A3438.toInt())
        engines(d, listOf(Pt(-66f, -11f), Pt(-66f, 11f)), cx, cy, phi, s, thrust, t, 110f, 15f)
        d.poly(place(floatArrayOf(-62f, -22f, -20f, -24f, 4f, -16f, -20f, -14f), cx, cy, phi, s), fill = dark, stroke = Palette.HULL_EDGE)
        d.poly(place(floatArrayOf(-62f, 22f, -20f, 24f, 4f, 16f, -20f, 14f), cx, cy, phi, s), fill = dark, stroke = Palette.HULL_EDGE)
        d.poly(place(floatArrayOf(-66f, -17f, 24f, -14f, 66f, 0f, 24f, 14f, -66f, 17f), cx, cy, phi, s), fill = plate, stroke = Palette.HULL_EDGE, strokeW = 1.2f)
        d.poly(place(floatArrayOf(-40f, -10f, 20f, -8f, 44f, 0f, 20f, 8f, -40f, 10f), cx, cy, phi, s), fill = dark)
        d.poly(place(floatArrayOf(-30f, -5f, 6f, -4f, 6f, 4f, -30f, 5f), cx, cy, phi, s), fill = argb.alpha(0.9f))
        for (sg in listOf(-1f, 1f)) for (k in 0 until 3) {
            val x0 = -10f + k * 11f
            d.poly(place(floatArrayOf(x0, sg * 14f, x0 + 8f, sg * 13f, x0 + 8f, sg * 19f, x0, sg * 20f), cx, cy, phi, s), fill = 0xFF2A1E20.toInt(), stroke = Palette.HULL_EDGE)
            d.circle(at(x0 + 9f, sg * 16f, cx, cy, phi, s)[0], at(x0 + 9f, sg * 16f, cx, cy, phi, s)[1], 1.5f * s, fill = Palette.AMBER)
        }
        d.poly(place(floatArrayOf(50f, -3f, 62f, -1f, 62f, 1f, 50f, 3f), cx, cy, phi, s), fill = 0xFFFFD27A.toInt().alpha(0.8f))
    }

    // Liga do Cinturão: módulos improvisados, tanques externos e treliça; casco assimétrico.
    private fun belt(d: Draw, argb: Int, cx: Float, cy: Float, phi: Double, s: Float, thrust: Float, t: Double) {
        val metal = hullTint(argb, 0xFFB7C7C6.toInt())
        val dark = hullTint(argb, 0xFF4A5B5A.toInt())
        engines(d, listOf(Pt(-66f, -5f), Pt(-66f, 8f)), cx, cy, phi, s, thrust, t, 110f, 12f)
        d.line(at(-40f, -22f, cx, cy, phi, s)[0], at(-40f, -22f, cx, cy, phi, s)[1], at(30f, 18f, cx, cy, phi, s)[0], at(30f, 18f, cx, cy, phi, s)[1], Palette.HULL_EDGE, 1.5f * s)
        for ((i, y) in listOf(-20f, 18f).withIndex()) {
            val x0 = if (i == 0) -48f else -20f
            d.poly(place(floatArrayOf(x0, y - 5f, x0 + 52f, y - 5f, x0 + 58f, y, x0 + 52f, y + 5f, x0, y + 5f), cx, cy, phi, s), fill = dark, stroke = Palette.HULL_EDGE)
            d.poly(place(floatArrayOf(x0 + 6f, y - 3f, x0 + 44f, y - 3f, x0 + 44f, y + 3f, x0 + 6f, y + 3f), cx, cy, phi, s), fill = metal.alpha(0.6f))
        }
        d.poly(place(floatArrayOf(-62f, -9f, 30f, -8f, 62f, 0f, 30f, 7f, -62f, 9f), cx, cy, phi, s), fill = metal, stroke = Palette.HULL_EDGE, strokeW = 1.2f)
        d.poly(place(floatArrayOf(-30f, -9f, -14f, -9f, -14f, 9f, -30f, 9f), cx, cy, phi, s), fill = argb.alpha(0.85f))
        d.poly(place(floatArrayOf(34f, -3f, 52f, -1f, 52f, 1f, 34f, 3f), cx, cy, phi, s), fill = 0xFF5CC8E8.toInt())
        for (k in 0 until 2) {
            val x0 = 4f + k * 14f
            d.poly(place(floatArrayOf(x0, 9f, x0 + 10f, 9f, x0 + 10f, 14f, x0, 14f), cx, cy, phi, s), fill = 0xFF1F2A2A.toInt(), stroke = Palette.HULL_EDGE)
            d.poly(place(floatArrayOf(x0 + 10f, 10.5f, x0 + 14f, 11.5f, x0 + 10f, 12.5f), cx, cy, phi, s), fill = Palette.RED)
        }
    }

    private fun generic(d: Draw, argb: Int, cx: Float, cy: Float, phi: Double, s: Float, thrust: Float, t: Double) {
        val metal = hullTint(argb, 0xFFC4C9D0.toInt())
        engines(d, listOf(Pt(-62f, -5f), Pt(-62f, 5f)), cx, cy, phi, s, thrust, t, 100f, 11f)
        d.poly(place(floatArrayOf(-62f, -11f, 28f, -10f, 60f, 0f, 28f, 10f, -62f, 11f), cx, cy, phi, s), fill = metal, stroke = Palette.HULL_EDGE, strokeW = 1.2f)
        d.poly(place(floatArrayOf(-30f, -6f, 10f, -5f, 10f, 5f, -30f, 6f), cx, cy, phi, s), fill = argb.alpha(0.85f))
        d.poly(place(floatArrayOf(34f, -3f, 50f, -1f, 50f, 1f, 34f, 3f), cx, cy, phi, s), fill = 0xFF5CC8E8.toInt())
    }

    /** Destroços de um cargueiro: casco partido, peças soltas e faíscas. */
    fun wreck(d: Draw, argb: Int, cx: Float, cy: Float, phi: Double, scale: Float, t: Double) {
        d.poly(place(floatArrayOf(-50f, -13f, -8f, -13f, -2f, -4f, -10f, 2f, -4f, 13f, -50f, 13f), cx, cy, phi, scale), fill = 0xFF4A505A.toInt(), stroke = Palette.HULL_EDGE, strokeW = 1.2f)
        d.poly(place(floatArrayOf(6f, -8f, 30f, -12f, 52f, -4f, 48f, 8f, 20f, 6f), cx, cy + 0f, phi + 0.35, scale), fill = 0xFF5A606A.toInt(), stroke = Palette.HULL_EDGE)
        d.poly(place(floatArrayOf(-30f, 20f, -14f, 18f, -12f, 26f, -28f, 28f), cx, cy, phi, scale), fill = 0xFF3A4048.toInt())
        d.poly(place(floatArrayOf(-30f, -8f, -4f, -8f, -4f, 8f, -30f, 8f), cx, cy, phi, scale), fill = Palette.AMBER.alpha(0.35f))
        val sp = (kotlin.math.sin(t * 6) > 0.5)
        if (sp) d.glow(at(-3f, 0f, cx, cy, phi, scale)[0], at(-3f, 0f, cx, cy, phi, scale)[1], 12f * scale, 0xFFFFB44A.toInt().alpha(0.6f))
    }
}
