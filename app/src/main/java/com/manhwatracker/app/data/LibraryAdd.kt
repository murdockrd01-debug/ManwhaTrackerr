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

    /** Agrega el manhwa si no estaba. Devuelve true si se agregó. */
    suspend fun ensure(ctx: Context, title: String, coverUrl: String?, status: String = Status.READING): Boolean = withContext(Dispatchers.IO) {
        val clean = title.trim()
        if (clean.isEmpty() || contains(ctx, clean)) return@withContext false
        AppDatabase.get(ctx).dao().insert(Manhwa(title = clean, titleKey = titleKeyOf(clean), coverUrl = coverUrl, status = status)) > 0L
    }
}
