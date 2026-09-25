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

class StapleWatchUsualStoreBasketPriceFactsTest {

    private val fixture = StapleWatchProductionPriceTestFixture()
    private val milk = ShoppingItemKey("milk")
    private val eggs = ShoppingItemKey("eggs")
    private val usualStore = ShoppingStoreKey("usual")
    private val intent =
        StapleWatchFactCheckIntent(
            request = ShoppingRequest(listOf(milk, eggs)),
            usualStoreKey = usualStore
        )
    private val store =
        PracticalShoppingProductionPriceStoreScope(
            storeKey = usualStore,
            merchantKey = "merchant-a",
            locationKey = "location-a",
            commerceChannelKey = "IN_STORE"
        )

    @Test
    fun usableAndMissingPricesExactlyCoverRequestOrder() {
        val eggsPrice =
            fixture.case(
                requestId = "eggs-price",
                providerItemId = "eggs-product",
                priceMinor = 425L
            )
        val registries = fixture.registries(listOf(eggsPrice))

        val facts =
            StapleWatchUsualStoreBasketPriceFacts.resolve(
                intent = intent,
                store = store,
                priceBindings = listOf(binding(eggs, eggsPrice)),
                priceRequests = listOf(eggsPrice.request),
                lifecycleRegistry = registries.lifecycle,
                dispositionRegistry = registries.disposition,
                evaluatedAtEpochMillis = fixture.evaluatedAtEpochMillis,
                acceptancePolicy = fixture.acceptancePolicy
            )

        assertEquals(intent, facts.intent)
        assertEquals(
            StapleWatchFactResolutionRequirement.USUAL_STORE_BASKET_PRICE_EVIDENCE,
            facts.resolvedRequirement
        )
        assertEquals(listOf(milk, eggs), facts.itemPrices.map { it.itemKey })
        assertEquals(
            StapleWatchBasketItemPriceState.NO_BOUND_PRODUCTION_PRICE,
            facts.itemPrices[0].state
        )
        assertNull(facts.itemPrices[0].exactPrice)
        assertEquals(
            StapleWatchBasketItemPriceState.USABLE_EXACT_PRICE,
            facts.itemPrices[1].state
        )
        assertEquals(Money(425L, "CAD"), facts.itemPrices[1].exactPrice)
    }

    @Test
    fun productionRejectedPriceRemainsBlockedWithoutMoney() {
        val milkPrice =
            fixture.case(
                requestId = "milk-price",
                providerItemId = "milk-product",
                priceMinor = 550L,
                availability = AvailabilityState.OUT_OF_STOCK
            )
        val registries = fixture.registries(listOf(milkPrice))

        val facts =
            StapleWatchUsualStoreBasketPriceFacts.resolve(
                intent = intent,
                store = store,
                priceBindings = listOf(binding(milk, milkPrice)),
                priceRequests = listOf(milkPrice.request),
                lifecycleRegistry = registries.lifecycle,
                dispositionRegistry = registries.disposition,
                evaluatedAtEpochMillis = fixture.evaluatedAtEpochMillis,
                acceptancePolicy = fixture.acceptancePolicy
            )

        assertEquals(listOf(milk, eggs), facts.itemPrices.map { it.itemKey })
        assertEquals(
            StapleWatchBasketItemPriceState.PRODUCTION_PRICE_BLOCKED,
            facts.itemPrices[0].state
        )
        assertNull(facts.itemPrices[0].exactPrice)
        assertEquals(
            StapleWatchBasketItemPriceState.NO_BOUND_PRODUCTION_PRICE,
            facts.itemPrices[1].state
        )
        assertNull(facts.itemPrices[1].exactPrice)
    }

    @Test
    fun storeScopeForDifferentLogicalStoreFailsClosed() {
        val registries = fixture.registries(emptyList())

        expectIllegalArgument {
            StapleWatchUsualStoreBasketPriceFacts.resolve(
                intent = intent,
                store = store.copy(storeKey = ShoppingStoreKey("other")),
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
    fun foreignStoreBindingFailsClosed() {
        val milkPrice = fixture.case("milk-price", "milk-product")
        val registries = fixture.registries(listOf(milkPrice))

        expectIllegalArgument {
            StapleWatchUsualStoreBasketPriceFacts.resolve(
                intent = intent,
                store = store,
                priceBindings =
                    listOf(
                        PracticalShoppingProductionPriceBinding(
                            itemKey = milk,
                            productKey = milkPrice.productKey,
                            storeKey = ShoppingStoreKey("other"),
                            currentPriceRequestId = milkPrice.request.requestId
                        )
                    ),
                priceRequests = listOf(milkPrice.request),
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
        val breadPrice = fixture.case("bread-price", "bread-product")
        val registries = fixture.registries(listOf(breadPrice))

        expectIllegalArgument {
            StapleWatchUsualStoreBasketPriceFacts.resolve(
                intent = intent,
                store = store,
                priceBindings = listOf(binding(bread, breadPrice)),
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
        val milkPrice = fixture.case("milk-price", "milk-product")
        val registries = fixture.registries(listOf(milkPrice))

        expectIllegalArgument {
            StapleWatchUsualStoreBasketPriceFacts.resolve(
                intent = intent,
                store = store,
                priceBindings = emptyList(),
                priceRequests = listOf(milkPrice.request),
                lifecycleRegistry = registries.lifecycle,
                dispositionRegistry = registries.disposition,
                evaluatedAtEpochMillis = fixture.evaluatedAtEpochMillis,
                acceptancePolicy = fixture.acceptancePolicy
            )
        }
    }

    @Test
    fun authoritativeFactsHavePrivateConstructionAndNoDataClassCopyEscape() {
        val constructors = StapleWatchUsualStoreBasketPriceFacts::class.java.declaredConstructors
        assertTrue(constructors.isNotEmpty())
        assertTrue(
            constructors
                .filterNot { constructor -> constructor.isSynthetic }
                .all { constructor -> Modifier.isPrivate(constructor.modifiers) }
        )

        val source = source("StapleWatchUsualStoreBasketPriceFacts.kt").readText()
        assertTrue(
            source.contains("class StapleWatchUsualStoreBasketPriceFacts private constructor(")
        )
        assertFalse(source.contains("data class StapleWatchUsualStoreBasketPriceFacts"))
        assertFalse(
            StapleWatchUsualStoreBasketPriceFacts::class.java.methods.any { method ->
                method.name == "copy" || method.name.startsWith("copy$")
            }
        )
    }

    @Test
    fun priceFactBoundaryDoesNotOwnCurrentnessTravelAlternativesEconomicsOrDelivery() {
        val source = source("StapleWatchUsualStoreBasketPriceFacts.kt").readText()

        assertTrue(source.contains("PracticalShoppingProductionCandidateBridge.evaluatePrices"))
        assertTrue(source.contains("USUAL_STORE_BASKET_PRICE_EVIDENCE"))
        assertTrue(source.contains("Money"))

        listOf(
            "EvidenceFreshness",
            ".freshness",
            "ShoppingTravel",
            "ALTERNATIVE_STORE_BASKET_PRICE_EVIDENCE",
            "EVIDENCE_CURRENTNESS_METADATA",
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
            assertFalse("Usual-store price facts must not own $forbidden", source.contains(forbidden))
        }
    }

    private fun binding(
        itemKey: ShoppingItemKey,
        priceCase: StapleWatchProductionPriceTestFixture.PriceCase
    ): PracticalShoppingProductionPriceBinding =
        PracticalShoppingProductionPriceBinding(
            itemKey = itemKey,
            productKey = priceCase.productKey,
            storeKey = usualStore,
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
