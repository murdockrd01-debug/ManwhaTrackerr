package com.manhwatracker.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class GrammarDrillTest {
    @Test fun checkAcceptsAlternatives() {
        assertTrue(GrammarDrill.check("were|was", "Was"))
        assertTrue(GrammarDrill.check("is raining|'s raining", "’s raining"))
        assertFalse(GrammarDrill.check("were|was", "is"))
        assertFalse(GrammarDrill.check("were|was", ""))
        assertEquals("were / was", GrammarDrill.shown("were|was"))
    }

    @Test fun fillQuestionsAreWellFormed() {
        val qs = GrammarDrill.build(500, GrammarDrill.Mode.FILL, Random(1))
        assertTrue(qs.size >= 60)
        for (q in qs) {
            assertTrue(q.prompt, q.prompt.contains(Structures.BLANK))
            assertTrue(q.prompt, Structures.byId(q.structureId) != null)
            assertTrue(q.prompt, q.answer.isNotBlank())
        }
        // todas las estructuras tienen al menos 2 ejercicios de completar
        for (s in Structures.all) assertTrue(s.id, qs.count { it.structureId == s.id } >= 2)
    }

    @Test fun identifyQuestionsHaveFourDistinctOptions() {
        val qs = GrammarDrill.build(500, GrammarDrill.Mode.IDENTIFY, Random(2))
        assertTrue(qs.isNotEmpty())
        for (q in qs) {
            assertEquals(q.prompt, 4, q.options.size)
            assertEquals(q.prompt, 4, q.options.toSet().size)
            assertTrue(q.prompt, q.answer in q.options)
            assertEquals(Structures.byId(q.structureId)!!.title, q.answer)
        }
    }

    @Test fun buildRespectsCountAndMode() {
        assertEquals(10, GrammarDrill.build(10, GrammarDrill.Mode.MIXED, Random(3)).size)
        val mixed = GrammarDrill.build(10, GrammarDrill.Mode.MIXED, Random(3))
        assertEquals(5, mixed.count { it.kind == GrammarDrill.Kind.FILL })
        assertTrue(GrammarDrill.build(6, GrammarDrill.Mode.FILL, Random(4)).all { it.kind == GrammarDrill.Kind.FILL })
        assertEquals(GrammarDrill.build(8, GrammarDrill.Mode.MIXED, Random(5)).map { it.prompt }, GrammarDrill.build(8, GrammarDrill.Mode.MIXED, Random(5)).map { it.prompt })
    }
}
