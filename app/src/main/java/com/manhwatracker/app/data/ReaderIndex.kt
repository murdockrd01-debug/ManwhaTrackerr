package com.manhwatracker.app.data

/**
 * Índice de lo que hay descargado: por cada carpeta de manhwa, sus capítulos con número.
 * Sirve para saber, sin abrir nada, cuántos capítulos tienes por leer de cada manhwa de tu biblioteca.
 */
object ReaderIndex {

    /** [path] son los identificadores de carpeta desde la raíz elegida hasta la carpeta del manhwa. */
    class Entry(val name: String, val path: List<String>, val chapters: List<Double>, val total: Int)

    /** Capítulos descargados que todavía no has leído (número mayor al leído). */
    fun unread(e: Entry, lastRead: Double): Int = e.chapters.count { it > lastRead }

    /** El primer capítulo por leer, o null si no hay. */
    fun next(e: Entry, lastRead: Double): Double? = e.chapters.filter { it > lastRead }.minOrNull()

    /** La carpeta que corresponde a [title]: gana el nombre exacto sobre uno recortado. */
    fun entryFor(entries: List<Entry>, title: String): Entry? {
        var best: Entry? = null
        var bestScore = 0
        for (e in entries) {
            val sc = ReaderProgress.folderScore(title, e.name)
            if (sc > bestScore) { best = e; bestScore = sc }
        }
        return best
    }

    private const val REC = "\u001E"
    private const val FLD = "\u001F"
    private const val SUB = "\u001D"

    private val control = Regex("[\u001D-\u001F]")

    fun encode(list: List<Entry>): String = list.joinToString(REC) { e ->
        listOf(
            e.name.replace(control, " "),
            e.path.joinToString(SUB) { it.replace(control, " ") },
            e.chapters.joinToString(",") { ReaderProgress.fmt(it) },
            e.total.toString()
        ).joinToString(FLD)
    }

    fun decode(s: String?): List<Entry> {
        if (s.isNullOrEmpty()) return emptyList()
        return s.split(REC).mapNotNull { rec ->
            val f = rec.split(FLD)
            if (f.size != 4) return@mapNotNull null
            val total = f[3].toIntOrNull() ?: return@mapNotNull null
            val chapters = if (f[2].isEmpty()) emptyList() else f[2].split(",").mapNotNull { it.toDoubleOrNull() }
            Entry(f[0], f[1].split(SUB), chapters, total)
        }
    }
}
