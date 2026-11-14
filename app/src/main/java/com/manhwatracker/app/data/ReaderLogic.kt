package com.manhwatracker.app.data

/** Lógica pura del lector integrado (se prueba sin el teléfono). */
object ReaderLogic {

    private val imageExt = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "avif")
    private val archiveExt = setOf("cbz", "zip")

    const val MAX_ZOOM = 3f
    /** Proporción ancho/alto que se usa mientras no se conoce la real de una página. */
    const val FALLBACK_RATIO = 0.7f

    /** true si el nombre parece una imagen de página (ignora archivos ocultos y basura de macOS). */
    fun isImage(name: String): Boolean {
        if (name.contains("__MACOSX")) return false
        val base = name.substringAfterLast('/')
        if (base.startsWith(".")) return false
        return base.substringAfterLast('.', "").lowercase() in imageExt
    }

    fun isArchive(name: String): Boolean = name.substringAfterLast('.', "").lowercase() in archiveExt

    /** Orden "natural": 2.jpg va antes que 10.jpg, "Cap 2" antes que "Cap 10". */
    val natural: Comparator<String> = Comparator { a, b -> compareNatural(a, b) }

    fun compareNatural(a: String, b: String): Int {
        val x = chunks(a.lowercase())
        val y = chunks(b.lowercase())
        val n = minOf(x.size, y.size)
        for (i in 0 until n) {
            val p = x[i]
            val q = y[i]
            val c = if (p[0].isDigit() && q[0].isDigit()) {
                val pt = p.trimStart('0')
                val qt = q.trimStart('0')
                if (pt.length != qt.length) pt.length.compareTo(qt.length) else pt.compareTo(qt)
            } else p.compareTo(q)
            if (c != 0) return c
        }
        if (x.size != y.size) return x.size.compareTo(y.size)
        // Empate total ("02.jpg" y "2.jpg"): se desempata por el texto para que el orden sea siempre el mismo.
        return a.compareTo(b)
    }

    /** "cap12b" -> ["cap", "12", "b"]: trozos seguidos de letras o de números. */
    private fun chunks(s: String): List<String> {
        val out = ArrayList<String>()
        var cur = StringBuilder()
        var digit = false
        for (ch in s) {
            val d = ch.isDigit()
            if (cur.isNotEmpty() && d != digit) {
                out.add(cur.toString())
                cur = StringBuilder()
            }
            cur.append(ch)
            digit = d
        }
        if (cur.isNotEmpty()) out.add(cur.toString())
        return out
    }

    /** Solo las imágenes, en orden de lectura. */
    fun sortPages(names: List<String>): List<String> = names.filter { isImage(it) }.sortedWith(natural)

    fun clampZoom(z: Float): Float = z.coerceIn(1f, MAX_ZOOM)

    /** Cuánto se puede mover la imagen hacia cada lado (en píxeles) con ese zoom. */
    fun maxPan(width: Float, zoom: Float): Float = ((zoom - 1f) * width / 2f).coerceAtLeast(0f)

    fun clampPan(pan: Float, width: Float, zoom: Float): Float {
        val m = maxPan(width, zoom)
        return pan.coerceIn(-m, m)
    }

    fun clampPage(index: Int, total: Int): Int = if (total <= 0) 0 else index.coerceIn(0, total - 1)

    /** Avance de lectura de 0 a 100. [index] empieza en 0. */
    fun percent(index: Int, total: Int): Int =
        if (total <= 0) 0 else ((index + 1) * 100 / total).coerceIn(0, 100)

    /** Proporción ancho/alto de una imagen, o null si las medidas no sirven. */
    fun safeRatio(w: Int, h: Int): Float? =
        if (w <= 0 || h <= 0) null else (w.toFloat() / h.toFloat()).coerceIn(0.02f, 8f)

    /** "primary:Download/Manhwa/Cap 1" -> "Cap 1". */
    fun nameFromDocId(docId: String): String =
        docId.trimEnd('/').substringAfterLast('/').substringAfterLast(':').ifEmpty { "Carpeta" }

    /**
     * Dónde se quedó el lector: [tree] es la carpeta elegida ("" si fue un .cbz suelto),
     * [dir] la carpeta que contiene al capítulo, [item] el capítulo (o la dirección del .cbz).
     */
    data class Place(val tree: String, val dir: String, val item: String, val name: String)

    fun encodePlace(p: Place): String = listOf(p.tree, p.dir, p.item, p.name).joinToString("\u0001")

    fun decodePlace(s: String?): Place? {
        if (s.isNullOrEmpty()) return null
        val parts = s.split("\u0001")
        if (parts.size != 4) return null
        return Place(parts[0], parts[1], parts[2], parts[3])
    }

    // ---------- Lectura continua: varios capítulos en una sola lista ----------
    // Cada capítulo ocupa sus páginas + 1 celda final ("cierre"). [counts] = páginas de cada capítulo cargado.

    /** Posición en la lista de la página [page] del capítulo [seg]; con page == counts[seg] es su celda de cierre. */
    fun flatIndex(counts: List<Int>, seg: Int, page: Int): Int = counts.take(seg).sumOf { it + 1 } + page

    /** Inversa de [flatIndex]: (capítulo, página). Si la página es igual al número de páginas, es el cierre. */
    fun locate(counts: List<Int>, flat: Int): Pair<Int, Int> {
        var rest = flat.coerceAtLeast(0)
        for ((s, c) in counts.withIndex()) {
            if (rest <= c) return s to rest
            rest -= c + 1
        }
        val last = counts.lastIndex.coerceAtLeast(0)
        return last to counts.getOrElse(last) { 0 }
    }

    /** ¿Conviene cargar ya el capítulo siguiente? Cuando lo último que se ve está a [ahead] celdas o menos del final. */
    fun shouldLoadNext(counts: List<Int>, lastVisible: Int, ahead: Int = 4): Boolean =
        counts.isNotEmpty() && lastVisible >= counts.sumOf { it + 1 } - ahead

    /** Capítulos cuyo cierre ya se alcanzó (se vio o se pasó) con la última celda visible [lastVisible]. */
    fun finished(counts: List<Int>, lastVisible: Int): List<Int> =
        counts.indices.filter { lastVisible >= flatIndex(counts, it, counts[it]) }

    /**
     * Qué capítulos extraídos borrar de la caché: se conserva el que se está abriendo ([keepId]) y los
     * [keep] - 1 más recientes; el resto se borra. [items] = (nombre de carpeta, última vez que se usó).
     */
    fun toEvict(items: List<Pair<String, Long>>, keepId: String, keep: Int = 6): List<String> =
        items.filter { it.first != keepId }
            .sortedByDescending { it.second }
            .drop((keep - 1).coerceAtLeast(0))
            .map { it.first }
}
