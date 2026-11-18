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
