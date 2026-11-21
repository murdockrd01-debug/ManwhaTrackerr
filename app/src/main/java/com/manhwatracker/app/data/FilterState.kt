package com.manhwatracker.app.data

import eu.kanade.tachiyomi.source.model.Filter

/**
 * Guarda y restaura lo elegido en los filtros de una fuente (géneros, estado, orden...).
 * Se recorre la lista de filtros en orden y se guarda un valor por filtro; si la fuente cambió sus filtros
 * (otra firma), lo guardado se ignora en vez de aplicarse mal.
 */
object FilterState {
    private const val SEP = '\u0001'
    private const val HEAD = '\u0002'

    private fun clean(s: String) = s.replace(SEP, ' ').replace(HEAD, ' ')

    /** Los filtros "con valor" (sin títulos ni separadores) en orden, entrando en los grupos. */
    private fun leaves(list: List<Filter<*>>): List<Filter<*>> = buildList {
        for (f in list) when (f) {
            is Filter.Header, is Filter.Separator -> {}
            is Filter.Group<*> -> addAll(leaves((f.state as List<*>).filterIsInstance<Filter<*>>()))
            else -> add(f)
        }
    }

    private fun kind(f: Filter<*>) = when (f) {
        is Filter.Select<*> -> "S"; is Filter.Text -> "T"; is Filter.CheckBox -> "C"
        is Filter.TriState -> "3"; is Filter.Sort -> "O"; else -> "?"
    }

    fun signature(list: List<Filter<*>>): String = leaves(list).joinToString("|") { kind(it) + clean(it.name) }

    private fun tokenOf(f: Filter<*>): String = when (f) {
        is Filter.Select<*> -> f.state.toString()
        is Filter.Text -> clean(f.state)
        is Filter.CheckBox -> if (f.state) "1" else "0"
        is Filter.TriState -> f.state.toString()
        is Filter.Sort -> f.state?.let { "${it.index},${if (it.ascending) 1 else 0}" } ?: "-"
        else -> ""
    }

    /** Texto para guardar. Vacío si todo está como viene por defecto no hace falta distinguirlo: se guarda igual. */
    fun encode(list: List<Filter<*>>): String =
        signature(list) + HEAD + leaves(list).joinToString(SEP.toString()) { tokenOf(it) }

    /** Pone lo guardado en [list]. Devuelve false (sin tocar nada) si no encaja con estos filtros. */
    fun apply(list: List<Filter<*>>, saved: String?): Boolean {
        if (saved.isNullOrEmpty()) return false
        val head = saved.substringBefore(HEAD, "")
        if (head != signature(list)) return false
        val items = leaves(list)
        val tokens = saved.substringAfter(HEAD).split(SEP)
        if (items.isEmpty() || tokens.size != items.size) return false
        // Primero se comprueba todo, luego se aplica: así un valor roto no deja los filtros a medias.
        val parsed = ArrayList<() -> Unit>()
        for ((f, t) in items.zip(tokens)) {
            val setter: () -> Unit = when (f) {
                is Filter.Select<*> -> { val i = t.toIntOrNull()?.takeIf { it in f.values.indices } ?: return false; { f.state = i } }
                is Filter.Text -> { { f.state = t } }
                is Filter.CheckBox -> { if (t != "0" && t != "1") return false; { f.state = t == "1" } }
                is Filter.TriState -> { val i = t.toIntOrNull()?.takeIf { it in 0..2 } ?: return false; { f.state = i } }
                is Filter.Sort -> {
                    if (t == "-") { { f.state = null } } else {
                        val (a, b) = t.split(',').takeIf { it.size == 2 } ?: return false
                        val i = a.toIntOrNull()?.takeIf { it in f.values.indices } ?: return false
                        if (b != "0" && b != "1") return false
                        { f.state = Filter.Sort.Selection(i, b == "1") }
                    }
                }
                else -> return false
            }
            parsed += setter
        }
        parsed.forEach { it() }
        return true
    }
}
