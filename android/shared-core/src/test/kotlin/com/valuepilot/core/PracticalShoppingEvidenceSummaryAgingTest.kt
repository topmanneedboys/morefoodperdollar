package com.valuepilot.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PracticalShoppingEvidenceSummaryAgingTest {

    @Test
    fun agingEvidenceCountsTowardCoveredItemsWithoutBecomingFreshOrStale() {
        val summary =
            ShoppingPlanEvidenceSummary(
                freshItemCount = 1,
                staleItemCount = 1,
                unknownFreshnessItemCount = 1,
                agingItemCount = 1
            )

        assertEquals(1, summary.freshItemCount)
        assertEquals(1, summary.agingItemCount)
        assertEquals(1, summary.staleItemCount)
        assertEquals(1, summary.unknownFreshnessItemCount)
        assertEquals(4, summary.totalItemCount)
    }

    @Test
    fun negativeAgingCountFailsClosed() {
        assertFailsWith<IllegalArgumentException> {
            ShoppingPlanEvidenceSummary(
                freshItemCount = 0,
                staleItemCount = 0,
                unknownFreshnessItemCount = 0,
                agingItemCount = -1
            )
        }
    }
}
