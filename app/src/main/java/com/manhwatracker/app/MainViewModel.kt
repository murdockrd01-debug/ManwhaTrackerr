package com.manhwatracker.app

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.manhwatracker.app.data.Activity
import com.manhwatracker.app.data.ActivityKind
import com.manhwatracker.app.data.DiscoverDisk
import com.manhwatracker.app.data.ListImport
import com.manhwatracker.app.data.AniList
import com.manhwatracker.app.data.AutoSync
import com.manhwatracker.app.data.AppDatabase
import com.manhwatracker.app.data.BackupFormatException
import com.manhwatracker.app.data.BackupImporter
import com.manhwatracker.app.data.ListEntry
import com.manhwatracker.app.data.Genres
import com.manhwatracker.app.data.Manhwa
import com.manhwatracker.app.data.ManhwaList
import com.manhwatracker.app.data.OwnBackup
import com.manhwatracker.app.data.Prefs
import com.manhwatracker.app.data.Srs
import com.manhwatracker.app.data.Stats
import com.manhwatracker.app.data.Status
import com.manhwatracker.app.data.Word
import com.manhwatracker.app.data.WordRepo
import com.manhwatracker.app.data.titleKeyOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Lo que se muestra en la pantalla de Sincronización. */
data class FolderInfo(val uri: String, val label: String)

data class SyncInfo(
    val komikkuFolders: List<FolderInfo>,
    val komikkuLastName: String?,
    val komikkuLastAt: Long,
    val backupFolder: String?,
    val backupLastName: String?,
    val backupLastAt: Long,
    val backupError: String?
)

