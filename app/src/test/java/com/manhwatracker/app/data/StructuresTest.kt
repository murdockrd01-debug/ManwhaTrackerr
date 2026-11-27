package com.manhwatracker.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StructuresTest {
    @Test fun contentIsComplete() {
        assertEquals(Structures.all.size, Structures.all.map { it.id }.toSet().size)
        for (s in Structures.all) {
            assertTrue(s.id, s.title.isNotBlank() && s.pattern.isNotBlank() && s.explanation.isNotBlank() && s.tip.isNotBlank())
            assertTrue(s.id, s.examples.isNotEmpty() && s.examples.all { it.first.isNotBlank() && it.second.isNotBlank() })
            assertTrue(s.id, s.quiz.first.contains(Structures.BLANK))
            assertTrue(s.id, s.quiz.second.isNotBlank())
        }
    }

    @Test fun progress() {
        assertEquals(0f, Structures.progress(emptySet()), 0.0001f)
        assertEquals(1f, Structures.progress(Structures.all.map { it.id }.toSet()), 0.0001f)
        assertEquals(0f, Structures.progress(setOf("no_existe")), 0.0001f)
        assertEquals(1f / Structures.all.size, Structures.progress(setOf(Structures.all[0].id, "no_existe")), 0.0001f)
    }

    @Test fun answerChecking() {
        assertTrue(Structures.isCorrect("goes", " Goes "))
        assertTrue(Structures.isCorrect("are  running", "are running"))
        assertTrue(Structures.isCorrect("can't", "can’t"))
        assertFalse(Structures.isCorrect("goes", "go"))
        assertFalse(Structures.isCorrect("goes", ""))
    }

    @Test fun byId() {
        assertEquals("Presente simple", Structures.byId("present_simple")?.title)
        assertEquals(null, Structures.byId("nope"))
    }
}
