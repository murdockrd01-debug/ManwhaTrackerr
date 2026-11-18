package com.manhwatracker.app.data

/** Datos que una fuente en línea da de un manhwa (etiquetas, capítulos), traducidos a lo que guarda la app. */
object SourceInfo {
    class Info(val type: String?, val total: Int, val adult: Boolean)

    private val adultTags = setOf("hentai", "adult", "smut", "erotica", "pornographic", "+18", "18+", "adulto", "nsfw")

    fun tags(genre: String?): List<String> = genre.orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }

    /** Manhwa/manga/manhua si alguna etiqueta lo dice; null si no se sabe. */
    fun type(tags: List<String>): String? {
        val t = tags.map { it.lowercase() }
        return when {
            "manhwa" in t -> Types.MANHWA
            "manhua" in t -> Types.MANHUA
            "manga" in t -> Types.MANGA
            else -> null
        }
    }

    fun isAdult(tags: List<String>): Boolean = tags.any { it.lowercase() in adultTags }

    /** Total de capítulos: el número más alto que trae la lista; si ninguno trae número, cuántos son. */
    fun total(nums: List<Double?>, count: Int): Int {
        val max = nums.filterNotNull().maxOrNull()
        return if (max != null && max >= 1.0) Math.floor(max).toInt() else count
    }

    fun info(genre: String?, nums: List<Double?>, count: Int): Info {
        val tags = tags(genre)
        return Info(type(tags), total(nums, count), isAdult(tags))
    }
}
