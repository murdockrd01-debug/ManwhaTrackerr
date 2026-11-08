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
    @Test fun progressRaisesOnly() {
        val old = ListImport.create(e(50.0)).copy(rating = 3, updatedAt = 1)
        assertNull(ListImport.progress(old, e(50.0), 99))
        assertNull(ListImport.progress(old, e(10.0), 99))
        val m = ListImport.progress(old, e(60.0), 99)!!
        assertEquals(60.0, m.lastChapter, 0.0); assertEquals(99L, m.updatedAt); assertEquals(3, m.rating)
    }
    @Test fun progressPlanBecomesReading() {
        val old = ListImport.create(e(0.0, "PLANNING")).copy(status = Status.PLAN)
        assertEquals(Status.READING, ListImport.progress(old, e(5.0), 1)!!.status)
    }
    @Test fun progressCompleted() {
        val old = ListImport.create(e(190.0))
        assertEquals(Status.COMPLETED, ListImport.progress(old, e(200.0, "COMPLETED"), 1)!!.status)
    }
}
