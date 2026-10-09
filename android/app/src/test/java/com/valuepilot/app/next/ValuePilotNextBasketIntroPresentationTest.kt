package com.valuepilot.app.next

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ValuePilotNextBasketIntroPresentationTest {

    @Test
    fun `empty basket does not claim that price evidence is attached`() {
        val presentation = valuePilotNextBasketIntroPresentation(
            itemCount = 0,
            hasMissingPriceEvidence = false
        )

        assertEquals("Your basket is ready for its first item.", presentation.title)
        assertFalse(presentation.title.contains("evidence attached", ignoreCase = true))
        assertEquals(
            "Add exact product identities. Prices appear only when fresh evidence is available; availability remains unknown.",
            presentation.detail
        )
    }

    @Test
    fun `incomplete basket calls out price gaps`() {
        val presentation = valuePilotNextBasketIntroPresentation(
            itemCount = 2,
            hasMissingPriceEvidence = true
        )

        assertEquals("Your basket, with price gaps made clear.", presentation.title)
        assertEquals(null, presentation.detail)
    }

    @Test
    fun `fully evidenced basket retains evidence headline`() {
        val presentation = valuePilotNextBasketIntroPresentation(
            itemCount = 2,
            hasMissingPriceEvidence = false
        )

        assertEquals("Your basket, with evidence attached.", presentation.title)
        assertEquals(null, presentation.detail)
    }
}
