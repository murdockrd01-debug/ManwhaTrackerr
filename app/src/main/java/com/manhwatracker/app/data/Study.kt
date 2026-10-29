package com.manhwatracker.app.data

import android.content.Context
import android.text.Html
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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

    // ---- Modo de la app (con o sin estudio de inglés)
    /** null = todavía no se eligió. */
    fun studyEnabled(c: Context): Boolean? =
        if (p(c).contains("study_enabled")) p(c).getBoolean("study_enabled", true) else null

    fun setStudyEnabled(c: Context, on: Boolean) {
        p(c).edit().putBoolean("study_enabled", on).apply()
    }

    /** true si la app ya guardó alguna preferencia (o sea, no es una instalación nueva). */
    fun hasAnySettings(c: Context): Boolean = p(c).all.isNotEmpty()

    // ---- Apariencia
    fun palette(c: Context): String = p(c).getString("palette", "VIOLET") ?: "VIOLET"
    fun themeMode(c: Context): String = p(c).getString("theme_mode", "SYSTEM") ?: "SYSTEM"
    fun amoled(c: Context): Boolean = p(c).getBoolean("amoled", false)
    fun customHue(c: Context): Float = p(c).getFloat("custom_hue", 200f)
    fun customAccent(c: Context): Float = p(c).getFloat("custom_accent", 320f)
    fun setLook(c: Context, palette: String, mode: String, amoled: Boolean, hue: Float, accent: Float) {
        p(c).edit().putString("palette", palette).putString("theme_mode", mode).putBoolean("amoled", amoled)
            .putFloat("custom_hue", hue).putFloat("custom_accent", accent).apply()
    }

    fun sort(c: Context): String = p(c).getString("sort", "RECENT") ?: "RECENT"
    fun setSort(c: Context, value: String) {
        p(c).edit().putString("sort", value).apply()
    }

    /** true = la lista de manhwas se ve como cuadrícula de portadas. */
    fun grid(c: Context): Boolean = p(c).getBoolean("grid", false)
    fun setGrid(c: Context, on: Boolean) {
        p(c).edit().putBoolean("grid", on).apply()
    }

    // ---- Sincronización con carpetas (ver AutoSync)
    /** Carpetas donde Komikku guarda sus copias (automáticas o manuales). Se guardan separadas por saltos de línea. */
    fun komikkuFolders(c: Context): List<String> {
        val joined = p(c).getString("komikku_folders", null)
        if (joined != null) return joined.split("\n").filter { it.isNotBlank() }
        // Versión 1.4 guardaba una sola carpeta.
        return listOfNotNull(p(c).getString("komikku_folder", null))
    }

    fun setKomikkuFolders(c: Context, uris: List<String>) {
        p(c).edit().putString("komikku_folders", uris.joinToString("\n")).remove("komikku_folder").apply()
    }

    fun lastKomikkuKey(c: Context): String? = p(c).getString("komikku_last_key", null)
    fun lastKomikkuName(c: Context): String? = p(c).getString("komikku_last_name", null)
    fun lastKomikkuAt(c: Context): Long = p(c).getLong("komikku_last_at", 0L)
    fun setLastKomikku(c: Context, key: String?, name: String?, at: Long) {
        p(c).edit().putString("komikku_last_key", key).putString("komikku_last_name", name)
            .putLong("komikku_last_at", at).apply()
    }

    fun backupFolder(c: Context): String? = p(c).getString("backup_folder", null)
    fun setBackupFolder(c: Context, uri: String?) {
        p(c).edit().putString("backup_folder", uri).apply()
    }

    fun lastBackupAt(c: Context): Long = p(c).getLong("backup_last_at", 0L)
    fun lastBackupName(c: Context): String? = p(c).getString("backup_last_name", null)
    fun setLastBackup(c: Context, at: Long, name: String?) {
        p(c).edit().putLong("backup_last_at", at).putString("backup_last_name", name).apply()
    }

    fun backupError(c: Context): String? = p(c).getString("backup_error", null)
    fun setBackupError(c: Context, message: String?) {
        p(c).edit().putString("backup_error", message).apply()
    }

    /** Paquete de la app lectora elegida; null = la primera que esté instalada. */
    fun reader(c: Context): String? = p(c).getString("reader", null)
    fun setReader(c: Context, pkg: String?) {
        p(c).edit().putString("reader", pkg).apply()
    }

    fun language(c: Context): String = p(c).getString("language", "auto") ?: "auto"
    fun setLanguage(c: Context, v: String) {
        p(c).edit().putString("language", v).apply()
    }

    // ---- Avisos de versión nueva
    fun updateCheck(c: Context): Boolean = p(c).getBoolean("update_check", true)
    fun setUpdateCheck(c: Context, on: Boolean) {
        p(c).edit().putBoolean("update_check", on).apply()
    }
    /** Versión nueva que el usuario ya vio, para no repetir el aviso. */
    fun seenUpdate(c: Context): String? = p(c).getString("seen_update", null)
    fun setSeenUpdate(c: Context, v: String) {
        p(c).edit().putString("seen_update", v).apply()
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
                // Wikimedia pide identificar la app.
                setRequestProperty("User-Agent", "ManhwaTracker/1.1 (personal English study app)")
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

private fun htmlToText(html: String): String =
    Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY).toString().replace(Regex("\\s+"), " ").trim()

