package com.manhwatracker.app.data

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Guarda los repositorios que añade el usuario y baja sus índices y APK. La app no trae ningún repositorio. */
object SourcesStore {
    private fun p(c: Context) = c.getSharedPreferences("sources", Context.MODE_PRIVATE)

    fun repos(c: Context): List<String> = p(c).getString("repos", "").orEmpty().split('\n').filter { it.isNotBlank() }

    private fun setRepos(c: Context, l: List<String>) {
        p(c).edit().putString("repos", l.joinToString("\n")).apply()
    }

    fun add(c: Context, url: String) = setRepos(c, Sources.addRepo(repos(c), url))

    fun remove(c: Context, url: String) {
        setRepos(c, repos(c).filter { it != url })
        cacheFile(c, url).delete()
    }

    fun showNsfw(c: Context): Boolean = p(c).getBoolean("nsfw", false)
    fun setShowNsfw(c: Context, v: Boolean) = p(c).edit().putBoolean("nsfw", v).apply()

    private fun sha(s: String): String =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }.take(16)

    private fun cacheFile(c: Context, url: String): File =
        File(File(c.filesDir, "sources").apply { mkdirs() }, sha(url) + ".json")

    /** El índice guardado de un repositorio (vacío si todavía no se ha bajado). */
    fun cached(c: Context, url: String): List<RepoExt> = runCatching { Sources.parseIndex(cacheFile(c, url).readText()) }.getOrDefault(emptyList())

    fun fetchedAt(c: Context, url: String): Long = cacheFile(c, url).takeIf { it.exists() }?.lastModified() ?: 0L

    private fun open(url: String, timeout: Int): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = timeout
            readTimeout = timeout
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "ManhwaTracker")
        }

    class FetchResult(val list: List<RepoExt>?, val error: String?)

    /** Baja el índice de [base], comprueba que sea válido y lo guarda. No guarda nada si falla. */
    suspend fun refresh(c: Context, base: String): FetchResult = withContext(Dispatchers.IO) {
        try {
            val conn = open(Sources.indexUrl(base), 20_000)
            if (conn.responseCode != 200) {
                val code = conn.responseCode
                conn.disconnect()
                return@withContext FetchResult(null, if (code == 404) "No encontré un repositorio en esa dirección (error 404)." else "El servidor respondió con el error $code.")
            }
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            val list = try { Sources.parseIndex(text) } catch (e: Exception) { return@withContext FetchResult(null, "Esa dirección no es un repositorio de extensiones válido.") }
            if (list.isEmpty()) return@withContext FetchResult(null, "El repositorio está vacío.")
            cacheFile(c, base).writeText(text)
            FetchResult(list, null)
        } catch (e: Exception) {
            FetchResult(null, "No pude conectar. Revisa tu internet y la dirección.")
        }
    }

    /** Baja el APK de una extensión a la carpeta temporal. [onProgress] va de 0 a 1 (o -1 si no se sabe el tamaño). */
    suspend fun downloadApk(c: Context, base: String, e: RepoExt, onProgress: (Float) -> Unit): File? = withContext(Dispatchers.IO) {
        try {
            val dir = File(c.cacheDir, "extensions").apply { mkdirs() }
            val out = File(dir, e.pkg + ".apk")
            val conn = open(Sources.apkUrl(base, e), 20_000)
            if (conn.responseCode != 200) { conn.disconnect(); return@withContext null }
            val total = conn.contentLengthLong
            var done = 0L
            conn.inputStream.use { input ->
                out.outputStream().use { o ->
                    val buf = ByteArray(32 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        o.write(buf, 0, n)
                        done += n
                        onProgress(if (total > 0) done.toFloat() / total else -1f)
                    }
                }
            }
            conn.disconnect()
            // Un APK es un zip: empieza por "PK". Así se descartan páginas de error guardadas por equivocación.
            val ok = out.length() > 1_000 && out.inputStream().use { it.read() == 'P'.code && it.read() == 'K'.code } &&
                (total <= 0 || out.length() == total)
            if (!ok) { out.delete(); null } else out
        } catch (ex: Exception) {
            null
        }
    }

    /** versionCode de la extensión instalada, o null si no está instalada. */
    fun installedCode(c: Context, pkg: String): Long? = try {
        PackageInfoCompat.getLongVersionCode(c.packageManager.getPackageInfo(pkg, 0))
    } catch (e: PackageManager.NameNotFoundException) {
        null
    } catch (e: Exception) {
        null
    }
}
