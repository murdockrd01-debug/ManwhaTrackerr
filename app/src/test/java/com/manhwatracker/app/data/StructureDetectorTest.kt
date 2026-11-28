package com.manhwatracker.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StructureDetectorTest {
    private fun ids(s: String) = StructureDetector.detect(s).map { it.id }

    @Test fun everyDetectedIdExists() {
        val phrases = listOf("I went home.", "She has been waiting.", "If I had known, I would have helped.", "Give up now!", "Who are you?")
        for (p in phrases) for (m in StructureDetector.detect(p)) assertTrue(m.id, Structures.byId(m.id) != null)
    }

    @Test fun pastSimple() {
        assertTrue("past_simple" in ids("I went home early."))
        assertTrue("past_simple" in ids("She walked to school."))
        assertTrue("past_simple" in ids("Did you see him?"))
    }

    @Test fun presentSimpleAndContinuous() {
        assertTrue("present_simple" in ids("She reads manhwa every night."))
        assertTrue("present_continuous" in ids("He is fighting the boss."))
        assertFalse("present_simple" in ids("He is fighting the boss."))
        assertTrue("present_continuous" in ids("I'm reading chapter 40."))
    }

    @Test fun perfectTenses() {
        assertTrue("present_perfect" in ids("I have seen this before."))
        assertTrue("present_perfect" in ids("She's never eaten sushi."))
        assertTrue("present_perfect_continuous" in ids("We have been waiting for an hour."))
        assertTrue("past_perfect" in ids("When I arrived, he had already left."))
        assertTrue("past_continuous" in ids("They were fighting all night."))
    }

    @Test fun conditionals() {
        assertTrue("first_conditional" in ids("If it rains, we will stay home."))
        assertTrue("second_conditional" in ids("If I were rich, I would buy a castle."))
        assertTrue("third_conditional" in ids("If I had studied, I would have passed."))
        assertFalse("past_perfect" in ids("If I had studied, I would have passed."))
    }

    @Test fun modalsAndFutures() {
        assertTrue("will_going_to" in ids("I will find him."))
        assertTrue("will_going_to" in ids("I'm going to find him."))
        assertTrue("can_could" in ids("I can't open the door."))
        assertTrue("should_must" in ids("You should rest."))
        assertTrue("should_must" in ids("I have to go."))
        assertTrue("used_to" in ids("I used to play every day."))
        assertTrue("wish" in ids("I wish I could fly."))
    }

    @Test fun passiveAndComparatives() {
        assertTrue("passive" in ids("The door was opened by the guard."))
        assertFalse("passive" in ids("I am tired."))
        assertTrue("comparatives" in ids("He is stronger than me."))
        assertTrue("comparatives" in ids("She is the smartest in class."))
        assertTrue("comparatives" in ids("It is as big as a house."))
    }

    @Test fun otherPatterns() {
        assertTrue("phrasal_verbs" in ids("Don't give up!"))
        assertTrue("phrasal_verbs" in ids("He picked it up."))
        assertTrue("informal" in ids("I'm gonna find him."))
        assertTrue("questions" in ids("Where did you go?"))
        assertTrue("relative" in ids("The man who saved me is gone."))
        assertTrue("gerund_infinitive" in ids("I enjoy reading manhwa."))
        assertTrue("gerund_infinitive" in ids("She decided to leave."))
    }

    @Test fun emptyAndNoise() {
        assertTrue(StructureDetector.detect("").isEmpty())
        assertTrue(StructureDetector.detect("!!! 123").isEmpty())
        assertEquals(listOf("i", "have", "seen"), StructureDetector.tokens("I've seen"))
    }
}
