package com.manhwatracker.app.data
import org.junit.Assert.*
import org.junit.Test

class CrashLogTest {
    private val sep = "\n=====\n"
    @Test fun shortUntouched() { assertEquals("a", CrashLog.trim("a", 10)) }
    @Test fun keepsRecentWholeEntries() {
        val text = "viejo-viejo-viejo" + sep + "medio-medio" + sep + "nuevo"
        val t = CrashLog.trim(text, 20)
        assertEquals("nuevo", t.substringAfterLast(sep))
        assertFalse(t.contains("viejo"))
    }
}
