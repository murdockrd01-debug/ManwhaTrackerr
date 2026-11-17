package com.manhwatracker.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChapterPickTest {
    @Test fun numberFromNameOrDeclared() {
        assertEquals(7.5, ChapterPick.number(7.5f, "lo que sea")!!, 0.0001)
        assertEquals(42.0, ChapterPick.number(-1f, "Chapter 42")!!, 0.0001)
        assertEquals(null, ChapterPick.number(-1f, "Prólogo"))
    }

    @Test fun order() {
        assertTrue(ChapterPick.newestFirst(listOf(3.0, 2.0, 1.0)))
        assertFalse(ChapterPick.newestFirst(listOf(1.0, 2.0, 3.0)))
        assertTrue(ChapterPick.newestFirst(listOf(null, null)))
        assertTrue(ChapterPick.newestFirst(listOf(null, 5.0, 4.0, null)))
    }

    @Test fun next() {
        val asc = listOf(1.0, 2.0, 3.0, 4.0)
        assertEquals(2, ChapterPick.nextAfter(asc, 2.0))
        assertEquals(0, ChapterPick.nextAfter(asc, 0.0))
        assertEquals(-1, ChapterPick.nextAfter(asc, 4.0))
        assertEquals(1, ChapterPick.nextAfter(listOf(1.0, 2.0, null, 3.0), 1.0))
        assertEquals(3, ChapterPick.nextAfter(listOf(1.0, 2.0, null, 3.0), 2.0).let { if (it == 3) 3 else it })
    }
}
