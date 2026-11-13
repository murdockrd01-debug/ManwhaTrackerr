package com.manhwatracker.app.data

import org.junit.Assert.*
import org.junit.Test

class ReaderLogicTest {
    @Test fun imagesByExtension() {
        assertTrue(ReaderLogic.isImage("001.JPG"))
        assertTrue(ReaderLogic.isImage("a/b/p.webp"))
        assertFalse(ReaderLogic.isImage("notas.txt"))
        assertFalse(ReaderLogic.isImage(".oculta.jpg"))
        assertFalse(ReaderLogic.isImage("__MACOSX/._1.jpg"))
        assertFalse(ReaderLogic.isImage("sinextension"))
    }

    @Test fun archives() {
        assertTrue(ReaderLogic.isArchive("Cap 1.CBZ"))
        assertTrue(ReaderLogic.isArchive("a.zip"))
        assertFalse(ReaderLogic.isArchive("a.cbr"))
        assertFalse(ReaderLogic.isArchive("a.jpg"))
    }

    @Test fun naturalOrder() {
        val l = listOf("10.jpg", "2.jpg", "1.jpg").sortedWith(ReaderLogic.natural)
        assertEquals(listOf("1.jpg", "2.jpg", "10.jpg"), l)
        assertEquals(listOf("Cap 2", "Cap 10", "Cap 100"), listOf("Cap 100", "Cap 10", "Cap 2").sortedWith(ReaderLogic.natural))
    }

    @Test fun naturalIsConsistentOnTies() {
        val c1 = ReaderLogic.compareNatural("02.jpg", "2.jpg")
        val c2 = ReaderLogic.compareNatural("2.jpg", "02.jpg")
        assertTrue(c1 < 0 && c2 > 0)
        assertEquals(0, ReaderLogic.compareNatural("a.jpg", "a.jpg"))
    }

    @Test fun sortPagesKeepsOnlyImages() {
        assertEquals(listOf("1.jpg", "2.png"), ReaderLogic.sortPages(listOf("2.png", "leeme.txt", "1.jpg", ".x.jpg")))
    }

    @Test fun zoomAndPan() {
        assertEquals(1f, ReaderLogic.clampZoom(0.2f), 0.0001f)
        assertEquals(ReaderLogic.MAX_ZOOM, ReaderLogic.clampZoom(9f), 0.0001f)
        assertEquals(500f, ReaderLogic.maxPan(1000f, 2f), 0.0001f)
        assertEquals(500f, ReaderLogic.clampPan(900f, 1000f, 2f), 0.0001f)
        assertEquals(-500f, ReaderLogic.clampPan(-900f, 1000f, 2f), 0.0001f)
        assertEquals(0f, ReaderLogic.clampPan(50f, 1000f, 1f), 0.0001f)
    }

    @Test fun pagesAndProgress() {
        assertEquals(2, ReaderLogic.clampPage(5, 3))
        assertEquals(0, ReaderLogic.clampPage(-1, 3))
        assertEquals(0, ReaderLogic.clampPage(2, 0))
        assertEquals(25, ReaderLogic.percent(0, 4))
        assertEquals(100, ReaderLogic.percent(3, 4))
        assertEquals(0, ReaderLogic.percent(0, 0))
    }

    @Test fun ratios() {
        assertNull(ReaderLogic.safeRatio(0, 10))
        assertEquals(0.5f, ReaderLogic.safeRatio(800, 1600)!!, 0.001f)
        assertEquals(0.02f, ReaderLogic.safeRatio(1, 100000)!!, 0.001f)
    }

    @Test fun placeRoundTrip() {
        val p = ReaderLogic.Place("content://t", "id/1", "id/2", "Cap 1")
        assertEquals(p, ReaderLogic.decodePlace(ReaderLogic.encodePlace(p)))
        val loose = ReaderLogic.Place("", "", "content://x", "a.cbz")
        assertEquals(loose, ReaderLogic.decodePlace(ReaderLogic.encodePlace(loose)))
        assertNull(ReaderLogic.decodePlace(null))
        assertNull(ReaderLogic.decodePlace(""))
        assertNull(ReaderLogic.decodePlace("a\u0001b"))
    }

    @Test fun folderNames() {
        assertEquals("Cap 1", ReaderLogic.nameFromDocId("primary:Download/Manhwa/Cap 1"))
        assertEquals("Download", ReaderLogic.nameFromDocId("primary:Download"))
        assertEquals("Carpeta", ReaderLogic.nameFromDocId("primary:"))
    }

    @Test fun continuousIndexing() {
        val c = listOf(3, 2)
        assertEquals(0, ReaderLogic.flatIndex(c, 0, 0)); assertEquals(3, ReaderLogic.flatIndex(c, 0, 3))
        assertEquals(4, ReaderLogic.flatIndex(c, 1, 0)); assertEquals(6, ReaderLogic.flatIndex(c, 1, 2))
        assertEquals(0 to 2, ReaderLogic.locate(c, 2)); assertEquals(0 to 3, ReaderLogic.locate(c, 3))
        assertEquals(1 to 0, ReaderLogic.locate(c, 4)); assertEquals(1 to 2, ReaderLogic.locate(c, 6))
        assertEquals(1 to 2, ReaderLogic.locate(c, 99)); assertEquals(0 to 0, ReaderLogic.locate(c, -5))
        for (f in 0..6) { val (sg, pg) = ReaderLogic.locate(c, f); assertEquals(f, ReaderLogic.flatIndex(c, sg, pg)) }
    }
    @Test fun continuousLoadAndFinish() {
        val c = listOf(3, 2) // 7 celdas: 0..6
        assertFalse(ReaderLogic.shouldLoadNext(c, 1)); assertTrue(ReaderLogic.shouldLoadNext(c, 3)); assertTrue(ReaderLogic.shouldLoadNext(c, 6))
        assertFalse(ReaderLogic.shouldLoadNext(emptyList(), 0))
        assertEquals(emptyList<Int>(), ReaderLogic.finished(c, 2)); assertEquals(listOf(0), ReaderLogic.finished(c, 3))
        assertEquals(listOf(0), ReaderLogic.finished(c, 5)); assertEquals(listOf(0, 1), ReaderLogic.finished(c, 6))
    }
}
