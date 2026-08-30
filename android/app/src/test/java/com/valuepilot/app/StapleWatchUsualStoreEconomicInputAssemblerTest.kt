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

class StapleWatchUsualStoreEconomicInputAssemblerTest {

    private val fixture = StapleWatchProductionPriceTestFixture()
    private val milk = ShoppingItemKey("milk")
    private val eggs = ShoppingItemKey("eggs")
    private val usual = ShoppingStoreKey("usual")
    private val intent =
        StapleWatchFactCheckIntent(
            request = ShoppingRequest(listOf(milk, eggs)),
            usualStoreKey = usual
        )

    @Test
    fun completeCurrentBaselineSumsExactMoneyAndPreservesFreshAndAgingEvidence() {
        val preconditions =
            preconditions(
                usualMilk =
                    priceCase(
                        id = "baseline-fresh-milk",
                        itemKey = milk,
                        priceMinor = 500L,
                        observedAtEpochMillis = 4_500L
                    ),
                usualEggs =
                    priceCase(
                        id = "baseline-aging-eggs",
                        itemKey = eggs,
                        priceMinor = 700L,
                        observedAtEpochMillis = 2_500L
                    )
            )
        assertTrue(preconditions.satisfied)

        val result = StapleWatchUsualStoreEconomicInputAssembler.assemble(preconditions)
        val candidate = requireNotNull(result.candidate)

        assertTrue(result.assembled)
        assertNull(result.blocker)
        assertSame(preconditions, result.preconditions)
        assertEquals(usual, candidate.storeKey)
        assertEquals(intent.request.itemKeys.toSet(), candidate.coveredItemKeys)
        assertEquals(Money(1_200L, "CAD"), candidate.knownBasketCost)
        assertEquals(1, candidate.evidence.freshItemCount)
        assertEquals(1, candidate.evidence.agingItemCount)
        assertEquals(0, candidate.evidence.staleItemCount)
        assertEquals(0, candidate.evidence.unknownFreshnessItemCount)
        assertEquals(2, candidate.evidence.totalItemCount)
    }

    @Test
    fun blockedEvidencePreconditionsCannotMintBaselineCandidate() {
        val preconditions =
            preconditions(
                usualMilk = priceCase("blocked-milk", milk, priceMinor = 500L),
                usualEggs = null
            )
        assertFalse(preconditions.satisfied)
        assertEquals(
            StapleWatchEconomicEvidencePreconditionIssue.USUAL_STORE_PRICE_COVERAGE_INCOMPLETE,
            preconditions.issue
        )

        val result = StapleWatchUsualStoreEconomicInputAssembler.assemble(preconditions)

        assertFalse(result.assembled)
        assertNull(result.candidate)
        assertEquals(
            StapleWatchUsualStoreEconomicInputBlocker.EVIDENCE_PRECONDITIONS_NOT_SATISFIED,
            result.blocker
        )
        assertSame(preconditions, result.preconditions)
    }

    @Test
    fun mixedMoneySpecificationBlocksBeforeBasketTotalIsMinted() {
        val cadMilk = priceCase("mixed-cad-milk", milk, priceMinor = 500L)
        val usdEggs =
            withCurrency(
                priceCase("mixed-usd-eggs", eggs, priceMinor = 700L),
                currencyCode = "USD"
            )
        val preconditions = preconditions(cadMilk, usdEggs)
        assertTrue(preconditions.satisfied)
        assertEquals(
            listOf("CAD", "USD"),
            preconditions.usualStorePriceFacts.itemPrices.map { fact ->
                requireNotNull(fact.exactPrice).currencyCode
            }
        )

        val result = StapleWatchUsualStoreEconomicInputAssembler.assemble(preconditions)

        assertFalse(result.assembled)
        assertNull(result.candidate)
        assertEquals(StapleWatchUsualStoreEconomicInputBlocker.MIXED_MONEY_SPEC, result.blocker)
    }

