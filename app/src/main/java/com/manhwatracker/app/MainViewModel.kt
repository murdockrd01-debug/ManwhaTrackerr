package com.manhwatracker.app

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.manhwatracker.app.data.AniList
import com.manhwatracker.app.data.AppDatabase
import com.manhwatracker.app.data.BackupImporter
import com.manhwatracker.app.data.Manhwa
import com.manhwatracker.app.data.Status
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

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val dao = AppDatabase.get(app).dao()

    val items: StateFlow<List<Manhwa>> = dao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun clearMessage() {
        _message.value = null
    }

    fun observe(id: Long) = dao.observe(id)

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
