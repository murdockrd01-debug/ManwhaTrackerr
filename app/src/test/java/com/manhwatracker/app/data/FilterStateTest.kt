package com.manhwatracker.app.data

import eu.kanade.tachiyomi.source.model.Filter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterStateTest {
    private class Sel(n: String) : Filter.Select<String>(n, arrayOf("a", "b", "c"))
    private class Txt(n: String) : Filter.Text(n)
    private class Chk(n: String) : Filter.CheckBox(n)
    private class Tri(n: String) : Filter.TriState(n)
    private class Srt : Filter.Sort("Orden", arrayOf("x", "y"))
    private class Grp(n: String, l: List<Filter<*>>) : Filter.Group<Filter<*>>(n, l)
    private class Hdr : Filter.Header("Títulos")

    private fun make(): List<Filter<*>> = listOf(Hdr(), Sel("Estado"), Txt("Autor"), Grp("Géneros", listOf(Tri("Acción"), Chk("Magia"))), Srt())

    @Test fun roundTrip() {
        val a = make()
        (a[1] as Sel).state = 2
        (a[2] as Txt).state = "Chu"
        val g = (a[3] as Grp).state
        (g[0] as Tri).state = Filter.TriState.STATE_EXCLUDE
        (g[1] as Chk).state = true
        (a[4] as Srt).state = Filter.Sort.Selection(1, true)
        val saved = FilterState.encode(a)

        val b = make()
        assertTrue(FilterState.apply(b, saved))
        assertEquals(2, (b[1] as Sel).state)
        assertEquals("Chu", (b[2] as Txt).state)
        val gb = (b[3] as Grp).state
        assertEquals(Filter.TriState.STATE_EXCLUDE, (gb[0] as Tri).state)
        assertEquals(true, (gb[1] as Chk).state)
        assertEquals(Filter.Sort.Selection(1, true), (b[4] as Srt).state)
    }

    @Test fun ignoresWhenSourceChanged() {
        val saved = FilterState.encode(make().also { (it[1] as Sel).state = 1 })
        val other = listOf<Filter<*>>(Sel("Otro"))
        assertFalse(FilterState.apply(other, saved))
        assertEquals(0, (other[0] as Sel).state)
        assertFalse(FilterState.apply(make(), null))
        assertFalse(FilterState.apply(make(), ""))
    }

    @Test fun brokenValueLeavesFiltersUntouched() {
        val a = make()
        val sig = FilterState.signature(a)
        val broken = sig + "\u0002" + "9\u0001x\u00010\u00010\u0001-"
        val b = make()
        assertFalse(FilterState.apply(b, broken))
        assertEquals(0, (b[1] as Sel).state)
    }

    @Test fun textWithSeparatorsIsCleaned() {
        val a = make(); (a[2] as Txt).state = "a\u0001b"
        val b = make(); assertTrue(FilterState.apply(b, FilterState.encode(a)))
        assertEquals("a b", (b[2] as Txt).state)
    }
}
