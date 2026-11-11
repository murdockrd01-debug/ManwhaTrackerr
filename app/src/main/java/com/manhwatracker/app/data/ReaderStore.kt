package com.manhwatracker.app.data

import android.content.Context

/** Guarda por dónde ibas en cada capítulo y qué capítulos ya terminaste. */
object ReaderStore {
    private fun p(c: Context) = c.getSharedPreferences("reader_store", Context.MODE_PRIVATE)

    fun last(c: Context): ReaderLogic.Place? = ReaderLogic.decodePlace(p(c).getString("last", null))

    fun setLast(c: Context, place: ReaderLogic.Place) {
        p(c).edit().putString("last", ReaderLogic.encodePlace(place)).apply()
    }

    fun position(c: Context, key: String): Int = p(c).getInt("pos:$key", 0)

    fun setPosition(c: Context, key: String, page: Int) {
        p(c).edit().putInt("pos:$key", page).apply()
    }

    fun done(c: Context, key: String): Boolean = p(c).getBoolean("done:$key", false)

    fun setDone(c: Context, key: String, value: Boolean) {
        p(c).edit().putBoolean("done:$key", value).apply()
    }
}
