package com.manhwatracker.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourcesV2Test {
    @Test fun stub() {
        assertTrue(SourcesV2.isStub(emptyList()))
        assertTrue(SourcesV2.isStub(listOf(RepoExt(name = "Outdated App", pkg = "x.y", apk = "a.apk"))))
        assertTrue(SourcesV2.isStub(listOf(RepoExt(name = "Tachiyomi: Foo", pkg = "x.a", apk = "a.apk", sources = listOf(RepoSource("Outdated app"))), RepoExt(name = "B", pkg = "x.b", apk = "b.apk"))))
        assertFalse(SourcesV2.isStub((1..30).map { RepoExt(name = "E$it", pkg = "x.$it", apk = "e$it.apk") }))
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

    private fun len(num: Int, body: ByteArray): ByteArray = varint(((num shl 3) or 2).toLong()) + varint(body.size.toLong()) + body
    private fun vi(num: Int, v: Long): ByteArray = varint((num shl 3).toLong()) + varint(v)
    private fun varint(v0: Long): ByteArray { var v = v0; val o = ArrayList<Byte>(); while (true) { val b = (v and 0x7f).toInt(); v = v ushr 7; if (v == 0L) { o.add(b.toByte()); break } else o.add((b or 0x80).toByte()) }; return o.toByteArray() }
    private fun s(num: Int, t: String) = len(num, t.toByteArray())

    @Test fun pb() {
        val src = len(8, vi(1, 6289731484943315811L) + s(2, "AHottie") + s(3, "all") + s(4, "https://ahottie.top"))
        val ext = s(1, "AHottie") + s(2, "eu.kanade.tachiyomi.extension.all.ahottie") +
            len(3, s(1, "https://github.com/k/e/releases/download/b0/tachiyomi-all.ahottie-v1.6.5.apk") + s(2, "https://cdn/x.png") + s(501, "https://x/y.jar")) +
            s(4, "1.6") + vi(5, 106005) + s(6, "1.6.5") + vi(7, 3) + src
        val ext2 = s(1, "Foo") + s(2, "eu.kanade.tachiyomi.extension.es.foo") + len(3, s(1, "https://h/foo.apk")) + vi(5, 106001) + s(6, "1.6.1")
        val root = s(1, "Keiyoushi") + s(3, "abc") + len(4, s(1, "https://k")) + len(101, len(1, ext) + len(1, ext2))
        val l = SourcesV2.parseIndexPb(root)
        assertEquals(2, l.size)
        assertEquals("eu.kanade.tachiyomi.extension.all.ahottie", l[0].pkg)
        assertEquals("tachiyomi-all.ahottie-v1.6.5.apk", l[0].apk)
        assertEquals(106005L, l[0].code); assertEquals("1.6.5", l[0].version); assertEquals(3, l[0].nsfw)
        assertEquals("all", l[0].lang); assertEquals("https://cdn/x.png", Sources.iconUrl("b", l[0]))
        assertEquals("https://github.com/k/e/releases/download/b0/tachiyomi-all.ahottie-v1.6.5.apk", Sources.apkUrl("b", l[0]))
        assertEquals(6289731484943315811L, l[0].sources[0].id); assertEquals("https://ahottie.top", l[0].sources[0].baseUrl)
        assertEquals("es", l[1].lang); assertEquals(0, l[1].nsfw)
        assertEquals("b/icon/eu.kanade.tachiyomi.extension.es.foo.png", Sources.iconUrl("b", l[1]))
    }
}
