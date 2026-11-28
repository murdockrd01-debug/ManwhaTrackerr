package com.manhwatracker.app.data

/**
 * Qué tan útil es aprender una palabra, según qué tan común es en inglés.
 * La frecuencia viene en "veces por millón de palabras" (Datamuse). Es una guía, no una verdad absoluta:
 * una palabra rara puede ser justo la que necesitas para entender tu manhwa.
 */
object Utility {

    enum class Tier(val label: String, val hint: String) {
        VERY_USEFUL("Muy útil", "Se usa mucho: vale la pena aprenderla"),
        USEFUL("Útil", "Aparece con regularidad"),
        UNCOMMON("Poco común", "Se ve de vez en cuando"),
        BASIC("Básica", "Muy común: probablemente ya la conoces"),
        RARE("Rara o jerga", "Casi no se usa fuera de este contexto"),
        UNKNOWN("Sin datos", "Todavía no se pudo medir")
    }

    /** La frecuencia de una frase es una estimación basada en sus palabras importantes. */
    fun tier(freq: Double, text: String): Tier = when {
        freq < 0 -> Tier.UNKNOWN
        freq >= 300 -> Tier.BASIC
        freq >= 10 -> Tier.VERY_USEFUL
        freq >= 1 -> Tier.USEFUL
        freq >= 0.1 -> Tier.UNCOMMON
        else -> Tier.RARE
    }

    fun isPhrase(text: String): Boolean = text.trim().any { it.isWhitespace() }

    fun label(tier: Tier, text: String): String =
        if (isPhrase(text)) "${tier.label} · Frase" else tier.label

    /** Prioridad de estudio en estrellas: más estrellas = conviene aprenderla antes. */
    fun stars(t: Tier): String = when (t) {
        Tier.VERY_USEFUL -> "★★★"
        Tier.USEFUL -> "★★☆"
        Tier.UNCOMMON -> "★☆☆"
        else -> ""
    }

    /** Consejo corto y claro de qué hacer con la palabra o frase. */
    fun advice(t: Tier): String = when (t) {
        Tier.VERY_USEFUL -> "Apréndela primero"
        Tier.USEFUL -> "Vale la pena aprenderla"
        Tier.UNCOMMON -> "Aprende con calma"
        Tier.BASIC -> "Seguro ya la sabes"
        Tier.RARE -> "Solo si la necesitas para tu manhwa"
        Tier.UNKNOWN -> "Aún sin medir"
    }

    /** Línea de resumen visible en la tarjeta: "★★★ Muy útil · Frase — Apréndela primero". */
    fun summary(t: Tier, text: String): String {
        val s = stars(t)
        return (if (s.isEmpty()) "" else "$s ") + label(t, text) + " — " + advice(t)
    }

    fun hint(tier: Tier, text: String): String = when {
        tier == Tier.UNKNOWN && isPhrase(text) -> "Todavía no se pudo estimar la utilidad de esta frase."
        isPhrase(text) -> "Utilidad estimada según sus palabras principales; no es la frecuencia exacta de la frase."
        else -> tier.hint
    }

    /** Orden para estudiar: primero las más útiles. Menor número = antes. */
    fun rank(t: Tier): Int = when (t) {
        Tier.VERY_USEFUL -> 0
        Tier.USEFUL -> 1
        Tier.UNCOMMON -> 2
        Tier.BASIC -> 3
        Tier.RARE -> 4
        Tier.UNKNOWN -> 5
    }

    /** Compara dos palabras: mejor tier primero y, dentro del mismo tier, la más frecuente primero. */
    val byUsefulness: Comparator<Word> =
        compareBy<Word> { rank(tier(it.freq, it.text)) }
            .thenByDescending { it.freq }
            .thenBy { it.text.lowercase() }

    /** Saca la frecuencia de la etiqueta "f:12.5" que devuelve Datamuse. */
    fun parseFreqTag(tags: List<String>): Double? =
        tags.firstOrNull { it.startsWith("f:") }?.removePrefix("f:")?.toDoubleOrNull()

    /** Separa un ejemplo guardado como "inglés⇒español" (el español es opcional). */
    fun splitExample(line: String): Pair<String, String?> {
        val i = line.indexOf('⇒')
        return if (i < 0) line.trim() to null
        else line.substring(0, i).trim() to line.substring(i + 1).trim().ifEmpty { null }
    }
}
