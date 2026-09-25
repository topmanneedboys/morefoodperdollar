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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticalShoppingPrivatePriceHistoryExportTest {

    @Test
    fun `export is deterministic newest first and preview is bounded`() {
        val entries =
            (1..6).map { index ->
                entry(
                    name = "Product $index",
                    priceMinor = 600L + index,
                    observedAt = index.toLong(),
                    source =
                        if (index % 2 == 0) {
                            CompareHerePrivatePriceMemorySource.CONFIRMED_GOOD_PRICE_CHECK
                        } else {
                            CompareHerePrivatePriceMemorySource.CONFIRMED_COMPARE_HERE
                        }
                )
            }

        val first =
            PracticalShoppingPrivatePriceHistoryExport.from(
                CompareHerePrivatePriceMemoryState(entries)
            )
        val second =
            PracticalShoppingPrivatePriceHistoryExport.from(
                CompareHerePrivatePriceMemoryState(entries.reversed())
            )

        assertTrue(first.accepted)
        assertEquals(first.text, second.text)
        assertEquals(first.preview, second.preview)
        assertEquals(6, first.observationCount)
        val text = requireNotNull(first.text)
        val preview = requireNotNull(first.preview)
        assertTrue(text.startsWith("ValuePilot private price history"))
        assertTrue(text.contains("Observation count: 6"))
        assertTrue(text.indexOf("Product 6") < text.indexOf("Product 1"))
        assertTrue(text.contains("Price: 6.06 CAD"))
        assertTrue(text.contains("Package: 1 items"))
        assertTrue(text.contains("Source: Is this a good price?"))
        assertTrue(text.contains("not live store prices"))
        assertTrue(text.contains("not a guarantee"))
        assertTrue(preview.contains("Observation count: 6 (preview shows 5"))
        assertTrue(preview.contains("Product 6"))
        assertFalse(preview.contains("Product 1"))
    }

    @Test
    fun `empty history cannot produce an export`() {
        val result =
            PracticalShoppingPrivatePriceHistoryExport.from(
                CompareHerePrivatePriceMemoryState.empty()
            )

        assertFalse(result.accepted)
        assertEquals(
            PracticalShoppingPrivatePriceHistoryExportIssue.EMPTY,
            result.issue
        )
        assertEquals(0, result.observationCount)
    }

    @Test
    fun `oversized history fails closed without truncating a user copy`() {
        val state =
            CompareHerePrivatePriceMemoryState(
                entries =
                    (1..MAX_COMPARE_HERE_PRIVATE_MEMORY_ENTRIES).map { index ->
                        entry(
                            name = "N".repeat(640),
                            priceMinor = 600L + index,
                            observedAt = index.toLong(),
                            promotionLabel = "P".repeat(640)
                        )
                    }
            )

        val result = PracticalShoppingPrivatePriceHistoryExport.from(state)

        assertFalse(result.accepted)
        assertEquals(
            PracticalShoppingPrivatePriceHistoryExportIssue.OUTPUT_TOO_LARGE,
            result.issue
        )
        assertEquals(MAX_COMPARE_HERE_PRIVATE_MEMORY_ENTRIES, result.observationCount)
    }

    private fun entry(
        name: String,
        priceMinor: Long,
        observedAt: Long,
        source: CompareHerePrivatePriceMemorySource =
            CompareHerePrivatePriceMemorySource.CONFIRMED_COMPARE_HERE,
        promotionLabel: String? = null
    ): CompareHerePrivatePriceMemoryEntry {
        val quantity = NormalizedQuantity(1_000_000L, BaseUnit.COUNT)
        val price = Money(priceMinor, "CAD")
        return CompareHerePrivatePriceMemoryEntry.fromExactCandidate(
            candidate =
                CompareHereExactCandidate(
                    candidateId = "candidate-$observedAt-$priceMinor",
                    comparisonIntentKey = CompareHereComparisonIntentKey("intent:export"),
                    selectedPrice = price,
                    quantity = quantity,
                    rate = UnitRate("CAD", priceMinor * 1_000_000L, RateUnit.ITEM)
                ),
            displayName = name,
            priceSelection = CompareHerePriceSelection.CURRENT,
            promotionLabel = promotionLabel,
            promotionReceivedUnits = if (promotionLabel == null) 1L else 2L,
            promotionPaidUnits = 1L,
            observedAtEpochMillis = observedAt,
            source = source
        )
    }
}
