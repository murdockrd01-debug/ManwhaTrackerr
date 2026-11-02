package com.manhwatracker.app.data
import org.junit.Assert.*
import org.junit.Test

class ChangelogTest {
    private val text = "## 4.1\n- Uno\n- Dos\n\n## 4.0\n- Tres\n"
    @Test fun section() {
        assertEquals("- Uno\n- Dos", Changelog.section(text, "4.1"))
        assertEquals("- Tres", Changelog.section(text, "4.0"))
        assertNull(Changelog.section(text, "9.9"))
    }
    @Test fun bodyFromJson() {
        val json = "{\"tag_name\":\"v4.1\",\"body\":\"- Uno\\n- Dos \\\"x\\\" \\u00e1\",\"draft\":false}"
        assertEquals("- Uno\n- Dos \"x\" \u00e1", Update.bodyFromJson(json))
        assertNull(Update.bodyFromJson("{\"tag_name\":\"v1\"}"))
    }
}
