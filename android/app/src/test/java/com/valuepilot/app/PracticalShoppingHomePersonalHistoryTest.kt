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
        assertTrue(rendered.privateMemoryExportActionVisible)
        assertTrue(rendered.privateMemoryForgetActionVisible)
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
        assertTrue(
            !PracticalShoppingHomeRenderer.render(
                LocalSamplePracticalShoppingDemo.initialModel().ui,
                requestDetails = null,
                privateMemory = CompareHerePrivatePriceMemoryState.empty()
            ).privateMemoryExportActionVisible
        )
        assertTrue(
            !PracticalShoppingHomeRenderer.render(
                LocalSamplePracticalShoppingDemo.initialModel().ui,
                requestDetails = null,
                privateMemory = CompareHerePrivatePriceMemoryState.empty()
            ).privateMemoryForgetActionVisible
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
    fun `matching package exposes deterministic last price and remembered range`() {
        val quantity = NormalizedQuantity(4_000_000_000L, BaseUnit.MILLILITRE)
        val memory =
            CompareHerePrivatePriceMemoryState(
                listOf(
                    entry("Milk", 649L, 6L, observedAt = 10L, quantity = quantity),
                    entry("  milk  ", 579L, 5L, observedAt = 20L, quantity = quantity)
                )
            )

        val notice =
            requireNotNull(
                PracticalShoppingHomePersonalHistory.noticeFor(
                    itemDisplayName = " MILK ",
                    memory = memory,
                    itemQuantity = quantity
                )
            )

        assertEquals(
            "Personal history: 2 observations for this name and package. " +
                "Last recorded 5.79 CAD (5 CAD/L) on 1970-01-01 00:00 UTC. " +
                "Remembered range 5 CAD/L–6 CAD/L. " +
                "Product identity, brand, promotion and store may differ; not live store pricing.",
            notice
        )
    }

    @Test
    fun `missing observation date stays explicit in exact package context`() {
        val quantity = NormalizedQuantity(4_000_000_000L, BaseUnit.MILLILITRE)
        val notice =
            requireNotNull(
                PracticalShoppingHomePersonalHistory.noticeFor(
                    itemDisplayName = "Milk",
                    memory =
                        CompareHerePrivatePriceMemoryState(
                            listOf(entry("Milk", 649L, 6L, observedAt = 0L, quantity = quantity))
                        ),
                    itemQuantity = quantity
                )
            )

        assertTrue(notice.contains("on date not recorded."))
        assertTrue(notice.contains("not live store pricing"))
    }

    @Test
    fun `mixed promotion shape falls back to cautious name-only notice`() {
        val quantity = NormalizedQuantity(4_000_000_000L, BaseUnit.MILLILITRE)
        val memory =
            CompareHerePrivatePriceMemoryState(
                listOf(
                    entry("Milk", 649L, 6L, observedAt = 10L, quantity = quantity),
                    entry(
                        "Milk",
                        579L,
                        5L,
                        observedAt = 20L,
                        quantity = quantity,
                        promotionLabel = "2 for 1"
                    )
                )
            )

        val notice =
            requireNotNull(
                PracticalShoppingHomePersonalHistory.noticeFor(
                    itemDisplayName = "Milk",
                    memory = memory,
                    itemQuantity = quantity
                )
            )

        assertEquals(
            "Private comparison history: 2 observations for this name. " +
                "Package and promotion details may differ; not live store pricing.",
            notice
        )
        assertTrue(!notice.contains("Last recorded"))
        assertTrue(!notice.contains("CAD"))
    }

    @Test
    fun `home forwards resolved package quantity for private history context`() {
        val model =
            PracticalShoppingHomeSession.submit(
                LocalSamplePracticalShoppingDemo.initialModel(),
                "milk"
            )
        val sourceResult = requireNotNull(model.ui.result)
        val quantity = NormalizedQuantity(4_000_000_000L, BaseUnit.MILLILITRE)
        val rendered =
            PracticalShoppingHomeRenderer.render(
                source = model.ui,
                requestDetails = null,
                privateMemory =
                    CompareHerePrivatePriceMemoryState(
                        listOf(entry("Milk", 649L, 6L, quantity = quantity))
                    )
            )

        assertSame(sourceResult, rendered.result)
        assertEquals(1, rendered.items.size)
        assertTrue(
            requireNotNull(rendered.items.single().personalHistoryNotice)
                .contains("Last recorded 6.49 CAD (6 CAD/L) on 1970-01-01 00:00 UTC")
        )
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
                "This is not live store pricing. Open Scan & compare prices to review it.\n" +
                "Name-matched personal history: 1 of 2 list items. This is not current-price coverage.",
            rendered.privateMemorySummary
        )
        assertEquals(
            listOf(null, "Private comparison history: 1 observation for this name. " +
                "Package and promotion details may differ; not live store pricing."),
            rendered.items.map { it.personalHistoryNotice }
        )
    }

    @Test
    fun `history coverage counts distinct normalized list names without claiming current prices`() {
        val summary =
            requireNotNull(
                PracticalShoppingHomePersonalHistory.summaryFor(
                    memory =
                        CompareHerePrivatePriceMemoryState(
                            listOf(
                                entry("Milk", 600L, 12L, observedAt = 1L),
                                entry("  milk  ", 700L, 14L, observedAt = 2L),
                                entry("Eggs", 500L, 10L, observedAt = 3L)
                            )
                        ),
                    requestedItemNames = listOf("MILK", "milk", "Bread")
                )
            )

        assertTrue(summary.contains("Name-matched personal history: 1 of 2 list items."))
        assertTrue(summary.contains("not current-price coverage"))
        assertTrue(!summary.contains("6.00"))
        assertTrue(!summary.contains("CAD"))
    }

    @Test
    fun `empty or blank requested names keep the general history summary`() {
        val memory =
            CompareHerePrivatePriceMemoryState(
                listOf(entry("Milk", 600L, 12L))
            )

        val summary = requireNotNull(
            PracticalShoppingHomePersonalHistory.summaryFor(
                memory = memory,
                requestedItemNames = listOf(" ", "\n")
            )
        )

        assertTrue(summary.startsWith("Private comparison history: 1 observation"))
        assertTrue(!summary.contains("Name-matched personal history"))
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
        assertTrue(rendered.privateMemoryReviewActionVisible)
        assertFalse(rendered.privateMemoryExportActionVisible)
        assertFalse(rendered.privateMemoryForgetActionVisible)
        assertEquals(listOf(null, null), rendered.items.map { it.personalHistoryNotice })
    }

    private fun entry(
        name: String,
        priceMinor: Long,
        rateMicros: Long,
        observedAt: Long = 1L,
        quantity: NormalizedQuantity = NormalizedQuantity(500_000_000L, BaseUnit.GRAM),
        priceSelection: CompareHerePriceSelection = CompareHerePriceSelection.CURRENT,
        promotionLabel: String? = null,
        promotionReceivedUnits: Long = 1L,
        promotionPaidUnits: Long = 1L
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
                            currencyCode = "CAD",
                            currencyMicrosPerUnit = rateMicros * 1_000_000L,
                            unit =
                                when (quantity.unit) {
                                    BaseUnit.GRAM -> RateUnit.KILOGRAM
                                    BaseUnit.MILLILITRE -> RateUnit.LITRE
                                    BaseUnit.COUNT -> RateUnit.ITEM
                                    BaseUnit.SQUARE_INCH -> RateUnit.SQUARE_INCH
                                }
                        )
                ),
            displayName = name,
            priceSelection = priceSelection,
            promotionLabel = promotionLabel,
            promotionReceivedUnits = promotionReceivedUnits,
            promotionPaidUnits = promotionPaidUnits,
            observedAtEpochMillis = observedAt
        )
}
