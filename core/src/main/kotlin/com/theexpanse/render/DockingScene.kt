package com.theexpanse.render

import com.theexpanse.content.BodyKind
import com.theexpanse.game.GameSession
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

enum class CinematicKind(val duration: Double) { DOCK(7.2), UNDOCK(4.6), LAND(8.5), TAKEOFF(7.0) }

/**
 * Cena de atracação: a nave faz a aproximação final à baía da estação, alinha com propulsores de manobra,
 * as travas se fecham e a estação gira ao fundo. A de saída é o caminho inverso. Um toque pula.
 */
object DockingScene {
    private const val PORT_X = 528f
    private const val Y = 188f

    private fun ease(u: Float) = 1f - (1f - u.coerceIn(0f, 1f)).let { it * it }
    private fun smooth(u: Float) = u.coerceIn(0f, 1f).let { it * it * (3f - 2f * it) }

    fun draw(d: Draw, g: GameSession, kind: CinematicKind, t: Double, anim: Double): List<Button> {
        if (kind == CinematicKind.LAND || kind == CinematicKind.TAKEOFF) return LandingScene.draw(d, g, kind, t, anim)
        val ui = Buttons(d)
        val u = (t / kind.duration).toFloat().coerceIn(0f, 1f)
        val station = g.sim.dockedAt ?: g.world.bodies.first()
        val color = station.faction.argb
        val dock = kind == CinematicKind.DOCK

        d.rect(0f, 0f, Layout.W, Layout.H, fill = Palette.BG)
        // estrelas à deriva e o planeta ao fundo
        for (i in 0 until 110) {
            val x = ((Fx.hash(i * 3) * Layout.W - anim * (4f + Fx.hash(i + 9) * 10f)) % Layout.W + Layout.W) % Layout.W
            d.circle(x.toFloat(), Fx.hash(i * 3 + 1) * Layout.H, 0.6f + Fx.hash(i * 3 + 2), fill = 0xFFC9D3E6.toInt().alpha(0.7f))
        }
        d.clip(0f, 0f, Layout.W, Layout.H)
        Planet.draw(d, 90f, 520f, 300f)

        drawStation(d, station.kind, color, station.name, anim)

        // posição da nave: aproximação (0–0.40), alinhamento (0.40–0.65), contato (0.65–0.82)
        val shipS = if (g.world.isMilitary) 1.45f else 2.0f
        val noseUnits = if (g.world.isMilitary) 70f else 52f
        val dockX = PORT_X - noseUnits * shipS - 6f
        val x: Float
        var thrust = 0f
        var rcsA = 0.0
        var y = Y
        var phi = 0.0
        val relSpeed: Float
        if (dock) {
            when {
                u < 0.40f -> { val p = ease(u / 0.40f); x = -150f + (dockX - 120f + 150f) * p; thrust = (0.9f * (1f - p)).coerceAtLeast(0f); relSpeed = 16f * (1f - p) + 1.2f }
                u < 0.65f -> { val p = smooth((u - 0.40f) / 0.25f); x = dockX - 120f + 100f * p; relSpeed = 1.2f - 0.9f * p
                    val k = 1f - p; y = Y + 15f * sin((u - 0.4f) * 38f) * k; phi = 0.07 * sin((u - 0.4f) * 30f) * k
                    rcsA = if (sin((u - 0.4f) * 38f) > 0) 0.5 else -0.5 }
                else -> { val p = smooth((u - 0.65f) / 0.17f); x = dockX - 20f + 20f * p; relSpeed = 0.3f * (1f - p) }
            }
        } else {
            when {
                u < 0.28f -> { x = dockX; relSpeed = 0f }
                u < 0.62f -> { val p = smooth((u - 0.28f) / 0.34f); x = dockX - 90f * p; relSpeed = 0.9f * p; rcsA = if (p < 0.5f) -0.5 else 0.5 }
                else -> { val p = (u - 0.62f) / 0.38f; x = dockX - 90f - 600f * p * p; thrust = (0.35f + 0.65f * p).coerceAtMost(1f); relSpeed = 3f + 40f * p }
            }
        }
        // linha de aproximação e luzes-guia
        d.line(-20f, Y, PORT_X, Y, Palette.GREEN.alpha(0.25f), 1f, dash = 6f)
        for (i in 0 until 8) {
            val lx = PORT_X - 30f - i * 46f
            val on = ((anim * 5).toInt() % 8) == (7 - i)
            d.circle(lx, Y + 36f, 3f, fill = if (on) Palette.GREEN else Palette.GREEN.alpha(0.25f))
            d.circle(lx, Y - 36f, 3f, fill = if (on) Palette.GREEN else Palette.GREEN.alpha(0.25f))
        }

        // travas (fecham no contato, abrem na saída)
        val clamp = if (dock) smooth((u - 0.66f) / 0.14f) else 1f - smooth(u / 0.22f)
        drawClamps(d, clamp, color)

        val fac = g.world.playerFaction
        if (g.world.isMilitary && fac != null) Models.cruiser(d, fac.style, fac.argb, x, y, Mat.rot(phi, shipS), thrust, rcsA, anim)
        else Models.freighter(d, x, y, Mat.rot(phi, shipS), thrust, rcsA, anim)
        // retropropulsores do nariz na frenagem final
        if (dock && u in 0.12f..0.44f) {
            val nx = x + noseUnits * shipS
            Fx.rcsPuff(d, nx, y - 12f, 1f, 0f, 22f, anim, 5)
            Fx.rcsPuff(d, nx, y + 12f, 1f, 0f, 22f, anim, 6)
        }

        // texto de estágio, telemetria e rádio do controle de tráfego
        val stage = if (dock) when {
            u < 0.40f -> "APROXIMAÇÃO FINAL"
            u < 0.65f -> "ALINHANDO COM A BAÍA 3"
            u < 0.85f -> "TRAVAS ACOPLANDO"
            else -> "ATRACADO"
        } else when {
            u < 0.28f -> "TRAVAS LIBERADAS"
            u < 0.62f -> "AFASTANDO DA BAÍA"
            else -> "CORREDOR DE SAÍDA LIVRE"
        }
        d.text(24f, 34f, stage, Palette.AMBER, 16f, bold = true)
        d.text(24f, 52f, station.name + " · " + station.faction.name, color, 10f)
        val gap = ((PORT_X - (x + noseUnits * shipS)) / (4.4f * shipS)).coerceAtLeast(0f)
        if (dock && u < 0.85f) d.text(24f, 68f, "distância ${Fmt.dec(gap.toDouble(), 1)} m · vel. rel. ${Fmt.dec(relSpeed.toDouble(), 1)} m/s", Palette.MUTED, 10f)
        val lines = if (dock) listOf(
            0.04f to "Controle de tráfego: ${station.shortName}, corredor 3 liberado.",
            0.42f to "Controle de tráfego: alinhamento bom. Mantenha 0,3 m/s.",
            0.70f to "Travas: acoplamento confirmado. Bem-vindo a bordo.",
        ) else listOf(
            0.02f to "Travas: liberando a nave.",
            0.34f to "Controle de tráfego: ${station.shortName}, rumo livre. Bom voo.",
        )
        d.rect(0f, Layout.H - 76f, 590f, 76f, fill = Palette.BG.alpha(0.72f))
        var ly = Layout.H - 40f
        for ((at, text) in lines.reversed()) if (u >= at) {
            d.text(24f, ly, Ui.clip(text, 10f, 560f), Palette.AMBER_SOFT.alpha(if (u - at < 0.05f) (u - at) / 0.05f else 1f), 10f)
            ly -= 14f
        }
        // barra de progresso
        d.rect(24f, Layout.H - 18f, 520f, 4f, fill = Palette.BUTTON, radius = 2f)
        d.rect(24f, Layout.H - 18f, 520f * u, 4f, fill = Palette.AMBER, radius = 2f)

        // fade de entrada e saída
        val fade = (1f - (u / 0.05f)).coerceIn(0f, 1f).coerceAtLeast(((u - 0.94f) / 0.06f).coerceIn(0f, 1f))
        if (fade > 0f) d.rect(0f, 0f, Layout.W, Layout.H, fill = 0xFF000000.toInt().alpha(fade))
        d.unclip()
        ui.add("skip", Layout.W - 104f, 10f, 92f, 34f, "PULAR")
        return ui.list
    }

