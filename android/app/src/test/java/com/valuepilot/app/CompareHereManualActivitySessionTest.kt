package com.valuepilot.app

import com.valuepilot.core.CompareHerePriceSelection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CompareHereManualActivitySessionTest {

    @Test
    fun `compare action needs two nonblank product entries`() {
        listOf(
            emptyList(),
            listOf(""),
            listOf("Milk", "   ")
        ).forEach { blocks ->
            val state =
                CompareHereManualDraftActionEvaluator.evaluate(
                    rawBlocks = blocks,
                    likeForLikeConfirmed = true
                )

            assertEquals(CompareHereManualDraftReadiness.ADD_PRODUCTS, state.readiness)
            assertFalse(state.compareEnabled)
        }
    }

    @Test
    fun `compare action needs explicit like for like confirmation`() {
        val state =
            CompareHereManualDraftActionEvaluator.evaluate(
                rawBlocks = listOf("Small milk", "Large milk"),
                likeForLikeConfirmed = false
            )

        assertEquals(CompareHereManualDraftReadiness.CONFIRM_LIKE_FOR_LIKE, state.readiness)
        assertFalse(state.compareEnabled)
    }

    @Test
    fun `confirmed two-entry draft enables exact route without parsing evidence in readiness`() {
        val state =
            CompareHereManualDraftActionEvaluator.evaluate(
                rawBlocks = listOf("unparsed first block", "unparsed second block"),
                likeForLikeConfirmed = true
            )

        assertEquals(CompareHereManualDraftReadiness.READY, state.readiness)
        assertTrue(state.compareEnabled)
    }

    @Test
    fun `draft action evaluation rejects an impossible activity slot count`() {
        assertThrows(IllegalArgumentException::class.java) {
            CompareHereManualDraftActionEvaluator.evaluate(
                rawBlocks = List(CompareHereManualInputAdapter.MAX_OBSERVATIONS + 1) { "product" },
                likeForLikeConfirmed = true
            )
        }
    }

    @Test
    fun `product change invalidates prior comparison and like for like confirmation`() {
        val prior =
            CompareHereManualActivitySessionState.restore(
                comparisonWasRun = true,
                observedAtEpochMillis = 123L,
                likeForLikeConfirmed = true
            )

        val next =
            CompareHereManualActivitySessionReducer.productsChanged(prior)

        assertFalse(next.comparisonWasRun)
        assertEquals(0L, next.observedAtEpochMillis)
        assertFalse(next.likeForLikeConfirmed)
    }

    @Test
    fun `confirmation change invalidates prior comparison but preserves requested checkbox state`() {
        val prior =
            CompareHereManualActivitySessionState.restore(
                comparisonWasRun = true,
                observedAtEpochMillis = 123L,
                likeForLikeConfirmed = false
            )

        val confirmed =
            CompareHereManualActivitySessionReducer.confirmationChanged(
                state = prior,
                confirmed = true
            )

        assertFalse(confirmed.comparisonWasRun)
        assertEquals(0L, confirmed.observedAtEpochMillis)
        assertTrue(confirmed.likeForLikeConfirmed)

        val unconfirmed =
            CompareHereManualActivitySessionReducer.confirmationChanged(
                state = confirmed.copy(
                    comparisonWasRun = true,
                    observedAtEpochMillis = 456L
                ),
                confirmed = false
            )

        assertFalse(unconfirmed.comparisonWasRun)
        assertEquals(0L, unconfirmed.observedAtEpochMillis)
        assertFalse(unconfirmed.likeForLikeConfirmed)
    }

    @Test
    fun `comparison attempt records time without changing semantic confirmation`() {
        val confirmed =
            CompareHereManualActivitySessionState.restore(
                comparisonWasRun = false,
                observedAtEpochMillis = 0L,
                likeForLikeConfirmed = true
            )

        val next =
            CompareHereManualActivitySessionReducer.comparisonAttempted(
                state = confirmed,
                observedAtEpochMillis = 999L
            )

        assertTrue(next.comparisonWasRun)
        assertEquals(999L, next.observedAtEpochMillis)
        assertTrue(next.likeForLikeConfirmed)
    }

    @Test
    fun `price basis change invalidates prior comparison but preserves confirmation`() {
        val prior =
            CompareHereManualActivitySessionState.restore(
                comparisonWasRun = true,
                observedAtEpochMillis = 123L,
                likeForLikeConfirmed = true,
                priceSelection = CompareHerePriceSelection.CURRENT
            )

        val next =
            CompareHereManualActivitySessionReducer.priceSelectionChanged(
                state = prior,
                selection = CompareHerePriceSelection.MEMBER
            )

        assertFalse(next.comparisonWasRun)
        assertEquals(0L, next.observedAtEpochMillis)
        assertTrue(next.likeForLikeConfirmed)
        assertEquals(CompareHerePriceSelection.MEMBER, next.priceSelection)
    }

    @Test
    fun `restore preserves unchanged confirmed draft while legacy restore defaults unconfirmed`() {
        val confirmed =
            CompareHereManualActivitySessionState.restore(
                comparisonWasRun = true,
                observedAtEpochMillis = 123L,
                likeForLikeConfirmed = true
            )
        assertTrue(confirmed.comparisonWasRun)
        assertEquals(123L, confirmed.observedAtEpochMillis)
        assertTrue(confirmed.likeForLikeConfirmed)
        assertEquals(CompareHerePriceSelection.CURRENT, confirmed.priceSelection)

        val legacy =
            CompareHereManualActivitySessionState.restore(
                comparisonWasRun = true,
                observedAtEpochMillis = 123L
            )
        assertTrue(legacy.comparisonWasRun)
        assertEquals(123L, legacy.observedAtEpochMillis)
        assertFalse(legacy.likeForLikeConfirmed)
        assertEquals(CompareHerePriceSelection.CURRENT, legacy.priceSelection)
    }

    @Test
    fun `clear returns initial unconfirmed state`() {
        val next = CompareHereManualActivitySessionReducer.clear()

        assertFalse(next.comparisonWasRun)
        assertEquals(0L, next.observedAtEpochMillis)
        assertFalse(next.likeForLikeConfirmed)
        assertEquals(CompareHerePriceSelection.CURRENT, next.priceSelection)
    }
}
