package com.valuepilot.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame

class StapleWatchTest {

    private val milk = ShoppingItemKey("milk")
    private val eggs = ShoppingItemKey("eggs")
    private val bread = ShoppingItemKey("bread")
    private val request = ShoppingRequest(listOf(milk, eggs, bread))

    private val policy =
        StapleWatchPolicy(
            minimumSwitchSavings = Money.parse("15.00", "CAD"),
            maxAdditionalTravelSeconds = 10 * 60L,
            maxAdditionalDistanceMetres = 5_000L
        )

    @Test
    fun worthwhileCompleteBasketSwitchClearsExplicitSavingsAndRouteThresholds() {
        val baseline = single("normal", "50.00", request.itemKeySet)
        val alternative = alternative("other", "31.00", request.itemKeySet, 300L, 2_000L)

        val decision =
            StapleWatchEconomicEvaluator.evaluate(
                request = request,
                baseline = baseline,
                alternatives = listOf(alternative),
                policy = policy
            )

        assertEquals(StapleWatchEconomicStatus.SWITCH_WORTHWHILE, decision.status)
        assertSame(alternative, decision.recommendedAlternative)
        assertEquals(Money.parse("19.00", "CAD"), decision.switchSavings)
        assertSame(alternative.storePlan.evidence, decision.recommendedAlternative?.storePlan?.evidence)
    }

    @Test
    fun oneItemRequestIsNotEvaluatedAsNormalStapleWatchAlert() {
        val oneItemRequest = ShoppingRequest(listOf(milk))
        val baseline = single("normal", "50.00", setOf(milk))
        val alternative = alternative("other", "1.00", setOf(milk), 0L, 0L)

        val decision =
            StapleWatchEconomicEvaluator.evaluate(
                request = oneItemRequest,
                baseline = baseline,
                alternatives = listOf(alternative),
                policy = policy
            )

        assertEquals(
            StapleWatchEconomicStatus.NOT_EVALUATED_NOT_ENOUGH_STAPLES,
            decision.status
        )
        assertNull(decision.recommendedAlternative)
        assertNull(decision.switchSavings)
    }

    @Test
    fun incompleteNormalStoreBaselineBlocksSavingsClaim() {
        val baseline = single("normal", "20.00", setOf(milk, eggs))
        val alternative = alternative("other", "1.00", request.itemKeySet, 0L, 0L)

        val decision =
            StapleWatchEconomicEvaluator.evaluate(request, baseline, listOf(alternative), policy)

        assertEquals(
            StapleWatchEconomicStatus.NOT_EVALUATED_BASELINE_INCOMPLETE,
            decision.status
        )
        assertNull(decision.recommendedAlternative)
    }

    @Test
    fun incompleteAlternativeIsIgnoredRatherThanComparedByKnownSubtotal() {
        val baseline = single("normal", "50.00", request.itemKeySet)
        val incomplete = alternative("other", "1.00", setOf(milk, eggs), 0L, 0L)

        val decision =
            StapleWatchEconomicEvaluator.evaluate(request, baseline, listOf(incomplete), policy)

        assertEquals(StapleWatchEconomicStatus.NOT_WORTH_SWITCHING, decision.status)
        assertNull(decision.switchSavings)
    }

    @Test
    fun savingsBelowExplicitThresholdDoNotTriggerSwitch() {
        val baseline = single("normal", "50.00", request.itemKeySet)
        val alternative = alternative("other", "36.00", request.itemKeySet, 0L, 0L)

        val decision =
            StapleWatchEconomicEvaluator.evaluate(request, baseline, listOf(alternative), policy)

        assertEquals(StapleWatchEconomicStatus.NOT_WORTH_SWITCHING, decision.status)
    }

    @Test
    fun routeCapsRejectOtherwiseLargeSavings() {
        val baseline = single("normal", "50.00", request.itemKeySet)
        val tooSlow = alternative("slow", "1.00", request.itemKeySet, 601L, 1_000L)
        val tooFar = alternative("far", "1.00", request.itemKeySet, 300L, 5_001L)

        val decision =
            StapleWatchEconomicEvaluator.evaluate(
                request,
                baseline,
                listOf(tooSlow, tooFar),
                policy
            )

        assertEquals(StapleWatchEconomicStatus.NOT_WORTH_SWITCHING, decision.status)
    }

