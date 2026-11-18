package com.manhwatracker.app.data

/** Zonas de toque del lector paginado: izquierda, centro (muestra/oculta los controles) y derecha. */
object ReaderTap {
    const val PREV = -1
    const val MENU = 0
    const val NEXT = 1

    /** [rtl] = lectura de derecha a izquierda (manga): el lado derecho va hacia atrás... y el izquierdo hacia adelante. */
    fun zone(x: Float, width: Float, rtl: Boolean): Int {
        if (width <= 0f) return MENU
        val f = x / width
        val raw = when {
            f < 0.3f -> PREV
            f > 0.7f -> NEXT
            else -> MENU
        }
        return if (rtl) -raw else raw
    }

    const val VERTICAL = "V"
    const val PAGED_LTR = "LTR"
    const val PAGED_RTL = "RTL"
    fun normalizeMode(v: String?): String = if (v == PAGED_LTR || v == PAGED_RTL) v else VERTICAL
}
