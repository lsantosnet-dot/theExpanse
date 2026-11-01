package com.theexpanse.content

/** Campanhas que vêm dentro do app (arquivos JSON em resources/packs). */
object Builtin {
    private val files = listOf("calice", "albatroz", "patrulha", "emboscada", "rota_do_gelo", "base_lunar")

    fun text(name: String): String? =
        Builtin::class.java.getResourceAsStream("/packs/$name.json")?.use { it.readBytes().toString(Charsets.UTF_8) }

    /** Pares (nome do arquivo, texto) das campanhas incluídas. */
    fun all(): List<Pair<String, String>> = files.mapNotNull { n -> text(n)?.let { n to it } }

    fun pack(name: String): ContentPackage {
        val r = Decoder.decode(text(name) ?: error("pacote $name não encontrado"))
        return r.pack ?: error("pacote $name inválido: ${r.errors}")
    }
}
