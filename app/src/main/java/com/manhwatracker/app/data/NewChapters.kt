package com.manhwatracker.app.data

/** Reglas para avisar de capítulos nuevos (lógica pura). */
object NewChapters {
    /**
     * ¿Avisar? [prev] es el último capítulo que se vio en la fuente en la revisión anterior (null = primera vez:
     * solo se guarda como punto de partida, sin avisar), [latest] el de ahora y [readUpTo] tu avance en la lista.
     */
    fun shouldNotify(prev: Double?, latest: Double?, readUpTo: Double): Boolean =
        prev != null && latest != null && latest > prev + 0.0001 && latest > readUpTo + 0.0001

    /** Cuántos capítulos de la lista pasan de tu avance. */
    fun unread(nums: List<Double?>, readUpTo: Double): Int = nums.count { it != null && it > readUpTo + 0.0001 }
}
