package com.manhwatracker.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/** Consulta GitHub Releases para saber si hay una versión más nueva instalada que la actual. */
object UpdateCheck {
    fun installedVersion(c: Context): String =
        runCatching { c.packageManager.getPackageInfo(c.packageName, 0).versionName }.getOrNull() ?: "0"

    /** La versión nueva (ej. "v2.7") o null si ya tienes la última o no hay internet. */
    suspend fun newerVersion(c: Context): String? = withContext(Dispatchers.IO) {
        try {
            val conn = (URL(Update.LATEST_API).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 8_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "ManhwaTracker")
            }
            if (conn.responseCode != 200) { conn.disconnect(); return@withContext null }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            val tag = Update.tagFromJson(body) ?: return@withContext null
            if (Update.isNewer(tag, installedVersion(c))) tag else null
        } catch (e: Exception) {
            null
        }
    }

    fun openDownload(c: Context) {
        runCatching {
            c.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Update.APK_URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    enum class InstallResult { STARTED, NEEDS_PERMISSION, FAILED }

    /** Baja el APK a la carpeta temporal de la app. [onProgress] va de 0 a 1 (o -1 si no se sabe el tamaño). */
    suspend fun download(c: Context, onProgress: (Float) -> Unit): File? = withContext(Dispatchers.IO) {
        try {
            val dir = File(c.cacheDir, "updates").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val out = File(dir, "ManhwaTracker.apk")
            val conn = (URL(Update.APK_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 20_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "ManhwaTracker")
            }
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
            if (out.length() < 100_000 || (total > 0 && out.length() != total)) { out.delete(); null } else out
        } catch (e: Exception) {
            null
        }
    }

    /** Abre el instalador de Android con el APK descargado. */
    fun install(c: Context, apk: File): InstallResult {
        if (Build.VERSION.SDK_INT >= 26 && !c.packageManager.canRequestPackageInstalls()) {
            runCatching {
                c.startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${c.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
            return InstallResult.NEEDS_PERMISSION
        }
        return try {
            val uri = FileProvider.getUriForFile(c, "${c.packageName}.files", apk)
            c.startActivity(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            InstallResult.STARTED
        } catch (e: Exception) {
            InstallResult.FAILED
        }
    }
}
