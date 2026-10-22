package com.manhwatracker.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Búsqueda pública de AniList. No necesita cuenta ni configuración. */
object AniList {

    private const val QUERY =
        "query(\$s: String) { Media(search: \$s, type: MANGA) { coverImage { large } } }"

    suspend fun searchCover(title: String): String? = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject()
                .put("query", QUERY)
                .put("variables", JSONObject().put("s", title))
                .toString()

            val conn = (URL("https://graphql.anilist.co").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10_000
                readTimeout = 10_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
            }
            conn.outputStream.use { it.write(body.toByteArray()) }

            if (conn.responseCode != 200) {
                conn.disconnect()
                return@withContext null
            }

            val text = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()

            JSONObject(text)
                .optJSONObject("data")
                ?.optJSONObject("Media")
                ?.optJSONObject("coverImage")
                ?.optString("large")
                ?.takeIf { it.isNotBlank() && it != "null" }
        } catch (e: Exception) {
            null
        }
    }
}
