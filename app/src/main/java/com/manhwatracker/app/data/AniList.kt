package com.manhwatracker.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Búsqueda pública de AniList. No necesita cuenta ni configuración. */
object AniList {

    /** Lo que AniList sabe de un manhwa. [id] = 0 significa que no lo encontró. */
    class Meta(val id: Long, val genres: List<String>, val cover: String?)

    /** Un manhwa sugerido (parecido a otro, o de las listas de tendencias). [because] = títulos tuyos que lo recomiendan. */
    data class Similar(
        val id: Long,
        val title: String,
        val cover: String?,
        val score: Int?,
        val genres: List<String> = emptyList(),
        val description: String? = null,
        val because: List<String> = emptyList()
    )

    private fun cleanDescription(raw: String?): String? =
        raw?.takeIf { it.isNotBlank() && it != "null" }
            ?.replace(Regex("<br\\s*/?>"), "\n")
            ?.replace(Regex("<[^>]+>"), "")
            ?.replace(Regex("\\n{3,}"), "\n\n")
            ?.trim()
            ?.take(600)
            ?.ifEmpty { null }

    /** Convierte un nodo "Media" de AniList en un [Similar]. Devuelve null si no tiene título o es para adultos. */
    private fun parseMedia(m: JSONObject): Similar? {
        if (m.optBoolean("isAdult", false)) return null
        val titles = m.optJSONObject("title")
        val title = titles?.optString("english")?.takeIf { it.isNotBlank() && it != "null" }
            ?: titles?.optString("romaji")?.takeIf { it.isNotBlank() && it != "null" }
            ?: return null
        val genres = m.optJSONArray("genres")?.let { arr -> (0 until arr.length()).map { arr.getString(it) } }.orEmpty()
        return Similar(
            id = m.optLong("id"),
            title = title,
            cover = m.optJSONObject("coverImage")?.optString("large")?.takeIf { it.isNotBlank() && it != "null" },
            score = if (m.isNull("averageScore")) null else m.optInt("averageScore"),
            genres = genres,
            description = cleanDescription(m.optString("description"))
        )
    }

    private class Reply(val code: Int, val json: JSONObject?)

    private suspend fun post(query: String, variables: JSONObject): Reply? = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().put("query", query).put("variables", variables).toString()
            val conn = (URL("https://graphql.anilist.co").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10_000
                readTimeout = 10_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
            }
            conn.outputStream.use { it.write(body.toByteArray()) }
            val code = conn.responseCode
            val text = if (code == 200) conn.inputStream.bufferedReader().use { it.readText() } else null
            conn.disconnect()
            Reply(code, text?.let { JSONObject(it) })
        } catch (e: Exception) {
            null
        }
    }

    private const val META_QUERY =
        "query(\$s: String) { Media(search: \$s, type: MANGA) { id genres coverImage { large } } }"

    /**
     * Busca el manhwa por título. Devuelve null si no hubo conexión (para reintentar luego)
     * y un [Meta] con id 0 si AniList no lo conoce.
     */
    suspend fun searchMeta(title: String): Meta? {
        val reply = post(META_QUERY, JSONObject().put("s", title)) ?: return null
        if (reply.code == 404) return Meta(0, emptyList(), null)
        val media = reply.json?.optJSONObject("data")?.optJSONObject("Media") ?: return if (reply.code == 200) Meta(0, emptyList(), null) else null
        val genres = media.optJSONArray("genres")?.let { arr -> (0 until arr.length()).map { arr.getString(it) } }.orEmpty()
        val cover = media.optJSONObject("coverImage")?.optString("large")?.takeIf { it.isNotBlank() && it != "null" }
        return Meta(media.optLong("id", 0), genres, cover)
    }

    suspend fun searchCover(title: String): String? = searchMeta(title)?.cover

    private const val SIMILAR_QUERY =
        "query(\$id: Int) { Media(id: \$id) { recommendations(sort: RATING_DESC, perPage: 15) { nodes { " +
            "mediaRecommendation { id isAdult title { romaji english } coverImage { large } averageScore genres " +
            "description(asHtml: false) } } } } }"

    /** Manhwas que otros lectores recomiendan junto a este. null = sin conexión. */
    suspend fun similar(anilistId: Long): List<Similar>? {
        val reply = post(SIMILAR_QUERY, JSONObject().put("id", anilistId)) ?: return null
        if (reply.code != 200) return if (reply.code == 404) emptyList() else null
        val nodes = reply.json?.optJSONObject("data")?.optJSONObject("Media")
            ?.optJSONObject("recommendations")?.optJSONArray("nodes") ?: return emptyList()
        val out = mutableListOf<Similar>()
        for (i in 0 until nodes.length()) {
            val m = nodes.optJSONObject(i)?.optJSONObject("mediaRecommendation") ?: continue
            parseMedia(m)?.let { out += it }
        }
        return out
    }

    private const val LISTING_QUERY =
        "query(\$p: Int, \$sort: [MediaSort], \$g: String) { Page(page: \$p, perPage: 30) { " +
            "media(type: MANGA, countryOfOrigin: \"KR\", sort: \$sort, genre: \$g, isAdult: false) { " +
            "id isAdult title { romaji english } coverImage { large } averageScore genres description(asHtml: false) } } }"

    /** Lista de manhwa coreano ordenada por [sort] (TRENDING_DESC, POPULARITY_DESC, SCORE_DESC), con género opcional. null = sin conexión. */
    suspend fun listing(sort: String, genre: String?): List<Similar>? {
        val vars = JSONObject().put("p", 1).put("sort", org.json.JSONArray().put(sort))
        if (genre != null) vars.put("g", genre)
        val reply = post(LISTING_QUERY, vars) ?: return null
        if (reply.code != 200) return null
        val media = reply.json?.optJSONObject("data")?.optJSONObject("Page")?.optJSONArray("media") ?: return emptyList()
        val out = mutableListOf<Similar>()
        for (i in 0 until media.length()) {
            media.optJSONObject(i)?.let { parseMedia(it) }?.let { out += it }
        }
        return out
    }
}

/** Nombres de los géneros de AniList en español (los que no estén aquí se muestran como vienen). */
object Genres {
    private val es = mapOf(
        "Action" to "Acción", "Adventure" to "Aventura", "Comedy" to "Comedia", "Drama" to "Drama",
        "Ecchi" to "Ecchi", "Fantasy" to "Fantasía", "Horror" to "Terror", "Mahou Shoujo" to "Chica mágica",
        "Mecha" to "Mecha", "Music" to "Música", "Mystery" to "Misterio", "Psychological" to "Psicológico",
        "Romance" to "Romance", "Sci-Fi" to "Ciencia ficción", "Slice of Life" to "Recuentos de la vida",
        "Sports" to "Deportes", "Supernatural" to "Sobrenatural", "Thriller" to "Suspenso", "Hentai" to "Adulto"
    )

    fun label(g: String) = es[g] ?: g

    fun parse(stored: String?): List<String> = stored?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
}
