package com.manhwatracker.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Agregar a "Mis manhwas" lo que empiezas a leer en una fuente. No duplica: si ya está (o se parece), no hace nada. */
object LibraryAdd {
    /** ¿Ya está en tu lista un manhwa con este título? */
    suspend fun contains(ctx: Context, title: String): Boolean = withContext(Dispatchers.IO) {
        val all = AppDatabase.get(ctx).dao().all()
        all.any { it.titleKey == titleKeyOf(title) } || ReaderProgress.match(all, title) != null
    }

    /** El manhwa de tu lista con este título, o null si no está. */
    suspend fun find(ctx: Context, title: String): Manhwa? = withContext(Dispatchers.IO) {
        val all = AppDatabase.get(ctx).dao().all()
        all.firstOrNull { it.titleKey == titleKeyOf(title) } ?: ReaderProgress.match(all, title)
    }

    /** El capítulo en que vas en tu lista para este título, o null si no está en la lista. */
    suspend fun lastChapter(ctx: Context, title: String): Double? = find(ctx, title)?.lastChapter

    /** Agrega el manhwa si no estaba. Devuelve true si se agregó. */
    suspend fun ensure(
        ctx: Context, title: String, coverUrl: String?, status: String = Status.READING, info: SourceInfo.Info? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val clean = title.trim()
        if (clean.isEmpty()) return@withContext false
        val dao = AppDatabase.get(ctx).dao()
        if (contains(ctx, clean)) {
            // Ya estaba: si no tenía total de capítulos, se completa con lo que dice la fuente.
            if (info != null && info.total > 0) {
                val all = dao.all()
                val old = all.firstOrNull { it.titleKey == titleKeyOf(clean) } ?: ReaderProgress.match(all, clean)
                if (old != null && old.totalChapters == 0) dao.update(old.copy(totalChapters = info.total))
            }
            return@withContext false
        }
        dao.insert(
            Manhwa(
                title = clean, titleKey = titleKeyOf(clean), coverUrl = coverUrl, status = status,
                totalChapters = info?.total ?: 0, type = info?.type ?: Types.MANHWA
            )
        ) > 0L
    }
}
