package com.manhwatracker.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

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
    val updatedAt: Long = System.currentTimeMillis()
)

/** Clave para comparar títulos sin importar mayúsculas, espacios o símbolos. */
fun titleKeyOf(title: String): String {
    val key = title.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), "")
    return key.ifEmpty { title.lowercase().trim() }
}

@Dao
interface ManhwaDao {
    @Query("SELECT * FROM manhwa ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<Manhwa>>

    @Query("SELECT * FROM manhwa WHERE id = :id")
    fun observe(id: Long): Flow<Manhwa?>

    @Query("SELECT * FROM manhwa WHERE titleKey = :key LIMIT 1")
    suspend fun findByKey(key: String): Manhwa?

    @Query("SELECT * FROM manhwa WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): Manhwa?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(m: Manhwa): Long

    @Update
    suspend fun update(m: Manhwa)

    @Delete
    suspend fun delete(m: Manhwa)
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
    val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface WordDao {
    @Query("SELECT * FROM word ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<Word>>

    @Query("SELECT * FROM word WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): Word?

    @Query("SELECT * FROM word WHERE lower(text) = lower(:text) LIMIT 1")
    suspend fun findByText(text: String): Word?

    @Query("SELECT * FROM word WHERE lookupDone = 0")
    suspend fun pending(): List<Word>

    @Insert
    suspend fun insert(w: Word): Long

    @Update
    suspend fun update(w: Word)

    @Delete
    suspend fun delete(w: Word)
}

/** Versión 1 -> 2: solo agrega la tabla de vocabulario. No toca tus manhwas. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `word` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`text` TEXT NOT NULL, " +
                "`translation` TEXT, " +
                "`definition` TEXT, " +
                "`example` TEXT, " +
                "`manhwaTitle` TEXT, " +
                "`chapter` TEXT, " +
                "`note` TEXT, " +
                "`learned` INTEGER NOT NULL, " +
                "`lookupDone` INTEGER NOT NULL, " +
                "`createdAt` INTEGER NOT NULL)"
        )
    }
}

/**
 * Versión 2 -> 3: agrega la definición en español y marca las palabras viejas
 * para que se vuelvan a buscar una vez con el sistema nuevo.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `word` ADD COLUMN `definitionEs` TEXT")
        db.execSQL("UPDATE `word` SET `lookupDone` = 0")
    }
}

@Database(entities = [Manhwa::class, Word::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): ManhwaDao
    abstract fun words(): WordDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "manhwa.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { instance = it }
            }
    }
}
