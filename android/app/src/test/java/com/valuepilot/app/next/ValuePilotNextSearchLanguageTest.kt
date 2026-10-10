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

    @Test
    fun `good price dynamic copy is localized without changing evidence facts`() {
        val guidance =
            "This is about 13.4% lower per unit than your last matching observation. " +
                "That is useful personal context, not a guarantee of the cheapest price."
        val history =
            "Personal history: 2 observations · lowest 1.6225 ARS/L · highest 1.75 ARS/L · latest 1.7 ARS/L."

        assertEquals(
            guidance,
            valuePilotNextLocalizedDynamicText(ValuePilotNextLanguage.ENGLISH, guidance)
        )
        assertEquals(
            "Es aproximadamente un 13,4 % más bajo por unidad que tu última observación coincidente. " +
                "Es un contexto personal útil, no una garantía del precio más bajo.",
            valuePilotNextLocalizedDynamicText(ValuePilotNextLanguage.SPANISH_AR, guidance)
        )
        assertEquals(
            "Historial personal: 2 observaciones · mínimo 1.6225 ARS/L · máximo 1.75 ARS/L · última 1.7 ARS/L.",
            valuePilotNextLocalizedDynamicText(ValuePilotNextLanguage.SPANISH_AR, history)
        )
    }

    @Test
    fun `comparison route copy is localized while product facts remain untouched`() {
        val guidance =
            "Lower exact unit price wins within this comparison. Best value is about 12.5% lower per unit than the next-best option."
        val memoryNotice =
            "Remembered 2 product prices privately on this device. This is comparison history, not live store pricing."

        assertEquals(
            "Dentro de esta comparación gana el menor precio unitario exacto. El mejor valor es aproximadamente un 12,5 % más bajo por unidad que la siguiente opción.",
            valuePilotNextLocalizedDynamicText(ValuePilotNextLanguage.SPANISH_AR, guidance)
        )
        assertEquals(
            "Se guardaron 2 precios de productos de forma privada en este dispositivo. Es historial de comparación, no precios de tiendas en vivo.",
            valuePilotNextLocalizedDynamicText(ValuePilotNextLanguage.SPANISH_AR, memoryNotice)
        )
        assertEquals(
            "2 product names could not be shown safely.",
            valuePilotNextLocalizedDynamicText(
                ValuePilotNextLanguage.ENGLISH,
                "2 product names could not be shown safely."
            )
        )
        assertEquals(
            "2 nombres de productos no se pudieron mostrar de forma segura.",
            valuePilotNextLocalizedDynamicText(
                ValuePilotNextLanguage.SPANISH_AR,
                "2 product names could not be shown safely."
            )
        )
    }

    @Test
    fun `numeric evidence uses Argentine decimal punctuation without changing identifiers`() {
        val unitRate = "1.6225 ARS/L"
        val history = "mínimo 1.6225 ARS/L · máximo 1.75 ARS/L"
        val identifier = "GTIN 0123456789012"

        assertEquals(unitRate, valuePilotNextLocalizedFactText(ValuePilotNextLanguage.ENGLISH, unitRate))
        assertEquals("1,6225 ARS/L", valuePilotNextLocalizedFactText(ValuePilotNextLanguage.SPANISH_AR, unitRate))
        assertEquals(
            "mínimo 1,6225 ARS/L · máximo 1,75 ARS/L",
            valuePilotNextLocalizedFactText(ValuePilotNextLanguage.SPANISH_AR, history)
        )
        assertEquals(identifier, valuePilotNextLocalizedFactText(ValuePilotNextLanguage.SPANISH_AR, identifier))
    }
}
