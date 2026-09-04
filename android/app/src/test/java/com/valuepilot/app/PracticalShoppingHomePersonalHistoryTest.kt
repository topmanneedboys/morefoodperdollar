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
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticalShoppingHomePersonalHistoryTest {

    @Test
    fun `summary makes nonempty private history discoverable without exposing price facts`() {
        val summary =
            requireNotNull(
                PracticalShoppingHomePersonalHistory.summaryFor(
                    CompareHerePrivatePriceMemoryState(
                        listOf(
                            entry("Milk", 600L, 12L, observedAt = 1L),
                            entry("Eggs", 700L, 14L, observedAt = 2L)
                        )
                    )
                )
            )

        assertEquals(
            "Private comparison history: 2 observations on this device. " +
                "Home shows matching context only; package and promotion details may differ. " +
                "This is not live store pricing. Open Scan & compare prices to review it.",
            summary
        )
        val rendered =
            PracticalShoppingHomeRenderer.render(
                LocalSamplePracticalShoppingDemo.initialModel().ui,
                requestDetails = null,
                privateMemory =
                    CompareHerePrivatePriceMemoryState(
                        listOf(entry("Milk", 600L, 12L, observedAt = 1L))
                    )
            )
        assertTrue(rendered.privateMemoryReviewActionVisible)
        assertTrue(!summary.contains("CAD"))
        assertTrue(!summary.contains("6.00"))
    }

    @Test
    fun `empty private history has no summary`() {
        assertNull(
            PracticalShoppingHomePersonalHistory.summaryFor(
                CompareHerePrivatePriceMemoryState.empty()
            )
        )
        assertTrue(
            !PracticalShoppingHomeRenderer.render(
                LocalSamplePracticalShoppingDemo.initialModel().ui,
                requestDetails = null,
                privateMemory = CompareHerePrivatePriceMemoryState.empty()
            ).privateMemoryReviewActionVisible
        )
    }

    @Test
    fun `notice counts normalized label history without exposing a price`() {
        val memory =
            CompareHerePrivatePriceMemoryState(
                listOf(
                    entry("Milk", 600L, 12L, observedAt = 1L),
                    entry("  milk  ", 700L, 14L, observedAt = 2L)
                )
            )

        val notice =
            requireNotNull(
                PracticalShoppingHomePersonalHistory.noticeFor(" MILK ", memory)
            )

        assertTrue(notice.contains("2 observations"))
        assertTrue(notice.contains("Package and promotion details may differ"))
        assertTrue(notice.contains("not live store pricing"))
        assertTrue(!notice.contains("CAD"))
    }

    @Test
    fun `different labels and empty memory stay undisclosed`() {
        val memory = CompareHerePrivatePriceMemoryState(listOf(entry("Whole milk", 600L, 12L)))

        assertNull(
            PracticalShoppingHomePersonalHistory.noticeFor("Milk", memory)
        )
        assertNull(
            PracticalShoppingHomePersonalHistory.noticeFor(
                "Milk",
                CompareHerePrivatePriceMemoryState.empty()
            )
        )
    }

    @Test
    fun `home rows expose history context without changing the projected plan`() {
        val model =
            PracticalShoppingHomeSession.submit(
                LocalSamplePracticalShoppingDemo.initialModel(),
                "eggs milk"
            )
        val sourceResult = requireNotNull(model.ui.result)
        val rendered =
            PracticalShoppingHomeRenderer.render(
                source = model.ui,
                requestDetails = null,
                privateMemory =
                    CompareHerePrivatePriceMemoryState(
                        listOf(entry("Milk", 600L, 12L))
                    )
            )

        assertSame(sourceResult, rendered.result)
        assertEquals(
            "Private comparison history: 1 observation on this device. " +
                "Home shows matching context only; package and promotion details may differ. " +
                "This is not live store pricing. Open Scan & compare prices to review it.",
            rendered.privateMemorySummary
        )
        assertEquals(
            listOf(null, "Private comparison history: 1 observation for this name. " +
                "Package and promotion details may differ; not live store pricing."),
            rendered.items.map { it.personalHistoryNotice }
        )
    }

    @Test
    fun `unavailable history is disclosed without exposing the unreadable rows`() {
        val model =
            PracticalShoppingHomeSession.submit(
                LocalSamplePracticalShoppingDemo.initialModel(),
                "eggs milk"
            )
        val sourceResult = requireNotNull(model.ui.result)
        val rendered =
            PracticalShoppingHomeRenderer.render(
                source = model.ui,
                requestDetails = null,
                privateMemory =
                    CompareHerePrivatePriceMemoryState(
                        listOf(entry("Milk", 600L, 12L))
                    ),
                privateMemoryStatus = PracticalShoppingHomePrivateMemoryStatus.UNAVAILABLE
            )

        assertSame(sourceResult, rendered.result)
        assertEquals(
            PracticalShoppingHomePrivateMemoryStatus.UNAVAILABLE,
            rendered.privateMemoryStatus
        )
        assertNull(rendered.privateMemorySummary)
        assertTrue(!rendered.privateMemoryReviewActionVisible)
        assertEquals(listOf(null, null), rendered.items.map { it.personalHistoryNotice })
    }

    private fun entry(
        name: String,
        priceMinor: Long,
        rateMicros: Long,
        observedAt: Long = 1L
    ): CompareHerePrivatePriceMemoryEntry =
        CompareHerePrivatePriceMemoryEntry.fromExactCandidate(
            candidate =
                CompareHereExactCandidate(
                    candidateId = "$name-$observedAt-$priceMinor",
                    comparisonIntentKey = CompareHereComparisonIntentKey("intent:milk"),
                    selectedPrice = Money(priceMinor, "CAD"),
                    quantity = NormalizedQuantity(500_000_000L, BaseUnit.GRAM),
                    rate =
                        UnitRate(
                            currencyCode = "CAD",
                            currencyMicrosPerUnit = rateMicros * 1_000_000L,
                            unit = RateUnit.KILOGRAM
                        )
                ),
            displayName = name,
            priceSelection = CompareHerePriceSelection.CURRENT,
            promotionLabel = null,
            promotionReceivedUnits = 1L,
            promotionPaidUnits = 1L,
            observedAtEpochMillis = observedAt
        )
}
