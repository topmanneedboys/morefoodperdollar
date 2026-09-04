package com.valuepilot.app

import com.valuepilot.core.CompareHerePriceSelection
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GoodPriceShareCardTest {

    @Test
    fun `exact result creates a generic card with unit math and live-pricing disclosure`() {
        val evaluation =
            GoodPriceCheckRouteCoordinator.checkBlock(
                rawBlock = "Secret Whole Milk\nCA$6.49\n4 L",
                observedAtEpochMillis = 100L,
                priceSelection = CompareHerePriceSelection.CURRENT,
                privateMemory = CompareHerePrivatePriceMemoryState.empty()
            )

        val card = requireNotNull(evaluation.state.shareCard)
        assertTrue(card.text.contains("6.49 CAD"))
        assertTrue(card.text.contains("4000 mL"))
        assertTrue(card.text.contains("1.6225 CAD/L"))
        assertTrue(card.text.contains("not live store pricing"))
        assertFalse(card.text.contains("Secret Whole Milk"))
        assertFalse(card.preview.contains("Secret Whole Milk"))
    }

    @Test
    fun `personal history remains private even when the answer uses it`() {
        val first =
            GoodPriceCheckRouteCoordinator.checkBlock(
                rawBlock = "Secret Coffee\nCA$7.49\n1 kg",
                observedAtEpochMillis = 100L,
                priceSelection = CompareHerePriceSelection.CURRENT,
                privateMemory = CompareHerePrivatePriceMemoryState.empty()
            )
        val history =
            CompareHerePrivatePriceMemoryStateManager.append(
                CompareHerePrivatePriceMemoryState.empty(),
                requireNotNull(first.privateMemoryCapture)
            )
        val second =
            GoodPriceCheckRouteCoordinator.checkBlock(
                rawBlock = "Secret Coffee\nCA$6.49\n1 kg",
                observedAtEpochMillis = 200L,
                priceSelection = CompareHerePriceSelection.CURRENT,
                privateMemory = history
            )

        val result = requireNotNull(second.state.result)
        assertTrue(result.historyText.orEmpty().contains("Personal history"))
        val card = requireNotNull(second.state.shareCard)
        assertFalse(card.text.contains("Secret Coffee"))
        assertFalse(card.text.contains("Personal history"))
        assertFalse(card.preview.contains("Below your last remembered price"))
    }

    @Test
    fun `unsafe display facts fail closed instead of entering a share card`() {
        val state =
            GoodPriceCheckUiState(
                headline = "Is this a good price?",
                priceModeText = "Price you entered",
                productName = "Private product",
                priceText = "6.49\u0000 CAD",
                quantityText = "4000 mL",
                unitRateText = "1.6225 CAD/L",
                answerTitle = "Not enough history yet",
                answerGuidance = "Personal context is limited.",
                answerTone = GoodPriceCheckAnswerTone.NEUTRAL,
                historyText = null,
                disclosure = "Not live store pricing."
            )

        assertNull(GoodPriceShareCardProjector.project(state))
    }
}
