package com.valuepilot.app

import com.valuepilot.core.CompareHerePriceSelection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompareHereManualRouteCoordinatorTest {

    @Test
    fun `like for like confirmation is required before parser or comparison can run`() {
        var parserCalls = 0
        val parser = ProductParser { _, _ ->
            parserCalls += 1
            error("Parser must not run before user confirmation")
        }

        val state =
            CompareHereManualRouteCoordinator.compareBlocks(
                rawBlocks = exactMilkBlocks(),
                observedAtEpochMillis = 1L,
                userConfirmedLikeForLike = false,
                parser = parser
            )

        assertEquals(CompareHereManualRouteStatus.NEEDS_LIKE_FOR_LIKE_CONFIRMATION, state.status)
        assertEquals(0, parserCalls)
        assertNull(state.comparisonState)
        assertEquals(0, state.rejectedProductCount)
    }

    @Test
    fun `fewer than two nonblank products is rejected before confirmation matters`() {
        val state =
            CompareHereManualRouteCoordinator.compareBlocks(
                rawBlocks = listOf("Small Milk\nCA$4.00\n500 g", "   "),
                observedAtEpochMillis = 1L,
                userConfirmedLikeForLike = true
            )

        assertEquals(CompareHereManualRouteStatus.NEEDS_PRODUCTS, state.status)
        assertNull(state.comparisonState)
    }

    @Test
    fun `route enforces exact Compare Here 32 product limit before parser runs`() {
        var parserCalls = 0
        val parser = ProductParser { _, _ ->
            parserCalls += 1
            error("Parser must not run above route limit")
        }
        val blocks =
            (1..33).map { index -> "Milk $index\nCA$4.00\n500 g" }

        val state =
            CompareHereManualRouteCoordinator.compareBlocks(
                rawBlocks = blocks,
                observedAtEpochMillis = 1L,
                userConfirmedLikeForLike = true,
                parser = parser
            )

        assertEquals(32, CompareHereManualInputAdapter.MAX_OBSERVATIONS)
        assertEquals(CompareHereManualRouteStatus.TOO_MANY_PRODUCTS, state.status)
        assertEquals(0, parserCalls)
        assertTrue(state.guidance.contains("32"))
        assertNull(state.comparisonState)
    }

    @Test
    fun `confirmed exact products evaluate through exact core and expose projected state only`() {
        val state =
            CompareHereManualRouteCoordinator.compareBlocks(
                rawBlocks = exactMilkBlocks(),
                observedAtEpochMillis = 1L,
                userConfirmedLikeForLike = true,
                priceSelection = CompareHerePriceSelection.CURRENT
            )

        assertEquals(CompareHereManualRouteStatus.EVALUATED, state.status)
        val comparison = requireNotNull(state.comparisonState)
        assertEquals(CompareHereUiStatus.READY, comparison.status)
        assertEquals(listOf("Large Milk", "Small Milk"), comparison.rows.map { it.title })
        assertEquals(listOf(1, 2), comparison.rows.map { it.valueRank })
        assertTrue(comparison.rows.first().bestValue)
        assertFalse(comparison.rows.last().bestValue)
        assertEquals(0, state.rejectedProductCount)
        assertTrue(allConsumerStrings(state).none { it.contains("manual-") })
    }

    @Test
    fun `estimated quantity remains an evaluated typed core blocker`() {
        val state =
            CompareHereManualRouteCoordinator.compareBlocks(
                rawBlocks =
                    listOf(
                        "Known Milk\nCA$4.00\n500 g",
                        "Range Milk\nCA$5.00\n500-700 g"
                    ),
                observedAtEpochMillis = 1L,
                userConfirmedLikeForLike = true
            )

        assertEquals(CompareHereManualRouteStatus.EVALUATED, state.status)
        val comparison = requireNotNull(state.comparisonState)
        assertEquals(CompareHereUiStatus.NOT_ENOUGH_DATA, comparison.status)
        assertEquals(listOf("Known Milk"), comparison.rows.map { it.title })
        assertNull(comparison.rows.single().valueRank)
        assertEquals(listOf("Range Milk"), comparison.blockedRows.map { it.title })
        assertEquals("Package quantity needed", comparison.blockedRows.single().reasonText)
    }

    @Test
    fun `pre core rejection becomes safe route failure with no partial winner projection`() {
        val state =
            CompareHereManualRouteCoordinator.compareBlocks(
                rawBlocks =
                    listOf(
                        "Small Milk\nCA$4.00\n500 g",
                        "Large Milk\nCA$7.00\n1 kg",
                        "Ambiguous Milk\n$2.00\n500 g"
                    ),
                observedAtEpochMillis = 1L,
                userConfirmedLikeForLike = true
            )

        assertEquals(CompareHereManualRouteStatus.PRODUCTS_REJECTED, state.status)
        assertEquals(1, state.rejectedProductCount)
        assertNull(state.comparisonState)
        assertTrue(allConsumerStrings(state).none { it.contains("manual-") })
        assertTrue(allConsumerStrings(state).none { it.contains("Ambiguous Milk") })
    }

    @Test
    fun `oversized product block maps to safe route state without parsing`() {
        var parserCalls = 0
        val parser = ProductParser { _, _ ->
            parserCalls += 1
            null
        }
        val oversized = "X".repeat(ManualProductObservationAdapter.MAX_BLOCK_CHARS + 1)

        val state =
            CompareHereManualRouteCoordinator.compareBlocks(
                rawBlocks = listOf(oversized, "Milk\nCA$4.00\n500 g"),
                observedAtEpochMillis = 1L,
                userConfirmedLikeForLike = true,
                parser = parser
            )

        assertEquals(CompareHereManualRouteStatus.PRODUCT_BLOCK_TOO_LONG, state.status)
        assertEquals(0, parserCalls)
        assertNull(state.comparisonState)
    }

    @Test
    fun `input length failure maps to safe route state without echoing raw input`() {
        val huge = "Y".repeat(ManualProductObservationAdapter.MAX_INPUT_CHARS + 1)

        val state =
            CompareHereManualRouteCoordinator.compareBlocks(
                rawBlocks = listOf(huge, "Milk\nCA$4.00\n500 g"),
                observedAtEpochMillis = 1L,
                userConfirmedLikeForLike = true
            )

        assertEquals(CompareHereManualRouteStatus.INPUT_TOO_LONG, state.status)
        assertNull(state.comparisonState)
        assertTrue(allConsumerStrings(state).none { it.contains("YYYYYYYY") })
    }

    private fun exactMilkBlocks(): List<String> =
        listOf(
            "Small Milk\nCA$4.00\n500 g",
            "Large Milk\nCA$7.00\n1 kg"
        )

    private fun allConsumerStrings(state: CompareHereManualRouteState): List<String> =
        buildList {
            add(state.title)
            add(state.guidance)
            state.comparisonState?.let { comparison ->
                add(comparison.headline)
                add(comparison.priceModeText)
                add(comparison.statusTitle)
                add(comparison.guidance)
                comparison.notice?.let(::add)
                comparison.rows.forEach { row ->
                    add(row.title)
                    add(row.priceText)
                    add(row.quantityText)
                    add(row.unitRateText)
                }
                comparison.blockedRows.forEach { row ->
                    add(row.title)
                    add(row.reasonText)
                }
            }
        }
}
