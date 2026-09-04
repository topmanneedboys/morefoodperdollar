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

class PracticalShoppingPrivatePriceHistoryPresentationTest {

    @Test
    fun `empty history stays explicit and does not imply live coverage`() {
        val presentation =
            PracticalShoppingPrivatePriceHistoryPresentation.from(
                CompareHerePrivatePriceMemoryState.empty()
            )

        assertTrue(presentation.rows.isEmpty())
        assertEquals(0, presentation.omittedRowCount)
        assertTrue(presentation.message.contains("No private comparison observations"))
        assertTrue(presentation.message.contains("not live store prices"))
        assertTrue(presentation.message.contains("not a guarantee"))
    }

    @Test
    fun `exact package observations group and expose latest low high and source`() {
        val state =
            CompareHerePrivatePriceMemoryState(
                entries =
                    listOf(
                        entry(
                            id = "a",
                            name = "Whole Milk",
                            priceMinor = 629,
                            observedAt = 1_700_000_000_000L,
                            source = CompareHerePrivatePriceMemorySource.CONFIRMED_COMPARE_HERE
                        ),
                        entry(
                            id = "b",
                            name = "Whole   Milk",
                            priceMinor = 579,
                            observedAt = 1_700_000_100_000L,
                            source = CompareHerePrivatePriceMemorySource.CONFIRMED_GOOD_PRICE_CHECK
                        ),
                        entry(
                            id = "c",
                            name = "Whole Milk",
                            priceMinor = 649,
                            observedAt = 1_700_000_200_000L,
                            source = CompareHerePrivatePriceMemorySource.CONFIRMED_COMPARE_HERE
                        )
                    )
            )

        val presentation = PracticalShoppingPrivatePriceHistoryPresentation.from(state)

        assertEquals(1, presentation.rows.size)
        assertEquals(0, presentation.omittedRowCount)
        val row = presentation.rows.single()
        assertEquals("Whole Milk", row.displayName)
        assertEquals(3, row.observationCount)
        assertTrue(row.latestPriceText.contains("6.49"))
        assertTrue(row.latestUnitRateText.contains("6.49 CAD/item"))
        assertTrue(row.rangeText.contains("5.79 CAD/item"))
        assertTrue(row.rangeText.contains("6.49 CAD/item"))
        assertTrue(row.packageText.contains("1 items"))
        assertEquals("Current price", row.priceBasisText)
        assertTrue(row.latestObservedText.contains("UTC"))
        assertTrue(row.sourceText.contains("Scan & compare"))
        assertTrue(row.sourceText.contains("Is this a good price?"))
        assertTrue(presentation.message.contains("Whole Milk"))
        assertTrue(presentation.message.contains("not live store prices"))
    }

    @Test
    fun `member price and promotion stay in a separate exact history`() {
        val current =
            entry(
                id = "d",
                name = "Coffee",
                priceMinor = 899,
                observedAt = 5L,
                selection = CompareHerePriceSelection.CURRENT,
                promotionLabel = "2 for 1",
                promotionReceivedUnits = 2L,
                promotionPaidUnits = 1L
            )
        val member =
            entry(
                id = "e",
                name = "Coffee",
                priceMinor = 799,
                observedAt = 6L,
                selection = CompareHerePriceSelection.MEMBER,
                promotionLabel = "2 for 1",
                promotionReceivedUnits = 2L,
                promotionPaidUnits = 1L
            )

        val presentation =
            PracticalShoppingPrivatePriceHistoryPresentation.from(
                CompareHerePrivatePriceMemoryState(entries = listOf(current, member))
            )

        assertEquals(2, presentation.rows.size)
        assertTrue(presentation.rows.any { it.priceBasisText == "Current price" })
        assertTrue(presentation.rows.any { it.priceBasisText == "Member price" })
        assertTrue(presentation.rows.all { it.promotionText == "Promotion: 2 for 1" })
    }

    @Test
    fun `history rows are bounded and omitted count is deterministic`() {
        val entries =
            (1..40).map { index ->
                entry(
                    id = "id-$index",
                    name = "Product $index",
                    priceMinor = 100L + index,
                    observedAt = index.toLong()
                )
            }

        val presentation =
            PracticalShoppingPrivatePriceHistoryPresentation.from(
                CompareHerePrivatePriceMemoryState(entries = entries)
            )

        assertEquals(32, presentation.rows.size)
        assertEquals(8, presentation.omittedRowCount)
        assertTrue(presentation.message.contains("Showing 32 exact package histories"))
        assertTrue(presentation.message.contains("8 more are stored"))
        assertFalse(presentation.rows.any { it.displayName == "Product 1" })
        assertEquals("Product 40", presentation.rows.first().displayName)
    }

    private fun entry(
        id: String,
        name: String,
        priceMinor: Long,
        observedAt: Long,
        source: CompareHerePrivatePriceMemorySource =
            CompareHerePrivatePriceMemorySource.CONFIRMED_COMPARE_HERE,
        selection: CompareHerePriceSelection = CompareHerePriceSelection.CURRENT,
        promotionLabel: String? = null,
        promotionReceivedUnits: Long = 1L,
        promotionPaidUnits: Long = 1L
    ): CompareHerePrivatePriceMemoryEntry {
        val quantity = NormalizedQuantity(1_000_000L, BaseUnit.COUNT)
        val price = Money(priceMinor, "CAD")
        return CompareHerePrivatePriceMemoryEntry.fromExactCandidate(
            candidate =
                CompareHereExactCandidate(
                    candidateId = "candidate-$id",
                    comparisonIntentKey = CompareHereComparisonIntentKey("intent:history"),
                    selectedPrice = price,
                    quantity = quantity,
                    rate =
                        UnitRate(
                            currencyCode = "CAD",
                            currencyMicrosPerUnit = priceMinor * 10_000L,
                            unit = RateUnit.ITEM
                        )
                ),
            displayName = name,
            priceSelection = selection,
            promotionLabel = promotionLabel,
            promotionReceivedUnits = promotionReceivedUnits,
            promotionPaidUnits = promotionPaidUnits,
            observedAtEpochMillis = observedAt,
            source = source
        )
    }
}