/** Aviso con botón "Deshacer" tras sumar un capítulo. [previous] es el manhwa tal como estaba antes. */
data class UndoInfo(val text: String, val previous: Manhwa)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val dao = AppDatabase.get(app).dao()

    val items: StateFlow<List<Manhwa>> = dao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun clearMessage() {
        _message.value = null
    }

    private val _undo = MutableStateFlow<UndoInfo?>(null)
    val undo: StateFlow<UndoInfo?> = _undo.asStateFlow()

    /** Solo borra el aviso si sigue siendo [info]; si ya llegó uno más nuevo, no lo toca. */
    fun clearUndo(info: UndoInfo) {
        _undo.compareAndSet(info, null)
    }

    fun undoIncrement(previous: Manhwa) {
        viewModelScope.launch {
            dao.update(previous)
            logActivity(ActivityKind.CHAPTER, -1)
        }
    }

    fun observe(id: Long) = dao.observe(id)

    // ---------- Actividad (estadísticas) ----------

    private val activityDao = AppDatabase.get(app).activity()

    val activity: StateFlow<List<Activity>> = activityDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Suma [n] a lo que se cuenta ese día (por defecto hoy). */
    private suspend fun logActivity(kind: String, n: Int, millis: Long = System.currentTimeMillis()) {
        activityDao.bump(Stats.dayOf(millis), kind, n)
    }

    /** Cuántos capítulos enteros se avanzaron entre dos posiciones (0 si no avanzó). */
    private fun chaptersBetween(from: Double, to: Double): Int =
        maxOf(0, Math.floor(to).toInt() - Math.floor(from).toInt())

    fun showMessage(text: String) {
        _message.value = text
    }

    // ---------- Estudio (vocabulario) ----------

    private val wordDao = AppDatabase.get(app).words()

    val words: StateFlow<List<Word>> = wordDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addWord(text: String, manhwa: String, chapter: String, note: String) {
        val ctx = getApplication<Application>()
        Prefs.setLast(
            ctx,
            manhwa.trim().takeIf { it.isNotEmpty() },
            chapter.trim().takeIf { it.isNotEmpty() }
        )
        viewModelScope.launch {
            val r = WordRepo.save(ctx, text, manhwa, chapter, note)
            _message.value = if (r.added) "Guardada" else "Ya la tenías guardada"
        }
    }

    fun toggleLearned(w: Word) {
        viewModelScope.launch {
            val fresh = wordDao.findById(w.id) ?: return@launch
            wordDao.update(Srs.setLearned(fresh, !fresh.learned, System.currentTimeMillis()))
        }
    }

    fun deleteWord(w: Word) {
        viewModelScope.launch { wordDao.delete(w) }
    }

    fun relookup(w: Word) {
        viewModelScope.launch {
            val ok = WordRepo.lookup(getApplication(), w.id)
            _message.value = if (ok) "Listo" else "No pude buscarla. Revisa tu conexión."
        }
    }

    private val retryMutex = Mutex()

    /** Completa las palabras que se guardaron sin internet. */
    fun retryPending() {
        viewModelScope.launch {
            if (!retryMutex.tryLock()) return@launch
            try {
                for (w in wordDao.pending()) {
                    WordRepo.lookup(getApplication(), w.id)
                    delay(400)
                }
                WordRepo.fillFrequencies(getApplication())
            } finally {
                retryMutex.unlock()
            }
        }
    }

    // ---------- Repaso con tarjetas ----------

    /**
     * Tarjetas de una ronda: las que ya tocan, o (si [all]) todas las no aprendidas.
     * [titleKey] limita a las palabras de un manhwa; [writing] deja solo las que tienen traducción.
     */
    suspend fun reviewQueue(all: Boolean, titleKey: String?, writing: Boolean): List<Word> =
        Srs.queue(wordDao.all(), System.currentTimeMillis(), all, titleKey, writing)

    /** Guarda la respuesta de una tarjeta y programa cuándo volverá a salir. */
    fun reviewed(w: Word, known: Boolean) {
        viewModelScope.launch {
            val fresh = wordDao.findById(w.id) ?: return@launch
            val next = Srs.next(fresh, known, System.currentTimeMillis())
            wordDao.update(next)
            logActivity(ActivityKind.REVIEW, 1)
            if (next.learned && !fresh.learned) _message.value = "¡Aprendida!: ${w.text}"
        }
    }

    // ---------- Copia propia (exportar / restaurar) ----------

    fun exportOwnBackup(uri: Uri) {
        viewModelScope.launch {
            try {
                val r = withContext(Dispatchers.IO) { OwnBackup.write(getApplication(), uri) }
                _message.value = "Copia guardada: ${r.manhwas} manhwas y ${r.words} palabras"
            } catch (e: BackupFormatException) {
                _message.value = e.message ?: "No pude guardar la copia"
            } catch (e: Exception) {
                _message.value = "No pude guardar la copia"
            }
        }
    }

    fun restoreOwnBackup(uri: Uri) {
        viewModelScope.launch {
            try {
                val r = withContext(Dispatchers.IO) { OwnBackup.restore(getApplication(), uri) }
                _message.value = "Restaurado: ${r.manhwasAdded} manhwas nuevos, ${r.manhwasUpdated} actualizados; " +
                    "${r.wordsAdded} palabras nuevas, ${r.wordsUpdated} actualizadas"
            } catch (e: BackupFormatException) {
                _message.value = e.message ?: "No pude restaurar la copia"
            } catch (e: Exception) {
                _message.value = "No pude restaurar la copia"
            }
        }
    }

    // ---------- Importar backup de Komikku / Mihon ----------

    fun importBackup(uri: Uri) {
        viewModelScope.launch { runImport(uri, quiet = false, prefix = "Listo") }
    }

    /**
     * Importa un backup de Komikku. Con [quiet] solo avisa si hubo cambios (para la importación automática).
     * Devuelve true si pudo leer el archivo.
     */
    private suspend fun runImport(uri: Uri, quiet: Boolean, prefix: String): Boolean {
        try {
            val parsed = withContext(Dispatchers.IO) { BackupImporter.read(getApplication(), uri) }
            var added = 0
            var updated = 0

            for (p in parsed) {
                val key = titleKeyOf(p.title)
                val existing = dao.findByKey(key)
                val readAt = if (p.lastReadAt > 0) p.lastReadAt else System.currentTimeMillis()

                if (existing == null) {
                    dao.insert(
                        Manhwa(
                            title = p.title,
                            titleKey = key,
                            coverUrl = p.coverUrl,
                            lastChapter = p.lastChapter,
                            totalChapters = p.totalChapters,
                            status = if (p.lastChapter > 0) Status.READING else Status.PLAN,
                            updatedAt = readAt
                        )
                    )
                    added++
                } else {
                    val progressChanged = p.lastChapter > existing.lastChapter
                    // Los capítulos nuevos se cuentan en el día en que los leíste, no en el de la importación.
                    if (progressChanged) {
                        logActivity(ActivityKind.CHAPTER, chaptersBetween(existing.lastChapter, p.lastChapter), readAt)
                    }
                    dao.update(
                        existing.copy(
                            lastChapter = if (progressChanged) p.lastChapter else existing.lastChapter,
                            totalChapters = maxOf(existing.totalChapters, p.totalChapters),
                            coverUrl = existing.coverUrl ?: p.coverUrl,
                            updatedAt = if (progressChanged) readAt else existing.updatedAt
                        )
                    )
                    if (progressChanged) updated++
                }
            }
            if (!quiet || added + updated > 0) {
                _message.value = "$prefix: $added nuevos, $updated actualizados"
            }
            return true
        } catch (e: Exception) {
            _message.value = "No pude leer ese archivo. ¿Es un backup de Komikku (.tachibk)?"
            return false
        }
    }

    // ---------- Sincronización con carpetas (Komikku y copia propia) ----------

    private fun readSync(): SyncInfo {
        val c = getApplication<Application>()
        return SyncInfo(
            komikkuFolders = Prefs.komikkuFolders(c).map { FolderInfo(it, AutoSync.folderLabel(Uri.parse(it))) },
            komikkuLastName = Prefs.lastKomikkuName(c),
            komikkuLastAt = Prefs.lastKomikkuAt(c),
            backupFolder = Prefs.backupFolder(c)?.let { AutoSync.folderLabel(Uri.parse(it)) },
            backupLastName = Prefs.lastBackupName(c),
            backupLastAt = Prefs.lastBackupAt(c),
            backupError = Prefs.backupError(c)
        )
    }

    private val _sync = MutableStateFlow(readSync())
    val sync: StateFlow<SyncInfo> = _sync.asStateFlow()

    fun refreshSync() {
        _sync.value = readSync()
    }

    /** Agrega una carpeta donde Komikku guarda copias (se pueden tener varias). */
    fun addKomikkuFolder(uri: Uri) {
        val c = getApplication<Application>()
        AutoSync.takePermission(c, uri, write = false)
        val current = Prefs.komikkuFolders(c)
        if (uri.toString() !in current) Prefs.setKomikkuFolders(c, current + uri.toString())
        Prefs.setLastKomikku(c, null, null, 0L)
        refreshSync()
        autoSync(manual = true)
    }

    fun removeKomikkuFolder(uri: String) {
        val c = getApplication<Application>()
        Prefs.setKomikkuFolders(c, Prefs.komikkuFolders(c).filter { it != uri })
        Prefs.setLastKomikku(c, null, null, 0L)
        refreshSync()
    }

    /**
     * Importa la copia más reciente de Komikku si es nueva. Se llama al abrir la app (en silencio)
     * y desde el botón "Actualizar ahora" ([manual] = true, que también avisa cuando no hay nada nuevo).
     */
    fun autoSync(manual: Boolean = false) {
        val c = getApplication<Application>()
        if (Prefs.komikkuFolders(c).isEmpty()) return
        viewModelScope.launch {
            // Si ya hay una revisión en curso, no se lanza otra (evita importar dos veces lo mismo).
            if (!syncMutex.tryLock()) return@launch
            try {
                when (val check = withContext(Dispatchers.IO) { AutoSync.checkKomikku(c) }) {
                    is AutoSync.KomikkuCheck.NoFiles ->
                        if (manual) _message.value = "No encontré copias de Komikku (.tachibk) en esa carpeta"

                    is AutoSync.KomikkuCheck.UpToDate ->
                        if (manual) _message.value = "Ya estás al día con la última copia de Komikku"

                    is AutoSync.KomikkuCheck.Pending -> {
                        val ok = runImport(check.uri, quiet = !manual, prefix = "Komikku")
                        if (ok) Prefs.setLastKomikku(c, check.key, check.name, System.currentTimeMillis())
                    }
                }
            } catch (e: Exception) {
                _message.value = "No pude leer la carpeta de Komikku. Elígela otra vez en Sincronización."
            } finally {
                syncMutex.unlock()
            }
            refreshSync()
        }
    }

    private val syncMutex = Mutex()

    fun setBackupFolder(uri: Uri) {
        val c = getApplication<Application>()
        AutoSync.takePermission(c, uri, write = true)
        Prefs.setBackupFolder(c, uri.toString())
        Prefs.setBackupError(c, null)
        refreshSync()
        backupNow()
    }

    fun clearBackupFolder() {
        val c = getApplication<Application>()
        Prefs.setBackupFolder(c, null)
        Prefs.setBackupError(c, null)
        refreshSync()
    }

    /** Guarda una copia propia ahora mismo en la carpeta elegida. */
    fun backupNow() {
        val c = getApplication<Application>()
        viewModelScope.launch {
            try {
                val name = AutoSync.backupIfDue(c, force = true)
                if (name != null) _message.value = "Copia guardada: $name"
            } catch (e: Exception) {
                Prefs.setBackupError(c, "No pude guardar la copia en la carpeta. Elígela otra vez.")
                _message.value = "No pude guardar la copia en esa carpeta"
            }
            refreshSync()
        }
    }

    // ---------- Agregar / editar / borrar ----------

    fun add(title: String, chapter: Double, total: Int) {
        val clean = title.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            val key = titleKeyOf(clean)
            if (dao.findByKey(key) != null) {
                _message.value = "Ese manhwa ya está en tu lista"
                return@launch
            }
            val id = dao.insert(
                Manhwa(
                    title = clean,
                    titleKey = key,
                    lastChapter = chapter,
                    totalChapters = total,
                    status = if (chapter > 0) Status.READING else Status.PLAN
                )
            )
            // La portada se busca sola en AniList (ver coverFallback en la tarjeta).
            if (id <= 0) _message.value = "No se pudo agregar"
        }
    }

    fun save(m: Manhwa) {
        viewModelScope.launch { dao.update(m.copy(updatedAt = System.currentTimeMillis())) }
    }

    /** Guarda lo editado en la pantalla de detalle y cuenta los capítulos que avanzaste. */
    fun saveDetail(old: Manhwa, edited: Manhwa) {
        viewModelScope.launch {
            dao.update(edited.copy(updatedAt = System.currentTimeMillis()))
            logActivity(ActivityKind.CHAPTER, chaptersBetween(old.lastChapter, edited.lastChapter))
        }
    }

    fun increment(m: Manhwa) {
        val next = Math.floor(m.lastChapter) + 1
        val shown = if (next % 1.0 == 0.0) next.toInt().toString() else next.toString()
        _undo.value = UndoInfo("Cap. $shown · ${m.title}", m)
        save(m.copy(lastChapter = next))
        // El manhwa que estás leyendo ahora pasa a ser el destino por defecto de las palabras nuevas.
        Prefs.setLast(getApplication<Application>(), m.title, shown)
        viewModelScope.launch { logActivity(ActivityKind.CHAPTER, 1) }
    }

    fun delete(m: Manhwa) {
        viewModelScope.launch { dao.delete(m) }
    }

    // ---------- Listas personalizadas ----------

    private val listDao = AppDatabase.get(app).lists()

    val lists: StateFlow<List<ManhwaList>> =
        listDao.observeLists().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val entries: StateFlow<List<ListEntry>> =
        listDao.observeEntries().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Crea una lista (y, si se pasa [addKey], mete ahí ese manhwa). */
    fun createList(name: String, addKey: String? = null) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            val existing = listDao.allLists().firstOrNull { it.name.equals(clean, ignoreCase = true) }
            val id = existing?.id ?: listDao.insertList(ManhwaList(name = clean))
            if (addKey != null) listDao.addEntry(ListEntry(listId = id, titleKey = addKey))
            if (existing != null) _message.value = "Ya tenías una lista llamada “${existing.name}”"
        }
    }

    fun renameList(id: Long, name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch { listDao.rename(id, clean) }
    }

    fun deleteList(id: Long) {
        viewModelScope.launch {
            listDao.deleteEntriesOf(id)
            listDao.deleteList(id)
        }
    }

    fun toggleInList(listId: Long, titleKey: String, on: Boolean) {
        viewModelScope.launch {
            if (on) listDao.addEntry(ListEntry(listId = listId, titleKey = titleKey))
            else listDao.removeEntry(listId, titleKey)
        }
    }

    // ---------- Géneros y parecidos (AniList) ----------

    private val metaMutex = Mutex()

    /** Trae géneros (y el número de AniList) de los manhwas que todavía no los tienen. Va despacio para respetar el límite de AniList. */
    fun fillMeta() {
        viewModelScope.launch {
            if (!metaMutex.tryLock()) return@launch
            try {
                for (m in dao.all().filter { it.genres == null }) {
                    val meta = coverMutex.withLock {
                        AniList.searchMeta(m.title).also { delay(800) }
                    } ?: break // sin conexión: se reintenta la próxima vez
                    val current = dao.findById(m.id) ?: continue
                    dao.update(
                        current.copy(
                            genres = meta.genres.joinToString(","),
                            anilistId = if (meta.id > 0) meta.id else current.anilistId,
                            coverUrl = current.coverUrl ?: meta.cover
                        )
                    )
                }
            } finally {
                metaMutex.unlock()
            }
        }
    }

    /** Manhwas parecidos al de [m]. Devuelve null si no hay conexión. */
    suspend fun loadSimilar(m: Manhwa): List<AniList.Similar>? {
        var id = m.anilistId
        if (id <= 0L) {
            val meta = coverMutex.withLock { AniList.searchMeta(m.title) } ?: return null
            if (meta.id <= 0L) return emptyList()
            id = meta.id
            dao.findById(m.id)?.let { dao.update(it.copy(anilistId = id, genres = it.genres ?: meta.genres.joinToString(","))) }
        }
        return AniList.similar(id)
    }

    // ---------- Descubrir ----------

    private val discoverCache = mutableMapOf<String, List<AniList.Similar>>()
    private val discoverMutex = Mutex()

    class DiscoverResult(val items: List<AniList.Similar>, val error: String?)

    /**
     * Sugerencias para la pestaña Descubrir. [tab]: FORYOU (según lo que lees), TREND, POPULAR o TOP.
     * Se guardan en memoria; con [force] se vuelven a pedir.
     */
    suspend fun discover(tab: String, genre: String?, force: Boolean = false): DiscoverResult {
        val key = "$tab|${genre.orEmpty()}"
        if (!force) discoverCache[key]?.let { return DiscoverResult(it, null) }
        val offline = "No pude conectar con AniList. Revisa tu internet."

        return discoverMutex.withLock {
            val result: DiscoverResult = when (tab) {
                "FORYOU" -> forYou()
                else -> {
                    val sort = when (tab) {
                        "POPULAR" -> "POPULARITY_DESC"
                        "TOP" -> "SCORE_DESC"
                        else -> "TRENDING_DESC"
                    }
                    val list = AniList.listing(sort, genre)
                    if (list == null) DiscoverResult(emptyList(), offline) else DiscoverResult(list, null)
                }
            }
            if (result.error == null) {
                discoverCache[key] = result.items
                if (result.items.isNotEmpty()) DiscoverDisk.save(getApplication(), key, result.items)
                result
            } else {
                // Sin internet: se muestran las últimas sugerencias guardadas, si las hay.
                val saved = DiscoverDisk.load(getApplication(), key)
                if (saved != null) DiscoverResult(saved, null) else result
            }
        }
    }

    /** Recomendaciones según tus manhwas: los que más de los tuyos recomiendan, y de tus géneros favoritos. */
    private suspend fun forYou(): DiscoverResult {
        val owned = dao.all()
        val seeds = owned.filter { it.anilistId > 0 }
            .sortedWith(
                compareByDescending<Manhwa> { it.rating }
                    .thenByDescending { it.status == Status.READING }
                    .thenByDescending { it.updatedAt }
            )
            .take(6)
        if (seeds.isEmpty()) {
            fillMeta()
            return DiscoverResult(
                emptyList(),
                "Todavía estoy reconociendo tus manhwas en AniList. Espera un momento y toca “Actualizar”."
            )
        }

        class Agg(val item: AniList.Similar) {
            var count = 0
            val sources = mutableListOf<String>()
        }

        val found = linkedMapOf<Long, Agg>()
        var failures = 0
        for (seed in seeds) {
            val recs = coverMutex.withLock { AniList.similar(seed.anilistId).also { delay(700) } }
            if (recs == null) {
                failures++
                continue
            }
            for (r in recs) {
                val agg = found.getOrPut(r.id) { Agg(r) }
                agg.count++
                if (seed.title !in agg.sources) agg.sources += seed.title
            }
        }
        if (failures == seeds.size) return DiscoverResult(emptyList(), "No pude conectar con AniList. Revisa tu internet.")

        val ownedKeys = owned.map { it.titleKey }.toSet()
        val ownedIds = owned.filter { it.anilistId > 0 }.map { it.anilistId }.toSet()

        // Géneros que más te gustan: pesan más los de los manhwas que calificaste alto.
        val weights = mutableMapOf<String, Int>()
        for (m in owned) {
            val w = when {
                m.rating >= 8 -> 3
                m.rating >= 6 -> 2
                else -> 1
            }
            for (g in Genres.parse(m.genres)) weights[g] = (weights[g] ?: 0) + w
        }
        val maxWeight = (weights.values.maxOrNull() ?: 1).coerceAtLeast(1)

        val ranked = found.values
            .filter { it.item.id !in ownedIds && titleKeyOf(it.item.title) !in ownedKeys }
            .sortedByDescending { agg ->
                val gs = agg.item.genres
                val affinity = if (gs.isEmpty()) 0.0 else gs.sumOf { (weights[it] ?: 0).toDouble() } / (gs.size * maxWeight)
                agg.count * 10.0 + (agg.item.score ?: 60) / 20.0 + affinity * 4.0
            }
            .take(40)
            .map { it.item.copy(because = it.sources.take(3)) }

        return DiscoverResult(ranked, null)
    }

    /** Reseñas de AniList para un manhwa tuyo (busca su número en AniList si todavía no se tiene). */
    suspend fun loadReviews(m: Manhwa): List<AniList.Review>? {
        var id = m.anilistId
        if (id <= 0L) {
            val meta = coverMutex.withLock { AniList.searchMeta(m.title) } ?: return null
            if (meta.id <= 0L) return emptyList()
            id = meta.id
            dao.findById(m.id)?.let { dao.update(it.copy(anilistId = id, genres = it.genres ?: meta.genres.joinToString(","))) }
        }
        return AniList.reviews(id)
    }

    suspend fun loadReviewsById(anilistId: Long): List<AniList.Review>? = AniList.reviews(anilistId)

    /** Agrega un manhwa sugerido a "Pendientes". */
    /** Importa la lista de un usuario de AniList. Nunca baja tus capítulos ni cambia tu estado o nota. */
    fun importAniList(username: String, onDone: () -> Unit = {}) {
        val name = username.trim().removePrefix("@")
        if (name.isEmpty()) { onDone(); return }
        viewModelScope.launch {
            val res = AniList.userList(name)
            if (res.error != null) {
                _message.value = res.error
            } else {
                var added = 0
                var updated = 0
                for (e in res.entries) {
                    val old = dao.findByKey(titleKeyOf(e.title))
                    if (old == null) {
                        if (dao.insert(ListImport.create(e)) > 0) added++
                    } else {
                        val m = ListImport.merge(old, e)
                        if (m != old) { dao.update(m); updated++ }
                    }
                }
                _message.value = "AniList: $added nuevos, $updated actualizados"
            }
            onDone()
        }
    }

    fun addPlanned(title: String, cover: String?) {
        viewModelScope.launch {
            val key = titleKeyOf(title)
            if (dao.findByKey(key) != null) {
                _message.value = "Ya lo tienes en tu lista"
                return@launch
            }
            dao.insert(Manhwa(title = title, titleKey = key, coverUrl = cover, status = Status.PLAN))
            _message.value = "Agregado a pendientes: $title"
        }
    }

    // ---------- Portadas ----------

    private val coverTried = mutableSetOf<Long>()
    private val coverMutex = Mutex()

    /**
     * Si una portada falta o no carga, la buscamos en AniList.
     * Se hace de una en una y con una pausa para no pasarnos del límite de AniList.
     */
    fun coverFallback(m: Manhwa, force: Boolean = false) {
        if (force) coverTried.remove(m.id)
        if (!coverTried.add(m.id)) return
        viewModelScope.launch {
            coverMutex.withLock {
                val url = AniList.searchCover(m.title)
                if (url != null) {
                    val current = dao.findById(m.id)
                    if (current != null && current.coverUrl != url) {
                        dao.update(current.copy(coverUrl = url))
                    }
                } else if (force) {
                    _message.value = "No encontré portada en AniList"
                }
                delay(700)
            }
        }
    }
}
