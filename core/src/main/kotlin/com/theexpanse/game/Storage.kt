package com.theexpanse.game

/** Onde o app guarda pacotes importados e salvamentos (vários por campanha). O Android grava em arquivos; os testes, na memória. */
interface Storage {
    fun packages(): Map<String, String>
    fun putPackage(id: String, text: String)
    fun removePackage(id: String)

    /** Quando o pacote foi incluído pela primeira vez (ms desde 1970); 0 se desconhecido. Reimportar não muda a data. */
    fun packageAddedAt(id: String): Long = 0L

    /** Preferências pequenas do app (por exemplo, a ordem da lista de campanhas). */
    fun setting(key: String): String? = null
    fun putSetting(key: String, value: String) {}

    /** Salvamentos de uma campanha: identificador do espaço → texto. */
    fun saves(packId: String): Map<String, String>
    fun putSave(packId: String, slot: String, text: String)
    fun removeSave(packId: String, slot: String)
}

/** O que só a plataforma sabe fazer. */
interface Host {
    fun importPackage()
    fun exportFile(fileName: String, text: String)
    fun copyText(text: String)

    /** Pede um texto ao jogador (por exemplo, o nome de um salvamento). [onResult] recebe null se cancelar. */
    fun promptText(title: String, initial: String, onResult: (String?) -> Unit)

    /** Toca um efeito sonoro. */
    fun play(sfx: Sfx) {}

    /** Liga ou desliga o ronco do motor. */
    fun engine(on: Boolean) {}
}

class MemoryStorage : Storage {
    private val pkgs = LinkedHashMap<String, String>()
    private val saves = HashMap<String, LinkedHashMap<String, String>>()
    override fun packages(): Map<String, String> = pkgs
    private val added = HashMap<String, Long>()
    private val prefs = HashMap<String, String>()
    private var clock = 1L
    override fun putPackage(id: String, text: String) { pkgs[id] = text; added.getOrPut(id) { clock++ } }
    override fun removePackage(id: String) { pkgs.remove(id); saves.remove(id); added.remove(id) }
    override fun packageAddedAt(id: String): Long = added[id] ?: 0L
    override fun setting(key: String): String? = prefs[key]
    override fun putSetting(key: String, value: String) { prefs[key] = value }
    override fun saves(packId: String): Map<String, String> = saves[packId] ?: emptyMap()
    override fun putSave(packId: String, slot: String, text: String) { saves.getOrPut(packId) { LinkedHashMap() }[slot] = text }
    override fun removeSave(packId: String, slot: String) { saves[packId]?.remove(slot) }
}

object NoHost : Host {
    override fun importPackage() {}
    override fun exportFile(fileName: String, text: String) {}
    override fun copyText(text: String) {}
    override fun promptText(title: String, initial: String, onResult: (String?) -> Unit) = onResult(null)
}
