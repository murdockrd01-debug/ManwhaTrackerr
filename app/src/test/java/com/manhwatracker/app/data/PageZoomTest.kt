package com.manhwatracker.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PageZoomTest {
    private val w = 1000f
    private val h = 2000f

    @Test fun pinchKeepsPointStill() {
        val t = PageZoom.pinch(PageZoom.T(), 2f, 700f, 1500f, 0f, 0f, w, h)
        assertEquals(2f, t.s, 0.001f)
        // El punto tocado (700,1500) queda en su sitio: centro + t + s*q, con q = (700-500, 1500-1000)
        assertEquals(700f, 500f + t.tx + t.s * 200f, 0.01f)
        assertEquals(1500f, 1000f + t.ty + t.s * 500f, 0.01f)
    }

    @Test fun scaleIsLimited() {
        assertEquals(PageZoom.MAX, PageZoom.pinch(PageZoom.T(), 100f, 500f, 1000f, 0f, 0f, w, h).s, 0.001f)
        assertEquals(1f, PageZoom.pinch(PageZoom.T(2f, 100f, 100f), 0.01f, 500f, 1000f, 0f, 0f, w, h).s, 0.001f)
        assertEquals(0f, PageZoom.pinch(PageZoom.T(2f, 100f, 100f), 0.01f, 500f, 1000f, 0f, 0f, w, h).tx, 0.001f)
    }

    @Test fun cannotDragPastEdges() {
        val t = PageZoom.drag(PageZoom.T(2f), 99999f, -99999f, w, h)
        assertEquals(500f, t.tx, 0.001f)
        assertEquals(-1000f, t.ty, 0.001f)
    }

    @Test fun dragDoesNothingWhenNotZoomed() {
        assertEquals(PageZoom.T(), PageZoom.drag(PageZoom.T(), 50f, 50f, w, h))
    }

    @Test fun doubleTapToggles() {
        val z = PageZoom.doubleTap(PageZoom.T(), 200f, 300f, w, h)
        assertTrue(z.zoomed)
        assertEquals(PageZoom.DOUBLE_TAP, z.s, 0.001f)
        val back = PageZoom.doubleTap(z, 200f, 300f, w, h)
        assertFalse(back.zoomed)
        assertEquals(PageZoom.T(), back)
    }
}
