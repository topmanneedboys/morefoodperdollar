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

class PracticalShoppingDataStatusPresentationTest {

    @Test
    fun `empty status names the real identity rail and every absent live-data boundary`() {
        val presentation =
            PracticalShoppingDataStatusPresentation.from(
                privateMemory = CompareHerePrivatePriceMemoryState.empty()
            )

        assertTrue(presentation.identityCatalog.contains("30,000"))
        assertTrue(presentation.identityCatalog.contains("GTA"))
        assertTrue(presentation.identityCatalog.contains("Metro Vancouver"))
        assertTrue(presentation.currentOffers.contains("0 authorized current-offer records"))
        assertTrue(presentation.storeDirectory.contains("Not included"))
        assertTrue(presentation.flyers.contains("Not included"))
        assertTrue(presentation.connectivity.contains("no INTERNET"))
        assertTrue(presentation.demoData.contains("fictional"))
        assertTrue(presentation.message.contains("Unknown prices stay unknown"))
    }

    @Test
    fun `private observation count is explicit and pluralized`() {
        val state =
            CompareHerePrivatePriceMemoryState(
                entries = listOf(
                    memoryEntry("milk", 1),
                    memoryEntry("eggs", 2)
                )
            )

        val presentation = PracticalShoppingDataStatusPresentation.from(state)

        assertTrue(presentation.privateObservations.contains("2 private observations"))
        assertTrue(presentation.privateObservations.contains("not live retailer offers"))
    }

    @Test
    fun `unavailable private memory hides any supplied stale entries`() {
        val presentation =
            PracticalShoppingDataStatusPresentation.from(
                privateMemory = CompareHerePrivatePriceMemoryState(
                    entries = listOf(memoryEntry("stale", 3))
                ),
                privateMemoryAvailable = false
            )

        assertTrue(presentation.privateObservations.contains("Unavailable"))
        assertTrue(presentation.privateObservations.contains("stale observations are hidden"))
        assertTrue(!presentation.privateObservations.contains("3 private"))
    }

    @Test
    fun `same immutable inputs produce byte-for-byte stable dialog copy`() {
        val state = CompareHerePrivatePriceMemoryState.empty()

        assertEquals(
            PracticalShoppingDataStatusPresentation.from(state).message,
            PracticalShoppingDataStatusPresentation.from(state).message
        )
    }

    private fun memoryEntry(name: String, suffix: Int): CompareHerePrivatePriceMemoryEntry =
        CompareHerePrivatePriceMemoryEntry.fromExactCandidate(
            candidate =
                CompareHereExactCandidate(
                    candidateId = "candidate-$suffix",
                    comparisonIntentKey = CompareHereComparisonIntentKey("intent:status"),
                    selectedPrice = Money(100L, "CAD"),
                    quantity = NormalizedQuantity(1_000_000L, BaseUnit.COUNT),
                    rate = UnitRate("CAD", 100_000_000L, RateUnit.ITEM)
                ),
            displayName = name,
            priceSelection = CompareHerePriceSelection.CURRENT,
            promotionLabel = null,
            promotionReceivedUnits = 1L,
            promotionPaidUnits = 1L,
            observedAtEpochMillis = suffix.toLong(),
            source = CompareHerePrivatePriceMemorySource.CONFIRMED_COMPARE_HERE,
        )
}
