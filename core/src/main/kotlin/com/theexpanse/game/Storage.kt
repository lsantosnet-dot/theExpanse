package com.theexpanse.game

/** Onde o app guarda pacotes importados e salvamentos. O Android grava em arquivos; os testes, na memória. */
interface Storage {
    fun packages(): Map<String, String>
    fun putPackage(id: String, text: String)
    fun removePackage(id: String)
    fun save(id: String): String?
    fun putSave(id: String, text: String)
    fun removeSave(id: String)
}

/** O que só a plataforma sabe fazer. */
interface Host {
    fun importPackage()
    fun exportFile(fileName: String, text: String)
    fun copyText(text: String)
}

class MemoryStorage : Storage {
    private val pkgs = LinkedHashMap<String, String>()
    private val saves = HashMap<String, String>()
    override fun packages(): Map<String, String> = pkgs
    override fun putPackage(id: String, text: String) { pkgs[id] = text }
    override fun removePackage(id: String) { pkgs.remove(id) }
    override fun save(id: String): String? = saves[id]
    override fun putSave(id: String, text: String) { saves[id] = text }
    override fun removeSave(id: String) { saves.remove(id) }
}

object NoHost : Host {
    override fun importPackage() {}
    override fun exportFile(fileName: String, text: String) {}
    override fun copyText(text: String) {}
}
