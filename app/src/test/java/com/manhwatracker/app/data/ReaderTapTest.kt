package com.manhwatracker.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderTapTest {
    @Test fun zones() {
        assertEquals(ReaderTap.PREV, ReaderTap.zone(10f, 100f, false))
        assertEquals(ReaderTap.MENU, ReaderTap.zone(50f, 100f, false))
        assertEquals(ReaderTap.NEXT, ReaderTap.zone(90f, 100f, false))
        // derecha a izquierda: el lado izquierdo avanza
        assertEquals(ReaderTap.NEXT, ReaderTap.zone(10f, 100f, true))
        assertEquals(ReaderTap.PREV, ReaderTap.zone(90f, 100f, true))
        assertEquals(ReaderTap.MENU, ReaderTap.zone(50f, 100f, true))
        assertEquals(ReaderTap.MENU, ReaderTap.zone(5f, 0f, false))
    }

    @Test fun modes() {
        assertEquals("V", ReaderTap.normalizeMode(null))
        assertEquals("V", ReaderTap.normalizeMode("x"))
        assertEquals("RTL", ReaderTap.normalizeMode("RTL"))
        assertEquals("LTR", ReaderTap.normalizeMode("LTR"))
    }
}
