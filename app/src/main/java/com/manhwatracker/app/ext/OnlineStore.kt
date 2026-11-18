package com.manhwatracker.app.ext

import android.content.Context
import com.manhwatracker.app.data.ReaderLogic
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.security.MessageDigest

/** Baja las páginas de un capítulo en línea a la caché del teléfono (guarda los últimos 4 capítulos). */
object OnlineStore {
    private val gate = Semaphore(3)

    private fun sha(s: String) =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }.take(16)

    private fun fetchTo(ctx: Context, src: Source, page: Page, f: File) {
        val tmp = File(f.parentFile, f.name.substringBefore('.') + ".tmp")
        if (src is HttpSource) {
            if (page.imageUrl.isNullOrEmpty()) page.imageUrl = src.getImageUrl(page)
            src.getImage(page).use { r -> r.body.byteStream().use { i -> tmp.outputStream().use { o -> i.copyTo(o) } } }
        } else {
            val url = page.imageUrl ?: throw IllegalStateException("La página no trae dirección de imagen")
            ExtensionHost.network(ctx).client.newCall(Request.Builder().url(url).build()).execute().use { r ->
                if (!r.isSuccessful) throw IllegalStateException("Error ${r.code} al bajar la imagen")
                r.body.byteStream().use { i -> tmp.outputStream().use { o -> i.copyTo(o) } }
            }
        }
        if (tmp.length() == 0L) { tmp.delete(); throw IllegalStateException("La imagen llegó vacía") }
        tmp.renameTo(f)
    }

    /** Baja una página al almacenamiento permanente (para leer sin conexión). */
    suspend fun downloadPage(ctx: Context, src: Source, chapterKey: String, page: Page): File = gate.withPermit {
        withContext(Dispatchers.IO) {
            val dir = OfflineStore.chapterDir(ctx, chapterKey).apply { mkdirs() }
            val f = File(dir, "%05d.img".format(page.index))
            if (!(f.exists() && f.length() > 0)) fetchTo(ctx, src, page, f)
            f
        }
    }

    suspend fun pageFile(ctx: Context, src: Source, chapterKey: String, page: Page): File {
        OfflineStore.pageFileIfAny(ctx, chapterKey, page.index)?.let { return it }
        return pageFileCached(ctx, src, chapterKey, page)
    }

    private suspend fun pageFileCached(ctx: Context, src: Source, chapterKey: String, page: Page): File = gate.withPermit {
        withContext(Dispatchers.IO) {
            val root = File(ctx.cacheDir, "online").apply { mkdirs() }
            val dir = File(root, sha(chapterKey))
            if (!dir.exists()) {
                root.listFiles()?.filter { it.isDirectory }?.map { it.name to it.lastModified() }
                    ?.let { ReaderLogic.toEvict(it, dir.name, 4) }?.forEach { File(root, it).deleteRecursively() }
                dir.mkdirs()
            }
            val f = File(dir, "%05d.img".format(page.index))
            if (f.exists() && f.length() > 0) return@withContext f
            fetchTo(ctx, src, page, f)
            f
        }
    }
}
