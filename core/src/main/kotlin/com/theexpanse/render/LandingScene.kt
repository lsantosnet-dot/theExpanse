package com.theexpanse.render

import com.theexpanse.game.GameSession
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * Cena de pouso e decolagem numa superfície: a nave desce de ré sobre a chama do motor, estende o trem de pouso,
 * levanta poeira e se apoia na plataforma. A decolagem é o caminho inverso. Um toque pula.
 * O céu muda com o corpo: com atmosfera tem cor e horizonte claro, sem atmosfera o céu é preto e as estrelas ficam visíveis.
 */
object LandingScene {
    private const val GROUND = 292f
    private const val PAD_X = 470f

    private fun smooth(u: Float) = u.coerceIn(0f, 1f).let { it * it * (3f - 2f * it) }

    fun draw(d: Draw, g: GameSession, kind: CinematicKind, t: Double, anim: Double): List<Button> {
        val ui = Buttons(d)
        val land = kind == CinematicKind.LAND
        val u = (t / kind.duration).toFloat().coerceIn(0f, 1f)
        val body = g.sim.dockedAt ?: g.world.bodies.first()
        val base = body.argb
        val atmo = body.def.atmoScale > 0.0
        val grav = g.sim.surfaceGravity(body)
        // corpos azulados (a cor do corpo é a dos oceanos, como a Terra): o céu usa o azul, o chão é terra e vegetação
        val red = (base shr 16) and 0xFF
        val green = (base shr 8) and 0xFF
        val blue = base and 0xFF
        val terrain = if (blue > red + 20 && blue > green) 0xFF7A8648.toInt() else base

        // céu
        val top = if (atmo) mix(0xFF05080F.toInt(), base, 0.25f) else 0xFF03060C.toInt()
        val low = if (atmo) mix(top, base, 0.75f) else 0xFF0A0E16.toInt()
        val steps = 14
        for (i in 0 until steps) d.rect(0f, i * GROUND / steps, Layout.W, GROUND / steps + 1f, fill = mix(top, low, (i / (steps - 1f)) * (i / (steps - 1f))))
        val starAlpha = if (atmo) 0.35f else 0.95f
        for (i in 0 until 90) d.circle(Fx.hash(i * 3) * Layout.W, Fx.hash(i * 3 + 1) * (GROUND - 40f), 0.5f + Fx.hash(i * 3 + 2) * 0.9f, fill = 0xFFFFFFFF.toInt().alpha(starAlpha * (0.4f + Fx.hash(i + 50))))
        // astro grande no céu (o planeta-mãe do corpo, ou o Sol)
        val parent = body.parent
        if (parent != null && parent.def.radius > 0.0) Planet.drawGeneric(d, 640f, 84f, 42f, parent.argb) else d.glow(780f, 80f, 70f, 0xFFFFE9B0.toInt().alpha(0.5f))

        // relevo distante e chão
        val far = mix(low, terrain, 0.45f).let { mix(it, 0xFF000000.toInt(), 0.35f) }
        d.poly(floatArrayOf(0f, GROUND, 0f, GROUND - 34f, 120f, GROUND - 52f, 260f, GROUND - 30f, 410f, GROUND - 62f, 590f, GROUND - 28f, 740f, GROUND - 48f, Layout.W, GROUND - 26f, Layout.W, GROUND), fill = far)
        val ground = mix(terrain, 0xFF000000.toInt(), 0.45f)
        d.rect(0f, GROUND, Layout.W, Layout.H - GROUND, fill = ground)
        d.rect(0f, GROUND, Layout.W, 3f, fill = mix(terrain, 0xFFFFFFFF.toInt(), 0.25f).alpha(0.7f))
        for (i in 0 until 16) d.rect(Fx.hash(i * 7) * Layout.W, GROUND + 14f + Fx.hash(i * 7 + 1) * 70f, 30f + Fx.hash(i * 7 + 2) * 60f, 3f, fill = mix(ground, 0xFF000000.toInt(), 0.35f))

        // base: cúpula, torre e plataforma de pouso
        d.poly(floatArrayOf(120f, GROUND, 130f, GROUND - 26f, 160f, GROUND - 42f, 200f, GROUND - 42f, 230f, GROUND - 26f, 240f, GROUND), fill = 0xFF9FD0E8.toInt().alpha(0.30f), stroke = 0xFFCFE9F5.toInt().alpha(0.7f), strokeW = 2f)
        d.rect(716f, GROUND - 92f, 8f, 92f, fill = 0xFF4B5667.toInt())
        d.rect(706f, GROUND - 106f, 28f, 16f, fill = 0xFF2C3646.toInt(), stroke = 0xFF7FD4FF.toInt())
        d.circle(720f, GROUND - 112f, 3f, fill = if ((anim * 2).toInt() % 2 == 0) Palette.RED else Palette.RED.alpha(0.3f))
        d.poly(floatArrayOf(PAD_X - 105f, GROUND + 14f, PAD_X - 85f, GROUND, PAD_X + 85f, GROUND, PAD_X + 105f, GROUND + 14f), fill = 0xFF2C3646.toInt(), stroke = 0xFF8B95A3.toInt(), strokeW = 2f)
        d.text(PAD_X, GROUND + 11f, body.shortName.uppercase().take(10) + " · A-1", Palette.AMBER, 8f, Align.CENTER, bold = true)
        for (i in 0 until 8) {
            val lx = PAD_X - 84f + i * 24f
            val on = ((anim * 5).toInt() % 8) == i
            d.circle(lx, GROUND + 2f, 2.4f, fill = if (on) Palette.GREEN else Palette.GREEN.alpha(0.3f))
        }

        // nave: altura do centro e empuxo ao longo da cena
        val shipS = if (g.world.isMilitary) 1.1f else 1.35f
        val half = (if (g.world.isMilitary) 70f else 52f) * shipS // metade do comprimento
        val legs = 9f
        val restY = GROUND - half - legs
        val startY = -half - 40f
        val h: Float // 0 = no chão, 1 = alto
        var thrust = 0f
        val legOut: Float
        val speed: Float
        if (land) {
            when {
                u < 0.62f -> { val p = u / 0.62f; h = (1f - p).let { it * it * 0.35f + it * 0.65f }; thrust = 0.35f + 0.5f * p; legOut = smooth((p - 0.55f) / 0.35f); speed = 60f * (1f - p) + 3f }
                u < 0.80f -> { val p = (u - 0.62f) / 0.18f; h = 0.02f * (1f - p); thrust = 0.55f * (1f - p); legOut = 1f; speed = 2.2f * (1f - p) }
                else -> { h = 0f; thrust = 0f; legOut = 1f; speed = 0f }
            }
        } else {
            when {
                u < 0.25f -> { h = 0f; thrust = 0.3f * (u / 0.25f); legOut = 1f; speed = 0f }
                u < 0.6f -> { val p = (u - 0.25f) / 0.35f; h = 0.03f * smooth(p); thrust = 0.6f + 0.2f * p; legOut = 1f - smooth((p - 0.5f) / 0.5f); speed = 6f * p }
                else -> { val p = (u - 0.6f) / 0.4f; h = 0.03f + 1.1f * p * p; thrust = 1f; legOut = 0f; speed = 6f + 140f * p * p }
            }
        }
        val y = restY - (restY - startY) * h.coerceAtMost(1.2f)

        // poeira e clarão do motor no chão
        val near = (1f - ((restY - y) / 170f)).coerceIn(0f, 1f)
        if (thrust > 0f && near > 0f) {
            val spread = 40f + 150f * near
            d.glow(PAD_X, GROUND, spread, 0xFFFFC27A.toInt().alpha(0.35f * near * thrust))
            for (i in 0 until 14) {
                val side = if (i % 2 == 0) -1f else 1f
                val dist = (20f + Fx.hash(i * 5) * 190f * near) * (0.6f + 0.4f * sin((anim * 3 + i).toFloat()))
                val dy = -Fx.hash(i * 5 + 1) * 14f * near
                d.circle(PAD_X + side * dist, GROUND + dy, 6f + Fx.hash(i * 5 + 2) * 12f * near, fill = mix(terrain, 0xFFFFFFFF.toInt(), 0.35f).alpha(0.22f * near * (if (atmo) 1f else 0.7f)))
            }
        }

        // trem de pouso
        val lo = legOut
        if (lo > 0f) for (sg in listOf(-1f, 1f)) {
            val topY = y + half * 0.55f
            val footX = PAD_X + sg * (12f + 24f * lo)
            d.line(PAD_X + sg * 8f, topY, footX, y + half + legs * lo, 0xFF8B95A3.toInt(), 3f)
            d.rect(footX - 6f, y + half + legs * lo - 1f, 12f, 3f, fill = 0xFF8B95A3.toInt())
        }
        if (g.world.isMilitary && g.world.playerFaction != null) {
            val fac = g.world.playerFaction!!
            Models.cruiser(d, fac.style, fac.argb, PAD_X, y, Mat.rot(PI / 2, shipS), thrust, 0.0, anim)
        } else Models.freighter(d, PAD_X, y, Mat.rot(PI / 2, shipS), thrust, 0.0, anim)

        // telemetria e rádio
        val alt = ((restY - y).coerceAtLeast(0f) / (restY - startY) * 400f)
        val title = if (land) when {
            u < 0.55f -> "DESCIDA FINAL"
            u < 0.80f -> "POUSANDO"
            else -> "POUSADO"
        } else when {
            u < 0.25f -> "PRÉ-VOO"
            u < 0.6f -> "DECOLANDO"
            else -> "SUBIDA"
        }
        d.text(24f, 34f, title, Palette.AMBER, 16f, bold = true)
        d.text(24f, 52f, body.name + " · plataforma A-1", body.argb, 10f)
        if (u < 0.95f) d.text(24f, 68f, "alt ${Fmt.dec(alt.toDouble(), 0)} m · vel. vertical ${Fmt.dec(speed.toDouble(), 1)} m/s · g ${Fmt.dec(grav, 2)} m/s²", Palette.MUTED, 10f)
        val lines = if (land) listOf(
            0.04f to "Piloto automático: descida assistida, motor em retropropulsão.",
            0.50f to "Trem de pouso estendido.",
            0.78f to "Contato. Motor desligado. Bem-vindo a ${body.name}.",
        ) else listOf(
            0.02f to "Piloto automático: verificando sistemas, ignição.",
            0.34f to "Empuxo acima da gravidade (${Fmt.dec(g.sim.accelLimit, 1)} contra ${Fmt.dec(grav, 2)} m/s²): decolando.",
            0.62f to "Trem recolhido. Subindo para a órbita.",
        )
        d.rect(0f, Layout.H - 76f, 590f, 76f, fill = Palette.BG.alpha(0.72f))
        var ly = Layout.H - 40f
        for ((at, text) in lines.reversed()) if (u >= at) {
            d.text(24f, ly, Ui.clip(text, 10f, 560f), Palette.AMBER_SOFT.alpha(if (u - at < 0.05f) (u - at) / 0.05f else 1f), 10f)
            ly -= 14f
        }
        d.rect(24f, Layout.H - 18f, 520f, 4f, fill = Palette.BUTTON, radius = 2f)
        d.rect(24f, Layout.H - 18f, 520f * u, 4f, fill = Palette.AMBER, radius = 2f)
        val fade = (1f - (u / 0.05f)).coerceIn(0f, 1f).coerceAtLeast(((u - 0.94f) / 0.06f).coerceIn(0f, 1f))
        if (fade > 0f) d.rect(0f, 0f, Layout.W, Layout.H, fill = 0xFF000000.toInt().alpha(fade))
        ui.add("skip", Layout.W - 104f, 10f, 92f, 34f, "PULAR")
        return ui.list
    }
}
