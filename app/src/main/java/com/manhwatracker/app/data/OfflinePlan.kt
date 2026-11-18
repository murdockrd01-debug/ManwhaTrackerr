package com.manhwatracker.app.data

/** Qué capítulos bajar para leer sin conexión (lógica pura). */
object OfflinePlan {
    /**
     * Posiciones (en [keys], del más viejo al más nuevo) de los próximos [count] capítulos que faltan por bajar,
     * empezando en [from]. [count] <= 0 significa "todos los que faltan".
     */
    fun pick(keys: List<String>, have: Set<String>, from: Int, count: Int): List<Int> {
        val start = from.coerceIn(0, keys.size)
        val out = ArrayList<Int>()
        for (i in start until keys.size) {
            if (keys[i] in have) continue
            out.add(i)
            if (count > 0 && out.size >= count) break
        }
        return out
    }

    /** Texto corto del avance de una descarga: "3/12". */
    fun progress(done: Int, total: Int): String = "${done.coerceAtLeast(0)}/${total.coerceAtLeast(0)}"
}
