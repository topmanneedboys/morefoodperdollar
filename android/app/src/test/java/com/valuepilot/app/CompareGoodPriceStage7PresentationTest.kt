package com.valuepilot.app

import com.valuepilot.core.CompareHereCandidate
import com.valuepilot.core.CompareHereComparisonIntentKey
import com.valuepilot.core.CompareHereEvaluator
import com.valuepilot.core.CompareHerePriceSelection
import com.valuepilot.core.Money
import com.valuepilot.core.NormalizedQuantity
import com.valuepilot.core.Offer
import com.valuepilot.core.QuantityNormalization
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Focused Stage 7 regression coverage for the consumer Compare and Good Price language. */
class CompareGoodPriceStage7PresentationTest {

    private val intent = CompareHereComparisonIntentKey("stage7:compare")

    @Test
    fun `compare presents a supported winner without engine language`() {
        val state =
            project(
                candidate("a", "5.20", QuantityNormalization.grams(500)),
                candidate("b", "8.60", QuantityNormalization.grams(1_000))
            )

        assertEquals(CompareHereUiStatus.READY, state.status)
        assertEquals("Better value", state.statusTitle)
        assertEquals("The lower unit price is the better value.", state.guidance)
        assertEquals(listOf("Choice 2", "Choice 1"), state.rows.map { it.title })
        assertTrue(state.rows.first().bestValue)
        assertTrue(
            listOf(state.headline, state.statusTitle, state.guidance).none {
                it.contains("evaluator", ignoreCase = true) ||
                    it.contains("normalization", ignoreCase = true) ||
                    it.contains("evidence class", ignoreCase = true)
            }
        )
    }

    @Test
    fun `compare exposes same value for an exact tie`() {
        val state =
            project(
                candidate("a", "4.00", QuantityNormalization.grams(500)),
                candidate("b", "8.00", QuantityNormalization.grams(1_000))
            )

        assertEquals("Same value", state.statusTitle)
        assertTrue(state.rows.all { it.bestValue })
        assertEquals(
            "Same value. Choice 1. 4.00 CAD. 500 g. 8 CAD/kg.",
            compareHereExactCardContentDescription(state.rows.first(), sameValue = true)
        )
    }

    @Test
    fun `compare normalizes compatible package sizes before showing winner`() {
        val state =
            project(
                candidate("small", "4.00", QuantityNormalization.grams(500)),
                candidate("large", "7.00", QuantityNormalization.grams(1_000))
            )

        assertEquals("Choice 2", state.rows.first().title)
        assertEquals("7 CAD/kg", state.rows.first().unitRateText)
        assertEquals("Better value", state.statusTitle)
    }

    @Test
    fun `compare keeps unknown package size explicit and does not pick a winner`() {
        val state =
            project(
                candidate("known", "4.00", QuantityNormalization.grams(500)),
                candidate("unknown", "3.00", null)
            )

        assertEquals(CompareHereUiStatus.NOT_ENOUGH_DATA, state.status)
        assertEquals("Can't compare yet", state.statusTitle)
        assertEquals("Package size unknown", state.blockedRows.single().reasonText)
        assertTrue(state.rows.none { it.bestValue })
    }

    @Test
    fun `compare keeps different currencies separate`() {
        val state =
            project(
                candidate("cad", "4.00", QuantityNormalization.grams(500)),
                candidate("usd", "4.00", QuantityNormalization.grams(500), currency = "USD")
            )

        assertEquals(CompareHereUiStatus.INCOMPATIBLE_DIMENSIONS, state.status)
        assertEquals("Can't compare yet", state.statusTitle)
        assertEquals("Different currencies.", state.guidance)
        assertTrue(state.rows.none { it.bestValue })
    }

    @Test
    fun `compare keeps missing selected member price unknown`() {
        val result =
            CompareHereEvaluator.evaluate(
                comparisonIntentKey = intent,
                priceSelection = CompareHerePriceSelection.MEMBER,
                candidates =
                    listOf(
                        candidate("a", "4.00", QuantityNormalization.grams(500), member = "3.00"),
                        candidate("b", "5.00", QuantityNormalization.grams(500))
                    )
            )
        val state =
            CompareHereUiProjector.project(result, metadata("a" to "Choice A", "b" to "Choice B")).state

        assertEquals(CompareHereUiStatus.NOT_ENOUGH_DATA, state.status)
        assertEquals("Member price unavailable", state.blockedRows.single().reasonText)
        assertTrue(state.rows.none { it.bestValue })
    }

