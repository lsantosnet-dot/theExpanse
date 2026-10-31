package com.theexpanse.app

import com.theexpanse.game.Storage
import java.io.File

/** Pacotes importados e salvamentos em arquivos privados do app (filesDir). */
class FileStorage(root: File) : Storage {
    private val pkgDir = File(root, "packages").apply { mkdirs() }
    private val saveDir = File(root, "saves").apply { mkdirs() }

    private fun safe(id: String) = id.filter { it.isLetterOrDigit() || it == '_' }

    override fun packages(): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        pkgDir.listFiles { f -> f.extension == "json" }?.sortedBy { it.name }?.forEach { out[it.nameWithoutExtension] = it.readText(Charsets.UTF_8) }
        return out
    }

    override fun putPackage(id: String, text: String) = File(pkgDir, safe(id) + ".json").writeText(text, Charsets.UTF_8)
    override fun removePackage(id: String) { File(pkgDir, safe(id) + ".json").delete() }
    override fun save(id: String): String? = File(saveDir, safe(id) + ".json").takeIf { it.exists() }?.readText(Charsets.UTF_8)
    override fun putSave(id: String, text: String) = File(saveDir, safe(id) + ".json").writeText(text, Charsets.UTF_8)
    override fun removeSave(id: String) { File(saveDir, safe(id) + ".json").delete() }
}
