package com.theexpanse.preview

import com.theexpanse.content.Builtin
import com.theexpanse.core.AutopilotMode
import com.theexpanse.content.HullStyle
import com.theexpanse.core.Config
import com.theexpanse.core.Missile
import com.theexpanse.core.Owner
import com.theexpanse.core.Vec2
import com.theexpanse.render.CinematicKind
import com.theexpanse.render.Draw
import com.theexpanse.render.Frame
import com.theexpanse.render.Models
import com.theexpanse.render.Palette
import com.theexpanse.render.Layout
import com.theexpanse.render.Align
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


/** Galeria dos cruzadores de cada facção e do cargueiro, no mesmo quadro. */
private fun gallery() {
    val d = Draw()
    d.rect(0f, 0f, Layout.W, Layout.H, fill = Palette.BG)
    for (i in 0 until 120) d.circle(Fx0.h(i * 2) * Layout.W, Fx0.h(i * 2 + 1) * Layout.H, 0.8f, fill = 0xFFC9D3E6.toInt().alpha80())
    d.text(Layout.W / 2, 30f, "NAVES DAS FACÇÕES", Palette.AMBER, 16f, Align.CENTER, bold = true)
    val items = listOf(
        Triple("Coalizão Terrana", HullStyle.TERRAN, 0xFF5B9BF0.toInt()),
        Triple("Federação Marciana", HullStyle.MARTIAN, 0xFFE8645A.toInt()),
        Triple("Liga do Cinturão", HullStyle.BELT, 0xFF3FD0B8.toInt()),
    )
    for ((i, it) in items.withIndex()) {
        val cx = 150f + (i % 3) * 270f
        d.text(cx, 62f, it.first, it.third, 11f, Align.CENTER, bold = true)
        Models.cruiser(d, it.second, it.third, cx, 150f, com.theexpanse.render.Mat.rot(0.0, 1.5f), 0.8f, 0.0, 0.37 + i * 0.1)
    }
    d.text(150f, 262f, "Cargueiro independente", Palette.INK, 11f, Align.CENTER, bold = true)
    Models.freighter(d, 150f, 330f, com.theexpanse.render.Mat.rot(0.0, 1.5f), 0.8f, 0.0, 0.5)
    d.text(420f, 262f, "Genérico (corsário)", 0xFFC9A06A.toInt(), 11f, Align.CENTER, bold = true)
    Models.cruiser(d, HullStyle.GENERIC, 0xFFC9A06A.toInt(), 420f, 330f, com.theexpanse.render.Mat.rot(0.0, 1.5f), 0.6f, 0.0, 0.9)
    d.text(690f, 262f, "Destroços", Palette.MUTED, 11f, Align.CENTER, bold = true)
    Models.wreck(d, 0xFF3FD0B8.toInt(), 690f, 330f, com.theexpanse.render.Mat.rot(0.2, 1.6f), 0.5)
    val img = Raster.render(Frame(d.prims, emptyList()), 2f)
    ImageIO.write(img, "png", File(outDir, "21_galeria_naves.png"))
    println("gravado 21_galeria_naves.png")
}

private object Fx0 { fun h(i: Int): Float = com.theexpanse.render.Fx.hash(i) }
private fun Int.alpha80(): Int = (this and 0x00FFFFFF) or (0xB0 shl 24)

