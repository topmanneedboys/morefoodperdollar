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

        assertTrue(presentation.identityCatalog.contains("No bundled product catalogue is shipped"))
        assertTrue(presentation.currentOffers.contains("None included"))
        assertTrue(presentation.storeDirectory.contains("No bundled store directory is shipped"))
        assertTrue(presentation.flyers.contains("None included"))
        assertTrue(presentation.connectivity.contains("No background syncing"))
        assertTrue(presentation.connectivity.contains("user-triggered Argentina identity Search"))
        assertTrue(presentation.demoData.contains("fictional"))
        assertTrue(presentation.demoData.contains("source-attributed names"))
        assertTrue(presentation.demoData.contains("not current offers"))
        assertTrue(presentation.message.contains("Unknown prices stay unknown"))
        assertTrue(presentation.message.contains("Product names"))
        assertTrue(presentation.message.contains("Live prices"))
        assertTrue(!presentation.message.contains("ACCESS_NETWORK_STATE"))
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
