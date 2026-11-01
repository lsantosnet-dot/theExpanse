package com.theexpanse.app

import com.theexpanse.game.Storage
import java.io.File

/**
 * Pacotes importados e salvamentos em arquivos privados do app (filesDir).
 * Salvamentos: saves/<campanha>/<espaço>.json. Um arquivo antigo saves/<campanha>.json vira o espaço "auto".
 */
class FileStorage(private val root: File) : Storage {
    private val pkgDir = File(root, "packages").apply { mkdirs() }
    private val saveDir = File(root, "saves").apply { mkdirs() }

    private fun safe(id: String) = id.filter { it.isLetterOrDigit() || it == '_' || it == '-' }

    override fun packages(): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        pkgDir.listFiles { f -> f.extension == "json" }?.sortedBy { it.name }?.forEach { out[it.nameWithoutExtension] = it.readText(Charsets.UTF_8) }
        return out
    }

    private val prefDir = File(root, "settings").apply { mkdirs() }

    override fun putPackage(id: String, text: String) {
        val file = File(pkgDir, safe(id) + ".json")
        val meta = File(pkgDir, safe(id) + ".added")
        // a data de inclusão é a da primeira vez; reimportar o mesmo id não a muda
        if (!meta.exists()) meta.writeText((if (file.exists()) file.lastModified() else System.currentTimeMillis()).toString())
        file.writeText(text, Charsets.UTF_8)
    }

    override fun packageAddedAt(id: String): Long =
        File(pkgDir, safe(id) + ".added").takeIf { it.exists() }?.readText()?.trim()?.toLongOrNull()
            ?: File(pkgDir, safe(id) + ".json").takeIf { it.exists() }?.lastModified() ?: 0L

    override fun setting(key: String): String? = File(prefDir, safe(key) + ".txt").takeIf { it.exists() }?.readText(Charsets.UTF_8)

    override fun putSetting(key: String, value: String) = File(prefDir, safe(key) + ".txt").writeText(value, Charsets.UTF_8)

    override fun removePackage(id: String) {
        File(pkgDir, safe(id) + ".json").delete()
        File(pkgDir, safe(id) + ".added").delete()
        File(saveDir, safe(id)).deleteRecursively()
        File(saveDir, safe(id) + ".json").delete()
    }

    override fun saves(packId: String): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        File(saveDir, safe(packId) + ".json").takeIf { it.exists() }?.let { out["auto"] = it.readText(Charsets.UTF_8) } // formato antigo
        File(saveDir, safe(packId)).listFiles { f -> f.extension == "json" }?.sortedBy { it.name }?.forEach { out[it.nameWithoutExtension] = it.readText(Charsets.UTF_8) }
        return out
    }

    override fun putSave(packId: String, slot: String, text: String) {
        val dir = File(saveDir, safe(packId)).apply { mkdirs() }
        File(dir, safe(slot) + ".json").writeText(text, Charsets.UTF_8)
        if (slot == "auto") File(saveDir, safe(packId) + ".json").delete() // o antigo já foi migrado
    }

    override fun removeSave(packId: String, slot: String) {
        File(File(saveDir, safe(packId)), safe(slot) + ".json").delete()
        if (slot == "auto") File(saveDir, safe(packId) + ".json").delete()
    }
}