/** Mercador atacado por uma fragata: lançamento, míssil em voo, despistador, desvio e explosão. */
private fun ambush() {
    val c = AppController(MemoryStorage(), seed = { 11L })
    c.press("home_play:emboscada")
    val g = c.session!!
    g.choose(1)
    g.sim.setThrust(100)
    g.sim.setAutopilot(AutopilotMode.INTERCEPT)
    c.view = ViewMode.EXTERNAL
    val ext = ArrayList<BufferedImage>()
    val cab = ArrayList<BufferedImage>()
    fun snap(anim: Double) {
        c.view = ViewMode.EXTERNAL; ext.add(quiet(c, anim, 1f))
        c.view = ViewMode.COCKPIT; cab.add(quiet(c, anim, 1f))
    }
    var guard = 0
    while (g.sim.missiles.isEmpty() && guard++ < 400_000) g.stepFixed(0.05)
    for (i in 0 until 3) g.stepFixed(0.05)
    c.view = ViewMode.EXTERNAL; shot(c, "22_missil_lancado", 0.4)
    snap(0.4)
    guard = 0
    while ((g.sim.impactEta() ?: 99.0) > 14.0 && guard++ < 100_000) g.stepFixed(0.05)
    c.view = ViewMode.EXTERNAL; shot(c, "23_missil_em_voo", 0.7)
    c.view = ViewMode.COCKPIT; shot(c, "24_missil_cabine", 0.7)
    snap(0.7)
    guard = 0
    while ((g.sim.impactEta() ?: 99.0) > 7.0 && guard++ < 100_000) g.stepFixed(0.05)
    c.press("decoy")
    for (i in 0 until 6) g.stepFixed(0.05)
    c.view = ViewMode.EXTERNAL; shot(c, "25_despistador", 0.2)
    snap(0.2)
    repeat(60) { g.stepFixed(0.05) }
    snap(0.5)
    guard = 0
    while (g.sim.missiles.isNotEmpty() && guard++ < 10_000) g.stepFixed(0.05)
    snap(0.9)
    ImageIO.write(mosaic(ext, 2), "png", File(outDir, "sequencia_missil_externa.png"))
    ImageIO.write(mosaic(cab, 2), "png", File(outDir, "sequencia_missil_cabine.png"))

    // impacto sem despistador: clarão, vibração e casco caindo
    val c2 = AppController(MemoryStorage(), seed = { 3L })
    c2.press("home_play:emboscada")
    val g2 = c2.session!!
    g2.choose(1)
    val dir = Vec2.fromAngle(0.4)
    g2.sim.missiles.add(Missile(900, Owner.ENEMY, g2.sim.ship.pos + dir * 12_000.0, g2.sim.ship.vel, "jogador", "fragata_vigilia"))
    guard = 0
    while (g2.sim.ship.hull >= 100.0 && guard++ < 100_000) g2.stepFixed(0.02)
    for (i in 0 until 10) g2.stepFixed(0.02)
    c2.view = ViewMode.EXTERNAL; shot(c2, "26_impacto_externa", 0.3)
    c2.view = ViewMode.COCKPIT; shot(c2, "27_impacto_cabine", 0.3)
}

/** Cruzador da Coalizão (papel militar) dispara e abate o corsário. */
private fun patrol() {
    val c = AppController(MemoryStorage(), seed = { 5L })
    c.press("home_play:patrulha")
    val g = c.session!!
    shot(c, "28_patrulha_cabine_inicio")
    val corvo = g.world.body("corvo")!!
    val p = g.sim.position(corvo)
    g.sim.ship.pos = p + Vec2.fromAngle(2.0) * 70_000.0
    g.sim.ship.vel = g.sim.velocity(corvo)
    g.sim.ship.angle = (p - g.sim.ship.pos).angle
    g.stepFixed(0.05)
    g.choose(1)
    g.fire()
    repeat(10) { g.stepFixed(0.05) }
    c.view = ViewMode.EXTERNAL; shot(c, "29_patrulha_disparo", 0.3)
    c.view = ViewMode.COCKPIT; shot(c, "30_patrulha_disparo_cabine", 0.3)
    var guard = 0
    var shotKill = false
    while (guard++ < 40_000) {
        g.stepFixed(0.05)
        val eta = g.sim.impactEta()
        if (eta != null && eta < 10.0 && g.sim.ship.decoys > 0) g.decoy()
        if (g.sim.ship.missiles > 0 && g.sim.missiles.none { it.owner == Owner.PLAYER } && corvo.id !in g.sim.destroyed) g.fire()
        if (corvo.id in g.sim.destroyed && !shotKill) {
            shotKill = true
            g.continueFlying()
            repeat(6) { g.stepFixed(0.05) }
            c.view = ViewMode.EXTERNAL; shot(c, "31_patrulha_abate", 0.3)
            c.view = ViewMode.COCKPIT; shot(c, "36_patrulha_abate_cabine", 0.3)
            break
        }
    }
}

/** Radar tático: alvo travado, mísseis nos dois sentidos com tempo de impacto, despistador e a seta de velocidade. */
private fun radar() {
    val c = AppController(MemoryStorage(), seed = { 5L })
    c.press("home_play:patrulha")
    val g = c.session!!
    val corvo = g.world.body("corvo")!!
    val p = g.sim.position(corvo)
    g.sim.ship.pos = p + Vec2.fromAngle(2.0) * 70_000.0
    g.sim.ship.vel = g.sim.velocity(corvo)
    g.sim.ship.angle = (p - g.sim.ship.pos).angle
    g.stepFixed(0.05)
    g.choose(1)
    g.sim.selectTarget(corvo)
    c.press("radar")
    shot(c, "60_radar_alvo")
    g.fire()
    c.frame()
    c.touchDown(com.theexpanse.render.RadarView.CX, com.theexpanse.render.RadarView.CY)
    c.touchMove(com.theexpanse.render.RadarView.CX - 70f, com.theexpanse.render.RadarView.CY + 55f)
    c.touchUp()
    var guard = 0
    while (guard++ < 4000 && g.sim.incoming().isEmpty()) g.stepFixed(0.05)
    repeat(160) { g.stepFixed(0.05) }
    shot(c, "61_radar_misseis")
    g.decoy()
    repeat(24) { g.stepFixed(0.05) }
    shot(c, "62_radar_despistador")
}

