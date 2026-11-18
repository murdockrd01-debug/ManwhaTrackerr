package com.manhwatracker.app.ext

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.setValue
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Cola de descargas: sigue mientras la app esté abierta (no se corta al salir de la pantalla). */
object OfflineDownloads {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queue = Mutex()

    /** Capítulos en cola o bajándose: clave → avance de 0 a 1 (-1 = esperando turno). */
    val active = mutableStateMapOf<String, Float>()
    /** Errores de la última descarga: clave → mensaje. */
    val errors = mutableStateMapOf<String, String>()
    /** Sube cada vez que un capítulo se termina o se borra (para refrescar las pantallas). */
    var version by mutableIntStateOf(0)
        private set

    fun enqueue(ctx: Context, src: Source, pkg: String, manga: SManga, chapters: List<SChapter>) {
        val app = ctx.applicationContext
        OfflineStore.saveManga(app, pkg, src.id, manga, chapters)
        for (ch in chapters) {
            val key = OfflineStore.key(src.id, manga.url, ch.url)
            if (active.containsKey(key) || OfflineStore.pageCount(app, key) > 0) continue
            active[key] = -1f
            errors.remove(key)
            scope.launch {
                queue.withLock {
                    try { download(app, src, key, ch) }
                    catch (e: CancellationException) { throw e }
                    catch (e: Throwable) { errors[key] = e.message ?: e.javaClass.simpleName }
                    finally { active.remove(key); version++ }
                }
            }
        }
    }

    private suspend fun download(ctx: Context, src: Source, key: String, ch: SChapter) {
        active[key] = 0f
        val pages = kotlinx.coroutines.withContext(Dispatchers.IO) { src.getPageList(ch).sortedBy { it.index } }
        if (pages.isEmpty()) throw IllegalStateException("El capítulo no tiene páginas")
        val done = java.util.concurrent.atomic.AtomicInteger()
        kotlinx.coroutines.coroutineScope {
            pages.map { p ->
                async {
                    OnlineStore.downloadPage(ctx, src, key, p)
                    active[key] = done.incrementAndGet().toFloat() / pages.size
                }
            }.awaitAll()
        }
        OfflineStore.markDone(ctx, key, pages.size)
    }

    fun delete(ctx: Context, key: String) {
        OfflineStore.delete(ctx, key)
        version++
    }
}
