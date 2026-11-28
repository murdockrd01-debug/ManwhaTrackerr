package com.manhwatracker.app.data

import kotlin.random.Random

/** Ejercicios de gramática: completar frases y adivinar qué estructura usa una frase. */
object GrammarDrill {
    enum class Kind { FILL, IDENTIFY }
    enum class Mode { MIXED, FILL, IDENTIFY }

    /** [options] solo se usa en IDENTIFY (títulos de estructuras). [answer] puede traer alternativas separadas con "|". */
    class Question(val kind: Kind, val structureId: String, val prompt: String, val answer: String, val options: List<String> = emptyList())

    private class Drill(val id: String, val prompt: String, val answer: String)

    private const val B = Structures.BLANK

    private val bank = listOf(
        Drill("present_simple", "My brother $B (play) soccer on Sundays.", "plays"),
        Drill("present_simple", "She $B (not / like) spicy food.", "doesn't like|does not like"),
        Drill("present_continuous", "Look! It $B (rain).", "is raining|'s raining"),
        Drill("present_continuous", "We $B (watch) a movie right now.", "are watching|'re watching"),
        Drill("past_simple", "She $B (buy) a new phone last week.", "bought"),
        Drill("past_simple", "They $B (not / come) to the party.", "didn't come|did not come"),
        Drill("will_going_to", "Look at those dark clouds. It $B (rain).", "is going to rain|'s going to rain"),
        Drill("will_going_to", "I think she $B (win) the match.", "will win|'ll win"),
        Drill("present_perfect", "I $B (never / eat) sushi.", "have never eaten|'ve never eaten"),
        Drill("present_perfect", "She $B (live) here since 2020.", "has lived|'s lived"),
        Drill("can_could", "When I was five, I $B already read.", "could"),
        Drill("can_could", "$B you help me, please? (petición cortés)", "could|can"),
        Drill("should_must", "You look very tired. You $B (consejo) go to bed early.", "should"),
        Drill("should_must", "You $B (obligación fuerte) wear a seatbelt in the car.", "must|have to"),
        Drill("first_conditional", "If it $B (rain) tomorrow, we will stay home.", "rains"),
        Drill("first_conditional", "If you study, you $B (pass) the exam.", "will pass|'ll pass"),
        Drill("second_conditional", "If I $B (be) rich, I would travel the world.", "were|was"),
        Drill("second_conditional", "If she $B (know) the answer, she would tell us.", "knew"),
        Drill("passive", "The door $B (open) by the guard. (pasado)", "was opened"),
        Drill("passive", "English $B (speak) in many countries.", "is spoken"),
        Drill("comparatives", "This book is $B (interesting) than that one.", "more interesting"),
        Drill("comparatives", "He is the $B (tall) boy in the class.", "tallest"),
        Drill("phrasal_verbs", "Don't $B up! Keep trying.", "give"),
        Drill("phrasal_verbs", "Please turn $B the light. It's dark.", "on"),
        Drill("used_to", "When I was a kid, I $B climb trees every day.", "used to"),
        Drill("used_to", "She $B live in Madrid, but now she lives in Rome.", "used to"),
        Drill("wish", "I wish I $B (have) more free time.", "had"),
        Drill("wish", "I wish he $B (be) here now.", "were|was"),
        Drill("questions", "Where $B you go yesterday?", "did"),
        Drill("questions", "What $B she want?", "does"),
        Drill("relative", "The woman $B lives next door is a doctor.", "who|that"),
        Drill("relative", "This is the book $B I told you about.", "that|which"),
        Drill("informal", "I'm $B call him tomorrow. (informal de going to)", "gonna"),
        Drill("informal", "I $B go home now. (informal de have got to)", "gotta"),
        Drill("gerund_infinitive", "She enjoys $B (read) at night.", "reading"),
        Drill("gerund_infinitive", "I hope $B (see) you soon.", "to see"),
        Drill("past_continuous", "At 8 pm yesterday, I $B (watch) TV.", "was watching"),
        Drill("past_continuous", "They $B (play) when it started to rain.", "were playing"),
        Drill("past_perfect", "She $B (already / leave) when I arrived.", "had already left"),
        Drill("past_perfect", "I was late because I $B (miss) the bus.", "had missed"),
        Drill("present_perfect_continuous", "He $B (work) here for ten years.", "has been working|'s been working"),
        Drill("present_perfect_continuous", "I $B (wait) since 8 o'clock.", "have been waiting|'ve been waiting"),
        Drill("third_conditional", "If she $B (study), she would have passed.", "had studied"),
        Drill("third_conditional", "If we had left earlier, we $B (not / miss) the train.", "wouldn't have missed|would not have missed")
    )

    /** Todos los huecos disponibles: los de este archivo más el mini ejercicio de cada estructura. */
    private fun fillPool(): List<Triple<String, String, String>> =
        bank.map { Triple(it.id, it.prompt, it.answer) } + Structures.all.map { Triple(it.id, it.quiz.first, it.quiz.second) }

    /** ¿Es correcta la respuesta? Acepta alternativas ("were|was") y no distingue mayúsculas ni apóstrofos curvos. */
    fun check(answer: String, typed: String): Boolean = answer.split('|').any { Structures.isCorrect(it, typed) }

    /** Primera respuesta válida, para mostrarla. */
    fun shown(answer: String): String = answer.split('|').joinToString(" / ")

    fun build(count: Int, mode: Mode, rnd: Random = Random.Default): List<Question> {
        val fills = fillPool().shuffled(rnd).map { Question(Kind.FILL, it.first, it.second, it.third) }
        val idents = Structures.all.flatMap { s -> s.examples.map { s to it.first } }.shuffled(rnd).map { (s, sentence) ->
            val bad = StructureDetector.detect(sentence).map { it.id }.toSet() + s.id
            val distractors = Structures.all.filter { it.id !in bad }.shuffled(rnd).take(3).map { it.title }
            Question(Kind.IDENTIFY, s.id, sentence, s.title, (distractors + s.title).shuffled(rnd))
        }
        val list = when (mode) {
            Mode.FILL -> fills
            Mode.IDENTIFY -> idents
            Mode.MIXED -> fills.zip(idents).flatMap { listOf(it.first, it.second) }
        }
        return list.take(count)
    }
}
