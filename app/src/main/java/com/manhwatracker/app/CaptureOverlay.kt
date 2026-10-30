package com.manhwatracker.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View

/**
 * Pantalla de selección sobre la captura: cada globo de texto se toca para elegirlo;
 * manteniendo el dedo sobre una palabra se elige solo esa palabra. Abajo: Cancelar y Guardar.
 */
class CaptureOverlay(
    context: Context,
    private var bitmap: Bitmap?,
    private val items: List<Piece>,
    private val onSave: (List<String>) -> Unit,
    private val onClose: () -> Unit
) : View(context) {

    class Piece(val rect: Rect, val text: String, val words: List<Piece> = emptyList())

    private val density = resources.displayMetrics.density
    private val accent = 0xFF7C5CFF.toInt()
    private val selectedBlocks = linkedSetOf<Int>()
    private val selectedWords = linkedSetOf<Pair<Int, Int>>()

    private val dim = Paint().apply { color = 0x99000000.toInt() }
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 2f * density; color = Color.WHITE
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = 16f * density; textAlign = Paint.Align.CENTER; isFakeBoldText = true
    }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = 14f * density; textAlign = Paint.Align.CENTER
    }

    private val cancelBtn = RectF()
    private val saveBtn = RectF()

    private fun sx() = if (bitmap == null) 1f else width.toFloat() / bitmap!!.width
    private fun sy() = if (bitmap == null) 1f else height.toFloat() / bitmap!!.height
    private fun scaled(r: Rect) = RectF(r.left * sx(), r.top * sy(), r.right * sx(), r.bottom * sy())

    private val gestures = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true

        override fun onSingleTapUp(e: MotionEvent): Boolean {
            if (cancelBtn.contains(e.x, e.y)) { onClose(); return true }
            if (saveBtn.contains(e.x, e.y)) {
                if (count() > 0) onSave(picked())
                return true
            }
            val i = items.indexOfFirst { scaled(it.rect).contains(e.x, e.y) }
            if (i >= 0) {
                if (!selectedBlocks.add(i)) selectedBlocks.remove(i)
                selectedWords.removeAll { it.first == i }
                invalidate()
            }
            return true
        }

        override fun onLongPress(e: MotionEvent) {
            for ((bi, b) in items.withIndex()) {
                val wi = b.words.indexOfFirst { scaled(it.rect).inflate(6f * density).contains(e.x, e.y) }
                if (wi >= 0) {
                    val key = bi to wi
                    if (!selectedWords.add(key)) selectedWords.remove(key)
                    selectedBlocks.remove(bi)
                    performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                    invalidate()
                    return
                }
            }
        }
    })

    private fun RectF.inflate(by: Float) = RectF(left - by, top - by, right + by, bottom + by)

    private fun count() = selectedBlocks.size + selectedWords.size

    /** Los elegidos, de arriba abajo. */
    private fun picked(): List<String> {
        val all = mutableListOf<Pair<Int, String>>()
        selectedBlocks.forEach { all += items[it].rect.top to items[it].text }
        selectedWords.forEach { (b, w) ->
            val p = items[b].words[w]
            all += p.rect.top to p.text.trim('.', ',', '!', '?', '"', '“', '”', ':', ';')
        }
        return all.sortedBy { it.first }.map { it.second }
    }

    override fun onDraw(canvas: Canvas) {
        bitmap?.let { canvas.drawBitmap(it, null, Rect(0, 0, width, height), null) }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), dim)

        val r = 10f * density
        items.forEachIndexed { i, b ->
            val rect = scaled(b.rect)
            if (i in selectedBlocks) {
                fillPaint.color = (accent and 0x00FFFFFF) or 0xAA000000.toInt()
                canvas.drawRoundRect(rect, r, r, fillPaint)
                outline.color = Color.WHITE
            } else {
                fillPaint.color = 0x33FFFFFF
                canvas.drawRoundRect(rect, r, r, fillPaint)
                outline.color = 0xCCFFFFFF.toInt()
            }
            canvas.drawRoundRect(rect, r, r, outline)
            b.words.forEachIndexed { wi, w ->
                if ((i to wi) in selectedWords) {
                    fillPaint.color = (accent and 0x00FFFFFF) or 0xCC000000.toInt()
                    canvas.drawRoundRect(scaled(w.rect), 6f * density, 6f * density, fillPaint)
                }
            }
        }

        val top = 48f * density
        canvas.drawText(tr("Toca un globo · mantén sobre una palabra para elegir solo esa"), width / 2f, top, hintPaint)

        val h = 52f * density
        val pad = 16f * density
        val y = height - h - 28f * density
        val half = (width - pad * 3) / 2f
        cancelBtn.set(pad, y, pad + half, y + h)
        saveBtn.set(pad * 2 + half, y, pad * 2 + half * 2, y + h)
        fillPaint.color = 0xFF444444.toInt()
        canvas.drawRoundRect(cancelBtn, h / 2, h / 2, fillPaint)
        fillPaint.color = if (count() > 0) accent else 0xFF666666.toInt()
        canvas.drawRoundRect(saveBtn, h / 2, h / 2, fillPaint)
        val base = y + h / 2 + textPaint.textSize / 3
        canvas.drawText(tr("Cancelar"), cancelBtn.centerX(), base, textPaint)
        canvas.drawText(tr("Guardar") + if (count() > 0) " (${count()})" else "", saveBtn.centerX(), base, textPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean = gestures.onTouchEvent(event)

    fun release() {
        bitmap?.recycle()
        bitmap = null
    }
}
