package com.manhwatracker.app.ext

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.manhwatracker.app.ExploreActivity
import com.manhwatracker.app.ExploreMemory
import com.manhwatracker.app.R
import com.manhwatracker.app.data.ChapterPick
import com.manhwatracker.app.data.LibraryAdd
import com.manhwatracker.app.data.NewChapters
import com.manhwatracker.app.data.ReaderProgress
import com.manhwatracker.app.data.Status
import eu.kanade.tachiyomi.source.Source
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import java.util.concurrent.TimeUnit

/** Revisa en las fuentes si salió algún capítulo nuevo de lo que lees y lo avisa. */
object UpdateChecker {
    private const val SEP = "\u001F"
    private fun prefs(c: Context) = c.getSharedPreferences("updates", Context.MODE_PRIVATE)

    /** Novedades pendientes: (título en tu lista, último capítulo de la fuente). Solo las que aún no has leído. */
    suspend fun fresh(ctx: Context): List<Pair<String, Double>> {
        val out = ArrayList<Pair<String, Double>>()
        for ((k, v) in prefs(ctx).all) {
            if (!k.startsWith("fresh_") || v !is String) continue
            val f = v.split(SEP)
            val title = f.getOrNull(0) ?: continue
            val latest = f.getOrNull(1)?.toDoubleOrNull() ?: continue
            val lib = LibraryAdd.find(ctx, title)
            if (lib != null && lib.lastChapter + 0.0001 < latest) out.add(lib.title to latest)
        }
        return out.sortedBy { it.first.lowercase() }
    }

    /** Revisa todo; devuelve cuántos manhwas tienen capítulos nuevos. Con [notify] también avisa con una notificación. */
    /** Anota el último capítulo que tiene la fuente para el manhwa de tu biblioteca [libTitle] (para el contador de sin leer). */
    fun recordLatest(ctx: Context, libTitle: String, latest: Double?) {
        if (latest == null) return
        prefs(ctx).edit().putString("lat_lib_" + ExploreMemory.norm(libTitle), latest.toString()).apply()
    }

    /** Capítulos sin leer de un manhwa de la biblioteca; 0 si no se sabe o está terminado. */
    fun unreadFor(ctx: Context, libTitle: String, readUpTo: Double, status: String): Int {
        if (status == Status.COMPLETED) return 0
        val latest = prefs(ctx).getString("lat_lib_" + ExploreMemory.norm(libTitle), null)?.toDoubleOrNull()
        return NewChapters.unreadFromLatest(latest, readUpTo)
    }

    suspend fun run(ctx: Context, notify: Boolean): Int {
        val sp = prefs(ctx)
        val loaded = HashMap<String, List<Source>?>()
        val found = ArrayList<Pair<String, Double>>()
        for ((titles, link) in ExploreMemory.allLinks(ctx).take(40)) {
            val lib = titles.firstNotNullOfOrNull { LibraryAdd.find(ctx, it) } ?: continue
            if (lib.status == Status.COMPLETED) continue
            val sources = loaded.getOrPut(link.pkg) { (ExtensionHost.load(ctx, link.pkg) as? ExtensionHost.Result.Ok)?.ext?.sources }
            val src = sources?.firstOrNull { it.id == link.srcId } ?: continue
            val manga = link.manga ?: continue
            val latest = try {
                withTimeout(30_000) {
                    src.getMangaUpdate(manga, emptyList(), false, true).chapters
                        .mapNotNull { ChapterPick.number(it.chapter_number, it.name) }.maxOrNull()
                }
            } catch (e: CancellationException) { throw e } catch (e: Throwable) { null }
            val key = "latest_" + ExploreMemory.norm(manga.title)
            val prev = sp.getString(key, null)?.toDoubleOrNull()
            if (latest != null) sp.edit().putString(key, latest.toString()).apply()
            recordLatest(ctx, lib.title, latest)
            if (NewChapters.shouldNotify(prev, latest, lib.lastChapter)) {
                found.add(lib.title to latest!!)
                sp.edit().putString("fresh_" + ExploreMemory.norm(lib.title), lib.title + SEP + latest).apply()
            }
            delay(1500)
        }
        if (notify && found.isNotEmpty()) show(ctx, found)
        return found.size
    }

    private fun show(ctx: Context, found: List<Pair<String, Double>>) {
        if (!NotificationManagerCompat.from(ctx).areNotificationsEnabled()) return
        if (Build.VERSION.SDK_INT >= 26) {
            val m = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (m.getNotificationChannel(CHANNEL) == null) m.createNotificationChannel(NotificationChannel(CHANNEL, "Capítulos nuevos", NotificationManager.IMPORTANCE_DEFAULT))
        }
        val text = found.joinToString(" · ") { "${it.first} (cap. ${ReaderProgress.fmt(it.second)})" }
        val open = PendingIntent.getActivity(ctx, 2, Intent(ctx, ExploreActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(if (found.size == 1) "Capítulo nuevo" else "${found.size} manhwas con capítulos nuevos")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try { NotificationManagerCompat.from(ctx).notify(4108, n) } catch (_: SecurityException) {}
    }

    private const val CHANNEL = "new_chapters"
}

class UpdateWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        if (!com.manhwatracker.app.data.Prefs.updatesEnabled(applicationContext)) return Result.success()
        return try { UpdateChecker.run(applicationContext, notify = true); Result.success() } catch (e: CancellationException) { throw e } catch (e: Throwable) { Result.retry() }
    }

    companion object {
        private const val NAME = "chapter_check"
        fun schedule(ctx: Context) {
            val req = PeriodicWorkRequestBuilder<UpdateWorker>(6, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, req)
        }
        fun cancel(ctx: Context) { WorkManager.getInstance(ctx).cancelUniqueWork(NAME) }
    }
}
