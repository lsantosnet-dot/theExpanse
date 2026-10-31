package com.theexpanse.game

/** Onde o app guarda pacotes importados e salvamentos (vários por campanha). O Android grava em arquivos; os testes, na memória. */
interface Storage {
    fun packages(): Map<String, String>
    fun putPackage(id: String, text: String)
    fun removePackage(id: String)

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
}

class MemoryStorage : Storage {
    private val pkgs = LinkedHashMap<String, String>()
    private val saves = HashMap<String, LinkedHashMap<String, String>>()
    override fun packages(): Map<String, String> = pkgs
    override fun putPackage(id: String, text: String) { pkgs[id] = text }
    override fun removePackage(id: String) { pkgs.remove(id); saves.remove(id) }
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
