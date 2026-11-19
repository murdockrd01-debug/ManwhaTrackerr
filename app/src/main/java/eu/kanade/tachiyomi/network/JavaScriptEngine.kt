package eu.kanade.tachiyomi.network

import android.content.Context
import app.cash.quickjs.QuickJs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Motor de JavaScript (QuickJS) para las extensiones que lo necesitan. */
@Suppress("UNUSED", "UNUSED_PARAMETER", "UNCHECKED_CAST")
class JavaScriptEngine(context: Context) {
    /**
     * Evalúa código JavaScript y devuelve el resultado como tipo primitivo (String, Int...).
     * @since extensions-lib 1.4
     */
    suspend fun <T> evaluate(script: String): T = withContext(Dispatchers.Default) {
        QuickJs.create().use { it.evaluate(script) as T }
    }
}
