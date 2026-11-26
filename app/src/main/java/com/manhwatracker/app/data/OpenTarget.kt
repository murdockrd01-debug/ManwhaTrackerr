package com.manhwatracker.app.data

/** A dónde mandar al usuario cuando toca "Leer" en un manhwa de su biblioteca. */
object OpenTarget {
    enum class Target { DETAIL, BROWSE, SEARCH }

    /**
     * - Ya lo leíste en una fuente (tiene vínculo, ficha guardada y la fuente sigue instalada): directo a su ficha.
     * - Vínculo sin ficha guardada pero con la fuente disponible: se busca en esa misma fuente.
     * - Nunca se vinculó (o su fuente ya no está): búsqueda en todas las fuentes. Nunca se usa "la última fuente
     *   que abriste", que puede ser de otro manhwa.
     */
    fun decide(linked: Boolean, hasManga: Boolean, sourceAvailable: Boolean): Target = when {
        linked && sourceAvailable && hasManga -> Target.DETAIL
        linked && sourceAvailable -> Target.BROWSE
        else -> Target.SEARCH
    }
}
