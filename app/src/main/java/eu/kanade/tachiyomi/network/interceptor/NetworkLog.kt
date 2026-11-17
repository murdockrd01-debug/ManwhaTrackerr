package eu.kanade.tachiyomi.network.interceptor

import okhttp3.Interceptor
import okhttp3.Response
import java.util.ArrayDeque

/** Apunta las últimas peticiones de las extensiones (sin contenido) para poder diagnosticar errores. */
object NetworkLog {
    private val items = ArrayDeque<String>()

    @Synchronized fun add(line: String) {
        items.addLast(line.take(300))
        while (items.size > 10) items.removeFirst()
    }

    @Synchronized fun dump(): String = if (items.isEmpty()) "(sin peticiones)" else items.joinToString("\n")
}

class NetworkLogInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val req = chain.request()
        val t = System.currentTimeMillis()
        try {
            val res = chain.proceed(req)
            val type = res.header("Content-Type").orEmpty()
            val enc = res.header("Content-Encoding")?.let { " enc=$it" }.orEmpty()
            NetworkLog.add("${req.method} ${req.url} -> ${res.code} $type$enc ${System.currentTimeMillis() - t}ms")
            return res
        } catch (e: Exception) {
            NetworkLog.add("${req.method} ${req.url} -> ERROR ${e.javaClass.simpleName}: ${e.message}")
            throw e
        }
    }
}
