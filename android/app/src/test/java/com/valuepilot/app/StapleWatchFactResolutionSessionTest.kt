package com.valuepilot.app

import com.valuepilot.core.PracticalShoppingProductionPriceBinding
import com.valuepilot.core.PracticalShoppingProductionPriceStoreScope
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
import java.io.File
import java.lang.reflect.Modifier

class StapleWatchFactResolutionSessionTest {

    private val fixture = StapleWatchProductionPriceTestFixture()
    private val milk = ShoppingItemKey("milk")
    private val eggs = ShoppingItemKey("eggs")
    private val usual = ShoppingStoreKey("opaque-usual-store-333333")
    private val alternative = ShoppingStoreKey("opaque-alt-store-444444")
    private val intent =
        StapleWatchFactCheckIntent(
            request = ShoppingRequest(listOf(milk, eggs)),
            usualStoreKey = usual
        )

    @Test
    fun startRetainsExactIntentAndReportsEveryCategoryUnresolved() {
        val session = StapleWatchFactResolutionSession.start(intent)

        assertSame(intent, session.intent)
        assertSame(intent, session.readiness.intent)
        assertEquals(intent.requirements, session.readiness.unresolvedRequirements)
        assertFalse(session.allFactCategoriesRetained)
        assertNull(session.economicPreconditionsOrNull())
    }

    @Test
    fun authoritativeFactsMayArriveOutOfOrderAndMintExactEconomicPreconditionsOnlyWhenComplete() {
        val facts = facts()
        var session = StapleWatchFactResolutionSession.start(intent)

        session = session.accept(facts.currentness)
        assertEquals(
            intent.requirements.filterNot {
                it == StapleWatchFactResolutionRequirement.EVIDENCE_CURRENTNESS_METADATA
            },
            session.readiness.unresolvedRequirements
        )
        assertNull(session.economicPreconditionsOrNull())

        session = session.accept(facts.travel)
        session = session.accept(facts.alternativePrices)
        session = session.accept(facts.usualPrices)
        assertFalse(session.allFactCategoriesRetained)
        assertNull(session.economicPreconditionsOrNull())

        session = session.accept(facts.identities)
        val preconditions = requireNotNull(session.economicPreconditionsOrNull())

        assertTrue(session.allFactCategoriesRetained)
        assertTrue(session.readiness.allRequirementsReportedResolved)
        assertTrue(session.readiness.unresolvedRequirements.isEmpty())
        assertSame(facts.identities, preconditions.identityFacts)
        assertSame(facts.usualPrices, preconditions.usualStorePriceFacts)
        assertSame(facts.alternativePrices, preconditions.alternativeStorePriceFacts)
        assertSame(facts.travel, preconditions.additionalTravelFacts)
        assertSame(facts.currentness, preconditions.currentnessFacts)
        assertTrue(preconditions.satisfied)
        assertEquals(listOf(alternative), preconditions.priceAndCurrentnessReadyAlternativeStoreKeys)
    }

    @Test
    fun reapplyingExactFactObjectIsIdempotentButDetachedReplacementFailsClosed() {
        val first =
            StapleWatchAlternativeStoreIdentityFacts.fromUnordered(
                intent = intent,
                alternativeStoreKeys = listOf(alternative)
            )
        val detachedEquivalent =
            StapleWatchAlternativeStoreIdentityFacts.fromUnordered(
                intent = intent,
                alternativeStoreKeys = listOf(alternative)
            )
        val accepted = StapleWatchFactResolutionSession.start(intent).accept(first)

        assertSame(accepted, accepted.accept(first))
        expectIllegalArgument {
            accepted.accept(detachedEquivalent)
        }
    }

    @Test
    fun dependentFactAcceptedFirstRejectsLaterDetachedUpstreamIdentity() {
        val facts = facts()
        val detachedEquivalent =
            StapleWatchAlternativeStoreIdentityFacts.fromUnordered(
                intent = intent,
                alternativeStoreKeys = listOf(alternative)
            )
        val session =
            StapleWatchFactResolutionSession.start(intent)
                .accept(facts.alternativePrices)

        expectIllegalArgument {
            session.accept(detachedEquivalent)
        }
    }

