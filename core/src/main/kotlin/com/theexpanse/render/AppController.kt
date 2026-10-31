package com.theexpanse.render

import com.theexpanse.content.Builtin
import com.theexpanse.content.ContentPackage
import com.theexpanse.content.Decoder
import com.theexpanse.content.Template
import com.theexpanse.content.Validator
import com.theexpanse.core.AutopilotMode
import com.theexpanse.core.Status
import com.theexpanse.game.EtaEstimator
import com.theexpanse.game.GameSession
import com.theexpanse.game.Host
import com.theexpanse.game.MemoryStorage
import com.theexpanse.game.NoHost
import com.theexpanse.game.RouteOption
import com.theexpanse.game.RoutePlanJob
import com.theexpanse.game.SaveInfo
import com.theexpanse.game.Sfx
import com.theexpanse.game.Saves
import com.theexpanse.game.Storage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

enum class Screen { HOME, FLIGHT, MAP, ROUTE, DIALOGUE, STATION, NOTEBOOK, HOLD, NEWS, MENU, END, ERRORS, CINEMATIC, SAVES }

class Entry(val pack: ContentPackage, val builtin: Boolean, val saveCount: Int) {
    val hasSave: Boolean get() = saveCount > 0
}

/** Uma cena animada em andamento (atracar ou desatracar). */
class Cinematic(val kind: CinematicKind, val start: Double)

/** O que desenhar neste quadro e onde há botões. */
class Frame(val prims: List<Prim>, val buttons: List<Button>)

/**
 * Controlador do app: catálogo de campanhas, sessão de jogo, telas e botões. Quem hospeda (Android, preview)
 * chama [update] a cada quadro, [frame] para desenhar e [press]/[release] nos toques.
 */
