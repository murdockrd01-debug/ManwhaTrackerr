package com.manhwatracker.app.data
import org.junit.Assert.*
import org.junit.Test

class ListImportTest {
    private fun e(p: Double = 10.0, s: String = "CURRENT") = ListImport.Entry("The Boxer", s, p, 200, 9, "c", listOf("Action"), 5)
    @Test fun status() {
        assertEquals(Status.READING, ListImport.mapStatus("CURRENT")); assertEquals(Status.PAUSED, ListImport.mapStatus("DROPPED"))
        assertEquals(Status.COMPLETED, ListImport.mapStatus("COMPLETED")); assertEquals(Status.PLAN, ListImport.mapStatus("PLANNING"))
    }
    @Test fun create() {
        val m = ListImport.create(e()); assertEquals("theboxer", m.titleKey); assertEquals(9, m.rating); assertEquals("Action", m.genres)
    }
    @Test fun mergeKeepsMine() {
        val old = ListImport.create(e(50.0)).copy(rating = 0, status = Status.COMPLETED)
        val m = ListImport.merge(old, e(10.0))
        assertEquals(50.0, m.lastChapter, 0.0); assertEquals(Status.COMPLETED, m.status); assertEquals(9, m.rating)
        assertEquals(60.0, ListImport.merge(old, e(60.0)).lastChapter, 0.0)
    }
}
