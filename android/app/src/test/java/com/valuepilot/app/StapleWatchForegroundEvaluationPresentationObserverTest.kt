package com.valuepilot.app

import com.valuepilot.core.Money
import com.valuepilot.core.PracticalShoppingProductionPriceBinding
import com.valuepilot.core.PracticalShoppingProductionPriceStoreScope
import com.valuepilot.core.ShoppingItemKey
import com.valuepilot.core.ShoppingRequest
import com.valuepilot.core.ShoppingStoreKey
import com.valuepilot.core.ShoppingTravel
import com.valuepilot.core.StapleWatchPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class StapleWatchForegroundEvaluationPresentationObserverTest {

    private val fixture = StapleWatchProductionPriceTestFixture()
    private val milk = ShoppingItemKey("milk")
    private val eggs = ShoppingItemKey("eggs")
    private val usual = ShoppingStoreKey("opaque-presentation-usual-111111")
    private val alternative = ShoppingStoreKey("opaque-presentation-alt-222222")
    private val intent =
        StapleWatchFactCheckIntent(
            request = ShoppingRequest(listOf(milk, eggs)),
            usualStoreKey = usual
        )

    @Test
    fun `foreground output clears stale result renders projected evaluation and fails closed without projection`() {
        var clearCalls = 0
        var renderCalls = 0
        var renderedState: StapleWatchUiState? = null
        val presenter =
            StapleWatchSurfacePresenter(
                StapleWatchSurfaceRenderer { state ->
                    renderCalls += 1
                    renderedState = state
                }
            )
        val presentationObserver =
            StapleWatchForegroundEvaluationPresentationObserver(
                presenter = presenter,
                clearSurface = { clearCalls += 1 }
            )
        val host =
            StapleWatchForegroundEvaluationInputHost(
                outputObserver = presentationObserver
            )

        host.onPreconditions(preconditions("rendered"))

        assertEquals(1, clearCalls)
        assertEquals(0, renderCalls)

        host.accept(metadata("Observed Market"))
        host.accept(policy("CAD"))

        assertEquals(1, clearCalls)
        assertEquals(1, renderCalls)
        val firstEvaluation = requireNotNull(host.currentSessionOrNull()?.evaluation)
        val firstProjection = requireNotNull(firstEvaluation.projection)
        assertSame(firstProjection.state, renderedState)
        assertEquals(StapleWatchUiStatus.WORTH_CHECKING, renderedState?.status)
        assertEquals("Observed Market", renderedState?.switchCandidate?.storeName)

        host.onPreconditions(preconditions("blocked"))

        assertEquals(2, clearCalls)
        assertEquals(1, renderCalls)

        host.accept(metadata("Replacement Market"))
        host.accept(policy("USD"))

        assertEquals(3, clearCalls)
        assertEquals(1, renderCalls)
        val blockedEvaluation = requireNotNull(host.currentSessionOrNull()?.evaluation)
        assertNull(blockedEvaluation.projection)
        assertTrue(!blockedEvaluation.evaluated)
    }

    @Test
    fun `presentation observer only selects projection render or clear lifecycle`() {
        val source = source("StapleWatchForegroundEvaluationPresentationObserver.kt").readText()

        assertTrue(source.contains("StapleWatchForegroundEvaluationOutputObserver"))
        assertTrue(source.contains("StapleWatchForegroundEvaluationOutput.Cleared -> clearSurface()"))
        assertTrue(source.contains("val projection = output.evaluation.projection"))
        assertTrue(source.contains("presenter.render(projection)"))

        listOf(
            "decisionCoordination",
            "baselineAssembly",
            "alternativeAssembly",
            "StapleWatchUiProjector",
            "StapleWatchEconomicEvaluator",
            "StapleWatchPolicy(",
            "Money(",
            "ShoppingStoreKey",
            "SharedPreferences",
            "WorkManager",
            "NotificationManager",
            "MainActivity",
            "android."
        ).forEach { forbidden ->
            assertFalse("Foreground presentation observer must not own $forbidden", source.contains(forbidden))
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

    private fun policy(currencyCode: String): StapleWatchPolicy =
        StapleWatchPolicy(
            minimumSwitchSavings = Money(100L, currencyCode),
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
