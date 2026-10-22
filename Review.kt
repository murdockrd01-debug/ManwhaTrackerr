package com.manhwatracker.app.data

/**
 * Repaso espaciado sencillo (cajas de Leitner).
 *
 * Una palabra nueva está en la caja 0. Cada vez que la sabes sube una caja y tarda más en volver:
 * caja 1 = 1 día, 2 = 3 días, 3 = 7 días, 4 = 14 días, 5 = 30 días.
 * Si la sabes estando en la caja 5, se marca como aprendida.
 * Si fallas, vuelve a la caja 0 y se repite en 10 minutos.
 */
object Srs {
    const val MAX_BOX = 5

    /** Máximo de tarjetas por ronda, para que no se haga eterno. */
    const val SESSION_MAX = 30

    private const val MINUTE = 60_000L
    private const val DAY = 24 * 60 * MINUTE

    /** Espera según la caja a la que acaba de subir la palabra (el índice 0 no se usa). */
    private val waits = longArrayOf(0, 1 * DAY, 3 * DAY, 7 * DAY, 14 * DAY, 30 * DAY)

    fun next(w: Word, known: Boolean, now: Long): Word = when {
        !known -> w.copy(box = 0, dueAt = now + 10 * MINUTE)
        w.box >= MAX_BOX -> w.copy(learned = true, dueAt = now)
        else -> {
            val box = w.box + 1
            w.copy(box = box, dueAt = now + waits[box])
        }
    }
}
