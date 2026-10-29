package com.manhwatracker.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

object Status {
    const val READING = "READING"
    const val PAUSED = "PAUSED"
    const val COMPLETED = "COMPLETED"
    const val PLAN = "PLAN"

    val all = listOf(READING, PAUSED, COMPLETED, PLAN)

    fun label(s: String) = when (s) {
        READING -> "Leyendo"
        PAUSED -> "Pausado"
        COMPLETED -> "Terminado"
        PLAN -> "Pendiente"
        else -> s
    }
}

@Entity(tableName = "manhwa", indices = [Index(value = ["titleKey"], unique = true)])
data class Manhwa(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val titleKey: String,
    val coverUrl: String? = null,
    val lastChapter: Double = 0.0,
    val totalChapters: Int = 0,
    val status: String = Status.READING,
    val updatedAt: Long = System.currentTimeMillis(),
    /** Tu nota de 1 a 10. 0 = todavía sin nota. */
    @ColumnInfo(defaultValue = "0") val rating: Int = 0,
    /** Tu comentario sobre el manhwa. */
    val review: String? = null,
    /** Géneros de AniList separados por coma. null = todavía no se consultó; "" = AniList no tiene. */
    val genres: String? = null,
    /** Identificador del manhwa en AniList (para buscar parecidos). 0 = no se sabe. */
    @ColumnInfo(defaultValue = "0") val anilistId: Long = 0,
    /** MANHWA, MANGA, MANHUA u OTHER (ver [Types]). */
    @ColumnInfo(defaultValue = "'MANHWA'") val type: String = Types.MANHWA
)

object Types {
    const val MANHWA = "MANHWA"
    const val MANGA = "MANGA"
    const val MANHUA = "MANHUA"
    const val OTHER = "OTHER"
    val all = listOf(MANHWA, MANGA, MANHUA, OTHER)

    fun label(t: String) = when (t) {
        MANHWA -> "Manhwa"
        MANGA -> "Manga"
        MANHUA -> "Manhua"
        else -> "Otro"
    }

    /** País de origen de AniList → tipo. */
    fun fromCountry(code: String?): String = when (code?.uppercase()) {
        "KR" -> MANHWA
        "JP" -> MANGA
        "CN", "TW", "HK" -> MANHUA
        else -> MANHWA
    }
}

/** Lista personalizada de manhwas ("Para recomendar", "Mejor arte"...). */
@Entity(tableName = "manhwa_list")
data class ManhwaList(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String
)

/** Un manhwa dentro de una lista. Se enlaza por titleKey para que sobreviva a importar y restaurar copias. */
@Entity(tableName = "list_entry", indices = [Index(value = ["listId", "titleKey"], unique = true)])
data class ListEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val listId: Long,
    val titleKey: String
)

/** Clave para comparar títulos sin importar mayúsculas, espacios o símbolos. */
fun titleKeyOf(title: String): String {
    val key = title.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), "")
    return key.ifEmpty { title.lowercase().trim() }
}

/** Palabra o frase guardada para estudiar inglés. */
@Entity(tableName = "word")
data class Word(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val translation: String? = null,
    val definition: String? = null,
    val definitionEs: String? = null,
    val example: String? = null,
    val manhwaTitle: String? = null,
    val chapter: String? = null,
    val note: String? = null,
    val learned: Boolean = false,
    /** true cuando ya se consiguió la traducción; si es false se reintenta al abrir Estudio. */
    val lookupDone: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    /** Caja del repaso espaciado (0 = nueva ... 5 = casi aprendida). Ver Srs en Review.kt. */
    @ColumnInfo(defaultValue = "0") val box: Int = 0,
    /** Desde cuándo toca repasarla (milisegundos). 0 = ya toca. */
    @ColumnInfo(defaultValue = "0") val dueAt: Long = 0,
    /** Cuándo se marcó como aprendida (milisegundos). 0 = no se sabe o no está aprendida. */
    @ColumnInfo(defaultValue = "0") val learnedAt: Long = 0,
    /** Veces por millón de palabras en inglés (para saber qué tan útil es). -1 = todavía no se consultó. */
    @ColumnInfo(defaultValue = "-1") val freq: Double = -1.0
)

/**
 * Registro de actividad por día, para las estadísticas.
 * [day] es el número de día (LocalDate.toEpochDay) y [kind] dice qué se cuenta: ver [ActivityKind].
 * Hay una sola fila por día y tipo; [amount] es la cantidad acumulada.
 */
@Entity(tableName = "activity", indices = [Index(value = ["day", "kind"], unique = true)])
data class Activity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val day: Long,
    val kind: String,
    val amount: Int
)

object ActivityKind {
    /** Tarjetas repasadas ese día. */
    const val REVIEW = "REVIEW"

    /** Capítulos leídos ese día (sumados con el botón + o con los cambios de capítulo). */
    const val CHAPTER = "CHAPTER"
}
