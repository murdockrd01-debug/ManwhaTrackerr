package com.manhwatracker.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourcesV2Test {
    @Test fun stub() {
        assertTrue(SourcesV2.isStub(emptyList()))
        assertTrue(SourcesV2.isStub(listOf(RepoExt(name = "Outdated App", pkg = "x.y", apk = "a.apk"))))
        assertFalse(SourcesV2.isStub(listOf(RepoExt(name = "Tachiyomi: Foo", pkg = "x.y", apk = "a.apk"))))
    }

    @Test fun jsonLoose() {
        val t = """{"extensions":[{"packageName":"eu.a.b","apkName":"a.apk","name":"Foo","lang":"en","versionCode":7,"versionName":"1.4.7","isNsfw":true,
            "sources":[{"name":"Foo","lang":"en","id":12,"baseUrl":"https://x.com"}]},{"nope":1}]}"""
        val l = SourcesV2.parseIndexJson(t)
        assertEquals(1, l.size)
        assertEquals("eu.a.b", l[0].pkg); assertEquals(7L, l[0].code); assertEquals(1, l[0].nsfw); assertEquals(12L, l[0].sources[0].id)
        assertEquals(3L, SourcesV2.parseIndexJson("""[{"pkg":"p","apk":"a.apk","version":"1.4.3"}]""")[0].code)
    }

    @Test fun roundTrip() {
        val l = listOf(RepoExt("A", "p.a", "a.apk", "en", 5, "1.4.5", 1, listOf(RepoSource("A", "en", 9, "u"))))
        val back = Sources.parseIndex(SourcesV2.encode(l))
        assertEquals(1, back.size); assertEquals(5L, back[0].code); assertEquals(1, back[0].nsfw); assertEquals(9L, back[0].sources[0].id)
    }

    @Test fun wireDump() {
        // campo 1 = mensaje { campo 1 = "hi", campo 2 = varint 5 }, repetido dos veces
        val inner = byteArrayOf(0x0a, 2, 'h'.code.toByte(), 'i'.code.toByte(), 0x10, 5)
        val msg = byteArrayOf(0x0a, inner.size.toByte()) + inner
        val d = SourcesV2.wireDump(msg + msg)
        assertTrue(d, d.contains("f1 msg (x2)"))
        assertTrue(d, d.contains("f1 str=\"hi\""))
        assertTrue(d, d.contains("f2 varint=5"))
    }
}
