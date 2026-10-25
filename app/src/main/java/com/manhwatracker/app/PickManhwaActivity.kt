package com.manhwatracker.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelProvider
import com.manhwatracker.app.data.Prefs

/**
 * Ventana pequeña que se abre desde la notificación ("Cambiar manhwa"):
 * eliges de qué manhwa son las palabras que vas a guardar y vuelves a la app donde leías.
 */
class PickManhwaActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val vm = ViewModelProvider(this)[MainViewModel::class.java]
        val app = applicationContext

        setContent {
            ManhwaTheme {
                val manhwas by vm.items.collectAsState()
                ManhwaPickerDialog(
                    manhwas = manhwas,
                    current = Prefs.lastManhwa(app),
                    onPick = { m ->
                        val chapter = m?.takeIf { it.lastChapter > 0 }?.lastChapter?.let {
                            if (it % 1.0 == 0.0) it.toInt().toString() else it.toString()
                        }
                        Prefs.setLast(app, m?.title, chapter)
                        QuickAdd.show(app, if (m != null) "Guardando en: ${m.title}" else "Sin manhwa asignado")
                        finish()
                    },
                    onDismiss = { finish() }
                )
            }
        }
    }
}
