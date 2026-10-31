package com.theexpanse

import com.theexpanse.content.Builtin
import com.theexpanse.core.AutopilotMode
import com.theexpanse.core.Config
import com.theexpanse.core.Status
import com.theexpanse.game.GameSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class StoryTest {
    private val calice = Builtin.pack("calice")
    private val albatroz = Builtin.pack("albatroz")

    /** Voa em passos fixos até algo pausar o jogo (diálogo, final), a nave atracar, cair ou o tempo acabar. */
    private fun fly(g: GameSession, until: Double = 200_000.0) {
        while (!g.paused && g.sim.status == Status.FLYING && g.sim.time < until) g.stepFixed(Config.SUBSTEP)
    }

    private fun autoTo(g: GameSession, bodyId: String, pct: Int = 100) {
        val sim = g.sim
        if (sim.status == Status.DOCKED) g.undock()
        sim.selectTarget(g.world.body(bodyId)!!)
        sim.setThrust(pct)
        sim.setAutopilot(AutopilotMode.INTERCEPT)
        fly(g)
    }

    private fun caliceUntilRadio(seed: Long = 1): GameSession {
        val g = GameSession(calice, seed)
        g.sim.setAutopilot(AutopilotMode.INTERCEPT)
        fly(g)
        assertEquals("d_radio", g.dialogue?.id, "o rádio deveria chamar")
        return g
    }

    @Test
    fun radioCallHappensAround18900Seconds() {
        val g = caliceUntilRadio()
        println("rádio em ${g.sim.time.toInt()} s")
        assertTrue(kotlin.math.abs(g.sim.time - 18_900.0) / 18_900.0 <= 0.05, "t=${g.sim.time}")
        assertTrue(g.sim.distanceTo(g.world.body("fragata_tribunal")!!) < 8e7)
        assertTrue(g.paused)
    }

    @Test
    fun escortChoiceDeliversWithMarsHeadline() {
        val g = caliceUntilRadio()
        assertTrue(g.choices().first { it.index == 2 }.enabled)
        g.choose(2)
        assertEquals(1, g.story.reputation["federacao"]!! - 0 - 9, "federação +10")
        assertEquals(350, g.story.credits, "500 - 150")
        fly(g)
        assertEquals("entregou_escolta", g.ending?.id)
        assertEquals(350 + 1200, g.story.credits)
        assertTrue(g.ending!!.headline.startsWith("Marte escolta cargueiro"))
        assertEquals("concluida", g.story.missions["m_entrega"])
    }

    @Test
    fun escortIsBlockedWhenMarsHatesYou() {
        val g = GameSession(calice, 1)
        g.story.reputation["federacao"] = -30
        g.sim.setAutopilot(AutopilotMode.INTERCEPT)
        fly(g)
        val c = g.choices().first { it.index == 2 }
        assertFalse(c.enabled)
        assertNotNull(c.blocked)
        g.choose(2) // tentar escolher bloqueada não faz nada
        assertEquals("d_radio", g.dialogue?.id)
    }

    @Test
    fun declaringTheCargoLosesTheContract() {
        val g = caliceUntilRadio()
        val coal = g.story.reputation["coalizao"]!!
        g.choose(0)
        assertEquals("declarou", g.ending?.id)
        assertEquals(0.0, g.sim.ship.cargo)
        assertEquals(coal + 8, g.story.reputation["coalizao"])
        assertEquals("falhou", g.story.missions["m_entrega"])
        assertTrue(g.paused)
        g.continueFlying()
        assertFalse(g.paused)
    }

    @Test
    fun lyingSucceedsOrFailsByChance() {
        var sawSuccess = false
        var sawFailure = false
        for (seed in 1L..60L) {
            val g = caliceUntilRadio(seed)
            val shown = g.choices().first { it.index == 1 }.detail!!
            assertTrue(shown.contains("42%") || shown.contains("43%"), shown) // 0,35 + 15 × 0,005 = 42,5%
            g.choose(1)
            if (g.story.flags["mentiu"] == true) {
                sawSuccess = true
                fly(g)
                assertEquals("entregou_mentiu", g.ending?.id)
                assertEquals(500 + 1200, g.story.credits)
                assertEquals(15 - 10, g.story.reputation["coalizao"], "a Coalizão descobre ao entregar")
            } else {
                sawFailure = true
                assertEquals("mentira_descoberta", g.ending?.id)
                assertEquals(200, g.story.credits)
                assertEquals(0.0, g.sim.ship.cargo)
            }
            if (sawSuccess && sawFailure) break
        }
        assertTrue(sawSuccess && sawFailure, "ambos os resultados deveriam aparecer")
    }

    @Test
    fun cuttingTheRadioStartsAPursuitCappedByDistance() {
        val g = caliceUntilRadio()
        g.choose(3)
        assertTrue(g.sim.isPursued())
        g.sim.setWarp(4)
        g.sim.advance(1.0 / 60)
        val nearest = g.sim.dynamics.keys.minOf { g.sim.distanceTo(g.sim.world.body(it)!!) }
        assertTrue(g.sim.effectiveWarp <= 120.0 * maxOf(1.0, nearest / 200_000.0) + 1e-6, "fator ${g.sim.effectiveWarp}")
        fly(g)
        println("perseguição terminou em ${g.sim.time.toInt()} s com final ${g.ending?.id}")
        assertTrue(g.ending?.id == "entregou_fugiu" || g.ending?.id == "abordado", "final ${g.ending?.id}")
        if (g.ending?.id == "entregou_fugiu") assertFalse(g.sim.isPursued() && g.sim.dynamics.values.any { it.mode == com.theexpanse.content.AiMode.PURSUE })
    }

    @Test
    fun crashingIntoThePlanetEndsTheMission() {
        val g = GameSession(calice, 1)
        // mergulha: queima contra a velocidade orbital até cair
        g.sim.setAutopilot(AutopilotMode.RETROGRADE)
        g.sim.selectTarget(g.world.body("doca_meridiano")!!)
        var steps = 0
        while (g.sim.status == Status.FLYING && steps++ < 2_000_000) {
            g.sim.setAutopilot(AutopilotMode.OFF)
            g.sim.manualEngine = true
            // aponta contra a velocidade
            g.sim.ship.angle = (g.sim.ship.vel * -1.0).angle
            g.stepFixed(Config.SUBSTEP)
            if (g.sim.ship.fuel <= 0) g.sim.manualEngine = false
        }
        assertEquals(Status.CRASHED, g.sim.status)
        assertEquals("destruido", g.ending?.id)
    }

    // ---- O silêncio do Albatroz: partida completa pelo piloto automático ----

    @Test
    fun albatrozCanBeSolvedFromStartToFinish() {
        val g = GameSession(albatroz, 7)
        autoTo(g, "destrocos_albatroz")
        assertEquals(Status.DOCKED, g.sim.status)
        assertTrue("caixa_preta" in g.story.objects && "f_sem_registro" in g.story.facts)
        assertEquals(40.0, g.sim.ship.extra, 1e-9, "a caixa-preta entra no porão com a sua massa")

        autoTo(g, "estacao_ceifa")
        assertEquals("estacao_ceifa", g.sim.dockedAt?.id)
        assertEquals(listOf("ilse_moraga"), g.charactersAt(g.sim.dockedAt!!).map { it.id })
        g.talkTo("ilse_moraga")
        assertTrue(g.choices().first { it.index == 1 }.enabled, "a pista da caixa-preta libera a escolha")
        g.choose(1)
        assertTrue("f_homem_terno" in g.story.facts)

        autoTo(g, "lab_helena")
        g.talkTo("dra_okafor")
        g.choose(0)
        assertEquals("d_okafor_fim", g.dialogue?.id, "o diálogo encadeia")
        g.choose(-1)
        assertTrue(g.dialogue == null)
        assertTrue(listOf("f_sabotagem", "f_codigo_marte").all { it in g.story.facts } && "relatorio_lab" in g.story.objects)

        autoTo(g, "arquivo_vesta")
        g.talkTo("noor"); g.choose(0)
        g.talkTo("noor"); g.choose(1)
        assertTrue(listOf("f_codigo_roubado", "f_apolice").all { it in g.story.facts })

        assertEquals(null, g.deduce(setOf("f_sabotagem", "f_apolice")), "pistas que não combinam")
        assertNotNull(g.deduce(setOf("f_codigo_marte", "f_codigo_roubado")))
        assertNotNull(g.deduce(setOf("f_homem_terno", "f_apolice")))
        assertEquals(setOf("ded_armacao", "ded_atlas"), g.story.deduced)

        assertTrue(g.accuse("s_atlas"))
        assertEquals("verdade", g.ending?.id)
        assertEquals("concluida", g.story.missions["m_verdade"])
        println("Albatroz resolvido em T+${g.sim.time.toInt()} s")
    }

    @Test
    fun accusingTooEarlyOrTheWrongPersonEndsBadly() {
        val early = GameSession(albatroz, 1)
        early.accuse("s_atlas")
        assertEquals("sem_provas", early.ending?.id)
        val marte = GameSession(albatroz, 1)
        marte.accuse("s_marte")
        assertEquals("guerra_injusta", marte.ending?.id)
        assertEquals(5 - 40, marte.story.reputation["federacao"])
    }

    @Test
    fun accusingTwiceDoesNotRepeatTheEffects() {
        val g = GameSession(albatroz, 1)
        g.accuse("s_marte")
        val rep = g.story.reputation["federacao"]
        g.continueFlying()
        assertFalse(g.accuse("s_marte"))
        assertEquals(rep, g.story.reputation["federacao"])
    }

    @Test
    fun timeRuleAddsAHeadline() {
        val g = GameSession(albatroz, 1)
        val before = g.story.news.size
        g.sim.setWarp(0)
        repeat(3) { g.sim.setAutopilot(AutopilotMode.OFF) }
        while (g.sim.time < 259_300) g.stepFixed(60.0)
        assertTrue(g.story.news.size > before && g.story.news.first().startsWith("Famílias"))
    }

    @Test
    fun saveAndLoadRestoresTheWholeGame() {
        val g = GameSession(albatroz, 7)
        autoTo(g, "destrocos_albatroz")
        autoTo(g, "estacao_ceifa")
        g.talkTo("ilse_moraga")
        val json = g.toJson().toString()
        val h = GameSession(albatroz, 99)
        h.fromJson(kotlinx.serialization.json.Json.parseToJsonElement(json) as kotlinx.serialization.json.JsonObject)
        assertEquals(g.sim.time, h.sim.time)
        assertEquals(g.sim.ship.pos, h.sim.ship.pos)
        assertEquals(g.sim.dockedAt?.id, h.sim.dockedAt?.id)
        assertEquals(g.story.facts, h.story.facts)
        assertEquals(g.story.objects, h.story.objects)
        assertEquals("d_ilse", h.dialogue?.id)
        assertEquals(g.story.credits, h.story.credits)
        h.choose(1)
        assertTrue("f_homem_terno" in h.story.facts)
    }

    @Test
    fun savedPursuitKeepsTheFrigateState() {
        val g = caliceUntilRadio()
        g.choose(3)
        repeat(2000) { g.stepFixed(Config.SUBSTEP) }
        val h = GameSession(calice, 5)
        h.fromJson(kotlinx.serialization.json.Json.parseToJsonElement(g.toJson().toString()) as kotlinx.serialization.json.JsonObject)
        assertEquals(g.sim.dynamics["fragata_tribunal"]!!.pos, h.sim.dynamics["fragata_tribunal"]!!.pos)
        assertTrue(h.sim.isPursued())
    }

    @Test
    fun restartRestoresTheExactInitialState() {
        val g = GameSession(albatroz, 3)
        val pos = g.sim.ship.pos
        val vel = g.sim.ship.vel
        autoTo(g, "destrocos_albatroz")
        g.restart()
        assertEquals(pos, g.sim.ship.pos)
        assertEquals(vel, g.sim.ship.vel)
        assertEquals(0.0, g.sim.time)
        assertTrue(g.story.facts.isEmpty() && g.story.objects.isEmpty())
        assertEquals(800, g.story.credits)
        assertEquals(0.0, g.sim.ship.extra)
    }
}
