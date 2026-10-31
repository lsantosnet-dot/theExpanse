package com.theexpanse.preview

import com.theexpanse.content.Builtin
import com.theexpanse.core.AutopilotMode
import com.theexpanse.core.Config
import com.theexpanse.core.Status
import com.theexpanse.core.wrapAngle
import com.theexpanse.game.GameSession
import com.theexpanse.game.MemoryStorage
import com.theexpanse.render.AppController
import com.theexpanse.render.Screen
import com.theexpanse.render.ViewMode
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs

private lateinit var outDir: File

private fun shot(c: AppController, name: String, anim: Double = 0.37, scale: Float = 2f): BufferedImage {
    c.animate(anim)
    val img = Raster.render(c.frame(), scale)
    ImageIO.write(img, "png", File(outDir, "$name.png"))
    println("gravado $name.png")
    return img
}

private fun quiet(c: AppController, anim: Double = 0.37, scale: Float = 1f): BufferedImage {
    c.animate(anim)
    return Raster.render(c.frame(), scale)
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

private fun fly(g: GameSession, until: Double = 200_000.0) {
    while (!g.paused && g.sim.status == Status.FLYING && g.sim.time < until) g.stepFixed(Config.SUBSTEP)
}

private fun autoTo(g: GameSession, id: String) {
    if (g.sim.status == Status.DOCKED) g.undock()
    g.sim.selectTarget(g.world.body(id)!!)
    g.sim.setThrust(100)
    g.sim.setAutopilot(AutopilotMode.INTERCEPT)
    fly(g)
}

private fun newController(): AppController = AppController(MemoryStorage(), seed = { 3L })

/** Voo do Cálice: aceleração, virada de 180°, frenagem, mapa, rota, rádio e final. */
private fun calice() {
    val c = newController()
    c.press("home_play:calice")
    val g = c.session!!
    shot(c, "09_rota_inicio").let { }
    c.screen = Screen.MENU
    c.press("open_route")
    shot(c, "09_rota")
    c.screen = Screen.FLIGHT

    // alinha ao prógrado, acelera, depois RETRO e queima de costas
    g.sim.setAutopilot(AutopilotMode.PROGRADE)
    repeat(3000) { g.stepFixed(Config.SUBSTEP) }
    g.sim.manualEngine = true
    repeat(600) { g.stepFixed(Config.SUBSTEP) }
    c.view = ViewMode.COCKPIT; shot(c, "03_cabine_aceleracao")
    c.view = ViewMode.EXTERNAL; shot(c, "04_externa_aceleracao")
    g.sim.manualEngine = false
    g.sim.setAutopilot(AutopilotMode.RETROGRADE)
    val ext = ArrayList<BufferedImage>()
    val cab = ArrayList<BufferedImage>()
    var next = g.sim.time
    var steps = 0
    while (steps++ < 3000 && ext.size < 6) {
        g.stepFixed(Config.SUBSTEP)
        if (g.sim.time >= next) {
            val a = 0.2 + ext.size * 0.113
            c.view = ViewMode.EXTERNAL; ext.add(quiet(c, a))
            c.view = ViewMode.COCKPIT; cab.add(quiet(c, a))
            if (ext.size == 3) { c.view = ViewMode.EXTERNAL; shot(c, "05_externa_virando", 0.55) }
            next += 1.1
        }
    }
    ImageIO.write(mosaic(ext, 2), "png", File(outDir, "sequencia_virada_externa.png"))
    ImageIO.write(mosaic(cab, 2), "png", File(outDir, "sequencia_virada_cabine.png"))
    var guard = 0
    while (guard++ < 20_000 && abs(wrapAngle(g.sim.ship.angle - (g.sim.relativeVelocity().angle + Math.PI))) > 0.01) g.stepFixed(Config.SUBSTEP)
    g.sim.manualEngine = true
    repeat(300) { g.stepFixed(Config.SUBSTEP) }
    c.view = ViewMode.COCKPIT; shot(c, "06_cabine_frenando", 0.81)
    c.view = ViewMode.EXTERNAL; shot(c, "07_externa_frenando", 0.81)
    g.sim.manualEngine = false
    c.screen = Screen.MAP
    shot(c, "08_mapa")
    c.screen = Screen.FLIGHT

    // o rádio da fragata e o final pela escolta
    val c2 = newController()
    c2.press("home_play:calice")
    val g2 = c2.session!!
    g2.sim.setAutopilot(AutopilotMode.INTERCEPT)
    fly(g2)
    shot(c2, "10_radio")
    g2.choose(2)
    fly(g2)
    shot(c2, "11_final_calice")
}

/** Mistério do Albatroz: início, estação, conversa, caderno, porão, notícias, menu, final. */
private fun albatroz() {
    val c = newController()
    c.importText("outra.json", Builtin.text("albatroz")!!.replace("\"id\": \"albatroz\"", "\"id\": \"gelo_e_polvora\"").replace("O silêncio do Albatroz", "Gelo e pólvora").replace("\"resumo\": \"O cargueiro Albatroz perdeu o reator sem explicação. Siga as pistas por quatro lugares e decida quem acusar. Mistério de investigação, sem combate.\"", "\"resumo\": \"Corrida pelo gelo dos anéis: três contratos, duas facções e um informante que mente.\""))
    shot(c, "01_inicio")
    c.importText("quebrado.json", Builtin.text("albatroz")!!.replace("\"id\": \"albatroz\"", "\"id\": \"quebrado\"").replace("{\"tipo\": \"dar_objeto\", \"objeto\": \"caixa_preta\"}, ", ""))
    shot(c, "02_erros_importacao")
    c.screen = Screen.HOME
    c.press("home_play:albatroz")
    val g = c.session!!
    autoTo(g, "destrocos_albatroz")
    c.update(0.0)
    shot(c, "12_estacao_destrocos")
    autoTo(g, "estacao_ceifa")
    g.talkTo("ilse_moraga")
    shot(c, "13_dialogo_ilse")
    g.choose(1)
    autoTo(g, "lab_helena"); g.talkTo("dra_okafor"); g.choose(0); g.choose(-1)
    autoTo(g, "arquivo_vesta"); g.talkTo("noor"); g.choose(0); g.talkTo("noor"); g.choose(1)
    c.screen = Screen.NOTEBOOK
    c.press("fact:f_homem_terno"); c.press("fact:f_apolice")
    shot(c, "14_caderno_selecao")
    c.press("fact:f_homem_terno"); c.press("fact:f_apolice")
    g.deduce(setOf("f_codigo_marte", "f_codigo_roubado"))
    c.press("fact:f_homem_terno"); c.press("fact:f_apolice"); c.press("deduce")
    c.press("suspect:s_atlas")
    shot(c, "15_caderno_acusar")
    c.screen = Screen.HOLD; shot(c, "16_porao")
    c.screen = Screen.NEWS; shot(c, "17_noticias")
    c.screen = Screen.MENU; shot(c, "18_menu")
    c.screen = Screen.FLIGHT
    g.undock()
    c.view = ViewMode.EXTERNAL
    c.press("ap_retro")
    shot(c, "19_voo_externa_albatroz")
    c.press("accuse")
    c.screen = Screen.NOTEBOOK
    shot(c, "20_final_albatroz")
}

fun main(args: Array<String>) {
    outDir = File(args.firstOrNull() ?: "docs/preview")
    outDir.mkdirs()
    calice()
    albatroz()
    File(outDir.parentFile, "the_expanse_template.md").writeText(com.theexpanse.content.Template.build())
    println("gravado the_expanse_template.md")
}
