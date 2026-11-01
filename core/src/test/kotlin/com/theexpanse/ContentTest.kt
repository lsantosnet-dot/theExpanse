package com.theexpanse

import com.theexpanse.content.Builtin
import com.theexpanse.content.Decoder
import com.theexpanse.content.Template
import com.theexpanse.content.Validator
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ContentTest {
    private fun text(name: String) = Builtin.text(name)!!

    @Test
    fun builtinPackagesDecodeAndValidate() {
        for (name in listOf("calice", "albatroz", "patrulha", "emboscada", "rota_do_gelo", "base_lunar")) {
            val r = Decoder.decode(text(name))
            assertTrue(r.errors.isEmpty(), "$name: ${r.errors}")
            val v = Validator.validate(r.pack!!)
            println("$name: avisos = ${v.warnings}")
            assertTrue(v.ok, "$name: ${v.errors}")
        }
    }

    @Test
    fun invalidJsonIsReportedInPortuguese() {
        val r = Decoder.decode("{ isto não é json")
        assertTrue(r.pack == null)
        assertTrue(r.errors.single().startsWith("JSON inválido"))
    }

    @Test
    fun unknownEffectPointsAtThePath() {
        val bad = text("albatroz").replace("\"tipo\": \"dar_objeto\", \"objeto\": \"caixa_preta\"", "\"tipo\": \"dar_objto\", \"objeto\": \"caixa_preta\"")
        val r = Decoder.decode(bad)
        assertTrue(r.pack == null)
        val msg = r.errors.joinToString("\n")
        println(msg)
        assertTrue(msg.contains("regras[1].entao[0]") && msg.contains("'dar_objto' desconhecido") && msg.contains("dar_objeto"), msg)
    }

    @Test
    fun unknownFieldAndWrongTypeAreReported() {
        val bad = text("albatroz").replace("\"creditos\": 800", "\"creditos\": \"muitos\", \"extra\": 1")
        val msg = Decoder.decode(bad).errors.joinToString("\n")
        assertTrue(msg.contains("'creditos' deve ser um número") && msg.contains("campo desconhecido 'extra'"), msg)
    }

    @Test
    fun brokenReferenceIsRejected() {
        val bad = text("albatroz").replace("\"faccao\": \"coalizao\", \"raioOrbita\": 4.3e8", "\"faccao\": \"inexistente\", \"raioOrbita\": 4.3e8")
        val v = Validator.validate(Decoder.decode(bad).pack!!)
        assertTrue(v.errors.any { it.contains("facção 'inexistente' não existe") }, v.errors.toString())
    }

    @Test
    fun unreachableSolutionIsFoundAndExplained() {
        // ninguém entrega a caixa-preta: a perita não tem o que analisar, e a solução some
        val bad = text("albatroz").replace("{\"tipo\": \"dar_objeto\", \"objeto\": \"caixa_preta\"}, ", "")
        val v = Validator.validate(Decoder.decode(bad).pack!!, physics = false)
        println(v.errors.joinToString("\n"))
        assertTrue(v.errors.any { it.contains("O final 'verdade' nunca é alcançável") }, v.errors.toString())
        assertTrue(v.errors.any { it.contains("caixa_preta") }, v.errors.toString())
    }

    @Test
    fun orbitInsideThePlanetIsRejected() {
        val bad = text("calice").replace("\"raioOrbita\": 3.2e8", "\"raioOrbita\": 5.0e7")
        val v = Validator.validate(Decoder.decode(bad).pack!!, physics = false)
        assertTrue(v.errors.any { it.contains("dentro ou colada no corpo central") }, v.errors.toString())
    }

    @Test
    fun shipWithoutFuelIsRejectedByPhysicsCheck() {
        val bad = text("calice").replace("\"combustivel\": 32000", "\"combustivel\": 300")
        val v = Validator.validate(Decoder.decode(bad).pack!!)
        assertTrue(v.errors.any { it.contains("Δv") }, v.errors.toString())
    }

    @Test
    fun templateContainsGuideAndAWorkingExample() {
        val t = Template.build()
        assertTrue(t.contains("Guia para criar uma campanha") && t.contains("`dar_objeto`") && t.contains("`sorteio`"))
        val json = t.substringAfter("```json\n").substringBefore("\n```")
        val r = Decoder.decode(json)
        assertNotNull(r.pack, r.errors.toString())
        assertTrue(Validator.validate(r.pack!!, physics = false).ok)
    }

    private fun packText(id: String) = Builtin.all().map { it.second }.first { Decoder.decode(it).pack?.id == id }

    @Test
    fun aMilitaryPackWithoutAmmoStartsWithTenMissilesAndTenDecoys() {
        val full = packText("patrulha")
        val noAmmo = full.replace("\"misseis\": 10, \"despistadores\": 10, ", "")
        assertTrue(noAmmo != full, "o texto da Patrulha mudou de forma e o teste precisa acompanhar")
        val ship = Decoder.decode(noAmmo).pack!!.ship
        kotlin.test.assertEquals(10, ship.missiles)
        kotlin.test.assertEquals(10, ship.decoys)
    }

    @Test
    fun aMerchantPackKeepsNoMissilesAndItsOwnDecoyDefault() {
        val full = packText("calice")
        val noAmmo = full.replace("\"despistadores\": 4, ", "")
        assertTrue(noAmmo != full)
        val ship = Decoder.decode(noAmmo).pack!!.ship
        kotlin.test.assertEquals(0, ship.missiles)
        kotlin.test.assertEquals(4, ship.decoys)
    }
}

class LandingContentTest {
    private fun pack(landable: String, trigger: String = "lua") =
        Decoder.decode(Builtin.text("base_lunar")!!.replace("\"id\": \"base_lunar\"", "\"id\": \"x_$landable\"").let {
            if (landable == "false") it.replaceFirst("\"id\": \"lua\"", "\"pousavel\": false, \"id\": \"lua\"") else it
        })

    @Test
    fun landingTriggerOnALandableMoonIsAccepted() {
        val v = Validator.validate(pack("true").pack!!)
        assertTrue(v.ok, v.errors.toString())
    }

    @Test
    fun landingTriggerOnANonLandableBodyIsRejected() {
        val v = Validator.validate(pack("false").pack!!)
        assertTrue(v.errors.any { it.contains("nem pousável") }, v.errors.toString())
    }
}
