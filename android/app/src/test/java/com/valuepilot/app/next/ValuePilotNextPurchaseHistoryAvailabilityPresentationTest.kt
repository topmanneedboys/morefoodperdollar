package com.valuepilot.app.next

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ValuePilotNextPurchaseHistoryAvailabilityPresentationTest {

    @Test
    fun `purchase history stays unavailable and distinct from price observations`() {
        val presentation = valuePilotNextPurchaseHistoryAvailabilityPresentation()

        assertEquals("PURCHASE HISTORY · UNAVAILABLE", presentation.eyebrow)
        assertTrue(presentation.title.contains("not available", ignoreCase = true))
        assertTrue(presentation.detail.contains("No purchase or receipt source is connected."))
        assertTrue(presentation.detail.contains("does not record what you bought."))
    }
}
