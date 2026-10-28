package com.manhwatracker.app.data
import org.junit.Assert.*
import org.junit.Test

class SrsTest {
    private val now = 1_000_000_000L
    private val d = 24 * 60 * 60_000L
    @Test fun knownGoesUp() { val w = Srs.next(Word(text = "a", box = 0), true, now); assertEquals(1, w.box); assertEquals(now + d, w.dueAt) }
    @Test fun failResets() { val w = Srs.next(Word(text = "a", box = 4), false, now); assertEquals(0, w.box); assertEquals(now + 600_000, w.dueAt) }
    @Test fun learnedAtTop() { val w = Srs.next(Word(text = "a", box = 5), true, now); assertTrue(w.learned); assertEquals(now, w.learnedAt) }
    @Test fun setLearnedKeepsDate() {
        assertEquals(5L, Srs.setLearned(Word(text = "a", learned = true, learnedAt = 5), true, now).learnedAt)
        assertEquals(0L, Srs.setLearned(Word(text = "a", learned = true, learnedAt = 5), false, now).learnedAt)
    }
    @Test fun queueFilters() {
        val ws = listOf(
            Word(id = 1, text = "due", dueAt = 0, translation = "x", manhwaTitle = "The Boxer"),
            Word(id = 2, text = "later", dueAt = now + 1, translation = "x"),
            Word(id = 3, text = "done", learned = true),
            Word(id = 4, text = "notr", dueAt = 0)
        )
        assertEquals(listOf(1L, 4L), Srs.queue(ws, now, false, null, false).map { it.id })
        assertEquals(listOf(1L), Srs.queue(ws, now, false, null, true).map { it.id })
        assertEquals(listOf(1L), Srs.queue(ws, now, true, titleKeyOf("the boxer"), false).map { it.id })
        assertEquals(3, Srs.queue(ws, now, true, null, false).size)
    }
    @Test fun queueMax() { assertEquals(Srs.SESSION_MAX, Srs.queue(List(50) { Word(id = it.toLong(), text = "w$it") }, now, true, null, false).size) }
}
