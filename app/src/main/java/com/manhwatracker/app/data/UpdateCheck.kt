package com.manhwatracker.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri
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
}
