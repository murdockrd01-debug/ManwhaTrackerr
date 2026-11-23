package com.manhwatracker.app.data

/** Orden de la lista de fuentes: favoritas primero, ocultas fuera (o al final si se piden). */
object SourceOrder {
    fun key(pkg: String, id: Long) = "$pkg:$id"

    fun <T> arrange(
        items: List<T>, key: (T) -> String, name: (T) -> String,
        favs: Set<String>, hidden: Set<String>, showHidden: Boolean
    ): List<T> {
        val byName = items.sortedBy { name(it).lowercase() }
        val visible = byName.filter { key(it) !in hidden }
        val shownHidden = if (showHidden) byName.filter { key(it) in hidden } else emptyList()
        return visible.filter { key(it) in favs } + visible.filter { key(it) !in favs } + shownHidden
    }

    /** Agrega [k] a [set] si no está, o lo quita si ya estaba. */
    fun toggled(set: Set<String>, k: String): Set<String> = if (k in set) set - k else set + k
}
