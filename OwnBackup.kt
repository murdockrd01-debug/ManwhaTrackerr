package com.manhwatracker.app.data

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** El archivo no es una copia válida de esta app. El mensaje se le muestra tal cual al usuario. */
class BackupFormatException(message: String) : Exception(message)

class ExportResult(val manhwas: Int, val words: Int)

class RestoreResult(
    val manhwasAdded: Int,
    val manhwasUpdated: Int,
    val wordsAdded: Int,
    val wordsUpdated: Int
)

/**
 * Copia de seguridad propia en JSON (manhwas + vocabulario).
 * Es independiente del backup de Komikku: sirve para cambiar de teléfono sin perder nada.
 *
 * Al restaurar se MEZCLA con lo que ya hay: nunca se borra nada.
 */
object OwnBackup {

    private const val APP = "ManhwaTracker"
    private const val VERSION = 1

    // ---------------------------------------------------------------- Exportar

    suspend fun write(context: Context, uri: Uri): ExportResult {
        val db = AppDatabase.get(context)
        val manhwas = db.dao().all()
        val words = db.words().all()

        val root = JSONObject()
            .put("app", APP)
            .put("version", VERSION)
            .put("exportedAt", System.currentTimeMillis())
            .put("manhwas", JSONArray().also { arr -> manhwas.forEach { arr.put(manhwaToJson(it)) } })
            .put("words", JSONArray().also { arr -> words.forEach { arr.put(wordToJson(it)) } })

        val out = context.contentResolver.openOutputStream(uri, "wt")
            ?: throw BackupFormatException("No se pudo abrir el archivo para guardar")
        out.use { it.write(root.toString(2).toByteArray(Charsets.UTF_8)) }
        return ExportResult(manhwas.size, words.size)
    }

    private fun manhwaToJson(m: Manhwa) = JSONObject()
        .put("title", m.title)
        .put("coverUrl", m.coverUrl)
        .put("lastChapter", m.lastChapter)
        .put("totalChapters", m.totalChapters)
        .put("status", m.status)
        .put("updatedAt", m.updatedAt)

    private fun wordToJson(w: Word) = JSONObject()
        .put("text", w.text)
        .put("translation", w.translation)
        .put("definition", w.definition)
        .put("definitionEs", w.definitionEs)
        .put("example", w.example)
        .put("manhwaTitle", w.manhwaTitle)
        .put("chapter", w.chapter)
        .put("note", w.note)
        .put("learned", w.learned)
        .put("lookupDone", w.lookupDone)
        .put("createdAt", w.createdAt)
        .put("box", w.box)
        .put("dueAt", w.dueAt)

    // ---------------------------------------------------------------- Restaurar

    suspend fun restore(context: Context, uri: Uri): RestoreResult {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw BackupFormatException("No se pudo abrir el archivo")

        val root = try {
            JSONObject(bytes.toString(Charsets.UTF_8))
        } catch (e: JSONException) {
            throw BackupFormatException("Ese archivo no es una copia de Manhwa Tracker")
        }
        if (root.optString("app") != APP) {
            throw BackupFormatException("Ese archivo no es una copia de Manhwa Tracker")
        }
        if (root.optInt("version", 0) !in 1..VERSION) {
            throw BackupFormatException("Esa copia es de una versión más nueva de la app. Actualiza la app e inténtalo otra vez")
        }

        // Primero se lee y valida todo; solo después se toca la base de datos.
        val manhwas = root.optJSONArray("manhwas").objects().mapNotNull { parseManhwa(it) }
        val words = root.optJSONArray("words").objects().mapNotNull { parseWord(it) }

        val db = AppDatabase.get(context)
        return db.withTransaction {
            var mAdded = 0
            var mUpdated = 0
            var wAdded = 0
            var wUpdated = 0

            for (b in manhwas) {
                val existing = db.dao().findByKey(b.titleKey)
                if (existing == null) {
                    db.dao().insert(b)
                    mAdded++
                } else if (b.updatedAt > existing.updatedAt) {
                    // Gana la copia más reciente de cada manhwa.
                    db.dao().update(
                        existing.copy(
                            lastChapter = b.lastChapter,
                            totalChapters = maxOf(existing.totalChapters, b.totalChapters),
                            status = b.status,
                            coverUrl = b.coverUrl ?: existing.coverUrl,
                            updatedAt = b.updatedAt
                        )
                    )
                    mUpdated++
                }
            }

            for (b in words) {
                val existing = db.words().findByText(b.text)
                if (existing == null) {
                    db.words().insert(b)
                    wAdded++
                } else {
                    val merged = existing.copy(
                        learned = existing.learned || b.learned,
                        translation = existing.translation ?: b.translation,
                        definition = existing.definition ?: b.definition,
                        definitionEs = existing.definitionEs ?: b.definitionEs,
                        example = existing.example ?: b.example,
                        manhwaTitle = existing.manhwaTitle ?: b.manhwaTitle,
                        chapter = existing.chapter ?: b.chapter,
                        note = existing.note ?: b.note,
                        lookupDone = existing.lookupDone || b.lookupDone,
                        box = maxOf(existing.box, b.box),
                        dueAt = if (b.box > existing.box) b.dueAt else existing.dueAt
                    )
                    if (merged != existing) {
                        db.words().update(merged)
                        wUpdated++
                    }
                }
            }

            RestoreResult(mAdded, mUpdated, wAdded, wUpdated)
        }
    }

    // ---------------------------------------------------------------- Lectura segura

    private fun JSONArray?.objects(): List<JSONObject> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { optJSONObject(it) }
    }

    /** Devuelve el texto o null si falta o es "null". */
    private fun JSONObject.str(key: String): String? =
        if (has(key) && !isNull(key)) getString(key).takeIf { it.isNotBlank() } else null

    private fun parseManhwa(o: JSONObject): Manhwa? {
        val title = o.str("title")?.trim() ?: return null
        val status = o.str("status")?.takeIf { it in Status.all } ?: Status.READING
        return Manhwa(
            title = title,
            titleKey = titleKeyOf(title),
            coverUrl = o.str("coverUrl"),
            lastChapter = o.optDouble("lastChapter", 0.0).takeIf { !it.isNaN() } ?: 0.0,
            totalChapters = o.optInt("totalChapters", 0),
            status = status,
            updatedAt = o.optLong("updatedAt", System.currentTimeMillis())
        )
    }

    private fun parseWord(o: JSONObject): Word? {
        val text = o.str("text")?.trim() ?: return null
        return Word(
            text = text,
            translation = o.str("translation"),
            definition = o.str("definition"),
            definitionEs = o.str("definitionEs"),
            example = o.str("example"),
            manhwaTitle = o.str("manhwaTitle"),
            chapter = o.str("chapter"),
            note = o.str("note"),
            learned = o.optBoolean("learned", false),
            lookupDone = o.optBoolean("lookupDone", false),
            createdAt = o.optLong("createdAt", System.currentTimeMillis()),
            box = o.optInt("box", 0).coerceIn(0, Srs.MAX_BOX),
            dueAt = o.optLong("dueAt", 0L)
        )
    }
}
