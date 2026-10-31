package com.theexpanse

import com.theexpanse.content.Builtin
import com.theexpanse.core.AutopilotMode
import com.theexpanse.core.Config
import com.theexpanse.core.Missile
import com.theexpanse.core.Owner
import com.theexpanse.core.Simulation
import com.theexpanse.core.Vec2
import com.theexpanse.core.World
import com.theexpanse.game.MemoryStorage
import com.theexpanse.render.AppController
import com.theexpanse.render.Layout
import com.theexpanse.render.RadarView
import com.theexpanse.render.Screen
import com.theexpanse.render.ViewMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RadarTest {
    private val patrulha = Builtin.pack("patrulha")
    private val calice = Builtin.pack("calice")

    private fun controller() = AppController(MemoryStorage(), seed = { 1L })

    private fun AppController.tap(id: String) {
        val b = frame().buttons.firstOrNull { it.id == id } ?: error("botão '$id' não está na tela $screen")
        assertTrue(b.enabled, "botão $id desabilitado")
        press(id)
        release(id)
    }

    private fun military(): AppController = controller().also { it.tap("home_play:patrulha") }

    // ---- como chegar ao radar ----

    @Test
    fun radarButtonExistsOnlyForTheMilitaryRole() {
        val merchant = controller()
        merchant.tap("home_play:calice")
        assertTrue(merchant.frame().buttons.none { it.id == "radar" })

        val c = military()
        assertTrue(c.frame().buttons.any { it.id == "radar" })
        c.tap("radar")
        assertEquals(ViewMode.RADAR, c.view)
        val ids = c.frame().buttons.map { it.id }
        for (id in listOf("radar_exit", "radar_zoom_in", "radar_zoom_out", "radar_zero", "radar_free", "target", "fire", "decoy", "warp_0")) assertTrue(id in ids, "falta o botão $id no radar")
        c.tap("radar_exit")
        assertEquals(ViewMode.COCKPIT, c.view)
    }

    @Test
    fun exitReturnsToTheViewYouCameFromAndBackKeyLeavesTheRadarFirst() {
        val c = military()
        c.tap("view")
        assertEquals(ViewMode.EXTERNAL, c.view)
        c.tap("radar")
        assertEquals(ViewMode.RADAR, c.view)
        assertTrue(c.onBack())
        assertEquals(ViewMode.EXTERNAL, c.view)
        assertEquals(Screen.FLIGHT, c.effective(), "voltar sai do radar, não abre o menu")
    }

    @Test
    fun theViewButtonNeverCyclesIntoTheRadar() {
        var v = ViewMode.COCKPIT
        repeat(10) { v = v.next(); assertTrue(v != ViewMode.RADAR) }
        assertEquals(ViewMode.COCKPIT, ViewMode.SPACE3D.next())
    }

    @Test
    fun merchantCannotEnterTheRadar() {
        val c = controller()
        c.tap("home_play:calice")
        c.press("radar")
        assertEquals(ViewMode.COCKPIT, c.view)
    }

    // ---- botão piscando ----

    private fun activeSamples(c: AppController, id: String): Set<Boolean> {
        val seen = HashSet<Boolean>()
        repeat(60) { c.update(1.0 / 60); seen.add(c.frame().buttons.first { it.id == id }.active) }
        return seen
    }

    @Test
    fun radarButtonBlinksWhenThereIsABattleAndStaysStillOtherwise() {
        val c = military()
        val sim = c.session!!.sim
        assertFalse(sim.battleAlert())
        assertEquals(setOf(false), activeSamples(c, "radar"))

        // um míssil do jogador em voo (você atirou primeiro) já liga o alerta
        sim.missiles.add(Missile(7, Owner.PLAYER, sim.ship.pos + Vec2(30_000.0, 0.0), sim.ship.vel, "corvo", "jogador"))
        assertTrue(sim.battleAlert())
        assertEquals(setOf(true, false), activeSamples(c, "radar"))
    }

    @Test
    fun enemyMissileAlsoTriggersTheAlert() {
        val sim = Simulation(World(patrulha))
        sim.missiles.add(Missile(8, Owner.ENEMY, sim.ship.pos + Vec2(0.0, 40_000.0), sim.ship.vel, "jogador", "corvo"))
        assertTrue(sim.battleAlert())
        assertEquals(sim.impactEta()!!, sim.missileEta(sim.missiles[0])!!, 1e-9)
    }

    // ---- desenho ----

    @Test
    fun radarDrawsInsideTheScreenAtEveryZoomWithMissilesAndFlares() {
        val c = military()
        val sim = c.session!!.sim
        sim.missiles.add(Missile(7, Owner.PLAYER, sim.ship.pos + Vec2(30_000.0, 5_000.0), sim.ship.vel + Vec2(100.0, 0.0), "corvo", "jogador"))
        sim.missiles.add(Missile(8, Owner.ENEMY, sim.ship.pos + Vec2(-20_000.0, 9_000.0), sim.ship.vel, "jogador", "corvo"))
        c.tap("radar")
        for (z in RadarView.ZOOMS_KM.indices) {
            c.radar.zoom = z
            val f = c.frame()
            assertTrue(f.prims.size > 40)
            for (b in f.buttons) assertTrue(b.x >= 0 && b.y >= 0 && b.x + b.w <= Layout.W + 0.5f && b.y + b.h <= Layout.H + 0.5f, "botão ${b.id} fora da tela")
        }
        c.radar.zoom = 2
        c.tap("radar_zoom_out")
        assertEquals(3, c.radar.zoom)
        assertFalse(c.frame().buttons.first { it.id == "radar_zoom_out" }.enabled, "no zoom mais aberto o botão − para")
        repeat(3) { c.tap("radar_zoom_in") }
        assertEquals(0, c.radar.zoom)
        assertFalse(c.frame().buttons.first { it.id == "radar_zoom_in" }.enabled)
    }

    // ---- piloto de velocidade ----

    @Test
    fun velocityPilotReachesTheDesiredRelativeVelocityAndHoldsIt() {
        val sim = Simulation(World(patrulha))
        val ref = sim.world.body("corvo")!!
        val p = sim.position(ref)
        sim.ship.pos = p + p.unit() * 60_000.0 // em combate o alvo está a dezenas de km
        sim.ship.vel = sim.velocity(ref)
        val start = sim.ship.vel - sim.velocity(ref)
        val want = start + Vec2(60.0, -80.0) // 100 m/s de diferença
        val fuel0 = sim.ship.fuel
        sim.commandVelocity(want, ref)
        assertEquals(AutopilotMode.VELOCITY, sim.autopilot)
        repeat(8000) { sim.step(0.1) }
        val now = sim.ship.vel - sim.velocity(ref)
        assertTrue((now - want).length < 0.5, "erro ${(now - want).length} m/s")
        assertEquals(AutopilotMode.VELOCITY, sim.autopilot, "continua engajado, segurando a velocidade")
        assertTrue(sim.ship.fuel < fuel0, "queimou combustível")
        val burning = sim.ship.burnAccel
        assertTrue(burning < 0.02, "depois de chegar, o motor quase não trabalha ($burning m/s²)")
    }

    @Test
    fun velocityPilotNeverBurnsFasterThanTheThrustSetting() {
        val sim = Simulation(World(patrulha))
        sim.commandVelocity(Vec2(900.0, 0.0), sim.target)
        var peak = 0.0
        repeat(2000) { sim.step(0.1); peak = maxOf(peak, sim.ship.burnAccel) }
        assertTrue(peak <= sim.accelLimit + 1e-9, "pico $peak > ${sim.accelLimit}")
        assertTrue(peak > 0.0)
    }

    @Test
    fun manualControlOrDestroyedReferenceReleasesTheVelocityPilot() {
        val sim = Simulation(World(patrulha))
        sim.commandVelocity(Vec2.ZERO, sim.target)
        sim.manualTurn = 1
        sim.step(0.1)
        assertEquals(AutopilotMode.OFF, sim.autopilot)
        sim.manualTurn = 0

        val foe = sim.world.body("corvo")!!
        sim.commandVelocity(Vec2.ZERO, foe)
        sim.step(0.1)
        assertEquals(AutopilotMode.VELOCITY, sim.autopilot)
        sim.destroyed.add(foe.id)
        sim.step(0.1)
        assertEquals(AutopilotMode.OFF, sim.autopilot, "sem a referência, o piloto se solta")
        sim.releaseVelocity() // já solto: não faz nada
        assertEquals(AutopilotMode.OFF, sim.autopilot)
    }

    // ---- toque ----

    @Test
    fun draggingTheShipSetsTheDesiredVelocityFromThePositionOfTheFinger() {
        val c = military()
        c.tap("radar")
        c.frame()
        val sim = c.session!!.sim
        assertFalse(c.touchDown(RadarView.AREA_W + 20f, 100f), "o painel da direita é dos botões")
        assertTrue(c.touchDown(RadarView.CX + 4f, RadarView.CY - 3f))
        c.touchMove(RadarView.CX + 5f, RadarView.CY - 2f)
        assertEquals(AutopilotMode.OFF, sim.autopilot, "um tremor do dedo não engaja nada")
        val k = RadarView.scale(c.radar)
        c.touchMove(RadarView.CX + 60f, RadarView.CY - 30f) // 60 px à direita e 30 acima
        assertEquals(AutopilotMode.VELOCITY, sim.autopilot)
        val want = sim.desiredRelVel!!
        assertEquals(60.0 / k / RadarView.HORIZON, want.x, 1e-9)
        assertEquals(30.0 / k / RadarView.HORIZON, want.y, 1e-9)
        assertEquals(sim.target, sim.velocityRef)

        // além do alcance da seta a meta fica no limite, na mesma direção
        c.touchMove(RadarView.CX + 600f, RadarView.CY)
        assertEquals(150.0 / k / RadarView.HORIZON, sim.desiredRelVel!!.length, 1e-6)
        assertEquals(0.0, sim.desiredRelVel!!.y, 1e-9)
        c.touchUp()
        c.touchMove(RadarView.CX, RadarView.CY - 100f)
        assertEquals(0.0, sim.desiredRelVel!!.y, 1e-9, "depois de soltar o dedo, mover não muda a meta")
    }

    @Test
    fun theGrabbedVectorHandleCanBeDraggedAgainAndFreeAndZeroWork() {
        val c = military()
        c.tap("radar")
        val sim = c.session!!.sim
        c.frame()
        c.touchDown(RadarView.CX, RadarView.CY)
        c.touchMove(RadarView.CX + 70f, RadarView.CY)
        c.touchUp()
        c.frame() // o desenho publica a posição da alça
        assertTrue(c.radar.handleOn)
        assertTrue(c.touchDown(c.radar.handleX + 3f, c.radar.handleY - 2f), "a alça da meta também é arrastável")
        c.touchMove(c.radar.handleX + 30f, c.radar.handleY)
        c.touchUp()
        assertTrue(sim.desiredRelVel!!.x > 0.0)

        c.tap("radar_zero")
        assertEquals(AutopilotMode.VELOCITY, sim.autopilot)
        assertEquals(0.0, sim.desiredRelVel!!.length, 1e-12)
        c.tap("radar_free")
        assertEquals(AutopilotMode.OFF, sim.autopilot)
    }

    @Test
    fun touchOnAShipSelectsItAndTouchIsIgnoredOutsideTheRadar() {
        val c = military()
        val sim = c.session!!.sim
        assertFalse(c.touchDown(RadarView.CX, RadarView.CY), "fora do radar o toque segue para a câmera")
        c.tap("radar")
        c.frame()
        val hit = c.radar.hits.firstOrNull { sim.world.body(it.id)?.id != sim.target.id }
        assertNotNull(hit, "algum corpo aparece no radar (ao menos como seta na borda)")
        assertTrue(c.touchDown(hit.x, hit.y))
        assertEquals(hit.id, sim.target.id)
    }

    @Test
    fun combatButtonsInTheRadarFireAndDeployDecoysLikeTheCockpit() {
        val c = military()
        val sim = c.session!!.sim
        c.tap("radar")
        val before = sim.ship.missiles
        // o alvo inicial está longe: o botão MÍSSIL fica desabilitado
        assertFalse(c.frame().buttons.first { it.id == "fire" }.enabled)
        assertFalse(c.frame().buttons.first { it.id == "decoy" }.enabled)
        assertEquals(before, sim.ship.missiles)
        // com um míssil inimigo a caminho, o despistador libera
        sim.missiles.add(Missile(9, Owner.ENEMY, sim.ship.pos + Vec2(0.0, 30_000.0), sim.ship.vel, "jogador", "corvo"))
        val decoy = c.frame().buttons.first { it.id == "decoy" }
        assertTrue(decoy.enabled)
        c.tap("decoy")
        assertEquals(sim.ship.decoysMax - 1, sim.ship.decoys)
    }
}
