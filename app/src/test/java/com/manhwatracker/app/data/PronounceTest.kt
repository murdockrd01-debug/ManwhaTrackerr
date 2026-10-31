package com.manhwatracker.app.data
import org.junit.Assert.*
import org.junit.Test

class PronounceTest {
    @Test fun exactAmongAlternatives() {
        assertEquals(Typing.Result.EXACT, Pronounce.check(listOf("hooligan", "hooligans"), "Hooligan"))
        assertEquals(Typing.Result.EXACT, Pronounce.check(listOf("wholigan", "hooligan"), "hooligan"))
    }
    @Test fun wrong() { assertEquals(Typing.Result.WRONG, Pronounce.check(listOf("hello"), "hooligan")); assertEquals(Typing.Result.WRONG, Pronounce.check(emptyList(), "a")) }
    @Test fun longPhraseMostWords() {
        assertEquals(Typing.Result.CLOSE, Pronounce.check(listOf("you are a hooligan today"), "you are a big hooligan"))
        assertEquals(Typing.Result.WRONG, Pronounce.check(listOf("you are"), "you are a big hooligan"))
    }
}
