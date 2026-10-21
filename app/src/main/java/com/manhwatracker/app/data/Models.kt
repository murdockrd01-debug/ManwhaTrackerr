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

@Database(entities = [Manhwa::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): ManhwaDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "manhwa.db"
                ).build().also { instance = it }
            }
    }
}