    @Test
    fun exactBasketTotalOverflowFailsClosed() {
        val individuallySafe = Long.MAX_VALUE / 2L + 10_000L
        val preconditions =
            preconditions(
                usualMilk =
                    priceCase(
                        id = "overflow-milk",
                        itemKey = milk,
                        priceMinor = individuallySafe
                    ),
                usualEggs =
                    priceCase(
                        id = "overflow-eggs",
                        itemKey = eggs,
                        priceMinor = individuallySafe
                    )
            )
        assertTrue(preconditions.satisfied)

        val result = StapleWatchUsualStoreEconomicInputAssembler.assemble(preconditions)

        assertFalse(result.assembled)
        assertNull(result.candidate)
        assertEquals(
            StapleWatchUsualStoreEconomicInputBlocker.BASKET_TOTAL_OVERFLOW,
            result.blocker
        )
    }

    @Test
    fun boundaryOwnsOnlyUsualStoreExactAssemblyNotTravelEconomicsOrDelivery() {
        val constructors =
            StapleWatchUsualStoreEconomicInputAssembly::class.java.declaredConstructors
                .filterNot { constructor -> constructor.isSynthetic }
        assertTrue(constructors.isNotEmpty())
        assertTrue(constructors.all { constructor -> Modifier.isPrivate(constructor.modifiers) })
        assertFalse(
            StapleWatchUsualStoreEconomicInputAssembly::class.java.methods.any { method ->
                method.name == "copy" || method.name.startsWith("copy$")
            }
        )

        val source = source("StapleWatchUsualStoreEconomicInputAssembler.kt").readText()
        assertTrue(source.contains("if (!preconditions.satisfied)"))
        assertTrue(source.contains("preconditions.usualStorePriceFacts.itemPrices"))
        assertTrue(source.contains("preconditions.currentnessFacts.usualStore.itemCurrentness"))
        assertTrue(source.contains("StapleWatchBasketCandidate("))
        assertTrue(source.contains("EvidenceFreshness.FRESH"))
        assertTrue(source.contains("EvidenceFreshness.AGING"))

        listOf(
            "StapleWatchFactResolutionReadiness",
            "SingleStorePlanCandidate",
            "StapleWatchBasketAlternativeCandidate",
            "StapleWatchAlternativeCandidate",
            "StapleWatchEconomicEvaluator",
            "StapleWatchPolicy",
            "ShoppingTravel",
            ".additionalTravel",
            "alternativeStorePriceFacts",
            "Math.addExact",
            "Math.subtractExact",
            "OpenPrices",
            "OpenStreetMap",
            "Http",
            "URL(",
            "System.currentTimeMillis",
            "SharedPreferences",
            "WorkManager",
            "NotificationManager",
            "android."
        ).forEach { forbidden ->
            assertFalse("Usual-store Watch assembler must not own $forbidden", source.contains(forbidden))
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
        priceMinor: Long,
        observedAtEpochMillis: Long = 4_500L
    ): StapleWatchProductionPriceTestFixture.PriceCase =
        fixture.case(
            requestId = id,
            providerItemId = "${usual.value}-${itemKey.value}-product-$id",
            merchantKey = "merchant-a",
            locationKey = "location-a",
            priceMinor = priceMinor,
            observedAtEpochMillis = observedAtEpochMillis
        )

    private fun withCurrency(
        priceCase: StapleWatchProductionPriceTestFixture.PriceCase,
        currencyCode: String
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
                                        fractionDigits = amount.fractionDigits
                                    )
                                }
                        )
                    }
            )
        return priceCase.copy(
            request = priceCase.request.copy(record = rewrittenRecord)
        )
    }

    private fun store(): PracticalShoppingProductionPriceStoreScope =
        PracticalShoppingProductionPriceStoreScope(
            storeKey = usual,
            merchantKey = "merchant-a",
            locationKey = "location-a",
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

    private fun source(fileName: String): File =
        File(
            System.getProperty("user.dir"),
            "src/main/java/com/valuepilot/app/$fileName"
        )
}
