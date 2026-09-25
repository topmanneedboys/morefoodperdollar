package com.valuepilot.app

import com.valuepilot.core.Money
import com.valuepilot.core.ShoppingItemKey
import com.valuepilot.core.ShoppingPlanEvidenceSummary
import com.valuepilot.core.ShoppingRequest
import com.valuepilot.core.ShoppingStoreKey
import com.valuepilot.core.ShoppingTravel
import com.valuepilot.core.StapleWatchBasketAlternativeCandidate
import com.valuepilot.core.StapleWatchBasketCandidate
import com.valuepilot.core.StapleWatchEconomicEvaluator
import com.valuepilot.core.StapleWatchPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class StapleWatchBasketSurfacePresenterTest {

    @Test
    fun presenterHandsOnlyNativeProjectedConsumerStateToPhysicalRenderer() {
        val milk = ShoppingItemKey("staple-milk")
        val eggs = ShoppingItemKey("staple-eggs")
        val bread = ShoppingItemKey("staple-bread")
        val request = ShoppingRequest(listOf(milk, eggs, bread))
        val requested = request.itemKeys.toSet()
        val usualStoreKey = ShoppingStoreKey("opaque-usual-store-111111")
        val alternativeStoreKey = ShoppingStoreKey("opaque-alt-store-222222")
        val evidence =
            ShoppingPlanEvidenceSummary(
                freshItemCount = 3,
                staleItemCount = 0,
                unknownFreshnessItemCount = 0
            )
        val decision =
            StapleWatchEconomicEvaluator.evaluate(
                request = request,
                baseline =
                    StapleWatchBasketCandidate(
                        storeKey = usualStoreKey,
                        coveredItemKeys = requested,
                        knownBasketCost = Money.parse("50.00", "CAD"),
                        evidence = evidence
                    ),
                alternatives =
                    listOf(
                        StapleWatchBasketAlternativeCandidate(
                            basket =
                                StapleWatchBasketCandidate(
                                    storeKey = alternativeStoreKey,
                                    coveredItemKeys = requested,
                                    knownBasketCost = Money.parse("31.00", "CAD"),
                                    evidence = evidence
                                ),
                            additionalTravel =
                                ShoppingTravel(
                                    distanceMetres = 2_000L,
                                    travelTimeSeconds = 300L
                                )
                        )
                    ),
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

    @Test
    fun nativePresenterOverloadForwardsStateWithoutOpeningDecisionAuthority() {
        val nativeOverload =
            StapleWatchSurfacePresenter::class.java.methods.single { method ->
                method.name == "render" &&
                    method.parameterTypes.contentEquals(
                        arrayOf(StapleWatchBasketUiProjection::class.java)
                    )
            }
        assertEquals(Void.TYPE, nativeOverload.returnType)

        val source = source("StapleWatchSurfacePresenter.kt").readText()
        val nativeBlock =
            source
                .substringAfter("fun render(projection: StapleWatchBasketUiProjection)")
                .substringBefore("}")

        assertTrue(nativeBlock.contains("renderer.render(projection.state)"))
        listOf(
            "exactDecision",
            "recommendedStoreKey",
            "switchSavings",
            "recommendedAlternative",
            "StapleWatchEconomicEvaluator",
            "Money",
            "ShoppingTravel",
            "NotificationManager",
            "WorkManager",
            "android."
        ).forEach { forbidden ->
            assertFalse("Native Watch presenter must not open $forbidden", nativeBlock.contains(forbidden))
        }
    }

    private fun source(fileName: String): File =
        File(
            System.getProperty("user.dir"),
            "src/main/java/com/valuepilot/app/$fileName"
        )
}
