package com.valuepilot.app

import com.valuepilot.core.AvailabilityState
import com.valuepilot.core.PracticalShoppingProductionPriceBinding
import com.valuepilot.core.PracticalShoppingProductionPriceStoreScope
import com.valuepilot.core.ShoppingItemKey
import com.valuepilot.core.ShoppingRequest
import com.valuepilot.core.ShoppingStoreKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

class StapleWatchAlternativeStoreBasketPriceReadinessAdapterTest {

    private val fixture = StapleWatchProductionPriceTestFixture()
    private val milk = ShoppingItemKey("milk")
    private val eggs = ShoppingItemKey("eggs")
    private val usualStore = ShoppingStoreKey("usual")
    private val east = ShoppingStoreKey("east")
    private val intent =
        StapleWatchFactCheckIntent(
            request = ShoppingRequest(listOf(milk, eggs)),
            usualStoreKey = usualStore
        )

    @Test
    fun matchingFactsResolveOnlyAlternativeStorePriceRequirement() {
        val facts = factsWithEggsPrice()
        val updated =
            StapleWatchAlternativeStoreBasketPriceReadinessAdapter.apply(
                readiness = StapleWatchFactResolutionReadiness.initial(intent),
                facts = facts
            )

        assertSame(intent, updated.intent)
        assertEquals(
            listOf(
                StapleWatchFactResolutionRequirement.USUAL_STORE_BASKET_PRICE_EVIDENCE,
                StapleWatchFactResolutionRequirement.ALTERNATIVE_STORE_CANDIDATE_IDENTITIES,
                StapleWatchFactResolutionRequirement.ALTERNATIVE_ADDITIONAL_TRAVEL_FACTS,
                StapleWatchFactResolutionRequirement.EVIDENCE_CURRENTNESS_METADATA
            ),
            updated.unresolvedRequirements
        )
        assertEquals(
            listOf(StapleWatchFactResolutionRequirement.ALTERNATIVE_STORE_BASKET_PRICE_EVIDENCE),
            updated.resolvedRequirements
        )
        assertFalse(updated.allRequirementsReportedResolved)
    }

    @Test
    fun blockedAndMissingFactsStillAccountForAlternativeStorePriceRequirement() {
        val blockedMilk =
            fixture.case(
                requestId = "alternative-blocked-milk-price",
                providerItemId = "alternative-blocked-milk-product",
                merchantKey = "merchant-east",
                locationKey = "location-east",
                availability = AvailabilityState.OUT_OF_STOCK
            )
        val registries = fixture.registries(listOf(blockedMilk))
        val facts =
            StapleWatchAlternativeStoreBasketPriceFacts.resolve(
                identityFacts = identities(intent, listOf(east)),
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
            facts.alternatives.single().itemPrices.map { item -> item.state }
        )

        val updated =
            StapleWatchAlternativeStoreBasketPriceReadinessAdapter.apply(
                StapleWatchFactResolutionReadiness.initial(intent),
                facts
            )

        assertFalse(
            StapleWatchFactResolutionRequirement.ALTERNATIVE_STORE_BASKET_PRICE_EVIDENCE in
                updated.unresolvedRequirements
        )
        assertTrue(
            StapleWatchFactResolutionRequirement.ALTERNATIVE_STORE_BASKET_PRICE_EVIDENCE in
                updated.resolvedRequirements
        )
    }

    @Test
    fun explicitNoAlternativeStoresStillAccountsForAlternativeStorePriceRequirement() {
        val facts = factsWithNoAlternatives(intent)
        assertTrue(facts.alternatives.isEmpty())

        val updated =
            StapleWatchAlternativeStoreBasketPriceReadinessAdapter.apply(
                StapleWatchFactResolutionReadiness.initial(intent),
                facts
            )

        assertFalse(
            StapleWatchFactResolutionRequirement.ALTERNATIVE_STORE_BASKET_PRICE_EVIDENCE in
                updated.unresolvedRequirements
        )
        assertTrue(
            StapleWatchFactResolutionRequirement.ALTERNATIVE_STORE_BASKET_PRICE_EVIDENCE in
                updated.resolvedRequirements
        )
    }

    @Test
    fun existingProgressIsPreservedAndRepeatedApplicationIsIdempotent() {
        val readiness =
            StapleWatchFactResolutionReadiness.fromUnresolved(
                intent = intent,
                unresolvedRequirements =
                    setOf(
                        StapleWatchFactResolutionRequirement.ALTERNATIVE_STORE_BASKET_PRICE_EVIDENCE,
                        StapleWatchFactResolutionRequirement.ALTERNATIVE_ADDITIONAL_TRAVEL_FACTS,
                        StapleWatchFactResolutionRequirement.EVIDENCE_CURRENTNESS_METADATA
                    )
            )
        val facts = factsWithNoBindings(intent, east)

        val updated = StapleWatchAlternativeStoreBasketPriceReadinessAdapter.apply(readiness, facts)
        val repeated = StapleWatchAlternativeStoreBasketPriceReadinessAdapter.apply(updated, facts)

        assertEquals(
            listOf(
                StapleWatchFactResolutionRequirement.ALTERNATIVE_ADDITIONAL_TRAVEL_FACTS,
                StapleWatchFactResolutionRequirement.EVIDENCE_CURRENTNESS_METADATA
            ),
            updated.unresolvedRequirements
        )
        assertEquals(
            listOf(
                StapleWatchFactResolutionRequirement.USUAL_STORE_BASKET_PRICE_EVIDENCE,
                StapleWatchFactResolutionRequirement.ALTERNATIVE_STORE_CANDIDATE_IDENTITIES,
                StapleWatchFactResolutionRequirement.ALTERNATIVE_STORE_BASKET_PRICE_EVIDENCE
            ),
            updated.resolvedRequirements
        )
        assertSame(updated, repeated)
    }