    @Test
    fun `good price with history describes personal context plainly`() {
        val first = check("Whole Milk\nCA$7.49\n4 L", observedAt = 100L)
        val history =
            CompareHerePrivatePriceMemoryStateManager.append(
                CompareHerePrivatePriceMemoryState.empty(),
                requireNotNull(first.privateMemoryCapture)
            )
        val result = check("Whole Milk\nCA$6.49\n4 L", observedAt = 200L, memory = history).result()

        assertEquals("Lower than your last recorded price", result.answerTitle)
        assertTrue(result.historyText.orEmpty().contains("Personal history"))
        assertTrue(result.disclosure.contains("personal price history"))
        assertFalse(result.answerTitle.contains("evidence", ignoreCase = true))
    }

    @Test
    fun `good price with no history asks the shopper to record prices`() {
        val result = check("Whole Milk\nCA$6.49\n4 L").result()

        assertEquals("Not enough history", result.answerTitle)
        assertEquals("Record prices or purchases to compare later.", result.answerGuidance)
        assertNull(result.historyText)
    }

    @Test
    fun `good price does not match a different package`() {
        val first = check("Whole Milk\nCA$7.49\n4 L", observedAt = 100L)
        val history =
            CompareHerePrivatePriceMemoryStateManager.append(
                CompareHerePrivatePriceMemoryState.empty(),
                requireNotNull(first.privateMemoryCapture)
            )
        val result = check("Whole Milk\nCA$6.49\n2 L", observedAt = 200L, memory = history).result()

        assertEquals("Not enough history", result.answerTitle)
        assertNull(result.historyText)
    }

    @Test
    fun `good price does not merge a different currency into personal history`() {
        val first = check("Whole Milk\nCA$7.49\n4 L", observedAt = 100L)
        val history =
            CompareHerePrivatePriceMemoryStateManager.append(
                CompareHerePrivatePriceMemoryState.empty(),
                requireNotNull(first.privateMemoryCapture)
            )
        val result = check("Whole Milk\nUS$6.49\n4 L", observedAt = 200L, memory = history).result()

        assertEquals("Not enough history", result.answerTitle)
        assertNull(result.historyText)
    }

    @Test
    fun `member price stays visibly distinct from personal history`() {
        val first = check(
            rawBlock = "Whole Milk\nMember price CA$7.49\n4 L",
            observedAt = 100L,
            priceSelection = CompareHerePriceSelection.MEMBER
        )
        val history =
            CompareHerePrivatePriceMemoryStateManager.append(
                CompareHerePrivatePriceMemoryState.empty(),
                requireNotNull(first.privateMemoryCapture)
            )
        val result =
            check(
                rawBlock = "Whole Milk\nMember price CA$6.49\n4 L",
                observedAt = 200L,
                priceSelection = CompareHerePriceSelection.MEMBER,
                memory = history
            ).result()

        assertEquals("Member price", result.priceModeText)
        assertEquals("Lower than your last recorded price", result.answerTitle)
        assertTrue(result.disclosure.contains("personal price history"))
        assertFalse(result.disclosure.contains("authorized", ignoreCase = true))
    }

    private fun project(vararg candidates: CompareHereCandidate): CompareHereUiState =
        CompareHereUiProjector.project(
            CompareHereEvaluator.evaluate(
                comparisonIntentKey = intent,
                priceSelection = CompareHerePriceSelection.CURRENT,
                candidates = candidates.toList()
            ),
            metadata(
                *candidates
                    .mapIndexed { index, candidate -> candidate.candidateId to "Choice ${index + 1}" }
                    .toTypedArray()
            )
        ).state

    private fun candidate(
        id: String,
        price: String,
        quantity: NormalizedQuantity?,
        member: String? = null,
        currency: String = "CAD"
    ): CompareHereCandidate =
        CompareHereCandidate(
            candidateId = id,
            comparisonIntentKey = intent,
            offer = Offer(current = Money.parse(price, currency), member = member?.let { Money.parse(it, currency) }),
            quantity = quantity
        )

    private fun metadata(vararg pairs: Pair<String, String>): CompareHereDisplayMetadata =
        CompareHereDisplayMetadata(pairs.map { (id, label) -> CompareHereDisplayMetadataEntry(id, label) })

    private fun check(
        rawBlock: String,
        observedAt: Long = 100L,
        priceSelection: CompareHerePriceSelection = CompareHerePriceSelection.CURRENT,
        memory: CompareHerePrivatePriceMemoryState = CompareHerePrivatePriceMemoryState.empty()
    ): GoodPriceCheckRouteEvaluation =
        GoodPriceCheckRouteCoordinator.checkBlock(
            rawBlock = rawBlock,
            observedAtEpochMillis = observedAt,
            priceSelection = priceSelection,
            privateMemory = memory
        )

    private fun GoodPriceCheckRouteEvaluation.result(): GoodPriceCheckUiState =
        requireNotNull(state.result)
}
