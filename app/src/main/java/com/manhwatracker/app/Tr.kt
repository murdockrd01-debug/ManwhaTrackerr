package com.manhwatracker.app

import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import java.util.Locale

/**
 * Traducción ligera: la app está escrita en español y, si el teléfono (o el ajuste Idioma) pide otro idioma,
 * cada texto se traduce al inglés con este diccionario. Lo que no esté en el diccionario se queda en español.
 * Los títulos y palabras del usuario nunca se tocan.
 */
object Tr {
    /** "auto" (sigue al teléfono), "es" o "en". */
    var lang by mutableStateOf("auto")

    private val cache = HashMap<String, String>()
    private var cacheLang = ""

    fun english(): Boolean = when (lang) {
        "es" -> false
        "en" -> true
        else -> Locale.getDefault().language != "es"
    }

    private class Pat(val regex: Regex, val out: String, val kinds: List<Boolean>)

    private val patterns: List<Pat> by lazy {
        TrData.patterns.map { (es, en) ->
            val kinds = ArrayList<Boolean>() // true = {t} (se traduce), false = {} (se deja igual)
            val sb = StringBuilder("^")
            var i = 0
            while (i < es.length) {
                when {
                    es.startsWith("{t}", i) -> { sb.append("(.+?)"); kinds.add(true); i += 3 }
                    es.startsWith("{}", i) -> { sb.append("(.+?)"); kinds.add(false); i += 2 }
                    else -> { sb.append(Regex.escape(es[i].toString())); i++ }
                }
            }
            sb.append("$")
            Pat(Regex(sb.toString(), RegexOption.DOT_MATCHES_ALL), en, kinds)
        }
    }

    private val fragments: List<Pair<String, String>> by lazy {
        TrData.exact.entries.filter { it.key.length >= 14 && it.key.contains(' ') }
            .sortedByDescending { it.key.length }.map { it.key to it.value }
    }

    fun t(s: String): String {
        if (s.isEmpty() || !english()) return s
        if (cacheLang != lang) { cache.clear(); cacheLang = lang }
        cache[s]?.let { return it }
        val r = translate(s, 0)
        if (cache.size > 3000) cache.clear()
        cache[s] = r
        return r
    }

    private fun translate(s: String, depth: Int): String {
        TrData.exact[s]?.let { return it }
        if (depth < 3) {
            for (p in patterns) {
                val m = p.regex.matchEntire(s) ?: continue
                var out = p.out
                m.groupValues.drop(1).forEachIndexed { k, v ->
                    out = out.replace("{${k + 1}}", if (p.kinds[k]) translate(v, depth + 1) else v)
                }
                return out
            }
        }
        // Textos largos unidos con "+" o con partes: se traduce cada trozo conocido.
        var out = s
        var changed = false
        for ((es, en) in fragments) {
            if (out.contains(es)) { out = out.replace(es, en); changed = true }
        }
        return if (changed) out else s
    }
}

/** Atajo para textos que no pasan por Text (avisos, notificaciones, descripciones). */
fun tr(s: String): String = Tr.t(s)

/** Mismo Text de Material3, pero traduce el texto según el idioma. Toda la app usa este. */
@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
    style: TextStyle = LocalTextStyle.current
) {
    @Suppress("UNUSED_VARIABLE") val observe = Tr.lang // recompone al cambiar el idioma
    androidx.compose.material3.Text(
        text = Tr.t(text), modifier = modifier, color = color, fontSize = fontSize, fontStyle = fontStyle,
        fontWeight = fontWeight, fontFamily = fontFamily, letterSpacing = letterSpacing,
        textDecoration = textDecoration, textAlign = textAlign, lineHeight = lineHeight, overflow = overflow,
        softWrap = softWrap, maxLines = maxLines, minLines = minLines, onTextLayout = onTextLayout, style = style
    )
}