    @Test
    fun factsForDifferentIntentFailClosed() {
        val otherIntent =
            StapleWatchFactCheckIntent(
                request = intent.request,
                usualStoreKey = ShoppingStoreKey("different-usual")
            )
        val otherFacts = factsWithNoAlternatives(otherIntent)

        try {
            StapleWatchAlternativeStoreBasketPriceReadinessAdapter.apply(
                readiness = StapleWatchFactResolutionReadiness.initial(intent),
                facts = otherFacts
            )
            fail("Facts for a different fact-check intent must be rejected")
        } catch (_: IllegalArgumentException) {
            // Expected fail-closed readiness boundary.
        }
    }

    @Test
    fun readinessAdapterDoesNotInspectAlternativePricePayloadOrOwnLaterAuthority() {
        val source = source("StapleWatchAlternativeStoreBasketPriceReadinessAdapter.kt").readText()

        assertTrue(source.contains("StapleWatchFactResolutionReadiness"))
        assertTrue(source.contains("StapleWatchAlternativeStoreBasketPriceFacts"))
        assertTrue(source.contains("facts.resolvedRequirement"))

        listOf(
            "alternatives",
            "identityFacts",
            "itemPrices",
            "exactPrice",
            "StapleWatchBasketItemPriceState",
            "Money",
            "PracticalShoppingProductionCandidateBridge",
            "ProductionCurrentPriceEligibilityRequest",
            "merchantKey",
            "locationKey",
            "commerceChannelKey",
            "EvidenceFreshness",
            "EVIDENCE_CURRENTNESS_METADATA",
            "ShoppingTravel",
            "StapleWatchAlternativeAdditionalTravelFacts",
            "StapleWatchPolicy",
            "StapleWatchEconomicEvaluator",
            "StapleWatchEconomicDecision",
            "SharedPreferences",
            "WorkManager",
            "NotificationManager",
            "android."
        ).forEach { forbidden ->
            assertFalse("Readiness adapter must not own $forbidden", source.contains(forbidden))
        }
    }

    private fun factsWithEggsPrice(): StapleWatchAlternativeStoreBasketPriceFacts {
        val eggsPrice =
            fixture.case(
                requestId = "alternative-readiness-eggs-price",
                providerItemId = "alternative-readiness-eggs-product",
                merchantKey = "merchant-east",
                locationKey = "location-east",
                priceMinor = 425L
            )
        val registries = fixture.registries(listOf(eggsPrice))

        return StapleWatchAlternativeStoreBasketPriceFacts.resolve(
            identityFacts = identities(intent, listOf(east)),
            stores = listOf(store(east)),
            priceBindings = listOf(binding(eggs, east, eggsPrice)),
            priceRequests = listOf(eggsPrice.request),
            lifecycleRegistry = registries.lifecycle,
            dispositionRegistry = registries.disposition,
            evaluatedAtEpochMillis = fixture.evaluatedAtEpochMillis,
            acceptancePolicy = fixture.acceptancePolicy
        )
    }

    private fun factsWithNoBindings(
        targetIntent: StapleWatchFactCheckIntent,
        alternativeStore: ShoppingStoreKey
    ): StapleWatchAlternativeStoreBasketPriceFacts {
        val registries = fixture.registries(emptyList())
        return StapleWatchAlternativeStoreBasketPriceFacts.resolve(
            identityFacts = identities(targetIntent, listOf(alternativeStore)),
            stores = listOf(store(alternativeStore)),
            priceBindings = emptyList(),
            priceRequests = emptyList(),
            lifecycleRegistry = registries.lifecycle,
            dispositionRegistry = registries.disposition,
            evaluatedAtEpochMillis = fixture.evaluatedAtEpochMillis,
            acceptancePolicy = fixture.acceptancePolicy
        )
    }

    private fun factsWithNoAlternatives(
        targetIntent: StapleWatchFactCheckIntent
    ): StapleWatchAlternativeStoreBasketPriceFacts {
        val registries = fixture.registries(emptyList())
        return StapleWatchAlternativeStoreBasketPriceFacts.resolve(
            identityFacts = identities(targetIntent, emptyList()),
            stores = emptyList(),
            priceBindings = emptyList(),
            priceRequests = emptyList(),
            lifecycleRegistry = registries.lifecycle,
            dispositionRegistry = registries.disposition,
            evaluatedAtEpochMillis = fixture.evaluatedAtEpochMillis,
            acceptancePolicy = fixture.acceptancePolicy
        )
    }

    private fun identities(
        targetIntent: StapleWatchFactCheckIntent,
        storeKeys: Collection<ShoppingStoreKey>
    ): StapleWatchAlternativeStoreIdentityFacts =
        StapleWatchAlternativeStoreIdentityFacts.fromUnordered(
            intent = targetIntent,
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

    private fun source(fileName: String): File {
        val workingDirectory =
            requireNotNull(System.getProperty("user.dir")) { "Missing user.dir for source boundary test" }
        return File(workingDirectory, "src/main/java/com/valuepilot/app/$fileName").also {
            assertTrue("Missing source $fileName at ${it.absolutePath}", it.isFile)
        }
    }
}