/** A cena de atracação (e a de saída) em seis instantes cada. */
private fun docking() {
    val c = AppController(MemoryStorage(), seed = { 7L })
    c.press("home_play:albatroz")
    val g = c.session!!
    g.sim.selectTarget(g.world.body("lab_helena")!!)
    g.sim.setThrust(100); g.sim.setAutopilot(AutopilotMode.INTERCEPT)
    var guard = 0
    while (g.sim.status != Status.DOCKED && guard++ < 400_000) g.stepFixed(Config.SUBSTEP)
    c.frame()
    val frames = ArrayList<BufferedImage>()
    val total = CinematicKind.DOCK.duration
    val marks = listOf(0.08, 0.28, 0.5, 0.7, 0.82, 0.93)
    var at = 0.0
    for (m in marks) {
        c.animate(m * total - at); at = m * total
        frames.add(Raster.render(c.frame(), 1f))
    }
    ImageIO.write(frames[2], "png", File(outDir, "32_atracacao.png"))
    ImageIO.write(mosaic(frames, 2), "png", File(outDir, "sequencia_atracacao.png"))
    println("gravado sequencia_atracacao.png")
}

/** Pouso e decolagem em Marte (com atmosfera) e na Lua (sem), em seis instantes cada. */
private fun landing() {
    for ((id, tag) in listOf("marte" to "marte", "lua" to "lua", "terra" to "terra", "ceres" to "ceres")) {
        val c = AppController(MemoryStorage(), seed = { 7L })
        c.press("home_play:rota_do_gelo")
        val g = c.session!!
        val body = g.world.body(id)!!
        g.sim.selectTarget(body)
        val radial = body.position(g.sim.time).unit()
        g.sim.ship.pos = body.position(g.sim.time) + radial * (body.def.radius + 4000.0)
        g.sim.ship.vel = body.velocity(g.sim.time)
        g.sim.setThrust(100); g.sim.setAutopilot(AutopilotMode.INTERCEPT)
        var guard = 0
        while (g.sim.status == Status.FLYING && guard++ < 400_000) g.sim.step(Config.SUBSTEP)
        println("pouso $id: ${g.sim.status} em ${g.sim.landedOn?.name}")
        c.frame()
        for (kind in listOf(CinematicKind.LAND, CinematicKind.TAKEOFF)) {
            if (kind == CinematicKind.TAKEOFF) { c.press("skip"); c.press("undock"); c.frame() }
            val frames = ArrayList<BufferedImage>()
            var at = 0.0
            for (m in listOf(0.06, 0.3, 0.55, 0.7, 0.84, 0.95)) {
                c.animate(m * kind.duration - at); at = m * kind.duration
                frames.add(Raster.render(c.frame(), 1f))
            }
            val name = if (kind == CinematicKind.LAND) "pouso" else "decolagem"
            ImageIO.write(mosaic(frames, 2), "png", File(outDir, "sequencia_${name}_$tag.png"))
            println("gravado sequencia_${name}_$tag.png")
        }
    }
}

/** Tela inicial: ordem da lista e exclusão de campanhas importadas (com a confirmação pendente). */
private fun home() {
    val c = AppController(MemoryStorage(), seed = { 7L })
    val base = com.theexpanse.content.Builtin.text("albatroz")!!
    c.importText("a.json", base.replace("\"id\": \"albatroz\"", "\"id\": \"zeta_x\"").replace("O silêncio do Albatroz", "Zeta importada"))
    c.importText("b.json", base.replace("\"id\": \"albatroz\"", "\"id\": \"alfa_x\"").replace("O silêncio do Albatroz", "Alfa importada"))
    c.press("home_sort:data_desc")
    c.press("home_del:alfa_x")
    shot(c, "70_inicio_ordem_exclusao")
}

