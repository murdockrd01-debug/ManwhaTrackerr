@file:OptIn(ExperimentalSerializationApi::class)

package com.manhwatracker.app.data

import android.content.Context
import android.net.Uri
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoNumber
import java.io.ByteArrayInputStream
import java.util.zip.GZIPInputStream
import kotlin.math.roundToInt

// Solo se declaran los campos que necesitamos. El resto del backup se ignora.
// Los números de campo son los del formato de Mihon, que Komikku comparte.

@Serializable
class Backup(
    @ProtoNumber(1) val manga: List<BackupManga> = emptyList()
)

@Serializable
class BackupManga(
    @ProtoNumber(1) val source: Long = 0,
    @ProtoNumber(2) val url: String = "",
    @ProtoNumber(3) val title: String = "",
    @ProtoNumber(9) val thumbnailUrl: String? = null,
    @ProtoNumber(16) val chapters: List<BackupChapter> = emptyList(),
    @ProtoNumber(104) val history: List<BackupHistory> = emptyList()
)

@Serializable
class BackupChapter(
    @ProtoNumber(1) val url: String = "",
    @ProtoNumber(4) val read: Boolean = false,
    @ProtoNumber(9) val chapterNumber: Float = -1f
)

@Serializable
class BackupHistory(
    @ProtoNumber(1) val url: String = "",
    @ProtoNumber(2) val lastRead: Long = 0
)

/** Lo que sacamos de cada manhwa del backup. */
data class ImportedManhwa(
    val title: String,
    val coverUrl: String?,
    val lastChapter: Double,
    val totalChapters: Int,
    val lastReadAt: Long
)

object BackupImporter {

    fun read(context: Context, uri: Uri): List<ImportedManhwa> {
        val raw = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("No se pudo abrir el archivo")

        // Los .tachibk van comprimidos con gzip (empiezan con 0x1f 0x8b).
        val bytes = if (raw.size > 2 && raw[0] == 0x1f.toByte() && raw[1] == 0x8b.toByte()) {
            GZIPInputStream(ByteArrayInputStream(raw)).use { it.readBytes() }
        } else {
            raw
        }

        val backup = ProtoBuf.decodeFromByteArray<Backup>(bytes)

        return backup.manga
            .filter { it.title.isNotBlank() }
            .map { m ->
                val readNumbers = m.chapters
                    .filter { it.read && it.chapterNumber >= 0f }
                    .map { it.chapterNumber.toDouble() }
                val lastChapter = ((readNumbers.maxOrNull() ?: 0.0) * 100).roundToInt() / 100.0

                val maxNumber = m.chapters
                    .filter { it.chapterNumber >= 0f }
                    .maxOfOrNull { it.chapterNumber }
                val total = maxNumber?.toInt()?.takeIf { it > 0 } ?: m.chapters.size

                ImportedManhwa(
                    title = m.title.trim(),
                    coverUrl = m.thumbnailUrl?.takeIf { it.startsWith("http") },
                    lastChapter = lastChapter,
                    totalChapters = total,
                    lastReadAt = m.history.maxOfOrNull { it.lastRead } ?: 0L
                )
            }
    }
}
