package com.manhwatracker.app.data

import org.junit.Assert.*
import org.junit.Test

class CoverStyleTest {
    @Test fun sizes() {
        assertEquals(MEDIUM_GRID, CoverStyle.gridMinDp(CoverStyle.MEDIUM))
        assertTrue(CoverStyle.gridMinDp("S") < CoverStyle.gridMinDp("M"))
        assertTrue(CoverStyle.gridMinDp("M") < CoverStyle.gridMinDp("L"))
        assertTrue(CoverStyle.gridMinDp("L") < CoverStyle.gridMinDp("XL"))
        assertTrue(CoverStyle.listWidthDp("S") < CoverStyle.listWidthDp("XL"))
        assertEquals(CoverStyle.MEDIUM, CoverStyle.normalize("basura"))
        assertEquals(CoverStyle.MEDIUM, CoverStyle.normalize(null))
    }

    @Test fun adult() {
        assertTrue(CoverStyle.isAdult("Action, Hentai", null, false))
        assertFalse(CoverStyle.isAdult("Action, Romance", null, false))
        assertFalse(CoverStyle.isAdult(null, null, false))
        assertFalse(CoverStyle.isAdult("Ecchi", null, false))
        assertTrue(CoverStyle.isAdult("Ecchi", null, true))
        assertTrue(CoverStyle.isAdult("Action", true, false))
        assertFalse(CoverStyle.isAdult("Hentai", false, false))
    }

    private companion object { const val MEDIUM_GRID = 104 }
}
