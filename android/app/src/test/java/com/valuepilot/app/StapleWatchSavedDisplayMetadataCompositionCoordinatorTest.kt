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
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class StapleWatchSavedDisplayMetadataCompositionCoordinatorTest {

    private val fixture = StapleWatchProductionPriceTestFixture()
    private val milk = ShoppingItemKey("milk")
    private val eggs = ShoppingItemKey("eggs")
    private val usual = ShoppingStoreKey("opaque-composition-usual-111111")
    private val alternative = ShoppingStoreKey("opaque-composition-alt-222222")
    private val intent =
        StapleWatchFactCheckIntent(
            request = ShoppingRequest(listOf(milk, eggs)),
            usualStoreKey = usual
        )

    @Test
    fun savedSnapshotBeforeEvidenceStillForwardsEvidenceBeforeDerivedMetadata() {
        val events = mutableListOf<String>()
        var forwardedPreconditions: StapleWatchEconomicEvidencePreconditions? = null
        var forwardedMetadata: StapleWatchStoreDisplayMetadata? = null
        val coordinator =
            StapleWatchSavedDisplayMetadataCompositionCoordinator(
                preconditionsObserver =
                    StapleWatchEconomicEvidencePreconditionsObserver { preconditions ->
                        events += "preconditions"
                        forwardedPreconditions = preconditions
                    },
                displayMetadataObserver =
                    StapleWatchStoreDisplayMetadataObserver { metadata ->
                        events += "metadata"
                        forwardedMetadata = metadata
                    }
            )
        val snapshot = snapshot("Alternative Market")
        val preconditions = preconditions("snapshot-first")

        coordinator.onSnapshot(snapshot)
        assertTrue(events.isEmpty())

        coordinator.onPreconditions(preconditions)

        assertEquals(listOf("preconditions", "metadata"), events)
        assertSame(preconditions, forwardedPreconditions)
        assertEquals(
            listOf(alternative to "Alternative Market"),
            requireNotNull(forwardedMetadata).entries.map { entry ->
                entry.storeKey to entry.displayName
            }
        )
    }

    @Test
    fun evidenceBeforeSavedSnapshotWaitsForValidatedMetadataWithoutReplayingEvidence() {
        val events = mutableListOf<String>()
        val coordinator =
            StapleWatchSavedDisplayMetadataCompositionCoordinator(
                preconditionsObserver =
                    StapleWatchEconomicEvidencePreconditionsObserver {
                        events += "preconditions"
                    },
                displayMetadataObserver =
                    StapleWatchStoreDisplayMetadataObserver {
                        events += "metadata"
                    }
            )
        val preconditions = preconditions("evidence-first")

        coordinator.onPreconditions(preconditions)
        assertEquals(listOf("preconditions"), events)

        coordinator.onSnapshot(snapshot("Alternative Market"))
        assertEquals(listOf("preconditions", "metadata"), events)
    }

    @Test
    fun laterSavedScopeReconfirmationRecomputesOldEvidenceMetadataFailClosed() {
        val emitted = mutableListOf<StapleWatchStoreDisplayMetadata>()
        val coordinator =
            StapleWatchSavedDisplayMetadataCompositionCoordinator(
                displayMetadataObserver =
                    StapleWatchStoreDisplayMetadataObserver { metadata ->
                        emitted += metadata
                    }
            )
        val preconditions = preconditions("reconfirmed")

        coordinator.onSnapshot(snapshot("Original Market"))
        coordinator.onPreconditions(preconditions)
        assertEquals("Original Market", emitted.single().entries.single().displayName)

        coordinator.onSnapshot(
            snapshot(
                displayName = "Replacement Market",
                scope =
                    PracticalShoppingStoreIdentityScope(
                        merchantKey = "replacement-merchant-${alternative.value}",
                        locationKey = locationKey(alternative),
                        commerceChannelKey = "IN_STORE"
                    )
            )
        )

        assertEquals(2, emitted.size)
        assertTrue(emitted.last().entries.isEmpty())
    }

    @Test
    fun exactDuplicateObjectsAreIdempotentAndCloseClearsFurtherComposition() {
        val events = mutableListOf<String>()
        val coordinator =
            StapleWatchSavedDisplayMetadataCompositionCoordinator(
                preconditionsObserver =
                    StapleWatchEconomicEvidencePreconditionsObserver {
                        events += "preconditions"
                    },
                displayMetadataObserver =
                    StapleWatchStoreDisplayMetadataObserver {
                        events += "metadata"
                    }
            )
        val snapshot = snapshot("Alternative Market")
        val preconditions = preconditions("idempotent")

        coordinator.onSnapshot(snapshot)
        coordinator.onSnapshot(snapshot)
        coordinator.onPreconditions(preconditions)
        coordinator.onPreconditions(preconditions)
        assertEquals(listOf("preconditions", "metadata"), events)

        coordinator.close()
        assertTrue(coordinator.isClosed())

        coordinator.onSnapshot(snapshot("After Close"))
        coordinator.onPreconditions(preconditions("after-close"))
        assertEquals(listOf("preconditions", "metadata"), events)
    }

    @Test
    fun coordinatorOwnsPairingAndOrderingOnly() {
        val source = source("StapleWatchSavedDisplayMetadataCompositionCoordinator.kt").readText()
        val preconditionsPath =
            source.substringAfter(
                "override fun onPreconditions(preconditions: StapleWatchEconomicEvidencePreconditions)"
            ).substringBefore("fun isClosed()")

        assertTrue(preconditionsPath.contains("preconditionsObserver.onPreconditions(preconditions)"))
        assertTrue(preconditionsPath.contains("emitMetadata(snapshot, preconditions)"))
        assertTrue(
            preconditionsPath.indexOf("preconditionsObserver.onPreconditions(preconditions)") <
                preconditionsPath.indexOf("emitMetadata(snapshot, preconditions)")
        )
        assertTrue(source.contains("StapleWatchSavedAlternativeStoreDisplayMetadataAdapter.adapt("))

        listOf(
            "StapleWatchPolicy",
            "StapleWatchForegroundEvaluationCoordinator",
            "StapleWatchEconomicDecisionCoordinator",
            "ProductionCurrentPrice",
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
            assertFalse("Saved metadata composition must not own $forbidden", source.contains(forbidden))
        }
    }

    private fun snapshot(
        displayName: String,
        scope: PracticalShoppingStoreIdentityScope = identityScope(alternative)
    ): PracticalShoppingSavedValidatedSnapshot =
        PracticalShoppingSavedValidatedSnapshot(
            exactState =
                PracticalShoppingSavedExactPreferenceState(
                    productPreferences = emptyList(),
                    storePreferences =
                        listOf(
                            PracticalShoppingSavedExactStorePreference(
                                storeKey = alternative,
                                scope = scope
                            )
                        )
                ),
            displayMetadata =
                PracticalShoppingSavedExactPreferenceDisplayMetadata(
                    storeDisplayNames = mapOf(alternative to displayName)
                )
        )

    private fun preconditions(prefix: String): StapleWatchEconomicEvidencePreconditions {
        val identityFacts =
            StapleWatchAlternativeStoreIdentityFacts.fromUnordered(
                intent = intent,
                alternativeStoreKeys = listOf(alternative)
            )
        val usualMilk = priceCase(usual, milk, "$prefix-usual-milk", 1_000L)
        val usualEggs = priceCase(usual, eggs, "$prefix-usual-eggs", 1_100L)
        val alternativeMilk = priceCase(alternative, milk, "$prefix-alt-milk", 400L)
        val alternativeEggs = priceCase(alternative, eggs, "$prefix-alt-eggs", 500L)
        val allCases = listOf(usualMilk, usualEggs, alternativeMilk, alternativeEggs)
        val registries = fixture.registries(allCases)

        val usualStorePriceFacts =
            StapleWatchUsualStoreBasketPriceFacts.resolve(
                intent = intent,
                store = productionScope(usual),
                priceBindings =
                    listOf(
                        binding(usual, milk, usualMilk),
                        binding(usual, eggs, usualEggs)
                    ),
                priceRequests = listOf(usualMilk.request, usualEggs.request),
                lifecycleRegistry = registries.lifecycle,
                dispositionRegistry = registries.disposition,
                evaluatedAtEpochMillis = fixture.evaluatedAtEpochMillis,
                acceptancePolicy = fixture.acceptancePolicy
            )
        val alternativeStorePriceFacts =
            StapleWatchAlternativeStoreBasketPriceFacts.resolve(
                identityFacts = identityFacts,
                stores = listOf(productionScope(alternative)),
                priceBindings =
                    listOf(
                        binding(alternative, milk, alternativeMilk),
                        binding(alternative, eggs, alternativeEggs)
                    ),
                priceRequests = listOf(alternativeMilk.request, alternativeEggs.request),
                lifecycleRegistry = registries.lifecycle,
                dispositionRegistry = registries.disposition,
                evaluatedAtEpochMillis = fixture.evaluatedAtEpochMillis,
                acceptancePolicy = fixture.acceptancePolicy
            )
        val additionalTravelFacts =
            StapleWatchAlternativeAdditionalTravelFacts.fromUnordered(
                identityFacts = identityFacts,
                additionalTravelByStore =
                    mapOf(
                        alternative to
                            ShoppingTravel(
                                distanceMetres = 500L,
                                travelTimeSeconds = 120L
                            )
                    )
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

    private fun binding(
        storeKey: ShoppingStoreKey,
        itemKey: ShoppingItemKey,
        priceCase: StapleWatchProductionPriceTestFixture.PriceCase
    ): PracticalShoppingProductionPriceBinding =
        PracticalShoppingProductionPriceBinding(
            itemKey = itemKey,
            productKey = priceCase.productKey,
            storeKey = storeKey,
            currentPriceRequestId = priceCase.request.requestId
        )

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
