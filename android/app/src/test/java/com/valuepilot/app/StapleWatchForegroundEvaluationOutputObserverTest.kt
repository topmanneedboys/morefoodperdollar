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
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class StapleWatchForegroundEvaluationOutputObserverTest {

    private val fixture = StapleWatchProductionPriceTestFixture()
    private val milk = ShoppingItemKey("milk")
    private val eggs = ShoppingItemKey("eggs")
    private val usual = ShoppingStoreKey("opaque-output-usual-111111")
    private val alternative = ShoppingStoreKey("opaque-output-alt-222222")
    private val intent =
        StapleWatchFactCheckIntent(
            request = ShoppingRequest(listOf(milk, eggs)),
            usualStoreKey = usual
        )

    @Test
    fun `new evidence clears output and completed explicit inputs emit exact evaluation`() {
        val outputs = mutableListOf<StapleWatchForegroundEvaluationOutput>()
        val host =
            StapleWatchForegroundEvaluationInputHost(
                outputObserver = StapleWatchForegroundEvaluationOutputObserver(outputs::add)
            )
        val preconditions = preconditions("first")

        host.onPreconditions(preconditions)
        assertEquals(1, outputs.size)
        assertSame(StapleWatchForegroundEvaluationOutput.Cleared, outputs.single())

        host.accept(policy())
        assertEquals(1, outputs.size)

        host.accept(metadata("Observed Market"))

        assertEquals(2, outputs.size)
        val evaluated = outputs[1] as StapleWatchForegroundEvaluationOutput.Evaluated
        val session = requireNotNull(host.currentSessionOrNull())
        assertSame(session.evaluation, evaluated.evaluation)
        assertSame(preconditions, evaluated.evaluation.preconditions)
        assertTrue(evaluated.evaluation.projection != null)
    }

    @Test
    fun `replacement evidence clears previously evaluated output before new completion`() {
        val outputs = mutableListOf<StapleWatchForegroundEvaluationOutput>()
        val host =
            StapleWatchForegroundEvaluationInputHost(
                outputObserver = StapleWatchForegroundEvaluationOutputObserver(outputs::add)
            )

        host.onPreconditions(preconditions("first"))
        host.accept(metadata("First Market"))
        host.accept(policy())
        val firstEvaluation =
            (outputs.last() as StapleWatchForegroundEvaluationOutput.Evaluated).evaluation

        val replacement = preconditions("second")
        host.onPreconditions(replacement)

        assertEquals(3, outputs.size)
        assertSame(StapleWatchForegroundEvaluationOutput.Cleared, outputs.last())
        val current = requireNotNull(host.currentSessionOrNull())
        assertSame(replacement, current.preconditions)
        assertTrue(current.evaluation == null)

        host.accept(policy())
        host.accept(metadata("Second Market"))

        assertEquals(4, outputs.size)
        val secondEvaluation =
            (outputs.last() as StapleWatchForegroundEvaluationOutput.Evaluated).evaluation
        assertTrue(firstEvaluation !== secondEvaluation)
        assertSame(replacement, secondEvaluation.preconditions)
    }

    @Test
    fun `repeated accepted preconditions mirror fresh-session lifecycle without hidden dedupe`() {
        val outputs = mutableListOf<StapleWatchForegroundEvaluationOutput>()
        val host =
            StapleWatchForegroundEvaluationInputHost(
                outputObserver = StapleWatchForegroundEvaluationOutputObserver(outputs::add)
            )
        val preconditions = preconditions("repeat")

        host.onPreconditions(preconditions)
        host.onPreconditions(preconditions)

        assertEquals(2, outputs.size)
        assertTrue(outputs.all { it === StapleWatchForegroundEvaluationOutput.Cleared })
    }

    @Test
    fun `closed host emits no later output callbacks`() {
        val outputs = mutableListOf<StapleWatchForegroundEvaluationOutput>()
        val host =
            StapleWatchForegroundEvaluationInputHost(
                outputObserver = StapleWatchForegroundEvaluationOutputObserver(outputs::add)
            )

        host.onPreconditions(preconditions("before-close"))
        host.close()
        val countAtClose = outputs.size

        host.onPreconditions(preconditions("after-close"))
        host.accept(policy())
        host.accept(metadata("After Close"))

        assertEquals(countAtClose, outputs.size)
    }

    @Test
    fun `output boundary owns lifecycle only and no presentation delivery or economic authority`() {
        val boundary = source("StapleWatchForegroundEvaluationOutput.kt").readText()
        val host = source("StapleWatchForegroundEvaluationInputHost.kt").readText()

        assertTrue(boundary.contains("object Cleared"))
        assertTrue(boundary.contains("class Evaluated("))
        assertTrue(boundary.contains("val evaluation: StapleWatchForegroundEvaluation"))
        assertTrue(host.contains("outputObserver.onOutputChanged(StapleWatchForegroundEvaluationOutput.Cleared)"))
        assertTrue(host.contains("session.evaluation?.let { evaluation ->"))

        listOf(
            "StapleWatchSurfacePresenter",
            "StapleWatchSurfaceView",
            "renderer.render",
            "StapleWatchUiProjector",
            "StapleWatchEconomicEvaluator",
            "StapleWatchPolicy(",
            "Money(",
            "SharedPreferences",
            "WorkManager",
            "NotificationManager",
            "MainActivity",
            "android."
        ).forEach { forbidden ->
            assertTrue(
                "Output boundary/host must not own $forbidden",
                !boundary.contains(forbidden) && !host.contains(forbidden)
            )
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
