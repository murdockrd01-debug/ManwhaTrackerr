package com.manhwatracker.app.data

/** Hoja para imprimir (o guardar como PDF) y completar a mano: estructuras y palabras con espacio para anotar. */
object PrintSheet {
    class WordRow(val text: String, val translation: String?, val example: String?)

    fun esc(s: String): String = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    fun html(structures: List<Structure>, words: List<WordRow>, date: String): String {
        val b = StringBuilder()
        b.append("<!doctype html><html><head><meta charset=\"utf-8\"><title>Lectoon - Estudio</title><style>")
        b.append("body{font-family:sans-serif;font-size:12pt;margin:16px;color:#111}h1{font-size:20pt;margin:0}h2{margin-top:22px;border-bottom:2px solid #333}")
        b.append(".s{margin:12px 0;page-break-inside:avoid}.p{font-weight:bold;color:#333}.ex{margin:2px 0 2px 14px}.es{color:#555}")
        b.append(".n{height:70px;border:1px solid #aaa;margin-top:6px;background:repeating-linear-gradient(white,white 21px,#ccc 22px)}")
        b.append("table{width:100%;border-collapse:collapse}td,th{border:1px solid #999;padding:6px;vertical-align:top}th{background:#eee}.w{height:44px}")
        b.append("</style></head><body><h1>Lectoon · Estudio</h1><div>").append(esc(date)).append("</div>")
        if (structures.isNotEmpty()) {
            b.append("<h2>Estructuras</h2>")
            for (s in structures) {
                b.append("<div class=\"s\"><div><b>").append(esc(s.title)).append("</b></div>")
                b.append("<div class=\"p\">").append(esc(s.pattern)).append("</div>")
                b.append("<div>").append(esc(s.explanation)).append("</div>")
                for ((en, es) in s.examples) b.append("<div class=\"ex\">").append(esc(en)).append(" <span class=\"es\">— ").append(esc(es)).append("</span></div>")
                b.append("<div class=\"es\">Ojo: ").append(esc(s.tip)).append("</div>")
                b.append("<div class=\"n\"></div></div>")
            }
        }
        if (words.isNotEmpty()) {
            b.append("<h2>Palabras</h2><table><tr><th>Palabra</th><th>Significado</th><th>Ejemplo</th><th>Mi frase</th></tr>")
            for (w in words) {
                b.append("<tr><td><b>").append(esc(w.text)).append("</b></td><td>").append(esc(w.translation.orEmpty()))
                    .append("</td><td>").append(esc(w.example.orEmpty())).append("</td><td class=\"w\"></td></tr>")
            }
            b.append("</table>")
        }
        b.append("</body></html>")
        return b.toString()
    }
}
