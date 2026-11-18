package com.manhwatracker.app.ext

import android.content.Context
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/**
 * Capítulos guardados en el teléfono para leer sin internet (en `filesDir/offline`, no se borran solos)
 * y copia de la ficha + lista de capítulos de cada manhwa para poder abrirla sin conexión.
 */
object OfflineStore {
    private fun sha(s: String) =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }.take(16)

    private fun root(ctx: Context) = File(ctx.filesDir, "offline")

    fun chapterDir(ctx: Context, chapterKey: String) = File(root(ctx), "ch/" + sha(chapterKey))

    /** Número de páginas si el capítulo está completo; 0 si no está bajado. */
    fun pageCount(ctx: Context, chapterKey: String): Int =
        File(chapterDir(ctx, chapterKey), "done").takeIf { it.exists() }?.readText()?.trim()?.toIntOrNull() ?: 0

    fun pageFileIfAny(ctx: Context, chapterKey: String, index: Int): File? =
        File(chapterDir(ctx, chapterKey), "%05d.img".format(index)).takeIf { it.exists() && it.length() > 0 }

    fun markDone(ctx: Context, chapterKey: String, pages: Int) {
        File(chapterDir(ctx, chapterKey), "done").writeText(pages.toString())
    }

    fun delete(ctx: Context, chapterKey: String) { chapterDir(ctx, chapterKey).deleteRecursively() }

    // ---- Copia de la ficha y los capítulos
    class Saved(val pkg: String, val srcId: Long, val manga: SManga, val chapters: List<SChapter>)

    private fun mangaFile(ctx: Context, srcId: Long, mangaUrl: String) = File(root(ctx), "manga/" + sha("$srcId|$mangaUrl") + ".json")

    fun saveManga(ctx: Context, pkg: String, srcId: Long, manga: SManga, chapters: List<SChapter>) {
        runCatching {
            val j = JSONObject()
                .put("pkg", pkg).put("src", srcId)
                .put("url", manga.url).put("title", manga.title).put("thumb", manga.thumbnail_url)
                .put("author", manga.author).put("desc", manga.description).put("genre", manga.genre).put("status", manga.status)
                .put("chapters", JSONArray().also { a ->
                    chapters.forEach { c ->
                        a.put(JSONObject().put("u", c.url).put("n", c.name).put("d", c.date_upload).put("c", c.chapter_number.toDouble()).put("s", c.scanlator))
                    }
                })
            val f = mangaFile(ctx, srcId, manga.url)
            f.parentFile?.mkdirs()
            f.writeText(j.toString())
        }
    }

    fun loadManga(ctx: Context, srcId: Long, mangaUrl: String): Saved? = runCatching { parse(mangaFile(ctx, srcId, mangaUrl)) }.getOrNull()

    private fun parse(f: File): Saved? {
        if (!f.exists()) return null
        val j = JSONObject(f.readText())
        fun s(k: String) = if (j.isNull(k)) null else j.getString(k)
        val m = SManga.create().apply {
            url = j.getString("url"); title = j.optString("title"); thumbnail_url = s("thumb")
            author = s("author"); description = s("desc"); genre = s("genre"); status = j.optInt("status")
        }
        val arr = j.getJSONArray("chapters")
        val chs = (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            SChapter.create().apply {
                url = o.getString("u"); name = o.optString("n"); date_upload = o.optLong("d")
                chapter_number = o.optDouble("c", -1.0).toFloat(); scanlator = if (o.isNull("s")) null else o.getString("s")
            }
        }
        return Saved(j.getString("pkg"), j.getLong("src"), m, chs)
    }

    fun coverFile(ctx: Context, srcId: Long, mangaUrl: String) = File(root(ctx), "manga/" + sha("$srcId|$mangaUrl") + ".cover")

    class Entry(val saved: Saved, val chapters: Int, val bytes: Long)

    private fun dirSize(d: File): Long = d.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    /** Los manhwas que tienen al menos un capítulo bajado, con cuántos capítulos y cuánto espacio ocupan. */
    fun downloadedManga(ctx: Context): List<Entry> {
        val files = File(root(ctx), "manga").listFiles { f -> f.name.endsWith(".json") }?.toList() ?: return emptyList()
        return files.mapNotNull { f ->
            val s = runCatching { parse(f) }.getOrNull() ?: return@mapNotNull null
            val have = s.chapters.filter { pageCount(ctx, key(s.srcId, s.manga.url, it.url)) > 0 }
            if (have.isEmpty()) null
            else Entry(s, have.size, have.sumOf { dirSize(chapterDir(ctx, key(s.srcId, s.manga.url, it.url))) })
        }.sortedBy { it.saved.manga.title.lowercase() }
    }

    /** Borra todos los capítulos bajados de un manhwa (la ficha guardada se queda). */
    fun deleteManga(ctx: Context, s: Saved) {
        s.chapters.forEach { delete(ctx, key(s.srcId, s.manga.url, it.url)) }
    }

    fun key(srcId: Long, mangaUrl: String, chapterUrl: String) = "$srcId|$mangaUrl|$chapterUrl"
}
