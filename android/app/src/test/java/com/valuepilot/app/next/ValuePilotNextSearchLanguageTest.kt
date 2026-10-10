package com.valuepilot.app.next

import org.junit.Assert.assertEquals
import org.junit.Test

class ValuePilotNextSearchLanguageTest {

    @Test
    fun `search renderer copy is localized while English remains unchanged`() {
        val status = "Product identities found. More matching identities are available."
        val caveat =
            "Identity suggestions only. No current price, stock, availability or ranking is confirmed; package size appears only when the source supplies it explicitly."

        assertEquals(
            status,
            valuePilotNextLocalizedSearchText(ValuePilotNextLanguage.ENGLISH, status)
        )
        assertEquals(
            "Se encontraron identidades de productos. Hay más identidades coincidentes disponibles.",
            valuePilotNextLocalizedSearchText(ValuePilotNextLanguage.SPANISH_AR, status)
        )
        assertEquals(
            "Solo sugerencias de identidad. No se confirman precio actual, stock, disponibilidad ni ranking; el tamaño del envase aparece únicamente cuando la fuente lo informa de forma explícita.",
            valuePilotNextLocalizedSearchText(ValuePilotNextLanguage.SPANISH_AR, caveat)
        )
    }
}
