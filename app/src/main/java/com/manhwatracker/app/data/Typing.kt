package com.manhwatracker.app.data

/** Comparación del modo escritura del repaso: tolerante con mayúsculas, signos y erratas pequeñas. */
object Typing {

    enum class Result {
        /** Igual, sin contar mayúsculas, signos ni espacios de más. */
        EXACT,

        /** Casi igual (una errata en palabras largas). Cuenta como acierto, pero se muestra cómo se escribe. */
        CLOSE,

        WRONG
    }

    /**
     * Deja el texto en una forma comparable:
     * minúsculas, sin apóstrofes (don't = dont), signos y guiones como espacios, espacios sin repetir.
     */
    fun normalize(text: String): String {
        val sb = StringBuilder(text.length)
        for (ch in text.lowercase()) {
            when {
                ch == '\'' || ch == '’' || ch == '‘' || ch == '´' || ch == '`' -> Unit
                ch.isLetterOrDigit() -> sb.append(ch)
                else -> sb.append(' ')
            }
        }
        return sb.toString().trim().replace(Regex("\\s+"), " ")
    }

    /** Cuántas erratas se perdonan según lo larga que sea la respuesta correcta. */
    private fun allowedTypos(answerLength: Int): Int = when {
        answerLength >= 13 -> 2
        answerLength >= 7 -> 1
        else -> 0 // en palabras cortas una letra distinta suele ser otra palabra (form / from)
    }

    fun check(typed: String, answer: String): Result {
        val a = normalize(answer)
        val t = normalize(typed)
        if (a.isEmpty() || t.isEmpty()) return Result.WRONG
        if (t == a) return Result.EXACT
        val limit = allowedTypos(a.length)
        if (limit > 0 && kotlin.math.abs(t.length - a.length) <= limit && distance(t, a) <= limit) {
            return Result.CLOSE
        }
        return Result.WRONG
    }

    /** Pista: la primera letra de cada palabra y rayitas por las demás. "give up" -> "g___ u_". */
    fun hint(answer: String): String =
        answer.trim().split(Regex("\\s+"))
            .filter { it.isNotEmpty() }
            .joinToString(" ") { word ->
                val letters = word.count { it.isLetterOrDigit() }
                if (letters <= 1) word else word.take(1) + "_".repeat(word.length - 1)
            }

    /** Distancia de Levenshtein: cuántos cambios de letra separan un texto del otro. */
    fun distance(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(cur[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
            }
            val tmp = prev
            prev = cur
            cur = tmp
        }
        return prev[b.length]
    }
}
