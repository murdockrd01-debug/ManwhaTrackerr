package com.manhwatracker.app.data

/** Lee las novedades de cada versión del archivo changelog.md (lógica pura, con pruebas). */
object Changelog {
    /** Las líneas de la sección "## [version]" (sin el título), o null si no existe. */
    fun section(text: String, version: String): String? {
        val lines = text.lines()
        val start = lines.indexOfFirst { it.trim() == "## $version" }
        if (start < 0) return null
        val body = lines.drop(start + 1).takeWhile { !it.startsWith("## ") }
        return body.joinToString("\n").trim().ifEmpty { null }
    }
}
