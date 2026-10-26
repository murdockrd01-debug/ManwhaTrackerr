package com.manhwatracker.app

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * Iconos de la app a elegir. Cada uno es un "alias" del manifiesto; solo uno está activo a la vez
 * y cambiarlo no reinicia ni borra nada de la app.
 */
object AppIcons {

    class Option(val id: String, val label: String, val className: String, val bg: Int, val fg: Int)

    val all = listOf(
        Option("classic", "Clásico", "IconClassic", R.drawable.ic_launcher_background, R.drawable.ic_launcher_foreground),
        Option("night", "Medianoche", "IconNight", R.drawable.ic_bg_night, R.drawable.ic_launcher_foreground),
        Option("ocean", "Océano", "IconOcean", R.drawable.ic_bg_ocean, R.drawable.ic_launcher_foreground),
        Option("sunset", "Atardecer", "IconSunset", R.drawable.ic_bg_sunset, R.drawable.ic_launcher_foreground),
        Option("words", "Palabras", "IconWords", R.drawable.ic_bg_words, R.drawable.ic_fg_bubble),
        Option("manga", "Manga", "IconManga", R.drawable.ic_bg_manga, R.drawable.ic_fg_panel)
    )

    private fun component(context: Context, o: Option) =
        ComponentName(context.packageName, "com.manhwatracker.app.${o.className}")

    /** El icono activo ahora. Si el sistema aún no guardó ninguno, vale el clásico (el del manifiesto). */
    fun current(context: Context): Option {
        val pm = context.packageManager
        return all.firstOrNull { o ->
            when (pm.getComponentEnabledSetting(component(context, o))) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED -> false
                else -> o.id == "classic"
            }
        } ?: all.first()
    }

    /** Activa primero el nuevo y después apaga los demás, para no quedarse nunca sin icono. */
    fun set(context: Context, option: Option) {
        val pm = context.packageManager
        pm.setComponentEnabledSetting(
            component(context, option),
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP
        )
        for (o in all) {
            if (o.id == option.id) continue
            pm.setComponentEnabledSetting(
                component(context, o),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
        }
    }
}
