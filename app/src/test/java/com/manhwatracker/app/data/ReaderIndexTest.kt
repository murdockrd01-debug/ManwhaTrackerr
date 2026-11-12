package com.manhwatracker.app.data

import org.junit.Assert.*
import org.junit.Test

class ReaderIndexTest {
    private fun e(name: String, vararg ch: Double) = ReaderIndex.Entry(name, listOf("root", "src", name), ch.toList(), ch.size)

    @Test fun unreadAndNext() {
        val x = e("A", 1.0, 2.0, 3.0, 4.5)
        assertEquals(2, ReaderIndex.unread(x, 3.0)); assertEquals(4.5, ReaderIndex.next(x, 3.0)!!, 0.0)
        assertEquals(0, ReaderIndex.unread(x, 9.0)); assertNull(ReaderIndex.next(x, 9.0))
        assertEquals(4, ReaderIndex.unread(x, 0.0))
    }
    @Test fun roundTrip() {
        val l = listOf(e("Solo Leveling", 1.0, 2.5), e("Vacío"), e("Raro\u001FNombre", 7.0))
        val back = ReaderIndex.decode(ReaderIndex.encode(l))
        assertEquals(3, back.size)
        assertEquals(listOf(1.0, 2.5), back[0].chapters); assertEquals(listOf("root", "src", "Solo Leveling"), back[0].path)
        assertTrue(back[1].chapters.isEmpty()); assertEquals("Raro Nombre", back[2].name)
        assertTrue(ReaderIndex.decode(null).isEmpty()); assertTrue(ReaderIndex.decode("basura").isEmpty())
    }
    @Test fun entryForPrefersExact() {
        val all = listOf(e("The Beginning After The End"), e("The Beginning After The End: Side Story"))
        assertEquals("The Beginning After The End: Side Story", ReaderIndex.entryFor(all, "The Beginning After The End: Side Story")!!.name)
        assertEquals("The Beginning After The End", ReaderIndex.entryFor(all, "The Beginning After The End")!!.name)
        assertNull(ReaderIndex.entryFor(all, "Otra cosa"))
    }
}
