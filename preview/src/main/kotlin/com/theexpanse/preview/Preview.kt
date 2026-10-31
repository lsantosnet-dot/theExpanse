package com.theexpanse.preview

import com.theexpanse.core.AutopilotMode
import com.theexpanse.core.Config
import com.theexpanse.core.Simulation
import com.theexpanse.core.Status
import com.theexpanse.render.GameController
import com.theexpanse.render.ViewMode
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

private fun shot(sim: Simulation, view: ViewMode, anim: Double, scale: Float = 2f): BufferedImage {
    val g = GameController(sim)
    g.view = view
    g.animate(anim)
    return Raster.render(g.frame(), scale)
}

private fun save(img: BufferedImage, dir: File, name: String) {
    ImageIO.write(img, "png", File(dir, "$name.png"))
    println("gravado $name.png")
}

private fun mosaic(frames: List<BufferedImage>, cols: Int): BufferedImage {
    val w = frames[0].width
    val h = frames[0].height
    val rows = (frames.size + cols - 1) / cols
    val out = BufferedImage(w * cols, h * rows, BufferedImage.TYPE_INT_ARGB)
    val g = out.createGraphics()
    for ((i, f) in frames.withIndex()) g.drawImage(f, (i % cols) * w, (i / cols) * h, null)
    g.dispose()
    return out
}

/** Cena do "virar e queimar", pelo caminho manual: alinha ao prograde, acelera, aperta RETRO, gira 180° e queima para frear. */
private fun flipScenario(dir: File) {
    val sim = Simulation()
    fun both(name: String, anim: Double) {
        save(shot(sim, ViewMode.COCKPIT, anim), dir, "cabine_$name")
        save(shot(sim, ViewMode.EXTERNAL, anim), dir, "externa_$name")
    }
    sim.setAutopilot(AutopilotMode.PROGRADE)
    for (i in 0 until 3000) sim.step(Config.SUBSTEP)
    sim.manualEngine = true
    for (i in 0 until 600) sim.step(Config.SUBSTEP)
    both("aceleracao", 0.37)
    sim.manualEngine = false
    sim.setAutopilot(AutopilotMode.RETROGRADE)
    val ext = ArrayList<BufferedImage>()
    val cab = ArrayList<BufferedImage>()
    var next = sim.time
    var steps = 0
    while (steps++ < 3000 && ext.size < 6) {
        sim.step(Config.SUBSTEP)
        if (sim.time >= next) {
            val a = 0.2 + ext.size * 0.113
            ext.add(shot(sim, ViewMode.EXTERNAL, a, 1f))
            cab.add(shot(sim, ViewMode.COCKPIT, a, 1f))
            if (ext.size == 3) both("virando", 0.55)
            next += 1.1
        }
    }
    save(mosaic(ext, 2), dir, "sequencia_virada_externa")
    save(mosaic(cab, 2), dir, "sequencia_virada_cabine")
    // espera terminar a virada e queima para frear
    var guard = 0
    while (guard++ < 20_000 && sim.autopilot == AutopilotMode.RETROGRADE && abs(sim.ship.omega) + abs(com.theexpanse.core.wrapAngle(sim.ship.angle - (sim.relativeVelocity().angle + Math.PI))) > 0.01) sim.step(Config.SUBSTEP)
    println("virada concluída em T+${sim.time.toInt()} s (passos extras: $guard), giro ${sim.ship.omega}")
    sim.manualEngine = true
    for (i in 0 until 300) sim.step(Config.SUBSTEP)
    both("frenando", 0.81)
}

/** Viagem completa pelo piloto automático: instantes de deriva e a atracação. */
private fun autoScenario(dir: File) {
    val sim = Simulation()
    sim.setAutopilot(AutopilotMode.INTERCEPT)
    var drift = false
    while (sim.status == Status.FLYING && sim.time < 60_000) {
        sim.step(Config.SUBSTEP)
        if (!drift && sim.time > 9000 && sim.ship.burnAccel < 0.001 && abs(sim.ship.omega) < 0.01) {
            drift = true
            save(shot(sim, ViewMode.COCKPIT, 0.1), dir, "cabine_deriva")
            save(shot(sim, ViewMode.EXTERNAL, 0.1), dir, "externa_deriva")
        }
    }
    println("viagem simulada: ${sim.time.toInt()} s, estado final ${sim.status}")
    save(shot(sim, ViewMode.EXTERNAL, 0.0), dir, "externa_atracado")
}

fun main(args: Array<String>) {
    val dir = File(args.firstOrNull() ?: "docs/preview")
    dir.mkdirs()
    flipScenario(dir)
    autoScenario(dir)
}
