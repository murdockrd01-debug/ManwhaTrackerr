package com.manhwatracker.app.data
import org.junit.Assert.*
import org.junit.Test

class UtilityTest {
    @Test fun tiers() {
        assertEquals(Utility.Tier.PHRASE, Utility.tier(50.0, "give up"))
        assertEquals(Utility.Tier.UNKNOWN, Utility.tier(-1.0, "hi"))
        assertEquals(Utility.Tier.BASIC, Utility.tier(500.0, "the"))
        assertEquals(Utility.Tier.VERY_USEFUL, Utility.tier(10.0, "x"))
        assertEquals(Utility.Tier.USEFUL, Utility.tier(1.0, "x"))
        assertEquals(Utility.Tier.UNCOMMON, Utility.tier(0.1, "x"))
        assertEquals(Utility.Tier.RARE, Utility.tier(0.01, "x"))
    }
    @Test fun order() {
        val l = listOf(Word(text = "rare", freq = 0.01), Word(text = "good", freq = 50.0), Word(text = "none")).sortedWith(Utility.byUsefulness)
        assertEquals(listOf("good", "rare", "none"), l.map { it.text })
    }
    @Test fun tag() { assertEquals(12.5, Utility.parseFreqTag(listOf("n", "f:12.5"))!!, 0.0); assertNull(Utility.parseFreqTag(listOf("n"))) }
    @Test fun example() { assertEquals("hi" to "hola", Utility.splitExample("hi ⇒ hola")); assertEquals("hi" to null, Utility.splitExample("hi")) }
    @Test fun titleKey() { assertEquals(titleKeyOf("The Boxer"), titleKeyOf("the-boxer!")); assertEquals("!!", titleKeyOf("!!")) }
}
