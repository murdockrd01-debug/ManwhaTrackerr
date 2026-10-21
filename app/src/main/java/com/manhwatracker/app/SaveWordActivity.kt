package com.manhwatracker.app

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.manhwatracker.app.data.Prefs
import com.manhwatracker.app.data.WordRepo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Aparece en el menú de "seleccionar texto" de otras apps como "Guardar en vocabulario".
 * No muestra pantalla: guarda el texto y avisa con un mensaje corto.
 */
class SaveWordActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val text = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()?.trim().orEmpty()
        if (text.isEmpty()) {
            finish()
            return
        }

        val app = applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            val result = WordRepo.save(app, text, Prefs.lastManhwa(app), Prefs.lastChapter(app), null)
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    app,
                    if (result.added) "Guardada: $text" else "Ya la tenías guardada",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        finish()
    }
}
