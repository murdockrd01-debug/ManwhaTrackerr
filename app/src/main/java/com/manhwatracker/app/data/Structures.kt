package com.manhwatracker.app.data

/** Una estructura gramatical del inglés, explicada en español, con ejemplos y una frase para practicar. */
class Structure(
    val id: String,
    val title: String,
    val pattern: String,
    val explanation: String,
    /** Pares (inglés, español). */
    val examples: List<Pair<String, String>>,
    /** Error típico de quien habla español. */
    val tip: String,
    /** Frase con "___" donde va la respuesta, y la respuesta. */
    val quiz: Pair<String, String>
)

object Structures {
    const val BLANK = "___"

    val all: List<Structure> = listOf(
        Structure(
            "present_simple", "Presente simple", "Sujeto + verbo (+s con he/she/it)",
            "Para hábitos, hechos y cosas que pasan siempre. En negativo y en preguntas se usa do/does + verbo base.",
            listOf("I read manhwa every night." to "Leo manhwa todas las noches.", "She doesn't know the truth." to "Ella no sabe la verdad."),
            "Con he/she/it el verbo lleva -s (reads), pero después de does no: «Does she read?».",
            "He $BLANK (go) to school by bus." to "goes"
        ),
        Structure(
            "present_continuous", "Presente continuo", "am / is / are + verbo-ing",
            "Algo que está pasando ahora mismo o durante este periodo.",
            listOf("He is fighting the boss right now." to "Está peleando con el jefe ahora mismo.", "I'm reading chapter 40." to "Estoy leyendo el capítulo 40."),
            "Los verbos de estado (know, like, want) casi no van en continuo: «I know», no «I am knowing».",
            "They $BLANK (run) away from the monster." to "are running"
        ),
        Structure(
            "past_simple", "Pasado simple", "verbo + -ed / forma irregular · did not + verbo base",
            "Para acciones terminadas en un momento del pasado.",
            listOf("I went home early." to "Me fui a casa temprano.", "She didn't tell anyone." to "No se lo dijo a nadie."),
            "Después de did / didn't el verbo vuelve a la forma base: «Did you go?», no «Did you went?».",
            "Yesterday I $BLANK (see) him." to "saw"
        ),
        Structure(
            "will_going_to", "Will y going to", "will + verbo base · am/is/are going to + verbo base",
            "Will: decisiones en el momento, promesas y predicciones sin pruebas. Going to: planes ya decididos o cuando hay señales claras.",
            listOf("I will protect you." to "Te protegeré.", "Look at those clouds. It's going to rain." to "Mira esas nubes. Va a llover."),
            "En español usamos «voy a» para casi todo; en inglés elige según si ya lo habías decidido.",
            "I've already decided: I $BLANK study English every day." to "am going to"
        ),
        Structure(
            "present_perfect", "Presente perfecto", "have / has + participio",
            "Une pasado y presente: experiencias, cosas recién pasadas o que siguen siendo ciertas. No se usa con un momento concreto del pasado.",
            listOf("I have read ten chapters today." to "He leído diez capítulos hoy.", "Have you ever seen a dungeon?" to "¿Alguna vez has visto una mazmorra?"),
            "Con «yesterday», «last week» o «in 2020» se usa pasado simple, no presente perfecto.",
            "She $BLANK (never/lose) a fight." to "has never lost"
        ),
        Structure(
            "can_could", "Can, could y be able to", "can / could / be able to + verbo base",
            "Can: habilidad o permiso ahora. Could: habilidad en el pasado o petición educada. Be able to: cuando can no se puede usar (futuro, perfecto).",
            listOf("I can't beat him alone." to "No puedo vencerlo yo solo.", "Could you help me?" to "¿Podrías ayudarme?"),
            "Can nunca lleva -s ni «to»: «she can swim».",
            "He $BLANK lift that sword. (no puede)" to "can't"
        ),
        Structure(
            "should_must", "Should, must y have to", "should / must / have to + verbo base",
            "Should: consejo. Must: obligación fuerte o deducción segura. Have to: obligación por reglas externas.",
            listOf("You should rest." to "Deberías descansar.", "You must not enter that room." to "No debes entrar en esa habitación."),
            "«Don't have to» NO es prohibido: significa que no hace falta. Prohibido es «mustn't».",
            "You $BLANK wear a uniform; it's the school rule." to "have to"
        ),
        Structure(
            "first_conditional", "Condicional 1 (real)", "If + presente simple, will + verbo base",
            "Situaciones posibles en el futuro: si pasa esto, pasará aquello.",
            listOf("If you leave, I will follow you." to "Si te vas, te seguiré.", "If it rains, we won't go." to "Si llueve, no iremos."),
            "Después de «if» no se usa will: «If you go», no «If you will go».",
            "If he $BLANK (come), I'll tell him." to "comes"
        ),
        Structure(
            "second_conditional", "Condicional 2 (irreal)", "If + pasado simple, would + verbo base",
            "Situaciones imaginarias o poco probables en el presente.",
            listOf("If I were stronger, I would win." to "Si fuera más fuerte, ganaría.", "If I had a sword, I would fight." to "Si tuviera una espada, pelearía."),
            "Con «if» se suele decir «were» para todas las personas: «If I were you».",
            "If I $BLANK (be) you, I would run." to "were"
        ),
        Structure(
            "passive", "Voz pasiva", "be + participio (+ by ...)",
            "Se usa cuando importa más lo que pasa que quién lo hace.",
            listOf("The boss was defeated by the hero." to "El jefe fue derrotado por el héroe.", "The gate is guarded day and night." to "La puerta está vigilada día y noche."),
            "En español usamos mucho «se» («se dice»); en inglés suele ser pasiva: «it is said».",
            "The city $BLANK (destroy) last year." to "was destroyed"
        ),
        Structure(
            "comparatives", "Comparativos y superlativos", "adj + -er than / more + adj than · the + adj-est / the most + adj",
            "Para comparar dos cosas y para decir cuál es la que más destaca.",
            listOf("He is stronger than me." to "Es más fuerte que yo.", "She is the smartest hunter." to "Ella es la cazadora más inteligente."),
            "Adjetivos cortos: -er/-est; largos: more/most. Irregulares: good-better-best, bad-worse-worst.",
            "This chapter is $BLANK (good) than the last one." to "better"
        ),
        Structure(
            "phrasal_verbs", "Phrasal verbs", "verbo + partícula (give up, run away, find out...)",
            "El significado cambia con la partícula, así que hay que aprenderlos enteros.",
            listOf("Don't give up!" to "¡No te rindas!", "He ran away from the guild." to "Huyó del gremio.", "I'll find out the truth." to "Descubriré la verdad."),
            "Aprende el verbo completo como una sola palabra: «give up», no «give» + «up».",
            "Never $BLANK up on your dream. (rendirse)" to "give"
        ),
        Structure(
            "used_to", "Used to", "used to + verbo base",
            "Hábitos o situaciones del pasado que ya no son así.",
            listOf("I used to be weak." to "Antes era débil.", "We used to train every day." to "Antes entrenábamos todos los días."),
            "En negativas y preguntas se pierde la -d: «I didn't use to», «Did you use to...?».",
            "She $BLANK live in Seoul. (antes vivía)" to "used to"
        ),
        Structure(
            "wish", "Wish (ojalá)", "wish + pasado simple (presente) · wish + had + participio (pasado)",
            "Para expresar deseos sobre cosas que no son así o que no pasaron.",
            listOf("I wish I had more time." to "Ojalá tuviera más tiempo.", "I wish I had told her." to "Ojalá se lo hubiera dicho."),
            "Aunque hable del presente, después de wish se usa el pasado: «I wish I were taller».",
            "I wish I $BLANK fly. (poder)" to "could"
        ),
        Structure(
            "questions", "Preguntas", "palabra interrogativa + do/does/did + sujeto + verbo base",
            "Se usa un auxiliar antes del sujeto. Si la palabra interrogativa es el sujeto, no hay auxiliar.",
            listOf("Where did you go?" to "¿Adónde fuiste?", "What does he want?" to "¿Qué quiere?"),
            "Con who/what como sujeto no hay auxiliar: «Who broke it?», no «Who did break it?».",
            "Why $BLANK you leave?" to "did"
        ),
        Structure(
            "relative", "Oraciones de relativo", "sustantivo + who / that / which + verbo",
            "Sirven para añadir información sobre una persona o cosa dentro de la misma frase.",
            listOf("The man who saved me is gone." to "El hombre que me salvó se fue.", "This is the sword that I found." to "Esta es la espada que encontré."),
            "Who para personas, which para cosas, that sirve para ambos. En español todo es «que».",
            "The girl $BLANK lives next door is a hunter." to "who"
        ),
        Structure(
            "informal", "Habla informal: gonna, wanna, gotta", "gonna = going to · wanna = want to · gotta = have got to",
            "Formas habladas que verás mucho en diálogos de manhwa y series. Se entienden bien, pero no se escriben en textos formales.",
            listOf("I'm gonna find him." to "Voy a encontrarlo.", "You gotta be kidding." to "Debes de estar bromeando."),
            "Reconócelas al leer; al escribir usa la forma completa, salvo que imites un diálogo.",
            "«Wanna» es la forma corta de want $BLANK." to "to"
        ),
        Structure(
            "gerund_infinitive", "Gerundio o infinitivo", "enjoy / avoid / finish + -ing · want / hope / decide + to + verbo base",
            "Cada verbo pide una forma concreta después de él.",
            listOf("I enjoy reading manhwa." to "Disfruto leyendo manhwa.", "She decided to leave." to "Decidió irse."),
            "Con stop cambia el sentido: «stop smoking» (dejar de fumar) y «stop to smoke» (parar para fumar).",
            "He wants $BLANK (be) a hunter." to "to be"
        )
    )

    fun byId(id: String): Structure? = all.firstOrNull { it.id == id }

    /** Porcentaje (0..1) de estructuras que el usuario marcó como entendidas; ignora ids que ya no existen. */
    fun progress(done: Set<String>): Float = if (all.isEmpty()) 0f else all.count { it.id in done }.toFloat() / all.size

    /** ¿La respuesta escrita es correcta? No distingue mayúsculas, espacios dobles ni el apóstrofo curvo. */
    fun isCorrect(answer: String, typed: String): Boolean {
        fun n(s: String) = s.trim().lowercase().replace('’', '\'').replace(Regex("\\s+"), " ")
        return typed.isNotBlank() && n(answer) == n(typed)
    }
}
