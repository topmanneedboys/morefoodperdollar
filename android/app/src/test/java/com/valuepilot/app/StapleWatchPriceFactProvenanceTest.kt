package com.valuepilot.app

import com.valuepilot.core.AvailabilityState
import com.valuepilot.core.PracticalShoppingProductionPriceBinding
import com.valuepilot.core.PracticalShoppingProductionPriceStoreScope
import com.valuepilot.core.ShoppingItemKey
import com.valuepilot.core.ShoppingRequest
import com.valuepilot.core.ShoppingStoreKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class StapleWatchPriceEvidenceProvenanceTest {

    private val fixture = StapleWatchProductionPriceTestFixture()
    private val milk = ShoppingItemKey("milk")
    private val eggs = ShoppingItemKey("eggs")
    private val usualStore = ShoppingStoreKey("usual")
    private val east = ShoppingStoreKey("east")
    private val west = ShoppingStoreKey("west")
    private val intent =
        StapleWatchFactCheckIntent(
            request = ShoppingRequest(listOf(milk, eggs)),
            usualStoreKey = usualStore
        )

    @Test
    fun usualStoreFactsRetainExactRawInputsInRequestOrderIncludingBlockedBinding() {
        val store =
            PracticalShoppingProductionPriceStoreScope(
                storeKey = usualStore,
                merchantKey = "merchant-a",
                locationKey = "location-a",
                commerceChannelKey = "IN_STORE"
            )
        val milkPrice = fixture.case("usual-anchor-milk", "usual-anchor-milk-product")
        val blockedEggs =
            fixture.case(
                requestId = "usual-anchor-eggs",
                providerItemId = "usual-anchor-eggs-product",
                availability = AvailabilityState.OUT_OF_STOCK
            )
        val milkBinding = binding(milk, usualStore, milkPrice)
        val eggsBinding = binding(eggs, usualStore, blockedEggs)
        val registries = fixture.registries(listOf(milkPrice, blockedEggs))

        val facts =
            StapleWatchUsualStoreBasketPriceFacts.resolve(
                intent = intent,
                store = store,
                priceBindings = listOf(eggsBinding, milkBinding),
                priceRequests = listOf(blockedEggs.request, milkPrice.request),
                lifecycleRegistry = registries.lifecycle,
                dispositionRegistry = registries.disposition,
                evaluatedAtEpochMillis = fixture.evaluatedAtEpochMillis,
                acceptancePolicy = fixture.acceptancePolicy
            )

        assertEquals(
            listOf(
                StapleWatchBasketItemPriceState.USABLE_EXACT_PRICE,
                StapleWatchBasketItemPriceState.PRODUCTION_PRICE_BLOCKED
            ),
            facts.itemPrices.map { fact -> fact.state }
        )
        assertSame(store, facts.productionStoreScope)
        assertEquals(listOf(milkBinding, eggsBinding), facts.productionPriceBindings)
        assertSame(milkPrice.request, facts.productionPriceRequests[0])
        assertSame(blockedEggs.request, facts.productionPriceRequests[1])
    }

    @Test
    fun alternativeFactsRetainExactRawInputsInStableStoreThenRequestOrder() {
        val identityFacts =
            StapleWatchAlternativeStoreIdentityFacts.fromUnordered(
                intent = intent,
                alternativeStoreKeys = listOf(west, east)
            )
        val eastStore = store(east)
        val westStore = store(west)
        val eastEggs =
            fixture.case(
                requestId = "east-anchor-eggs",
                providerItemId = "east-anchor-eggs-product",
                merchantKey = "merchant-east",
                locationKey = "location-east"
            )
        val westMilk =
            fixture.case(
                requestId = "west-anchor-milk",
                providerItemId = "west-anchor-milk-product",
                merchantKey = "merchant-west",
                locationKey = "location-west"
            )
        val eastEggsBinding = binding(eggs, east, eastEggs)
        val westMilkBinding = binding(milk, west, westMilk)
        val registries = fixture.registries(listOf(eastEggs, westMilk))

        val facts =
            StapleWatchAlternativeStoreBasketPriceFacts.resolve(
                identityFacts = identityFacts,
                stores = listOf(westStore, eastStore),
                priceBindings = listOf(westMilkBinding, eastEggsBinding),
                priceRequests = listOf(westMilk.request, eastEggs.request),
                lifecycleRegistry = registries.lifecycle,
                dispositionRegistry = registries.disposition,
                evaluatedAtEpochMillis = fixture.evaluatedAtEpochMillis,
                acceptancePolicy = fixture.acceptancePolicy
            )

        assertEquals(listOf(east, west), facts.productionStoreScopes.map { store -> store.storeKey })
        assertEquals(
            listOf(east to eggs, west to milk),
            facts.productionPriceBindings.map { binding -> binding.storeKey to binding.itemKey }
        )
        assertSame(eastEggs.request, facts.productionPriceRequests[0])
        assertSame(westMilk.request, facts.productionPriceRequests[1])
    }

    @Test
    fun explicitNoAlternativesRetainsNoProductionPriceProvenance() {
        val identityFacts =
            StapleWatchAlternativeStoreIdentityFacts.fromUnordered(
                intent = intent,
                alternativeStoreKeys = emptyList()
            )
        val registries = fixture.registries(emptyList())

        val facts =
            StapleWatchAlternativeStoreBasketPriceFacts.resolve(
                identityFacts = identityFacts,
                stores = emptyList(),
                priceBindings = emptyList(),
                priceRequests = emptyList(),
                lifecycleRegistry = registries.lifecycle,
                dispositionRegistry = registries.disposition,
                evaluatedAtEpochMillis = fixture.evaluatedAtEpochMillis,
                acceptancePolicy = fixture.acceptancePolicy
            )

        assertTrue(facts.productionStoreScopes.isEmpty())
        assertTrue(facts.productionPriceBindings.isEmpty())
        assertTrue(facts.productionPriceRequests.isEmpty())
    }

    private fun store(storeKey: ShoppingStoreKey): PracticalShoppingProductionPriceStoreScope =
        PracticalShoppingProductionPriceStoreScope(
            storeKey = storeKey,
            merchantKey = "merchant-${storeKey.value}",
            locationKey = "location-${storeKey.value}",
            commerceChannelKey = "IN_STORE"
        )

    private fun binding(
        itemKey: ShoppingItemKey,
        storeKey: ShoppingStoreKey,
        priceCase: StapleWatchProductionPriceTestFixture.PriceCase
    ): PracticalShoppingProductionPriceBinding =
        PracticalShoppingProductionPriceBinding(
            itemKey = itemKey,
            productKey = priceCase.productKey,
            storeKey = storeKey,
            currentPriceRequestId = priceCase.request.requestId
        )
}
