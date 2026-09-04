package com.valuepilot.app

import com.valuepilot.core.Money
import com.valuepilot.core.PracticalShoppingPolicy
import com.valuepilot.core.PracticalShoppingProductionOrchestrationRequest
import com.valuepilot.core.PracticalShoppingProductionOrchestrator
import com.valuepilot.core.PracticalShoppingProductionPriceBinding
import com.valuepilot.core.PracticalShoppingProductionStoreScope
import com.valuepilot.core.ProductionCurrentPriceEligibilityRequest
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

class PracticalShoppingProductionHomeAdapterTest {

    private val fixture = StapleWatchProductionPriceTestFixture()
    private val policy =
        PracticalShoppingPolicy(
            minimumSecondStopSavings = Money.parse("15.00", "CAD"),
            maxAdditionalTravelSeconds = 600L,
            maxAdditionalDistanceMetres = 5_000L
        )

    @Test
    fun validCompleteProductionDecisionUsesTheExistingHomeProjectorExactly() {
        val item = ShoppingItemKey("home-milk")
        val store = store("home-store")
        val price =
            fixture.case(
                requestId = "home-price",
                providerItemId = "milk",
                merchantKey = "merchant-home-store",
                locationKey = "location-home-store",
                commerceChannelKey = "IN_STORE",
                priceMinor = 599L
            )
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
        val registries = fixture.registries(listOf(price))
        val orchestrationResult =
            PracticalShoppingProductionOrchestrator.evaluate(
                request = request,
                lifecycleRegistry = registries.lifecycle,
                dispositionRegistry = registries.disposition
            )

        assertTrue(orchestrationResult.validation.valid)
        val decision = requireNotNull(orchestrationResult.decisionResult).decision
        val projection =
            PracticalShoppingProductionHomeAdapter.project(
                request = request,
                orchestrationResult = orchestrationResult,
                storeDisplayNames = mapOf(store.storeKey to "Neighbourhood Market"),
                itemDisplayNames = mapOf(item to "Milk")
            )

        assertEquals(PracticalShoppingProductionHomeStatus.READY, projection.status)
        assertNull(projection.notice)
        assertEquals("Your best practical shop", projection.state?.headline)
        assertEquals("Basket 5.99 CAD", projection.state?.primary?.basketCostText)
        assertEquals("Neighbourhood Market", projection.state?.primary?.storeName)
        assertEquals(
            listOf("5.99 CAD"),
            projection.state?.itemStoreAssignments?.map { it.priceText }
        )
        assertSame(decision, requireNotNull(projection.result).exactDecision)
    }

    @Test
    fun validProductionNoCoverageRemainsAVisibleNoCoverageOutcome() {
        val item = ShoppingItemKey("home-coffee")
        val store = store("home-empty-store")
        val request =
            orchestrationRequest(
                shoppingRequest = ShoppingRequest(listOf(item)),
                store = store
            )
        val orchestrationResult =
            PracticalShoppingProductionOrchestrator.evaluate(
                request = request,
                lifecycleRegistry = ProductionDatasetLifecycleRegistry(),
                dispositionRegistry = ProductionDatasetDispositionRegistry()
            )

        val projection =
            PracticalShoppingProductionHomeAdapter.project(
                request = request,
                orchestrationResult = orchestrationResult,
                storeDisplayNames = mapOf(store.storeKey to "Neighbourhood Market"),
                itemDisplayNames = mapOf(item to "Coffee")
            )

        assertEquals(PracticalShoppingProductionHomeStatus.READY, projection.status)
        assertEquals("Not enough price coverage yet", projection.state?.headline)
        assertNull(projection.state?.primary)
        assertEquals(
            "No requested item has a usable price yet.",
            projection.state?.secondaryMessage
        )
        assertNull(projection.notice)
    }

    @Test
    fun invalidProductionResultCannotMasqueradeAsNoCoverage() {
        val firstItem = ShoppingItemKey("home-eggs")
        val secondItem = ShoppingItemKey("home-milk")
        val store = store("home-invalid-store")
        val price =
            fixture.case(
                requestId = "home-invalid-price",
                providerItemId = "same-product",
                merchantKey = "merchant-home-invalid-store",
                locationKey = "location-home-invalid-store",
                commerceChannelKey = "IN_STORE"
            )
        val secondRawRequest = price.request.copy(requestId = "home-invalid-price-2")
        val request =
            orchestrationRequest(
                shoppingRequest = ShoppingRequest(listOf(firstItem, secondItem)),
                store = store,
                priceBindings =
                    listOf(
                        PracticalShoppingProductionPriceBinding(
                            itemKey = firstItem,
                            productKey = price.productKey,
                            storeKey = store.storeKey,
                            currentPriceRequestId = price.request.requestId
                        ),
                        PracticalShoppingProductionPriceBinding(
                            itemKey = secondItem,
                            productKey = price.productKey,
                            storeKey = store.storeKey,
                            currentPriceRequestId = secondRawRequest.requestId
                        )
                    ),
                priceRequests = listOf(price.request, secondRawRequest)
            )
        val registries = fixture.registries(listOf(price))
        val orchestrationResult =
            PracticalShoppingProductionOrchestrator.evaluate(
                request = request,
                lifecycleRegistry = registries.lifecycle,
                dispositionRegistry = registries.disposition
            )

        assertFalse(orchestrationResult.validation.valid)
        val projection =
            PracticalShoppingProductionHomeAdapter.project(
                request = request,
                orchestrationResult = orchestrationResult,
                storeDisplayNames = mapOf(store.storeKey to "Neighbourhood Market"),
                itemDisplayNames = mapOf(firstItem to "Eggs", secondItem to "Milk")
            )

        assertEquals(PracticalShoppingProductionHomeStatus.UNAVAILABLE, projection.status)
        assertNull(projection.result)
        assertEquals(
            PracticalShoppingProductionHomeAdapter.UNAVAILABLE_NOTICE,
            projection.notice
        )
        assertFalse(projection.notice.orEmpty().contains("DUPLICATE"))
        assertFalse(projection.notice.orEmpty().contains("home-invalid"))
    }

    private fun orchestrationRequest(
        shoppingRequest: ShoppingRequest,
        store: PracticalShoppingProductionStoreScope,
        priceBindings: List<PracticalShoppingProductionPriceBinding> = emptyList(),
        priceRequests: List<ProductionCurrentPriceEligibilityRequest> = emptyList()
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

    private fun store(key: String): PracticalShoppingProductionStoreScope =
        PracticalShoppingProductionStoreScope(
            storeKey = ShoppingStoreKey(key),
            merchantKey = "merchant-$key",
            locationKey = "location-$key",
            commerceChannelKey = "IN_STORE",
            travelFromUser = ShoppingTravel(distanceMetres = 1_200L, travelTimeSeconds = 300L)
        )
}
