package com.manhwatracker.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Guarda en el teléfono las últimas sugerencias de Descubrir, para verlas sin internet. */
object DiscoverDisk {
    private fun file(c: Context, key: String) =
        File(c.filesDir, "discover_" + key.replace(Regex("[^A-Za-z0-9]"), "_") + ".json")

    fun save(c: Context, key: String, items: List<AniList.Similar>) {
        runCatching {
            val arr = JSONArray()
            items.forEach {
                arr.put(JSONObject().put("id", it.id).put("title", it.title).put("cover", it.cover)
                    .put("score", it.score ?: JSONObject.NULL).put("genres", JSONArray(it.genres))
                    .put("description", it.description).put("because", JSONArray(it.because)))
            }
            file(c, key).writeText(arr.toString())
        }
    }

    fun load(c: Context, key: String): List<AniList.Similar>? = runCatching {
        val f = file(c, key)
        if (!f.exists()) return null
        val arr = JSONArray(f.readText())
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            fun strs(n: String) = o.optJSONArray(n)?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList()
            AniList.Similar(
                id = o.getLong("id"), title = o.getString("title"),
                cover = o.optString("cover").takeIf { it.isNotEmpty() && it != "null" },
                score = if (o.isNull("score")) null else o.getInt("score"),
                genres = strs("genres"),
                description = o.optString("description").takeIf { it.isNotEmpty() && it != "null" },
                because = strs("because")
            )
        }.takeIf { it.isNotEmpty() }
    }.getOrNull()
}
