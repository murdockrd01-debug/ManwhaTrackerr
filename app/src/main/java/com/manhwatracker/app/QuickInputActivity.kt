package com.manhwatracker.app

import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.manhwatracker.app.data.Prefs
import com.manhwatracker.app.data.WordRepo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Ventanita para escribir una palabra o frase mientras lees (se abre al tocar la burbuja).
 * Deja ver la app de lectura detrás, así no tienes que acordarte del texto.
 */
class QuickInputActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val d = resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()
        val dark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val bg = if (dark) 0xFF1F1B2E.toInt() else Color.WHITE
        val fg = if (dark) Color.WHITE else 0xFF1B1530.toInt()
        val sub = if (dark) 0xFFB8B3CC.toInt() else 0xFF6B6680.toInt()
        val accent = 0xFF7C5CFF.toInt()

        window.setBackgroundDrawableResource(android.R.color.transparent)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE or WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        val manhwa = Prefs.lastManhwa(this)
        val chapter = Prefs.lastChapter(this)

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(12))
            background = GradientDrawable().apply { setColor(bg); cornerRadius = dp(22).toFloat() }
            elevation = dp(8).toFloat()
        }
        val where = TextView(this).apply {
            text = tr(if (manhwa != null) "Guardando en: $manhwa" else "Sin manhwa asignado") +
                (if (manhwa != null && !chapter.isNullOrBlank()) " · cap. $chapter" else "")
            setTextColor(sub); textSize = 13f
        }
        val input = EditText(this).apply {
            hint = tr("Palabra o frase")
            setTextColor(fg); setHintTextColor(sub); textSize = 18f
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            minLines = 1; maxLines = 4
            requestFocus()
        }
        fun btn(label: String, filled: Boolean, onClick: () -> Unit) = Button(this).apply {
            text = label
            isAllCaps = false
            setTextColor(if (filled) Color.WHITE else fg)
            background = GradientDrawable().apply {
                cornerRadius = dp(22).toFloat()
                if (filled) setColor(accent) else { setColor(Color.TRANSPARENT); setStroke(dp(1), sub) }
            }
            setOnClickListener { onClick() }
        }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.END }
        val lp = { LinearLayout.LayoutParams(0, dp(46), 1f).apply { marginStart = dp(8) } }
        row.addView(btn(tr("Cambiar manhwa"), false) {
            startActivity(Intent(this, PickManhwaActivity::class.java))
            finish()
        }, lp())
        row.addView(btn(tr("Cancelar"), false) { finish() }, lp())
        row.addView(btn(tr("Guardar"), true) { save(input.text.toString()) }, lp())

        card.addView(where)
        card.addView(input, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        card.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })

        // La ventanita va arriba, para que el teclado no tape lo que lees; tocar fuera la cierra.
        val root = LinearLayout(this).apply {
            gravity = Gravity.TOP
            setPadding(dp(12), dp(48), dp(12), dp(12))
            setOnClickListener { finish() }
            addView(card, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        card.setOnClickListener { }
        setContentView(root)
    }

    private fun save(raw: String) {
        val text = raw.trim()
        if (text.isEmpty()) { finish(); return }
        val app = applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            val r = WordRepo.save(app, text, Prefs.lastManhwa(app), Prefs.lastChapter(app), null)
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    app,
                    tr(if (r.added) "Guardada: $text" else "Ya la tenías guardada"),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        finish()
    }
}
