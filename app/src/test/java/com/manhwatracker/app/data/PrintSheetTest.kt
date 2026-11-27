package com.manhwatracker.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrintSheetTest {
    @Test fun escapesHtml() {
        val h = PrintSheet.html(emptyList(), listOf(PrintSheet.WordRow("<script>alert(1)</script>", "a & b", "say \"hi\"")), "hoy")
        assertFalse(h.contains("<script>"))
        assertTrue(h.contains("&lt;script&gt;"))
        assertTrue(h.contains("a &amp; b"))
        assertTrue(h.contains("say &quot;hi&quot;"))
    }

    @Test fun sectionsAppearOnlyWhenThereIsContent() {
        val onlyStructures = PrintSheet.html(Structures.all.take(2), emptyList(), "hoy")
        assertTrue(onlyStructures.contains("<h2>Estructuras</h2>"))
        assertFalse(onlyStructures.contains("<h2>Palabras</h2>"))
        assertTrue(onlyStructures.contains(Structures.all[0].title))
        val onlyWords = PrintSheet.html(emptyList(), listOf(PrintSheet.WordRow("tree", "árbol", null)), "hoy")
        assertFalse(onlyWords.contains("<h2>Estructuras</h2>"))
        assertTrue(onlyWords.contains("<h2>Palabras</h2>"))
        assertTrue(onlyWords.contains("árbol"))
    }
}
