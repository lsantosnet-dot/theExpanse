package com.theexpanse.content

/** Arquivo exportado pelo jogo: guia para a IA, vocabulário e um exemplo completo. */
object Template {
    const val FILE_NAME = "the_expanse_template.md"

    fun build(): String = buildString {
        append(Vocabulary.guide())
        append("\n\n## Exemplo completo (um mistério válido)\n\n```json\n")
        append(Builtin.text("albatroz") ?: "{}")
        append("\n```\n\n## Exemplo completo de missão de pouso (Base na Lua: pouso, entrega, decolagem e volta)\n\n```json\n")
        append(Builtin.text("base_lunar") ?: "{}")
        append("\n```\n\n## Como usar\n1. Entregue este arquivo a uma IA e peça: \"crie uma campanha nova seguindo este guia: [sua ideia]\".\n")
        append("2. Salve só o JSON que ela devolver, com extensão .json.\n3. No jogo, em Campanhas, toque em Importar. Se houver erros, copie-os e devolva à IA para corrigir.\n")
    }
}
