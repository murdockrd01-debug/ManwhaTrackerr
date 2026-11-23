package com.manhwatracker.app.data

/** Reglas para pasar de un capítulo al siguiente sin cortes (lógica pura). */
object ChapterFlow {
    /** Espera con la tarjeta de fin visible antes de pasar sola al siguiente capítulo. */
    const val ADVANCE_MS = 2500L

    /** Cuántas páginas del siguiente capítulo se bajan por adelantado. */
    const val PREFETCH_PAGES = 3

    /** Fracción del capítulo a partir de la cual se empieza a preparar el siguiente. */
    private const val NEAR_END = 0.6

    /** ¿Ya vas lo bastante avanzado como para preparar el siguiente capítulo? [lastIdx] = última página a la vista. */
    fun nearEnd(lastIdx: Int, total: Int): Boolean = total > 0 && lastIdx >= (total * NEAR_END).toInt()

    /** ¿Debe pasar solo? Hace falta: opción activa, estar viendo la tarjeta de fin, y que exista un capítulo siguiente. */
    fun shouldAutoAdvance(enabled: Boolean, onEndCard: Boolean, index: Int, lastIndex: Int): Boolean =
        enabled && onEndCard && index < lastIndex

    /** Páginas a bajar del siguiente capítulo: las primeras [PREFETCH_PAGES] (o menos si tiene pocas). */
    fun prefetchCount(pageCount: Int): Int = pageCount.coerceIn(0, PREFETCH_PAGES)
}
