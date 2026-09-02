package com.valuepilot.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticalShoppingProductionPlanCandidateBridgeTest {

    private val eggs = ShoppingItemKey("eggs")
    private val milk = ShoppingItemKey("milk")
    private val request = ShoppingRequest(listOf(eggs, milk))

    private val baseStore =
        PracticalShoppingProductionStoreScope(
            storeKey = ShoppingStoreKey("base"),
            merchantKey = "merchant-base",
            locationKey = "location-base",
            commerceChannelKey = "PHYSICAL_STORE",
            travelFromUser = ShoppingTravel(1_000L, 180L)
        )

    private val addedStore =
        PracticalShoppingProductionStoreScope(
            storeKey = ShoppingStoreKey("added"),
            merchantKey = "merchant-added",
            locationKey = "location-added",
            commerceChannelKey = "PHYSICAL_STORE",
            travelFromUser = ShoppingTravel(2_000L, 300L)
        )

    private val pair =
        PracticalShoppingProductionStorePairScope(
            baseStoreKey = baseStore.storeKey,
            addedStoreKey = addedStore.storeKey,
            additionalTravel = ShoppingTravel(1_200L, 240L)
        )

    @Test
    fun `fixed pair selects only strictly cheaper comparable added-store prices`() {
        val baseEggs = price(baseStore, eggs, 500L, EvidenceFreshness.FRESH)
        val baseMilk = price(baseStore, milk, 600L, EvidenceFreshness.AGING)
        val addedEggs = price(addedStore, eggs, 400L, EvidenceFreshness.FRESH)
        val addedMilk = price(addedStore, milk, 700L, EvidenceFreshness.FRESH)
        val result =
            result(
                prices = listOf(baseEggs, baseMilk, addedEggs, addedMilk),
                baseCovered = setOf(eggs, milk),
                addedCovered = setOf(eggs, milk)
            )

        val evaluation =
            PracticalShoppingProductionPlanCandidateBridge.buildPairEvaluations(
                request = request,
                stores = listOf(baseStore, addedStore),
                storePairs = listOf(pair),
                oneStoreResult = result
            ).single()

        val candidate = requireNotNull(evaluation.candidate)
        assertTrue(evaluation.blockers.isEmpty())
        assertEquals(Money(1_000L, "CAD"), candidate.knownCombinedBasketCost)
        assertEquals(request.itemKeys.toSet(), candidate.coveredItemKeys)
        assertEquals(setOf(eggs), candidate.addedStoreItemKeys)
        assertEquals(pair.additionalTravel, candidate.additionalTravel)
        assertEquals(
            listOf(addedStore.storeKey, baseStore.storeKey),
            evaluation.itemSelections.map { it.selectedStoreKey }
        )
        assertEquals(1, candidate.evidence.freshItemCount)
        assertEquals(1, candidate.evidence.agingItemCount)
        assertEquals(0, candidate.evidence.staleItemCount)
        assertEquals(0, candidate.evidence.unknownFreshnessItemCount)
    }

    @Test
    fun `equal or higher added-store prices do not create a fake second stop`() {
        val prices =
            listOf(
                price(baseStore, eggs, 500L, EvidenceFreshness.FRESH),
                price(baseStore, milk, 600L, EvidenceFreshness.FRESH),
                price(addedStore, eggs, 500L, EvidenceFreshness.FRESH),
                price(addedStore, milk, 700L, EvidenceFreshness.FRESH)
            )

        val evaluation =
            evaluate(
                stores = listOf(baseStore, addedStore),
                oneStoreResult =
                    result(
                        prices = prices,
                        baseCovered = setOf(eggs, milk),
                        addedCovered = setOf(eggs, milk)
                    )
            )

        assertNull(evaluation.candidate)
        assertTrue(evaluation.itemSelections.isEmpty())
        assertEquals(
            setOf(PracticalShoppingProductionPairBlocker.ADDED_STORE_DOES_NOT_IMPROVE_BASKET),
            evaluation.blockers
        )
    }

    @Test
    fun `incomplete base cannot be upgraded into a complete split basket`() {
        val prices =
            listOf(
                price(baseStore, eggs, 500L, EvidenceFreshness.FRESH),
                price(addedStore, eggs, 400L, EvidenceFreshness.FRESH),
                price(addedStore, milk, 500L, EvidenceFreshness.FRESH)
            )

        val evaluation =
            evaluate(
                stores = listOf(baseStore, addedStore),
                oneStoreResult =
                    result(
                        prices = prices,
                        baseCovered = setOf(eggs),
                        addedCovered = setOf(eggs, milk)
                    )
            )

        assertNull(evaluation.candidate)
        assertEquals(
            setOf(PracticalShoppingProductionPairBlocker.BASE_STORE_NOT_COMPLETE),
            evaluation.blockers
        )
    }

    @Test
    fun `different store keys for the same exact offer scope cannot masquerade as a second stop`() {
        val aliasStore =
            addedStore.copy(
                merchantKey = baseStore.merchantKey,
                locationKey = baseStore.locationKey,
                commerceChannelKey = baseStore.commerceChannelKey
            )
        val aliasPair =
            PracticalShoppingProductionStorePairScope(
                baseStoreKey = baseStore.storeKey,
                addedStoreKey = aliasStore.storeKey,
                additionalTravel = ShoppingTravel(0L, 0L)
            )
        val prices =
            listOf(
                price(baseStore, eggs, 500L, EvidenceFreshness.FRESH),
                price(baseStore, milk, 600L, EvidenceFreshness.FRESH),
                price(aliasStore, eggs, 400L, EvidenceFreshness.FRESH),
                price(aliasStore, milk, 500L, EvidenceFreshness.FRESH)
            )
        val oneStore =
            result(
                prices = prices,
                baseCovered = setOf(eggs, milk),
                addedCovered = setOf(eggs, milk),
                secondStore = aliasStore
            )

        val evaluation =
            PracticalShoppingProductionPlanCandidateBridge.buildPairEvaluations(
                request = request,
                stores = listOf(baseStore, aliasStore),
                storePairs = listOf(aliasPair),
                oneStoreResult = oneStore
            ).single()

        assertNull(evaluation.candidate)
        assertTrue(PracticalShoppingProductionPairBlocker.SAME_OFFER_SCOPE in evaluation.blockers)
    }

    @Test
    fun `incomparable currency cannot be used merely because its minor-unit number is lower`() {
        val prices =
            listOf(
                price(baseStore, eggs, 500L, EvidenceFreshness.FRESH),
                price(baseStore, milk, 600L, EvidenceFreshness.FRESH),
                price(
                    addedStore,
                    eggs,
                    100L,
                    EvidenceFreshness.FRESH,
                    currencyCode = "USD"
                ),
                price(
                    addedStore,
                    milk,
                    100L,
                    EvidenceFreshness.FRESH,
                    currencyCode = "USD"
                )
            )

        val evaluation =
            evaluate(
                stores = listOf(baseStore, addedStore),
                oneStoreResult =
                    result(
                        prices = prices,
                        baseCovered = setOf(eggs, milk),
                        addedCovered = emptySet()
                    )
            )

        assertNull(evaluation.candidate)
        assertEquals(
            setOf(PracticalShoppingProductionPairBlocker.ADDED_STORE_DOES_NOT_IMPROVE_BASKET),
            evaluation.blockers
        )
    }

    @Test
    fun `ordered pair declarations are bounded and unique`() {
        assertThrows(IllegalArgumentException::class.java) {
            PracticalShoppingProductionPlanCandidateBridge.buildPairEvaluations(
                request = request,
                stores = listOf(baseStore, addedStore),
                storePairs = listOf(pair, pair),
                oneStoreResult =
                    result(
                        prices =
                            listOf(
                                price(baseStore, eggs, 500L, EvidenceFreshness.FRESH),
                                price(baseStore, milk, 600L, EvidenceFreshness.FRESH)
                            ),
                        baseCovered = setOf(eggs, milk),
                        addedCovered = emptySet()
                    )
            )
        }
    }

    private fun evaluate(
        stores: List<PracticalShoppingProductionStoreScope>,
        oneStoreResult: PracticalShoppingProductionCandidateBridgeResult
    ): PracticalShoppingProductionPairEvaluation =
        PracticalShoppingProductionPlanCandidateBridge.buildPairEvaluations(
            request = request,
            stores = stores,
            storePairs = listOf(pair),
            oneStoreResult = oneStoreResult
        ).single()

    private fun result(
        prices: List<PracticalShoppingProductionPriceEvaluation>,
        baseCovered: Set<ShoppingItemKey>,
        addedCovered: Set<ShoppingItemKey>,
        secondStore: PracticalShoppingProductionStoreScope = addedStore
    ): PracticalShoppingProductionCandidateBridgeResult {
        val baseCandidate = candidate(baseStore, baseCovered, prices)
        val addedCandidate =
            if (addedCovered.isEmpty()) {
                null
            } else {
                candidate(secondStore, addedCovered, prices)
            }

        return PracticalShoppingProductionCandidateBridgeResult(
            priceEvaluations = prices,
            storeEvaluations =
                listOf(
                    PracticalShoppingProductionStoreEvaluation(
                        store = baseStore,
                        candidate = baseCandidate,
                        blockers = emptySet()
                    ),
                    if (addedCandidate == null) {
                        PracticalShoppingProductionStoreEvaluation(
                            store = secondStore,
                            candidate = null,
                            blockers =
                                setOf(
                                    PracticalShoppingProductionStoreBlocker.NO_USABLE_PRICES
                                )
                        )
                    } else {
                        PracticalShoppingProductionStoreEvaluation(
                            store = secondStore,
                            candidate = addedCandidate,
                            blockers = emptySet()
                        )
                    }
                )
        )
    }

    private fun candidate(
        store: PracticalShoppingProductionStoreScope,
        covered: Set<ShoppingItemKey>,
        prices: List<PracticalShoppingProductionPriceEvaluation>
    ): SingleStorePlanCandidate {
        val selected =
            prices.filter {
                it.usable &&
                    it.binding.storeKey == store.storeKey &&
                    it.binding.itemKey in covered
            }
        val money = selected.map { requireNotNull(it.selectedPrice) }
        val total = money.drop(1).fold(money.first()) { sum, value -> sum + value }

        return SingleStorePlanCandidate(
            storeKey = store.storeKey,
            coveredItemKeys = covered,
            knownBasketCost = total,
            travel = store.travelFromUser,
            evidence =
                ShoppingPlanEvidenceSummary(
                    freshItemCount = selected.count { it.freshness == EvidenceFreshness.FRESH },
                    agingItemCount = selected.count { it.freshness == EvidenceFreshness.AGING },
                    staleItemCount = 0,
                    unknownFreshnessItemCount = 0
                )
        )
    }

    private fun price(
        store: PracticalShoppingProductionStoreScope,
        item: ShoppingItemKey,
        minorUnits: Long,
        freshness: EvidenceFreshness,
        currencyCode: String = "CAD"
    ): PracticalShoppingProductionPriceEvaluation {
        val productKey =
            ProductionProductEvidenceKey(
                value = "product-${item.value}",
                scope = ProductionProductKeyScope.CROSS_SOURCE_GTIN
            )
        val binding =
            PracticalShoppingProductionPriceBinding(
                itemKey = item,
                productKey = productKey,
                storeKey = store.storeKey,
                currentPriceRequestId = "price-${store.storeKey.value}-${item.value}-$currencyCode"
            )

        return PracticalShoppingProductionPriceEvaluation(
            binding = binding,
            selectedPrice = Money(minorUnits, currencyCode),
            freshness = freshness,
            upstreamBlockers = emptySet(),
            blockers = emptySet()
        )
    }
}
