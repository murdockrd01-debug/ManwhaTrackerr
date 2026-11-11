package com.manhwatracker.app.data

import android.content.Context

/**
 * Une el lector con tu biblioteca: al terminar un capítulo descargado, sube el capítulo leído del manhwa.
 * Komikku guarda sus descargas como <fuente>/<título del manhwa>/<capítulo>, así que el nombre de la carpeta
 * que contiene el capítulo es el título del manhwa.
 */
object ReaderProgress {

    private val keyword = Regex(
        "(?<!\\p{L})(?:cap[ií]tulo|cap|chapter|ch|episode|episodio|ep|#)\\.?\\s*[-_:]?\\s*(\\d+(?:[.,]\\d+)?)",
        RegexOption.IGNORE_CASE
    )
    private val anyNumber = Regex("\\d+(?:[.,]\\d+)?")

    /** Número de capítulo que sale del nombre de la carpeta o archivo ("Ch.45 - Título" -> 45.0). */
    fun chapterNumber(name: String): Double? {
        val base = if (ReaderLogic.isArchive(name)) name.substringBeforeLast('.') else name
        val raw = keyword.find(base)?.groupValues?.get(1) ?: anyNumber.find(base)?.value ?: return null
        return raw.replace(',', '.').toDoubleOrNull()
    }

    /**
     * Sube el capítulo leído (nunca lo baja). Un manhwa pendiente o pausado pasa a "Leyendo".
     * Devuelve null si no cambia nada.
     */
    fun raise(old: Manhwa, chapter: Double, now: Long): Manhwa? {
        if (chapter <= old.lastChapter) return null
        val status = if (old.status == Status.PLAN || old.status == Status.PAUSED) Status.READING else old.status
        return old.copy(lastChapter = chapter, updatedAt = now, status = status)
    }

    /**
     * Busca el manhwa por el nombre de su carpeta. Komikku limpia símbolos y puede recortar nombres largos,
     * así que además del título exacto se acepta un único título que empiece igual (si el nombre es largo).
     */
    fun match(all: List<Manhwa>, folder: String): Manhwa? {
        val key = titleKeyOf(folder)
        all.firstOrNull { it.titleKey == key }?.let { return it }
        if (key.length < 12) return null
        val starts = all.filter { it.titleKey.startsWith(key) }
        return starts.singleOrNull()
    }

    /** ¿La carpeta [folder] es del manhwa [title]? Mismo criterio que [match]: título igual, o nombre de carpeta recortado. */
    fun folderIsManhwa(title: String, folder: String): Boolean {
        val t = titleKeyOf(title)
        val f = titleKeyOf(folder)
        if (t == f) return true
        return f.length >= 12 && t.startsWith(f)
    }

    /**
     * Qué capítulo abrir en "Continuar leyendo". [names] van en orden de lectura y [partial] dice cuáles dejaste a medias.
     * Primero el último a medias que todavía no cuenta como leído; si no hay, el primero con número mayor a [lastRead].
     * Devuelve su posición, o null si no hay nada nuevo descargado.
     */
    fun pickChapter(names: List<String>, partial: List<Boolean>, lastRead: Double): Int? {
        val mid = names.indices.lastOrNull { i ->
            partial.getOrElse(i) { false } && (chapterNumber(names[i])?.let { it > lastRead } ?: true)
        }
        if (mid != null) return mid
        val next = names.indexOfFirst { n -> chapterNumber(n)?.let { it > lastRead } == true }
        return if (next >= 0) next else null
    }

    class Saved(val title: String, val chapter: Double)

    /** Guarda el avance en la base de datos. Devuelve lo guardado, o null si no hay manhwa, número o avance. */
    suspend fun record(ctx: Context, folder: String, chapterName: String, now: Long = System.currentTimeMillis()): Saved? {
        val number = chapterNumber(chapterName) ?: return null
        val db = AppDatabase.get(ctx)
        val dao = db.dao()
        val old = match(dao.all(), folder) ?: return null
        val updated = raise(old, number, now) ?: return null
        dao.update(updated)
        val n = maxOf(0, Math.floor(updated.lastChapter).toInt() - Math.floor(old.lastChapter).toInt())
        db.activity().bump(Stats.dayOf(now), ActivityKind.CHAPTER, n)
        val shown = if (number % 1.0 == 0.0) number.toInt().toString() else number.toString()
        Prefs.setLast(ctx, updated.title, shown)
        return Saved(updated.title, number)
    }
}
