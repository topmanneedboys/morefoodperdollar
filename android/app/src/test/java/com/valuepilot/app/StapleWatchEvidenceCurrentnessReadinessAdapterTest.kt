package com.valuepilot.app

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

class StapleWatchEvidenceCurrentnessReadinessAdapterTest {

    private val fixture = StapleWatchProductionPriceTestFixture()
    private val milk = ShoppingItemKey("milk")
    private val eggs = ShoppingItemKey("eggs")
    private val usual = ShoppingStoreKey("usual")
    private val east = ShoppingStoreKey("east")
    private val intent =
        StapleWatchFactCheckIntent(
            request = ShoppingRequest(listOf(milk, eggs)),
            usualStoreKey = usual
        )

    @Test
    fun matchingFactsResolveOnlyEvidenceCurrentnessRequirement() {
        val facts = currentnessFacts(intent, listOf(east))
        val updated =
            StapleWatchEvidenceCurrentnessReadinessAdapter.apply(
                readiness = StapleWatchFactResolutionReadiness.initial(intent),
                facts = facts
            )

        assertSame(intent, updated.intent)
        assertEquals(
            listOf(
                StapleWatchFactResolutionRequirement.USUAL_STORE_BASKET_PRICE_EVIDENCE,
                StapleWatchFactResolutionRequirement.ALTERNATIVE_STORE_CANDIDATE_IDENTITIES,
                StapleWatchFactResolutionRequirement.ALTERNATIVE_STORE_BASKET_PRICE_EVIDENCE,
                StapleWatchFactResolutionRequirement.ALTERNATIVE_ADDITIONAL_TRAVEL_FACTS
            ),
            updated.unresolvedRequirements
        )
        assertEquals(
            listOf(StapleWatchFactResolutionRequirement.EVIDENCE_CURRENTNESS_METADATA),
            updated.resolvedRequirements
        )
        assertFalse(updated.allRequirementsReportedResolved)
    }

    @Test
    fun explicitNoAlternativesAndUnboundEvidenceStillReportCurrentnessRequirementResolved() {
        val facts = currentnessFacts(intent, emptyList())

        assertTrue(facts.alternatives.isEmpty())
        assertTrue(
            facts.usualStore.itemCurrentness.all { item ->
                item.status == StapleWatchEvidenceCurrentnessStatus.NO_BOUND_PRODUCTION_EVIDENCE &&
                    item.freshness == null
            }
        )

        val updated =
            StapleWatchEvidenceCurrentnessReadinessAdapter.apply(
                readiness = StapleWatchFactResolutionReadiness.initial(intent),
                facts = facts
            )

        assertFalse(
            StapleWatchFactResolutionRequirement.EVIDENCE_CURRENTNESS_METADATA in
                updated.unresolvedRequirements
        )
        assertTrue(
            StapleWatchFactResolutionRequirement.EVIDENCE_CURRENTNESS_METADATA in
                updated.resolvedRequirements
        )
        assertTrue(
            StapleWatchFactResolutionRequirement.USUAL_STORE_BASKET_PRICE_EVIDENCE in
                updated.unresolvedRequirements
        )
        assertTrue(
            StapleWatchFactResolutionRequirement.ALTERNATIVE_STORE_BASKET_PRICE_EVIDENCE in
                updated.unresolvedRequirements
        )
    }

