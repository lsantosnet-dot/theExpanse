package com.theexpanse.game

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put

/** Um salvamento como o jogador o vê: nome editável, quando foi gravado e um resumo. */
class SaveInfo(val slot: String, val name: String, val whenMs: Long, val summary: String, val text: String) {
    val isAuto: Boolean get() = slot == AUTO

    /** O estado do jogo guardado (para GameSession.fromJson). */
    fun game(): JsonObject {
        val o = Json.parseToJsonElement(text).jsonObject
        return o["jogo"]?.jsonObject ?: o
    }

    companion object {
        const val AUTO = "auto"
    }
}

object Saves {
    fun wrap(name: String, whenMs: Long, summary: String, game: JsonObject): String = buildJsonObject {
        put("meta", buildJsonObject { put("nome", name); put("quando", whenMs); put("resumo", summary) })
        put("jogo", game)
    }.toString()

    /** Lê um salvamento, inclusive os antigos (só o estado do jogo, sem nome). */
    fun parse(slot: String, text: String): SaveInfo? = try {
        val o = Json.parseToJsonElement(text).jsonObject
        val meta = o["meta"]?.jsonObject
        if (meta != null) {
            SaveInfo(slot, meta["nome"]!!.jsonPrimitive.content, meta["quando"]!!.jsonPrimitive.long, meta["resumo"]!!.jsonPrimitive.content, text)
        } else {
            val t = o["sim"]?.jsonObject?.get("tempo")?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
            SaveInfo(slot, "Automático", 0L, "T+ ${(t / 3600).toInt()} h", text)
        }
    } catch (e: Exception) {
        null
    }

    /** Troca o nome mantendo o resto. */
    fun rename(info: SaveInfo, newName: String): String = wrap(newName, info.whenMs, info.summary, info.game())
}
