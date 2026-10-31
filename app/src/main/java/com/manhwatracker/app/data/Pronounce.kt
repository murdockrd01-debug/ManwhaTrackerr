package com.manhwatracker.app.data

/** Comprobar lo que dijiste con lo que entendió el reconocimiento de voz (lógica pura, con pruebas). */
object Pronounce {

    /** [heard] son las alternativas que devuelve el reconocedor (la mejor primero). */
    fun check(heard: List<String>, answer: String): Typing.Result {
        var best = Typing.Result.WRONG
        for (h in heard) {
            when (single(h, answer)) {
                Typing.Result.EXACT -> return Typing.Result.EXACT
                Typing.Result.CLOSE -> best = Typing.Result.CLOSE
                else -> Unit
            }
        }
        return best
    }

    private fun single(heard: String, answer: String): Typing.Result {
        val r = Typing.check(heard, answer)
        if (r != Typing.Result.WRONG) return r
        // En frases largas, si dijiste casi todas las palabras cuenta como acierto.
        val aw = Typing.normalize(answer).split(' ').filter { it.isNotEmpty() }
        if (aw.size >= 4) {
            val hw = Typing.normalize(heard).split(' ').toSet()
            if (aw.count { it in hw }.toDouble() / aw.size >= 0.8) return Typing.Result.CLOSE
        }
        return Typing.Result.WRONG
    }
}
