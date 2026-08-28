package com.valuepilot.app

import com.valuepilot.core.ProductionBestValueBlockedPresentationItem
import com.valuepilot.core.ProductionBestValuePresentationSnapshot
import com.valuepilot.core.ProductionCurrentPriceEligibilityBlocker
import com.valuepilot.core.ProductionUnitValueEligibilityBlocker
import org.junit.Assert.assertEquals
import org.junit.Test

class ProductionSearchUiProjectionBoundTest {

    @Test
    fun `projector accepts exactly the bounded candidate ceiling`() {
        val snapshot = blockedSnapshot(candidateCount = 128)

        val projection = ProductionSearchUiProjector.project(snapshot)

        assertEquals(128, projection.state.blocked.size)
        assertEquals(128, projection.blockedByCandidateId.size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `projector rejects a manually constructed snapshot above the candidate ceiling`() {
        ProductionSearchUiProjector.project(blockedSnapshot(candidateCount = 129))
    }

    private fun blockedSnapshot(candidateCount: Int): ProductionBestValuePresentationSnapshot =
        ProductionBestValuePresentationSnapshot(
            evaluatedAtEpochMillis = 2_000L,
            groups = emptyList(),
            blockedItems =
                (1..candidateCount).map { index ->
                    ProductionBestValueBlockedPresentationItem(
                        candidateId = "blocked-$index",
                        unitValueBlockers =
                            setOf(ProductionUnitValueEligibilityBlocker.PRICE_STAGE_BLOCKED),
                        priceBlockers =
                            setOf(
                                ProductionCurrentPriceEligibilityBlocker.CANDIDATE_NOT_ACCEPTANCE_RANKABLE
                            ),
                        unitValuePolicyBlockReasons = emptySet()
                    )
                }
        )
}
