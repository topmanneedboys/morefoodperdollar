package com.valuepilot.app

import com.valuepilot.core.PracticalShoppingProductionPriceBinding
import com.valuepilot.core.PracticalShoppingProductionPriceStoreScope
import com.valuepilot.core.ShoppingItemKey
import com.valuepilot.core.ShoppingRequest
import com.valuepilot.core.ShoppingStoreKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class StapleWatchEconomicEvidencePreconditionsFanoutTest {

    private val fixture = StapleWatchProductionPriceTestFixture()
    private val milk = ShoppingItemKey("milk")
    private val eggs = ShoppingItemKey("eggs")
    private val usual = ShoppingStoreKey("fanout-usual")
    private val intent =
        StapleWatchFactCheckIntent(
            request = ShoppingRequest(listOf(milk, eggs)),
            usualStoreKey = usual
        )

    @Test
    fun forwardsExactObjectToForegroundThenPolicy() {
        val events = mutableListOf<String>()
        var foreground: StapleWatchEconomicEvidencePreconditions? = null
        var policy: StapleWatchEconomicEvidencePreconditions? = null
        val fanout =
            StapleWatchEconomicEvidencePreconditionsFanout(
                foregroundInputObserver =
                    StapleWatchEconomicEvidencePreconditionsObserver { value ->
                        events += "foreground"
                        foreground = value
                    },
                policySetupObserver =
                    StapleWatchEconomicEvidencePreconditionsObserver { value ->
                        events += "policy"
                        policy = value
                    }
            )
        val preconditions = preconditions("exact")

        fanout.onPreconditions(preconditions)

        assertEquals(listOf("foreground", "policy"), events)
        assertSame(preconditions, foreground)
        assertSame(preconditions, policy)
    }

    @Test
    fun repeatedUpstreamCallbackRemainsRepeatedWithoutHiddenDedupeState() {
        val events = mutableListOf<String>()
        val forwarded = mutableListOf<StapleWatchEconomicEvidencePreconditions>()
        val fanout =
            StapleWatchEconomicEvidencePreconditionsFanout(
                foregroundInputObserver =
                    StapleWatchEconomicEvidencePreconditionsObserver { value ->
                        events += "foreground"
                        forwarded += value
                    },
                policySetupObserver =
                    StapleWatchEconomicEvidencePreconditionsObserver { value ->
                        events += "policy"
                        forwarded += value
                    }
            )
        val preconditions = preconditions("repeat")

        fanout.onPreconditions(preconditions)
        fanout.onPreconditions(preconditions)

        assertEquals(
            listOf("foreground", "policy", "foreground", "policy"),
            events
        )
        assertEquals(4, forwarded.size)
        forwarded.forEach { assertSame(preconditions, it) }
    }

    @Test
    fun fanoutOwnsOnlyExactForwardingAndOrder() {
        val source = source().readText()
        val foregroundCall = "foregroundInputObserver.onPreconditions(preconditions)"
        val policyCall = "policySetupObserver.onPreconditions(preconditions)"

        assertTrue(source.contains(foregroundCall))
        assertTrue(source.contains(policyCall))
        assertTrue(source.indexOf(foregroundCall) < source.indexOf(policyCall))

        listOf(
            "StapleWatchEconomicEvidencePreconditions.evaluate(",
            "StapleWatchPolicyBaselineMoneySpecResolver",
            "StapleWatchPolicy(",
            "StapleWatchPolicyDraft(",
            "Money(",
            "copy(",
            "mutableListOf",
            "latestPreconditions",
            "StapleWatchEconomicEvaluator",
            "StapleWatchEconomicDecision",
            "MainActivity",
            "SharedPreferences",
            "WorkManager",
            "NotificationManager",
            "android."
        ).forEach { forbidden ->
            assertFalse("Evidence fanout must not own $forbidden", source.contains(forbidden))
        }
    }

    private fun preconditions(prefix: String): StapleWatchEconomicEvidencePreconditions {
        val identityFacts =
            StapleWatchAlternativeStoreIdentityFacts.fromUnordered(
                intent = intent,
                alternativeStoreKeys = emptyList()
            )
        val milkPrice = priceCase("$prefix-milk", milk, 500L)
        val eggsPrice = priceCase("$prefix-eggs", eggs, 700L)
        val allCases = listOf(milkPrice, eggsPrice)
        val registries = fixture.registries(allCases)
        val usualStorePriceFacts =
            StapleWatchUsualStoreBasketPriceFacts.resolve(
                intent = intent,
                store = productionScope(),
                priceBindings =
                    listOf(
                        binding(milk, milkPrice),
                        binding(eggs, eggsPrice)
                    ),
                priceRequests = listOf(milkPrice.request, eggsPrice.request),
                lifecycleRegistry = registries.lifecycle,
                dispositionRegistry = registries.disposition,
                evaluatedAtEpochMillis = fixture.evaluatedAtEpochMillis,
                acceptancePolicy = fixture.acceptancePolicy
            )
        val alternativeStorePriceFacts =
            StapleWatchAlternativeStoreBasketPriceFacts.resolve(
                identityFacts = identityFacts,
                stores = emptyList(),
                priceBindings = emptyList(),
                priceRequests = emptyList(),
                lifecycleRegistry = registries.lifecycle,
                dispositionRegistry = registries.disposition,
                evaluatedAtEpochMillis = fixture.evaluatedAtEpochMillis,
                acceptancePolicy = fixture.acceptancePolicy
            )
        val additionalTravelFacts =
            StapleWatchAlternativeAdditionalTravelFacts.fromUnordered(
                identityFacts = identityFacts,
                additionalTravelByStore = emptyMap()
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
        id: String,
        itemKey: ShoppingItemKey,
        priceMinor: Long
    ): StapleWatchProductionPriceTestFixture.PriceCase =
        fixture.case(
            requestId = id,
            providerItemId = "${usual.value}-${itemKey.value}-$id",
            merchantKey = "merchant-${usual.value}",
            locationKey = "location-${usual.value}",
            priceMinor = priceMinor,
            observedAtEpochMillis = 4_500L
        )

    private fun binding(
        itemKey: ShoppingItemKey,
        priceCase: StapleWatchProductionPriceTestFixture.PriceCase
    ): PracticalShoppingProductionPriceBinding =
        PracticalShoppingProductionPriceBinding(
            itemKey = itemKey,
            productKey = priceCase.productKey,
            storeKey = usual,
            currentPriceRequestId = priceCase.request.requestId
        )

    private fun productionScope(): PracticalShoppingProductionPriceStoreScope =
        PracticalShoppingProductionPriceStoreScope(
            storeKey = usual,
            merchantKey = "merchant-${usual.value}",
            locationKey = "location-${usual.value}",
            commerceChannelKey = "IN_STORE"
        )

    private fun source(): File =
        File(
            System.getProperty("user.dir"),
            "src/main/java/com/valuepilot/app/StapleWatchEconomicEvidencePreconditionsFanout.kt"
        ).also {
            assertTrue("Missing evidence fanout source at ${it.absolutePath}", it.isFile)
        }
}
