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

    /** Un índice que solo trae avisos de "app desactualizada" (keiyoushi lo deja así en index.min.json). */
    fun isStub(list: List<RepoExt>): Boolean =
        list.isEmpty() || list.all { it.name.contains("outdated", true) || it.pkg.contains("outdated", true) }

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
