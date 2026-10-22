package com.manhwatracker.app

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.manhwatracker.app.data.AniList
import com.manhwatracker.app.data.AppDatabase
import com.manhwatracker.app.data.BackupFormatException
import com.manhwatracker.app.data.BackupImporter
import com.manhwatracker.app.data.Manhwa
import com.manhwatracker.app.data.OwnBackup
import com.manhwatracker.app.data.Prefs
import com.manhwatracker.app.data.Srs
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
        viewModelScope.launch { dao.update(previous) }
    }

    fun observe(id: Long) = dao.observe(id)

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
            wordDao.update(fresh.copy(learned = !fresh.learned))
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
            } finally {
                retryMutex.unlock()
            }
        }
    }

    // ---------- Repaso con tarjetas ----------

    /** Tarjetas de una ronda: las que ya tocan, o (si [all]) todas las no aprendidas. */
    suspend fun reviewQueue(all: Boolean): List<Word> {
        val list = if (all) wordDao.unlearned() else wordDao.due(System.currentTimeMillis())
        return list.take(Srs.SESSION_MAX)
    }

    /** Guarda la respuesta de una tarjeta y programa cuándo volverá a salir. */
    fun reviewed(w: Word, known: Boolean) {
        viewModelScope.launch {
            val fresh = wordDao.findById(w.id) ?: return@launch
            val next = Srs.next(fresh, known, System.currentTimeMillis())
            wordDao.update(next)
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
        viewModelScope.launch {
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
                _message.value = "Listo: $added nuevos, $updated actualizados"
            } catch (e: Exception) {
                _message.value = "No pude leer ese archivo. ¿Es un backup de Komikku (.tachibk)?"
            }
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

    fun increment(m: Manhwa) {
        val next = Math.floor(m.lastChapter) + 1
        val shown = if (next % 1.0 == 0.0) next.toInt().toString() else next.toString()
        _undo.value = UndoInfo("Cap. $shown · ${m.title}", m)
        save(m.copy(lastChapter = next))
    }

    fun delete(m: Manhwa) {
        viewModelScope.launch { dao.delete(m) }
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