class AppController(
    val storage: Storage = MemoryStorage(),
    val host: Host = NoHost,
    private val seed: () -> Long = { System.nanoTime() },
    private val now: () -> Long = { System.currentTimeMillis() },
) {
    var screen = Screen.HOME
    var session: GameSession? = null
        private set
    var view = ViewMode.COCKPIT
    var animTime = 0.0
        private set

    var entries: List<Entry> = emptyList()
        private set
    var homePage = 0
    var banner: String? = null
    private var bannerUntil = 0.0

    var errors: List<String> = emptyList()
    var errorsTitle = ""
    var errorsPage = 0

    var mapZoom = 0
    var mapCenter = 0
    var routeJob: RoutePlanJob? = null
        private set
    val routeOptions: List<RouteOption> get() = routeJob?.options?.filterNotNull().orEmpty()
    var routeSelected = 1

    val selectedFacts = LinkedHashSet<String>()
    var factPage = 0
    var selectedSuspect: String? = null
    var deduceText: String? = null
    var newsPage = 0
    var savesPack: String? = null
    var savesPage = 0
    var savesFrom = Screen.HOME
    var pendingDelete: String? = null
    private var engineOn = false

    val cam = Camera()
    val eta = EtaEstimator()
    var cinematic: Cinematic? = null
        private set
    private var wasDocked = false

    private val held = HashSet<String>()
    private var buttons: List<Button> = emptyList()
    private var lastSave = 0.0

    init {
        refreshCatalog()
    }

    // ---- catálogo ----

    fun refreshCatalog() {
        val list = ArrayList<Entry>()
        for ((_, text) in Builtin.all()) Decoder.decode(text).pack?.let { list.add(Entry(it, true, saveList(it.id).size)) }
        for ((_, text) in storage.packages()) Decoder.decode(text).pack?.let { p -> if (list.none { it.pack.id == p.id }) list.add(Entry(p, false, saveList(p.id).size)) }
        entries = list
    }

    fun say(text: String) {
        banner = text
        bannerUntil = animTime + 6.0
    }

    /** Recebe o texto de um arquivo escolhido pelo jogador. Aceita só pacotes que decodificam e passam na validação. */
    fun importText(fileName: String, text: String): Boolean {
        val r = Decoder.decode(text)
        val pack = r.pack
        if (pack == null) return fail(fileName, r.errors)
        if (Builtin.all().any { (_, t) -> Decoder.decode(t).pack?.id == pack.id }) {
            return fail(fileName, listOf("O id '${pack.id}' já é de uma campanha incluída no jogo. Escolha outro 'id'."))
        }
        val v = Validator.validate(pack)
        if (!v.ok) return fail(fileName, v.errors)
        storage.putPackage(pack.id, text)
        refreshCatalog()
        screen = Screen.HOME
        say("Campanha “${pack.title}” importada." + if (v.warnings.isNotEmpty()) " (${v.warnings.size} avisos)" else "")
        return true
    }

    private fun fail(name: String, list: List<String>): Boolean {
        errors = list
        errorsTitle = name
        errorsPage = 0
        screen = Screen.ERRORS
        return false
    }

    // ---- sessão ----

    /** Salvamentos de uma campanha, do mais recente ao mais antigo. */
    fun saveList(packId: String): List<SaveInfo> =
        storage.saves(packId).mapNotNull { (slot, text) -> Saves.parse(slot, text) }.sortedByDescending { it.whenMs }

    /** Começa um jogo novo ([slot] nulo) ou carrega um salvamento. */
    fun start(entry: Entry, resume: Boolean, slot: String? = null) {
        val s = GameSession(entry.pack, seed())
        s.sound = { host.play(it) }
        if (resume) {
            val info = (if (slot != null) saveList(entry.pack.id).firstOrNull { it.slot == slot } else saveList(entry.pack.id).firstOrNull())
            if (info != null) {
                try { s.fromJson(info.game()) } catch (e: Exception) { s.restart(); say("Salvamento inválido; começando de novo.") }
            }
        }
        session = s
        screen = Screen.FLIGHT
        view = ViewMode.COCKPIT
        selectedFacts.clear(); selectedSuspect = null; deduceText = null; factPage = 0
        held.clear()
        cam.reset()
        cinematic = null
        wasDocked = s.sim.status == Status.DOCKED
    }

    /** Grava o jogo em andamento (o app chama ao pausar). */
    fun saveNow() {
        val s = session ?: return
        if (s.ended && !s.story.continued) return
        if (s.sim.status == Status.CRASHED || s.sim.status == Status.DESTROYED) return
        writeSave(s, SaveInfo.AUTO, "Automático")
    }

    private fun writeSave(s: GameSession, slot: String, name: String) {
        storage.putSave(s.pack.id, slot, Saves.wrap(name, now(), s.summary(), s.toJson()))
    }

    /** Grava um salvamento novo (além do automático) e devolve o nome dado. */
    fun saveNew(): String? {
        val s = session ?: return null
        val n = saveList(s.pack.id).count { !it.isAuto } + 1
        val name = "Jogo $n"
        var slot = "s${now()}"
        while (slot in storage.saves(s.pack.id)) slot += "x"
        writeSave(s, slot, name)
        s.addMessage("Jogo salvo: $name.")
        return name
    }

    /** Abre a lista de salvamentos de uma campanha. */
    fun openSaves(packId: String) {
        savesPack = packId
        savesPage = 0
        pendingDelete = null
        savesFrom = if (session != null) Screen.MENU else Screen.HOME
        screen = Screen.SAVES
    }

    fun renameSave(packId: String, slot: String) {
        val info = saveList(packId).firstOrNull { it.slot == slot } ?: return
        host.promptText("Nome do salvamento", info.name) { n ->
            val clean = n?.trim()?.take(40)
            if (!clean.isNullOrEmpty()) {
                storage.putSave(packId, slot, Saves.rename(info, clean))
                refreshCatalog()
            }
        }
    }

    fun leaveToHome() {
        saveNow()
        session = null
        held.clear()
        screen = Screen.HOME
        refreshCatalog()
    }

    /** Botão voltar do sistema. Devolve false quando não há mais para onde voltar (sair do app). */
    fun onBack(): Boolean {
        return when (effective()) {
            Screen.HOME -> false
            Screen.ERRORS -> { screen = Screen.HOME; true }
            Screen.FLIGHT -> { screen = Screen.MENU; true }
            Screen.DIALOGUE, Screen.END -> true
            Screen.STATION -> { screen = Screen.MENU; true }
            else -> { screen = Screen.FLIGHT; true }
        }
    }

    /** Tela efetiva: diálogos, finais e atracação têm prioridade sobre a tela escolhida. */
    /** Detecta a chegada a uma estação e dispara a cena de atracação. */
    private fun trackDock() {
        val s = session ?: return
        val docked = s.sim.status == Status.DOCKED
        if (docked && !wasDocked && cinematic == null) cinematic = Cinematic(CinematicKind.DOCK, animTime)
        wasDocked = docked
    }

    fun effective(): Screen {
        trackDock()
        if (cinematic != null && session != null) return Screen.CINEMATIC
        val s = session
        if (screen == Screen.SAVES) return Screen.SAVES
        if (s == null || screen == Screen.HOME || screen == Screen.ERRORS) return if (s == null && screen != Screen.ERRORS) Screen.HOME else screen
        if (s.dialogue != null) return Screen.DIALOGUE
        if (s.ended && !s.story.continued) return Screen.END
        if (s.sim.status == Status.DOCKED && screen == Screen.FLIGHT) return Screen.STATION
        return screen
    }

    // ---- laço ----

    fun update(realDt: Double) {
        animTime += realDt
        if (banner != null && animTime > bannerUntil) banner = null
        val s = session ?: return
        // giro da câmera enquanto o botão está pressionado
        if ("cam_l" in held) cam.rotate(1.6 * realDt)
        if ("cam_r" in held) cam.rotate(-1.6 * realDt)
        if ("cam_u" in held) cam.tilt(0.9 * realDt)
        if ("cam_d" in held) cam.tilt(-0.9 * realDt)
        val sc = effective()
        cinematic?.let { c ->
            if (animTime - c.start >= c.kind.duration) finishCinematic()
        }
        if (sc == Screen.ROUTE) routeJob?.advance(60_000)
        val burning = sc == Screen.FLIGHT && s.sim.status == Status.FLYING && s.sim.ship.burnAccel > 0.0
        if (burning != engineOn) { engineOn = burning; host.engine(burning) }
        if (sc == Screen.FLIGHT || sc == Screen.MAP) {
            s.update(realDt)
            eta.update(s.sim, animTime)
        } else s.update(0.0)
        if (animTime - lastSave > 15.0) {
            lastSave = animTime
            saveNow()
        }
    }

    private fun finishCinematic() {
        val c = cinematic ?: return
        cinematic = null
        if (c.kind == CinematicKind.UNDOCK) {
            session?.undock()
            wasDocked = false
            screen = Screen.FLIGHT
        }
    }

    /** Arrastar na cena gira a câmera ([dx] em unidades de desenho). */
    fun drag(dx: Float, dy: Float = 0f) {
        cam.rotate(dx * 0.004)
        if (view == ViewMode.SPACE3D) cam.tilt(dy * 0.004)
    }

    /** Pinça na cena aproxima ou afasta a câmera. */
    fun pinch(factor: Float) = cam.zoomBy(factor.toDouble())

    /** A cena aceita arrastar? Só no voo, fora dos botões. */
    val canDrag: Boolean get() = session != null && effective() == Screen.FLIGHT

    /** Só a animação, sem avançar o tempo simulado (para o preview). */
    fun animate(dt: Double) {
        animTime += dt
        session?.let { it.update(0.0) }
    }

    fun hit(x: Float, y: Float): Button? = buttons.lastOrNull { it.contains(x, y) }

    fun release(id: String) {
        held.remove(id)
        val sim = session?.sim ?: return
        when (id) {
            "turn_l", "turn_r" -> sim.manualTurn = when {
                "turn_l" in held -> 1
                "turn_r" in held -> -1
                else -> 0
            }
            "engine" -> sim.manualEngine = false
        }
    }

    fun press(id: String) {
        val s = session
        val sim = s?.sim
        if (id != "engine" && !id.startsWith("turn_") && !id.startsWith("cam_")) host.play(Sfx.CLICK)
        when {
            id == "back" -> screen = when (screen) { Screen.ERRORS -> Screen.HOME; Screen.SAVES -> savesFrom; else -> Screen.FLIGHT }
            id.startsWith("home_") -> homeAction(id)
            id.startsWith("save_load:") -> savesPack?.let { pid -> entries.firstOrNull { it.pack.id == pid }?.let { start(it, true, id.removePrefix("save_load:")) } }
            id.startsWith("save_rename:") -> savesPack?.let { renameSave(it, id.removePrefix("save_rename:")) }
            id.startsWith("save_del:") -> savesPack?.let { pid ->
                val slot = id.removePrefix("save_del:")
                if (pendingDelete == slot) { storage.removeSave(pid, slot); pendingDelete = null; refreshCatalog() } else pendingDelete = slot
            }
            id == "saves_up" -> savesPage = (savesPage - 1).coerceAtLeast(0)
            id == "saves_down" -> savesPage += 1
            s == null || sim == null -> {}
            id == "turn_l" -> { held.add(id); sim.manualTurn = 1 }
            id == "turn_r" -> { held.add(id); sim.manualTurn = -1 }
            id == "engine" -> { held.add(id); sim.manualEngine = true }
            id == "thr_down" -> sim.adjustThrust(-1)
            id == "thr_up" -> sim.adjustThrust(1)
            id == "ap_intercept" -> sim.toggleAutopilot(AutopilotMode.INTERCEPT)
            id == "ap_pro" -> sim.toggleAutopilot(AutopilotMode.PROGRADE)
            id == "ap_retro" -> sim.toggleAutopilot(AutopilotMode.RETROGRADE)
            id == "ap_stop" -> sim.setAutopilot(AutopilotMode.STOP_SPIN)
            id == "view" -> view = view.next()
            id == "target" -> sim.cycleTarget()
            id.startsWith("warp_") -> sim.setWarp(id.removePrefix("warp_").toInt())
            id == "menu" -> screen = Screen.MENU
            id == "restart" -> { held.clear(); sim.manualTurn = 0; sim.manualEngine = false; s.restart(); screen = Screen.FLIGHT; selectedFacts.clear(); selectedSuspect = null; deduceText = null; cam.reset(); cinematic = null; wasDocked = false }
            id == "rescue" -> s.callRescue()
            id == "continue" -> { s.continueFlying(); screen = Screen.FLIGHT }
            id == "undock" -> if (cinematic == null) cinematic = Cinematic(CinematicKind.UNDOCK, animTime)
            id == "skip" -> finishCinematic()
            id == "cam_l" || id == "cam_r" || id == "cam_u" || id == "cam_d" -> held.add(id)
            id == "board" -> s.board()
            id == "cam_reset" -> cam.reset()
            id == "zoom_in" -> cam.zoomBy(1.25)
            id == "zoom_out" -> cam.zoomBy(0.8)
            id == "fire" -> s.fire()
            id == "decoy" -> s.decoy()
            id == "open_map" -> screen = Screen.MAP
            id == "open_route" -> {
                routeJob = RoutePlanJob(sim).also { it.advance(1_500_000) }
                routeSelected = (routeJob!!.pcts.indexOf(sim.thrustPct)).coerceAtLeast(0)
                screen = Screen.ROUTE
            }
            id == "open_notebook" -> { screen = Screen.NOTEBOOK }
            id == "open_hold" -> screen = Screen.HOLD
            id == "open_news" -> screen = Screen.NEWS
            id == "open_home" -> leaveToHome()
            id == "save_new" -> saveNew()
            id == "open_saves" -> openSaves(s.pack.id)
            id == "map_zoom_in" -> mapZoom = (mapZoom + 1).coerceAtMost(26)
            id == "map_zoom_out" -> mapZoom = (mapZoom - 1).coerceAtLeast(-1)
            id == "map_center" -> mapCenter = (mapCenter + 1) % 4
            id == "map_fit" -> MapScreen.fit(this, s)
            id.startsWith("pick:") -> s.world.body(id.removePrefix("pick:"))?.let { sim.selectTarget(it) }
            id.startsWith("route_sel:") -> routeSelected = id.removePrefix("route_sel:").toInt()
            id == "route_go" -> routeJob?.options?.getOrNull(routeSelected)?.let {
                if (sim.status == Status.DOCKED) s.undock()
                sim.setThrust(it.thrustPct)
                sim.setAutopilot(AutopilotMode.INTERCEPT)
                screen = Screen.FLIGHT
            }
            id.startsWith("choice:") -> s.choose(id.removePrefix("choice:").toInt())
            id.startsWith("talk:") -> s.talkTo(id.removePrefix("talk:"))
            id.startsWith("fact:") -> id.removePrefix("fact:").let { f -> if (!selectedFacts.remove(f)) { if (selectedFacts.size >= 3) selectedFacts.remove(selectedFacts.first()); selectedFacts.add(f) }; deduceText = null }
            id == "facts_up" -> factPage = (factPage - 1).coerceAtLeast(0)
            id == "facts_down" -> factPage += 1
            id == "deduce" -> {
                val t = s.deduce(selectedFacts.toSet())
                deduceText = t ?: "Essas pistas não levam a nenhuma conclusão ainda."
                if (t != null) selectedFacts.clear()
            }
            id.startsWith("suspect:") -> id.removePrefix("suspect:").let { selectedSuspect = if (selectedSuspect == it) null else it }
            id == "accuse" -> selectedSuspect?.let { s.accuse(it) }
            id == "news_up" -> newsPage = (newsPage - 1).coerceAtLeast(0)
            id == "news_down" -> newsPage += 1
        }
    }

    private fun homeAction(id: String) {
        when {
            id == "home_import" -> host.importPackage()
            id == "home_export" -> host.exportFile(Template.FILE_NAME, Template.build())
            id == "home_prev" -> homePage = (homePage - 1).coerceAtLeast(0)
            id == "home_next" -> homePage += 1
            id == "home_errors_copy" -> host.copyText(errors.joinToString("\n") { "- $it" })
            id == "home_errors_up" -> errorsPage = (errorsPage - 1).coerceAtLeast(0)
            id == "home_errors_down" -> errorsPage += 1
            id == "home_errors_close" -> screen = Screen.HOME
            id.startsWith("home_play:") -> entries.firstOrNull { it.pack.id == id.removePrefix("home_play:") }?.let { start(it, false) }
            id.startsWith("home_resume:") -> entries.firstOrNull { it.pack.id == id.removePrefix("home_resume:") }?.let { start(it, true) }
            id.startsWith("home_saves:") -> openSaves(id.removePrefix("home_saves:"))
            id.startsWith("home_del:") -> id.removePrefix("home_del:").let { storage.removePackage(it); refreshCatalog(); say("Campanha removida.") }
        }
    }

    // ---- desenho ----

    fun frame(): Frame {
        val d = Draw()
        val sc = effective()
        val list: List<Button> = when (sc) {
            Screen.HOME -> HomeScreen.draw(d, this)
            Screen.ERRORS -> HomeScreen.drawErrors(d, this)
            Screen.SAVES -> SavesScreen.draw(d, this)
            else -> {
                val g = session!!
                when (sc) {
                    Screen.FLIGHT -> {
                        when (view) {
                            ViewMode.COCKPIT -> CockpitView.draw(d, g.sim, animTime, cam)
                            ViewMode.EXTERNAL -> ExternalView.draw(d, g.sim, animTime, cam)
                            ViewMode.SPACE3D -> Space3DView.draw(d, g.sim, animTime, cam)
                        }
                        Hud.drawTop(d, g, view, animTime)
                        val l = ArrayList<Button>(Hud.drawPanel(d, g.sim, view, held, eta))
                        l.addAll(Hud.drawSceneButtons(d, g, cam, held, animTime, view))
                        l.addAll(Hud.drawStranded(d, g))
                        l.addAll(Hud.drawCrash(d, g.sim))
                        l
                    }
                    Screen.CINEMATIC -> {
                        val c = cinematic!!
                        DockingScene.draw(d, g, c.kind, animTime - c.start, animTime)
                    }
                    Screen.MAP -> MapScreen.draw(d, this, g)
                    Screen.ROUTE -> RouteScreen.draw(d, this, g)
                    Screen.DIALOGUE -> DialogueScreen.draw(d, g)
                    Screen.STATION -> StationScreen.draw(d, this, g)
                    Screen.NOTEBOOK -> NotebookScreen.draw(d, this, g)
                    Screen.HOLD -> HoldScreen.draw(d, g)
                    Screen.NEWS -> NewsScreen.draw(d, this, g)
                    Screen.MENU -> MenuScreen.draw(d)
                    Screen.END -> EndScreen.draw(d, g)
                    else -> emptyList()
                }
            }
        }
        buttons = list
        return Frame(d.prims, list)
    }
}
