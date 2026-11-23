package com.manhwatracker.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChapterFlowTest {
    @Test fun nearEnd() {
        assertFalse(ChapterFlow.nearEnd(0, 0))
        assertFalse(ChapterFlow.nearEnd(5, 20))
        assertTrue(ChapterFlow.nearEnd(12, 20))
        assertTrue(ChapterFlow.nearEnd(20, 20))
        assertTrue(ChapterFlow.nearEnd(0, 1))
    }

    @Test fun autoAdvance() {
        assertTrue(ChapterFlow.shouldAutoAdvance(true, true, 3, 10))
        assertFalse(ChapterFlow.shouldAutoAdvance(false, true, 3, 10))
        assertFalse(ChapterFlow.shouldAutoAdvance(true, false, 3, 10))
        assertFalse(ChapterFlow.shouldAutoAdvance(true, true, 10, 10))
    }

    @Test fun prefetch() {
        assertEquals(0, ChapterFlow.prefetchCount(0))
        assertEquals(2, ChapterFlow.prefetchCount(2))
        assertEquals(3, ChapterFlow.prefetchCount(40))
    }
}
