package com.theexpanse.preview

import com.theexpanse.render.Align
import com.theexpanse.render.Circle
import com.theexpanse.render.Clip
import com.theexpanse.render.Frame
import com.theexpanse.render.Glow
import com.theexpanse.render.Layout
import com.theexpanse.render.Line
import com.theexpanse.render.Poly
import com.theexpanse.render.Prim
import com.theexpanse.render.Rect
import com.theexpanse.render.Txt
import com.theexpanse.render.Unclip
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.RadialGradientPaint
import java.awt.RenderingHints
import java.awt.Shape
import java.awt.geom.Ellipse2D
import java.awt.geom.Path2D
import java.awt.geom.Point2D
import java.awt.geom.Rectangle2D
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage

/** Rasteriza a lista de desenho com Java2D, só para ver as telas sem aparelho. */
object Raster {
    private fun color(argb: Int) = Color(argb, true)

    fun render(frame: Frame, scale: Float = 2f): BufferedImage {
        val img = BufferedImage((Layout.W * scale).toInt(), (Layout.H * scale).toInt(), BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
        g.scale(scale.toDouble(), scale.toDouble())
        g.color = Color.BLACK
        g.fill(Rectangle2D.Float(0f, 0f, Layout.W, Layout.H))
        val clips = ArrayList<Shape?>()
        for (p in frame.prims) draw(g, p, clips)
        g.dispose()
        return img
    }

    private fun draw(g: java.awt.Graphics2D, p: Prim, clips: MutableList<Shape?>) {
        when (p) {
            is Rect -> {
                val shape: Shape = if (p.radius > 0f) RoundRectangle2D.Float(p.x, p.y, p.w, p.h, p.radius * 2, p.radius * 2) else Rectangle2D.Float(p.x, p.y, p.w, p.h)
                if (p.fill != 0) { g.color = color(p.fill); g.fill(shape) }
                if (p.stroke != 0) { g.color = color(p.stroke); g.stroke = BasicStroke(p.strokeW); g.draw(shape) }
            }
            is Circle -> {
                val shape = Ellipse2D.Float(p.cx - p.r, p.cy - p.r, p.r * 2, p.r * 2)
                if (p.fill != 0) { g.color = color(p.fill); g.fill(shape) }
                if (p.stroke != 0) { g.color = color(p.stroke); g.stroke = BasicStroke(p.strokeW); g.draw(shape) }
            }
            is Poly -> {
                val path = Path2D.Float()
                path.moveTo(p.pts[0], p.pts[1])
                var i = 2
                while (i < p.pts.size) { path.lineTo(p.pts[i], p.pts[i + 1]); i += 2 }
                path.closePath()
                if (p.fill != 0) { g.color = color(p.fill); g.fill(path) }
                if (p.stroke != 0) { g.color = color(p.stroke); g.stroke = BasicStroke(p.strokeW, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND); g.draw(path) }
            }
            is Line -> {
                g.color = color(p.color)
                g.stroke = if (p.dash > 0f) BasicStroke(p.w, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, floatArrayOf(p.dash, p.dash), 0f) else BasicStroke(p.w, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
                g.draw(java.awt.geom.Line2D.Float(p.x1, p.y1, p.x2, p.y2))
            }
            is Glow -> {
                if (p.r < 0.5f) return
                val c = color(p.color)
                val paint = RadialGradientPaint(Point2D.Float(p.cx, p.cy), p.r, floatArrayOf(0f, 1f), arrayOf(c, Color(c.red, c.green, c.blue, 0)))
                g.paint = paint
                g.fill(Ellipse2D.Float(p.cx - p.r, p.cy - p.r, p.r * 2, p.r * 2))
            }
            is Txt -> {
                g.font = Font(Font.MONOSPACED, if (p.bold) Font.BOLD else Font.PLAIN, 1).deriveFont(p.size)
                g.color = color(p.color)
                val w = g.fontMetrics.stringWidth(p.text)
                val x = when (p.align) { Align.LEFT -> p.x; Align.CENTER -> p.x - w / 2f; Align.RIGHT -> p.x - w }
                g.drawString(p.text, x, p.y)
            }
            is Clip -> {
                clips.add(g.clip)
                g.clip(Rectangle2D.Float(p.x, p.y, p.w, p.h))
            }
            Unclip -> g.clip = clips.removeAt(clips.size - 1)
        }
    }
}
