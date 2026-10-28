package com.manhwatracker.app.data
import org.junit.Assert.*
import org.junit.Test

class TypingTest {
    @Test fun normalize() { assertEquals("dont give up", Typing.normalize("  Don't  GIVE-up! ")) }
    @Test fun exact() { assertEquals(Typing.Result.EXACT, Typing.check("Give Up.", "give up")) }
    @Test fun shortWordNoTypo() { assertEquals(Typing.Result.WRONG, Typing.check("form", "from")) }
    @Test fun longWordClose() { assertEquals(Typing.Result.CLOSE, Typing.check("hooligon", "hooligan")) }
    @Test fun empty() { assertEquals(Typing.Result.WRONG, Typing.check("", "a")) }
    @Test fun hint() { assertEquals("g___ u_", Typing.hint("give up")) }
    @Test fun distance() { assertEquals(3, Typing.distance("kitten", "sitting")); assertEquals(0, Typing.distance("a", "a")) }
}
