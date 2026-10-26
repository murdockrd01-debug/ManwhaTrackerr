package com.manhwatracker.app

import android.content.Context
import android.content.Intent

/** Abre Komikku (o Mihon) para buscar un manhwa, sin salir del flujo de lectura. */
object KomikkuLink {
    private val packages = listOf("app.komikku", "app.komikku.beta", "app.mihon")

    /** Devuelve false si no hay ninguna de esas apps instalada. */
    fun open(context: Context, title: String): Boolean {
        val pm = context.packageManager
        for (pkg in packages) {
            val launch = pm.getLaunchIntentForPackage(pkg) ?: continue

            // Primero se intenta la búsqueda directa con el título (Mihon y Komikku la entienden).
            try {
                context.startActivity(
                    Intent("eu.kanade.tachiyomi.SEARCH")
                        .setPackage(pkg)
                        .putExtra("query", title)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                return true
            } catch (e: Exception) {
                // Si no la acepta, se abre la app normalmente.
            }
            try {
                context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return true
            } catch (e: Exception) {
                // pasa a la siguiente
            }
        }
        return false
    }
}
