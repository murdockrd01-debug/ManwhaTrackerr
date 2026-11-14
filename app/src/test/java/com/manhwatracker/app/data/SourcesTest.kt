package com.manhwatracker.app.data

import org.junit.Assert.*
import org.junit.Test

class SourcesTest {
    private val sample = """[
      {"name":"Tachiyomi: MangaDex","pkg":"eu.kanade.tachiyomi.extension.all.mangadex","apk":"tachiyomi-all.mangadex-v1.4.190.apk","lang":"all","code":190,"version":"1.4.190","nsfw":0,
       "sources":[{"name":"MangaDex","lang":"en","id":2499283573021220255,"baseUrl":"https://mangadex.org"}],"extra":"x"},
      {"name":"Tachiyomi: Algo Adulto","pkg":"eu.kanade.tachiyomi.extension.es.adulto","apk":"a.apk","lang":"es","code":5,"version":"1.4.5","nsfw":1,"sources":[]},
      {"name":"Tachiyomi: Rota","pkg":"","apk":"x.apk","lang":"en","code":1,"version":"1","nsfw":0,"sources":[]},
      {"name":"Tachiyomi: Español Normal","pkg":"eu.kanade.tachiyomi.extension.es.normal","apk":"n.apk","lang":"es","code":7,"version":"1.4.7","nsfw":0,"sources":[{"name":"Lectortmo","lang":"es","id":1,"baseUrl":"x"}]}
    ]"""

    @Test fun parse() {
        val l = Sources.parseIndex(sample)
        assertEquals(3, l.size) // la que no tiene paquete se descarta
        assertEquals(2499283573021220255L, l[0].sources[0].id)
        assertEquals("MangaDex", Sources.displayName(l[0]))
        assertThrows(Exception::class.java) { Sources.parseIndex("no es json") }
        assertThrows(Exception::class.java) { Sources.parseIndex("{\"a\":1}") }
    }
    @Test fun normalize() {
        val base = "https://raw.githubusercontent.com/keiyoushi/extensions/repo"
        assertEquals(base, Sources.normalizeRepoUrl(base))
        assertEquals(base, Sources.normalizeRepoUrl("  $base/index.min.json "))
        assertEquals(base, Sources.normalizeRepoUrl("$base/"))
        assertEquals(base, Sources.normalizeRepoUrl("mihon://add-repo?url=https%3A%2F%2Fraw.githubusercontent.com%2Fkeiyoushi%2Fextensions%2Frepo%2Findex.min.json"))
        assertEquals(base, Sources.normalizeRepoUrl("tachiyomi://add-repo?url=$base/index.min.json"))
        assertNull(Sources.normalizeRepoUrl("hola")); assertNull(Sources.normalizeRepoUrl("ftp://x.com/y")); assertNull(Sources.normalizeRepoUrl("https://x"))
        assertEquals("$base/index.min.json", Sources.indexUrl(base))
    }
    @Test fun names() {
        assertEquals("keiyoushi/extensions", Sources.repoName("https://raw.githubusercontent.com/keiyoushi/extensions/repo"))
        assertEquals("ejemplo.com", Sources.repoName("https://ejemplo.com/repo"))
        assertEquals(listOf("a", "b"), Sources.addRepo(Sources.addRepo(listOf("a"), "b"), "B"))
    }
    @Test fun states() {
        val e = Sources.parseIndex(sample)[0]
        assertEquals(Sources.State.NOT_INSTALLED, Sources.state(e, null))
        assertEquals(Sources.State.UPDATE, Sources.state(e, 150))
        assertEquals(Sources.State.INSTALLED, Sources.state(e, 190)); assertEquals(Sources.State.INSTALLED, Sources.state(e, 999))
    }
    @Test fun mergeKeepsNewest() {
        val a = RepoExt(name = "X", pkg = "p", apk = "a", code = 3); val b = RepoExt(name = "X", pkg = "p", apk = "b", code = 9)
        assertEquals("b", Sources.merge(listOf(a, b)).single().apk)
    }
    @Test fun filterAndLanguages() {
        val l = Sources.parseIndex(sample)
        assertEquals(2, Sources.filter(l, "", null, false).size)            // sin adulto
        assertEquals(3, Sources.filter(l, "", null, true).size)
        assertEquals(listOf("Español Normal"), Sources.filter(l, "", "es", false).map { Sources.displayName(it) })
        assertEquals(1, Sources.filter(l, "lectortmo", null, false).size)   // por nombre de la fuente
        assertEquals(1, Sources.filter(l, "mangadex", null, false).size)
        assertEquals("es", Sources.languages(l).first())                     // 2 en español, 1 en "all"
        assertEquals("Varios idiomas", Sources.langLabel("all")); assertTrue(Sources.langLabel("en").startsWith("Ingl"))
    }
}
