package com.valuepilot.app

import com.valuepilot.core.CompareHerePriceSelection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GoodPriceCheckRouteCoordinatorTest {

    @Test
    fun `blank input stays at the bounded product entry state`() {
        val evaluation = check("   ")

        assertEquals(GoodPriceCheckRouteStatus.NEEDS_PRODUCT, evaluation.state.status)
        assertNull(evaluation.state.result)
        assertNull(evaluation.privateMemoryCapture)
    }

    @Test
    fun `one exact current price is projected and captured as good price memory`() {
        val evaluation = check("Whole Milk\nCA$6.49\n4 L", observedAt = 100L)

        assertEquals(GoodPriceCheckRouteStatus.EVALUATED, evaluation.state.status)
        val result = requireNotNull(evaluation.state.result)
        assertEquals("Whole Milk", result.productName)
        assertEquals("Price you entered", result.priceModeText)
        assertEquals("6.49 CAD", result.priceText)
        assertEquals("4000 mL", result.quantityText)
        assertEquals("1.6225 CAD/L", result.unitRateText)
        assertEquals("Not enough history yet", result.answerTitle)
        assertTrue(result.answerGuidance.contains("not enough matching evidence"))
        assertEquals(GoodPriceCheckAnswerTone.NEUTRAL, result.answerTone)
        assertNull(result.historyText)
        assertTrue(result.disclosure.contains("Not live store pricing"))
        val shareCard = requireNotNull(evaluation.state.shareCard)
        assertTrue(shareCard.text.contains("1.6225 CAD/L"))
        assertFalse(shareCard.text.contains("Whole Milk"))

        val capture = requireNotNull(evaluation.privateMemoryCapture)
        assertEquals(1, capture.entries.size)
        assertEquals(
            CompareHerePrivatePriceMemorySource.CONFIRMED_GOOD_PRICE_CHECK,
            capture.entries.single().source
        )
    }

    @Test
    fun `a lower repeat is compared with private history and remembered again`() {
        val first = check("Whole Milk\nCA$7.49\n4 L", observedAt = 100L)
        val firstCapture = requireNotNull(first.privateMemoryCapture)
        val history =
            CompareHerePrivatePriceMemoryStateManager.append(
                CompareHerePrivatePriceMemoryState.empty(),
                firstCapture
            )

        val second =
            GoodPriceCheckRouteCoordinator.checkBlock(
                rawBlock = "Whole Milk\nCA$6.49\n4 L",
                observedAtEpochMillis = 200L,
                priceSelection = CompareHerePriceSelection.CURRENT,
                privateMemory = history
            )

        val result = requireNotNull(second.state.result)
        assertEquals("Below your last remembered price", result.answerTitle)
        assertEquals(GoodPriceCheckAnswerTone.POSITIVE, result.answerTone)
        assertTrue(result.answerGuidance.contains("about 13.4% lower per unit"))
        assertTrue(result.historyText.orEmpty().contains("Personal history: 2 observations"))
        assertTrue(result.historyText.orEmpty().contains("lowest 1.6225 CAD/L"))
        assertEquals(
            CompareHerePrivatePriceMemorySource.CONFIRMED_GOOD_PRICE_CHECK,
            requireNotNull(second.privateMemoryCapture).entries.single().source
        )
    }

    @Test
    fun `replaying the same exact observation excludes itself from restored history`() {
        val first = check("Whole Milk\nCA$6.49\n4 L", observedAt = 100L)
        val history =
            CompareHerePrivatePriceMemoryStateManager.append(
                CompareHerePrivatePriceMemoryState.empty(),
                requireNotNull(first.privateMemoryCapture)
            )

        val replay =
            GoodPriceCheckRouteCoordinator.checkBlock(
                rawBlock = "Whole Milk\nCA$6.49\n4 L",
                observedAtEpochMillis = 100L,
                priceSelection = CompareHerePriceSelection.CURRENT,
                privateMemory = history
            )

        val result = requireNotNull(replay.state.result)
        assertEquals("Not enough history yet", result.answerTitle)
        assertNull(result.historyText)
    }

    @Test
    fun `a higher repeat quantifies the personal rate increase`() {
        val first = check("Whole Milk\nCA$6.49\n4 L", observedAt = 100L)
        val history =
            CompareHerePrivatePriceMemoryStateManager.append(
                CompareHerePrivatePriceMemoryState.empty(),
                requireNotNull(first.privateMemoryCapture)
            )

        val second =
            GoodPriceCheckRouteCoordinator.checkBlock(
                rawBlock = "Whole Milk\nCA$7.49\n4 L",
                observedAtEpochMillis = 200L,
                priceSelection = CompareHerePriceSelection.CURRENT,
                privateMemory = history
            )

        val result = requireNotNull(second.state.result)
        assertEquals("Above your last remembered price", result.answerTitle)
        assertEquals(GoodPriceCheckAnswerTone.CAUTION, result.answerTone)
        assertTrue(result.answerGuidance.contains("about 15.4% higher per unit"))
    }

    @Test
    fun `member selection never falls back to the current price`() {
        val evaluation =
            check(
                rawBlock = "Whole Milk\nCurrent price CA$8.00\n4 L",
                priceSelection = CompareHerePriceSelection.MEMBER
            )

        assertEquals(GoodPriceCheckRouteStatus.NEEDS_EXACT_INFORMATION, evaluation.state.status)
        assertTrue(evaluation.state.guidance.contains("Member price unavailable"))
        assertNull(evaluation.state.result)
        assertNull(evaluation.privateMemoryCapture)
    }

    @Test
    fun `estimated quantity remains unknown and is never remembered`() {
        val evaluation = check("Whole Milk\nCA$6.49\n3-4 L")

        assertEquals(GoodPriceCheckRouteStatus.NEEDS_EXACT_INFORMATION, evaluation.state.status)
        assertEquals("Need an exact price and package", evaluation.state.title)
        assertTrue(evaluation.state.guidance.contains("Package quantity needed"))
        assertNull(evaluation.privateMemoryCapture)
    }

    @Test
    fun `ambiguous currency is rejected without echoing user text`() {
        val evaluation = check("Secret Product\n$6.49\n4 L")

        assertEquals(GoodPriceCheckRouteStatus.PRODUCT_REJECTED, evaluation.state.status)
        assertNull(evaluation.privateMemoryCapture)
        val messages = listOf(evaluation.state.title, evaluation.state.guidance)
        assertFalse(messages.any { it.contains("Secret Product") })
    }

    @Test
    fun `input limits fail closed before parsing`() {
        val evaluation = check("X".repeat(ManualProductObservationAdapter.MAX_INPUT_CHARS + 1))

        assertEquals(GoodPriceCheckRouteStatus.INPUT_TOO_LONG, evaluation.state.status)
        assertNull(evaluation.privateMemoryCapture)
    }

    private fun check(
        rawBlock: String,
        observedAt: Long = 100L,
        priceSelection: CompareHerePriceSelection = CompareHerePriceSelection.CURRENT
    ): GoodPriceCheckRouteEvaluation =
        GoodPriceCheckRouteCoordinator.checkBlock(
            rawBlock = rawBlock,
            observedAtEpochMillis = observedAt,
            priceSelection = priceSelection,
            privateMemory = CompareHerePrivatePriceMemoryState.empty()
        )
}
