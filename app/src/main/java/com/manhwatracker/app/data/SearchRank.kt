package com.manhwatracker.app.data

/** Orden de los resultados de la búsqueda general: lo que más se parece a lo que escribiste va primero. */
object SearchRank {
    const val EXACT = 3
    const val STARTS = 2
    const val CONTAINS = 1
    const val NONE = 0

    /** Qué tanto coincide [title] con [query] (sin mayúsculas, acentos de símbolos ni espacios). */
    fun score(query: String, title: String): Int {
        val q = titleKeyOf(query)
        val t = titleKeyOf(title)
        if (q.isEmpty() || t.isEmpty()) return NONE
        return when {
            t == q -> EXACT
            t.startsWith(q) -> STARTS
            t.contains(q) -> CONTAINS
            else -> NONE
        }
    }

    /** Los mejores resultados primero; a igualdad, por orden alfabético del título. */
    fun <T> sortTitles(query: String, items: List<T>, title: (T) -> String): List<T> =
        items.sortedWith(compareByDescending<T> { score(query, title(it)) }.thenBy { title(it).lowercase() })

    /** El mejor puntaje de una lista de títulos (para ordenar las fuentes). */
    fun best(query: String, titles: List<String>): Int = titles.maxOfOrNull { score(query, it) } ?: NONE
}
