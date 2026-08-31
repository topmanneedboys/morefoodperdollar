package com.valuepilot.app

import com.valuepilot.core.PracticalShoppingProductionPriceBinding
import com.valuepilot.core.PracticalShoppingProductionPriceStoreScope
import com.valuepilot.core.PracticalShoppingStoreIdentityScope
import com.valuepilot.core.ShoppingItemKey
import com.valuepilot.core.ShoppingRequest
import com.valuepilot.core.ShoppingStoreKey
import com.valuepilot.core.ShoppingTravel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class StapleWatchSavedAlternativeStoreDisplayMetadataAdapterTest {

    private val fixture = StapleWatchProductionPriceTestFixture()
    private val milk = ShoppingItemKey("milk")
    private val eggs = ShoppingItemKey("eggs")
    private val usual = ShoppingStoreKey("opaque-metadata-usual-111111")
    private val alternativeA = ShoppingStoreKey("opaque-metadata-alt-a-222222")
    private val alternativeB = ShoppingStoreKey("opaque-metadata-alt-b-333333")
    private val intent =
        StapleWatchFactCheckIntent(
            request = ShoppingRequest(listOf(milk, eggs)),
            usualStoreKey = usual
        )

    @Test
    fun matchingSavedAlternativeScopesEmitNamesInEvidenceStoreOrderOnly() {
        val preconditions = preconditions(listOf(alternativeB, alternativeA), "ordered")
        val snapshot =
            snapshot(
                stores = listOf(usual, alternativeB, alternativeA),
                names =
                    mapOf(
                        usual to "Usual Market",
                        alternativeB to "Beta Market",
                        alternativeA to "Alpha Market"
                    )
            )

        val metadata =
            StapleWatchSavedAlternativeStoreDisplayMetadataAdapter.adapt(
                snapshot = snapshot,
                preconditions = preconditions
            )

        assertEquals(
            listOf(alternativeA, alternativeB),
            metadata.entries.map { entry -> entry.storeKey }
        )
        assertEquals(
            listOf("Alpha Market", "Beta Market"),
            metadata.entries.map { entry -> entry.displayName }
        )
        assertFalse(metadata.entries.any { entry -> entry.storeKey == usual })
    }

    @Test
    fun sameStableKeyWithReconfirmedSavedScopeCannotRelabelOlderEvidence() {
        val preconditions = preconditions(listOf(alternativeA), "reconfirmed")
        val reconfirmedScope =
            PracticalShoppingStoreIdentityScope(
                merchantKey = "replacement-merchant-${alternativeA.value}",
                locationKey = locationKey(alternativeA),
                commerceChannelKey = "IN_STORE"
            )
        val snapshot =
            snapshot(
                stores = listOf(alternativeA),
                names = mapOf(alternativeA to "Replacement Market"),
                scopeOverrides = mapOf(alternativeA to reconfirmedScope)
            )

        val metadata =
            StapleWatchSavedAlternativeStoreDisplayMetadataAdapter.adapt(
                snapshot = snapshot,
                preconditions = preconditions
            )

        assertTrue(metadata.entries.isEmpty())
    }

    @Test
    fun missingSavedNameOrUnsavedAlternativeStaysOmitted() {
        val preconditions = preconditions(listOf(alternativeA, alternativeB), "partial")
        val snapshot =
            snapshot(
                stores = listOf(alternativeA),
                names = emptyMap()
            )

        val metadata =
            StapleWatchSavedAlternativeStoreDisplayMetadataAdapter.adapt(
                snapshot = snapshot,
                preconditions = preconditions
            )

        assertTrue(metadata.entries.isEmpty())
    }

    @Test
    fun adapterOwnsPresentationBindingOnly() {
        val source = source("StapleWatchSavedAlternativeStoreDisplayMetadataAdapter.kt").readText()

        assertTrue(source.contains("snapshot.exactState.storeFor(scope.storeKey)"))
        assertTrue(source.contains("savedScope.merchantKey == evidenceScope.merchantKey"))
        assertTrue(source.contains("savedScope.locationKey == evidenceScope.locationKey"))
        assertTrue(source.contains("savedScope.commerceChannelKey == evidenceScope.commerceChannelKey"))
        assertTrue(source.contains("snapshot.displayMetadata.storeDisplayNames[scope.storeKey]"))

        listOf(
            "StapleWatchPolicy",
            "StapleWatchForegroundEvaluationCoordinator",
            "StapleWatchEconomicDecisionCoordinator",
            "ProductionCurrentPrice",
            "PracticalShoppingProductionPriceBinding",
            "EvidenceProvider",
            "OpenPrices",
            "OpenStreetMap",
            "OpenFoodFacts",
            "System.currentTimeMillis",
            "SharedPreferences",
            "WorkManager",
            "NotificationManager",
            "renderer.render",
            "StapleWatchSurfacePresenter",
            "MainActivity",
            "android."
        ).forEach { forbidden ->
            assertFalse("Saved Watch metadata adapter must not own $forbidden", source.contains(forbidden))
        }
    }

    private fun snapshot(
        stores: List<ShoppingStoreKey>,
        names: Map<ShoppingStoreKey, String>,
        scopeOverrides: Map<ShoppingStoreKey, PracticalShoppingStoreIdentityScope> = emptyMap()
    ): PracticalShoppingSavedValidatedSnapshot {
        val state =
            PracticalShoppingSavedExactPreferenceState(
                productPreferences = emptyList(),
                storePreferences =
                    stores.map { storeKey ->
                        PracticalShoppingSavedExactStorePreference(
                            storeKey = storeKey,
                            scope = scopeOverrides[storeKey] ?: identityScope(storeKey)
                        )
                    }
            )
        return PracticalShoppingSavedValidatedSnapshot(
            exactState = state,
            displayMetadata =
                PracticalShoppingSavedExactPreferenceDisplayMetadata(
                    storeDisplayNames = names
                )
        )
    }

    private fun preconditions(
        alternatives: List<ShoppingStoreKey>,
        prefix: String
    ): StapleWatchEconomicEvidencePreconditions {
        val identityFacts =
            StapleWatchAlternativeStoreIdentityFacts.fromUnordered(
                intent = intent,
                alternativeStoreKeys = alternatives
            )
        val usualCases = priceCases(usual, prefix, 1_000L, 1_100L)
        val alternativeCases =
            identityFacts.alternativeStoreKeys.associateWith { storeKey ->
                priceCases(storeKey, prefix, 400L, 500L)
            }
        val allCases =
            usualCases + alternativeCases.values.flatMap { cases -> cases }
        val registries = fixture.registries(allCases)

        val usualStorePriceFacts =
            StapleWatchUsualStoreBasketPriceFacts.resolve(
                intent = intent,
                store = productionScope(usual),
                priceBindings = bindings(usual, usualCases),
                priceRequests = usualCases.map { priceCase -> priceCase.request },
                lifecycleRegistry = registries.lifecycle,
                dispositionRegistry = registries.disposition,
                evaluatedAtEpochMillis = fixture.evaluatedAtEpochMillis,
                acceptancePolicy = fixture.acceptancePolicy
            )
        val alternativeStorePriceFacts =
            StapleWatchAlternativeStoreBasketPriceFacts.resolve(
                identityFacts = identityFacts,
                stores =
                    identityFacts.alternativeStoreKeys.reversed().map(::productionScope),
                priceBindings =
                    identityFacts.alternativeStoreKeys.reversed().flatMap { storeKey ->
                        bindings(storeKey, requireNotNull(alternativeCases[storeKey]))
                    },
                priceRequests =
                    identityFacts.alternativeStoreKeys.reversed().flatMap { storeKey ->
                        requireNotNull(alternativeCases[storeKey]).map { priceCase -> priceCase.request }
                    },
                lifecycleRegistry = registries.lifecycle,
                dispositionRegistry = registries.disposition,
                evaluatedAtEpochMillis = fixture.evaluatedAtEpochMillis,
                acceptancePolicy = fixture.acceptancePolicy
            )
        val additionalTravelFacts =
            StapleWatchAlternativeAdditionalTravelFacts.fromUnordered(
                identityFacts = identityFacts,
                additionalTravelByStore =
                    identityFacts.alternativeStoreKeys.associateWith {
                        ShoppingTravel(
                            distanceMetres = 500L,
                            travelTimeSeconds = 120L
                        )
                    }
            )
        val currentnessFacts =
            StapleWatchEvidenceCurrentnessFacts.resolve(
                usualStorePriceFacts = usualStorePriceFacts,
                alternativeStorePriceFacts = alternativeStorePriceFacts,
                lifecycleRegistry = registries.lifecycle,
                dispositionRegistry = registries.disposition,
                evaluatedAtEpochMillis = fixture.evaluatedAtEpochMillis,
                acceptancePolicy = fixture.acceptancePolicy
            )

        return StapleWatchEconomicEvidencePreconditions.evaluate(
            identityFacts = identityFacts,
            usualStorePriceFacts = usualStorePriceFacts,
            alternativeStorePriceFacts = alternativeStorePriceFacts,
            additionalTravelFacts = additionalTravelFacts,
            currentnessFacts = currentnessFacts
        )
    }

    private fun priceCases(
        storeKey: ShoppingStoreKey,
        prefix: String,
        milkMinor: Long,
        eggsMinor: Long
    ): List<StapleWatchProductionPriceTestFixture.PriceCase> =
        listOf(
            priceCase(storeKey, milk, "$prefix-${storeKey.value}-milk", milkMinor),
            priceCase(storeKey, eggs, "$prefix-${storeKey.value}-eggs", eggsMinor)
        )

    private fun priceCase(
        storeKey: ShoppingStoreKey,
        itemKey: ShoppingItemKey,
        id: String,
        priceMinor: Long
    ): StapleWatchProductionPriceTestFixture.PriceCase =
        fixture.case(
            requestId = id,
            providerItemId = "${storeKey.value}-${itemKey.value}-$id",
            merchantKey = merchantKey(storeKey),
            locationKey = locationKey(storeKey),
            priceMinor = priceMinor,
            observedAtEpochMillis = 4_500L
        )

    private fun bindings(
        storeKey: ShoppingStoreKey,
        cases: List<StapleWatchProductionPriceTestFixture.PriceCase>
    ): List<PracticalShoppingProductionPriceBinding> =
        listOf(milk, eggs).zip(cases).map { (itemKey, priceCase) ->
            PracticalShoppingProductionPriceBinding(
                itemKey = itemKey,
                productKey = priceCase.productKey,
                storeKey = storeKey,
                currentPriceRequestId = priceCase.request.requestId
            )
        }

    private fun identityScope(storeKey: ShoppingStoreKey): PracticalShoppingStoreIdentityScope =
        PracticalShoppingStoreIdentityScope(
            merchantKey = merchantKey(storeKey),
            locationKey = locationKey(storeKey),
            commerceChannelKey = "IN_STORE"
        )

    private fun productionScope(storeKey: ShoppingStoreKey): PracticalShoppingProductionPriceStoreScope =
        PracticalShoppingProductionPriceStoreScope(
            storeKey = storeKey,
            merchantKey = merchantKey(storeKey),
            locationKey = locationKey(storeKey),
            commerceChannelKey = "IN_STORE"
        )

    private fun merchantKey(storeKey: ShoppingStoreKey): String = "merchant-${storeKey.value}"

    private fun locationKey(storeKey: ShoppingStoreKey): String = "location-${storeKey.value}"

    private fun source(fileName: String): File {
        var directory = File(System.getProperty("user.dir") ?: error("user.dir unavailable"))
        repeat(8) {
            val candidate = File(directory, "app/src/main/java/com/valuepilot/app/$fileName")
            if (candidate.isFile) return candidate
            directory = directory.parentFile ?: return@repeat
        }
        error("Could not locate $fileName")
    }
}
