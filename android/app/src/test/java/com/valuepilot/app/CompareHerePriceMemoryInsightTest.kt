package com.valuepilot.app

import com.valuepilot.core.BaseUnit
import com.valuepilot.core.CompareHereComparisonIntentKey
import com.valuepilot.core.CompareHereExactCandidate
import com.valuepilot.core.CompareHerePriceSelection
import com.valuepilot.core.Money
import com.valuepilot.core.NormalizedQuantity
import com.valuepilot.core.RateUnit
import com.valuepilot.core.UnitRate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompareHerePriceMemoryInsightTest {

    @Test
    fun `one matching observation is described as last price not a range`() {
        val previous = entry("Milk", 600L, 12L)
        val current = entry("Milk", 550L, 11L, observedAt = 2L)

        val insight =
            CompareHerePriceMemoryEvaluator.assess(
                current = current,
                history = CompareHerePrivatePriceMemoryState(listOf(previous))
            )

        assertEquals(CompareHerePriceMemoryAssessment.LOWER_THAN_LAST, insight.assessment)
        assertEquals(1, insight.historicalObservationCount)
        assertTrue(CompareHerePriceMemoryInsightPresenter.describe(insight).contains("last"))
    }

    @Test
    fun `multiple observations produce below within and above personal range`() {
        val history =
            CompareHerePrivatePriceMemoryState(
                listOf(
                    entry("Milk", 600L, 12L, observedAt = 1L),
                    entry("Milk", 700L, 14L, observedAt = 2L),
                    entry("Milk", 650L, 13L, observedAt = 3L)
                )
            )

        val below = assess("Milk", 500L, 10L, history, 4L)
        val within = assess("Milk", 650L, 13L, history, 5L)
        val above = assess("Milk", 800L, 16L, history, 6L)

        assertEquals(CompareHerePriceMemoryAssessment.BELOW_PERSONAL_RANGE, below.assessment)
        assertEquals(CompareHerePriceMemoryAssessment.WITHIN_PERSONAL_RANGE, within.assessment)
        assertEquals(CompareHerePriceMemoryAssessment.ABOVE_PERSONAL_RANGE, above.assessment)
        assertEquals(3, above.historicalObservationCount)
        assertTrue(
            CompareHerePriceMemoryInsightPresenter.describe(below)
                .contains("Below your remembered range")
        )
    }

    @Test
    fun `history matching stays strict for label package and price basis`() {
        val previous = entry("Milk", 600L, 12L)
        val differentLabel = assess("Whole Milk", 500L, 10L, listOf(previous), 2L)
        val differentPackage =
            entry(
                "Milk",
                500L,
                10L,
                observedAt = 2L,
                quantity = NormalizedQuantity(1_000_000L, BaseUnit.COUNT)
            )
        val differentBasis =
            entry(
                "Milk",
                500L,
                10L,
                observedAt = 3L,
                priceSelection = CompareHerePriceSelection.MEMBER
            )

        assertEquals(
            CompareHerePriceMemoryAssessment.NO_MATCHING_HISTORY,
            differentLabel.assessment
        )
        assertEquals(
            CompareHerePriceMemoryAssessment.NO_MATCHING_HISTORY,
            CompareHerePriceMemoryEvaluator.assess(
                current = differentPackage,
                history = CompareHerePrivatePriceMemoryState(listOf(previous))
            ).assessment
        )
        assertEquals(
            CompareHerePriceMemoryAssessment.NO_MATCHING_HISTORY,
            CompareHerePriceMemoryEvaluator.assess(
                current = differentBasis,
                history = CompareHerePrivatePriceMemoryState(listOf(previous))
            ).assessment
        )
    }

    @Test
    fun `history summary exposes count lowest highest and latest without inventing trends`() {
        val older = entry("Milk", 600L, 12L, observedAt = 1L)
        val latest = entry("Milk", 700L, 14L, observedAt = 3L)
        val current = entry("Milk", 650L, 13L, observedAt = 4L)

        val summary =
            CompareHerePriceMemoryHistory.summarize(
                current = current,
                state = CompareHerePrivatePriceMemoryState(listOf(older, latest))
            )

        assertEquals(3, summary.observationCount)
        assertEquals(12_000_000L, summary.lowestRate.currencyMicrosPerUnit)
        assertEquals(14_000_000L, summary.highestRate.currencyMicrosPerUnit)
        assertEquals(13_000_000L, summary.lastRate.currencyMicrosPerUnit)
        assertTrue(
            CompareHerePriceMemoryInsightPresenter.describeHistory(summary)
                .contains("Personal history: 3 observations")
        )
    }

    private fun assess(
        name: String,
        priceMinor: Long,
        rateMicros: Long,
        history: CompareHerePrivatePriceMemoryState,
        observedAt: Long
    ): CompareHerePriceMemoryInsight =
        CompareHerePriceMemoryEvaluator.assess(
            current = entry(name, priceMinor, rateMicros, observedAt = observedAt),
            history = history
        )

    private fun assess(
        name: String,
        priceMinor: Long,
        rateMicros: Long,
        history: List<CompareHerePrivatePriceMemoryEntry>,
        observedAt: Long
    ): CompareHerePriceMemoryInsight =
        assess(
            name,
            priceMinor,
            rateMicros,
            CompareHerePrivatePriceMemoryState(history),
            observedAt
        )

    private fun entry(
        name: String,
        priceMinor: Long,
        rateMicros: Long,
        observedAt: Long = 1L,
        quantity: NormalizedQuantity = NormalizedQuantity(500_000_000L, BaseUnit.GRAM),
        priceSelection: CompareHerePriceSelection = CompareHerePriceSelection.CURRENT
    ): CompareHerePrivatePriceMemoryEntry =
        CompareHerePrivatePriceMemoryEntry.fromExactCandidate(
            candidate =
                CompareHereExactCandidate(
                    candidateId = "$name-$observedAt-$priceMinor",
                    comparisonIntentKey = CompareHereComparisonIntentKey("intent:milk"),
                    selectedPrice = Money(priceMinor, "CAD"),
                    quantity = quantity,
                    rate =
                        UnitRate(
                            "CAD",
                            rateMicros * 1_000_000L,
                            if (quantity.unit == BaseUnit.GRAM) RateUnit.KILOGRAM else RateUnit.ITEM
                        )
                ),
            displayName = name,
            priceSelection = priceSelection,
            promotionLabel = null,
            promotionReceivedUnits = 1L,
            promotionPaidUnits = 1L,
            observedAtEpochMillis = observedAt
        )
}
