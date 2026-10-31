package com.theexpanse.app

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import com.theexpanse.render.Align
import com.theexpanse.render.Circle
import com.theexpanse.render.Clip
import com.theexpanse.render.AppController
import com.theexpanse.render.Glow
import com.theexpanse.render.Layout
import com.theexpanse.render.Line
import com.theexpanse.render.Poly
import com.theexpanse.render.Prim
import com.theexpanse.render.Rect
import com.theexpanse.render.Txt
import com.theexpanse.render.Unclip
import kotlin.math.min

/** Letterbox: o espaço de desenho 844x390 é escalado por igual e centralizado. */
private class Fit(val scale: Float, val ox: Float, val oy: Float)

private fun fit(w: Float, h: Float): Fit {
    val s = min(w / Layout.W, h / Layout.H)
    return Fit(s, (w - Layout.W * s) / 2f, (h - Layout.H * s) / 2f)
}

@Composable
fun GameScreen(game: AppController) {
    var tick by remember { mutableLongStateOf(0L) }

    // relógio do jogo: um passo por quadro, com teto de 0,1 s para não dar salto depois de uma pausa
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            androidx.compose.runtime.withFrameNanos { now ->
                if (last != 0L) game.update(((now - last) / 1e9).coerceAtMost(0.1))
                last = now
                tick = now
            }
        }
    }

    val paint = remember { Paint().apply { isAntiAlias = true; typeface = Typeface.MONOSPACE } }

    Canvas(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                // multitoque: cada dedo segura o seu botão (girar e motor ao mesmo tempo); um dedo livre na cena gira a
                // câmera e dois dedos livres fazem zoom (pinça)
                awaitPointerEventScope {
                    val held = HashMap<PointerId, String>()
                    val free = HashMap<PointerId, Offset>()
                    var radarPtr: PointerId? = null // dedo que arrasta a nave no radar tático
                    while (true) {
                        val event = awaitPointerEvent()
                        val f = fit(size.width.toFloat(), size.height.toFloat())
                        for (c in event.changes) {
                            val x = (c.position.x - f.ox) / f.scale
                            val y = (c.position.y - f.oy) / f.scale
                            if (c.pressed && !c.previousPressed) {
                                val b = game.hit(x, y)
                                if (b != null) { held[c.id] = b.id; game.press(b.id) }
                                else if (radarPtr == null && game.touchDown(x, y)) radarPtr = c.id
                                else if (game.canDrag && y < Layout.SCENE_H) free[c.id] = c.position
                            } else if (!c.pressed && c.previousPressed) {
                                held.remove(c.id)?.let { game.release(it) }
                                free.remove(c.id)
                                if (c.id == radarPtr) { game.touchUp(); radarPtr = null }
                            } else if (c.pressed && c.id == radarPtr) game.touchMove(x, y)
                            c.consume()
                        }
                        val ids = free.keys.toList()
                        if (ids.size == 1) {
                            val c = event.changes.firstOrNull { it.id == ids[0] }
                            val last = free[ids[0]]
                            if (c != null && c.pressed && last != null) {
                                game.drag((c.position.x - last.x) / f.scale, (c.position.y - last.y) / f.scale)
                                free[ids[0]] = c.position
                            }
                        } else if (ids.size >= 2) {
                            val a = event.changes.firstOrNull { it.id == ids[0] }
                            val b = event.changes.firstOrNull { it.id == ids[1] }
                            val la = free[ids[0]]
                            val lb = free[ids[1]]
                            if (a != null && b != null && la != null && lb != null) {
                                val before = (la - lb).getDistance()
                                val now = (a.position - b.position).getDistance()
                                if (before > 10f) game.pinch(now / before)
                                free[ids[0]] = a.position
                                free[ids[1]] = b.position
                            }
                        }
                    }
                }
            },
    ) {
        tick // lido aqui para redesenhar a cada quadro
        val f = fit(size.width, size.height)
        val frame = game.frame()
        drawRect(Color(0xFF05070C))
        translate(f.ox, f.oy) {
            scale(f.scale, Offset.Zero) {
                clipToDesign()
                drawPrims(frame.prims, paint)
            }
        }
    }
}

private fun DrawScope.clipToDesign() {
    drawContext.canvas.save()
    drawContext.canvas.clipRect(0f, 0f, Layout.W, Layout.H)
}

private fun DrawScope.drawPrims(prims: List<Prim>, paint: Paint) {
    var clips = 0
    for (p in prims) {
        when (p) {
            is Rect -> {
                if (p.fill != 0) drawRect(p, Color(p.fill), Fill)
                if (p.stroke != 0) drawRect(p, Color(p.stroke), Stroke(p.strokeW))
            }
            is Circle -> {
                if (p.fill != 0) drawCircle(Color(p.fill), p.r, Offset(p.cx, p.cy))
                if (p.stroke != 0) drawCircle(Color(p.stroke), p.r, Offset(p.cx, p.cy), style = Stroke(p.strokeW))
            }
            is Poly -> {
                val path = Path().apply {
                    moveTo(p.pts[0], p.pts[1])
                    var i = 2
                    while (i < p.pts.size) { lineTo(p.pts[i], p.pts[i + 1]); i += 2 }
                    close()
                }
                if (p.fill != 0) drawPath(path, Color(p.fill), style = Fill)
                if (p.stroke != 0) drawPath(path, Color(p.stroke), style = Stroke(p.strokeW))
            }
            is Line -> drawLine(
                Color(p.color), Offset(p.x1, p.y1), Offset(p.x2, p.y2), p.w,
                pathEffect = if (p.dash > 0f) PathEffect.dashPathEffect(floatArrayOf(p.dash, p.dash)) else null,
            )
            is Glow -> if (p.r > 0.5f) {
                val c = Color(p.color)
                drawCircle(Brush.radialGradient(listOf(c, c.copy(alpha = 0f)), Offset(p.cx, p.cy), p.r), p.r, Offset(p.cx, p.cy))
            }
            is Txt -> {
                paint.color = p.color
                paint.textSize = p.size
                paint.isFakeBoldText = p.bold
                paint.textAlign = when (p.align) { Align.LEFT -> Paint.Align.LEFT; Align.CENTER -> Paint.Align.CENTER; Align.RIGHT -> Paint.Align.RIGHT }
                drawContext.canvas.nativeCanvas.drawText(p.text, p.x, p.y, paint)
            }
            is Clip -> {
                drawContext.canvas.save()
                drawContext.canvas.clipRect(p.x, p.y, p.x + p.w, p.y + p.h)
                clips++
            }
            Unclip -> if (clips > 0) { drawContext.canvas.restore(); clips-- }
        }
    }
    repeat(clips) { drawContext.canvas.restore() }
    drawContext.canvas.restore() // o clipToDesign()
}

private fun DrawScope.drawRect(p: Rect, color: Color, style: androidx.compose.ui.graphics.drawscope.DrawStyle) {
    if (p.radius > 0f) drawRoundRect(color, Offset(p.x, p.y), Size(p.w, p.h), CornerRadius(p.radius), style)
    else drawRect(color, Offset(p.x, p.y), Size(p.w, p.h), style = style)
}