    @Test
    fun factForDifferentIntentFailsClosedWithoutChangingProgress() {
        val session = StapleWatchFactResolutionSession.start(intent)
        val otherIntent =
            StapleWatchFactCheckIntent(
                request = ShoppingRequest(listOf(milk, eggs)),
                usualStoreKey = ShoppingStoreKey("different-usual-store")
            )
        val foreignFacts =
            StapleWatchAlternativeStoreIdentityFacts.fromUnordered(
                intent = otherIntent,
                alternativeStoreKeys = listOf(alternative)
            )

        expectIllegalArgument {
            session.accept(foreignFacts)
        }
        assertEquals(intent.requirements, session.readiness.unresolvedRequirements)
    }

    @Test
    fun sessionOwnsOnlyFactHandoffReadinessAndPreconditionComposition() {
        val constructors =
            StapleWatchFactResolutionSession::class.java.declaredConstructors
                .filterNot { constructor -> constructor.isSynthetic }
        assertTrue(constructors.isNotEmpty())
        assertTrue(constructors.all { constructor -> Modifier.isPrivate(constructor.modifiers) })
        assertFalse(
            StapleWatchFactResolutionSession::class.java.methods.any { method ->
                method.name == "copy" || method.name.startsWith("copy$")
            }
        )

        val source = source("StapleWatchFactResolutionSession.kt").readText()
        assertTrue(source.contains("StapleWatchFactResolutionReadiness.initial(intent)"))
        assertTrue(source.contains("StapleWatchAlternativeStoreIdentityReadinessAdapter.apply"))
        assertTrue(source.contains("StapleWatchUsualStoreBasketPriceReadinessAdapter.apply"))
        assertTrue(source.contains("StapleWatchAlternativeStoreBasketPriceReadinessAdapter.apply"))
        assertTrue(source.contains("StapleWatchAlternativeAdditionalTravelReadinessAdapter.apply"))
        assertTrue(source.contains("StapleWatchEvidenceCurrentnessReadinessAdapter.apply"))
        assertTrue(source.contains("StapleWatchEconomicEvidencePreconditions.evaluate("))

        listOf(
            "PracticalShoppingProductionPriceBinding",
            "ProductionCurrentPrice",
            "EvidenceProvider",
            "OpenPrices",
            "OpenStreetMap",
            "OpenFoodFacts",
            "System.currentTimeMillis",
            "Math.addExact",
            "Math.subtractExact",
            ".minorUnits",
            "SharedPreferences",
            "WorkManager",
            "NotificationManager",
            "StapleWatchEconomicEvaluator",
            "StapleWatchEconomicDecisionCoordinator",
            "StapleWatchForegroundEvaluationCoordinator",
            "StapleWatchUiProjector",
            "StapleWatchSurfacePresenter",
            "renderer.render",
            "android."
        ).forEach { forbidden ->
            assertFalse("Fact-resolution session must not own $forbidden", source.contains(forbidden))
        }
    }

