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
import kotlinx.coroutines.delay
import com.manhwatracker.app.data.OfflinePlan
import com.manhwatracker.app.data.Prefs
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Cola de descargas: sigue mientras la app esté abierta (no se corta al salir de la pantalla). */
object OfflineDownloads {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queue = Mutex()
    private val jobs = java.util.concurrent.ConcurrentHashMap<String, kotlinx.coroutines.Job>()

    /** Capítulos en cola o bajándose: clave → avance de 0 a 1 (-1 = esperando turno). */
    val active = mutableStateMapOf<String, Float>()
    /** Errores de la última descarga: clave → mensaje. */
    val errors = mutableStateMapOf<String, String>()
    /** Sube cada vez que un capítulo se termina o se borra (para refrescar las pantallas). */
    var version by mutableIntStateOf(0)
        private set

    // ---- Cola guardada: si la app se cierra a mitad, al volver a abrirla se retoma (ver [resume]).
    private const val SEP = "\u001F"
    private fun prefs(c: Context) = c.getSharedPreferences("offline_queue", Context.MODE_PRIVATE)
    private fun pend(c: Context, add: Boolean, pkg: String, srcId: Long, mangaUrl: String, chUrl: String) = synchronized(this) {
        val cur = (prefs(c).getStringSet("pending", emptySet()) ?: emptySet()).toMutableSet()
        val e = listOf(pkg, srcId.toString(), mangaUrl, chUrl).joinToString(SEP)
        if (add) cur.add(e) else cur.remove(e)
        prefs(c).edit().putStringSet("pending", cur).apply()
    }
    /** Pendientes guardados: pkg, id de fuente, url del manhwa, url del capítulo. */
    fun pending(c: Context): List<List<String>> =
        (prefs(c).getStringSet("pending", emptySet()) ?: emptySet()).map { it.split(SEP) }.filter { it.size == 4 }

    private fun saveCover(ctx: Context, src: Source, manga: SManga) {
        val url = manga.thumbnail_url ?: return
        val f = OfflineStore.coverFile(ctx, src.id, manga.url)
        if (f.exists() && f.length() > 0) return
        runCatching {
            val b = okhttp3.Request.Builder().url(url)
            (src as? eu.kanade.tachiyomi.source.online.HttpSource)?.headers?.let { b.headers(it) }
            ExtensionHost.network(ctx).client.newCall(b.build()).execute().use { r ->
                if (r.isSuccessful) { f.parentFile?.mkdirs(); r.body.byteStream().use { i -> f.outputStream().use { o -> i.copyTo(o) } } }
            }
        }
    }

    fun enqueue(ctx: Context, src: Source, pkg: String, manga: SManga, chapters: List<SChapter>) {
        val app = ctx.applicationContext
        OfflineStore.saveManga(app, pkg, src.id, manga, chapters)
        scope.launch { saveCover(app, src, manga) }
        DownloadService.start(app)
        for (ch in chapters) {
            val key = OfflineStore.key(src.id, manga.url, ch.url)
            if (active.containsKey(key) || OfflineStore.pageCount(app, key) > 0) continue
            active[key] = -1f
            errors.remove(key)
            pend(app, true, pkg, src.id, manga.url, ch.url)
            jobs[key] = scope.launch {
                queue.withLock {
                    try { download(app, src, key, ch) }
                    catch (e: CancellationException) { throw e }
                    catch (e: Throwable) { errors[key] = e.message ?: e.javaClass.simpleName }
                    finally { active.remove(key); jobs.remove(key); pend(app, false, pkg, src.id, manga.url, ch.url); version++ }
                }
            }
        }
    }

    private suspend fun download(ctx: Context, src: Source, key: String, ch: SChapter) {
        active[key] = 0f
        // "Solo Wi-Fi": espera (sin gastar datos) hasta que haya una red no medida.
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        while (!OfflinePlan.canDownload(Prefs.wifiOnly(ctx), cm.isActiveNetworkMetered)) {
            active[key] = -1f
            delay(4000)
        }
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

    /** Cancela todo lo que está en cola o bajándose (lo ya bajado se conserva). */
    fun cancelAll(ctx: Context) {
        jobs.values.forEach { it.cancel() }
        jobs.clear()
        synchronized(this) { prefs(ctx).edit().remove("pending").apply() }
        active.clear()
        version++
    }

    /** Avisa a las pantallas que cambió lo descargado (por ejemplo tras borrar un manhwa entero). */
    fun refresh() { version++ }

    fun delete(ctx: Context, key: String) {
        OfflineStore.delete(ctx, key)
        version++
    }
}