/** Traducción inglés -> español con MyMemory (gratis, sin cuenta). */
object Translator {

    // MyMemory acepta unos 500 caracteres por consulta, así que se parte el texto largo.
    private fun chunks(text: String, max: Int = 400): List<String> {
        if (text.length <= max) return listOf(text)
        val out = mutableListOf<String>()
        var current = StringBuilder()
        for (word in text.split(Regex("\\s+"))) {
            if (current.isNotEmpty() && current.length + word.length + 1 > max) {
                out += current.toString()
                current = StringBuilder()
            }
            if (current.isNotEmpty()) current.append(' ')
            current.append(word)
        }
        if (current.isNotEmpty()) out += current.toString()
        return out
    }

    private suspend fun translateChunk(text: String): String? {
        val q = URLEncoder.encode(text, "UTF-8")
        val body = Http.get("https://api.mymemory.translated.net/get?q=$q&langpair=en%7Ces") ?: return null
        return try {
            val json = JSONObject(body)
            if (json.optInt("responseStatus", 200) != 200) return null
            val raw = json.optJSONObject("responseData")?.optString("translatedText").orEmpty()
            // Si se acabó el cupo diario gratis, MyMemory devuelve un aviso en vez de traducción.
            if (raw.isBlank() || raw.contains("MYMEMORY WARNING", ignoreCase = true)) null
            else htmlToText(raw)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun toSpanish(text: String): String? {
        val clean = text.trim()
        if (clean.isEmpty()) return null
        val parts = mutableListOf<String>()
        for (chunk in chunks(clean)) {
            parts += translateChunk(chunk) ?: return null
        }
        return parts.joinToString(" ")
    }
}

/** Lo que devuelve una búsqueda de diccionario: hasta 2 significados y un ejemplo. */
private class Entry(val definitions: List<String>, val example: String?, val source: String)

/** Definición lista para guardar: en inglés, en español y un ejemplo. */
class Defined(val english: String, val spanish: String?, val example: String?)

/** Diccionarios gratuitos y sin cuenta: dictionaryapi.dev y Wiktionary. */
object Dictionary {

    private val stopWords = setOf(
        "the", "and", "but", "for", "you", "your", "are", "was", "were", "with", "that", "this",
        "have", "has", "had", "not", "what", "when", "where", "who", "how", "why", "can", "will",
        "would", "could", "should", "from", "they", "them", "their", "there", "here", "our", "his",
        "her", "its", "been", "being", "just", "than", "then", "about", "into", "over", "some",
        "very", "also", "only", "even", "much", "more", "such", "does", "did", "get", "got"
    )

    private suspend fun fromDictionaryApi(word: String): Entry? {
        val body = Http.get(
            "https://api.dictionaryapi.dev/api/v2/entries/en/" + URLEncoder.encode(word, "UTF-8")
        ) ?: return null
        return try {
            val meanings = JSONArray(body).optJSONObject(0)?.optJSONArray("meanings") ?: return null
            val definitions = mutableListOf<String>()
            val examples = mutableListOf<String>()
            for (i in 0 until meanings.length()) {
                val m = meanings.getJSONObject(i)
                val pos = m.optString("partOfSpeech")
                val defs = m.optJSONArray("definitions") ?: continue
                for (j in 0 until defs.length()) {
                    val d = defs.getJSONObject(j)
                    val text = d.optString("definition")
                    if (examples.size < 3) {
                        val e = d.optString("example")
                        if (e.isNotBlank()) examples += e
                    }
                    if (j == 0 && text.isNotBlank() && definitions.size < 2) {
                        definitions += if (pos.isNotBlank()) "($pos) $text" else text
                    }
                }
            }
            if (definitions.isEmpty()) null else Entry(definitions, examples.joinToString("\n").ifEmpty { null }, "dictionaryapi.dev")
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun fromWiktionary(term: String): Entry? {
        for (candidate in listOf(term.lowercase(), term).distinct()) {
            val path = URLEncoder.encode(candidate, "UTF-8").replace("+", "%20")
            val body = Http.get("https://en.wiktionary.org/api/rest_v1/page/definition/$path") ?: continue
            try {
                val entries = JSONObject(body).optJSONArray("en") ?: continue
                val definitions = mutableListOf<String>()
                val examples = mutableListOf<String>()
                for (i in 0 until entries.length()) {
                    val e = entries.getJSONObject(i)
                    val pos = e.optString("partOfSpeech").lowercase()
                    val defs = e.optJSONArray("definitions") ?: continue
                    for (j in 0 until defs.length()) {
                        val d = defs.getJSONObject(j)
                        val text = htmlToText(d.optString("definition"))
                        if (text.isBlank()) continue
                        if (examples.size < 3) {
                            val parsed = d.optJSONArray("parsedExamples")?.optJSONObject(0)?.optString("example")
                            val plain = d.optJSONArray("examples")?.optString(0)
                            val raw = parsed?.takeIf { it.isNotBlank() } ?: plain
                            if (!raw.isNullOrBlank()) examples += htmlToText(raw)
                        }
                        if (definitions.size < 2) {
                            definitions += if (pos.isNotBlank()) "($pos) $text" else text
                        }
                        break // solo la primera definición de cada categoría gramatical
                    }
                }
                if (definitions.isNotEmpty()) return Entry(definitions, examples.joinToString("\n").ifEmpty { null }, "Wiktionary")
            } catch (e: Exception) {
                // pasa a la siguiente variante
            }
        }
        return null
    }

    /** WordNet vía Datamuse: tiene muchas palabras que los otros diccionarios no traen. */
    private suspend fun fromDatamuse(word: String): Entry? {
        val body = Http.get(
            "https://api.datamuse.com/words?sp=" + URLEncoder.encode(word, "UTF-8") + "&md=d&max=1"
        ) ?: return null
        return try {
            val first = JSONArray(body).optJSONObject(0) ?: return null
            if (!first.optString("word").equals(word, ignoreCase = true)) return null
            val defs = first.optJSONArray("defs") ?: return null
            val seen = mutableSetOf<String>()
            val out = mutableListOf<String>()
            for (i in 0 until defs.length()) {
                val parts = defs.optString(i).split("\t", limit = 2)
                if (parts.size < 2) continue
                val pos = when (parts[0]) {
                    "n" -> "noun"
                    "v" -> "verb"
                    "adj" -> "adjective"
                    "adv" -> "adverb"
                    else -> ""
                }
                if (out.size >= 2 || !seen.add(pos)) continue
                val text = parts[1].trim()
                out += if (pos.isNotBlank()) "($pos) $text" else text
            }
            if (out.isEmpty()) null else Entry(out, null, "WordNet")
        } catch (e: Exception) {
            null
        }
    }

    /** Busca el primer texto en español dentro de la respuesta de Tatoeba (que anida listas y objetos). */
    private fun findSpanish(node: Any?): String? = when (node) {
        is JSONObject ->
            if (node.optString("lang") == "spa" && node.optString("text").isNotBlank()) node.optString("text").trim()
            else node.keys().asSequence().firstNotNullOfOrNull { findSpanish(node.opt(it)) }
        is JSONArray -> (0 until node.length()).firstNotNullOfOrNull { findSpanish(node.opt(it)) }
        else -> null
    }

    /**
     * Oraciones reales de ejemplo (Tatoeba, gratis y sin cuenta), con su traducción al español si la tienen.
     * Cada línea sale como "inglés⇒español".
     */
    private suspend fun fromTatoeba(term: String): List<String> {
        val q = URLEncoder.encode(term.trim(), "UTF-8")
        val body = Http.get(
            "https://tatoeba.org/en/api_v0/search?from=eng&query=$q&orphans=no&unapproved=no" +
                "&sort=relevance&trans_filter=limit&trans_to=spa"
        ) ?: return emptyList()
        return try {
            val results = JSONObject(body).optJSONArray("results") ?: return emptyList()
            val out = mutableListOf<String>()
            for (i in 0 until results.length()) {
                val r = results.getJSONObject(i)
                val text = r.optString("text").trim()
                if (text.length !in 15..120 || !text.contains(term.trim(), ignoreCase = true)) continue
                val spanish = findSpanish(r.opt("translations"))
                out += if (spanish != null) "$text⇒$spanish" else text
                if (out.size >= 3) break
            }
            out
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Qué tan común es la palabra (veces por millón de palabras), con Datamuse.
     * Devuelve null si no hubo internet (para reintentar luego) y 0.0 si la palabra no aparece en sus datos.
     */
    suspend fun frequency(word: String): Double? {
        val w = word.lowercase().trim { !it.isLetter() }
        if (w.isEmpty() || w.contains(' ')) return null
        val body = Http.get(
            "https://api.datamuse.com/words?sp=" + URLEncoder.encode(w, "UTF-8") + "&md=f&max=1"
        ) ?: return null
        return try {
            val first = JSONArray(body).optJSONObject(0)
            if (first == null || !first.optString("word").equals(w, ignoreCase = true)) {
                0.0
            } else {
                val tags = first.optJSONArray("tags")
                val list = (0 until (tags?.length() ?: 0)).map { tags!!.optString(it) }
                Utility.parseFreqTag(list) ?: 0.0
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun cleanUrban(s: String) =
        s.replace("[", "").replace("]", "").replace(Regex("\\s+"), " ").trim()

    /** Jerga y expresiones informales (escrito por usuarios, puede ser crudo o impreciso). */
    private suspend fun fromUrban(term: String): Entry? {
        val body = Http.get(
            "https://api.urbandictionary.com/v0/define?term=" + URLEncoder.encode(term, "UTF-8")
        ) ?: return null
        return try {
            val list = JSONObject(body).optJSONArray("list") ?: return null
            var best: JSONObject? = null
            for (i in 0 until minOf(list.length(), 5)) {
                val o = list.getJSONObject(i)
                if (best == null || o.optInt("thumbs_up") > best.optInt("thumbs_up")) best = o
            }
            val b = best ?: return null
            val def = cleanUrban(b.optString("definition"))
            if (def.isBlank()) return null
            val ex = cleanUrban(b.optString("example")).takeIf { it.isNotBlank() }
            Entry(listOf("(slang) " + def.take(250)), ex?.take(200), "Urban Dictionary")
        } catch (e: Exception) {
            null
        }
    }

    /** Nombres propios, lugares y términos que no están en un diccionario. */
    private suspend fun fromWikipedia(term: String): Entry? {
        val title = URLEncoder.encode(
            term.trim().replaceFirstChar { it.uppercase() }.replace(' ', '_'), "UTF-8"
        )
        val body = Http.get("https://en.wikipedia.org/api/rest_v1/page/summary/$title") ?: return null
        return try {
            val json = JSONObject(body)
            if (json.optString("type") != "standard") return null
            val extract = json.optString("extract").trim()
            if (extract.isBlank()) return null
            val first = extract.split(Regex("(?<=[.!?])\\s+")).first().take(300)
            Entry(listOf("(Wikipedia) $first"), null, "Wikipedia")
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Prueba las fuentes en orden hasta que una responda.
     * deep = false se usa para las palabras sueltas de una frase (más rápido).
     */
    private suspend fun lookupEntry(term: String, deep: Boolean = true): Entry? {
        val isSingleWord = !term.contains(' ')
        val w = term.lowercase().trim { !it.isLetter() }

        if (isSingleWord && w.isNotEmpty()) fromDictionaryApi(w)?.let { return it }
        fromWiktionary(term)?.let { return it }
        if (isSingleWord && w.isNotEmpty()) fromDatamuse(w)?.let { return it }
        if (!deep) return null

        // Con mayúscula inicial parece un nombre propio: Wikipedia primero. Si no, jerga primero.
        val looksLikeName = term.firstOrNull()?.isUpperCase() == true
        if (looksLikeName) {
            fromWikipedia(term)?.let { return it }
            fromUrban(term)?.let { return it }
        } else {
            fromUrban(term)?.let { return it }
            fromWikipedia(term)?.let { return it }
        }
        return null
    }

    private fun stripPos(line: String) = line.replace(Regex("^\\([^)]*\\)\\s*"), "")

    /** Palabras importantes de una frase (sin las más comunes), máximo 4. */
    private fun keyWords(text: String): List<String> =
        text.split(Regex("\\s+"))
            .map { it.lowercase().trim { c -> !c.isLetter() } }
            .filter { it.length >= 4 && it !in stopWords && it.all { c -> c.isLetter() } }
            .distinct()
            .take(4)

    suspend fun define(text: String): Defined? {
        val clean = text.trim()
        if (clean.isEmpty()) return null

        // 1) El texto completo como entrada: palabras sueltas y expresiones (give up, piece of cake...)
        lookupEntry(clean)?.let { entry ->
            val spanish = entry.definitions
                .map { Translator.toSpanish(stripPos(it).take(400)) }
                .takeIf { list -> list.any { it != null } }
                ?.joinToString("\n") { it ?: "" }
            val english = entry.definitions.joinToString("\n") + "\n(fuente: ${entry.source})"
            // Si el diccionario trajo pocos ejemplos, se completan con oraciones reales de Tatoeba.
            val examples = entry.example?.split("\n")?.filter { it.isNotBlank() }.orEmpty().toMutableList()
            if (examples.size < 3 && clean.length <= 40) {
                for (extra in fromTatoeba(clean)) {
                    if (examples.size >= 4) break
                    if (examples.none { it.equals(extra, ignoreCase = true) }) examples += extra
                }
            }
            return Defined(english, spanish, examples.joinToString("\n").ifEmpty { null })
        }

        // 2) Frase larga: definimos sus palabras clave una por una.
        if (!clean.contains(' ')) return null
        val english = mutableListOf<String>()
        val spanish = mutableListOf<String>()
        for (word in keyWords(clean)) {
            val entry = lookupEntry(word, deep = false) ?: continue
            english += "• $word " + entry.definitions.first()
            Translator.toSpanish(word)?.let { spanish += "• $word: $it" }
            delay(200)
        }
        if (english.isEmpty()) return null
        return Defined(
            english.joinToString("\n"),
            spanish.takeIf { it.isNotEmpty() }?.joinToString("\n"),
            null
        )
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

    /** Busca traducción, definiciones y ejemplo. Devuelve true si consiguió la traducción. */
    suspend fun lookup(context: Context, id: Long): Boolean {
        val dao = AppDatabase.get(context).words()
        val word = dao.findById(id) ?: return false
        val clean = word.text.trim()

        val translation = Translator.toSpanish(clean)
        val defined = Dictionary.define(clean)
        val freq = Dictionary.frequency(clean)

        // Se vuelve a leer por si la palabra cambió mientras esperábamos la red.
        val fresh = dao.findById(id) ?: return false
        dao.update(
            fresh.copy(
                translation = translation ?: fresh.translation,
                definition = defined?.english ?: fresh.definition,
                definitionEs = defined?.spanish ?: fresh.definitionEs,
                example = defined?.example ?: fresh.example,
                freq = freq ?: fresh.freq,
                lookupDone = translation != null || fresh.lookupDone
            )
        )
        return translation != null
    }

    /** Mide la frecuencia de las palabras guardadas antes de esta versión (o sin internet en su momento). */
    suspend fun fillFrequencies(context: Context) {
        val dao = AppDatabase.get(context).words()
        for (w in dao.needFreq()) {
            val f = Dictionary.frequency(w.text) ?: continue
            val fresh = dao.findById(w.id) ?: continue
            dao.update(fresh.copy(freq = f))
            delay(250)
        }
    }
}
