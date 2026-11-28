package com.manhwatracker.app.data

/**
 * Dice qué estructuras del inglés usa una frase. Funciona sin internet, con reglas simples:
 * es una guía para aprender, no un análisis gramatical perfecto.
 */
object StructureDetector {
    /** [id] de una estructura de [Structures] y [why] = la pista (en español) que lo justifica. */
    class Match(val id: String, val why: String)

    private val irregularParticiple = setOf(
        "been", "done", "gone", "seen", "taken", "given", "eaten", "written", "spoken", "broken", "chosen", "known", "grown",
        "shown", "thrown", "flown", "drawn", "driven", "ridden", "risen", "fallen", "forgotten", "gotten", "hidden", "bitten",
        "worn", "torn", "begun", "drunk", "sung", "swum", "rung", "sunk", "stolen", "frozen", "woken", "become", "come", "run",
        "made", "said", "paid", "laid", "told", "sold", "held", "heard", "kept", "left", "lost", "met", "read", "sent", "spent",
        "stood", "understood", "won", "found", "bought", "brought", "caught", "taught", "thought", "fought", "built", "meant",
        "felt", "slept", "led", "fed", "shot", "sat", "hung", "dug", "put", "cut", "hit", "let", "set", "shut", "hurt", "got", "had"
    )
    private val irregularPast = setOf(
        "went", "saw", "took", "gave", "ate", "wrote", "spoke", "broke", "chose", "knew", "grew", "threw", "flew", "drew", "drove",
        "rode", "rose", "fell", "forgot", "got", "hid", "bit", "wore", "tore", "began", "drank", "sang", "swam", "rang", "sank",
        "stole", "froze", "woke", "was", "were", "made", "said", "paid", "told", "sold", "held", "heard", "kept", "left", "lost",
        "met", "sent", "spent", "stood", "understood", "won", "found", "bought", "brought", "caught", "taught", "thought", "fought",
        "built", "meant", "felt", "slept", "came", "ran", "became", "sat", "led", "fed", "shot", "hung", "dug", "did", "had"
    )
    private val notEd = setOf("need", "speed", "bed", "red", "seed", "feed", "weed", "indeed", "breed", "proceed", "succeed", "exceed", "hundred", "naked", "sacred", "wicked")
    private val notPassive = setOf(
        "tired", "excited", "bored", "interested", "scared", "worried", "surprised", "married", "pleased", "relaxed", "shocked",
        "confused", "satisfied", "embarrassed", "annoyed", "amazed", "frightened", "disappointed", "determined", "used", "supposed"
    )
    private val notIng = setOf("thing", "something", "nothing", "anything", "everything", "king", "ring", "spring", "morning", "evening", "during", "bring", "sing", "string", "wing", "swing", "sting")
    private val notEst = setOf("interest", "forest", "request", "west", "honest", "modest", "guest", "nest", "rest", "test", "vest", "contest", "harvest", "protest", "suggest", "invest", "arrest", "quest", "chest", "crest", "east")
    private val skip = setOf("not", "never", "already", "just", "ever", "still", "always", "also", "often", "really", "now", "once", "even", "only", "finally", "probably")
    private val subjects = setOf("i", "you", "he", "she", "it", "we", "they")
    private val gap = setOf("it", "them", "me", "him", "her", "us", "you", "the", "a", "an", "this", "that", "my", "your", "his", "their", "our")
    private val gerundVerbs = setOf("enjoy", "avoid", "finish", "keep", "mind", "stop", "quit", "suggest", "consider", "practice", "imagine", "miss", "risk", "postpone")
    private val infinitiveVerbs = setOf("want", "hope", "decide", "plan", "need", "try", "learn", "refuse", "promise", "agree", "manage", "afford", "expect", "wish", "offer", "pretend")
    private val whWords = setOf("what", "where", "when", "why", "who", "whom", "whose", "how", "which")
    private val auxStart = setOf("do", "does", "did", "is", "are", "was", "were", "can", "could", "will", "would", "should", "have", "has", "had", "am", "may", "might", "shall")
    private val irregularLemma = mapOf(
        "gave" to "give", "came" to "come", "took" to "take", "found" to "find", "got" to "get", "ran" to "run", "went" to "go",
        "made" to "make", "broke" to "break", "sat" to "sit", "woke" to "wake", "brought" to "bring", "held" to "hold", "kept" to "keep",
        "left" to "leave", "lost" to "lose", "ate" to "eat", "fell" to "fall", "threw" to "throw", "drew" to "draw", "grew" to "grow",
        "stood" to "stand", "ran" to "run", "told" to "tell", "met" to "meet", "sent" to "send", "led" to "lead", "wore" to "wear",
        "shut" to "shut", "cut" to "cut", "put" to "put", "set" to "set", "let" to "let", "hung" to "hang", "caught" to "catch"
    )
    private val phrasal = setOf(
        "give up", "give in", "give away", "give back", "come up", "come back", "come out", "come on", "come in", "come over",
        "take off", "take on", "take out", "take over", "put on", "put off", "put out", "put up", "put down", "find out", "get up",
        "get out", "get back", "get on", "get off", "get along", "get over", "turn on", "turn off", "turn up", "turn down", "turn out",
        "pick up", "break down", "break up", "break out", "run away", "run out", "run into", "set up", "set off", "carry on", "carry out",
        "work out", "look up", "look out", "look after", "look for", "look forward", "make up", "make out", "hold on", "hold up",
        "hang out", "hang on", "show up", "show off", "sit down", "stand up", "wake up", "grow up", "throw away", "throw up",
        "check in", "check out", "cheer up", "calm down", "slow down", "settle down", "fall down", "fall apart", "fall behind",
        "go on", "go out", "go away", "go back", "go over", "keep up", "keep on", "let down", "let go", "point out", "pass out",
        "pass away", "figure out", "bring up", "bring back", "call off", "call back", "clean up", "drop out", "fill in", "fill out",
        "log in", "hand in", "sign up", "back up", "end up", "move on", "move out", "pull out", "wear out", "shut down", "shut up",
        "cut off", "cut down", "blow up", "catch up", "step in", "step up", "try on", "try out", "mess up", "open up", "speak up"
    )

