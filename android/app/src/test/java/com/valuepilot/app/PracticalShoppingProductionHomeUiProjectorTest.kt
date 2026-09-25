package com.valuepilot.app

import com.valuepilot.core.Money
import com.valuepilot.core.PracticalShoppingPolicy
import com.valuepilot.core.PracticalShoppingProductionOrchestrationRequest
import com.valuepilot.core.PracticalShoppingProductionOrchestrator
import com.valuepilot.core.PracticalShoppingProductionPriceBinding
import com.valuepilot.core.PracticalShoppingProductionStoreScope
import com.valuepilot.core.ProductionDatasetDispositionRegistry
import com.valuepilot.core.ProductionDatasetLifecycleRegistry
import com.valuepilot.core.ShoppingItemKey
import com.valuepilot.core.ShoppingRequest
import com.valuepilot.core.ShoppingStoreKey
import com.valuepilot.core.ShoppingTravel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticalShoppingProductionHomeUiProjectorTest {

    private val fixture = StapleWatchProductionPriceTestFixture()
    private val policy =
        PracticalShoppingPolicy(
            minimumSecondStopSavings = Money.parse("15.00", "CAD"),
            maxAdditionalTravelSeconds = 600L,
            maxAdditionalDistanceMetres = 5_000L
        )

    @Test
    fun completeResultKeepsExistingDecisionFormattingAndAddsSafeRows() {
        val item = ShoppingItemKey("production-home-milk")
        val store = store("production-home-store")
        val price = priceCase("production-home-complete", store)
        val request =
            orchestrationRequest(
                shoppingRequest = ShoppingRequest(listOf(item)),
                store = store,
                priceBindings =
                    listOf(
                        PracticalShoppingProductionPriceBinding(
                            itemKey = item,
                            productKey = price.productKey,
                            storeKey = store.storeKey,
                            currentPriceRequestId = price.request.requestId
                        )
                    ),
                priceRequests = listOf(price.request)
            )
        val projection = projection(request, listOf(price))

        val uiState =
            PracticalShoppingProductionHomeUiProjector.project(
                request = request,
                projection = projection,
                itemDisplayNames = mapOf(item to "Milk")
            )

        assertEquals(PracticalShoppingProductionHomeStatus.READY, uiState.status)
        assertNull(uiState.notice)
        assertSame(projection.state, uiState.result)
        assertEquals(listOf("Milk"), uiState.items.map { row -> row.name })
        assertEquals(
            listOf("Neighbourhood Market"),
            uiState.items.map { row -> row.storeAssignment }
        )
        assertEquals(listOf("5.00 CAD"), uiState.items.map { row -> row.plannedPriceText })
        assertNull(uiState.items.single().coverageNotice)
        assertFalse(uiState.toString().contains(item.value))
        assertFalse(uiState.toString().contains(store.storeKey.value))
    }

    @Test
    fun incompleteResultKeepsKnownSubtotalAndMarksOnlyMissingRowsUnknown() {
        val firstItem = ShoppingItemKey("production-home-eggs")
        val missingItem = ShoppingItemKey("production-home-coffee")
        val store = store("production-home-partial-store")
        val price = priceCase("production-home-partial", store)
        val request =
            orchestrationRequest(
                shoppingRequest = ShoppingRequest(listOf(firstItem, missingItem)),
                store = store,
                priceBindings =
                    listOf(
                        PracticalShoppingProductionPriceBinding(
                            itemKey = firstItem,
                            productKey = price.productKey,
                            storeKey = store.storeKey,
                            currentPriceRequestId = price.request.requestId
                        )
                    ),
                priceRequests = listOf(price.request)
            )
        val projection = projection(request, listOf(price))
        val uiState =
            PracticalShoppingProductionHomeUiProjector.project(
                request = request,
                projection = projection,
                itemDisplayNames = mapOf(firstItem to "Eggs", missingItem to "Coffee")
            )

        val primary = requireNotNull(uiState.result).primary
        assertEquals(PracticalShoppingProductionHomeStatus.READY, uiState.status)
        assertEquals("Known subtotal 5.00 CAD", primary?.basketCostText)
        assertEquals("1 of 2 items priced", primary?.coverageText)
        assertEquals(
            listOf(null, PracticalShoppingProductionHomeUiProjector.UNKNOWN_PRICE_NOTICE),
            uiState.items.map { row -> row.coverageNotice }
        )
        assertEquals(listOf("5.00 CAD", null), uiState.items.map { row -> row.plannedPriceText })
    }

    @Test
    fun coveredResultWithoutOptionalBreakdownStatesThatTheSubtotalIncludesTheItem() {
        val item = ShoppingItemKey("production-home-breakdownless-item")
        val store = store("production-home-breakdownless-store")
        val price = priceCase("production-home-breakdownless", store)
        val request =
            orchestrationRequest(
                shoppingRequest = ShoppingRequest(listOf(item)),
                store = store,
                priceBindings =
                    listOf(
                        PracticalShoppingProductionPriceBinding(
                            itemKey = item,
                            productKey = price.productKey,
                            storeKey = store.storeKey,
                            currentPriceRequestId = price.request.requestId
                        )
                    ),
                priceRequests = listOf(price.request)
            )
        val projection = projection(request, listOf(price))
        val originalResult = requireNotNull(projection.result)
        val resultWithoutBreakdown =
            originalResult.copy(
                state =
                    originalResult.state.copy(
                        itemStoreAssignments =
                            originalResult.state.itemStoreAssignments.map { assignment ->
                                assignment.copy(priceText = null)
                            }
                    )
            )

        val uiState =
            PracticalShoppingProductionHomeUiProjector.project(
                request = request,
                projection = projection.copy(result = resultWithoutBreakdown),
                itemDisplayNames = mapOf(item to "Milk")
            )

        val row = uiState.items.single()
        assertNull(row.plannedPriceText)
        assertEquals(
            PracticalShoppingProductionHomeUiProjector.ITEM_PRICE_BREAKDOWN_NOTICE,
            row.plannedPriceNotice
        )
        assertNull(row.coverageNotice)
    }

    @Test
    fun validNoCoverageStaysReadyAndMakesEveryUnknownItemVisible() {
        val firstItem = ShoppingItemKey("production-home-no-price-eggs")
        val secondItem = ShoppingItemKey("production-home-no-price-milk")
        val store = store("production-home-no-price-store")
        val request =
            orchestrationRequest(
                shoppingRequest = ShoppingRequest(listOf(firstItem, secondItem)),
                store = store
            )
        val projection = projection(request, emptyList())
        val uiState =
            PracticalShoppingProductionHomeUiProjector.project(
                request = request,
                projection = projection,
                itemDisplayNames = mapOf(firstItem to "Eggs", secondItem to "Milk")
            )

        assertEquals(PracticalShoppingProductionHomeStatus.READY, uiState.status)
        assertNull(uiState.notice)
        assertNull(uiState.result?.primary)
        assertEquals(
            listOf(
                PracticalShoppingProductionHomeUiProjector.UNKNOWN_PRICE_NOTICE,
                PracticalShoppingProductionHomeUiProjector.UNKNOWN_PRICE_NOTICE
            ),
            uiState.items.map { row -> row.coverageNotice }
        )
        assertEquals(
            "No requested item has a usable price yet.",
            uiState.result?.secondaryMessage
        )
    }

    @Test
    fun unavailableProjectionDoesNotNeedDisplayNamesOrExposeInternalDetails() {
        val item = ShoppingItemKey("production-home-unavailable-item")
        val store = store("production-home-unavailable-store")
        val request =
            orchestrationRequest(
                shoppingRequest = ShoppingRequest(listOf(item)),
                store = store
            )
        val unavailable =
            PracticalShoppingProductionHomeProjection(
                status = PracticalShoppingProductionHomeStatus.UNAVAILABLE,
                result = null,
                notice = PracticalShoppingProductionHomeAdapter.UNAVAILABLE_NOTICE
            )

        val uiState =
            PracticalShoppingProductionHomeUiProjector.project(
                request = request,
                projection = unavailable,
                itemDisplayNames = emptyMap()
            )

        assertEquals(PracticalShoppingProductionHomeStatus.UNAVAILABLE, uiState.status)
        assertTrue(uiState.items.isEmpty())
        assertNull(uiState.result)
        assertEquals(PracticalShoppingProductionHomeAdapter.UNAVAILABLE_NOTICE, uiState.notice)
        assertFalse(uiState.toString().contains(item.value))
        assertFalse(uiState.toString().contains(store.storeKey.value))
    }

    @Test
    fun missingOrUnsafeDisplayMetadataFailsClosedWithoutChangingThePlan() {
        val item = ShoppingItemKey("production-home-unsafe-item")
        val store = store("production-home-unsafe-store")
        val price = priceCase("production-home-unsafe", store)
        val request =
            orchestrationRequest(
                shoppingRequest = ShoppingRequest(listOf(item)),
                store = store,
                priceBindings =
                    listOf(
                        PracticalShoppingProductionPriceBinding(
                            itemKey = item,
                            productKey = price.productKey,
                            storeKey = store.storeKey,
                            currentPriceRequestId = price.request.requestId
                        )
                    ),
                priceRequests = listOf(price.request)
            )
        val projection = projection(request, listOf(price))

        val missingName =
            PracticalShoppingProductionHomeUiProjector.project(
                request,
                projection,
                emptyMap()
            )
        val unsafeName =
            PracticalShoppingProductionHomeUiProjector.project(
                request,
                projection,
                mapOf(item to "Milk\u0000")
            )

        listOf(missingName, unsafeName).forEach { uiState ->
            assertEquals(PracticalShoppingProductionHomeStatus.UNAVAILABLE, uiState.status)
            assertNull(uiState.result)
            assertEquals(PracticalShoppingProductionHomeAdapter.UNAVAILABLE_NOTICE, uiState.notice)
            assertTrue(uiState.items.isEmpty())
        }
    }

    @Test
    fun assignmentForAnUnrequestedItemFailsClosed() {
        val requestedItem = ShoppingItemKey("production-home-requested")
        val extraItem = ShoppingItemKey("production-home-extra")
        val store = store("production-home-extra-store")
        val price = priceCase("production-home-extra", store)
        val request =
            orchestrationRequest(
                shoppingRequest = ShoppingRequest(listOf(requestedItem)),
                store = store,
                priceBindings =
                    listOf(
                        PracticalShoppingProductionPriceBinding(
                            itemKey = requestedItem,
                            productKey = price.productKey,
                            storeKey = store.storeKey,
                            currentPriceRequestId = price.request.requestId
                        )
                    ),
                priceRequests = listOf(price.request)
            )
        val projection = projection(request, listOf(price))
        val originalResult = requireNotNull(projection.result)
        val malformedState =
            originalResult.state.copy(
                itemStoreAssignments =
                    originalResult.state.itemStoreAssignments +
                        PracticalShoppingItemStoreAssignmentUiState(
                            itemKey = extraItem,
                            storeName = "Neighbourhood Market",
                            priceText = "1.00 CAD"
                        )
            )
        val malformedProjection = projection.copy(result = originalResult.copy(state = malformedState))

        val uiState =
            PracticalShoppingProductionHomeUiProjector.project(
                request,
                malformedProjection,
                mapOf(requestedItem to "Milk")
            )

        assertEquals(PracticalShoppingProductionHomeStatus.UNAVAILABLE, uiState.status)
        assertNull(uiState.result)
        assertEquals(PracticalShoppingProductionHomeAdapter.UNAVAILABLE_NOTICE, uiState.notice)
    }

    private fun projection(
        request: PracticalShoppingProductionOrchestrationRequest,
        cases: Collection<StapleWatchProductionPriceTestFixture.PriceCase>
    ): PracticalShoppingProductionHomeProjection {
        val registries = fixture.registries(cases)
        val orchestrationResult =
            PracticalShoppingProductionOrchestrator.evaluate(
                request = request,
                lifecycleRegistry = registries.lifecycle,
                dispositionRegistry = registries.disposition
            )
        return PracticalShoppingProductionHomeAdapter.project(
            request = request,
            orchestrationResult = orchestrationResult,
            storeDisplayNames =
                request.stores.associate { store ->
                    store.storeKey to "Neighbourhood Market"
                },
            itemDisplayNames =
                request.shoppingRequest.itemKeys
                    .mapIndexed { index, item -> item to "Item ${index + 1}" }
                    .toMap()
        )
    }

    private fun orchestrationRequest(
        shoppingRequest: ShoppingRequest,
        store: PracticalShoppingProductionStoreScope,
        priceBindings: List<PracticalShoppingProductionPriceBinding> = emptyList(),
        priceRequests: List<com.valuepilot.core.ProductionCurrentPriceEligibilityRequest> = emptyList()
    ): PracticalShoppingProductionOrchestrationRequest =
        PracticalShoppingProductionOrchestrationRequest(
            shoppingRequest = shoppingRequest,
            stores = listOf(store),
            storePairs = emptyList(),
            priceBindings = priceBindings,
            priceRequests = priceRequests,
            evaluatedAtEpochMillis = fixture.evaluatedAtEpochMillis,
            acceptancePolicy = fixture.acceptancePolicy,
            planningPolicy = policy
        )

    private fun priceCase(
        requestId: String,
        store: PracticalShoppingProductionStoreScope
    ): StapleWatchProductionPriceTestFixture.PriceCase =
        fixture.case(
            requestId = requestId,
            providerItemId = requestId,
            merchantKey = store.merchantKey,
            locationKey = store.locationKey,
            commerceChannelKey = store.commerceChannelKey
        )

    private fun store(key: String): PracticalShoppingProductionStoreScope =
        PracticalShoppingProductionStoreScope(
            storeKey = ShoppingStoreKey(key),
            merchantKey = "merchant-$key",
            locationKey = "location-$key",
            commerceChannelKey = "IN_STORE",
            travelFromUser = ShoppingTravel(1_200L, 300L)
        )
}
