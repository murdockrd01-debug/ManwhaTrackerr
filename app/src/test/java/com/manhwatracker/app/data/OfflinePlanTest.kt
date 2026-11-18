package com.manhwatracker.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class OfflinePlanTest {
    private val keys = listOf("a", "b", "c", "d", "e")

    @Test fun nextN() {
        assertEquals(listOf(1, 2), OfflinePlan.pick(keys, setOf("a"), 0, 2))
        assertEquals(listOf(2, 4), OfflinePlan.pick(keys, setOf("d", "b"), 1, 2))
    }

    @Test fun all() {
        assertEquals(listOf(0, 2, 4), OfflinePlan.pick(keys, setOf("b", "d"), 0, 0))
        assertEquals(emptyList<Int>(), OfflinePlan.pick(keys, keys.toSet(), 0, 0))
    }

    @Test fun edges() {
        assertEquals(emptyList<Int>(), OfflinePlan.pick(keys, emptySet(), 9, 3))
        assertEquals(listOf(0), OfflinePlan.pick(keys, emptySet(), -4, 1))
        assertEquals("3/12", OfflinePlan.progress(3, 12))
    }

    @Test fun sizes() {
        assertEquals("0 KB", OfflinePlan.size(-5))
        assertEquals("850 KB", OfflinePlan.size(850L * 1024))
        assertEquals("12.5 MB", OfflinePlan.size((12.5 * 1048576).toLong()))
        assertEquals("1.5 GB", OfflinePlan.size((1.5 * 1073741824).toLong()))
    }
}
