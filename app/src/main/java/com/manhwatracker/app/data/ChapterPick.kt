package com.manhwatracker.app.data

/** Elegir el capítulo que sigue en una lista de una fuente en línea. Muchas fuentes no traen el número: se saca del nombre. */
object ChapterPick {
    /** El número declarado por la fuente (si es 0 o más) o, si no, el que sale del nombre ("Capítulo 42" → 42.0). */
    fun number(declared: Float, name: String): Double? =
        if (declared >= 0f) declared.toDouble() else ReaderProgress.chapterNumber(name)

    /** true si la lista viene del más nuevo al más viejo (hay que invertirla para leer en orden). Sin números, se asume que sí. */
    fun newestFirst(nums: List<Double?>): Boolean {
        val f = nums.firstOrNull { it != null } ?: return true
        val l = nums.lastOrNull { it != null } ?: return true
        return f >= l
    }

    /** Posición (en una lista del más viejo al más nuevo) del primer capítulo con número mayor que [after]; -1 si no hay. */
    fun nextAfter(nums: List<Double?>, after: Double): Int = nums.indexOfFirst { it != null && it > after + 0.0001 }

    /** ¿Este capítulo ya está leído según tu avance? Sin número, no se puede saber (false). */
    fun isRead(num: Double?, readUpTo: Double?): Boolean = num != null && readUpTo != null && readUpTo > 0.0 && num <= readUpTo + 0.0001

    /** El número del capítulo que sigue a tu avance (el menor mayor que [readUpTo]); null si no hay. */
    fun nextNumber(nums: List<Double?>, readUpTo: Double): Double? =
        nums.filterNotNull().filter { it > readUpTo + 0.0001 }.minOrNull()

    /**
     * Qué capítulo abrir con "Continuar leyendo" (lista del más viejo al más nuevo). [lastUrl] es el último capítulo
     * que abriste en el lector y [lastDone] si llegaste a su última página. Si lo dejaste a medias, se reabre ese mismo
     * (aunque sea viejo); si lo terminaste, el que sigue. Sin ese dato, el siguiente al avance de tu lista ([libraryChapter]).
     * Devuelve -1 si no queda nada por leer.
     */
    fun resumeIndex(urls: List<String>, nums: List<Double?>, lastUrl: String?, lastDone: Boolean, libraryChapter: Double): Int {
        val li = if (lastUrl == null) -1 else urls.indexOf(lastUrl)
        if (li >= 0) return if (!lastDone) li else if (li + 1 < urls.size) li + 1 else -1
        return nextAfter(nums, libraryChapter)
    }
}
