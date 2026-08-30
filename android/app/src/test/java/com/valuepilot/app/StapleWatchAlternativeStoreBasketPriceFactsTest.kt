package com.valuepilot.app

import com.valuepilot.core.AvailabilityState
import com.valuepilot.core.Money
import com.valuepilot.core.PracticalShoppingProductionPriceBinding
import com.valuepilot.core.PracticalShoppingProductionPriceStoreScope
import com.valuepilot.core.ShoppingItemKey
import com.valuepilot.core.ShoppingRequest
import com.valuepilot.core.ShoppingStoreKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.lang.reflect.Modifier

class StapleWatchAlternativeStoreBasketPriceFactsTest {

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
    fun usableAndMissingPricesPreserveStableStoreAndRequestOrder() {
        val identities = identities(west, east)
        val eastEggs =
            fixture.case(
                requestId = "east-eggs-price",
                providerItemId = "east-eggs-product",
                merchantKey = "merchant-east",
                locationKey = "location-east",
                priceMinor = 410L
            )
        val westMilk =
            fixture.case(
                requestId = "west-milk-price",
                providerItemId = "west-milk-product",
                merchantKey = "merchant-west",
                locationKey = "location-west",
                priceMinor = 520L
            )
        val registries = fixture.registries(listOf(eastEggs, westMilk))

        val facts =
            StapleWatchAlternativeStoreBasketPriceFacts.resolve(
                identityFacts = identities,
                stores = listOf(store(west), store(east)),
                priceBindings =
                    listOf(
                        binding(eggs, east, eastEggs),
                        binding(milk, west, westMilk)
                    ),
                priceRequests = listOf(westMilk.request, eastEggs.request),
                lifecycleRegistry = registries.lifecycle,
                dispositionRegistry = registries.disposition,
                evaluatedAtEpochMillis = fixture.evaluatedAtEpochMillis,
                acceptancePolicy = fixture.acceptancePolicy
            )

        assertEquals(intent, facts.intent)
        assertEquals(
            StapleWatchFactResolutionRequirement.ALTERNATIVE_STORE_BASKET_PRICE_EVIDENCE,
            facts.resolvedRequirement
        )
        assertEquals(listOf(east, west), facts.alternatives.map { fact -> fact.storeKey })
        assertEquals(listOf(milk, eggs), facts.alternatives[0].itemPrices.map { it.itemKey })
        assertEquals(
            listOf(
                StapleWatchBasketItemPriceState.NO_BOUND_PRODUCTION_PRICE,
                StapleWatchBasketItemPriceState.USABLE_EXACT_PRICE
            ),
            facts.alternatives[0].itemPrices.map { it.state }
        )
        assertNull(facts.alternatives[0].itemPrices[0].exactPrice)
        assertEquals(Money(410L, "CAD"), facts.alternatives[0].itemPrices[1].exactPrice)

        assertEquals(listOf(milk, eggs), facts.alternatives[1].itemPrices.map { it.itemKey })
        assertEquals(
            listOf(
                StapleWatchBasketItemPriceState.USABLE_EXACT_PRICE,
                StapleWatchBasketItemPriceState.NO_BOUND_PRODUCTION_PRICE
            ),
            facts.alternatives[1].itemPrices.map { it.state }
        )
        assertEquals(Money(520L, "CAD"), facts.alternatives[1].itemPrices[0].exactPrice)
        assertNull(facts.alternatives[1].itemPrices[1].exactPrice)
    }

