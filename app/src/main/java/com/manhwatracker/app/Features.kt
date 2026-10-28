package com.manhwatracker.app

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.manhwatracker.app.data.Prefs

/**
 * Qué partes de la app están activas. Por ahora: el estudio de inglés es opcional,
 * y quien solo quiere un tracker no ve nada de eso.
 */
object Features {
    /** true = se muestran Estudio, Repaso, palabras, etc. */
    var study by mutableStateOf(true)

    /** true = es la primera vez y hay que preguntar qué modo quiere. */
    var needsChoice by mutableStateOf(false)

    /** true = se muestra la guía de inicio (tras elegir el modo, o desde Ajustes). */
    var showIntro by mutableStateOf(false)

    fun load(context: Context) {
        val saved = Prefs.studyEnabled(context)
        when {
            saved != null -> {
                study = saved
                needsChoice = false
            }
            // Quien ya usaba la app antes de esta opción sigue con todo activado, sin preguntar.
            Prefs.hasAnySettings(context) -> {
                study = true
                needsChoice = false
                Prefs.setStudyEnabled(context, true)
            }
            else -> {
                study = true
                needsChoice = true
            }
        }
    }

    fun setStudy(context: Context, on: Boolean) {
        study = on
        needsChoice = false
        Prefs.setStudyEnabled(context, on)

        // "Guardar en vocabulario" del menú de seleccionar texto solo aparece con el estudio activo.
        context.packageManager.setComponentEnabledSetting(
            ComponentName(context, SaveWordActivity::class.java),
            if (on) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )

        // La barra rápida de notificaciones también es parte del estudio.
        if (!on) QuickAdd.hide(context)
        else if (Prefs.quickBar(context)) QuickAdd.show(context)
    }
}
