package com.manhwatracker.app

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import com.manhwatracker.app.data.Prefs

/**
 * Burbuja flotante: se puede arrastrar; tocarla abre la ventanita para escribir una palabra,
 * mantenerla pulsada toma la captura de pantalla. Vive dentro del servicio de captura (no usa notificación).
 */
@SuppressLint("ViewConstructor", "ClickableViewAccessibility")
class CaptureBubble(
    context: Context,
    private val lp: WindowManager.LayoutParams,
    private val wm: WindowManager,
    private val onTap: () -> Unit,
    private val onLong: () -> Unit
) : View(context) {

    private val d = resources.displayMetrics.density
    val sizePx = (52 * d).toInt()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val icon = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 24f * d; textAlign = Paint.Align.CENTER }
    private val handler = Handler(Looper.getMainLooper())
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var startX = 0
    private var startY = 0
    private var moved = false
    private var longFired = false
    private val longRun = Runnable { longFired = true; performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS); onLong() }

    override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(sizePx, sizePx)

    override fun onDraw(c: Canvas) {
        val r = sizePx / 2f
        paint.shader = RadialGradient(r, r * 0.7f, r * 1.2f, intArrayOf(0xFFA48BFF.toInt(), 0xFF6C4BEA.toInt()), null, Shader.TileMode.CLAMP)
        paint.setShadowLayer(4f * d, 0f, 2f * d, 0x66000000)
        c.drawCircle(r, r, r - 4f * d, paint)
        c.drawText("✍", r, r + 8f * d, icon)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.rawX; downY = e.rawY
                startX = lp.x; startY = lp.y
                moved = false; longFired = false
                handler.postDelayed(longRun, 650)
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = e.rawX - downX
                val dy = e.rawY - downY
                if (!moved && (kotlin.math.abs(dx) > slop || kotlin.math.abs(dy) > slop)) {
                    moved = true
                    handler.removeCallbacks(longRun)
                }
                if (moved) {
                    lp.x = (startX + dx).toInt()
                    lp.y = (startY + dy).toInt()
                    runCatching { wm.updateViewLayout(this, lp) }
                }
            }
            MotionEvent.ACTION_UP -> {
                handler.removeCallbacks(longRun)
                if (moved) {
                    // Se pega al borde más cercano y se recuerda dónde quedó.
                    val screenW = resources.displayMetrics.widthPixels
                    lp.x = if (lp.x + sizePx / 2 < screenW / 2) 0 else screenW - sizePx
                    runCatching { wm.updateViewLayout(this, lp) }
                    Prefs.setBubblePos(context, lp.x, lp.y)
                } else if (!longFired) {
                    onTap()
                }
            }
            MotionEvent.ACTION_CANCEL -> handler.removeCallbacks(longRun)
        }
        return true
    }
}
