package com.valuepilot.app

import com.valuepilot.core.ShoppingPlanEvidenceSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticalShoppingEvidenceAgingProjectionTest {

    @Test
    fun agingEvidenceRemainsExplicitInsteadOfBeingUpgradedOrDowngraded() {
        val text =
            PracticalShoppingUiProjector.formatEvidence(
                ShoppingPlanEvidenceSummary(
                    freshItemCount = 1,
                    staleItemCount = 1,
                    unknownFreshnessItemCount = 1,
                    agingItemCount = 1
                )
            )

        assertEquals("1 fresh · 1 aging · 1 stale · 1 unknown", text)
        assertTrue(text.contains("aging"))
    }
}
