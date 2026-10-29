package com.manhwatracker.app

import android.content.Context
import android.content.Intent
import com.manhwatracker.app.data.Prefs

/** Abre tu app lectora (Komikku, Mihon, Mangayomi…) para buscar un título. */
object ReaderLink {
    class Reader(val pkg: String, val name: String)

    val known = listOf(
        Reader("app.komikku", "Komikku"),
        Reader("app.komikku.beta", "Komikku Beta"),
        Reader("app.mihon", "Mihon"),
        Reader("app.mihon.dev", "Mihon Preview"),
        Reader("eu.kanade.tachiyomi", "Tachiyomi"),
        Reader("eu.kanade.tachiyomi.sy", "TachiyomiSY"),
        Reader("com.kodjodevf.mangayomi", "Mangayomi")
    )

    fun installed(context: Context): List<Reader> =
        known.filter { context.packageManager.getLaunchIntentForPackage(it.pkg) != null }

    /** La app elegida en Ajustes, o la primera instalada si está en automático. */
    fun target(context: Context): Reader? {
        val inst = installed(context)
        val chosen = Prefs.reader(context)
        return inst.firstOrNull { it.pkg == chosen } ?: inst.firstOrNull()
    }

    /** Devuelve false si no hay ninguna app lectora instalada. */
    fun open(context: Context, title: String): Boolean {
        val r = target(context) ?: return false
        val launch = context.packageManager.getLaunchIntentForPackage(r.pkg) ?: return false
        // Primero la búsqueda directa con el título (Mihon y Komikku la entienden).
        try {
            context.startActivity(
                Intent("eu.kanade.tachiyomi.SEARCH").setPackage(r.pkg).putExtra("query", title)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return true
        } catch (e: Exception) {
            // Si no la acepta, se abre la app normalmente.
        }
        return try {
            context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (e: Exception) {
            false
        }
    }
}