    @Test
    fun existingProgressIsPreservedAndRepeatedApplicationIsIdempotent() {
        val readiness =
            StapleWatchFactResolutionReadiness.fromUnresolved(
                intent = intent,
                unresolvedRequirements =
                    setOf(
                        StapleWatchFactResolutionRequirement.ALTERNATIVE_STORE_CANDIDATE_IDENTITIES,
                        StapleWatchFactResolutionRequirement.ALTERNATIVE_ADDITIONAL_TRAVEL_FACTS,
                        StapleWatchFactResolutionRequirement.EVIDENCE_CURRENTNESS_METADATA
                    )
            )
        val facts = currentnessFacts(intent, listOf(east))

        val updated = StapleWatchEvidenceCurrentnessReadinessAdapter.apply(readiness, facts)
        val repeated = StapleWatchEvidenceCurrentnessReadinessAdapter.apply(updated, facts)

        assertEquals(
            listOf(
                StapleWatchFactResolutionRequirement.ALTERNATIVE_STORE_CANDIDATE_IDENTITIES,
                StapleWatchFactResolutionRequirement.ALTERNATIVE_ADDITIONAL_TRAVEL_FACTS
            ),
            updated.unresolvedRequirements
        )
        assertEquals(
            listOf(
                StapleWatchFactResolutionRequirement.USUAL_STORE_BASKET_PRICE_EVIDENCE,
                StapleWatchFactResolutionRequirement.ALTERNATIVE_STORE_BASKET_PRICE_EVIDENCE,
                StapleWatchFactResolutionRequirement.EVIDENCE_CURRENTNESS_METADATA
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
        val otherFacts = currentnessFacts(otherIntent, emptyList())

        try {
            StapleWatchEvidenceCurrentnessReadinessAdapter.apply(
                readiness = StapleWatchFactResolutionReadiness.initial(intent),
                facts = otherFacts
            )
            fail("Currentness facts for a different fact-check intent must be rejected")
        } catch (_: IllegalArgumentException) {
            // Expected fail-closed readiness boundary.
        }
    }

    @Test
    fun readinessAdapterDoesNotInspectCurrentnessPayloadOrOwnEarlierOrLaterAuthority() {
        val source = source("StapleWatchEvidenceCurrentnessReadinessAdapter.kt").readText()

        assertTrue(source.contains("StapleWatchFactResolutionReadiness"))
        assertTrue(source.contains("StapleWatchEvidenceCurrentnessFacts"))
        assertTrue(source.contains("facts.intent"))
        assertTrue(source.contains("facts.resolvedRequirement"))

        listOf(
            "facts.usualStore",
            "facts.alternatives",
            "facts.usualStorePriceFacts",
            "facts.alternativeStorePriceFacts",
            "itemCurrentness",
            ".freshness",
            "EvidenceFreshness",
            "StapleWatchEvidenceCurrentnessStatus",
            "StapleWatchUsualStoreBasketPriceFacts",
            "StapleWatchAlternativeStoreBasketPriceFacts",
            "PracticalShoppingProductionCandidateBridge",
            "ProductionCurrentPriceEligibilityRequest",
            "EvidenceAcceptancePolicy",
            "ProductionDatasetLifecycleRegistry",
            "ProductionDatasetDispositionRegistry",
            "EVIDENCE_CURRENTNESS_METADATA",
            "ShoppingTravel",
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

    private fun currentnessFacts(
        targetIntent: StapleWatchFactCheckIntent,
        alternativeStoreKeys: Collection<ShoppingStoreKey>
    ): StapleWatchEvidenceCurrentnessFacts {
        val registries = fixture.registries(emptyList())
        val usualPrices =
            StapleWatchUsualStoreBasketPriceFacts.resolve(
                intent = targetIntent,
                store = store(targetIntent.usualStoreKey),
                priceBindings = emptyList(),
                priceRequests = emptyList(),
                lifecycleRegistry = registries.lifecycle,
                dispositionRegistry = registries.disposition,
                evaluatedAtEpochMillis = fixture.evaluatedAtEpochMillis,
                acceptancePolicy = fixture.acceptancePolicy
            )
        val identityFacts =
            StapleWatchAlternativeStoreIdentityFacts.fromUnordered(
                intent = targetIntent,
                alternativeStoreKeys = alternativeStoreKeys
            )
        val alternativePrices =
            StapleWatchAlternativeStoreBasketPriceFacts.resolve(
                identityFacts = identityFacts,
                stores = identityFacts.alternativeStoreKeys.map(::store),
                priceBindings = emptyList(),
                priceRequests = emptyList(),
                lifecycleRegistry = registries.lifecycle,
                dispositionRegistry = registries.disposition,
                evaluatedAtEpochMillis = fixture.evaluatedAtEpochMillis,
                acceptancePolicy = fixture.acceptancePolicy
            )

        return StapleWatchEvidenceCurrentnessFacts.resolve(
            usualStorePriceFacts = usualPrices,
            alternativeStorePriceFacts = alternativePrices,
            lifecycleRegistry = registries.lifecycle,
            dispositionRegistry = registries.disposition,
            evaluatedAtEpochMillis = fixture.evaluatedAtEpochMillis,
            acceptancePolicy = fixture.acceptancePolicy
        )
    }

    private fun store(storeKey: ShoppingStoreKey): PracticalShoppingProductionPriceStoreScope =
        PracticalShoppingProductionPriceStoreScope(
            storeKey = storeKey,
            merchantKey = "merchant-${storeKey.value}",
            locationKey = "location-${storeKey.value}",
            commerceChannelKey = "IN_STORE"
        )

    private fun source(fileName: String): File {
        val workingDirectory =
            requireNotNull(System.getProperty("user.dir")) { "Missing user.dir for source boundary test" }
        return File(workingDirectory, "src/main/java/com/valuepilot/app/$fileName").also {
            assertTrue("Missing source $fileName at ${it.absolutePath}", it.isFile)
        }
    }
}
