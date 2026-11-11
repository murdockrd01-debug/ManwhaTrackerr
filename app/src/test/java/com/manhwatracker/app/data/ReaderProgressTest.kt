package com.manhwatracker.app.data

import org.junit.Assert.*
import org.junit.Test

class ReaderProgressTest {
    private fun m(title: String, ch: Double = 10.0, st: String = Status.READING) =
        Manhwa(id = 1, title = title, titleKey = titleKeyOf(title), lastChapter = ch, status = st, updatedAt = 1)

    @Test fun numbers() {
        assertEquals(45.0, ReaderProgress.chapterNumber("Chapter 45")!!, 0.0)
        assertEquals(45.0, ReaderProgress.chapterNumber("Ch.45 - Título 2")!!, 0.0)
        assertEquals(12.5, ReaderProgress.chapterNumber("Capítulo 12.5")!!, 0.0)
        assertEquals(7.0, ReaderProgress.chapterNumber("Group_Chapter 7")!!, 0.0)
        assertEquals(30.0, ReaderProgress.chapterNumber("Chapter 30.cbz")!!, 0.0)
        assertEquals(8.0, ReaderProgress.chapterNumber("Episodio 8 (2)")!!, 0.0)
        assertEquals(15.0, ReaderProgress.chapterNumber("015")!!, 0.0)
        assertNull(ReaderProgress.chapterNumber("Prólogo"))
    }
    @Test fun raiseOnly() {
        assertNull(ReaderProgress.raise(m("A", 10.0), 10.0, 5))
        assertNull(ReaderProgress.raise(m("A", 10.0), 4.0, 5))
        val r = ReaderProgress.raise(m("A", 10.0, Status.PLAN), 11.0, 5)!!
        assertEquals(11.0, r.lastChapter, 0.0); assertEquals(Status.READING, r.status); assertEquals(5L, r.updatedAt)
        assertEquals(Status.COMPLETED, ReaderProgress.raise(m("A", 10.0, Status.COMPLETED), 11.0, 5)!!.status)
    }
    @Test fun match() {
        val all = listOf(m("Solo Leveling"), m("The Beginning After The End"), m("The Beginning After The End: Side Story"))
        assertEquals("Solo Leveling", ReaderProgress.match(all, "Solo Leveling")!!.title)
        assertEquals("Solo Leveling", ReaderProgress.match(all, "solo-leveling!")!!.title)
        assertNull(ReaderProgress.match(all, "Otra cosa"))
        // nombre recortado por Komikku: empieza igual que un solo título
        assertEquals("Solo Leveling Ragnarok", ReaderProgress.match(listOf(m("Solo Leveling Ragnarok")), "Solo Leveling Rag")!!.title)
        // ambiguo: dos títulos empiezan igual -> no adivina
        assertNull(ReaderProgress.match(all, "The Beginning After"))
    }

    @Test fun folderIsManhwa() {
        assertTrue(ReaderProgress.folderIsManhwa("Solo Leveling", "Solo Leveling"))
        assertTrue(ReaderProgress.folderIsManhwa("Solo Leveling", "solo-leveling!"))
        assertFalse(ReaderProgress.folderIsManhwa("Solo Leveling", "Tower of God"))
        // Komikku recorta nombres largos: se acepta si el nombre de carpeta es largo y el título empieza igual.
        assertTrue(ReaderProgress.folderIsManhwa("The Beginning After The End: Side Story", "The Beginning After The End"))
        assertFalse(ReaderProgress.folderIsManhwa("Solo Leveling", "Solo"))
    }

    @Test fun pickChapterNextAfterLastRead() {
        val names = listOf("Chapter 1", "Chapter 2", "Chapter 3", "Chapter 4")
        val none = listOf(false, false, false, false)
        assertEquals(2, ReaderProgress.pickChapter(names, none, 2.0))
        assertEquals(0, ReaderProgress.pickChapter(names, none, 0.0))
        assertNull(ReaderProgress.pickChapter(names, none, 4.0))
        assertNull(ReaderProgress.pickChapter(emptyList(), emptyList(), 0.0))
    }

    @Test fun pickChapterPrefersUnfinished() {
        val names = listOf("Chapter 5", "Chapter 6", "Chapter 7")
        // El 6 quedó a medias: se retoma aunque el 7 también esté descargado.
        assertEquals(1, ReaderProgress.pickChapter(names, listOf(false, true, false), 5.0))
        // Un capítulo a medias que ya quedó atrás (progreso mayor) no cuenta.
        assertEquals(2, ReaderProgress.pickChapter(names, listOf(true, false, false), 6.0))
    }

    @Test fun pickChapterSkipsUnnumbered() {
        val names = listOf("Prólogo", "Chapter 1", "Chapter 2")
        assertEquals(1, ReaderProgress.pickChapter(names, listOf(false, false, false), 0.0))
    }
}
