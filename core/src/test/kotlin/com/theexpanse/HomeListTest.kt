package com.theexpanse

import com.theexpanse.content.Builtin
import com.theexpanse.game.MemoryStorage
import com.theexpanse.render.AppController
import com.theexpanse.render.HomeSort
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HomeListTest {
    private fun pack(id: String, title: String) = Builtin.text("albatroz")!!
        .replace("\"id\": \"albatroz\"", "\"id\": \"$id\"").replace("O silêncio do Albatroz", title)

    private fun controller(storage: MemoryStorage = MemoryStorage()): AppController {
        val c = AppController(storage, seed = { 1L })
        // o título do Albatroz aparece em outros textos: o importado só precisa ter título próprio e único
        assertTrue(c.importText("a.json", pack("zeta_x", "Zeta importada")))
        assertTrue(c.importText("b.json", pack("alfa_x", "Alfa importada")))
        return c
    }

    private fun titles(c: AppController) = c.entries.map { it.pack.title }

    @Test
    fun defaultOrderIsByDateWithBuiltinsFirstAndImportsInImportOrder() {
        val c = controller()
        assertEquals(HomeSort.DATE_ASC, c.homeSort)
        val t = titles(c)
        assertEquals(listOf("Zeta importada", "Alfa importada"), t.takeLast(2))
        assertEquals(Builtin.all().size, t.size - 2)
        assertTrue(c.entries.take(Builtin.all().size).all { it.builtin })
    }

    @Test
    fun sortByNameAscendingAndDescending() {
        val c = controller()
        c.press("home_sort:az")
        val az = titles(c)
        assertEquals(az.sortedWith(java.text.Collator.getInstance(java.util.Locale("pt", "BR"))), az)
        c.press("home_sort:za")
        assertEquals(az.reversed(), titles(c))
    }

    @Test
    fun sortByDateAscendingAndDescending() {
        val c = controller()
        c.press("home_sort:data_desc")
        val desc = titles(c)
        assertEquals(listOf("Alfa importada", "Zeta importada"), desc.take(2), "o mais recente primeiro")
        c.press("home_sort:data_asc")
        assertEquals(desc.reversed(), titles(c))
    }

    @Test
    fun sortChoiceIsRememberedAndShownOnScreen() {
        val storage = MemoryStorage()
        val c = controller(storage)
        c.press("home_sort:za")
        assertEquals(HomeSort.NAME_DESC, AppController(storage, seed = { 1L }).homeSort)
        val ids = c.frame().buttons.map { it.id }
        assertTrue(HomeSort.values().all { "home_sort:${it.key}" in ids })
        assertTrue(c.frame().buttons.first { it.id == "home_sort:za" }.active)
        c.press("home_next")
        c.press("home_sort:az")
        assertEquals(0, c.homePage, "mudar a ordem volta à primeira página")
    }

    @Test
    fun deleteNeedsASecondTapAndRemovesTheSavesToo() {
        val storage = MemoryStorage()
        val c = controller(storage)
        storage.putSave("zeta_x", "auto", "{}")
        c.press("home_sort:data_desc")
        c.frame()
        c.press("home_del:zeta_x")
        assertEquals("zeta_x", c.pendingDeletePack)
        assertTrue(storage.packages().containsKey("zeta_x"), "ainda não excluiu")
        // outro toque cancela
        c.press("home_sort:az")
        assertEquals(null, c.pendingDeletePack)
        c.press("home_del:zeta_x")
        c.press("home_del:zeta_x")
        assertFalse(storage.packages().containsKey("zeta_x"))
        assertTrue(storage.saves("zeta_x").isEmpty(), "os salvamentos vão junto")
        assertFalse(titles(c).contains("Zeta importada"))
        assertTrue(storage.packages().containsKey("alfa_x"), "a outra importada continua")
    }

    @Test
    fun builtinCampaignsCannotBeDeleted() {
        val c = controller()
        c.press("home_sort:data_asc")
        val ids = (0..5).flatMap { p -> c.homePage = p; c.frame().buttons.map { it.id } }
        assertTrue(ids.none { it == "home_del:rota_do_gelo" || it == "home_del:calice" })
        assertTrue(ids.any { it == "home_del:zeta_x" })
    }

    @Test
    fun reimportingKeepsTheOriginalDate() {
        val c = controller()
        c.press("home_sort:data_desc")
        assertEquals("Alfa importada", titles(c).first())
        assertTrue(c.importText("a2.json", pack("zeta_x", "Zeta importada")))
        assertEquals("Alfa importada", titles(c).first(), "reimportar a Zeta não a torna a mais recente")
    }
}
