package com.valuepilot.app

import com.valuepilot.core.ProductionBestValueBlockedPresentationItem
import com.valuepilot.core.ProductionBestValuePresentationSnapshot
import com.valuepilot.core.ProductionCurrentPriceEligibilityBlocker
import com.valuepilot.core.ProductionUnitValueEligibilityBlocker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductionSearchRefreshGateTest {

    @Test
    fun `newer generations replace older projected state`() {
        val first =
            ProductionSearchRefreshGate.applySnapshot(
                current = ProductionSearchRefreshState(),
                incomingGeneration = 4L,
                snapshot = snapshot("first", evaluatedAt = 1_000L)
            )

        assertEquals(ProductionSearchRefreshDisposition.APPLIED, first.disposition)
        assertEquals(4L, first.state.latestGeneration)
        assertEquals("first", first.state.projection?.state?.blocked?.single()?.candidateId)

        val newer =
            ProductionSearchRefreshGate.applySnapshot(
                current = first.state,
                incomingGeneration = 6L,
                snapshot = snapshot("newer", evaluatedAt = 2_000L)
            )

        assertEquals(ProductionSearchRefreshDisposition.APPLIED, newer.disposition)
        assertEquals(6L, newer.state.latestGeneration)
        assertEquals(2_000L, newer.state.projection?.state?.evaluatedAtEpochMillis)
        assertEquals("newer", newer.state.projection?.state?.blocked?.single()?.candidateId)
    }

    @Test
    fun `older generation cannot replace newer projected state`() {
        val current =
            ProductionSearchRefreshGate.applySnapshot(
                current = ProductionSearchRefreshState(),
                incomingGeneration = 8L,
                snapshot = snapshot("current")
            ).state

        val stale =
            ProductionSearchRefreshGate.applySnapshot(
                current = current,
                incomingGeneration = 7L,
                snapshot = snapshot("stale")
            )

        assertEquals(ProductionSearchRefreshDisposition.STALE, stale.disposition)
        assertSame(current, stale.state)
        assertEquals("current", stale.state.projection?.state?.blocked?.single()?.candidateId)
    }

    @Test
    fun `identical same generation replay is idempotent`() {
        val sourceSnapshot = snapshot("same", evaluatedAt = 1_500L)
        val current =
            ProductionSearchRefreshGate.applySnapshot(
                current = ProductionSearchRefreshState(),
                incomingGeneration = 3L,
                snapshot = sourceSnapshot
            ).state

        val duplicate =
            ProductionSearchRefreshGate.applySnapshot(
                current = current,
                incomingGeneration = 3L,
                snapshot = snapshot("same", evaluatedAt = 1_500L)
            )

        assertEquals(ProductionSearchRefreshDisposition.DUPLICATE, duplicate.disposition)
        assertSame(current, duplicate.state)
    }

    @Test
    fun `different payload at same generation fails closed`() {
        val current =
            ProductionSearchRefreshGate.applySnapshot(
                current = ProductionSearchRefreshState(),
                incomingGeneration = 5L,
                snapshot = snapshot("original")
            ).state

        val conflict =
            ProductionSearchRefreshGate.applySnapshot(
                current = current,
                incomingGeneration = 5L,
                snapshot = snapshot("different")
            )

        assertEquals(ProductionSearchRefreshDisposition.GENERATION_CONFLICT, conflict.disposition)
        assertSame(current, conflict.state)
        assertEquals("original", conflict.state.projection?.state?.blocked?.single()?.candidateId)
    }

    @Test
    fun `newer clear prevents stale result from repopulating surface`() {
        val current =
            ProductionSearchRefreshGate.applySnapshot(
                current = ProductionSearchRefreshState(),
                incomingGeneration = 10L,
                snapshot = snapshot("visible")
            ).state

        val cleared = ProductionSearchRefreshGate.clear(current, incomingGeneration = 11L)
        assertEquals(ProductionSearchRefreshDisposition.APPLIED, cleared.disposition)
        assertEquals(11L, cleared.state.latestGeneration)
        assertNull(cleared.state.projection)

        val stale =
            ProductionSearchRefreshGate.applySnapshot(
                current = cleared.state,
                incomingGeneration = 10L,
                snapshot = snapshot("late-result")
            )

        assertEquals(ProductionSearchRefreshDisposition.STALE, stale.disposition)
        assertSame(cleared.state, stale.state)
        assertNull(stale.state.projection)
    }

    @Test
    fun `same generation cannot change between clear and snapshot`() {
        val cleared =
            ProductionSearchRefreshGate.clear(
                current = ProductionSearchRefreshState(),
                incomingGeneration = 2L
            ).state

        val conflict =
            ProductionSearchRefreshGate.applySnapshot(
                current = cleared,
                incomingGeneration = 2L,
                snapshot = snapshot("ambiguous")
            )

        assertEquals(ProductionSearchRefreshDisposition.GENERATION_CONFLICT, conflict.disposition)
        assertSame(cleared, conflict.state)
        assertNull(conflict.state.projection)

        val duplicateClear = ProductionSearchRefreshGate.clear(cleared, incomingGeneration = 2L)
        assertEquals(ProductionSearchRefreshDisposition.DUPLICATE, duplicateClear.disposition)
        assertSame(cleared, duplicateClear.state)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `negative generation is rejected`() {
        ProductionSearchRefreshGate.clear(
            current = ProductionSearchRefreshState(),
            incomingGeneration = -1L
        )
    }

    private fun snapshot(
        candidateId: String,
        evaluatedAt: Long = 1_000L
    ): ProductionBestValuePresentationSnapshot =
        ProductionBestValuePresentationSnapshot(
            evaluatedAtEpochMillis = evaluatedAt,
            groups = emptyList(),
            blockedItems =
                listOf(
                    ProductionBestValueBlockedPresentationItem(
                        candidateId = candidateId,
                        unitValueBlockers =
                            setOf(ProductionUnitValueEligibilityBlocker.PRICE_STAGE_BLOCKED),
                        priceBlockers =
                            setOf(
                                ProductionCurrentPriceEligibilityBlocker.CANDIDATE_NOT_ACCEPTANCE_RANKABLE
                            ),
                        unitValuePolicyBlockReasons = emptySet()
                    )
                )
        )
}
