package com.theexpanse.render

import kotlin.math.cos
import kotlin.math.sin

/**
 * Lista de desenho independente de plataforma. O :core monta a cena com estas primitivas em um espaço fixo
 * de [Layout.W] x [Layout.H]; o Android desenha com Compose e o :preview com AWT. Cor 0 = sem cor.
 */
sealed interface Prim

class Rect(
    val x: Float, val y: Float, val w: Float, val h: Float,
    val fill: Int = 0, val stroke: Int = 0, val strokeW: Float = 1f, val radius: Float = 0f,
) : Prim

class Circle(
    val cx: Float, val cy: Float, val r: Float,
    val fill: Int = 0, val stroke: Int = 0, val strokeW: Float = 1f,
) : Prim

/** Polígono (pares x,y). */
class Poly(val pts: FloatArray, val fill: Int = 0, val stroke: Int = 0, val strokeW: Float = 1f) : Prim

class Line(
    val x1: Float, val y1: Float, val x2: Float, val y2: Float,
    val color: Int, val w: Float = 1f, val dash: Float = 0f,
) : Prim

/** Brilho radial: [color] no centro, transparente na borda. */
class Glow(val cx: Float, val cy: Float, val r: Float, val color: Int) : Prim

enum class Align { LEFT, CENTER, RIGHT }

class Txt(
    val x: Float, val y: Float, val text: String, val color: Int,
    val size: Float = 11f, val align: Align = Align.LEFT, val bold: Boolean = false,
) : Prim

class Clip(val x: Float, val y: Float, val w: Float, val h: Float) : Prim
object Unclip : Prim

object Palette {
    val BG = 0xFF05070C.toInt()
    val PANEL = 0xFF0E141F.toInt()
    val PANEL_EDGE = 0xFF2A3648.toInt()
    val BUTTON = 0xFF121A27.toInt()
    val BUTTON_EDGE = 0xFF2E3B50.toInt()
    val AMBER = 0xFFF2A93B.toInt()
    val AMBER_DIM = 0xFF3A2A10.toInt()
    val AMBER_SOFT = 0xFFF2C77A.toInt()
    val INK = 0xFFD8DEE9.toInt()
    val MUTED = 0xFF8A95A8.toInt()
    val GREEN = 0xFF6BD49A.toInt()
    val RED = 0xFFE8645A.toInt()
    val RED_SOFT = 0xFFFFB3AC.toInt()
    val PLUME_CORE = 0xFFFFFFFF.toInt()
    val PLUME_HOT = 0xFFA8DCFF.toInt()
    val PLUME_MID = 0xFF3D8BFF.toInt()
    val PLUME_OUTER = 0xFF2A56FF.toInt()
    val HULL = 0xFFD2D7DE.toInt()
    val HULL_EDGE = 0xFF5B6470.toInt()
}

/** Multiplica o alfa de uma cor ARGB por [a] (0..1). */
fun Int.alpha(a: Float): Int {
    val na = (((this ushr 24) and 0xFF) * a.coerceIn(0f, 1f)).toInt()
    return (na shl 24) or (this and 0x00FFFFFF)
}

