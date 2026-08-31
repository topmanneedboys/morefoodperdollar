package com.valuepilot.app

import com.valuepilot.core.Money
import com.valuepilot.core.PracticalShoppingProductionPriceBinding
import com.valuepilot.core.PracticalShoppingProductionPriceStoreScope
import com.valuepilot.core.ShoppingItemKey
import com.valuepilot.core.ShoppingRequest
import com.valuepilot.core.ShoppingStoreKey
import com.valuepilot.core.ShoppingTravel
import com.valuepilot.core.StapleWatchPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class StapleWatchForegroundEvaluationInputHostTest {

    private val fixture = StapleWatchProductionPriceTestFixture()
    private val milk = ShoppingItemKey("milk")
    private val eggs = ShoppingItemKey("eggs")
    private val usual = ShoppingStoreKey("opaque-host-usual-111111")
    private val alternative = ShoppingStoreKey("opaque-host-alt-222222")
    private val intent =
        StapleWatchFactCheckIntent(
            request = ShoppingRequest(listOf(milk, eggs)),
            usualStoreKey = usual
        )

    @Test
    fun completedPreconditionsStartFreshExplicitInputSession() {
        val preconditions = preconditions("start")
        val host = StapleWatchForegroundEvaluationInputHost()

        host.onPreconditions(preconditions)

        val session = requireNotNull(host.currentSessionOrNull())
        assertSame(preconditions, session.preconditions)
        assertNull(session.policy)
        assertNull(session.displayMetadata)
        assertNull(session.evaluation)
        assertFalse(session.readyForEvaluation)
    }

    @Test
    fun displayMetadataObserverTargetsOnlyCurrentOpenEvidenceSession() {
        val host = StapleWatchForegroundEvaluationInputHost()
        val observer: StapleWatchStoreDisplayMetadataObserver = host
        val metadata = metadata("Observed Market")

        observer.onDisplayMetadata(metadata)
        assertNull(host.currentSessionOrNull())

        val preconditions = preconditions("observer")
        host.onPreconditions(preconditions)
        observer.onDisplayMetadata(metadata)

        val session = requireNotNull(host.currentSessionOrNull())
        assertSame(preconditions, session.preconditions)
        assertSame(metadata, session.displayMetadata)
        assertNull(session.policy)
        assertNull(session.evaluation)
        assertFalse(session.readyForEvaluation)

        host.close()
        observer.onDisplayMetadata(metadata("After Close"))
        assertNull(host.currentSessionOrNull())
    }

    @Test
    fun explicitInputsStayScopedToCurrentEvidenceSession() {
        val firstPreconditions = preconditions("first")
        val policy = policy()
        val metadata = metadata("First Market")
        val host = StapleWatchForegroundEvaluationInputHost()

        host.onPreconditions(firstPreconditions)
        host.accept(policy)
        host.accept(metadata)

        val completed = requireNotNull(host.currentSessionOrNull())
        assertSame(firstPreconditions, completed.preconditions)
        assertSame(policy, completed.policy)
        assertSame(metadata, completed.displayMetadata)
        assertTrue(completed.readyForEvaluation)
        assertTrue(completed.evaluation != null)

        val secondPreconditions = preconditions("second")
        host.onPreconditions(secondPreconditions)

        val replacement = requireNotNull(host.currentSessionOrNull())
        assertFalse(replacement === completed)
        assertSame(secondPreconditions, replacement.preconditions)
        assertNull(replacement.policy)
        assertNull(replacement.displayMetadata)
        assertNull(replacement.evaluation)
        assertFalse(replacement.readyForEvaluation)
    }

    @Test
    fun inputsOutsideAnOpenEvidenceSessionAreIgnored() {
        val host = StapleWatchForegroundEvaluationInputHost()
        val policy = policy()
        val metadata = metadata("Ignored Market")

        host.accept(policy)
        host.accept(metadata)
        assertNull(host.currentSessionOrNull())
        assertFalse(host.isClosed())

        host.onPreconditions(preconditions("open"))
        assertTrue(host.currentSessionOrNull() != null)

        host.close()
        assertTrue(host.isClosed())
        assertNull(host.currentSessionOrNull())

        host.onPreconditions(preconditions("after-close"))
        host.accept(policy)
        host.accept(metadata)
        assertNull(host.currentSessionOrNull())
    }

    @Test
    fun hostOwnsOnlyForegroundInputSessionSequencing() {
        val source = source("StapleWatchForegroundEvaluationInputHost.kt").readText()

        assertTrue(
            source.contains(
                "StapleWatchEconomicEvidencePreconditionsObserver,\n    StapleWatchStoreDisplayMetadataObserver,"
            )
        )
        assertTrue(
            source.contains(
                "currentSession = StapleWatchForegroundEvaluationInputSession.start(preconditions)"
            )
        )
        assertTrue(
            source.contains(
                "override fun onDisplayMetadata(metadata: StapleWatchStoreDisplayMetadata)"
            )
        )
        assertTrue(source.contains("accept(metadata)"))
        assertTrue(source.contains("session.withPolicy(policy)"))
        assertTrue(source.contains("session.withDisplayMetadata(displayMetadata)"))

        listOf(
            "StapleWatchPolicy(",
            "Money(",
            "minimumSwitchSavings =",
            "StapleWatchStoreDisplayMetadata(",
            "StapleWatchForegroundEvaluationCoordinator",
            "PracticalShoppingProduction",
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
            assertFalse("Foreground input host must not own $forbidden", source.contains(forbidden))
        }
    }

    private fun preconditions(prefix: String): StapleWatchEconomicEvidencePreconditions {
        val identityFacts =
            StapleWatchAlternativeStoreIdentityFacts.fromUnordered(
                intent = intent,
                alternativeStoreKeys = listOf(alternative)
            )
        val usualMilk = priceCase(usual, milk, "$prefix-usual-milk", 1_000L)
        val usualEggs = priceCase(usual, eggs, "$prefix-usual-eggs", 1_000L)
        val alternativeMilk = priceCase(alternative, milk, "$prefix-alt-milk", 400L)
        val alternativeEggs = priceCase(alternative, eggs, "$prefix-alt-eggs", 500L)
        val registries =
            fixture.registries(
                listOf(usualMilk, usualEggs, alternativeMilk, alternativeEggs)
            )

        val usualStorePriceFacts =
            StapleWatchUsualStoreBasketPriceFacts.resolve(
                intent = intent,
                store = storeScope(usual),
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
                stores = listOf(storeScope(alternative)),
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

    private fun storeScope(storeKey: ShoppingStoreKey): PracticalShoppingProductionPriceStoreScope =
        PracticalShoppingProductionPriceStoreScope(
            storeKey = storeKey,
            merchantKey = merchantKey(storeKey),
            locationKey = locationKey(storeKey),
            commerceChannelKey = "IN_STORE"
        )

    private fun policy(): StapleWatchPolicy =
        StapleWatchPolicy(
            minimumSwitchSavings = Money(100L, "CAD"),
            maxAdditionalTravelSeconds = 600L,
            maxAdditionalDistanceMetres = 5_000L
        )

    private fun metadata(displayName: String): StapleWatchStoreDisplayMetadata =
        StapleWatchStoreDisplayMetadata(
            entries =
                listOf(
                    StapleWatchStoreDisplayMetadataEntry(
                        storeKey = alternative,
                        displayName = displayName
                    )
                )
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
