package com.manhwatracker.app.data

import com.manhwatracker.app.data.OpenTarget.Target
import org.junit.Assert.assertEquals
import org.junit.Test

class OpenTargetTest {
    @Test fun linkedGoesStraightToDetail() = assertEquals(Target.DETAIL, OpenTarget.decide(true, true, true))
    @Test fun linkedWithoutMangaSearchesInThatSource() = assertEquals(Target.BROWSE, OpenTarget.decide(true, false, true))
    @Test fun neverLinkedSearchesEverywhere() {
        assertEquals(Target.SEARCH, OpenTarget.decide(false, false, true))
        assertEquals(Target.SEARCH, OpenTarget.decide(false, true, true))
    }
    @Test fun missingSourceSearchesEverywhere() {
        assertEquals(Target.SEARCH, OpenTarget.decide(true, true, false))
        assertEquals(Target.SEARCH, OpenTarget.decide(true, false, false))
    }
}
