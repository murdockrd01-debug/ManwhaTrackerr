package com.manhwatracker.app.data
import org.junit.Assert.*
import org.junit.Test

class ScanLogicTest {
    private val lines = listOf(
        ScanLogic.Line("ARE A HOOLIGAN", 100f, 200f, 300f, 230f),
        ScanLogic.Line("YOU", 100f, 160f, 160f, 190f),
        ScanLogic.Line("far away", 500f, 600f, 700f, 640f)
    )
    @Test fun pickRect() { assertEquals(listOf(0, 1), ScanLogic.pick(lines, 90f, 150f, 320f, 240f)) }
    @Test fun pickRectReversed() { assertEquals(listOf(0, 1), ScanLogic.pick(lines, 320f, 240f, 90f, 150f)) }
    @Test fun pickNone() { assertTrue(ScanLogic.pick(lines, 0f, 0f, 10f, 10f).isEmpty()) }
    @Test fun lineAtTap() { assertEquals(2, ScanLogic.lineAt(lines, 600f, 620f)); assertNull(ScanLogic.lineAt(lines, 0f, 0f)) }
    @Test fun joinOrder() { assertEquals("YOU ARE A HOOLIGAN", ScanLogic.join(lines, listOf(0, 1))) }
    @Test fun joinEmpty() { assertEquals("", ScanLogic.join(lines, emptyList())) }
    @Test fun words() {
        assertEquals(listOf("You", "are", "a", "hooligan", "don't"), ScanLogic.words("You are a hooligan, YOU ARE... don't! 12 x"))
    }
}
