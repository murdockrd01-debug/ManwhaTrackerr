package com.manhwatracker.app.data
import org.junit.Assert.*
import org.junit.Test

import java.time.LocalDate
import java.time.ZoneOffset
class StatsTest {
    private val today = LocalDate.of(2026, 10, 28).toEpochDay() // miércoles
    @Test fun streakAlive() {
        val s = Stats.streak(listOf(today - 2, today - 1), today)
        assertEquals(2, s.current); assertFalse(s.reviewedToday)
    }
    @Test fun streakBroken() {
        val s = Stats.streak(listOf(today - 5, today - 4, today - 3), today)
        assertEquals(0, s.current); assertEquals(3, s.best)
    }
    @Test fun streakToday() { assertTrue(Stats.streak(listOf(today), today).reviewedToday) }
    @Test fun streakEmpty() { assertEquals(0, Stats.streak(emptyList(), today).best) }
    @Test fun weekStartMonday() { assertEquals(today - 2, Stats.weekStart(today)) }
    @Test fun weekly() {
        val b = Stats.weeklyCounts(listOf(today, today - 1, today - 8), today, 3)
        assertEquals(listOf(0, 1, 2), b.map { it.value })
    }
    @Test fun monthly() {
        val b = Stats.monthlySums(listOf(today to 5, today to -9, (today - 40) to 3), today, 3)
        assertEquals(3, b.size); assertEquals("oct", b.last().label); assertEquals(0, b.last().value)
    }
    @Test fun dayOf() { assertEquals(0L, Stats.dayOf(0L, ZoneOffset.UTC)) }
    @Test fun wordsPer() {
        val w = listOf(Word(text = "a", manhwaTitle = "The Boxer"), Word(text = "b", manhwaTitle = "the boxer!"), Word(text = "c", manhwaTitle = "Other"), Word(text = "d"))
        val r = Stats.wordsPerManhwa(w)
        assertEquals(2, r[0].value); assertEquals(2, r.size)
    }
}
