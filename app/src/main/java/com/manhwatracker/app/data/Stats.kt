package com.manhwatracker.app.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Cálculos de las estadísticas. Todo son funciones puras, para poder probarlas sin el teléfono. */
object Stats {

    /** Una barra de las gráficas: etiqueta (semana o mes) y cantidad. */
    data class Bucket(val label: String, val value: Int)

    data class Streak(val current: Int, val best: Int, val reviewedToday: Boolean)

    private val monthNames = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")

    /** Número de día (como LocalDate.toEpochDay) de un instante, en la zona horaria del teléfono. */
    fun dayOf(millis: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalDate().toEpochDay()

    /**
     * Racha de días seguidos repasando.
     * La racha actual sigue viva si repasaste hoy o ayer: hoy todavía puedes repasar sin perderla.
     */
    fun streak(days: Collection<Long>, today: Long): Streak {
        val set = days.filter { it <= today }.toSortedSet()
        if (set.isEmpty()) return Streak(0, 0, false)

        var best = 0
        var run = 0
        var prev: Long? = null
        for (d in set) {
            run = if (prev != null && d == prev + 1) run + 1 else 1
            if (run > best) best = run
            prev = d
        }

        val reviewedToday = today in set
        var cursor = if (reviewedToday) today else today - 1
        var current = 0
        while (cursor in set) {
            current++
            cursor--
        }
        return Streak(current, best, reviewedToday)
    }

    /** Lunes de la semana a la que pertenece [day]. */
    fun weekStart(day: Long): Long = day - (LocalDate.ofEpochDay(day).dayOfWeek.value - 1)

    /**
     * Cuántos eventos hubo en cada una de las últimas [weeks] semanas (de lunes a domingo),
     * de la más vieja a la actual. La etiqueta es el día del mes en que empieza la semana, ej. "13/10".
     */
    fun weeklyCounts(days: Collection<Long>, today: Long, weeks: Int = 8): List<Bucket> {
        val thisWeek = weekStart(today)
        val counts = HashMap<Long, Int>()
        for (d in days) {
            val w = weekStart(d)
            counts[w] = (counts[w] ?: 0) + 1
        }
        return (weeks - 1 downTo 0).map { back ->
            val start = thisWeek - 7L * back
            val date = LocalDate.ofEpochDay(start)
            Bucket("${date.dayOfMonth}/${date.monthValue}", counts[start] ?: 0)
        }
    }

    /**
     * Suma por mes de las últimas [months] mensualidades, de la más vieja al mes actual.
     * [entries] son pares (día, cantidad). Los totales negativos (por deshacer) se muestran como 0.
     */
    fun monthlySums(entries: Collection<Pair<Long, Int>>, today: Long, months: Int = 6): List<Bucket> {
        val now = LocalDate.ofEpochDay(today)
        val sums = HashMap<Int, Int>() // año*12 + mes (0-11)
        for ((day, amount) in entries) {
            val d = LocalDate.ofEpochDay(day)
            val key = d.year * 12 + (d.monthValue - 1)
            sums[key] = (sums[key] ?: 0) + amount
        }
        val nowKey = now.year * 12 + (now.monthValue - 1)
        return (months - 1 downTo 0).map { back ->
            val key = nowKey - back
            Bucket(monthNames[key % 12], maxOf(0, sums[key] ?: 0))
        }
    }

    /** Las obras de las que más palabras guardaste: título (tal como lo escribiste) y cantidad. */
    fun wordsPerManhwa(words: List<Word>, limit: Int = 5): List<Bucket> =
        words.mapNotNull { w -> w.manhwaTitle?.trim()?.takeIf { it.isNotEmpty() } }
            .groupBy { titleKeyOf(it) }
            .map { (_, titles) -> Bucket(titles.first(), titles.size) }
            .sortedWith(compareByDescending<Bucket> { it.value }.thenBy { it.label.lowercase() })
            .take(limit)
}
