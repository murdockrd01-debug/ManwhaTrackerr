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

    /** Saca "tag_name" del JSON de GitHub sin depender de librerías. */
    fun tagFromJson(json: String): String? =
        Regex("\"tag_name\"\\s*:\\s*\"([^\"]+)\"").find(json)?.groupValues?.get(1)
}
