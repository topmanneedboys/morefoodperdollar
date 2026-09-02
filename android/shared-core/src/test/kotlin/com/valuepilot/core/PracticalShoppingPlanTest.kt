package com.valuepilot.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PracticalShoppingPlanTest {

    private val eggs = ShoppingItemKey("eggs")
    private val milk = ShoppingItemKey("milk")
    private val chicken = ShoppingItemKey("chicken")
    private val bread = ShoppingItemKey("bread")

    private val request = ShoppingRequest(listOf(eggs, milk, chicken))

    private val policy = PracticalShoppingPolicy(
        minimumSecondStopSavings = Money.parse("15.00", "CAD"),
        maxAdditionalTravelSeconds = 10 * 60L,
        maxAdditionalDistanceMetres = 5_000L
    )

    @Test
    fun completeBasketAlwaysBeatsCheaperIncompleteKnownSubtotal() {
        val incomplete = single(
            store = "cheap-incomplete",
            cost = "1.00",
            covered = setOf(eggs, milk),
            travelSeconds = 60L
        )
        val complete = single(
            store = "complete",
            cost = "60.00",
            covered = request.itemKeySet,
            travelSeconds = 600L
        )

        val decision = PracticalShoppingPlanner.evaluate(
            request = request,
            singleStoreCandidates = listOf(incomplete, complete),
            twoStoreCandidates = emptyList(),
            policy = policy
        )

        assertEquals(ShoppingStoreKey("complete"), decision.primary?.storeKey)
        assertEquals(
            PrimaryShoppingPlanKind.COMPLETE_PRICE_COMPARISON,
            decision.primaryKind
        )
    }

    @Test
    fun completeBasketsRankByExactBasketCostThenTravel() {
        val expensive = single(
            store = "expensive",
            cost = "60.00",
            covered = request.itemKeySet,
            travelSeconds = 60L
        )
        val cheaperFarther = single(
            store = "cheaper",
            cost = "55.00",
            covered = request.itemKeySet,
            travelSeconds = 600L
        )

        val decision = PracticalShoppingPlanner.evaluate(
            request,
            listOf(expensive, cheaperFarther),
            emptyList(),
            policy
        )

        assertEquals(ShoppingStoreKey("cheaper"), decision.primary?.storeKey)
    }

    @Test
    fun equalCompleteBasketCostPrefersShorterTripDeterministically() {
        val near = single(
            store = "near",
            cost = "55.00",
            covered = request.itemKeySet,
            travelSeconds = 300L,
            distanceMetres = 2_000L
        )
        val far = single(
            store = "far",
            cost = "55.00",
            covered = request.itemKeySet,
            travelSeconds = 900L,
            distanceMetres = 6_000L
        )

        val decision = PracticalShoppingPlanner.evaluate(
            request,
            listOf(far, near),
            emptyList(),
            policy
        )

        assertEquals(ShoppingStoreKey("near"), decision.primary?.storeKey)
    }

    @Test
    fun incompleteBasketsUseCoverageThenTravelNotMisleadingSubtotal() {
        val nearerButExpensiveKnownItems = single(
            store = "near",
            cost = "90.00",
            covered = setOf(eggs, milk),
            travelSeconds = 120L
        )
        val fartherButTinyKnownSubtotal = single(
            store = "far",
            cost = "1.00",
            covered = setOf(eggs, chicken),
            travelSeconds = 1_200L
        )

        val decision = PracticalShoppingPlanner.evaluate(
            request,
            listOf(fartherButTinyKnownSubtotal, nearerButExpensiveKnownItems),
            emptyList(),
            policy
        )

        assertEquals(ShoppingStoreKey("near"), decision.primary?.storeKey)
        assertEquals(
            PrimaryShoppingPlanKind.INCOMPLETE_BEST_COVERAGE,
            decision.primaryKind
        )
        assertEquals(
            SecondStopDecision.NOT_EVALUATED_PRIMARY_INCOMPLETE,
            decision.secondStopDecision
        )
        assertNull(decision.incrementalSecondStopSavings)
    }

    @Test
    fun noCoveredItemsProducesNoRecommendation() {
        val emptyCoverage = single(
            store = "empty",
            cost = "0.00",
            covered = emptySet(),
            travelSeconds = 0L
        )

        val decision = PracticalShoppingPlanner.evaluate(
            request,
            listOf(emptyCoverage),
            emptyList(),
            policy
        )

        assertNull(decision.primary)
        assertEquals(PrimaryShoppingPlanKind.NO_COVERAGE, decision.primaryKind)
        assertEquals(
            SecondStopDecision.NOT_EVALUATED_NO_PRIMARY,
            decision.secondStopDecision
        )
    }

    @Test
    fun secondStopBelowExplicitSavingsThresholdIsNotRecommended() {
        val primary = single(
            store = "primary",
            cost = "60.00",
            covered = request.itemKeySet,
            travelSeconds = 300L
        )
        val pair = twoStore(
            base = "primary",
            added = "second",
            cost = "46.00",
            covered = request.itemKeySet,
            extraSeconds = 180L
        )

        val decision = PracticalShoppingPlanner.evaluate(
            request,
            listOf(primary),
            listOf(pair),
            policy
        )

        assertEquals(SecondStopDecision.NOT_WORTH_IT, decision.secondStopDecision)
        assertNull(decision.secondStop)
        assertNull(decision.incrementalSecondStopSavings)
    }

    @Test
    fun secondStopAtExplicitThresholdIsRecommended() {
        val primary = single(
            store = "primary",
            cost = "60.00",
            covered = request.itemKeySet,
            travelSeconds = 300L
        )
        val pair = twoStore(
            base = "primary",
            added = "second",
            cost = "45.00",
            covered = request.itemKeySet,
            extraSeconds = 180L,
            extraDistanceMetres = 1_000L
        )

        val decision = PracticalShoppingPlanner.evaluate(
            request,
            listOf(primary),
            listOf(pair),
            policy
        )

        assertEquals(SecondStopDecision.RECOMMENDED, decision.secondStopDecision)
        assertEquals(ShoppingStoreKey("second"), decision.secondStop?.addedStoreKey)
        assertEquals(
            Money.parse("15.00", "CAD"),
            decision.incrementalSecondStopSavings
        )
    }

    @Test
    fun secondStopOutsideTravelCapIsRejectedEvenWithLargeSavings() {
        val primary = single(
            store = "primary",
            cost = "80.00",
            covered = request.itemKeySet,
            travelSeconds = 300L
        )
        val tooSlow = twoStore(
            base = "primary",
            added = "slow-second",
            cost = "40.00",
            covered = request.itemKeySet,
            extraSeconds = 11 * 60L,
            extraDistanceMetres = 1_000L
        )
        val tooFar = twoStore(
            base = "primary",
            added = "far-second",
            cost = "40.00",
            covered = request.itemKeySet,
            extraSeconds = 300L,
            extraDistanceMetres = 5_001L
        )

        val decision = PracticalShoppingPlanner.evaluate(
            request,
            listOf(primary),
            listOf(tooSlow, tooFar),
            policy
        )

        assertEquals(SecondStopDecision.NOT_WORTH_IT, decision.secondStopDecision)
    }

    @Test
    fun secondStopMustStartFromChosenPrimaryAndCoverSameCompleteBasket() {
        val primary = single(
            store = "primary",
            cost = "80.00",
            covered = request.itemKeySet,
            travelSeconds = 300L
        )
        val wrongBase = twoStore(
            base = "different-base",
            added = "second",
            cost = "20.00",
            covered = request.itemKeySet,
            extraSeconds = 120L
        )
        val incompletePair = twoStore(
            base = "primary",
            added = "other",
            cost = "20.00",
            covered = setOf(eggs, milk),
            extraSeconds = 120L
        )

        val decision = PracticalShoppingPlanner.evaluate(
            request,
            listOf(primary),
            listOf(wrongBase, incompletePair),
            policy
        )

        assertEquals(SecondStopDecision.NOT_WORTH_IT, decision.secondStopDecision)
        assertNull(decision.secondStop)
    }

    @Test
    fun highestSavingsEligibleSecondStopWinsWithoutHiddenTimeValuation() {
        val primary = single(
            store = "primary",
            cost = "100.00",
            covered = request.itemKeySet,
            travelSeconds = 300L
        )
        val savesTwenty = twoStore(
            base = "primary",
            added = "twenty",
            cost = "80.00",
            covered = request.itemKeySet,
            extraSeconds = 120L
        )
        val savesThirty = twoStore(
            base = "primary",
            added = "thirty",
            cost = "70.00",
            covered = request.itemKeySet,
            extraSeconds = 500L
        )

        val decision = PracticalShoppingPlanner.evaluate(
            request,
            listOf(primary),
            listOf(savesTwenty, savesThirty),
            policy
        )

        assertEquals(ShoppingStoreKey("thirty"), decision.secondStop?.addedStoreKey)
        assertEquals(
            Money.parse("30.00", "CAD"),
            decision.incrementalSecondStopSavings
        )
    }

    @Test
    fun candidateOutsideShoppingRequestFailsClosed() {
        val invalid = single(
            store = "invalid",
            cost = "10.00",
            covered = setOf(eggs, bread),
            travelSeconds = 60L
        )

        assertFailsWith<IllegalArgumentException> {
            PracticalShoppingPlanner.evaluate(
                request,
                listOf(invalid),
                emptyList(),
                policy
            )
        }
    }

    @Test
    fun currencyOrPrecisionMismatchFailsClosed() {
        val usd = SingleStorePlanCandidate(
            storeKey = ShoppingStoreKey("usd"),
            coveredItemKeys = request.itemKeySet,
            knownBasketCost = Money.parse("50.00", "USD"),
            travel = ShoppingTravel(1_000L, 300L),
            evidence = evidenceFor(request.itemKeySet.size)
        )

        assertFailsWith<IllegalArgumentException> {
            PracticalShoppingPlanner.evaluate(
                request,
                listOf(usd),
                emptyList(),
                policy
            )
        }
    }

    @Test
    fun requestAndCandidateBoundsFailClosed() {
        assertFailsWith<IllegalArgumentException> {
            ShoppingRequest(
                (0 until 129).map { ShoppingItemKey("item-$it") }
            )
        }

        val candidates = (0 until 65).map { index ->
            single(
                store = "store-$index",
                cost = "50.00",
                covered = request.itemKeySet,
                travelSeconds = index.toLong()
            )
        }

        assertFailsWith<IllegalArgumentException> {
            PracticalShoppingPlanner.evaluate(
                request,
                candidates,
                emptyList(),
                policy
            )
        }
    }

    @Test
    fun evidenceSummaryMustExactlyMatchCoveredItemCount() {
        assertFailsWith<IllegalArgumentException> {
            SingleStorePlanCandidate(
                storeKey = ShoppingStoreKey("bad-evidence"),
                coveredItemKeys = setOf(eggs, milk),
                knownBasketCost = Money.parse("10.00", "CAD"),
                travel = ShoppingTravel(100L, 60L),
                evidence = ShoppingPlanEvidenceSummary(
                    freshItemCount = 1,
                    staleItemCount = 0,
                    unknownFreshnessItemCount = 0
                )
            )
        }
    }

    @Test
    fun duplicateShoppingItemsAreRejected() {
        assertFailsWith<IllegalArgumentException> {
            ShoppingRequest(listOf(eggs, milk, eggs))
        }
    }

    private fun single(
        store: String,
        cost: String,
        covered: Set<ShoppingItemKey>,
        travelSeconds: Long,
        distanceMetres: Long = 1_000L
    ): SingleStorePlanCandidate =
        SingleStorePlanCandidate(
            storeKey = ShoppingStoreKey(store),
            coveredItemKeys = covered,
            knownBasketCost = Money.parse(cost, "CAD"),
            travel = ShoppingTravel(distanceMetres, travelSeconds),
            evidence = evidenceFor(covered.size)
        )

    private fun twoStore(
        base: String,
        added: String,
        cost: String,
        covered: Set<ShoppingItemKey>,
        extraSeconds: Long,
        extraDistanceMetres: Long = 1_000L
    ): TwoStorePlanCandidate =
        TwoStorePlanCandidate(
            baseStoreKey = ShoppingStoreKey(base),
            addedStoreKey = ShoppingStoreKey(added),
            coveredItemKeys = covered,
            addedStoreItemKeys = covered.take(1).toSet(),
            knownCombinedBasketCost = Money.parse(cost, "CAD"),
            additionalTravel = ShoppingTravel(extraDistanceMetres, extraSeconds),
            evidence = evidenceFor(covered.size)
        )

    private fun evidenceFor(itemCount: Int): ShoppingPlanEvidenceSummary =
        ShoppingPlanEvidenceSummary(
            freshItemCount = itemCount,
            staleItemCount = 0,
            unknownFreshnessItemCount = 0
        )
}
