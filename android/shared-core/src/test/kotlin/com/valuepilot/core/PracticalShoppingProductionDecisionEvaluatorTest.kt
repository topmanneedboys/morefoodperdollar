package com.valuepilot.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticalShoppingProductionDecisionEvaluatorTest {

    private val eggs = ShoppingItemKey("eggs")
    private val milk = ShoppingItemKey("milk")
    private val request = ShoppingRequest(listOf(eggs, milk))

    private val cadPolicy =
        PracticalShoppingPolicy(
            minimumSecondStopSavings = Money(1_500L, "CAD"),
            maxAdditionalTravelSeconds = 600L,
            maxAdditionalDistanceMetres = 5_000L
        )

    @Test
    fun `different money specs are excluded instead of converted or crashing planner`() {
        val cad = single("cad", 2_000L, "CAD")
        val usd = single("usd", 100L, "USD")

        val result =
            PracticalShoppingProductionDecisionEvaluator.decideFromBridge(
                request = request,
                bridgeResult = bridge(singles = listOf(usd, cad)),
                planningPolicy = cadPolicy
            )

        assertEquals(ShoppingStoreKey("cad"), result.decision.primary?.storeKey)
        assertEquals(listOf(cad), result.comparableCandidates.singleStoreCandidates)
        assertEquals(listOf(usd), result.comparableCandidates.excludedSingleStoreCandidates)
        assertTrue(result.comparableCandidates.twoStoreCandidates.isEmpty())
    }

    @Test
    fun `no comparable money spec produces no coverage rather than currency guessing`() {
        val usd = single("usd", 100L, "USD")

        val result =
            PracticalShoppingProductionDecisionEvaluator.decideFromBridge(
                request = request,
                bridgeResult = bridge(singles = listOf(usd)),
                planningPolicy = cadPolicy
            )

        assertNull(result.decision.primary)
        assertEquals(PrimaryShoppingPlanKind.NO_COVERAGE, result.decision.primaryKind)
        assertEquals(listOf(usd), result.comparableCandidates.excludedSingleStoreCandidates)
    }

    @Test
    fun `eligible fixed pair is passed to existing planner for savings and travel decision`() {
        val base = single("base", 4_000L, "CAD", travelSeconds = 180L)
        val added = single("added", 5_000L, "CAD", travelSeconds = 300L)
        val pairEvaluation =
            pairEvaluation(
                baseStoreKey = base.storeKey,
                addedStoreKey = added.storeKey,
                combinedMinorUnits = 2_000L,
                additionalTravelSeconds = 240L
            )

        val result =
            PracticalShoppingProductionDecisionEvaluator.decideFromBridge(
                request = request,
                bridgeResult = bridge(listOf(base, added), listOf(pairEvaluation)),
                planningPolicy = cadPolicy
            )

        assertEquals(base.storeKey, result.decision.primary?.storeKey)
        assertEquals(SecondStopDecision.RECOMMENDED, result.decision.secondStopDecision)
        assertEquals(added.storeKey, result.decision.secondStop?.addedStoreKey)
        assertEquals(Money(2_000L, "CAD"), result.decision.incrementalSecondStopSavings)
    }

    @Test
    fun `production coordinator does not weaken planner second-stop threshold`() {
        val base = single("base", 4_000L, "CAD", travelSeconds = 180L)
        val added = single("added", 5_000L, "CAD", travelSeconds = 300L)
        val pairEvaluation =
            pairEvaluation(
                baseStoreKey = base.storeKey,
                addedStoreKey = added.storeKey,
                combinedMinorUnits = 2_000L,
                additionalTravelSeconds = 240L
            )
        val stricterPolicy =
            cadPolicy.copy(minimumSecondStopSavings = Money(2_500L, "CAD"))

        val result =
            PracticalShoppingProductionDecisionEvaluator.decideFromBridge(
                request = request,
                bridgeResult = bridge(listOf(base, added), listOf(pairEvaluation)),
                planningPolicy = stricterPolicy
            )

        assertEquals(SecondStopDecision.NOT_WORTH_IT, result.decision.secondStopDecision)
        assertNull(result.decision.secondStop)
        assertNull(result.decision.incrementalSecondStopSavings)
    }

    private fun bridge(
        singles: List<SingleStorePlanCandidate>,
        pairs: List<PracticalShoppingProductionPairEvaluation> = emptyList()
    ): PracticalShoppingProductionPlanCandidateBridgeResult {
        val oneStoreResult =
            PracticalShoppingProductionCandidateBridgeResult(
                priceEvaluations = emptyList(),
                storeEvaluations =
                    singles.map { candidate ->
                        PracticalShoppingProductionStoreEvaluation(
                            store = scope(candidate.storeKey),
                            candidate = candidate,
                            blockers = emptySet()
                        )
                    }
            )

        return PracticalShoppingProductionPlanCandidateBridgeResult(
            oneStoreResult = oneStoreResult,
            pairEvaluations = pairs
        )
    }

    private fun single(
        store: String,
        minorUnits: Long,
        currencyCode: String,
        travelSeconds: Long = 120L
    ): SingleStorePlanCandidate =
        SingleStorePlanCandidate(
            storeKey = ShoppingStoreKey(store),
            coveredItemKeys = request.itemKeys.toSet(),
            knownBasketCost = Money(minorUnits, currencyCode),
            travel = ShoppingTravel(1_000L, travelSeconds),
            evidence = freshEvidence(request.itemKeys.size)
        )

    private fun pairEvaluation(
        baseStoreKey: ShoppingStoreKey,
        addedStoreKey: ShoppingStoreKey,
        combinedMinorUnits: Long,
        additionalTravelSeconds: Long
    ): PracticalShoppingProductionPairEvaluation {
        val pair =
            PracticalShoppingProductionStorePairScope(
                baseStoreKey = baseStoreKey,
                addedStoreKey = addedStoreKey,
                additionalTravel = ShoppingTravel(1_000L, additionalTravelSeconds)
            )
        val selections =
            listOf(
                PracticalShoppingProductionPairItemSelection(
                    itemKey = eggs,
                    selectedStoreKey = addedStoreKey,
                    currentPriceRequestId = "added-eggs",
                    selectedPrice = Money(500L, "CAD"),
                    freshness = EvidenceFreshness.FRESH
                ),
                PracticalShoppingProductionPairItemSelection(
                    itemKey = milk,
                    selectedStoreKey = baseStoreKey,
                    currentPriceRequestId = "base-milk",
                    selectedPrice = Money(combinedMinorUnits - 500L, "CAD"),
                    freshness = EvidenceFreshness.FRESH
                )
            )

        return PracticalShoppingProductionPairEvaluation(
            pair = pair,
            itemSelections = selections,
            candidate =
                TwoStorePlanCandidate(
                    baseStoreKey = baseStoreKey,
                    addedStoreKey = addedStoreKey,
                    coveredItemKeys = request.itemKeys.toSet(),
                    addedStoreItemKeys = setOf(eggs),
                    knownCombinedBasketCost = Money(combinedMinorUnits, "CAD"),
                    additionalTravel = pair.additionalTravel,
                    evidence = freshEvidence(request.itemKeys.size)
                ),
            blockers = emptySet()
        )
    }

    private fun scope(storeKey: ShoppingStoreKey): PracticalShoppingProductionStoreScope =
        PracticalShoppingProductionStoreScope(
            storeKey = storeKey,
            merchantKey = "merchant-${storeKey.value}",
            locationKey = "location-${storeKey.value}",
            commerceChannelKey = "PHYSICAL_STORE",
            travelFromUser = ShoppingTravel(1_000L, 120L)
        )

    private fun freshEvidence(count: Int): ShoppingPlanEvidenceSummary =
        ShoppingPlanEvidenceSummary(
            freshItemCount = count,
            agingItemCount = 0,
            staleItemCount = 0,
            unknownFreshnessItemCount = 0
        )
}
