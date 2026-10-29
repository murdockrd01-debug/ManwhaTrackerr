package com.manhwatracker.app.data

/** Importar la lista de lectura de un usuario de AniList (lógica pura, para poder probarla). */
object ListImport {
    class Entry(
        val title: String,
        val status: String,
        val progress: Double,
        val total: Int,
        val rating10: Int,
        val cover: String?,
        val genres: List<String>,
        val anilistId: Long,
        val country: String? = null
    )

    /** Estados de AniList → los de la app. */
    fun mapStatus(s: String): String = when (s.uppercase()) {
        "CURRENT", "REPEATING" -> Status.READING
        "COMPLETED" -> Status.COMPLETED
        "PAUSED", "DROPPED" -> Status.PAUSED
        else -> Status.PLAN
    }

    /** Un manhwa nuevo a partir de una entrada. */
    fun create(e: Entry): Manhwa = Manhwa(
        title = e.title, titleKey = titleKeyOf(e.title), coverUrl = e.cover,
        lastChapter = e.progress, totalChapters = e.total, status = mapStatus(e.status),
        rating = e.rating10.coerceIn(0, 10), genres = e.genres.takeIf { it.isNotEmpty() }?.joinToString(","),
        anilistId = e.anilistId, type = Types.fromCountry(e.country)
    )

    /**
     * Mezcla con uno que ya tienes: nunca baja tu capítulo, ni cambia tu estado o tu nota;
     * solo rellena lo que te faltaba.
     */
    fun merge(old: Manhwa, e: Entry): Manhwa = old.copy(
        lastChapter = maxOf(old.lastChapter, e.progress),
        totalChapters = if (old.totalChapters > 0) old.totalChapters else e.total,
        coverUrl = old.coverUrl ?: e.cover,
        rating = if (old.rating > 0) old.rating else e.rating10.coerceIn(0, 10),
        genres = old.genres ?: e.genres.takeIf { it.isNotEmpty() }?.joinToString(","),
        anilistId = if (old.anilistId > 0) old.anilistId else e.anilistId
    )
}
