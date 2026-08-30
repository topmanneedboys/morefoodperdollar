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

class StapleWatchUsualStoreBasketPriceReadinessAdapterTest {

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
    fun matchingFactsResolveOnlyUsualStorePriceRequirement() {
        val facts = factsWithEggsPrice()
        val updated =
            StapleWatchUsualStoreBasketPriceReadinessAdapter.apply(
                readiness = StapleWatchFactResolutionReadiness.initial(intent),
                facts = facts
            )

        assertSame(intent, updated.intent)
        assertEquals(
            listOf(
                StapleWatchFactResolutionRequirement.ALTERNATIVE_STORE_CANDIDATE_IDENTITIES,
                StapleWatchFactResolutionRequirement.ALTERNATIVE_STORE_BASKET_PRICE_EVIDENCE,
                StapleWatchFactResolutionRequirement.ALTERNATIVE_ADDITIONAL_TRAVEL_FACTS,
                StapleWatchFactResolutionRequirement.EVIDENCE_CURRENTNESS_METADATA
            ),
            updated.unresolvedRequirements
        )
        assertEquals(
            listOf(StapleWatchFactResolutionRequirement.USUAL_STORE_BASKET_PRICE_EVIDENCE),
            updated.resolvedRequirements
        )
        assertFalse(updated.allRequirementsReportedResolved)
    }

    @Test
    fun blockedAndMissingFactsStillAccountForUsualStorePriceRequirement() {
        val milkPrice =
            fixture.case(
                requestId = "blocked-milk-price",
                providerItemId = "blocked-milk-product",
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

        assertEquals(
            listOf(
                StapleWatchBasketItemPriceState.PRODUCTION_PRICE_BLOCKED,
                StapleWatchBasketItemPriceState.NO_BOUND_PRODUCTION_PRICE
            ),
            facts.itemPrices.map { item -> item.state }
        )

        val updated =
            StapleWatchUsualStoreBasketPriceReadinessAdapter.apply(
                StapleWatchFactResolutionReadiness.initial(intent),
                facts
            )

        assertFalse(
            StapleWatchFactResolutionRequirement.USUAL_STORE_BASKET_PRICE_EVIDENCE in
                updated.unresolvedRequirements
        )
        assertTrue(
            StapleWatchFactResolutionRequirement.USUAL_STORE_BASKET_PRICE_EVIDENCE in
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
                        StapleWatchFactResolutionRequirement.USUAL_STORE_BASKET_PRICE_EVIDENCE,
                        StapleWatchFactResolutionRequirement.ALTERNATIVE_ADDITIONAL_TRAVEL_FACTS,
                        StapleWatchFactResolutionRequirement.EVIDENCE_CURRENTNESS_METADATA
                    )
            )
        val facts = factsWithNoBindings(intent, store)

        val updated = StapleWatchUsualStoreBasketPriceReadinessAdapter.apply(readiness, facts)
        val repeated = StapleWatchUsualStoreBasketPriceReadinessAdapter.apply(updated, facts)

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
        val otherStore =
            PracticalShoppingProductionPriceStoreScope(
                storeKey = otherIntent.usualStoreKey,
                merchantKey = "merchant-b",
                locationKey = "location-b",
                commerceChannelKey = "IN_STORE"
            )
        val otherFacts = factsWithNoBindings(otherIntent, otherStore)

        try {
            StapleWatchUsualStoreBasketPriceReadinessAdapter.apply(
                readiness = StapleWatchFactResolutionReadiness.initial(intent),
                facts = otherFacts
            )
            fail("Facts for a different fact-check intent must be rejected")
        } catch (_: IllegalArgumentException) {
            // Expected fail-closed readiness boundary.
        }
    }

    @Test
    fun readinessAdapterDoesNotInspectPricePayloadOrOwnLaterAuthority() {
        val source = source("StapleWatchUsualStoreBasketPriceReadinessAdapter.kt").readText()

        assertTrue(source.contains("StapleWatchFactResolutionReadiness"))
        assertTrue(source.contains("StapleWatchUsualStoreBasketPriceFacts"))
        assertTrue(source.contains("facts.resolvedRequirement"))

        listOf(
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
            "ShoppingTravel",
            "StapleWatchAlternativeStoreIdentityFacts",
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

    private fun factsWithEggsPrice(): StapleWatchUsualStoreBasketPriceFacts {
        val eggsPrice =
            fixture.case(
                requestId = "readiness-eggs-price",
                providerItemId = "readiness-eggs-product",
                priceMinor = 425L
            )
        val registries = fixture.registries(listOf(eggsPrice))

        return StapleWatchUsualStoreBasketPriceFacts.resolve(
            intent = intent,
            store = store,
            priceBindings = listOf(binding(eggs, eggsPrice)),
            priceRequests = listOf(eggsPrice.request),
            lifecycleRegistry = registries.lifecycle,
            dispositionRegistry = registries.disposition,
            evaluatedAtEpochMillis = fixture.evaluatedAtEpochMillis,
            acceptancePolicy = fixture.acceptancePolicy
        )
    }

    private fun factsWithNoBindings(
        targetIntent: StapleWatchFactCheckIntent,
        targetStore: PracticalShoppingProductionPriceStoreScope
    ): StapleWatchUsualStoreBasketPriceFacts {
        val registries = fixture.registries(emptyList())
        return StapleWatchUsualStoreBasketPriceFacts.resolve(
            intent = targetIntent,
            store = targetStore,
            priceBindings = emptyList(),
            priceRequests = emptyList(),
            lifecycleRegistry = registries.lifecycle,
            dispositionRegistry = registries.disposition,
            evaluatedAtEpochMillis = fixture.evaluatedAtEpochMillis,
            acceptancePolicy = fixture.acceptancePolicy
        )
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

    private fun source(fileName: String): File {
        val workingDirectory =
            requireNotNull(System.getProperty("user.dir")) { "Missing user.dir for source boundary test" }
        return File(workingDirectory, "src/main/java/com/valuepilot/app/$fileName").also {
            assertTrue("Missing source $fileName at ${it.absolutePath}", it.isFile)
        }
    }
}