/** Painel com chegada em tempo de jogo e tempo real; câmera girada na cabine e na externa. */
private fun panelAndCamera() {
    val c = AppController(MemoryStorage(), seed = { 9L })
    c.press("home_play:calice")
    c.session!!.sim.setThrust(50)
    c.session!!.sim.setAutopilot(AutopilotMode.INTERCEPT)
    c.press("warp_3")
    repeat(240) { c.update(1.0 / 30) }
    c.view = ViewMode.COCKPIT
    shot(c, "33_painel_chegada_cruzeiro")
    c.press("cam_l")
    repeat(45) { c.update(1.0 / 30) }
    c.release("cam_l")
    shot(c, "34_camera_cabine_girada")
    c.view = ViewMode.EXTERNAL
    c.cam.reset(); c.cam.rotate(-0.9); c.cam.zoomBy(1.4)
    shot(c, "35_camera_externa_girada")
}


/** Sistema Solar real: vistas, mapa, escolta, abordagem, 3D e salvamentos. */
private fun solar() {
    var clock = 1_761_900_000_000L
    val c = AppController(MemoryStorage(), seed = { 4L }, now = { clock })
    c.press("home_play:rota_do_gelo")
    val g = c.session!!
    shot(c, "37_solar_escolta_dialogo")
    g.choose(0) // contrata a escolta
    c.view = ViewMode.COCKPIT
    c.update(0.0)
    repeat(4000) { g.stepFixed(0.5) }
    c.view = ViewMode.SPACE3D; c.cam.reset(); c.cam.rotate(0.9); shot(c, "38_solar_3d_escolta")
    c.view = ViewMode.EXTERNAL; c.cam.reset(); shot(c, "39_solar_externa_escolta")
    c.cam.reset()

    // mapa do sistema inteiro e ajustado a Ceres–Marte
    c.screen = Screen.MAP
    c.mapZoom = 0; c.mapCenter = 0
    shot(c, "40_solar_mapa_sistema")
    c.press("map_fit")
    shot(c, "41_solar_mapa_ajustado")
    c.screen = Screen.FLIGHT

    // viagem: piloto automático em escala de cruzeiro
    g.sim.setThrust(50); g.sim.setAutopilot(AutopilotMode.INTERCEPT)
    c.press("warp_3")
    var frames = 0
    while (g.sim.time < 2.2e5 && frames++ < 12000 && g.dialogue == null) { g.update(1.0 / 30); c.animate(1.0 / 30) }
    c.eta.update(g.sim, c.animTime, 3_000_000)
    c.view = ViewMode.COCKPIT; shot(c, "42_solar_cabine_cruzeiro")
    c.view = ViewMode.SPACE3D; c.cam.reset(); shot(c, "43_solar_3d_cruzeiro")
    // emboscada dos corsários (a escolta reage)
    while (g.dialogue == null && g.sim.status == Status.FLYING && g.sim.time < 4.5e5 && frames++ < 30000) { g.update(1.0 / 30); c.animate(1.0 / 30) }
    c.view = ViewMode.EXTERNAL; shot(c, "44_solar_abordagem_corsario")
    c.view = ViewMode.SPACE3D; c.cam.reset(); c.cam.rotate(0.4); shot(c, "45_solar_3d_corsario")
    if (g.dialogue != null) shot(c, "46_solar_dialogo_pirata")

    // rota calculando em segundo plano
    val c2 = AppController(MemoryStorage(), seed = { 4L })
    c2.press("home_play:rota_do_gelo")
    c2.session!!.choose(1)
    c2.screen = Screen.MENU
    c2.press("open_route")
    c2.update(1.0 / 60)
    shot(c2, "47_solar_rota_calculando")
    var f2 = 0
    while (c2.routeJob?.done != true && f2++ < 4000) c2.update(1.0 / 60)
    shot(c2, "48_solar_rota_pronta")

    // salvamentos: dois manuais e o automático, um renomeado
    val host = object : com.theexpanse.game.Host {
        override fun importPackage() {}
        override fun exportFile(fileName: String, text: String) {}
        override fun copyText(text: String) {}
        override fun promptText(title: String, initial: String, onResult: (String?) -> Unit) = onResult("Antes da emboscada")
    }
    val c3 = AppController(MemoryStorage(), host, seed = { 4L }, now = { clock })
    c3.press("home_play:rota_do_gelo")
    c3.session!!.choose(0)
    c3.press("save_new")
    clock += 3_600_000
    repeat(900) { c3.session!!.stepFixed(1.0) }
    c3.press("save_new")
    c3.saveNow()
    c3.screen = Screen.MENU
    shot(c3, "49_menu_salvar")
    c3.press("open_saves")
    c3.press("save_rename:" + c3.saveList("rota_do_gelo").first { !it.isAuto }.slot)
    shot(c3, "50_saves")
    c3.leaveToHome()
    shot(c3, "51_inicio_com_saves")
}

