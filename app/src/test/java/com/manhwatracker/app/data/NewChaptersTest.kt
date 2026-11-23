package com.manhwatracker.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class NewChaptersTest {
    @Test fun notifyRules() {
        assertEquals(false, NewChapters.shouldNotify(null, 50.0, 10.0))   // primera vez: solo punto de partida
        assertEquals(true, NewChapters.shouldNotify(49.0, 50.0, 10.0))    // salió uno nuevo
        assertEquals(false, NewChapters.shouldNotify(50.0, 50.0, 10.0))   // sin cambios
        assertEquals(false, NewChapters.shouldNotify(49.0, 50.0, 50.0))   // ya lo leíste
        assertEquals(false, NewChapters.shouldNotify(49.0, null, 10.0))   // la fuente no respondió
    }

    @Test fun unread() {
        assertEquals(2, NewChapters.unread(listOf(1.0, 2.0, 3.0, null, 4.0), 2.0))
        assertEquals(0, NewChapters.unread(emptyList(), 0.0))
    }
}

class NewChaptersUnreadTest {
    @org.junit.Test fun unreadFromLatest() {
        org.junit.Assert.assertEquals(0, NewChapters.unreadFromLatest(null, 5.0))
        org.junit.Assert.assertEquals(0, NewChapters.unreadFromLatest(5.0, 5.0))
        org.junit.Assert.assertEquals(0, NewChapters.unreadFromLatest(4.0, 5.0))
        org.junit.Assert.assertEquals(3, NewChapters.unreadFromLatest(8.0, 5.0))
        org.junit.Assert.assertEquals(1, NewChapters.unreadFromLatest(5.5, 5.0))
        org.junit.Assert.assertEquals(99, NewChapters.unreadFromLatest(900.0, 5.0))
    }
}
