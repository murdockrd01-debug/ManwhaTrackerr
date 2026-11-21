package com.manhwatracker.app.data

/**
 * Zoom con dos dedos del lector por páginas. La página se escala desde su centro ([s]) y se mueve ([tx], [ty]);
 * aquí vive solo la cuenta, para poder probarla sin pantalla.
 */
object PageZoom {
    const val MAX = 4f
    const val DOUBLE_TAP = 2.5f

    data class T(val s: Float = 1f, val tx: Float = 0f, val ty: Float = 0f) {
        val zoomed get() = s > 1.01f
    }

    /** Deja la página dentro de la pantalla: no se puede arrastrar más allá de sus bordes. */
    fun clamp(t: T, w: Float, h: Float): T {
        if (t.s <= 1f) return T()
        val mx = (t.s - 1f) * w / 2f
        val my = (t.s - 1f) * h / 2f
        return T(t.s, t.tx.coerceIn(-mx, mx), t.ty.coerceIn(-my, my))
    }

    /** Pellizco: cambia la escala por [factor] manteniendo quieto el punto ([cx], [cy]) y suma el arrastre ([panX], [panY]). */
    fun pinch(t: T, factor: Float, cx: Float, cy: Float, panX: Float, panY: Float, w: Float, h: Float): T {
        val ns = (t.s * factor).coerceIn(1f, MAX)
        val k = ns / t.s
        val dx = cx - w / 2f
        val dy = cy - h / 2f
        return clamp(T(ns, dx - k * (dx - t.tx) + panX, dy - k * (dy - t.ty) + panY), w, h)
    }

    /** Arrastrar con un dedo solo mueve la página si está ampliada. */
    fun drag(t: T, dx: Float, dy: Float, w: Float, h: Float): T =
        if (t.s <= 1f) t else clamp(T(t.s, t.tx + dx, t.ty + dy), w, h)

    /** Doble toque: amplía en el punto tocado, o vuelve al tamaño normal si ya estaba ampliada. */
    fun doubleTap(t: T, cx: Float, cy: Float, w: Float, h: Float): T =
        if (t.zoomed) T() else pinch(t, DOUBLE_TAP, cx, cy, 0f, 0f, w, h)
}
