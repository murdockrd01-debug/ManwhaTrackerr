package com.manhwatracker.app.data

import org.junit.Assert.*
import org.junit.Test

class SourceInfoTest {
    @Test fun tagsAndType() {
        assertEquals(listOf("Action", "Manhwa"), SourceInfo.tags(" Action , Manhwa,, "))
        assertEquals(Types.MANHWA, SourceInfo.type(listOf("Action", "Manhwa")))
        assertEquals(Types.MANHUA, SourceInfo.type(listOf("manhua")))
        assertEquals(Types.MANGA, SourceInfo.type(listOf("MANGA", "Drama")))
        assertNull(SourceInfo.type(listOf("Action")))
        assertNull(SourceInfo.type(emptyList()))
    }

    @Test fun adult() {
        assertTrue(SourceInfo.isAdult(listOf("Action", "Smut")))
        assertTrue(SourceInfo.isAdult(listOf("18+")))
        assertFalse(SourceInfo.isAdult(listOf("Mature", "Romance")))
        assertFalse(SourceInfo.isAdult(emptyList()))
    }

    @Test fun total() {
        assertEquals(50, SourceInfo.total(listOf(50.0, 49.5, null, 1.0), 4))
        assertEquals(3, SourceInfo.total(listOf(null, null, null), 3))
        assertEquals(2, SourceInfo.total(listOf(0.0, 0.5), 2))
    }
}
