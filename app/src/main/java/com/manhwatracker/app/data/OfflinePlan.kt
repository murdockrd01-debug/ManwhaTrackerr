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

    /** Cuántos capítulos bajar solos mientras lees (0 = apagado). */
    const val AUTO_NEXT = 3

    /** Los próximos capítulos que faltan por bajar después del [index] que estás leyendo ([count] <= 0: ninguno). */
    fun autoNext(keys: List<String>, have: Set<String>, index: Int, count: Int): List<Int> =
        if (count <= 0) emptyList() else pick(keys, have, index + 1, count.coerceAtMost(10))

    /** Texto corto del avance de una descarga: "3/12". */
    fun progress(done: Int, total: Int): String = "${done.coerceAtLeast(0)}/${total.coerceAtLeast(0)}"

    /** ¿Se puede bajar ahora? Con "solo Wi-Fi" activado, no si la conexión es de datos móviles (medida). */
    fun canDownload(wifiOnly: Boolean, metered: Boolean): Boolean = !(wifiOnly && metered)

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
