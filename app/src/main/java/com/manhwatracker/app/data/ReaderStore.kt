package com.manhwatracker.app.data

import android.content.Context

/** Guarda por dónde ibas en cada capítulo y qué capítulos ya terminaste. */
object ReaderStore {
    private fun p(c: Context) = c.getSharedPreferences("reader_store", Context.MODE_PRIVATE)

    fun last(c: Context): ReaderLogic.Place? = ReaderLogic.decodePlace(p(c).getString("last", null))

    fun setLast(c: Context, place: ReaderLogic.Place) {
        p(c).edit().putString("last", ReaderLogic.encodePlace(place)).apply()
    }

    /** Carpeta de descargas de Komikku elegida por el usuario (para "Continuar leyendo"); null si no hay. */
    fun downloads(c: Context): String? = p(c).getString("downloads", null)

    fun setDownloads(c: Context, uri: String?) {
        p(c).edit().putString("downloads", uri).apply()
    }

    /** Capítulo empezado y no terminado. */
    fun inProgress(c: Context, key: String): Boolean = position(c, key) > 0 && !done(c, key)

    fun position(c: Context, key: String): Int = p(c).getInt("pos:$key", 0)

    fun setPosition(c: Context, key: String, page: Int) {
        p(c).edit().putInt("pos:$key", page).apply()
    }

    fun done(c: Context, key: String): Boolean = p(c).getBoolean("done:$key", false)

    fun setDone(c: Context, key: String, value: Boolean) {
        p(c).edit().putBoolean("done:$key", value).apply()
    }

    /** Índice de descargas guardado (ver [ReaderIndex]) y cuándo se hizo. */
    fun index(c: Context): List<ReaderIndex.Entry> = ReaderIndex.decode(p(c).getString("index", null))
    fun indexAt(c: Context): Long = p(c).getLong("index_at", 0L)
    fun setIndex(c: Context, encoded: String, at: Long) {
        p(c).edit().putString("index", encoded).putLong("index_at", at).apply()
    }
}
