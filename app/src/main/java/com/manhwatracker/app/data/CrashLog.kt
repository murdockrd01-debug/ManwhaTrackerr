package com.manhwatracker.app.data

import android.content.Context
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Guarda los errores de la app en un archivo para poder verlos y compartirlos desde Ajustes. */
object CrashLog {
    private const val FILE = "errores.log"
    const val MAX_CHARS = 24_000
    private const val SEP = "\n=====\n"

    private fun file(c: Context) = File(c.filesDir, FILE)

    fun install(app: Context) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                add(app, "CIERRE INESPERADO en ${thread.name}", error)
                Prefs.setCrashPending(app, true)
            } catch (_: Throwable) {
            }
            previous?.uncaughtException(thread, error)
        }
    }

    /** Anota un error que la app atrapó pero que conviene conocer. */
    fun note(c: Context, what: String, error: Throwable) {
        runCatching { add(c.applicationContext, what, error) }
    }

    private fun add(c: Context, what: String, error: Throwable) {
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val version = runCatching { c.packageManager.getPackageInfo(c.packageName, 0).versionName }.getOrNull()
        val entry = "$stamp · v$version · ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.SDK_INT}\n" +
            "$what\n${error.stackTraceToString().take(4_000)}"
        val f = file(c)
        val old = if (f.exists()) f.readText() else ""
        f.writeText(trim(old + (if (old.isEmpty()) "" else SEP) + entry, MAX_CHARS))
    }

    /** Si el texto es muy largo, deja solo lo más reciente, empezando en un registro completo. */
    fun trim(text: String, max: Int): String {
        if (text.length <= max) return text
        val tail = text.takeLast(max)
        val cut = tail.indexOf(SEP)
        return if (cut >= 0) tail.substring(cut + SEP.length) else tail
    }

    fun read(c: Context): String = file(c).takeIf { it.exists() }?.readText().orEmpty()

    fun count(c: Context): Int = read(c).let { if (it.isBlank()) 0 else it.split(SEP).size }

    fun clear(c: Context) {
        file(c).delete()
        Prefs.setCrashPending(c, false)
    }
}