    @Test
    fun highestExactSavingsWinsThenLowerAdditionalTravelBreaksTie() {
        val baseline = single("normal", "60.00", request.itemKeySet)
        val smallerSavings = alternative("small", "44.00", request.itemKeySet, 30L, 100L)
        val tiedFarther = alternative("farther", "40.00", request.itemKeySet, 300L, 2_000L)
        val tiedNearer = alternative("nearer", "40.00", request.itemKeySet, 120L, 1_500L)

        val decision =
            StapleWatchEconomicEvaluator.evaluate(
                request,
                baseline,
                listOf(smallerSavings, tiedFarther, tiedNearer),
                policy
            )

        assertEquals(ShoppingStoreKey("nearer"), decision.recommendedAlternative?.storePlan?.storeKey)
        assertEquals(Money.parse("20.00", "CAD"), decision.switchSavings)
    }

    @Test
    fun zeroSavingsNeverTriggersEvenWhenPolicyThresholdIsZero() {
        val zeroThreshold =
            policy.copy(minimumSwitchSavings = Money.parse("0.00", "CAD"))
        val baseline = single("normal", "50.00", request.itemKeySet)
        val equalCost = alternative("other", "50.00", request.itemKeySet, 0L, 0L)

        val decision =
            StapleWatchEconomicEvaluator.evaluate(
                request,
                baseline,
                listOf(equalCost),
                zeroThreshold
            )

        assertEquals(StapleWatchEconomicStatus.NOT_WORTH_SWITCHING, decision.status)
    }

    @Test
    fun mismatchedCurrencyFailsClosed() {
        val baseline = single("normal", "50.00", request.itemKeySet)
        val usdAlternative =
            StapleWatchAlternativeCandidate(
                storePlan =
                    single(
                        store = "other",
                        cost = "20.00",
                        covered = request.itemKeySet,
                        currency = "USD"
                    ),
                additionalTravel = ShoppingTravel(0L, 0L)
            )

        assertFailsWith<IllegalArgumentException> {
            StapleWatchEconomicEvaluator.evaluate(
                request,
                baseline,
                listOf(usdAlternative),
                policy
            )
        }
    }

    @Test
    fun candidateOutsideWatchedBasketFailsClosed() {
        val baseline = single("normal", "50.00", request.itemKeySet)
        val invalid =
            alternative(
                "other",
                "20.00",
                request.itemKeySet + ShoppingItemKey("outside"),
                0L,
                0L
            )

        assertFailsWith<IllegalArgumentException> {
            StapleWatchEconomicEvaluator.evaluate(request, baseline, listOf(invalid), policy)
        }
    }

    @Test
    fun duplicateOrBaselineStoreAlternativesFailClosed() {
        val baseline = single("normal", "50.00", request.itemKeySet)
        val duplicateOne = alternative("other", "20.00", request.itemKeySet, 0L, 0L)
        val duplicateTwo = alternative("other", "19.00", request.itemKeySet, 0L, 0L)
        val baselineAgain = alternative("normal", "10.00", request.itemKeySet, 0L, 0L)

        assertFailsWith<IllegalArgumentException> {
            StapleWatchEconomicEvaluator.evaluate(
                request,
                baseline,
                listOf(duplicateOne, duplicateTwo),
                policy
            )
        }
        assertFailsWith<IllegalArgumentException> {
            StapleWatchEconomicEvaluator.evaluate(
                request,
                baseline,
                listOf(baselineAgain),
                policy
            )
        }
    }

    @Test
    fun alternativesAreBounded() {
        val baseline = single("normal", "50.00", request.itemKeySet)
        val alternatives =
            (1..65).map { index ->
                alternative("store-$index", "20.00", request.itemKeySet, 0L, 0L)
            }

        assertFailsWith<IllegalArgumentException> {
            StapleWatchEconomicEvaluator.evaluate(request, baseline, alternatives, policy)
        }
    }

    private fun single(
        store: String,
        cost: String,
        covered: Set<ShoppingItemKey>,
        currency: String = "CAD"
    ): SingleStorePlanCandidate =
        SingleStorePlanCandidate(
            storeKey = ShoppingStoreKey(store),
            coveredItemKeys = covered,
            knownBasketCost = Money.parse(cost, currency),
            travel = ShoppingTravel(distanceMetres = 1_000L, travelTimeSeconds = 300L),
            evidence =
                ShoppingPlanEvidenceSummary(
                    freshItemCount = covered.size,
                    staleItemCount = 0,
                    unknownFreshnessItemCount = 0
                )
        )

    private fun alternative(
        store: String,
        cost: String,
        covered: Set<ShoppingItemKey>,
        additionalTravelSeconds: Long,
        additionalDistanceMetres: Long
    ): StapleWatchAlternativeCandidate =
        StapleWatchAlternativeCandidate(
            storePlan = single(store, cost, covered),
            additionalTravel =
                ShoppingTravel(
                    distanceMetres = additionalDistanceMetres,
                    travelTimeSeconds = additionalTravelSeconds
                )
        )
}
