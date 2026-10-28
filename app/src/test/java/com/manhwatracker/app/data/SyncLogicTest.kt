package com.manhwatracker.app.data
import org.junit.Assert.*
import org.junit.Test

import java.time.LocalDate
import com.manhwatracker.app.data.SyncLogic.FileInfo
class SyncLogicTest {
    @Test fun isBackup() { assertTrue(SyncLogic.isKomikkuBackup("A.TACHIBK")); assertTrue(SyncLogic.isKomikkuBackup("x.proto.gz")); assertFalse(SyncLogic.isKomikkuBackup("x.json")) }
    @Test fun newest() {
        val f = listOf(FileInfo("a.tachibk", 5, 1), FileInfo("b.tachibk", 9, 1), FileInfo("c.json", 99, 1))
        assertEquals("b.tachibk", SyncLogic.newestKomikku(f)?.name)
        assertNull(SyncLogic.newestKomikku(emptyList()))
    }
    @Test fun fingerprintChanges() { assertNotEquals(SyncLogic.fingerprint(FileInfo("a", 1, 2)), SyncLogic.fingerprint(FileInfo("a", 2, 2))) }
    @Test fun autoNames() {
        val n = SyncLogic.autoName(LocalDate.of(2026, 10, 28))
        assertTrue(SyncLogic.isAutoName(n)); assertEquals(LocalDate.of(2026, 10, 28), SyncLogic.autoDate(n))
        assertTrue(SyncLogic.isAutoName("manhwa-tracker-auto-2026-10-28 (1).json"))
        assertFalse(SyncLogic.isAutoName("mis-fotos.json"))
    }
    @Test fun toDelete() {
        val files = (1..10).map { FileInfo(SyncLogic.autoName(LocalDate.of(2026, 10, it)), 0, 0) } + FileInfo("otro.json", 0, 0)
        val del = SyncLogic.toDelete(files, 7)
        assertEquals(3, del.size); assertFalse(del.any { it.name == "otro.json" })
        assertTrue(del.all { SyncLogic.autoDate(it.name)!!.dayOfMonth <= 3 })
    }
    @Test fun due() { assertTrue(SyncLogic.isDue(0, 10, 5)); assertTrue(SyncLogic.isDue(100, 50, 5)); assertFalse(SyncLogic.isDue(10, 12, 5)); assertTrue(SyncLogic.isDue(10, 15, 5)) }
}
