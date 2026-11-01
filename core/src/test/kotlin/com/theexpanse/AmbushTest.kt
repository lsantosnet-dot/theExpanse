package com.theexpanse

import com.theexpanse.content.Builtin
import com.theexpanse.content.Decoder
import com.theexpanse.content.Validator
import com.theexpanse.game.GameSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AmbushTest {
    private fun ambushed(seed: Long, rep: Int?, noCargo: Boolean = false, farFromCeres: Boolean = false): Boolean {
        val g = GameSession(Builtin.pack("rota_do_gelo"), seed)
        g.choose(1) // segue sozinho (sem escolta)
        if (rep != null) g.story.reputation["corsarios"] = rep
        if (noCargo) g.sim.ship.cargo = 0.0
        if (farFromCeres) g.sim.ship.pos = g.world.body("marte")!!.position(g.sim.time) + com.theexpanse.core.Vec2(5e9, 0.0)
        repeat(1500) { g.stepFixed(30.0) } // 12,5 h de missão, 4 sorteios
        return g.story.fired.contains("e_gralha")
    }

    private fun rate(rep: Int?, noCargo: Boolean = false, far: Boolean = false) = (1L..200L).count { ambushed(it, rep, noCargo, far) } / 200.0

    @Test
    fun ambushHappensSometimesAndNotAlways() {
        val r = rate(null)
        println("EMBOSCADA padrão (rep -50): $r")
        assertTrue(r in 0.3..0.97, "chance observada $r")
    }

    @Test
    fun reputationChangesTheOdds() {
        val hated = rate(-100)
        val liked = rate(100)
        println("EMBOSCADA rep -100: $hated, rep +100: $liked")
        assertTrue(hated > liked + 0.25, "odiado $hated contra querido $liked")
    }

    @Test
    fun noCargoMakesItLessLikely() {
        val withCargo = rate(0)
        val without = rate(0, noCargo = true)
        println("EMBOSCADA com carga: $withCargo, sem carga: $without")
        assertTrue(without < withCargo)
    }

    @Test
    fun neverHappensFarFromTheLurkingZone() {
        assertEquals(0.0, rate(-100, far = true), "a região é perto de Ceres")
    }

    @Test
    fun ambushIsMessagedAndOnlyOncePerGame() {
        val g = (1L..50L).map { GameSession(Builtin.pack("rota_do_gelo"), it) }.first { s ->
            s.choose(1); repeat(1500) { s.stepFixed(30.0) }; s.story.fired.contains("e_gralha")
        }
        assertTrue(g.sim.dynamics.containsKey("gralha"))
        assertTrue(g.messages.any { it.text.startsWith("CONTATO HOSTIL") })
        assertEquals(true, g.story.flags["emboscada_e_gralha"])
        assertTrue(g.story.news.any { it.contains("Corsários atacam") })
    }

    @Test
    fun invalidAmbushesAreRejected() {
        val base = Builtin.text("rota_do_gelo")!!
        val bad = base.replace("\"nave\": \"gralha\", \"faccao\": \"corsarios\", \"chance\": 0.25", "\"nave\": \"nao_existe\", \"faccao\": \"corsarios\", \"chance\": 1.7")
        val r = Decoder.decode(bad.replace("\"id\": \"rota_do_gelo\"", "\"id\": \"x_emb\""))
        val v = Validator.validate(r.pack!!)
        assertTrue(v.errors.any { it.contains("emboscada 'e_gralha'") }, v.errors.toString())
        assertNotNull(r.pack)
    }
}
