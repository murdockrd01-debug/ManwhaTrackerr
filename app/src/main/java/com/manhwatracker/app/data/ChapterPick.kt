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
}
