package com.valuepilot.app

import com.valuepilot.core.Money
import com.valuepilot.core.ShoppingItemKey
import com.valuepilot.core.ShoppingPlanEvidenceSummary
import com.valuepilot.core.ShoppingRequest
import com.valuepilot.core.ShoppingStoreKey
import com.valuepilot.core.ShoppingTravel
import com.valuepilot.core.SingleStorePlanCandidate
import com.valuepilot.core.StapleWatchAlternativeCandidate
import com.valuepilot.core.StapleWatchEconomicEvaluator
import com.valuepilot.core.StapleWatchPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Test

class StapleWatchSurfacePresenterTest {

    @Test
    fun presenterHandsOnlyProjectedConsumerStateToPhysicalRenderer() {
        val request =
            ShoppingRequest(
                listOf(
                    ShoppingItemKey("staple-milk"),
                    ShoppingItemKey("staple-eggs"),
                    ShoppingItemKey("staple-bread")
                )
            )
        val requested = request.itemKeys.toSet()
        val usualStoreKey = ShoppingStoreKey("opaque-usual-store-111111")
        val alternativeStoreKey = ShoppingStoreKey("opaque-alt-store-222222")
        val baseline =
            SingleStorePlanCandidate(
                storeKey = usualStoreKey,
                coveredItemKeys = requested,
                knownBasketCost = Money.parse("50.00", "CAD"),
                travel = ShoppingTravel(distanceMetres = 1_000L, travelTimeSeconds = 300L),
                evidence =
                    ShoppingPlanEvidenceSummary(
                        freshItemCount = 3,
                        staleItemCount = 0,
                        unknownFreshnessItemCount = 0
                    )
            )
        val alternative =
            StapleWatchAlternativeCandidate(
                storePlan =
                    SingleStorePlanCandidate(
                        storeKey = alternativeStoreKey,
                        coveredItemKeys = requested,
                        knownBasketCost = Money.parse("31.00", "CAD"),
                        travel = ShoppingTravel(distanceMetres = 2_000L, travelTimeSeconds = 420L),
                        evidence =
                            ShoppingPlanEvidenceSummary(
                                freshItemCount = 3,
                                staleItemCount = 0,
                                unknownFreshnessItemCount = 0
                            )
                    ),
                additionalTravel =
                    ShoppingTravel(distanceMetres = 2_000L, travelTimeSeconds = 300L)
            )
        val decision =
            StapleWatchEconomicEvaluator.evaluate(
                request = request,
                baseline = baseline,
                alternatives = listOf(alternative),
                policy =
                    StapleWatchPolicy(
                        minimumSwitchSavings = Money.parse("15.00", "CAD"),
                        maxAdditionalTravelSeconds = 600L,
                        maxAdditionalDistanceMetres = 5_000L
                    )
            )
        val projection =
            StapleWatchUiProjector.project(
                decision,
                StapleWatchStoreDisplayMetadata(
                    listOf(
                        StapleWatchStoreDisplayMetadataEntry(
                            alternativeStoreKey,
                            "Example Grocer"
                        )
                    )
                )
            )

        var renderedState: StapleWatchUiState? = null
        var renderCalls = 0
        val presenter =
            StapleWatchSurfacePresenter(
                StapleWatchSurfaceRenderer { state ->
                    renderCalls += 1
                    renderedState = state
                }
            )

        presenter.render(projection)

        assertEquals(1, renderCalls)
        assertSame(projection.state, renderedState)
        assertEquals(StapleWatchUiStatus.WORTH_CHECKING, renderedState?.status)
        assertEquals("Example Grocer", renderedState?.switchCandidate?.storeName)
        assertFalse(renderedState.toString().contains(usualStoreKey.value))
        assertFalse(renderedState.toString().contains(alternativeStoreKey.value))
    }
}
