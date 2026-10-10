package com.valuepilot.app.next

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ValuePilotNextPurchaseHistoryAvailabilityPresentationTest {

    @Test
    fun `purchase history stays unavailable and distinct from price observations`() {
        val presentation = valuePilotNextPurchaseHistoryAvailabilityPresentation(
            ValuePilotNextLanguage.ENGLISH
        )

        assertEquals("PURCHASE HISTORY · UNAVAILABLE", presentation.eyebrow)
        assertTrue(presentation.title.contains("not available", ignoreCase = true))
        assertTrue(presentation.detail.contains("No purchase or receipt source is connected."))
        assertTrue(presentation.detail.contains("does not record what you bought."))
    }

    @Test
    fun `purchase history unavailable copy follows the selected language`() {
        val presentation = valuePilotNextPurchaseHistoryAvailabilityPresentation(
            ValuePilotNextLanguage.SPANISH_AR
        )

        assertEquals("HISTORIAL DE COMPRAS · NO DISPONIBLE", presentation.eyebrow)
        assertTrue(presentation.title.contains("no está disponible", ignoreCase = true))
        assertTrue(presentation.detail.contains("No hay una fuente de compras o comprobantes conectada."))
        assertTrue(presentation.detail.contains("no registra lo que compraste."))
    }
}
