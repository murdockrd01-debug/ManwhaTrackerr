package com.manhwatracker.app

import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

/**
 * Lee palabras en inglés en voz alta con el texto a voz de Android.
 * No necesita internet ni cuenta, pero el teléfono debe tener una voz en inglés instalada.
 */
class Speaker(context: Context) {

    @Volatile
    private var ready = false

    private var tts: TextToSpeech? = null

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale.US)
                ready = result != null &&
                    result != TextToSpeech.LANG_MISSING_DATA &&
                    result != TextToSpeech.LANG_NOT_SUPPORTED
            }
        }
    }

    /** Devuelve false si todavía no está lista o no hay voz en inglés. */
    fun speak(text: String): Boolean {
        val engine = tts
        if (!ready || engine == null || text.isBlank()) return false
        return engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "manhwa-tracker") != TextToSpeech.ERROR
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ready = false
    }
}

const val SPEAKER_UNAVAILABLE =
    "No pude reproducir el audio. Revisa que tu teléfono tenga una voz en inglés (Ajustes → Texto a voz)."

/** Crea un [Speaker] que vive mientras la pantalla esté abierta y se libera al salir. */
@Composable
fun rememberSpeaker(): Speaker {
    val context = LocalContext.current
    val speaker = remember { Speaker(context) }
    DisposableEffect(speaker) { onDispose { speaker.shutdown() } }
    return speaker
}

/** Botón de audio. [onUnavailable] se llama si no hay voz disponible. */
@Composable
fun SpeakButton(text: String, speaker: Speaker, onUnavailable: () -> Unit) {
    TextButton(onClick = { if (!speaker.speak(text)) onUnavailable() }) {
        Text("🔊 Escuchar")
    }
}
