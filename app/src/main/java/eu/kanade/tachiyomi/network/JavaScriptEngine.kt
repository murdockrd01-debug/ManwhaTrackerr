package eu.kanade.tachiyomi.network

import android.content.Context

/** Motor de JavaScript para extensiones. Todavía no incluido: las que lo necesiten darán este error. */
@Suppress("UNUSED", "UNUSED_PARAMETER")
class JavaScriptEngine(context: Context) {
    suspend fun <T> evaluate(script: String): T =
        throw UnsupportedOperationException("Esta extensión necesita un motor de JavaScript que la app aún no tiene")
}
