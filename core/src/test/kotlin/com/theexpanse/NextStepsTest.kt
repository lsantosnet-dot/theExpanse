package com.theexpanse

import com.theexpanse.content.AiMode
import com.theexpanse.content.Builtin
import com.theexpanse.content.Validator
import com.theexpanse.core.Config
import com.theexpanse.core.Missile
import com.theexpanse.core.Owner
import com.theexpanse.core.Simulation
import com.theexpanse.core.Vec2
import com.theexpanse.core.World
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NextStepsTest {
    private val pack = Builtin.pack("rota_do_gelo")

    private fun sim(seed: Long = 1L) = Simulation(World(pack), seed)

    @Test
    fun newSolarBodiesHaveRealOrbits() {
        val w = World(pack)
        assertTrue(Validator.validate(pack).ok)
        val day = 86400.0
        assertEquals(87.97 * day, w.body("mercurio")!!.period, 0.01 * 87.97 * day)
        assertEquals(224.7 * day, w.body("venus")!!.period, 0.01 * 224.7 * day)
        assertEquals(10759.0 * day, w.body("saturno")!!.period, 0.01 * 10759.0 * day)
        // luas de Saturno acompanham o planeta
        val saturn = w.body("saturno")!!
        for (id in listOf("encelado", "reia", "titan")) {
            val m = w.body(id)!!
            val d = (m.position(1e6) - saturn.position(1e6)).length
            assertTrue(d in 2.0e8..1.3e9, "$id a $d m de Saturno")
        }
        // o Titã tem o período real, ≈ 15,95 dias
        assertEquals(15.95 * day, w.body("titan")!!.period, 0.03 * 15.95 * day)
    }

    @Test
    fun fleeingShipMovesAwayFromThePlayer() {
        val s = sim()
        val g = s.world.body("cruzador_aurora")!!
        s.setAi(g, AiMode.FLEE)
        val start = (s.position(g) - s.ship.pos).length
        repeat(600) { s.step(0.5) }
        val end = (s.position(g) - s.ship.pos).length
        assertTrue(end > start, "fugitivo foi de $start a $end")
        assertTrue(!s.isPursued(), "fuga não conta como perseguição")
    }

    @Test
    fun patrolVisitsEveryWaypointOfTheRoute() {
        val s = sim()
        val aurora = s.world.body("cruzador_aurora")!!
        s.setAi(aurora, AiMode.PATROL, listOf("ceres", "porto_ceres"))
        val visited = HashSet<Int>()
        repeat(4000) {
            s.step(1.0)
            visited.add(s.dynamics["cruzador_aurora"]!!.leg)
        }
        assertEquals(setOf(0, 1), visited)
        assertTrue(!s.isPursued())
    }

    @Test
    fun patrolSurvivesSaveAndLoad() {
        val s = sim()
        s.setAi(s.world.body("cruzador_aurora")!!, AiMode.PATROL, listOf("ceres", "porto_ceres"))
        s.step(1.0)
        val copy = sim(2L)
        copy.fromJson(s.toJson())
        val d = copy.dynamics["cruzador_aurora"]!!
        assertEquals(AiMode.PATROL, d.mode)
        assertEquals(listOf("ceres", "porto_ceres"), d.route)
    }

    @Test
    fun attackersDoNotStackMissilesBeyondTheCap() {
        val s = sim()
        for (i in 1..Config.MAX_INCOMING + 2) s.missiles.add(Missile(100 + i, Owner.ENEMY, s.ship.pos + Vec2(1e7, 0.0), s.ship.vel, "jogador", "gralha"))
        assertTrue(s.incoming().size > Config.MAX_INCOMING)
        val g = s.world.body("cruzador_aurora")!!
        s.setAi(g, AiMode.ATTACK)
        s.dynamics["cruzador_aurora"]!!.pos = s.ship.pos + Vec2(30_000.0, 0.0)
        s.dynamics["cruzador_aurora"]!!.vel = s.ship.vel
        val before = s.incoming().size
        repeat(60) { s.step(1.0) }
        assertTrue(s.incoming().size <= before, "não deve lançar mais mísseis acima do teto")
    }
}

class SoundTest {
    @Test
    fun everyEffectRendersAudibleBoundedPcm() {
        for (sfx in com.theexpanse.game.Sfx.values()) {
            val pcm = com.theexpanse.game.Synth.render(sfx)
            assertEquals((sfx.seconds * com.theexpanse.game.Synth.RATE).toInt(), pcm.size)
            val peak = pcm.maxOf { kotlin.math.abs(it.toInt()) }
            assertTrue(peak in 3000..32767, "$sfx pico $peak")
        }
    }

    @Test
    fun engineLoopSeamIsContinuous() {
        val pcm = com.theexpanse.game.Synth.render(com.theexpanse.game.Sfx.ENGINE)
        val jump = kotlin.math.abs(pcm.last().toInt() - pcm.first().toInt())
        assertTrue(jump < 4000, "salto na emenda do laço: $jump")
    }

    @Test
    fun gameEventsAndButtonsPlaySounds() {
        val played = ArrayList<com.theexpanse.game.Sfx>()
        val host = object : com.theexpanse.game.Host {
            override fun importPackage() {}
            override fun exportFile(fileName: String, text: String) {}
            override fun copyText(text: String) {}
            override fun promptText(title: String, initial: String, onResult: (String?) -> Unit) {}
            override fun play(sfx: com.theexpanse.game.Sfx) { played.add(sfx) }
        }
        val c = com.theexpanse.render.AppController(com.theexpanse.game.MemoryStorage(), host, seed = { 1L })
        c.press("home_play:albatroz")
        assertTrue(played.contains(com.theexpanse.game.Sfx.CLICK))
        val g = c.session!!
        g.sim.missiles.add(Missile(1, Owner.ENEMY, g.sim.ship.pos, g.sim.ship.vel, "jogador", "x"))
        c.update(0.05)
        c.update(0.05)
        assertTrue(played.contains(com.theexpanse.game.Sfx.HIT), "impacto deve tocar HIT, tocou $played")
    }
}
