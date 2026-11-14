package com.manhwatracker.app.data

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull

/** Formatos nuevos de repositorio (índice v2): lector tolerante de `index.json` y diagnóstico de `index.pb`. */
object SourcesV2 {

    private val out = Json { ignoreUnknownKeys = true }

    fun encode(list: List<RepoExt>): String = out.encodeToString(ListSerializer(RepoExt.serializer()), list)

    private fun RepoExt.looksOutdated(): Boolean =
        name.contains("outdated", true) || pkg.contains("outdated", true) || apk.contains("outdated", true) ||
            sources.any { it.name.contains("outdated", true) }

    /**
     * Un índice que solo trae avisos de "app desactualizada" (keiyoushi lo deja así en index.min.json y quizá en index.json):
     * vacío, todo avisos, o una lista diminuta con algún aviso. Un repositorio real trae decenas de extensiones.
     */
    fun isStub(list: List<RepoExt>): Boolean =
        list.isEmpty() || list.all { it.looksOutdated() } || (list.size < 20 && list.any { it.looksOutdated() })

    private fun JsonObject.str(vararg keys: String): String? =
        keys.firstNotNullOfOrNull { (this[it] as? JsonPrimitive)?.contentOrNull?.takeIf { s -> s.isNotEmpty() } }

    private fun JsonObject.num(vararg keys: String): Long? =
        keys.firstNotNullOfOrNull { k -> (this[k] as? JsonPrimitive)?.let { it.longOrNull ?: it.booleanOrNull?.let { b -> if (b) 1L else 0L } } }

