package com.manhwatracker.app.data
import org.junit.Assert.*
import org.junit.Test

class UpdateTest {
    @Test fun newer() { assertTrue(Update.isNewer("v2.7", "2.6")) ; assertTrue(Update.isNewer("v2.10", "2.9")) }
    @Test fun same() { assertFalse(Update.isNewer("v2.6", "2.6")); assertFalse(Update.isNewer("2.6.0", "2.6")) }
    @Test fun older() { assertFalse(Update.isNewer("v2.5", "2.6")) }
    @Test fun junk() { assertFalse(Update.isNewer("latest", "2.6")); assertFalse(Update.isNewer("v3", "")) }
    @Test fun tag() { assertEquals("v2.7", Update.tagFromJson("{\"id\":1,\"tag_name\": \"v2.7\",\"x\":2}")); assertNull(Update.tagFromJson("{}")) }
}
