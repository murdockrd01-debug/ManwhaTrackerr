package com.manhwatracker.app.data

/** Lógica pura de "guardar texto de una imagen": elegir líneas, unirlas y sacar las palabras. */
object ScanLogic {
    /** Una línea de texto leída de la imagen, con su rectángulo en píxeles de la imagen. */
    class Line(val text: String, val left: Float, val top: Float, val right: Float, val bottom: Float)

    /** Índices de las líneas que tocan el rectángulo marcado. */
    fun pick(lines: List<Line>, l: Float, t: Float, r: Float, b: Float): List<Int> {
        val x1 = minOf(l, r)
        val x2 = maxOf(l, r)
        val y1 = minOf(t, b)
        val y2 = maxOf(t, b)
        return lines.indices.filter { i ->
            val ln = lines[i]
            ln.left <= x2 && ln.right >= x1 && ln.top <= y2 && ln.bottom >= y1
        }
    }

    /** La línea bajo un toque (con un margen, porque el dedo es grueso). */
    fun lineAt(lines: List<Line>, x: Float, y: Float, pad: Float = 12f): Int? {
        val hits = lines.indices.filter { i ->
            val ln = lines[i]
            x >= ln.left - pad && x <= ln.right + pad && y >= ln.top - pad && y <= ln.bottom + pad
        }
        // Si hay varias, la más cercana al centro del toque.
        return hits.minByOrNull { i ->
            val ln = lines[i]
            val cx = (ln.left + ln.right) / 2
            val cy = (ln.top + ln.bottom) / 2
            (cx - x) * (cx - x) + (cy - y) * (cy - y)
        }
    }

    /** Une las líneas elegidas, de arriba abajo y de izquierda a derecha, en un solo texto. */
    fun join(lines: List<Line>, indices: Collection<Int>): String =
        indices.mapNotNull { lines.getOrNull(it) }
            .sortedWith(compareBy<Line>({ it.top }, { it.left }))
            .joinToString(" ") { it.text.trim() }
            .replace(Regex("\\s+"), " ")
            .trim()

    private val wordRegex = Regex("[A-Za-z]+(?:['’][A-Za-z]+)*")

    /** Palabras en inglés del texto, sin repetir (sin importar mayúsculas), en orden de aparición. */
    fun words(text: String): List<String> {
        val seen = HashSet<String>()
        val out = ArrayList<String>()
        for (m in wordRegex.findAll(text)) {
            val w = m.value
            val key = w.lowercase()
            if (w.length < 2 && key != "i" && key != "a") continue
            if (seen.add(key)) out += w
        }
        return out
    }
}
