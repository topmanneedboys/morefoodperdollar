package com.valuepilot.app

import com.valuepilot.core.Money
import com.valuepilot.core.PracticalShoppingProductionPriceBinding
import com.valuepilot.core.PracticalShoppingProductionPriceStoreScope
import com.valuepilot.core.ShoppingItemKey
import com.valuepilot.core.ShoppingRequest
import com.valuepilot.core.ShoppingStoreKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class StapleWatchPolicyRouteAvailabilityObserverTest {

    private val fixture = StapleWatchProductionPriceTestFixture()
    private val milk = ShoppingItemKey("milk")
    private val eggs = ShoppingItemKey("eggs")
    private val usual = ShoppingStoreKey("policy-route-availability-usual")
    private val intent =
        StapleWatchFactCheckIntent(
            request = ShoppingRequest(listOf(milk, eggs)),
            usualStoreKey = usual
        )

    @Test
    fun resolvedHiddenEvidenceEmitsAvailableWithoutCreatingDraftSession() {
        val availability = mutableListOf<StapleWatchPolicyRouteAvailability>()
        var sessionFactoryCalls = 0
        val coordinator =
            StapleWatchPolicySetupCompositionCoordinator(
                routeAvailabilityObserver =
                    StapleWatchPolicyRouteAvailabilityObserver { value -> availability += value },
                sessionFactory = {
                    sessionFactoryCalls += 1
                    error("Hidden availability observation must not create a draft session")
                }
            )

        coordinator.onPreconditions(resolvedPreconditions("resolved"))

        assertEquals(listOf(StapleWatchPolicyRouteAvailability.AVAILABLE), availability)
        assertEquals(0, sessionFactoryCalls)
    }

    @Test
    fun exactDuplicateEvidenceEmitsNoDuplicateAvailability() {
        val availability = mutableListOf<StapleWatchPolicyRouteAvailability>()
        val coordinator = coordinator(availability)
        val preconditions = resolvedPreconditions("duplicate")

        coordinator.onPreconditions(preconditions)
        coordinator.onPreconditions(preconditions)

        assertEquals(listOf(StapleWatchPolicyRouteAvailability.AVAILABLE), availability)
    }

    @Test
    fun blockedReplacementEmitsUnavailableFromSameBaselineResolutionBoundary() {
        val availability = mutableListOf<StapleWatchPolicyRouteAvailability>()
        val coordinator = coordinator(availability)
        val blocked = mixedMoneyPreconditions("blocked")
        assertTrue(blocked.satisfied)
        assertNull(StapleWatchPolicyBaselineMoneySpecResolver.resolve(blocked).moneySpec)

        coordinator.onPreconditions(resolvedPreconditions("first"))
        coordinator.onPreconditions(blocked)

        assertEquals(
            listOf(
                StapleWatchPolicyRouteAvailability.AVAILABLE,
                StapleWatchPolicyRouteAvailability.UNAVAILABLE
            ),
            availability
        )
    }

    @Test
    fun newResolvedEvidenceObjectEmitsFreshAvailableSignal() {
        val availability = mutableListOf<StapleWatchPolicyRouteAvailability>()
        val coordinator = coordinator(availability)

        coordinator.onPreconditions(resolvedPreconditions("first"))
        coordinator.onPreconditions(
            resolvedPreconditions(
                prefix = "second",
                currencyCode = "BHD",
                fractionDigits = 3
            )
        )

        assertEquals(
            listOf(
                StapleWatchPolicyRouteAvailability.AVAILABLE,
                StapleWatchPolicyRouteAvailability.AVAILABLE
            ),
            availability
        )
    }

    @Test
    fun closeEmitsNothingAndIgnoresLaterEvidence() {
        val availability = mutableListOf<StapleWatchPolicyRouteAvailability>()
        val coordinator = coordinator(availability)
        coordinator.onPreconditions(resolvedPreconditions("before-close"))
        val countBeforeClose = availability.size

        coordinator.close()
        coordinator.onPreconditions(mixedMoneyPreconditions("after-close"))

        assertTrue(coordinator.isClosed())
        assertEquals(countBeforeClose, availability.size)
    }

    @Test
    fun availabilitySignalOwnsNoNavigationPolicyCompletionOrEconomicAuthority() {
        val source = source().readText()

        assertTrue(source.contains("internal enum class StapleWatchPolicyRouteAvailability"))
        assertTrue(source.contains("StapleWatchPolicyRouteAvailabilityObserver"))
        assertTrue(
            source.contains(
                "latestMoneySpec = StapleWatchPolicyBaselineMoneySpecResolver.resolve(preconditions).moneySpec"
            )
        )
        assertTrue(source.contains("routeAvailabilityObserver.onAvailabilityChanged("))
        assertTrue(source.contains("StapleWatchPolicyRouteAvailability.AVAILABLE"))
        assertTrue(source.contains("StapleWatchPolicyRouteAvailability.UNAVAILABLE"))

        listOf(
            "AppRoute",
            "AppShellIntent",
            "MainActivity",
            "dispatch(",
            "StapleWatchPolicy(",
            "Money(",
            "StapleWatchEconomicEvaluator",
            "StapleWatchEconomicDecision",
            "StapleWatchForegroundEvaluationCoordinator",
            "SharedPreferences",
            "WorkManager",
            "NotificationManager",
            "android."
        ).forEach { forbidden ->
            assertFalse("Availability signal must not own $forbidden", source.contains(forbidden))
        }
    }

    private fun coordinator(
        availability: MutableList<StapleWatchPolicyRouteAvailability>
    ): StapleWatchPolicySetupCompositionCoordinator =
        StapleWatchPolicySetupCompositionCoordinator(
            routeAvailabilityObserver =
                StapleWatchPolicyRouteAvailabilityObserver { value -> availability += value },
            sessionFactory = {
                error("Hidden availability tests must not create a draft session")
            }
        )

    private fun resolvedPreconditions(
        prefix: String,
        currencyCode: String = "CAD",
        fractionDigits: Int = 2
    ): StapleWatchEconomicEvidencePreconditions {
        val milkPrice =
            withMoneySpec(
                priceCase("$prefix-milk-$fractionDigits", milk, 500L),
                currencyCode = currencyCode,
                fractionDigits = fractionDigits
            )
        val eggsPrice =
            withMoneySpec(
                priceCase("$prefix-eggs-$fractionDigits", eggs, 700L),
                currencyCode = currencyCode,
                fractionDigits = fractionDigits
            )
        return preconditions(milkPrice, eggsPrice)
    }

    private fun mixedMoneyPreconditions(prefix: String): StapleWatchEconomicEvidencePreconditions {
        val milkPrice =
            withMoneySpec(
                priceCase("$prefix-milk-2", milk, 500L),
                currencyCode = "CAD",
                fractionDigits = 2
            )
        val eggsPrice =
            withMoneySpec(
                priceCase("$prefix-eggs-2", eggs, 700L),
                currencyCode = "USD",
                fractionDigits = 2
            )
        return preconditions(milkPrice, eggsPrice)
    }

    private fun preconditions(
        usualMilk: StapleWatchProductionPriceTestFixture.PriceCase,
        usualEggs: StapleWatchProductionPriceTestFixture.PriceCase
    ): StapleWatchEconomicEvidencePreconditions {
        val casesByItem = mapOf(milk to usualMilk, eggs to usualEggs)
        val allCases = casesByItem.values.toList()
        val registries = fixture.registries(allCases)
        val identityFacts =
            StapleWatchAlternativeStoreIdentityFacts.fromUnordered(
                intent = intent,
                alternativeStoreKeys = emptyList()
            )
        val bindings =
            intent.request.itemKeys.map { itemKey ->
                binding(itemKey, requireNotNull(casesByItem[itemKey]))
            }
        val requests =
            intent.request.itemKeys.map { itemKey -> requireNotNull(casesByItem[itemKey]).request }
        val usualStorePriceFacts =
            StapleWatchUsualStoreBasketPriceFacts.resolve(
                intent = intent,
                store = store(),
                priceBindings = bindings,
                priceRequests = requests,
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
            merchantKey = "merchant-policy-route-availability",
            locationKey = "location-policy-route-availability",
            priceMinor = priceMinor,
            observedAtEpochMillis = 4_500L
        )

    private fun withMoneySpec(
        priceCase: StapleWatchProductionPriceTestFixture.PriceCase,
        currencyCode: String,
        fractionDigits: Int
    ): StapleWatchProductionPriceTestFixture.PriceCase {
        val record = priceCase.request.record
        val rewrittenRecord =
            record.copy(
                sourcePriceFields =
                    record.sourcePriceFields.map { field ->
                        field.copy(
                            parsedAmount =
                                field.parsedAmount?.let { amount ->
                                    Money(
                                        minorUnits = amount.minorUnits,
                                        currencyCode = currencyCode,
                                        fractionDigits = fractionDigits
                                    )
                                }
                        )
                    }
            )
        return priceCase.copy(request = priceCase.request.copy(record = rewrittenRecord))
    }

    private fun store(): PracticalShoppingProductionPriceStoreScope =
        PracticalShoppingProductionPriceStoreScope(
            storeKey = usual,
            merchantKey = "merchant-policy-route-availability",
            locationKey = "location-policy-route-availability",
            commerceChannelKey = "IN_STORE"
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

    private fun source(): File =
        File(
            System.getProperty("user.dir"),
            "src/main/java/com/valuepilot/app/StapleWatchPolicySetupCompositionCoordinator.kt"
        )
}
