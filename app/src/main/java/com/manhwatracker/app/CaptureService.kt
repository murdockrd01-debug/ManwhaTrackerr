package com.manhwatracker.app

import android.accessibilityservice.AccessibilityButtonController
import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Display
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.manhwatracker.app.data.Prefs
import com.manhwatracker.app.data.WordRepo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Captura rápida de texto. Se activa en Ajustes de Android > Accesibilidad.
 * Al pulsar el botón de accesibilidad (o las dos teclas de volumen, o el mosaico del panel rápido):
 * toma una captura de la pantalla, lee el texto en el teléfono y deja tocar los globos que quieres guardar.
 * No lee eventos ni contenido de otras apps: solo la captura que tú pides.
 */
class CaptureService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: CaptureService? = null

        fun isEnabled(c: Context): Boolean {
            val list = Settings.Secure.getString(c.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
            return list.split(':').any { it.startsWith(c.packageName + "/") && it.endsWith("CaptureService") }
        }
    }

    private val main = Handler(Looper.getMainLooper())
    private var overlay: CaptureOverlay? = null
    private var bubble: CaptureBubble? = null

    override fun onServiceConnected() {
        instance = this
        refreshBubble()
        if (Build.VERSION.SDK_INT >= 26) {
            accessibilityButtonController.registerAccessibilityButtonCallback(
                object : AccessibilityButtonController.AccessibilityButtonCallback() {
                    override fun onClicked(controller: AccessibilityButtonController) {
                        start()
                    }
                }
            )
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        close()
        hideBubble()
        return super.onUnbind(intent)
    }

    private fun toast(text: String) = Toast.makeText(applicationContext, tr(text), Toast.LENGTH_SHORT).show()

    /** Muestra u oculta la burbuja según el ajuste. */
    fun refreshBubble() {
        if (Prefs.bubble(this) && Prefs.studyEnabled(this) != false) showBubble() else hideBubble()
    }

    private fun showBubble() {
        if (bubble != null) return
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = android.view.Gravity.TOP or android.view.Gravity.START }
        val m = resources.displayMetrics
        val view = CaptureBubble(
            this, lp, wm,
            onTap = {
                startActivity(Intent(this, QuickInputActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            },
            onLong = { start() }
        )
        val savedX = Prefs.bubbleX(this)
        val savedY = Prefs.bubbleY(this)
        lp.x = if (savedX >= 0) savedX else m.widthPixels - view.sizePx
        lp.y = if (savedY >= 0) savedY else (m.heightPixels * 0.4f).toInt()
        try {
            wm.addView(view, lp)
            bubble = view
        } catch (e: Exception) {
            // Sin poder mostrarla, no pasa nada: la captura sigue funcionando.
        }
    }

    private fun hideBubble() {
        val v = bubble ?: return
        bubble = null
        runCatching { (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(v) }
    }

    /** Cierra el panel de notificaciones (si está abierto) y toma la captura. */
    fun start() {
        if (Build.VERSION.SDK_INT < 30) {
            toast("Esta función necesita Android 11 o más nuevo")
            return
        }
        if (Prefs.studyEnabled(this) == false) {
            toast("Activa el estudio de inglés en Ajustes para guardar palabras")
            return
        }
        if (overlay != null) return
        performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
        main.postDelayed({ capture() }, 450)
    }

    @RequiresApi(30)
    private fun capture() {
        takeScreenshot(Display.DEFAULT_DISPLAY, ContextCompat.getMainExecutor(this), object : TakeScreenshotCallback {
            override fun onSuccess(screenshot: ScreenshotResult) {
                val hw = Bitmap.wrapHardwareBuffer(screenshot.hardwareBuffer, screenshot.colorSpace)
                val bmp = hw?.copy(Bitmap.Config.ARGB_8888, false)
                hw?.recycle()
                screenshot.hardwareBuffer.close()
                if (bmp == null) toast("No pude tomar la captura") else recognize(bmp)
            }

            override fun onFailure(errorCode: Int) {
                toast("No pude tomar la captura (la app puede bloquearla)")
            }
        })
    }

    private fun recognize(bmp: Bitmap) {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            .process(InputImage.fromBitmap(bmp, 0))
            .addOnSuccessListener { text ->
                val items = text.textBlocks.mapNotNull { b ->
                    val rect = b.boundingBox ?: return@mapNotNull null
                    val words = b.lines.flatMap { it.elements }.mapNotNull { e ->
                        e.boundingBox?.let { r -> CaptureOverlay.Piece(r, e.text) }
                    }
                    CaptureOverlay.Piece(rect, b.text.replace('\n', ' ').trim(), words)
                }
                if (items.isEmpty()) {
                    toast("No encontré texto en la pantalla")
                    bmp.recycle()
                } else {
                    show(bmp, items)
                }
            }
            .addOnFailureListener {
                toast("No pude leer el texto")
                bmp.recycle()
            }
    }

    private fun show(bmp: Bitmap, items: List<CaptureOverlay.Piece>) {
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val view = CaptureOverlay(this, bmp, items, onSave = { picked -> save(picked) }, onClose = { close() })
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        try {
            wm.addView(view, lp)
            overlay = view
        } catch (e: Exception) {
            toast("No pude mostrar la pantalla de selección")
            bmp.recycle()
        }
    }

    private fun close() {
        val v = overlay ?: return
        overlay = null
        runCatching { (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(v) }
        v.release()
    }

    /** Cada elemento elegido se guarda como una palabra o frase aparte, con el último manhwa y capítulo. */
    private fun save(picked: List<String>) {
        close()
        val app = applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            var added = 0
            var already = 0
            for (raw in picked) {
                val text = tidy(raw)
                if (text.isEmpty()) continue
                val r = WordRepo.save(app, text, Prefs.lastManhwa(app), Prefs.lastChapter(app), null)
                if (r.added) added++ else already++
            }
            withContext(Dispatchers.Main) {
                toast(
                    when {
                        added == 1 && already == 0 -> "Guardada 1 palabra o frase"
                        added > 0 -> "Guardadas: $added"
                        else -> "Ya las tenías guardadas"
                    }
                )
            }
        }
    }

    /** Los globos de manhwa suelen venir en MAYÚSCULAS: se pasan a minúsculas normales. */
    private fun tidy(raw: String): String {
        val t = raw.trim().trim('"', '“', '”', ',', ';').trim()
        val letters = t.filter { it.isLetter() }
        if (letters.length > 1 && letters == letters.uppercase()) {
            val lower = t.lowercase()
            return if (t.contains(' ')) lower.replaceFirstChar { it.uppercase() } else lower
        }
        return t
    }
}
