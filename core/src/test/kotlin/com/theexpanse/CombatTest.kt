package com.theexpanse

import com.theexpanse.content.AiMode
import com.theexpanse.content.Builtin
import com.theexpanse.core.AutopilotMode
import com.theexpanse.core.Config
import com.theexpanse.core.FireResult
import com.theexpanse.core.Missile
import com.theexpanse.core.Owner
import com.theexpanse.core.SimEvent
import com.theexpanse.core.Simulation
import com.theexpanse.core.Status
import com.theexpanse.core.Vec2
import com.theexpanse.core.World
import com.theexpanse.core.decoyTiming
import com.theexpanse.game.GameSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CombatTest {
    private val patrulha = Builtin.pack("patrulha")
    private val emboscada = Builtin.pack("emboscada")
    private val calice = Builtin.pack("calice")

    /** Coloca a nave do jogador a [dist] metros do corpo, na mesma velocidade (alvo parado em relação a ela). */
    private fun placeNear(sim: Simulation, id: String, dist: Double) {
        val b = sim.world.body(id)!!
        val p = sim.position(b)
        sim.ship.pos = p + p.unit() * dist
        sim.ship.vel = sim.velocity(b)
    }

    @Test
    fun rolesAreFixedByThePackage() {
        assertEquals("militar", patrulha.role.kind.key)
        assertEquals("coalizao", patrulha.role.factionId)
        assertEquals("mercador", emboscada.role.kind.key)
        val m = Simulation(World(patrulha))
        assertEquals(10, m.ship.missiles)
        assertEquals(10, m.ship.decoys)
        assertEquals(220.0, m.ship.hullMax)
        val c = Simulation(World(calice))
        assertEquals(0, c.ship.missiles)
        assertEquals(FireResult.NOT_MILITARY, c.fireMissile())
    }

    @Test
    fun playerMissileDestroysTheTargetAndReportsIt() {
        val sim = Simulation(World(patrulha))
        placeNear(sim, "corvo", 60_000.0)
        assertEquals("corvo", sim.target.id)
        assertEquals(FireResult.FIRED, sim.fireMissile())
        assertEquals(9, sim.ship.missiles)
        var killed = false
        var steps = 0
        val seen = ArrayList<SimEvent>()
        while (!killed && steps++ < 2000) {
            sim.step(0.05)
            for (e in sim.drainEvents()) { seen.add(e); if (e is SimEvent.BodyDestroyed && e.id == "corvo") killed = true }
        }
        println("míssil acertou em ${sim.time.toInt()} s")
        assertTrue(killed, "o míssil deveria acertar um alvo parado")
        assertTrue("corvo" in sim.destroyed)
        assertTrue(sim.missiles.isEmpty())
        assertTrue(seen.any { it is SimEvent.Launched })
    }

    @Test
    fun impactEtaMatchesTheMissileFlightTime() {
        val sim = Simulation(World(emboscada))
        enemyMissileAt(sim, 40_000.0)
        val eta = sim.impactEta()!!
        var steps = 0
        val t0 = sim.time
        while (sim.drainEvents().none { it is SimEvent.Hit } && steps++ < 20_000) sim.step(0.05)
        val real = sim.time - t0
        println("ETA previsto ${eta.toInt()} s, real ${real.toInt()} s")
        assertTrue(Math.abs(eta - real) / real < 0.35, "previsto $eta, real $real")
    }

    @Test
    fun cannotFireOutOfRangeOrAtFriends() {
        val sim = Simulation(World(patrulha))
        assertEquals(FireResult.OUT_OF_RANGE, sim.canFire())
        sim.selectTarget(sim.world.body("cruzador_aurora")!!)
        placeNear(sim, "cruzador_aurora", 20_000.0)
        assertEquals(FireResult.FRIENDLY, sim.canFire())
        sim.selectTarget(sim.world.body("doca_meridiano")!!)
        assertEquals(FireResult.NO_TARGET, sim.canFire())
    }

    private fun enemyMissileAt(sim: Simulation, dist: Double): Missile {
        val dir = Vec2.fromAngle(0.7)
        val m = Missile(900, Owner.ENEMY, sim.ship.pos + dir * dist, sim.ship.vel, "jogador", "fragata_vigilia")
        sim.missiles.add(m)
        return m
    }

    @Test
    fun unopposedMissileHurtsTheHull() {
        val sim = Simulation(World(emboscada))
        enemyMissileAt(sim, 30_000.0)
        val hull0 = sim.ship.hull
        var hit: SimEvent.Hit? = null
        var steps = 0
        while (hit == null && steps++ < 5000) {
            sim.step(0.05)
            hit = sim.drainEvents().filterIsInstance<SimEvent.Hit>().firstOrNull()
        }
        assertTrue(hit != null, "o míssil deveria atingir")
        assertTrue(hit!!.damage in Config.MISSILE_DAMAGE_MIN..Config.MISSILE_DAMAGE_MAX)
        assertEquals(hull0 - hit.damage, sim.ship.hull, 1e-9)
        assertTrue(sim.ship.hull > 0.0, "um míssil não destrói um cargueiro inteiro")
    }

    @Test
    fun twoHitsDestroyAMerchant() {
        val sim = Simulation(World(emboscada))
        enemyMissileAt(sim, 20_000.0); enemyMissileAt(sim, 25_000.0)
        var steps = 0
        while (sim.status == Status.FLYING && steps++ < 5000) sim.step(0.05)
        assertEquals(Status.DESTROYED, sim.status)
        assertTrue(sim.drainEvents().any { it is SimEvent.ShipDestroyed })
    }

    @Test
    fun decoySuccessIsRandomAroundTheChanceShownForThatMoment() {
        var diverted = 0
        var expected = 0.0
        val n = 400
        for (seed in 1L..n) {
            val sim = Simulation(World(emboscada), seed)
            val m = enemyMissileAt(sim, 30_000.0)
            expected = sim.decoyChance(m)
            assertTrue(sim.deployDecoy())
            val ev = sim.drainEvents().filterIsInstance<SimEvent.DecoyResult>().single()
            assertEquals(1, ev.total)
            assertEquals(expected, ev.chance, 1e-9, "o evento informa a chance usada")
            if (m.targetId != "jogador") diverted++
            assertEquals(3, sim.ship.decoys)
        }
        val rate = diverted.toDouble() / n
        println("despistadores: $diverted de $n ($rate), esperado $expected")
        assertTrue(Math.abs(rate - expected) < 0.07, "taxa $rate, esperada $expected")
    }

    @Test
    fun launchingEarlyBeatsLaunchingLateAndTooEarlyIsWorseThanTheWindow() {
        assertTrue(decoyTiming(3.0) < decoyTiming(10.0) && decoyTiming(10.0) < decoyTiming(18.0) && decoyTiming(18.0) < decoyTiming(30.0))
        assertEquals(decoyTiming(20.0), decoyTiming(45.0), 1e-9, "a janela é plana de 20 a 45 s")
        assertTrue(decoyTiming(60.0) < decoyTiming(30.0), "cedo demais vale menos que a janela")
        assertTrue(decoyTiming(60.0) > decoyTiming(8.0), "mas ainda vale mais que em cima da hora")
        var prev = decoyTiming(0.0)
        for (e in 1..80) { val v = decoyTiming(e.toDouble()); assertTrue(Math.abs(v - prev) < 0.2, "sem saltos em $e s"); prev = v }

        val sim = Simulation(World(patrulha))
        val early = Missile(950, Owner.ENEMY, sim.ship.pos + Vec2(0.0, 40_000.0), sim.ship.vel, "jogador", "corvo")
        val late = Missile(951, Owner.ENEMY, sim.ship.pos + Vec2(0.0, 5_000.0), sim.ship.vel, "jogador", "corvo")
        assertTrue(sim.decoyChance(early) > sim.decoyChance(late))
        for (m in listOf(early, late)) assertTrue(sim.decoyChance(m) in Config.DECOY_CHANCE_MIN..Config.DECOY_CHANCE_MAX)
    }

    @Test
    fun theButtonOddsFollowTheMissileThatArrivesFirstAndTheMessageTellsTheChance() {
        val g = GameSession(emboscada, 4)
        val sim = g.sim
        assertEquals(null, sim.decoyOdds())
        val far = enemyMissileAt(sim, 60_000.0)
        val near = enemyMissileAt(sim, 12_000.0)
        assertEquals(sim.decoyChance(near), sim.decoyOdds()!!, 1e-12)
        assertTrue(com.theexpanse.render.Hud.oddsText(sim).endsWith("%"))
        g.decoy()
        val msg = g.messages.last().text
        assertTrue("chance de" in msg, msg)
        sim.missiles.remove(far)
    }

    @Test
    fun divertedMissileChasesTheFlareAndExplodesHarmlessly() {
        // acha uma semente em que o despistador funciona
        for (seed in 1L..50L) {
            val sim = Simulation(World(emboscada), seed)
            val m = enemyMissileAt(sim, 30_000.0)
            sim.deployDecoy()
            if (m.targetId == "jogador") continue
            val hull = sim.ship.hull
            var steps = 0
            while (sim.missiles.isNotEmpty() && steps++ < 5000) sim.step(0.05)
            assertTrue(sim.missiles.isEmpty())
            assertEquals(hull, sim.ship.hull, "o míssil enganado não fere a nave")
            return
        }
        error("nenhuma semente enganou o míssil")
    }

    @Test
    fun noDecoyWithoutIncomingMissileAndAmmoIsLimited() {
        val sim = Simulation(World(emboscada))
        assertFalse(sim.deployDecoy())
        assertEquals(4, sim.ship.decoys)
        repeat(4) { enemyMissileAt(sim, 40_000.0); assertTrue(sim.deployDecoy()); sim.missiles.clear() }
        enemyMissileAt(sim, 40_000.0)
        assertFalse(sim.deployDecoy(), "acabaram os despistadores")
    }

    @Test
    fun missilesForceRealTime() {
        val sim = Simulation(World(emboscada))
        sim.setWarp(4)
        sim.advance(1.0 / 60)
        assertTrue(sim.effectiveWarp > 1.0)
        enemyMissileAt(sim, 500_000.0)
        sim.advance(1.0 / 60)
        assertEquals(1.0, sim.effectiveWarp)
    }

    @Test
    fun attackingFrigateFiresMissilesAtTheMerchant() {
        val g = GameSession(emboscada, 11)
        // o rádio chama logo no início, a fragata está a ~60 km
        assertEquals("d_abordagem", g.dialogue?.id)
        g.choose(1)
        assertEquals(AiMode.ATTACK, g.sim.dynamics["fragata_vigilia"]!!.mode)
        g.sim.setThrust(100)
        g.sim.setAutopilot(AutopilotMode.INTERCEPT)
        var launched = 0
        var guard = 0
        var decoysUsed = 0
        while (!g.paused && g.sim.status == Status.FLYING && guard++ < 1_000_000) {
            val before = g.messages.size
            g.stepFixed(0.1)
            for (m in g.messages.drop(before)) if (m.text.startsWith("ALERTA")) launched++
            val eta = g.sim.impactEta()
            if (eta != null && eta < 12.0 && g.sim.ship.decoys > 0) { g.decoy(); decoysUsed++ }
        }
        println("mísseis lançados: $launched, despistadores usados: $decoysUsed, casco ${g.sim.ship.hull.toInt()}, final ${g.ending?.id}")
        assertTrue(launched >= 1, "a fragata deveria ter disparado")
        assertTrue(decoysUsed >= 1)
        assertTrue(g.ending != null)
    }

    @Test
    fun attackIsDeterministicForTheSameSeed() {
        fun run(): String {
            val g = GameSession(emboscada, 5)
            g.choose(1)
            g.sim.setThrust(100); g.sim.setAutopilot(AutopilotMode.INTERCEPT)
            var guard = 0
            while (!g.paused && g.sim.status == Status.FLYING && guard++ < 400_000) {
                g.stepFixed(0.1)
                val eta = g.sim.impactEta()
                if (eta != null && eta < 12.0 && g.sim.ship.decoys > 0) g.decoy()
            }
            return "${g.ending?.id} ${g.sim.time} ${g.sim.ship.hull}"
        }
        assertEquals(run(), run())
    }

    @Test
    fun militaryCampaignCanBeWonByShootingTheCorsair() {
        val g = GameSession(patrulha, 3)
        g.sim.setThrust(100)
        g.sim.setAutopilot(AutopilotMode.INTERCEPT)
        var guard = 0
        while (!g.paused && g.sim.status == Status.FLYING && guard++ < 2_000_000) g.stepFixed(Config.SUBSTEP)
        assertEquals("d_corvo", g.dialogue?.id)
        g.choose(1) // abrir fogo sem aviso
        assertTrue(g.story.flags["hostil"] == true)
        // aproxima-se até o alcance e dispara até abater (ou até acabar a munição)
        guard = 0
        while (g.ending == null && g.sim.status == Status.FLYING && guard++ < 400_000) {
            g.stepFixed(0.1)
            if (g.sim.distanceTo() < 100_000.0 && g.sim.ship.missiles > 0 && g.sim.missiles.none { it.owner == Owner.PLAYER }) g.fire()
            val eta = g.sim.impactEta()
            if (eta != null && eta < 12.0 && g.sim.ship.decoys > 0) g.decoy()
        }
        println("patrulha: final ${g.ending?.id} em ${g.sim.time.toInt()} s, mísseis restantes ${g.sim.ship.missiles}, casco ${g.sim.ship.hull.toInt()}")
        assertTrue(g.ending?.id == "abatido" || g.ending?.id == "perdido", "final ${g.ending?.id}")
    }

    @Test
    fun surrenderOrdersCanEndTheMissionPeacefully() {
        var won = false
        for (seed in 1L..40L) {
            val g = GameSession(patrulha, seed)
            g.sim.setThrust(100); g.sim.setAutopilot(AutopilotMode.INTERCEPT)
            var guard = 0
            while (!g.paused && g.sim.status == Status.FLYING && guard++ < 2_000_000) g.stepFixed(Config.SUBSTEP)
            g.choose(0)
            if (g.ending?.id == "capturado") { won = true; break }
        }
        assertTrue(won)
    }

    @Test
    fun dockingRepairsAndRearms() {
        val sim = Simulation(World(patrulha))
        sim.ship.hull = 50.0; sim.ship.missiles = 1; sim.ship.decoys = 0
        sim.selectTarget(sim.world.body("doca_meridiano")!!)
        placeNear(sim, "doca_meridiano", 10_000.0)
        sim.step(0.14)
        assertEquals(Status.DOCKED, sim.status)
        assertEquals(220.0, sim.ship.hull)
        assertEquals(10, sim.ship.missiles)
        assertEquals(10, sim.ship.decoys)
    }

    @Test
    fun combatStateSurvivesSaveAndLoad() {
        val g = GameSession(emboscada, 11)
        g.choose(1)
        var guard = 0
        while (g.sim.missiles.isEmpty() && guard++ < 100_000) g.stepFixed(0.1)
        assertTrue(g.sim.missiles.isNotEmpty())
        val h = GameSession(emboscada, 1)
        h.fromJson(kotlinx.serialization.json.Json.parseToJsonElement(g.toJson().toString()) as kotlinx.serialization.json.JsonObject)
        assertEquals(g.sim.missiles.size, h.sim.missiles.size)
        assertEquals(g.sim.missiles[0].pos, h.sim.missiles[0].pos)
        assertEquals(g.sim.ship.decoys, h.sim.ship.decoys)
        assertEquals(AiMode.ATTACK, h.sim.dynamics["fragata_vigilia"]!!.mode)
    }

    @Test
    fun decoyIsEjectedSidewaysAndFliesAwayInAStraightLine() {
        val sim = Simulation(World(emboscada))
        val m = enemyMissileAt(sim, 30_000.0)
        assertTrue(sim.deployDecoy())
        val f = sim.flares.single()
        val rel = f.vel - sim.ship.vel
        assertEquals(Config.DECOY_EJECT_SPEED, rel.length, 1e-6)
        val los = (sim.ship.pos - m.pos).unit()
        assertEquals(0.0, rel.unit() dot los, 1e-9, "sai de lado, perpendicular à linha de visada do míssil")
        assertEquals(0.0, (f.pos - sim.ship.pos).length, 1e-9, "nasce na nave")
        sim.missiles.clear()
        repeat(200) { sim.step(0.05) } // 10 s
        val away = (sim.flares.single().pos - sim.ship.pos).length
        assertTrue(away in 1_400.0..1_600.0, "10 s depois está a ${away.toInt()} m da nave")
    }

    @Test
    fun aDivertedMissileFollowsTheDecoyAwayInsteadOfPassingNearTheShip() {
        for (seed in 1L..50L) {
            val sim = Simulation(World(emboscada), seed)
            val m = enemyMissileAt(sim, 30_000.0)
            sim.deployDecoy()
            if (m.targetId == "jogador") continue
            var closest = Double.MAX_VALUE
            var steps = 0
            while (sim.missiles.isNotEmpty() && steps++ < 5000) {
                sim.step(0.05)
                sim.missiles.firstOrNull()?.let { closest = minOf(closest, (it.pos - sim.ship.pos).length) }
            }
            println("míssil enganado passou a no mínimo ${closest.toInt()} m da nave")
            assertTrue(closest > 1_500.0, "passou a ${closest.toInt()} m da nave: o despistador não é um campo de força")
            return
        }
        error("nenhuma semente enganou o míssil")
    }

    @Test
    fun anEnemyDecoyAlsoLeavesItsShipAtEjectionSpeed() {
        val sim = Simulation(World(patrulha))
        placeNear(sim, "corvo", 40_000.0)
        val foe = sim.world.body("corvo")!!
        val m = Missile(901, Owner.PLAYER, sim.ship.pos, sim.ship.vel, foe.id, "jogador")
        m.decoyAt = 0.0
        sim.missiles.add(m)
        sim.step(0.05)
        val f = sim.flares.single()
        assertEquals(Config.DECOY_EJECT_SPEED, (f.vel - sim.velocity(foe)).length, 0.1)
        assertEquals(f.id, m.targetId, "o míssil passa a perseguir o despistador")
    }
}
