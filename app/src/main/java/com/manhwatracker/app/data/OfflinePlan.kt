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

    /** Tamaño legible: "850 KB", "12.4 MB", "1.2 GB". */
    fun size(bytes: Long): String {
        val b = bytes.coerceAtLeast(0)
        return when {
            b < 1024 * 1024 -> "${b / 1024} KB"
            b < 1024L * 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f MB", b / 1048576.0)
            else -> String.format(java.util.Locale.US, "%.1f GB", b / 1073741824.0)
        }
    }
}