    @Test
    fun productionRejectedPriceRemainsBlockedWithoutMoney() {
        val identities = identities(east)
        val blockedMilk =
            fixture.case(
                requestId = "east-blocked-milk",
                providerItemId = "east-blocked-milk-product",
                merchantKey = "merchant-east",
                locationKey = "location-east",
                availability = AvailabilityState.OUT_OF_STOCK
            )
        val registries = fixture.registries(listOf(blockedMilk))

        val facts =
            StapleWatchAlternativeStoreBasketPriceFacts.resolve(
                identityFacts = identities,
                stores = listOf(store(east)),
                priceBindings = listOf(binding(milk, east, blockedMilk)),
                priceRequests = listOf(blockedMilk.request),
                lifecycleRegistry = registries.lifecycle,
                dispositionRegistry = registries.disposition,
                evaluatedAtEpochMillis = fixture.evaluatedAtEpochMillis,
                acceptancePolicy = fixture.acceptancePolicy
            )

        assertEquals(
            listOf(
                StapleWatchBasketItemPriceState.PRODUCTION_PRICE_BLOCKED,
                StapleWatchBasketItemPriceState.NO_BOUND_PRODUCTION_PRICE
            ),
            facts.alternatives.single().itemPrices.map { it.state }
        )
        assertTrue(facts.alternatives.single().itemPrices.all { it.exactPrice == null })
    }

    @Test
    fun explicitNoAlternativeIdentitiesProducesExplicitEmptyPriceFacts() {
        val identities = identities()
        val registries = fixture.registries(emptyList())

        val facts =
            StapleWatchAlternativeStoreBasketPriceFacts.resolve(
                identityFacts = identities,
                stores = emptyList(),
                priceBindings = emptyList(),
                priceRequests = emptyList(),
                lifecycleRegistry = registries.lifecycle,
                dispositionRegistry = registries.disposition,
                evaluatedAtEpochMillis = fixture.evaluatedAtEpochMillis,
                acceptancePolicy = fixture.acceptancePolicy
            )

        assertTrue(facts.alternatives.isEmpty())
        assertEquals(
            StapleWatchFactResolutionRequirement.ALTERNATIVE_STORE_BASKET_PRICE_EVIDENCE,
            facts.resolvedRequirement
        )
    }

    @Test
    fun productionStoreScopesMustExactlyCoverResolvedAlternativeIdentities() {
        val identities = identities(east, west)
        val registries = fixture.registries(emptyList())

        expectIllegalArgument {
            StapleWatchAlternativeStoreBasketPriceFacts.resolve(
                identityFacts = identities,
                stores = listOf(store(east)),
                priceBindings = emptyList(),
                priceRequests = emptyList(),
                lifecycleRegistry = registries.lifecycle,
                dispositionRegistry = registries.disposition,
                evaluatedAtEpochMillis = fixture.evaluatedAtEpochMillis,
                acceptancePolicy = fixture.acceptancePolicy
            )
        }
    }

    @Test
    fun bindingForStoreOutsideResolvedAlternativesFailsClosed() {
        val identities = identities(east)
        val westPrice =
            fixture.case(
                requestId = "foreign-west-price",
                providerItemId = "foreign-west-product",
                merchantKey = "merchant-west",
                locationKey = "location-west"
            )
        val registries = fixture.registries(listOf(westPrice))

        expectIllegalArgument {
            StapleWatchAlternativeStoreBasketPriceFacts.resolve(
                identityFacts = identities,
                stores = listOf(store(east)),
                priceBindings = listOf(binding(milk, west, westPrice)),
                priceRequests = listOf(westPrice.request),
                lifecycleRegistry = registries.lifecycle,
                dispositionRegistry = registries.disposition,
                evaluatedAtEpochMillis = fixture.evaluatedAtEpochMillis,
                acceptancePolicy = fixture.acceptancePolicy
            )
        }
    }

    @Test
    fun bindingForUnrequestedItemFailsClosed() {
        val bread = ShoppingItemKey("bread")
        val identities = identities(east)
        val breadPrice =
            fixture.case(
                requestId = "east-bread-price",
                providerItemId = "east-bread-product",
                merchantKey = "merchant-east",
                locationKey = "location-east"
            )
        val registries = fixture.registries(listOf(breadPrice))

        expectIllegalArgument {
            StapleWatchAlternativeStoreBasketPriceFacts.resolve(
                identityFacts = identities,
                stores = listOf(store(east)),
                priceBindings = listOf(binding(bread, east, breadPrice)),
                priceRequests = listOf(breadPrice.request),
                lifecycleRegistry = registries.lifecycle,
                dispositionRegistry = registries.disposition,
                evaluatedAtEpochMillis = fixture.evaluatedAtEpochMillis,
                acceptancePolicy = fixture.acceptancePolicy
            )
        }
    }

