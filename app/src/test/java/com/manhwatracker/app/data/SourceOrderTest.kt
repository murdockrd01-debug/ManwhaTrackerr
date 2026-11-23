package com.manhwatracker.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SourceOrderTest {
    private val names = listOf("Zeta", "alfa", "Beta", "Gamma")
    private fun arr(favs: Set<String>, hidden: Set<String>, show: Boolean) =
        SourceOrder.arrange(names, { it }, { it }, favs, hidden, show)

    @Test fun alphabeticalByDefault() = assertEquals(listOf("alfa", "Beta", "Gamma", "Zeta"), arr(emptySet(), emptySet(), false))
    @Test fun favoritesFirst() = assertEquals(listOf("Gamma", "Zeta", "alfa", "Beta"), arr(setOf("Zeta", "Gamma"), emptySet(), false))
    @Test fun hiddenRemoved() = assertEquals(listOf("alfa", "Gamma", "Zeta"), arr(emptySet(), setOf("Beta"), false))
    @Test fun hiddenShownAtEnd() = assertEquals(listOf("alfa", "Gamma", "Zeta", "Beta"), arr(emptySet(), setOf("Beta"), true))
    @Test fun hiddenFavoriteStaysHidden() = assertEquals(listOf("alfa", "Gamma", "Zeta"), arr(setOf("Beta"), setOf("Beta"), false))
    @Test fun toggle() {
        assertEquals(setOf("a"), SourceOrder.toggled(emptySet(), "a"))
        assertEquals(emptySet<String>(), SourceOrder.toggled(setOf("a"), "a"))
    }
    @Test fun keyFormat() = assertEquals("pkg:5", SourceOrder.key("pkg", 5L))
}
