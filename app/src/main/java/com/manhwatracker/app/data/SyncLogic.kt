package com.manhwatracker.app.data

import java.time.LocalDate

/** Reglas de la sincronización de carpetas. Son funciones puras, para poder probarlas sin el teléfono. */
object SyncLogic {

    /** Lo que sabemos de un archivo de la carpeta. */
    data class FileInfo(val name: String, val lastModified: Long, val size: Long)

    /** Los backups de Komikku/Mihon terminan en .tachibk (los más viejos, en .proto.gz). */
    fun isKomikkuBackup(name: String): Boolean {
        val n = name.lowercase()
        return n.endsWith(".tachibk") || n.endsWith(".proto.gz")
    }

    /** La copia de Komikku más reciente de la carpeta, o null si no hay ninguna. */
    fun newestKomikku(files: List<FileInfo>): FileInfo? =
        files.filter { isKomikkuBackup(it.name) }
            .maxWithOrNull(compareBy<FileInfo>({ it.lastModified }, { it.name }))

    /** Identifica una versión concreta de un archivo, para saber si ya la importamos. */
    fun fingerprint(f: FileInfo): String = "${f.name}|${f.lastModified}|${f.size}"

    // ---------------------------------------------------------------- Copia propia

    const val AUTO_PREFIX = "manhwa-tracker-auto-"

    /** Una copia por día: si se repite el día, se reescribe el mismo archivo. */
    fun autoName(date: LocalDate): String = "$AUTO_PREFIX$date.json"

    private val autoRegex =
        Regex("^" + Regex.escape(AUTO_PREFIX) + "(\\d{4}-\\d{2}-\\d{2})(?: \\(\\d+\\))?\\.json$")

    /** true solo para los archivos que creó esta función (nunca toca archivos de otro tipo). */
    fun isAutoName(name: String): Boolean = autoRegex.matches(name)

    fun autoDate(name: String): LocalDate? =
        autoRegex.find(name)?.groupValues?.get(1)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    /** Qué copias automáticas borrar para quedarse con las [keep] más recientes. */
    fun toDelete(files: List<FileInfo>, keep: Int = 7): List<FileInfo> =
        files.filter { isAutoName(it.name) }
            .sortedWith(compareByDescending<FileInfo> { autoDate(it.name) }.thenBy { it.name })
            .drop(keep)

    /** Si ya toca hacer otra copia. Si el reloj retrocedió, también toca. */
    fun isDue(lastAt: Long, now: Long, minInterval: Long): Boolean =
        lastAt <= 0 || now < lastAt || now - lastAt >= minInterval
}
