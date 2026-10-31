package com.theexpanse.render

import com.theexpanse.content.Builtin
import com.theexpanse.content.ContentPackage
import com.theexpanse.content.Decoder
import com.theexpanse.content.Template
import com.theexpanse.content.Validator
import com.theexpanse.core.AutopilotMode
import com.theexpanse.core.Status
import com.theexpanse.game.GameSession
import com.theexpanse.game.Host
import com.theexpanse.game.MemoryStorage
import com.theexpanse.game.NoHost
import com.theexpanse.game.RouteOption
import com.theexpanse.game.RoutePlanner
import com.theexpanse.game.Storage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

enum class Screen { HOME, FLIGHT, MAP, ROUTE, DIALOGUE, STATION, NOTEBOOK, HOLD, NEWS, MENU, END, ERRORS }

class Entry(val pack: ContentPackage, val builtin: Boolean, val hasSave: Boolean)

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
    var mapOnShip = false
    var routeOptions: List<RouteOption> = emptyList()
    var routeSelected = 1

    val selectedFacts = LinkedHashSet<String>()
    var factPage = 0
    var selectedSuspect: String? = null
    var deduceText: String? = null
    var newsPage = 0

    private val held = HashSet<String>()
    private var buttons: List<Button> = emptyList()
    private var lastSave = 0.0

    init {
        refreshCatalog()
    }

    // ---- catálogo ----

    fun refreshCatalog() {
        val list = ArrayList<Entry>()
        for ((_, text) in Builtin.all()) Decoder.decode(text).pack?.let { list.add(Entry(it, true, storage.save(it.id) != null)) }
        for ((_, text) in storage.packages()) Decoder.decode(text).pack?.let { p -> if (list.none { it.pack.id == p.id }) list.add(Entry(p, false, storage.save(p.id) != null)) }
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

    fun start(entry: Entry, resume: Boolean) {
        val s = GameSession(entry.pack, seed())
        if (resume) storage.save(entry.pack.id)?.let { t ->
            try { s.fromJson(Json.parseToJsonElement(t).jsonObject) } catch (e: Exception) { s.restart(); say("Salvamento inválido; começando de novo.") }
        } else storage.removeSave(entry.pack.id)
        session = s
        screen = Screen.FLIGHT
        view = ViewMode.COCKPIT
        selectedFacts.clear(); selectedSuspect = null; deduceText = null; factPage = 0
        held.clear()
    }

    /** Grava o jogo em andamento (o app chama ao pausar). */
    fun saveNow() {
        val s = session ?: return
        if (s.ended && !s.story.continued) return
        if (s.sim.status == Status.CRASHED) return
        storage.putSave(s.pack.id, s.toJson().toString())
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
    fun effective(): Screen {
        val s = session
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
        if (effective() == Screen.FLIGHT || effective() == Screen.MAP) s.update(realDt) else s.update(0.0)
        if (animTime - lastSave > 15.0) {
            lastSave = animTime
            saveNow()
        }
    }

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
        when {
            id == "back" -> screen = if (screen == Screen.ERRORS) Screen.HOME else Screen.FLIGHT
            id.startsWith("home_") -> homeAction(id)
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
            id == "view" -> view = if (view == ViewMode.COCKPIT) ViewMode.EXTERNAL else ViewMode.COCKPIT
            id == "target" -> sim.cycleTarget()
            id.startsWith("warp_") -> sim.setWarp(id.removePrefix("warp_").toInt())
            id == "menu" -> screen = Screen.MENU
            id == "restart" -> { held.clear(); sim.manualTurn = 0; sim.manualEngine = false; s.restart(); screen = Screen.FLIGHT; selectedFacts.clear(); selectedSuspect = null; deduceText = null }
            id == "continue" -> { s.continueFlying(); screen = Screen.FLIGHT }
            id == "undock" -> { s.undock(); screen = Screen.FLIGHT }
            id == "open_map" -> screen = Screen.MAP
            id == "open_route" -> { routeOptions = RoutePlanner.plan(sim); routeSelected = routeOptions.indexOfFirst { it.thrustPct == sim.thrustPct }.coerceAtLeast(0); screen = Screen.ROUTE }
            id == "open_notebook" -> { screen = Screen.NOTEBOOK }
            id == "open_hold" -> screen = Screen.HOLD
            id == "open_news" -> screen = Screen.NEWS
            id == "open_home" -> leaveToHome()
            id == "map_zoom_in" -> mapZoom = (mapZoom + 1).coerceAtMost(8)
            id == "map_zoom_out" -> mapZoom = (mapZoom - 1).coerceAtLeast(-1)
            id == "map_center" -> mapOnShip = !mapOnShip
            id.startsWith("pick:") -> s.world.body(id.removePrefix("pick:"))?.let { sim.selectTarget(it) }
            id.startsWith("route_sel:") -> routeSelected = id.removePrefix("route_sel:").toInt()
            id == "route_go" -> routeOptions.getOrNull(routeSelected)?.let {
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
            id.startsWith("home_del:") -> id.removePrefix("home_del:").let { storage.removePackage(it); storage.removeSave(it); refreshCatalog(); say("Campanha removida.") }
        }
    }

    // ---- desenho ----

    fun frame(): Frame {
        val d = Draw()
        val sc = effective()
        val list: List<Button> = when (sc) {
            Screen.HOME -> HomeScreen.draw(d, this)
            Screen.ERRORS -> HomeScreen.drawErrors(d, this)
            else -> {
                val g = session!!
                when (sc) {
                    Screen.FLIGHT -> {
                        when (view) {
                            ViewMode.COCKPIT -> CockpitView.draw(d, g.sim, animTime)
                            ViewMode.EXTERNAL -> ExternalView.draw(d, g.sim, animTime)
                        }
                        Hud.drawTop(d, g, view)
                        val l = ArrayList<Button>(Hud.drawPanel(d, g.sim, view, held))
                        l.addAll(Hud.drawCrash(d, g.sim))
                        l
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
