package com.manhwatracker.app.data

/** Lógica pura para saber si hay una versión nueva en GitHub Releases. */
object Update {
    const val LATEST_API = "https://api.github.com/repos/murdockrd01-debug/ManwhaTrackerr/releases/latest"
    const val APK_URL = "https://github.com/murdockrd01-debug/ManwhaTrackerr/releases/latest/download/ManhwaTracker.apk"
    const val RELEASES_PAGE = "https://github.com/murdockrd01-debug/ManwhaTrackerr/releases/latest"

    /** "v2.7" o "2.7.1" -> [2, 7, 1]. Lo que no sea número se ignora; null si no hay ninguno. */
    fun parse(version: String): List<Int>? {
        val nums = Regex("\\d+").findAll(version.removePrefix("v").removePrefix("V")).map { it.value.toIntOrNull() }.toList()
        if (nums.isEmpty() || nums.any { it == null }) return null
        return nums.filterNotNull()
    }

    /** true si [remote] es estrictamente más nueva que [installed]. Si no se entiende alguna, false. */
    fun isNewer(remote: String, installed: String): Boolean {
        val r = parse(remote) ?: return false
        val i = parse(installed) ?: return false
        for (k in 0 until maxOf(r.size, i.size)) {
            val a = r.getOrElse(k) { 0 }
            val b = i.getOrElse(k) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    /** Saca "body" (las notas de la versión) del JSON de GitHub, ya sin caracteres de escape. */
    fun bodyFromJson(json: String): String? {
        val m = Regex("\"body\"\\s*:\\s*\"").find(json) ?: return null
        val sb = StringBuilder()
        var i = m.range.last + 1
        while (i < json.length) {
            val c = json[i]
            when {
                c == '"' -> return sb.toString().trim().ifEmpty { null }
                c == '\\' && i + 1 < json.length -> {
                    when (val n = json[i + 1]) {
                        'n' -> sb.append('\n')
                        'r' -> {}
                        't' -> sb.append(' ')
                        'u' -> if (i + 5 < json.length) {
                            json.substring(i + 2, i + 6).toIntOrNull(16)?.let { sb.append(it.toChar()) }
                            i += 4
                        }
                        else -> sb.append(n)
                    }
                    i += 2
                    continue
                }
                else -> sb.append(c)
            }
            i++
        }
        return null
    }

    /** Saca "tag_name" del JSON de GitHub sin depender de librerías. */
    fun tagFromJson(json: String): String? =
        Regex("\"tag_name\"\\s*:\\s*\"([^\"]+)\"").find(json)?.groupValues?.get(1)
}