    private fun facts(): FactBundle {
        val identities =
            StapleWatchAlternativeStoreIdentityFacts.fromUnordered(
                intent = intent,
                alternativeStoreKeys = listOf(alternative)
            )
        val usualCases =
            mapOf(
                milk to priceCase(usual, milk, "session-usual-milk", 1_000L),
                eggs to priceCase(usual, eggs, "session-usual-eggs", 1_000L)
            )
        val alternativeCases =
            mapOf(
                milk to priceCase(alternative, milk, "session-alt-milk", 400L),
                eggs to priceCase(alternative, eggs, "session-alt-eggs", 500L)
            )
        val allCases = usualCases.values + alternativeCases.values
        val registries = fixture.registries(allCases)

        val usualBindings =
            intent.request.itemKeys.map { itemKey ->
                binding(usual, itemKey, requireNotNull(usualCases[itemKey]))
            }
        val usualPrices =
            StapleWatchUsualStoreBasketPriceFacts.resolve(
                intent = intent,
                store = storeScope(usual),
                priceBindings = usualBindings,
                priceRequests = usualBindings.map { binding ->
                    requireNotNull(usualCases[binding.itemKey]).request
                },
                lifecycleRegistry = registries.lifecycle,
                dispositionRegistry = registries.disposition,
                evaluatedAtEpochMillis = fixture.evaluatedAtEpochMillis,
                acceptancePolicy = fixture.acceptancePolicy
            )

        val alternativeBindings =
            intent.request.itemKeys.map { itemKey ->
                binding(alternative, itemKey, requireNotNull(alternativeCases[itemKey]))
            }
        val alternativePrices =
            StapleWatchAlternativeStoreBasketPriceFacts.resolve(
                identityFacts = identities,
                stores = listOf(storeScope(alternative)),
                priceBindings = alternativeBindings,
                priceRequests = alternativeBindings.map { binding ->
                    requireNotNull(alternativeCases[binding.itemKey]).request
                },
                lifecycleRegistry = registries.lifecycle,
                dispositionRegistry = registries.disposition,
                evaluatedAtEpochMillis = fixture.evaluatedAtEpochMillis,
                acceptancePolicy = fixture.acceptancePolicy
            )
        val travel =
            StapleWatchAlternativeAdditionalTravelFacts.fromUnordered(
                identityFacts = identities,
                additionalTravelByStore =
                    mapOf(
                        alternative to
                            ShoppingTravel(
                                distanceMetres = 2_000L,
                                travelTimeSeconds = 300L
                            )
                    )
            )
        val currentness =
            StapleWatchEvidenceCurrentnessFacts.resolve(
                usualStorePriceFacts = usualPrices,
                alternativeStorePriceFacts = alternativePrices,
                lifecycleRegistry = registries.lifecycle,
                dispositionRegistry = registries.disposition,
                evaluatedAtEpochMillis = fixture.evaluatedAtEpochMillis,
                acceptancePolicy = fixture.acceptancePolicy
            )

        return FactBundle(
            identities = identities,
            usualPrices = usualPrices,
            alternativePrices = alternativePrices,
            travel = travel,
            currentness = currentness
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
            providerItemId = "${storeKey.value}-${itemKey.value}-product-$id",
            merchantKey = merchantKey(storeKey),
            locationKey = locationKey(storeKey),
            priceMinor = priceMinor,
            observedAtEpochMillis = 4_500L
        )

    private fun storeScope(storeKey: ShoppingStoreKey): PracticalShoppingProductionPriceStoreScope =
        PracticalShoppingProductionPriceStoreScope(
            storeKey = storeKey,
            merchantKey = merchantKey(storeKey),
            locationKey = locationKey(storeKey),
            commerceChannelKey = "IN_STORE"
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

    private fun merchantKey(storeKey: ShoppingStoreKey): String =
        "merchant-${storeKey.value}"

    private fun locationKey(storeKey: ShoppingStoreKey): String =
        "location-${storeKey.value}"

    private fun expectIllegalArgument(block: () -> Unit) {
        try {
            block()
            throw AssertionError("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // Expected fail-closed contract.
        }
    }

    private fun source(fileName: String): File {
        var directory = File(System.getProperty("user.dir") ?: error("user.dir unavailable"))
        repeat(8) {
            val candidate = File(directory, "app/src/main/java/com/valuepilot/app/$fileName")
            if (candidate.isFile) return candidate
            directory = directory.parentFile ?: return@repeat
        }
        error("Could not locate $fileName")
    }

    private data class FactBundle(
        val identities: StapleWatchAlternativeStoreIdentityFacts,
        val usualPrices: StapleWatchUsualStoreBasketPriceFacts,
        val alternativePrices: StapleWatchAlternativeStoreBasketPriceFacts,
        val travel: StapleWatchAlternativeAdditionalTravelFacts,
        val currentness: StapleWatchEvidenceCurrentnessFacts
    )
}