    /** Pasa la frase a palabras en minúscula, con las contracciones abiertas (I've → i have, don't → do not). */
    fun tokens(sentence: String): List<String> {
        var s = sentence.lowercase().replace('’', '\'').replace('‘', '\'')
        s = s.replace("won't", "will not").replace("can't", "can not").replace("cannot", "can not").replace("shan't", "shall not")
            .replace("n't", " not").replace("'ve", " have").replace("'ll", " will").replace("'re", " are").replace("'m", " am")
        val words = Regex("[a-z]+(?:'[a-z]+)?").findAll(s).map { it.value }.toList()
        val out = ArrayList<String>()
        for ((i, w) in words.withIndex()) {
            val next = words.getOrNull(i + 1).orEmpty()
            val nxt = words.drop(i + 1).firstOrNull { it !in skip }.orEmpty()
            when {
                w.endsWith("'d") -> { out += w.removeSuffix("'d"); out += if (isParticiple(next)) "had" else "would" }
                w.endsWith("'s") && w.length > 2 -> {
                    out += w.removeSuffix("'s")
                    if (w.removeSuffix("'s") in subjects || w == "that's" || w == "there's" || w == "what's" || w == "who's") {
                        out += if (nxt in irregularParticiple && nxt != "gone") "has" else "is"
                    }
                }
                else -> out += w
            }
        }
        return out
    }

    private fun isParticiple(t: String) = t in irregularParticiple || (t.length > 3 && t.endsWith("ed") && t !in notEd)
    private fun isIng(t: String) = t.length > 4 && t.endsWith("ing") && t !in notIng

    private fun nextWord(t: List<String>, i: Int): Int {
        var j = i + 1
        while (j < t.size && t[j] in skip) j++
        return j
    }

    private fun lemmas(w: String): List<String> {
        val l = mutableListOf(w, w.removeSuffix("s"), w.removeSuffix("es"), w.removeSuffix("ed"), w.removeSuffix("d"))
        irregularLemma[w]?.let { l += it }
        if (w.endsWith("ing")) {
            val b = w.removeSuffix("ing"); l += b; l += b + "e"
            if (b.length > 2 && b[b.length - 1] == b[b.length - 2]) l += b.dropLast(1)
        }
        if (w.endsWith("ied")) l += w.removeSuffix("ied") + "y"
        if (w.endsWith("ies")) l += w.removeSuffix("ies") + "y"
        return l
    }