    private fun drawStation(d: Draw, kind: BodyKind, color: Int, name: String, anim: Double) {
        if (kind == BodyKind.WRECK) {
            Models.wreck(d, color, 720f, Y, Mat.rot(anim * 0.05 + 3.1, 2.1f), anim)
            d.rect(PORT_X, Y - 14f, 110f, 28f, fill = 0xFF39414C.toInt(), stroke = Palette.HULL_EDGE)
            d.text(700f, Layout.H - 12f, name, color, 11f, Align.CENTER, bold = true)
            return
        }
        val cx = 720f
        val ringR = 150f
        d.glow(cx, Y, 230f, color.alpha(0.10f))
        d.circle(cx, Y, ringR, stroke = 0xFF8C949F.toInt(), strokeW = 14f)
        d.circle(cx, Y, ringR, stroke = 0xFF3B414A.toInt(), strokeW = 2f)
        for (k in 0 until 6) {
            val a = k * PI.toFloat() / 3 + (anim * 0.25).toFloat()
            d.line(cx, Y, cx + cos(a) * ringR, Y + sin(a) * ringR, 0xFF6E7682.toInt(), 6f)
            d.circle(cx + cos(a) * ringR, Y + sin(a) * ringR, 3f, fill = 0xFFF2D27A.toInt())
        }
        // braço de atracação até a baía
        d.rect(PORT_X + 8f, Y - 17f, cx - PORT_X - 40f, 34f, fill = 0xFF5B6470.toInt(), stroke = Palette.HULL_EDGE)
        for (k in 0 until 5) d.line(PORT_X + 30f + k * 28f, Y - 17f, PORT_X + 30f + k * 28f, Y + 17f, Palette.HULL_EDGE, 1.5f)
        d.circle(cx, Y, 52f, fill = 0xFFA9B0BA.toInt(), stroke = color, strokeW = 3f)
        d.circle(cx, Y, 20f, fill = 0xFF39414C.toInt())
        // moldura da baía
        d.poly(floatArrayOf(PORT_X - 4f, Y - 34f, PORT_X + 14f, Y - 28f, PORT_X + 14f, Y + 28f, PORT_X - 4f, Y + 34f), fill = 0xFF39414C.toInt(), stroke = color, strokeW = 2f)
        d.text(cx, Layout.H - 12f, name, color, 11f, Align.CENTER, bold = true)
    }

    private fun drawClamps(d: Draw, closed: Float, color: Int) {
        val open = 46f
        val gap = open - (open - 20f) * closed
        for (sg in listOf(-1f, 1f)) {
            val y = Y + sg * gap
            d.poly(floatArrayOf(PORT_X - 4f, Y + sg * 30f, PORT_X - 40f, y, PORT_X - 40f, y + sg * 8f, PORT_X - 4f, Y + sg * 36f), fill = 0xFF7A838F.toInt(), stroke = Palette.HULL_EDGE)
            if (closed > 0.9f) d.circle(PORT_X - 40f, y + sg * 4f, 2.4f, fill = Palette.GREEN)
        }
        if (closed > 0.9f) d.glow(PORT_X - 22f, Y, 40f, color.alpha(0.25f))
    }
}
