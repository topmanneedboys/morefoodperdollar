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
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class StapleWatchUiProjectorTest {

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
    fun worthwhileDecisionProjectsConsumerSafeEconomicCandidateOnly() {
        val decision =
            StapleWatchEconomicEvaluator.evaluate(
                request = request,
                baseline = single(usualKey, "50.00", requested, fresh = 3),
                alternatives =
                    listOf(
                        alternative(
                            alternativeKey,
                            "31.00",
                            requested,
                            additionalSeconds = 300L,
                            additionalMetres = 2_000L,
                            fresh = 2,
                            stale = 1
                        )
                    ),
                policy = policy
            )

        val projection = StapleWatchUiProjector.project(decision, safeMetadata)
        val state = projection.state
        val candidate = requireNotNull(state.switchCandidate)

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
            "Alternative evidence: 2 fresh · 1 stale · 0 unknown",
            candidate.alternativeEvidenceText
        )
        assertEquals("Worth checking before your next shop", candidate.actionText)
        assertEquals(
            "Economic eligibility alone does not authorize a notification.",
            state.notice
        )
        assertEquals(alternativeKey, projection.recommendedStoreKey)
        assertFalse(state.toString().contains(usualKey.value))
        assertFalse(state.toString().contains(alternativeKey.value))
        assertTrue(state.guidance.contains("price freshness"))
        assertFalse(candidate.actionText.contains("now", ignoreCase = true))
    }

    @Test
    fun notWorthSwitchingProjectsNoStoreRecommendation() {
        val decision =
            StapleWatchEconomicEvaluator.evaluate(
                request,
                single(usualKey, "50.00", requested),
                listOf(alternative(alternativeKey, "40.00", requested, 120L, 1_000L)),
                policy
            )

        val projection = StapleWatchUiProjector.project(decision, StapleWatchStoreDisplayMetadata())

        assertEquals(StapleWatchUiStatus.NOT_WORTH_SWITCHING, projection.state.status)
        assertEquals("No worthwhile store switch", projection.state.statusTitle)
        assertNull(projection.state.switchCandidate)
        assertNull(projection.recommendedStoreKey)
        assertNull(projection.state.notice)
    }

    @Test
    fun notEnoughStaplesProjectsEducationWithoutRecommendation() {
        val oneItemRequest = ShoppingRequest(listOf(milk))
        val decision =
            StapleWatchEconomicEvaluator.evaluate(
                oneItemRequest,
                single(usualKey, "5.00", setOf(milk)),
                listOf(alternative(alternativeKey, "1.00", setOf(milk), 0L, 0L)),
                policy
            )

        val state = StapleWatchUiProjector.project(
            decision,
            StapleWatchStoreDisplayMetadata()
        ).state

        assertEquals(StapleWatchUiStatus.NOT_ENOUGH_STAPLES, state.status)
        assertEquals("Add more recurring items", state.statusTitle)
        assertNull(state.switchCandidate)
    }

    @Test
    fun incompleteBaselineNeverPresentsPotentialSavings() {
        val decision =
            StapleWatchEconomicEvaluator.evaluate(
                request,
                single(usualKey, "20.00", setOf(milk, eggs)),
                listOf(alternative(alternativeKey, "1.00", requested, 0L, 0L)),
                policy
            )

        val state = StapleWatchUiProjector.project(
            decision,
            StapleWatchStoreDisplayMetadata()
        ).state

        assertEquals(StapleWatchUiStatus.BASELINE_INCOMPLETE, state.status)
        assertEquals("Need complete usual-store prices", state.statusTitle)
        assertNull(state.switchCandidate)
        assertFalse(state.toString().contains("save", ignoreCase = true))
    }

    @Test
    fun missingWorthwhileStoreLabelSuppressesSavingsAndAction() {
        val decision = worthwhileDecision()

        val projection = StapleWatchUiProjector.project(
            decision,
            StapleWatchStoreDisplayMetadata()
        )

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
    fun opaqueOrTechnicalWorthwhileStoreLabelsFailClosed() {
        val decision = worthwhileDecision()
        val labels =
            listOf(
                alternativeKey.value,
                "Deal at ${alternativeKey.value}",
                "provider:merchant-123",
                "internal-store-example",
                "123456789"
            )

        labels.forEach { unsafe ->
            val state =
                StapleWatchUiProjector.project(
                    decision,
                    StapleWatchStoreDisplayMetadata(
                        listOf(
                            StapleWatchStoreDisplayMetadataEntry(alternativeKey, unsafe)
                        )
                    )
                ).state

            assertEquals(StapleWatchUiStatus.DISPLAY_METADATA_INCOMPLETE, state.status)
            assertNull(state.switchCandidate)
        }
    }

    @Test
    fun displayMetadataRequiresUniqueBoundedStoreEntries() {
        assertThrows(IllegalArgumentException::class.java) {
            StapleWatchStoreDisplayMetadata(
                listOf(
                    StapleWatchStoreDisplayMetadataEntry(alternativeKey, "One"),
                    StapleWatchStoreDisplayMetadataEntry(alternativeKey, "Two")
                )
            )
        }

        assertThrows(IllegalArgumentException::class.java) {
            StapleWatchStoreDisplayMetadata(
                (1..65).map { index ->
                    StapleWatchStoreDisplayMetadataEntry(
                        ShoppingStoreKey("opaque-store-$index"),
                        "Store $index"
                    )
                }
            )
        }
    }

    @Test
    fun longOrControlCharacterStoreLabelsFailClosedWithoutLeakingKey() {
        val decision = worthwhileDecision()
        val labels = listOf("x".repeat(161), "Example\nGrocer")

        labels.forEach { unsafe ->
            val state =
                StapleWatchUiProjector.project(
                    decision,
                    StapleWatchStoreDisplayMetadata(
                        listOf(
                            StapleWatchStoreDisplayMetadataEntry(alternativeKey, unsafe)
                        )
                    )
                ).state

            assertEquals(StapleWatchUiStatus.DISPLAY_METADATA_INCOMPLETE, state.status)
            assertNull(state.switchCandidate)
            assertFalse(state.toString().contains(alternativeKey.value))
        }
    }

    private fun worthwhileDecision() =
        StapleWatchEconomicEvaluator.evaluate(
            request = request,
            baseline = single(usualKey, "50.00", requested, fresh = 3),
            alternatives =
                listOf(
                    alternative(
                        alternativeKey,
                        "31.00",
                        requested,
                        additionalSeconds = 300L,
                        additionalMetres = 2_000L,
                        fresh = 3
                    )
                ),
            policy = policy
        )

    private fun single(
        key: ShoppingStoreKey,
        cost: String,
        covered: Set<ShoppingItemKey>,
        fresh: Int = covered.size,
        stale: Int = 0
    ): SingleStorePlanCandidate =
        SingleStorePlanCandidate(
            storeKey = key,
            coveredItemKeys = covered,
            knownBasketCost = Money.parse(cost, "CAD"),
            travel = ShoppingTravel(distanceMetres = 1_000L, travelTimeSeconds = 300L),
            evidence =
                ShoppingPlanEvidenceSummary(
                    freshItemCount = fresh,
                    staleItemCount = stale,
                    unknownFreshnessItemCount = covered.size - fresh - stale
                )
        )

    private fun alternative(
        key: ShoppingStoreKey,
        cost: String,
        covered: Set<ShoppingItemKey>,
        additionalSeconds: Long,
        additionalMetres: Long,
        fresh: Int = covered.size,
        stale: Int = 0
    ): StapleWatchAlternativeCandidate =
        StapleWatchAlternativeCandidate(
            storePlan = single(key, cost, covered, fresh, stale),
            additionalTravel =
                ShoppingTravel(
                    distanceMetres = additionalMetres,
                    travelTimeSeconds = additionalSeconds
                )
        )
}