    /** Estructuras detectadas en [sentence], de la más específica a la más general. Vacío si no se reconoce ninguna. */
    fun detect(sentence: String): List<Match> {
        val t = tokens(sentence)
        if (t.isEmpty()) return emptyList()
        val found = LinkedHashMap<String, String>()
        fun add(id: String, why: String) { if (id !in found) found[id] = why }

        val hasIf = "if" in t
        var thirdCond = false
        if (hasIf) {
            val hadIdx = t.indices.firstOrNull { t[it] == "had" && isParticiple(t.getOrElse(nextWord(t, it)) { "" }) }
            val wouldHave = t.indices.firstOrNull { t[it] in setOf("would", "could", "might") && t.getOrElse(nextWord(t, it)) { "" } == "have" }
            if (hadIdx != null && wouldHave != null) { thirdCond = true; add("third_conditional", "if + had + participio, would have + participio") }
        }

        for (i in t.indices) {
            val w = t[i]
            val n = nextWord(t, i)
            val nw = t.getOrElse(n) { "" }
            when (w) {
                "had" -> if (!thirdCond && isParticiple(nw) && nw != "had") add("past_perfect", "had + «$nw» (participio)")
                "have", "has" -> {
                    if (nw == "been" && isIng(t.getOrElse(nextWord(t, n)) { "" })) add("present_perfect_continuous", "$w been + «${t[nextWord(t, n)]}»")
                    else if (isParticiple(nw) && nw != "had" && t.getOrElse(i - 1) { "" } !in setOf("would", "could", "might", "should", "must", "will")) add("present_perfect", "$w + «$nw» (participio)")
                    if (nw == "to") add("should_must", "$w to = obligación")
                }
                "was", "were" -> if (isIng(nw)) add("past_continuous", "$w + «$nw»")
                "am", "is", "are" -> when {
                    nw == "going" && t.getOrElse(n + 1) { "" } == "to" -> add("will_going_to", "$w going to + verbo")
                    isIng(nw) -> add("present_continuous", "$w + «$nw»")
                }
            }
            if (w in setOf("is", "are", "am", "was", "were", "be", "been", "being") && nw !in notPassive && isParticiple(nw) && nw != "had" && nw != "been" && nw != "gone")
                add("passive", "$w + «$nw» (participio)")
            if (w == "will" || w == "gonna") add("will_going_to", if (w == "will") "will + verbo" else "gonna = going to")
            if (w == "can" || w == "could") add("can_could", "$w + verbo")
            if (w == "should" || w == "must" || w == "ought") add("should_must", "$w = consejo u obligación")
            if (w == "had" && nw == "to") add("should_must", "had to = tuve que")
            if (w == "used" && nw == "to") add("used_to", "used to + verbo")
            if (w == "wish" || (w == "if" && nw == "only")) add("wish", "wish / if only")
            if (w == "than") add("comparatives", "than: comparación")
            if (w == "as" && (1..3).any { t.getOrElse(i + it) { "" } == "as" }) add("comparatives", "as … as: igualdad")
            if (w == "the" && nw.length > 4 && nw.endsWith("est") && nw !in notEst) add("comparatives", "the «$nw»: superlativo")
            if (w == "the" && nw == "most") add("comparatives", "the most: superlativo")
            if (w == "more" && t.drop(i).take(4).contains("than")) add("comparatives", "more … than")
            if (w in setOf("gonna", "wanna", "gotta", "gimme", "lemme", "kinda", "dunno")) add("informal", "«$w» es habla informal")
            if (w == "who" || w == "which" || w == "whose") {
                if (i > 0 && t[i - 1] !in whWords && t[i - 1] !in auxStart && !sentence.trim().startsWith(w, ignoreCase = true)) add("relative", "$w une la información con la frase anterior")
            }
            if (w in gerundVerbs || lemmas(w).any { it in gerundVerbs }) {
                if (isIng(nw)) add("gerund_infinitive", "$w + «$nw» (verbo-ing)")
            }
            if (lemmas(w).any { it in infinitiveVerbs } && nw == "to") add("gerund_infinitive", "$w + to + verbo")
            // phrasal verbs: verbo + partícula (a veces con "it", "the"… en medio)
            for (l in lemmas(w)) {
                val p1 = t.getOrElse(i + 1) { "" }
                val p2 = if (p1 in gap) t.getOrElse(i + 2) { "" } else ""
                if ("$l $p1" in phrasal) { add("phrasal_verbs", "$w $p1 = phrasal verb"); break }
                if (p2.isNotEmpty() && "$l $p2" in phrasal) { add("phrasal_verbs", "$w … $p2 = phrasal verb"); break }
            }
        }

        // Condicionales de 1.º y 2.º tipo
        if (hasIf && !thirdCond) {
            val hasWould = t.any { it == "would" || it == "could" || it == "might" }
            val hasWill = "will" in t
            val pastInIf = t.any { it == "were" || it in irregularPast || (it.endsWith("ed") && isParticiple(it)) }
            if (hasWould && pastInIf) add("second_conditional", "if + pasado, would + verbo")
            else if (hasWill) add("first_conditional", "if + presente, will + verbo")
        }

        val q = sentence.trim().endsWith("?") || (t.size > 1 && t[0] in whWords && t[1] in auxStart) || (t[0] in auxStart && t.size > 2 && t[1] in subjects)
        if (q) add("questions", "pregunta: auxiliar antes del sujeto")

        // Tiempos básicos: solo si no apareció ya una forma verbal más específica
        val core = setOf(
            "present_continuous", "past_continuous", "present_perfect", "present_perfect_continuous", "past_perfect", "will_going_to",
            "can_could", "should_must", "used_to", "wish", "passive", "first_conditional", "second_conditional", "third_conditional"
        )
        if (found.keys.none { it in core }) {
            val pastWord = t.firstOrNull { it in irregularPast || (it.length > 3 && it.endsWith("ed") && it !in notEd && it !in notPassive) }
            when {
                "did" in t && t.indexOf("did") < t.size - 1 -> add("past_simple", "did + verbo base")
                pastWord != null -> add("past_simple", "«$pastWord» está en pasado")
                t.size >= 3 && (t.any { it in subjects } || "do" in t || "does" in t) -> add("present_simple", "presente: hábito o hecho")
                t.size >= 3 && found.isEmpty() -> add("present_simple", "parece presente simple")
            }
        }

        return found.map { Match(it.key, it.value) }
    }
}
