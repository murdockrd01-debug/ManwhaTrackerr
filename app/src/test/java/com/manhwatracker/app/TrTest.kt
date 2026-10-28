package com.manhwatracker.app

import org.junit.Assert.*
import org.junit.Test

class TrTest {
    @Test fun english() {
        Tr.lang = "en"
        assertEquals("Reading", Tr.t("Leyendo"))
        assertEquals("Ch. 5 of 40", Tr.t("Cap. 5 de 40"))
        assertEquals("Action (3)", Tr.t("Acción (3)"))
        assertEquals("Saved: hello (in The Boxer)", Tr.t("Guardada: hello (en The Boxer)"))
        assertEquals("You reviewed 3 words and missed 2 times.", Tr.t("Repasaste 3 palabras y fallaste 2 veces."))
        assertEquals("The Boxer", Tr.t("The Boxer")) // títulos del usuario no se tocan
    }
    @Test fun spanishUntouched() {
        Tr.lang = "es"
        assertEquals("Leyendo", Tr.t("Leyendo"))
    }
}
