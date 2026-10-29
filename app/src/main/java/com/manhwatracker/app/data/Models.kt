package com.manhwatracker.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface ManhwaDao {
    @Query("SELECT * FROM manhwa ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<Manhwa>>

    @Query("SELECT * FROM manhwa WHERE id = :id")
    fun observe(id: Long): Flow<Manhwa?>

    @Query("SELECT * FROM manhwa")
    suspend fun all(): List<Manhwa>

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

    /** Palabras sueltas a las que todavía no se les midió la frecuencia. */
    @Query("SELECT * FROM word WHERE freq < 0 AND instr(text, ' ') = 0")
    suspend fun needFreq(): List<Word>

    @Query("SELECT * FROM word")
    suspend fun all(): List<Word>

    /** Palabras que ya toca repasar (no aprendidas y con fecha vencida). */
    @Query("SELECT * FROM word WHERE learned = 0 AND dueAt <= :now ORDER BY dueAt ASC, createdAt ASC")
    suspend fun due(now: Long): List<Word>

    @Query("SELECT * FROM word WHERE learned = 0 ORDER BY dueAt ASC, createdAt ASC")
    suspend fun unlearned(): List<Word>

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

/** Versión 3 -> 4: agrega los campos del repaso con tarjetas. Las palabras viejas quedan listas para repasar. */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `word` ADD COLUMN `box` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `word` ADD COLUMN `dueAt` INTEGER NOT NULL DEFAULT 0")
    }
}

/**
 * Versión 4 -> 5: nota y comentario por manhwa, fecha en que se aprendió cada palabra
 * y el registro de actividad para las estadísticas. No borra ni cambia nada de lo que ya tienes.
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `manhwa` ADD COLUMN `rating` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `manhwa` ADD COLUMN `review` TEXT")
        db.execSQL("ALTER TABLE `word` ADD COLUMN `learnedAt` INTEGER NOT NULL DEFAULT 0")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `activity` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`day` INTEGER NOT NULL, " +
                "`kind` TEXT NOT NULL, " +
                "`amount` INTEGER NOT NULL)"
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_activity_day_kind` ON `activity` (`day`, `kind`)")
    }
}

/** Versión 8: tipo de obra (manhwa, manga, manhua). Todo lo que ya tienes queda como manhwa. */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `manhwa` ADD COLUMN `type` TEXT NOT NULL DEFAULT 'MANHWA'")
    }
}

/** Versión 7: géneros y número de AniList en cada manhwa, y listas personalizadas. No cambia nada de lo que ya tienes. */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `manhwa` ADD COLUMN `genres` TEXT")
        db.execSQL("ALTER TABLE `manhwa` ADD COLUMN `anilistId` INTEGER NOT NULL DEFAULT 0")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `manhwa_list` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `list_entry` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`listId` INTEGER NOT NULL, `titleKey` TEXT NOT NULL)"
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_list_entry_listId_titleKey` ON `list_entry` (`listId`, `titleKey`)")
    }
}

@Dao
interface ListDao {
    @Query("SELECT * FROM manhwa_list ORDER BY name COLLATE NOCASE ASC")
    fun observeLists(): Flow<List<ManhwaList>>

    @Query("SELECT * FROM list_entry")
    fun observeEntries(): Flow<List<ListEntry>>

    @Query("SELECT * FROM manhwa_list")
    suspend fun allLists(): List<ManhwaList>

    @Query("SELECT * FROM list_entry")
    suspend fun allEntries(): List<ListEntry>

    @Insert
    suspend fun insertList(l: ManhwaList): Long

    @Query("UPDATE manhwa_list SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("DELETE FROM manhwa_list WHERE id = :id")
    suspend fun deleteList(id: Long)

    @Query("DELETE FROM list_entry WHERE listId = :id")
    suspend fun deleteEntriesOf(id: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addEntry(e: ListEntry): Long

    @Query("DELETE FROM list_entry WHERE listId = :listId AND titleKey = :key")
    suspend fun removeEntry(listId: Long, key: String)
}

/** Versión 6: guarda qué tan común es cada palabra, para ordenar por utilidad. No cambia nada de lo que ya tienes. */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `word` ADD COLUMN `freq` REAL NOT NULL DEFAULT -1")
    }
}

// Clase abstracta (no interfaz) porque tiene un método con cuerpo, y Room lo admite así sin ajustes.
@Dao
abstract class ActivityDao {
    @Query("SELECT * FROM activity ORDER BY day ASC")
    abstract fun observeAll(): Flow<List<Activity>>

    @Query("SELECT * FROM activity")
    abstract suspend fun all(): List<Activity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertIgnore(a: Activity): Long

    @Query("UPDATE activity SET amount = amount + :n WHERE day = :day AND kind = :kind")
    abstract suspend fun addTo(day: Long, kind: String, n: Int): Int

    @Query("UPDATE activity SET amount = :amount WHERE day = :day AND kind = :kind")
    abstract suspend fun setAmount(day: Long, kind: String, amount: Int): Int

    /** Suma [n] al contador de ese día y tipo (lo crea si no existía). */
    open suspend fun bump(day: Long, kind: String, n: Int) {
        if (n == 0) return
        if (insertIgnore(Activity(day = day, kind = kind, amount = n)) == -1L) addTo(day, kind, n)
    }
}

@Database(entities = [Manhwa::class, Word::class, Activity::class, ManhwaList::class, ListEntry::class], version = 8, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): ManhwaDao
    abstract fun words(): WordDao
    abstract fun activity(): ActivityDao
    abstract fun lists(): ListDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "manhwa.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8).build().also { instance = it }
            }
    }
}
