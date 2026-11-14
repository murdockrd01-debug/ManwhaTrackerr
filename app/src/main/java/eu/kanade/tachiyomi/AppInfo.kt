package eu.kanade.tachiyomi

import com.manhwatracker.app.BuildConfig

/** Datos de la app para las extensiones (se usan, por ejemplo, en el User-Agent). */
@Suppress("Unused")
object AppInfo {
    fun getVersionCode(): Int = BuildConfig.VERSION_CODE
    fun getVersionName(): String = BuildConfig.VERSION_NAME
}