    @Test
    fun unboundProductionRequestFailsClosedInsteadOfGrantingExtraEvidence() {
        val identities = identities(east)
        val eastPrice =
            fixture.case(
                requestId = "unbound-east-price",
                providerItemId = "unbound-east-product",
                merchantKey = "merchant-east",
                locationKey = "location-east"
            )
        val registries = fixture.registries(listOf(eastPrice))

        expectIllegalArgument {
            StapleWatchAlternativeStoreBasketPriceFacts.resolve(
                identityFacts = identities,
                stores = listOf(store(east)),
                priceBindings = emptyList(),
                priceRequests = listOf(eastPrice.request),
                lifecycleRegistry = registries.lifecycle,
                dispositionRegistry = registries.disposition,
                evaluatedAtEpochMillis = fixture.evaluatedAtEpochMillis,
                acceptancePolicy = fixture.acceptancePolicy
            )
        }
    }

    @Test
    fun authoritativeFactsArePrivatelyMintedAndOwnNoCurrentnessTravelEconomicsOrDelivery() {
        val constructors = StapleWatchAlternativeStoreBasketPriceFacts::class.java.declaredConstructors
        assertTrue(constructors.isNotEmpty())
        assertTrue(
            constructors
                .filterNot { constructor -> constructor.isSynthetic }
                .all { constructor -> Modifier.isPrivate(constructor.modifiers) }
        )
        assertFalse(
            StapleWatchAlternativeStoreBasketPriceFacts::class.java.methods.any { method ->
                method.name == "copy" || method.name.startsWith("copy$")
            }
        )

        val source = source("StapleWatchAlternativeStoreBasketPriceFacts.kt").readText()
        assertTrue(
            source.contains("class StapleWatchAlternativeStoreBasketPriceFacts private constructor(")
        )
        assertTrue(source.contains("PracticalShoppingProductionCandidateBridge.evaluatePrices"))
        assertTrue(source.contains("ALTERNATIVE_STORE_BASKET_PRICE_EVIDENCE"))
        assertFalse(source.contains("StapleWatchUsualStoreBasketPriceFacts"))

        listOf(
            "EvidenceFreshness",
            ".freshness",
            "EVIDENCE_CURRENTNESS_METADATA",
            "ShoppingTravel",
            "StapleWatchAlternativeAdditionalTravelFacts",
            "StapleWatchPolicy",
            "StapleWatchEconomicEvaluator",
            "StapleWatchEconomicDecision",
            "OpenPrices",
            "OpenStreetMap",
            "Http",
            "URL(",
            "System.currentTimeMillis",
            "SharedPreferences",
            "WorkManager",
            "NotificationManager",
            "android."
        ).forEach { forbidden ->
            assertFalse("Alternative price facts must not own $forbidden", source.contains(forbidden))
        }
    }

    private fun identities(): StapleWatchAlternativeStoreIdentityFacts =
        identities(emptyList())

    private fun identities(storeKey: ShoppingStoreKey): StapleWatchAlternativeStoreIdentityFacts =
        identities(listOf(storeKey))

    private fun identities(
        firstStoreKey: ShoppingStoreKey,
        secondStoreKey: ShoppingStoreKey
    ): StapleWatchAlternativeStoreIdentityFacts =
        identities(listOf(firstStoreKey, secondStoreKey))

    private fun identities(
        storeKeys: Collection<ShoppingStoreKey>
    ): StapleWatchAlternativeStoreIdentityFacts =
        StapleWatchAlternativeStoreIdentityFacts.fromUnordered(
            intent = intent,
            alternativeStoreKeys = storeKeys
        )

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

    private fun expectIllegalArgument(block: () -> Unit) {
        try {
            block()
            fail("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // Expected fail-closed boundary.
        }
    }

    private fun source(fileName: String): File {
        val workingDirectory =
            requireNotNull(System.getProperty("user.dir")) { "Missing user.dir for source boundary test" }
        return File(workingDirectory, "src/main/java/com/valuepilot/app/$fileName").also {
            assertTrue("Missing source $fileName at ${it.absolutePath}", it.isFile)
        }
    }
}
