package com.manhwatracker.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Escucha lo que dices en inglés con el reconocimiento de voz del teléfono. */
class VoiceInput(private val context: Context) {
    var listening by mutableStateOf(false)
        private set

    private var recognizer: SpeechRecognizer? = null

    fun available(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    /** [onResult] recibe las alternativas (la mejor primero); [onError] un mensaje para mostrar. */
    fun start(onResult: (List<String>) -> Unit, onError: (String) -> Unit) {
        stop()
        val r = try {
            SpeechRecognizer.createSpeechRecognizer(context)
        } catch (e: Exception) {
            onError("Tu teléfono no tiene reconocimiento de voz.")
            return
        }
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}

            override fun onResults(results: Bundle?) {
                listening = false
                val list = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                if (list.isEmpty()) onError("No te escuché bien. Inténtalo otra vez.") else onResult(list)
            }

            override fun onError(error: Int) {
                listening = false
                onError(
                    when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                            "No te escuché bien. Inténtalo otra vez."
                        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER ->
                            "El reconocimiento de voz de tu teléfono necesita internet."
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                            "Necesito permiso para usar el micrófono."
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "El micrófono está ocupado. Inténtalo otra vez."
                        else -> "No pude escuchar. Inténtalo otra vez."
                    }
                )
            }
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
        listening = true
        try {
            r.startListening(intent)
        } catch (e: Exception) {
            listening = false
            onError("No pude escuchar. Inténtalo otra vez.")
        }
    }

    fun stop() {
        recognizer?.let { runCatching { it.destroy() } }
        recognizer = null
        listening = false
    }
}