/** Cruzador abordando o corsário: botão ABORDAR liberado, na externa e na 3D. */
private fun boarding() {
    val c = AppController(MemoryStorage(), seed = { 8L })
    c.press("home_play:patrulha")
    val g = c.session!!
    val corvo = g.world.body("corvo")!!
    val p = g.sim.position(corvo)
    g.sim.ship.pos = p + Vec2.fromAngle(2.0) * 500.0
    g.sim.ship.vel = g.sim.velocity(corvo)
    g.sim.ship.angle = (p - g.sim.ship.pos).angle
    g.stepFixed(0.05)
    g.choose(0)  // ordena a rendição
    if (g.dialogue != null) g.choose(0)
    g.stepFixed(0.05)
    c.view = ViewMode.EXTERNAL; shot(c, "52_abordagem_botao")
    c.view = ViewMode.SPACE3D; c.cam.reset(); c.cam.rotate(0.6); c.cam.zoomBy(1.6); shot(c, "53_abordagem_3d")
    c.press("board")
    repeat(4) { g.stepFixed(0.05) }
    c.view = ViewMode.EXTERNAL; shot(c, "54_abordagem_resultado")
}

/** Verificação da rota antes de engajar o INTERCEPTAR: a barra de progresso e o aviso de rota inviável. */
private fun preflight() {
    val c = newController()
    c.press("home_play:albatroz")
    val sim = c.session!!.sim
    val helena = sim.world.body("lab_helena")!!
    sim.selectTarget(sim.bodies.indexOf(helena))
    val dir = -helena.position(sim.time).unit()
    sim.ship.pos = dir * 250_000_000.0
    sim.ship.vel = -dir * 57_000.0
    sim.ship.angle = (helena.position(sim.time) - sim.ship.pos).angle
    c.view = ViewMode.EXTERNAL
    c.press("ap_intercept")
    var n = 0
    while (c.preflight != null && n++ < 20000) c.update(1.0 / 60)
    shot(c, "56_rota_inviavel")

    // viagem longa (Sistema Solar): a verificação leva vários quadros e mostra o progresso
    val s2 = newController()
    s2.press("home_play:rota_do_gelo")
    s2.session!!.choose(1)
    s2.view = ViewMode.EXTERNAL
    s2.press("ap_intercept")
    repeat(6) { s2.update(1.0 / 60) }
    shot(s2, "55_rota_verificando")
}

/** Caderno com tentativas de acusação: um suspeito já descartado e a contagem restante. */
private fun accusations() {
    val c = newController()
    c.press("home_play:albatroz")
    val g = c.session!!
    c.screen = Screen.NOTEBOOK
    g.accuse("s_marte")
    shot(c, "57_acusacoes_tentativas")
    g.accuse("s_atlas")
    shot(c, "58_acusacoes_ultima")

    // caso já encerrado (acusação certa ou parcial): o Caderno avisa e desabilita ACUSAR
    val c2 = newController()
    c2.press("home_play:albatroz")
    val g2 = c2.session!!
    g2.runEffects(listOf(com.theexpanse.content.Effect.Reveal("f_homem_terno"), com.theexpanse.content.Effect.Reveal("f_apolice")))
    g2.deduce(setOf("f_homem_terno", "f_apolice"))
    g2.accuse("s_terno")
    c2.press("continue")
    c2.screen = Screen.NOTEBOOK
    shot(c2, "59_caderno_caso_encerrado")
}

fun main(args: Array<String>) {
    outDir = File(args.firstOrNull() ?: "docs/preview")
    outDir.mkdirs()
    if (System.getenv("ONLY_PREFLIGHT") != null) { preflight(); accusations(); return }
    if (System.getenv("ONLY_RADAR") != null) { radar(); return }
    if (System.getenv("ONLY_HOME") != null) { home(); return }
    if (System.getenv("ONLY_LANDING") != null) { landing(); return }
    if (System.getenv("ONLY_TEMPLATE") != null) {
        File(outDir.parentFile, "the_expanse_template.md").writeText(com.theexpanse.content.Template.build())
        println("gravado the_expanse_template.md")
        return
    }
    calice()
    albatroz()
    gallery()
    ambush()
    patrol()
    radar()
    docking()
    landing()
    panelAndCamera()
    solar()
    boarding()
    preflight()
    accusations()
    File(outDir.parentFile, "the_expanse_template.md").writeText(com.theexpanse.content.Template.build())
    println("gravado the_expanse_template.md")
}
