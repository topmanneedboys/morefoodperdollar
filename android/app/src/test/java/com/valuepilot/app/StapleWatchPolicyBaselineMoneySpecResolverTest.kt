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
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.lang.reflect.Modifier

class StapleWatchPolicyBaselineMoneySpecResolverTest {

    private val fixture = StapleWatchProductionPriceTestFixture()
    private val milk = ShoppingItemKey("milk")
    private val eggs = ShoppingItemKey("eggs")
    private val usual = ShoppingStoreKey("opaque-policy-money-usual")
    private val intent =
        StapleWatchFactCheckIntent(
            request = ShoppingRequest(listOf(milk, eggs)),
            usualStoreKey = usual
        )

    @Test
    fun assembledBaselineExposesExactNonDefaultMoneySpecification() {
        val milkPrice =
            withMoneySpec(
                priceCase("bhd-milk", milk, 500L),
                currencyCode = "BHD",
                fractionDigits = 3
            )
        val eggsPrice =
            withMoneySpec(
                priceCase("bhd-eggs", eggs, 700L),
                currencyCode = "BHD",
                fractionDigits = 3
            )
        val preconditions = preconditions(milkPrice, eggsPrice)

        val resolution = StapleWatchPolicyBaselineMoneySpecResolver.resolve(preconditions)
        val moneySpec = requireNotNull(resolution.moneySpec)
        val baseline = requireNotNull(resolution.baselineAssembly.candidate)

        assertTrue(resolution.resolved)
        assertSame(preconditions, resolution.preconditions)
        assertSame(resolution.baselineAssembly, moneySpec.baselineAssembly)
        assertEquals(Money(1_200L, "BHD", 3), baseline.knownBasketCost)
        assertEquals("BHD", moneySpec.currencyCode)
        assertEquals(3, moneySpec.fractionDigits)
    }

    @Test
    fun mixedBaselineMoneySpecificationFailsClosedWithAssemblerBlocker() {
        val cadMilk = priceCase("mixed-cad-milk", milk, 500L)
        val usdEggs =
            withMoneySpec(
                priceCase("mixed-usd-eggs", eggs, 700L),
                currencyCode = "USD",
                fractionDigits = 2
            )
        val preconditions = preconditions(cadMilk, usdEggs)
        assertTrue(preconditions.satisfied)

        val resolution = StapleWatchPolicyBaselineMoneySpecResolver.resolve(preconditions)

        assertFalse(resolution.resolved)
        assertNull(resolution.moneySpec)
        assertFalse(resolution.baselineAssembly.assembled)
        assertEquals(
            StapleWatchUsualStoreEconomicInputBlocker.MIXED_MONEY_SPEC,
            resolution.baselineAssembly.blocker
        )
    }

    @Test
    fun incompleteBaselineEvidenceCannotMintPolicyMoneySpecification() {
        val preconditions = preconditions(priceCase("only-milk", milk, 500L), null)
        assertFalse(preconditions.satisfied)

        val resolution = StapleWatchPolicyBaselineMoneySpecResolver.resolve(preconditions)

        assertFalse(resolution.resolved)
        assertNull(resolution.moneySpec)
        assertEquals(
            StapleWatchUsualStoreEconomicInputBlocker.EVIDENCE_PRECONDITIONS_NOT_SATISFIED,
            resolution.baselineAssembly.blocker
        )
        assertSame(preconditions, resolution.baselineAssembly.preconditions)
    }

    @Test
    fun resolverDelegatesMoneyAuthorityWithoutOwningPolicyEconomicsOrUi() {
        val moneySpecConstructors =
            StapleWatchPolicyBaselineMoneySpec::class.java.declaredConstructors
                .filterNot { constructor -> constructor.isSynthetic }
        val resolutionConstructors =
            StapleWatchPolicyBaselineMoneySpecResolution::class.java.declaredConstructors
                .filterNot { constructor -> constructor.isSynthetic }
        assertTrue(moneySpecConstructors.isNotEmpty())
        assertTrue(resolutionConstructors.isNotEmpty())
        assertTrue(moneySpecConstructors.all { constructor -> Modifier.isPrivate(constructor.modifiers) })
        assertTrue(resolutionConstructors.all { constructor -> Modifier.isPrivate(constructor.modifiers) })

        val source = source().readText()
        assertTrue(
            source.contains(
                "StapleWatchUsualStoreEconomicInputAssembler.assemble(preconditions)"
            )
        )
        assertTrue(source.contains("baseline.knownBasketCost.currencyCode"))
        assertTrue(source.contains("baseline.knownBasketCost.fractionDigits"))

        listOf(
            "usualStorePriceFacts.itemPrices",
            "alternativeStorePriceFacts",
            "additionalTravelFacts",
            "Money(",
            "StapleWatchPolicy(",
            "minimumSwitchSavings",
            "maxAdditionalTravelSeconds",
            "maxAdditionalDistanceMetres",
            "minimumStapleItemCount",
            "System.currentTimeMillis",
            "Locale.",
            "Currency.getInstance",
            "SharedPreferences",
            "WorkManager",
            "NotificationManager",
            "renderer.render",
            "MainActivity",
            "android."
        ).forEach { forbidden ->
            assertFalse("Policy money resolver must not own $forbidden", source.contains(forbidden))
        }
    }

    private fun preconditions(
        usualMilk: StapleWatchProductionPriceTestFixture.PriceCase,
        usualEggs: StapleWatchProductionPriceTestFixture.PriceCase?
    ): StapleWatchEconomicEvidencePreconditions {
        val casesByItem = mapOf(milk to usualMilk, eggs to usualEggs)
        val allCases = casesByItem.values.filterNotNull()
        val registries = fixture.registries(allCases)
        val identityFacts =
            StapleWatchAlternativeStoreIdentityFacts.fromUnordered(
                intent = intent,
                alternativeStoreKeys = emptyList()
            )
        val bindings =
            intent.request.itemKeys.mapNotNull { itemKey ->
                casesByItem[itemKey]?.let { priceCase -> binding(itemKey, priceCase) }
            }
        val requests =
            intent.request.itemKeys.mapNotNull { itemKey -> casesByItem[itemKey]?.request }
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
            merchantKey = "merchant-policy-money",
            locationKey = "location-policy-money",
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
            merchantKey = "merchant-policy-money",
            locationKey = "location-policy-money",
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
            "src/main/java/com/valuepilot/app/StapleWatchPolicyBaselineMoneySpecResolver.kt"
        )
}
