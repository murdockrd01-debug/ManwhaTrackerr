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
        PHRASE("Frase", "Frase o línea de diálogo"),
        UNKNOWN("Sin datos", "Todavía no se pudo medir")
    }

    /** [freq] < 0 significa que todavía no se consultó. [text] se usa para detectar frases. */
    fun tier(freq: Double, text: String): Tier = when {
        text.trim().contains(' ') -> Tier.PHRASE
        freq < 0 -> Tier.UNKNOWN
        freq >= 300 -> Tier.BASIC
        freq >= 10 -> Tier.VERY_USEFUL
        freq >= 1 -> Tier.USEFUL
        freq >= 0.1 -> Tier.UNCOMMON
        else -> Tier.RARE
    }

    /** Orden para estudiar: primero las más útiles. Menor número = antes. */
    fun rank(t: Tier): Int = when (t) {
        Tier.VERY_USEFUL -> 0
        Tier.USEFUL -> 1
        Tier.UNCOMMON -> 2
        Tier.PHRASE -> 3
        Tier.BASIC -> 4
        Tier.RARE -> 5
        Tier.UNKNOWN -> 6
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
