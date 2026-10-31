package com.theexpanse.render

enum class ButtonKind { TAP, HOLD }

class Button(
    val id: String,
    val x: Float, val y: Float, val w: Float, val h: Float,
    val label: String,
    val kind: ButtonKind = ButtonKind.TAP,
    val active: Boolean = false,
    val pressed: Boolean = false,
    val accent: Int = 0,
    val enabled: Boolean = true,
    val sub: String? = null,
    val left: Boolean = false,
    val hidden: Boolean = false,
) {
    fun contains(px: Float, py: Float) = enabled && px >= x && px <= x + w && py >= y && py <= y + h
}

/** Ajudantes de desenho compartilhados pelas telas: texto com quebra de linha, painéis e botões. */
object Ui {
    /** Largura média de um caractere monoespaçado, em múltiplos do tamanho da fonte. */
    private const val CHAR_W = 0.62f

    fun wrap(text: String, size: Float, maxW: Float): List<String> {
        val maxChars = (maxW / (size * CHAR_W)).toInt().coerceAtLeast(4)
        val out = ArrayList<String>()
        for (para in text.split('\n')) {
            var line = StringBuilder()
            for (word in para.split(' ')) {
                if (line.isNotEmpty() && line.length + 1 + word.length > maxChars) {
                    out.add(line.toString()); line = StringBuilder()
                }
                if (line.isNotEmpty()) line.append(' ')
                line.append(word)
            }
            out.add(line.toString())
        }
        return out
    }

    fun clip(text: String, size: Float, maxW: Float): String {
        val maxChars = (maxW / (size * CHAR_W)).toInt().coerceAtLeast(4)
        return if (text.length <= maxChars) text else text.take(maxChars - 1) + "…"
    }

    /** Escreve texto quebrado em linhas; devolve o y logo abaixo da última. */
    fun paragraph(d: Draw, x: Float, y: Float, text: String, size: Float, color: Int, maxW: Float, maxLines: Int = 99, lineH: Float = size * 1.35f, bold: Boolean = false): Float {
        var cy = y
        val lines = wrap(text, size, maxW)
        for ((i, l) in lines.withIndex()) {
            if (i >= maxLines) break
            val shown = if (i == maxLines - 1 && lines.size > maxLines) clip(l + "…", size, maxW) else l
            d.text(x, cy, shown, color, size, bold = bold)
            cy += lineH
        }
        return cy
    }

    fun panel(d: Draw, x: Float, y: Float, w: Float, h: Float, fill: Int = Palette.PANEL, edge: Int = Palette.PANEL_EDGE) {
        d.rect(x, y, w, h, fill = fill, stroke = edge, radius = 6f)
    }

    fun title(d: Draw, text: String, sub: String? = null) {
        d.text(16f, 28f, text, Palette.AMBER, 16f, bold = true)
        if (sub != null) d.text(16f, 44f, sub, Palette.MUTED, 10f)
    }

    fun pill(d: Draw, x: Float, y: Float, text: String, color: Int): Float {
        val w = text.length * 5.6f + 14f
        d.rect(x, y, w, 16f, fill = color.alpha(0.18f), radius = 8f)
        d.text(x + 7f, y + 11.5f, text, color, 9f)
        return w + 6f
    }

    fun draw(d: Draw, b: Button) {
        val lit = b.active || b.pressed
        val fill = when {
            !b.enabled -> 0xFF0B0F17.toInt()
            b.pressed -> Palette.AMBER
            b.active -> Palette.AMBER_DIM
            else -> Palette.BUTTON
        }
        val edge = when {
            !b.enabled -> Palette.BUTTON_EDGE.alpha(0.6f)
            lit || b.accent != 0 -> Palette.AMBER
            else -> Palette.BUTTON_EDGE
        }
        d.rect(b.x, b.y, b.w, b.h, fill = fill, stroke = edge, strokeW = if (lit) 1.6f else 1f, radius = 6f)
        val textColor = when {
            !b.enabled -> Palette.MUTED.alpha(0.7f)
            b.pressed -> 0xFF1A1206.toInt()
            b.active || b.accent != 0 -> Palette.AMBER
            else -> Palette.INK
        }
        val size = if (b.h > 60f && b.sub == null) 13f else if (b.id.startsWith("warp")) 10f else 11f
        if (b.left) {
            val lw = b.w - 20f
            d.text(b.x + 10f, b.y + 16f, clip(b.label, size, lw), textColor, size, bold = true)
            if (b.sub != null) paragraph(d, b.x + 10f, b.y + 29f, b.sub, 9f, if (b.enabled) Palette.MUTED else Palette.RED_SOFT.alpha(0.85f), lw, 3, 11f)
        } else {
            val lines = b.label.split('\n')
            val top = b.y + b.h / 2 - (lines.size - 1) * size * 0.65f + size * 0.35f
            for ((i, l) in lines.withIndex()) d.text(b.x + b.w / 2, top + i * size * 1.3f, l, textColor, size, Align.CENTER, bold = true)
        }
    }
}

/** Constrói a lista de botões enquanto desenha. */
class Buttons(private val d: Draw) {
    val list = ArrayList<Button>()

    fun add(id: String, x: Float, y: Float, w: Float, h: Float, label: String, kind: ButtonKind = ButtonKind.TAP, active: Boolean = false,
            pressed: Boolean = false, accent: Int = 0, enabled: Boolean = true, sub: String? = null, left: Boolean = false, hidden: Boolean = false): Button {
        val b = Button(id, x, y, w, h, label, kind, active, pressed, accent, enabled, sub, left, hidden)
        list.add(b)
        if (!hidden) Ui.draw(d, b)
        return b
    }

    fun back(id: String = "back", label: String = "VOLTAR") = add(id, Layout.W - 104f, 10f, 92f, 34f, label)
}
