package com.valuepilot.app

import com.valuepilot.core.Money
import com.valuepilot.core.ShoppingItemKey
import com.valuepilot.core.ShoppingPlanEvidenceSummary
import com.valuepilot.core.ShoppingRequest
import com.valuepilot.core.ShoppingStoreKey
import com.valuepilot.core.ShoppingTravel
import com.valuepilot.core.StapleWatchBasketAlternativeCandidate
import com.valuepilot.core.StapleWatchBasketCandidate
import com.valuepilot.core.StapleWatchBasketEconomicDecision
import com.valuepilot.core.StapleWatchEconomicEvaluator
import com.valuepilot.core.StapleWatchEconomicStatus
import com.valuepilot.core.StapleWatchPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class StapleWatchBasketUiProjectorTest {

    private val milk = ShoppingItemKey("staple-milk")
    private val eggs = ShoppingItemKey("staple-eggs")
    private val bread = ShoppingItemKey("staple-bread")
    private val request = ShoppingRequest(listOf(milk, eggs, bread))
    private val requested = request.itemKeys.toSet()

    private val usualKey = ShoppingStoreKey("opaque-usual-store-111111")
    private val alternativeKey = ShoppingStoreKey("opaque-alt-store-222222")

    private val policy =
        StapleWatchPolicy(
            minimumSwitchSavings = Money.parse("15.00", "CAD"),
            maxAdditionalTravelSeconds = 600L,
            maxAdditionalDistanceMetres = 5_000L
        )

    private val safeMetadata =
        StapleWatchStoreDisplayMetadata(
            listOf(
                StapleWatchStoreDisplayMetadataEntry(usualKey, "Sample Market"),
                StapleWatchStoreDisplayMetadataEntry(alternativeKey, "Example Grocer")
            )
        )

    @Test
    fun nativeWorthwhileDecisionProjectsConsumerStateWithoutLegacyAbsoluteTravel() {
        val decision = worthwhileDecision()

        val projection = StapleWatchUiProjector.project(decision, safeMetadata)
        val state = projection.state
        val candidate = requireNotNull(state.switchCandidate)

        assertSame(decision, projection.exactDecision)
        assertEquals(alternativeKey, projection.recommendedStoreKey)
        assertEquals(StapleWatchUiStatus.WORTH_CHECKING, state.status)
        assertEquals("Watch my staples", state.headline)
        assertEquals("Store switch worth checking", state.statusTitle)
        assertEquals("ECONOMIC SWITCH CANDIDATE", candidate.badge)
        assertEquals("Example Grocer", candidate.storeName)
        assertEquals("Could save 19.00 CAD", candidate.savingsText)
        assertEquals("Adds 5 min · 2 km", candidate.additionalTravelText)
        assertEquals(
            "Usual store evidence: 3 fresh · 0 stale · 0 unknown",
            state.baselineEvidenceText
        )
        assertEquals(
            "Alternative evidence: 3 fresh · 0 stale · 0 unknown",
            candidate.alternativeEvidenceText
        )
        assertTrue(state.guidance.contains("notification timing"))
        assertFalse(state.guidance.contains("price freshness"))
        assertEquals(
            "Economic eligibility alone does not authorize a notification.",
            state.notice
        )
        assertFalse(state.toString().contains(usualKey.value))
        assertFalse(state.toString().contains(alternativeKey.value))
    }

    @Test
    fun nativeNotWorthSwitchingProjectsNoRecommendation() {
        val decision =
            StapleWatchEconomicEvaluator.evaluate(
                request = request,
                baseline = basket(usualKey, "50.00"),
                alternatives =
                    listOf(
                        alternative(
                            key = alternativeKey,
                            cost = "40.00",
                            additionalSeconds = 120L,
                            additionalMetres = 1_000L
                        )
                    ),
                policy = policy
            )
        assertEquals(StapleWatchEconomicStatus.NOT_WORTH_SWITCHING, decision.status)

        val projection =
            StapleWatchUiProjector.project(
                decision,
                StapleWatchStoreDisplayMetadata()
            )

        assertSame(decision, projection.exactDecision)
        assertEquals(StapleWatchUiStatus.NOT_WORTH_SWITCHING, projection.state.status)
        assertNull(projection.state.switchCandidate)
        assertNull(projection.recommendedStoreKey)
        assertNull(projection.state.notice)
    }

    @Test
    fun missingNativeWorthwhileStoreLabelFailsClosedWithoutSavingsOrOpaqueKey() {
        val decision = worthwhileDecision()

        val projection =
            StapleWatchUiProjector.project(
                decision,
                StapleWatchStoreDisplayMetadata()
            )

        assertSame(decision, projection.exactDecision)
        assertEquals(StapleWatchUiStatus.DISPLAY_METADATA_INCOMPLETE, projection.state.status)
        assertNull(projection.state.switchCandidate)
        assertNull(projection.recommendedStoreKey)
        assertFalse(projection.state.toString().contains("19.00"))
        assertFalse(projection.state.toString().contains(alternativeKey.value))
        assertEquals(
            "Opaque store identifiers are never used as consumer labels.",
            projection.state.notice
        )
    }

    @Test
    fun nativeOverloadConsumesBasketDecisionDirectlyWithoutLegacyReconstruction() {
        val nativeOverload =
            StapleWatchUiProjector::class.java.methods.single { method ->
                method.name == "project" &&
                    method.parameterTypes.contentEquals(
                        arrayOf(
                            StapleWatchBasketEconomicDecision::class.java,
                            StapleWatchStoreDisplayMetadata::class.java
                        )
                    )
            }
        assertEquals(StapleWatchBasketUiProjection::class.java, nativeOverload.returnType)

        val source = source("StapleWatchUiPresentation.kt").readText()
        val nativeBlock =
            source
                .substringAfter("decision: StapleWatchBasketEconomicDecision")
                .substringBefore("private fun projectConsumerState")

        assertTrue(nativeBlock.contains("recommendation?.basket?.storeKey"))
        assertTrue(nativeBlock.contains("recommendation?.basket?.evidence"))
        assertTrue(nativeBlock.contains("recommendation?.additionalTravel"))
        assertTrue(nativeBlock.contains("exactDecision = decision"))

        listOf(
            "SingleStorePlanCandidate",
            "StapleWatchAlternativeCandidate",
            ".storePlan",
            "ShoppingTravel(",
            "StapleWatchEconomicEvaluator",
            "Math.addExact",
            "Math.subtractExact",
            "System.currentTimeMillis",
            "WorkManager",
            "NotificationManager",
            "android."
        ).forEach { forbidden ->
            assertFalse("Native Watch projector must not reconstruct or own $forbidden", nativeBlock.contains(forbidden))
        }
    }

    private fun worthwhileDecision(): StapleWatchBasketEconomicDecision =
        StapleWatchEconomicEvaluator.evaluate(
            request = request,
            baseline = basket(usualKey, "50.00"),
            alternatives =
                listOf(
                    alternative(
                        key = alternativeKey,
                        cost = "31.00",
                        additionalSeconds = 300L,
                        additionalMetres = 2_000L
                    )
                ),
            policy = policy
        )

    private fun basket(
        key: ShoppingStoreKey,
        cost: String
    ): StapleWatchBasketCandidate =
        StapleWatchBasketCandidate(
            storeKey = key,
            coveredItemKeys = requested,
            knownBasketCost = Money.parse(cost, "CAD"),
            evidence =
                ShoppingPlanEvidenceSummary(
                    freshItemCount = requested.size,
                    staleItemCount = 0,
                    unknownFreshnessItemCount = 0
                )
        )

    private fun alternative(
        key: ShoppingStoreKey,
        cost: String,
        additionalSeconds: Long,
        additionalMetres: Long
    ): StapleWatchBasketAlternativeCandidate =
        StapleWatchBasketAlternativeCandidate(
            basket = basket(key, cost),
            additionalTravel =
                ShoppingTravel(
                    distanceMetres = additionalMetres,
                    travelTimeSeconds = additionalSeconds
                )
        )

    private fun source(fileName: String): File =
        File(
            System.getProperty("user.dir"),
            "src/main/java/com/valuepilot/app/$fileName"
        )
}