    /** `index.json` del formato nuevo. Acepta una lista, o un objeto con la lista en "extensions". Nombres de campo tolerantes. */
    fun parseIndexJson(text: String): List<RepoExt> {
        val root: JsonElement = Json.parseToJsonElement(text)
        val arr: JsonArray = when (root) {
            is JsonArray -> root
            is JsonObject -> (root["extensions"] as? JsonArray) ?: throw IllegalArgumentException("sin extensiones")
            else -> throw IllegalArgumentException("formato")
        }
        return arr.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val pkg = o.str("pkg", "packageName", "package") ?: return@mapNotNull null
            val apk = o.str("apk", "apkName") ?: return@mapNotNull null
            val version = o.str("version", "versionName") ?: ""
            val code = o.num("code", "versionCode") ?: version.substringAfterLast('.').toLongOrNull() ?: 0L
            val sources = (o["sources"] as? JsonArray).orEmpty().mapNotNull { s ->
                val so = s as? JsonObject ?: return@mapNotNull null
                RepoSource(so.str("name") ?: "", so.str("lang") ?: "", so.num("id") ?: 0L, so.str("baseUrl", "baseurl") ?: "")
            }
            RepoExt(o.str("name") ?: pkg, pkg, apk, o.str("lang") ?: "", code, version, (o.num("nsfw", "isNsfw") ?: 0L).toInt(), sources)
        }
    }

    // ---- index.pb (índice v2, esquema deducido del diagnóstico real de keiyoushi) ----
    // raíz: f1 nombre, f2 sigla, f3 huella, f4 {f1 web, f2 discord}, f101 { f1* = extensión }
    // extensión: f1 nombre, f2 pkg, f3 {f1 apk(url), f2 ícono(url), f501 jar(url)}, f4 lib, f5 versionCode,
    //            f6 versión, f7 marca (¿+18?), f8* fuente { f1 id, f2 nombre, f3 idioma, f4 baseUrl }

    private class Field(val num: Int, val wire: Int, val value: Long, val start: Int, val end: Int)

    private fun fields(b: ByteArray, from: Int, to: Int): List<Field> {
        val r = Reader(b, from, to)
        val out = ArrayList<Field>()
        while (r.more()) {
            val tag = r.varint()
            val num = (tag ushr 3).toInt()
            when (val w = (tag and 7).toInt()) {
                0 -> out.add(Field(num, w, r.varint(), 0, 0))
                1 -> { out.add(Field(num, w, 0, r.p, r.p + 8)); r.p += 8 }
                5 -> { out.add(Field(num, w, 0, r.p, r.p + 4)); r.p += 4 }
                2 -> {
                    val l = r.varint().toInt()
                    if (l < 0 || r.p + l > to) throw IllegalStateException("longitud")
                    out.add(Field(num, w, 0, r.p, r.p + l)); r.p += l
                }
                else -> throw IllegalStateException("tipo")
            }
        }
        return out
    }

    /** Idioma según el paquete: eu.kanade.tachiyomi.extension.<idioma>.<nombre>; si no, el de la primera fuente. */
    internal fun langOf(pkg: String, sources: List<RepoSource>): String {
        val seg = pkg.substringAfter(".extension.", "").substringBefore('.')
        return if (seg.isNotEmpty() && seg.length <= 8) seg else sources.firstOrNull()?.lang ?: ""
    }

    fun parseIndexPb(raw: ByteArray): List<RepoExt> {
        val bytes = if (raw.size > 2 && raw[0] == 0x1f.toByte() && raw[1] == 0x8b.toByte())
            java.util.zip.GZIPInputStream(raw.inputStream()).use { it.readBytes() } else raw
        fun str(f: Field) = String(bytes, f.start, f.end - f.start, Charsets.UTF_8)
        val list = ArrayList<RepoExt>()
        for (top in fields(bytes, 0, bytes.size)) {
            if (top.num != 101 || top.wire != 2) continue
            for (e in fields(bytes, top.start, top.end)) {
                if (e.num != 1 || e.wire != 2) continue
                var name = ""; var pkg = ""; var apkUrl = ""; var icon = ""; var version = ""
                var code = 0L; var flag = 0L
                val sources = ArrayList<RepoSource>()
                for (f in fields(bytes, e.start, e.end)) when {
                    f.num == 1 && f.wire == 2 -> name = str(f)
                    f.num == 2 && f.wire == 2 -> pkg = str(f)
                    f.num == 3 && f.wire == 2 -> for (u in fields(bytes, f.start, f.end)) {
                        if (u.wire != 2) continue
                        if (u.num == 1) apkUrl = str(u) else if (u.num == 2) icon = str(u)
                    }
                    f.num == 5 && f.wire == 0 -> code = f.value
                    f.num == 6 && f.wire == 2 -> version = str(f)
                    f.num == 7 && f.wire == 0 -> flag = f.value
                    f.num == 8 && f.wire == 2 -> {
                        var id = 0L; var sn = ""; var lang = ""; var url = ""
                        for (s in fields(bytes, f.start, f.end)) when {
                            s.num == 1 && s.wire == 0 -> id = s.value
                            s.num == 2 && s.wire == 2 -> sn = str(s)
                            s.num == 3 && s.wire == 2 -> lang = str(s)
                            s.num == 4 && s.wire == 2 -> url = str(s)
                        }
                        sources.add(RepoSource(sn, lang, id, url))
                    }
                }
                if (pkg.isBlank() || apkUrl.isBlank()) continue
                list.add(RepoExt(name.ifBlank { pkg }, pkg, apkUrl.substringBefore('?').substringAfterLast('/'), langOf(pkg, sources), code, version, flag.toInt(), sources, apkUrl, icon))
            }
        }
        return list
    }

    // ---- Diagnóstico de protobuf sin esquema ----

    private class Reader(val b: ByteArray, var p: Int, val end: Int) {
        fun more() = p < end
        fun varint(): Long {
            var r = 0L; var s = 0
            while (true) {
                if (p >= end || s > 63) throw IllegalStateException()
                val x = b[p++].toInt()
                r = r or ((x and 0x7f).toLong() shl s)
                if (x and 0x80 == 0) return r
                s += 7
            }
        }
    }

    private fun printable(b: ByteArray, from: Int, to: Int): String? {
        if (to <= from) return ""
        val s = String(b, from, to - from, Charsets.UTF_8)
        return if (s.all { it == '\n' || it == '\t' || !it.isISOControl() } && !s.contains('\uFFFD')) s else null
    }

    /** ¿Se puede leer [from,to) como mensaje protobuf completo? */
    private fun looksLikeMessage(b: ByteArray, from: Int, to: Int): Boolean {
        if (to <= from) return false
        return try {
            val r = Reader(b, from, to)
            while (r.more()) {
                val tag = r.varint()
                if (tag ushr 3 == 0L) return false
                when ((tag and 7).toInt()) {
                    0 -> r.varint()
                    1 -> r.p += 8
                    2 -> { val l = r.varint().toInt(); if (l < 0 || r.p + l > to) return false; r.p += l }
                    5 -> r.p += 4
                    else -> return false
                }
                if (r.p > to) return false
            }
            true
        } catch (e: Exception) { false }
    }

    /**
     * Resumen legible de un protobuf cuyo esquema no conocemos: número de campo, tipo y valores de muestra.
     * De cada campo repetido solo se muestran los primeros [maxRepeat] elementos.
     */
    fun wireDump(bytes: ByteArray, maxRepeat: Int = 2, maxDepth: Int = 5): String {
        val sb = StringBuilder()
        fun walk(from: Int, to: Int, depth: Int) {
            val r = Reader(bytes, from, to)
            val seen = HashMap<Long, Int>()
            val counts = HashMap<Long, Int>()
            val pos = ArrayList<Triple<Long, Int, Int>>() // campo, inicio, fin (para LEN)
            // primera pasada: contar
            val r0 = Reader(bytes, from, to)
            while (r0.more()) {
                val tag = r0.varint(); val f = tag ushr 3
                counts[f] = (counts[f] ?: 0) + 1
                when ((tag and 7).toInt()) { 0 -> r0.varint(); 1 -> r0.p += 8; 2 -> { val l = r0.varint().toInt(); r0.p += l }; 5 -> r0.p += 4 }
            }
            val pad = "  ".repeat(depth)
            while (r.more()) {
                val tag = r.varint(); val f = tag ushr 3
                val n = (seen[f] ?: 0) + 1; seen[f] = n
                val show = n <= maxRepeat
                when ((tag and 7).toInt()) {
                    0 -> { val v = r.varint(); if (show) sb.append(pad).append("f$f varint=$v\n") }
                    1 -> { r.p += 8; if (show) sb.append(pad).append("f$f fixed64\n") }
                    5 -> { r.p += 4; if (show) sb.append(pad).append("f$f fixed32\n") }
                    2 -> {
                        val l = r.varint().toInt(); val s = r.p; val e = s + l; r.p = e
                        if (show) {
                            val text = printable(bytes, s, e)
                            val total = counts[f] ?: 1
                            val rep = if (n == 1 && total > 1) " (x$total)" else ""
                            if (text != null && !(l > 1 && looksLikeMessage(bytes, s, e) && text.any { it.isISOControl() })) {
                                sb.append(pad).append("f$f str=\"").append(text.take(120).replace("\n", "\\n")).append("\"$rep\n")
                            } else if (depth < maxDepth && looksLikeMessage(bytes, s, e)) {
                                sb.append(pad).append("f$f msg$rep {\n"); walk(s, e, depth + 1); sb.append(pad).append("}\n")
                            } else sb.append(pad).append("f$f bytes[$l]$rep\n")
                        }
                    }
                }
            }
        }
        return try { walk(0, bytes.size, 0); sb.toString().take(6000) } catch (e: Exception) { sb.toString().take(6000) + "\n(error leyendo: ${e.javaClass.simpleName})" }
    }
}
