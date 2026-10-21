package com.manhwatracker.app.data

import android.content.Context
import android.text.Html
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Preferencias pequeñas: último manhwa usado y si la barra rápida está activada. */
object Prefs {
    private fun p(c: Context) = c.getSharedPreferences("prefs", Context.MODE_PRIVATE)

    fun lastManhwa(c: Context): String? = p(c).getString("last_manhwa", null)
    fun lastChapter(c: Context): String? = p(c).getString("last_chapter", null)

    fun setLast(c: Context, manhwa: String?, chapter: String?) {
        p(c).edit().putString("last_manhwa", manhwa).putString("last_chapter", chapter).apply()
    }

    fun quickBar(c: Context): Boolean = p(c).getBoolean("quick_bar", false)
    fun setQuickBar(c: Context, on: Boolean) {
        p(c).edit().putBoolean("quick_bar", on).apply()
    }
}

private object Http {
    suspend fun get(url: String): String? = withContext(Dispatchers.IO) {
        try {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 8_000
                setRequestProperty("Accept", "application/json")
            }
            if (conn.responseCode != 200) {
                conn.disconnect()
                return@withContext null
            }
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            text
        } catch (e: Exception) {
            null
        }
    }
}

/** Traducción inglés -> español con MyMemory (gratis, sin cuenta). */
object Translator {
    suspend fun toSpanish(text: String): String? {
        val q = URLEncoder.encode(text, "UTF-8")
        val body = Http.get("https://api.mymemory.translated.net/get?q=$q&langpair=en%7Ces") ?: return null
        return try {
            val json = JSONObject(body)
            if (json.optInt("responseStatus", 200) != 200) return null
            val raw = json.optJSONObject("responseData")?.optString("translatedText").orEmpty()
            if (raw.isBlank()) null
            else Html.fromHtml(raw, Html.FROM_HTML_MODE_LEGACY).toString().trim()
        } catch (e: Exception) {
            null
        }
    }
}

/** Definición y ejemplo en inglés con dictionaryapi.dev (gratis, solo palabras sueltas). */
object Dictionary {
    suspend fun lookup(word: String): Pair<String, String?>? {
        val w = word.trim().lowercase().trim { !it.isLetter() }
        if (w.isEmpty()) return null
        val body = Http.get("https://api.dictionaryapi.dev/api/v2/entries/en/" + URLEncoder.encode(w, "UTF-8"))
            ?: return null
        return try {
            val meanings = JSONArray(body).optJSONObject(0)?.optJSONArray("meanings") ?: return null
            var definition: String? = null
            var example: String? = null
            outer@ for (i in 0 until meanings.length()) {
                val m = meanings.getJSONObject(i)
                val pos = m.optString("partOfSpeech")
                val defs = m.optJSONArray("definitions") ?: continue
                for (j in 0 until defs.length()) {
                    val d = defs.getJSONObject(j)
                    if (definition == null) {
                        val text = d.optString("definition")
                        if (text.isNotBlank()) {
                            definition = if (pos.isNotBlank()) "($pos) $text" else text
                        }
                    }
                    if (example == null) {
                        val e = d.optString("example")
                        if (e.isNotBlank()) example = e
                    }
                    if (definition != null && example != null) break@outer
                }
            }
            definition?.let { it to example }
        } catch (e: Exception) {
            null
        }
    }
}

data class SaveResult(val added: Boolean, val id: Long)

/** Lógica compartida por la app, la barra de notificaciones y el menú de seleccionar texto. */
object WordRepo {

    suspend fun save(
        context: Context,
        text: String,
        manhwa: String?,
        chapter: String?,
        note: String?
    ): SaveResult {
        val dao = AppDatabase.get(context).words()
        val clean = text.trim()
        if (clean.isEmpty()) return SaveResult(false, -1)

        val existing = dao.findByText(clean)
        if (existing != null) return SaveResult(false, existing.id)

        val id = dao.insert(
            Word(
                text = clean,
                manhwaTitle = manhwa?.trim()?.takeIf { it.isNotEmpty() },
                chapter = chapter?.trim()?.takeIf { it.isNotEmpty() },
                note = note?.trim()?.takeIf { it.isNotEmpty() }
            )
        )
        lookup(context, id)
        return SaveResult(true, id)
    }

    /** Busca traducción, definición y ejemplo. Devuelve true si consiguió la traducción. */
    suspend fun lookup(context: Context, id: Long): Boolean {
        val dao = AppDatabase.get(context).words()
        val word = dao.findById(id) ?: return false
        val clean = word.text.trim()

        val translation = Translator.toSpanish(clean)
        val isSingleWord = clean.split(Regex("\\s+")).size == 1
        val dictionary = if (isSingleWord) Dictionary.lookup(clean) else null

        // Se vuelve a leer por si la palabra cambió mientras esperábamos la red.
        val fresh = dao.findById(id) ?: return false
        dao.update(
            fresh.copy(
                translation = translation ?: fresh.translation,
                definition = dictionary?.first ?: fresh.definition,
                example = dictionary?.second ?: fresh.example,
                lookupDone = translation != null || fresh.lookupDone
            )
        )
        return translation != null
    }
}
