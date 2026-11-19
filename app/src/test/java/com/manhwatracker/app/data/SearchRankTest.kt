package com.manhwatracker.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchRankTest {
    @Test fun scores() {
        assertEquals(SearchRank.EXACT, SearchRank.score("solo leveling", "Solo Leveling"))
        assertEquals(SearchRank.EXACT, SearchRank.score("Solo-Leveling", "solo leveling!"))
        assertEquals(SearchRank.STARTS, SearchRank.score("solo leveling", "Solo Leveling: Ragnarok"))
        assertEquals(SearchRank.CONTAINS, SearchRank.score("leveling", "Solo Leveling"))
        assertEquals(SearchRank.NONE, SearchRank.score("naruto", "Solo Leveling"))
        assertEquals(SearchRank.NONE, SearchRank.score("", "Solo Leveling"))
    }

    @Test fun sorting() {
        val titles = listOf("Ragnarok of Solo Leveling", "Solo Leveling: Ragnarok", "Solo Leveling", "Otro")
        assertEquals(
            listOf("Solo Leveling", "Solo Leveling: Ragnarok", "Ragnarok of Solo Leveling", "Otro"),
            SearchRank.sortTitles("solo leveling", titles) { it }
        )
        assertEquals(SearchRank.EXACT, SearchRank.best("solo leveling", listOf("x", "Solo Leveling")))
        assertEquals(SearchRank.NONE, SearchRank.best("solo leveling", emptyList()))
    }
}
