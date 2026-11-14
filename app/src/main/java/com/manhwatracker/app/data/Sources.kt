package com.manhwatracker.app.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.net.URLDecoder
import java.util.Locale

/** Una fuente que trae una extensión (por ejemplo "MangaDex" en español). */
@Serializable
class RepoSource(val name: String = "", val lang: String = "", val id: Long = 0, val baseUrl: String = "")

/** Una extensión tal como la lista un repositorio (formato `index.min.json` de Komikku/Mihon/Tachiyomi). */
@Serializable
class RepoExt(
    val name: String = "",
    val pkg: String = "",
    val apk: String = "",
    val lang: String = "",
    val code: Long = 0,
    val version: String = "",
    val nsfw: Int = 0,
    val sources: List<RepoSource> = emptyList()
)

/** Lógica pura de los repositorios de extensiones (se prueba sin el teléfono). */
object Sources {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

    /**
     * Deja la dirección base de un repositorio. Acepta lo que pega la gente: la dirección base, la del
     * `index.min.json` o un enlace `mihon://add-repo?url=...` / `tachiyomi://add-repo?url=...`.
     * Devuelve null si no es una dirección http(s).
     */
    fun normalizeRepoUrl(input: String): String? {
        var u = input.trim()
        val q = Regex("^[a-z]+://add-repo\\?url=(.+)$", RegexOption.IGNORE_CASE).find(u)
        if (q != null) u = runCatching { URLDecoder.decode(q.groupValues[1], "UTF-8") }.getOrDefault(q.groupValues[1]).trim()
        if (!u.startsWith("http://", true) && !u.startsWith("https://", true)) return null
        u = u.substringBefore('#').substringBefore('?')
        for (tail in listOf("/index.min.json", "/index.json")) if (u.endsWith(tail, true)) u = u.dropLast(tail.length)
        u = u.trimEnd('/')
        val host = u.substringAfter("://").substringBefore('/')
        return if (host.contains('.') && host.length > 3) u else null
    }

    fun indexUrl(base: String) = "$base/index.min.json"
    fun apkUrl(base: String, e: RepoExt) = "$base/apk/${e.apk}"
    fun iconUrl(base: String, e: RepoExt) = "$base/icon/${e.pkg}.png"

    /** Nombre corto para mostrar: "keiyoushi/extensions" para GitHub, o el dominio. */
    fun repoName(base: String): String {
        val rest = base.substringAfter("://")
        val host = rest.substringBefore('/')
        val parts = rest.substringAfter('/', "").split('/').filter { it.isNotEmpty() }
        return if (host == "raw.githubusercontent.com" && parts.size >= 2) parts[0] + "/" + parts[1] else host
    }

    /** Agrega [url] a la lista sin repetirla (comparando sin mayúsculas). */
    fun addRepo(list: List<String>, url: String): List<String> =
        if (list.any { it.equals(url, true) }) list else list + url

    /** Lee el índice. Lanza una excepción si no tiene el formato esperado. Quita entradas sin paquete o sin apk. */
    fun parseIndex(text: String): List<RepoExt> =
        json.decodeFromString(ListSerializer(RepoExt.serializer()), text).filter { it.pkg.isNotBlank() && it.apk.isNotBlank() }

    /** Nombre sin el prefijo "Tachiyomi: " que traen los repositorios. */
    fun displayName(e: RepoExt): String =
        e.name.removePrefix("Tachiyomi: ").removePrefix("Aniyomi: ").removePrefix("Mihon: ").trim().ifEmpty { e.pkg.substringAfterLast('.') }

    enum class State { NOT_INSTALLED, INSTALLED, UPDATE }

    /** [installedCode] = versionCode instalado, o null si no está instalada. */
    fun state(e: RepoExt, installedCode: Long?): State = when {
        installedCode == null -> State.NOT_INSTALLED
        e.code > installedCode -> State.UPDATE
        else -> State.INSTALLED
    }

    /** Si el mismo paquete está en varios repositorios, deja el de versión más nueva. */
    fun merge(all: List<RepoExt>): List<RepoExt> =
        all.groupBy { it.pkg }.values.map { g -> g.maxByOrNull { it.code }!! }

    /** Busca por nombre (o por el de sus fuentes), filtra por idioma y oculta contenido adulto si no se pidió. */
    fun filter(list: List<RepoExt>, query: String, lang: String?, showNsfw: Boolean): List<RepoExt> {
        val q = query.trim().lowercase()
        return list.filter { e ->
            (showNsfw || e.nsfw == 0) &&
                (lang == null || e.lang == lang) &&
                (q.isEmpty() || displayName(e).lowercase().contains(q) || e.sources.any { it.name.lowercase().contains(q) })
        }.sortedBy { displayName(it).lowercase() }
    }

    /** Idiomas que hay, del más común al menos común. */
    fun languages(list: List<RepoExt>): List<String> =
        list.groupingBy { it.lang }.eachCount().entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key }).map { it.key }

    fun langLabel(code: String): String = when (code) {
        "all" -> "Varios idiomas"
        "" -> "?"
        else -> Locale.forLanguageTag(code).getDisplayLanguage(Locale("es")).replaceFirstChar { it.uppercase() }.ifEmpty { code }
    }
}