/** Mistura duas cores ARGB (t = 0 → a, t = 1 → b). */
fun mix(a: Int, b: Int, t: Float): Int {
    fun ch(s: Int) = (((a ushr s) and 0xFF) * (1 - t) + ((b ushr s) and 0xFF) * t).toInt()
    return (ch(24) shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
}

/** Construtor de cena com um deslocamento global (usado na vibração do motor). */
class Draw {
    val prims = ArrayList<Prim>(512)
    var ox = 0f
    var oy = 0f

    fun rect(x: Float, y: Float, w: Float, h: Float, fill: Int = 0, stroke: Int = 0, strokeW: Float = 1f, radius: Float = 0f) {
        prims.add(Rect(x + ox, y + oy, w, h, fill, stroke, strokeW, radius))
    }

    fun circle(cx: Float, cy: Float, r: Float, fill: Int = 0, stroke: Int = 0, strokeW: Float = 1f) {
        prims.add(Circle(cx + ox, cy + oy, r, fill, stroke, strokeW))
    }

    fun poly(pts: FloatArray, fill: Int = 0, stroke: Int = 0, strokeW: Float = 1f) {
        val p = FloatArray(pts.size) { if (it % 2 == 0) pts[it] + ox else pts[it] + oy }
        prims.add(Poly(p, fill, stroke, strokeW))
    }

    fun line(x1: Float, y1: Float, x2: Float, y2: Float, color: Int, w: Float = 1f, dash: Float = 0f) {
        prims.add(Line(x1 + ox, y1 + oy, x2 + ox, y2 + oy, color, w, dash))
    }

    fun glow(cx: Float, cy: Float, r: Float, color: Int) {
        prims.add(Glow(cx + ox, cy + oy, r, color))
    }

    fun text(x: Float, y: Float, s: String, color: Int, size: Float = 11f, align: Align = Align.LEFT, bold: Boolean = false) {
        prims.add(Txt(x + ox, y + oy, s, color, size, align, bold))
    }

    fun clip(x: Float, y: Float, w: Float, h: Float) {
        prims.add(Clip(x, y, w, h))
    }

    fun unclip() {
        prims.add(Unclip)
    }
}

/** Rotaciona e translada pontos locais (x para frente) para a tela; [angle] anti-horário, y da tela cresce para baixo. */
fun placePoints(local: FloatArray, cx: Float, cy: Float, angle: Double, scale: Float = 1f): FloatArray {
    val c = cos(angle).toFloat()
    val s = sin(angle).toFloat()
    val out = FloatArray(local.size)
    var i = 0
    while (i < local.size) {
        val lx = local[i] * scale
        val ly = local[i + 1] * scale
        out[i] = cx + lx * c - ly * s
        out[i + 1] = cy - (lx * s + ly * c)
        i += 2
    }
    return out
}

/**
 * Transformação linear de coordenadas locais da nave (nariz em +x) para deslocamentos na tela:
 * x = cx + a·lx + b·ly, y = cy + c·lx + d·ly. A câmera 2D usa rotação+escala; a 3D, a projeção do plano orbital.
 */
class Mat(val a: Float, val b: Float, val c: Float, val d: Float) {
    /** Tamanho na tela de uma unidade local ao longo do eixo do nariz. */
    val scale: Float get() = kotlin.math.sqrt(a * a + c * c)

    fun place(local: FloatArray, cx: Float, cy: Float): FloatArray {
        val out = FloatArray(local.size)
        var i = 0
        while (i < local.size) {
            out[i] = cx + a * local[i] + b * local[i + 1]
            out[i + 1] = cy + c * local[i] + d * local[i + 1]
            i += 2
        }
        return out
    }

    /** Direção unitária na tela de um vetor local. */
    fun dir(lx: Float, ly: Float): FloatArray {
        val x = a * lx + b * ly
        val y = c * lx + d * ly
        val l = kotlin.math.sqrt(x * x + y * y)
        return if (l < 1e-6f) floatArrayOf(1f, 0f) else floatArrayOf(x / l, y / l)
    }

    /** A mesma transformação depois de girar o objeto local em [phi] (anti-horário). */
    fun rotated(phi: Double): Mat {
        val cs = cos(phi).toFloat()
        val sn = sin(phi).toFloat()
        return Mat(a * cs + b * sn, -a * sn + b * cs, c * cs + d * sn, -c * sn + d * cs)
    }

    companion object {
        /** Vista de cima: nariz a [phi] rad (anti-horário), y da tela para baixo. */
        fun rot(phi: Double, scale: Float): Mat {
            val cs = cos(phi).toFloat() * scale
            val sn = sin(phi).toFloat() * scale
            return Mat(cs, -sn, -sn, -cs)
        }
    }
}
