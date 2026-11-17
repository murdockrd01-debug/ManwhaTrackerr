package com.manhwatracker.app.data

/** Tamaños de carátula y detección de manhwas +18 (lógica pura, sin pantalla). */
object CoverStyle {
    const val SMALL = "S"
    const val MEDIUM = "M"
    const val LARGE = "L"
    const val XLARGE = "XL"
    val sizes = listOf(SMALL to "Pequeña", MEDIUM to "Mediana", LARGE to "Grande", XLARGE to "Muy grande")

    fun normalize(v: String?): String = if (sizes.any { it.first == v }) v!! else MEDIUM

    /** Ancho mínimo de cada celda de la cuadrícula, en dp (menos ancho = más columnas). */
    fun gridMinDp(size: String): Int = when (normalize(size)) {
        SMALL -> 80
        LARGE -> 140
        XLARGE -> 190
        else -> 104
    }

    /** Ancho de la carátula en la vista de lista, en dp (el alto es 1.44 veces el ancho). */
    fun listWidthDp(size: String): Int = when (normalize(size)) {
        SMALL -> 48
        LARGE -> 84
        XLARGE -> 108
        else -> 64
    }

    fun listHeightDp(size: String): Int = (listWidthDp(size) * 1.44f).toInt()

    /**
     * ¿Es +18? Una marca manual del usuario manda sobre todo; si no hay, se mira el género
     * ("Hentai" siempre, "Ecchi" solo si [includeEcchi]).
     */
    fun isAdult(genres: String?, manual: Boolean?, includeEcchi: Boolean): Boolean {
        if (manual != null) return manual
        val g = genres.orEmpty().split(',').map { it.trim().lowercase() }
        return "hentai" in g || (includeEcchi && "ecchi" in g)
    }
}
