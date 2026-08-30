package com.valuepilot.app

import com.valuepilot.core.Money
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

class StapleWatchAlternativeEconomicInputAssemblerTest {

    private val fixture = StapleWatchProductionPriceTestFixture()
    private val milk = ShoppingItemKey("milk")
    private val eggs = ShoppingItemKey("eggs")
    private val usual = ShoppingStoreKey("usual")
    private val altA = ShoppingStoreKey("alt-a")
    private val altB = ShoppingStoreKey("alt-b")
    private val intent =
        StapleWatchFactCheckIntent(
            request = ShoppingRequest(listOf(milk, eggs)),
            usualStoreKey = usual
        )

    @Test
    fun canonicalOutcomesPreserveReadyCandidateAndBlockedStoreWithoutRanking() {
        val scenario =
            scenario(
                alternativeStoreKeys = listOf(altB, altA),
                alternativeCases =
                    mapOf(
                        altA to
                            mapOf(
                                milk to priceCase(altA, milk, "canonical-a-milk", 400L),
                                eggs to
                                    priceCase(
                                        altA,
                                        eggs,
                                        "canonical-a-eggs",
                                        500L,
                                        observedAtEpochMillis = 2_500L
                                    )
                            ),
                        altB to
                            mapOf(
                                milk to priceCase(altB, milk, "canonical-b-milk", 300L),
                                eggs to null
                            )
                    )
            )
        assertTrue(scenario.preconditions.satisfied)
        assertEquals(listOf(altA), scenario.preconditions.priceAndCurrentnessReadyAlternativeStoreKeys)

        val result =
            StapleWatchAlternativeEconomicInputAssembler.assemble(
                preconditions = scenario.preconditions,
                baselineAssembly = scenario.baselineAssembly
            )

        assertNull(result.blocker)
        assertSame(scenario.preconditions, result.preconditions)
        assertSame(scenario.baselineAssembly, result.baselineAssembly)
        assertEquals(listOf(altA, altB), result.outcomes.map { outcome -> outcome.storeKey })

        val first = result.outcomes[0]
        val firstCandidate = requireNotNull(first.candidate)
        assertTrue(first.assembled)
        assertNull(first.blocker)
        assertEquals(Money(900L, "CAD"), firstCandidate.basket.knownBasketCost)
        assertEquals(intent.request.itemKeys.toSet(), firstCandidate.basket.coveredItemKeys)
        assertEquals(1, firstCandidate.basket.evidence.freshItemCount)
        assertEquals(1, firstCandidate.basket.evidence.agingItemCount)
        assertEquals(0, firstCandidate.basket.evidence.staleItemCount)
        assertEquals(0, firstCandidate.basket.evidence.unknownFreshnessItemCount)
        assertEquals(travel(altA), firstCandidate.additionalTravel)

        val second = result.outcomes[1]
        assertFalse(second.assembled)
        assertNull(second.candidate)
        assertEquals(
            StapleWatchAlternativeEconomicInputBlocker.PRICE_COVERAGE_INCOMPLETE,
            second.blocker
        )
        assertEquals(listOf(firstCandidate), result.assembledCandidates)
    }

    @Test
    fun laterStaleAlternativeIsPreservedAsCurrentnessBlocker() {
        val scenario =
            scenario(
                alternativeStoreKeys = listOf(altA),
                alternativeCases =
                    mapOf(
                        altA to
                            mapOf(
                                milk to
                                    priceCase(
                                        altA,
                                        milk,
                                        "stale-a-milk",
                                        400L,
                                        observedAtEpochMillis = 2_500L
                                    ),
                                eggs to
                                    priceCase(
                                        altA,
                                        eggs,
                                        "stale-a-eggs",
                                        500L,
                                        observedAtEpochMillis = 2_500L
                                    )
                            )
                    ),
                currentnessEvaluatedAtEpochMillis = 8_000L
            )
        assertTrue(scenario.preconditions.satisfied)
        assertTrue(scenario.preconditions.priceAndCurrentnessReadyAlternativeStoreKeys.isEmpty())
        assertTrue(scenario.baselineAssembly.assembled)

        val result =
            StapleWatchAlternativeEconomicInputAssembler.assemble(
                scenario.preconditions,
                scenario.baselineAssembly
            )

        assertNull(result.blocker)
        assertEquals(1, result.outcomes.size)
        assertEquals(
            StapleWatchAlternativeEconomicInputBlocker.CURRENTNESS_INCOMPLETE,
            result.outcomes.single().blocker
        )
        assertNull(result.outcomes.single().candidate)
    }

    @Test
    fun mixedMoneyAndBaselineMoneyMismatchRemainDifferentTypedBlockers() {
        val mixedEggs =
            withCurrency(
                priceCase(altA, eggs, "money-mixed-a-eggs", 500L),
                currencyCode = "USD"
            )
        val usdBMilk =
            withCurrency(
                priceCase(altB, milk, "money-usd-b-milk", 300L),
                currencyCode = "USD"
            )
        val usdBEggs =
            withCurrency(
                priceCase(altB, eggs, "money-usd-b-eggs", 400L),
                currencyCode = "USD"
            )
        val scenario =
            scenario(
                alternativeStoreKeys = listOf(altA, altB),
                alternativeCases =
                    mapOf(
                        altA to
                            mapOf(
                                milk to priceCase(altA, milk, "money-cad-a-milk", 400L),
                                eggs to mixedEggs
                            ),
                        altB to mapOf(milk to usdBMilk, eggs to usdBEggs)
                    )
            )
        assertTrue(scenario.preconditions.satisfied)
        assertEquals(
            listOf(altA, altB),
            scenario.preconditions.priceAndCurrentnessReadyAlternativeStoreKeys
        )
        assertEquals(Money(2_000L, "CAD"), requireNotNull(scenario.baselineAssembly.candidate).knownBasketCost)

        val result =
            StapleWatchAlternativeEconomicInputAssembler.assemble(
                scenario.preconditions,
                scenario.baselineAssembly
            )

        assertEquals(
            listOf(
                StapleWatchAlternativeEconomicInputBlocker.MIXED_MONEY_SPEC,
                StapleWatchAlternativeEconomicInputBlocker.MONEY_SPEC_DIFFERS_FROM_BASELINE
            ),
            result.outcomes.map { outcome -> outcome.blocker }
        )
        assertTrue(result.assembledCandidates.isEmpty())
    }

    @Test
    fun alternativeBasketOverflowFailsClosedWithoutCandidate() {
        val individuallySafe = Long.MAX_VALUE / 2L + 10_000L
        val scenario =
            scenario(
                alternativeStoreKeys = listOf(altA),
                alternativeCases =
                    mapOf(
                        altA to
                            mapOf(
                                milk to
                                    priceCase(
                                        altA,
                                        milk,
                                        "overflow-a-milk",
                                        individuallySafe
                                    ),
                                eggs to
                                    priceCase(
                                        altA,
                                        eggs,
                                        "overflow-a-eggs",
                                        individuallySafe
                                    )
                            )
                    )
            )
        assertTrue(scenario.preconditions.satisfied)
        assertEquals(listOf(altA), scenario.preconditions.priceAndCurrentnessReadyAlternativeStoreKeys)

        val result =
            StapleWatchAlternativeEconomicInputAssembler.assemble(
                scenario.preconditions,
                scenario.baselineAssembly
            )
        val outcome = result.outcomes.single()

        assertFalse(outcome.assembled)
        assertNull(outcome.candidate)
        assertEquals(
            StapleWatchAlternativeEconomicInputBlocker.BASKET_TOTAL_OVERFLOW,
            outcome.blocker
        )
    }

    @Test
    fun blockedExactBaselinePreventsAlternativeCandidateExposure() {
        val scenario =
            scenario(
                alternativeStoreKeys = listOf(altA),
                usualCases =
                    mapOf(
                        milk to priceCase(usual, milk, "blocked-baseline-milk", 1_000L),
                        eggs to null
                    ),
                alternativeCases =
                    mapOf(
                        altA to
                            mapOf(
                                milk to priceCase(altA, milk, "blocked-a-milk", 400L),
                                eggs to priceCase(altA, eggs, "blocked-a-eggs", 500L)
                            )
                    )
            )
        assertFalse(scenario.preconditions.satisfied)
        assertFalse(scenario.baselineAssembly.assembled)

        val result =
            StapleWatchAlternativeEconomicInputAssembler.assemble(
                scenario.preconditions,
                scenario.baselineAssembly
            )

        assertEquals(
            StapleWatchAlternativeEconomicInputAssemblyBlocker.BASELINE_INPUT_NOT_ASSEMBLED,
            result.blocker
        )
        assertTrue(result.outcomes.isEmpty())
        assertTrue(result.assembledCandidates.isEmpty())
        assertSame(scenario.preconditions, result.preconditions)
        assertSame(scenario.baselineAssembly, result.baselineAssembly)
    }

    @Test
    fun boundaryOwnsAssemblyOnlyNotRankingSavingsRoutePolicyOrDelivery() {
        val assemblyConstructors =
            StapleWatchAlternativeEconomicInputAssembly::class.java.declaredConstructors
                .filterNot { constructor -> constructor.isSynthetic }
        val outcomeConstructors =
            StapleWatchAlternativeEconomicInputOutcome::class.java.declaredConstructors
                .filterNot { constructor -> constructor.isSynthetic }
        assertTrue(assemblyConstructors.isNotEmpty())
        assertTrue(outcomeConstructors.isNotEmpty())
        assertTrue(assemblyConstructors.all { constructor -> Modifier.isPrivate(constructor.modifiers) })
        assertTrue(outcomeConstructors.all { constructor -> Modifier.isPrivate(constructor.modifiers) })

        val source = source("StapleWatchAlternativeEconomicInputAssembler.kt").readText()
        assertTrue(source.contains("preconditions.identityFacts.alternativeStoreKeys.map"))
        assertTrue(source.contains("preconditions.additionalTravelFacts.alternatives"))
        assertTrue(source.contains("priceAndCurrentnessReadyAlternativeStoreKeys"))
        assertTrue(source.contains("StapleWatchBasketAlternativeCandidate("))
        assertTrue(source.contains("additionalTravel = travel.additionalTravel"))

        listOf(
            "StapleWatchFactResolutionReadiness",
            "SingleStorePlanCandidate",
            "StapleWatchEconomicEvaluator",
            "StapleWatchPolicy",
            "Math.subtractExact",
            "travelTimeSeconds",
            "distanceMetres",
            "maxAdditionalTravel",
            "sortedBy",
            "compareBy",
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
            assertFalse("Alternative Watch assembler must not own $forbidden", source.contains(forbidden))
        }
    }

    private fun scenario(
        alternativeStoreKeys: Collection<ShoppingStoreKey>,
        alternativeCases: Map<ShoppingStoreKey, Map<ShoppingItemKey, StapleWatchProductionPriceTestFixture.PriceCase?>>,
        usualCases: Map<ShoppingItemKey, StapleWatchProductionPriceTestFixture.PriceCase?> =
            mapOf(
                milk to
                    priceCase(
                        usual,
                        milk,
                        "default-usual-milk",
                        1_000L,
                        observedAtEpochMillis = 5_000L
                    ),
                eggs to
                    priceCase(
                        usual,
                        eggs,
                        "default-usual-eggs",
                        1_000L,
                        observedAtEpochMillis = 5_000L
                    )
            ),
        currentnessEvaluatedAtEpochMillis: Long = fixture.evaluatedAtEpochMillis
    ): Scenario {
        val identityFacts =
            StapleWatchAlternativeStoreIdentityFacts.fromUnordered(
                intent = intent,
                alternativeStoreKeys = alternativeStoreKeys
            )
        val allCases =
            buildList {
                usualCases.values.filterNotNull().forEach(::add)
                alternativeCases.values.forEach { casesByItem ->
                    casesByItem.values.filterNotNull().forEach(::add)
                }
            }
        val registries = fixture.registries(allCases)

        val usualBindings =
            intent.request.itemKeys.mapNotNull { itemKey ->
                usualCases[itemKey]?.let { priceCase -> binding(usual, itemKey, priceCase) }
            }
        val usualRequests =
            intent.request.itemKeys.mapNotNull { itemKey -> usualCases[itemKey]?.request }
        val usualStorePriceFacts =
            StapleWatchUsualStoreBasketPriceFacts.resolve(
                intent = intent,
                store = storeScope(usual),
                priceBindings = usualBindings,
                priceRequests = usualRequests,
                lifecycleRegistry = registries.lifecycle,
                dispositionRegistry = registries.disposition,
                evaluatedAtEpochMillis = fixture.evaluatedAtEpochMillis,
                acceptancePolicy = fixture.acceptancePolicy
            )

        val alternativeBindings =
            identityFacts.alternativeStoreKeys.flatMap { storeKey ->
                intent.request.itemKeys.mapNotNull { itemKey ->
                    alternativeCases[storeKey]?.get(itemKey)?.let { priceCase ->
                        binding(storeKey, itemKey, priceCase)
                    }
                }
            }
        val alternativeRequests =
            alternativeBindings.map { binding ->
                val priceCase =
                    requireNotNull(alternativeCases[binding.storeKey]?.get(binding.itemKey))
                priceCase.request
            }
        val alternativeStorePriceFacts =
            StapleWatchAlternativeStoreBasketPriceFacts.resolve(
                identityFacts = identityFacts,
                stores = identityFacts.alternativeStoreKeys.map(::storeScope),
                priceBindings = alternativeBindings,
                priceRequests = alternativeRequests,
                lifecycleRegistry = registries.lifecycle,
                dispositionRegistry = registries.disposition,
                evaluatedAtEpochMillis = fixture.evaluatedAtEpochMillis,
                acceptancePolicy = fixture.acceptancePolicy
            )
        val additionalTravelFacts =
            StapleWatchAlternativeAdditionalTravelFacts.fromUnordered(
                identityFacts = identityFacts,
                additionalTravelByStore =
                    identityFacts.alternativeStoreKeys.associateWith(::travel)
            )
        val currentnessFacts =
            StapleWatchEvidenceCurrentnessFacts.resolve(
                usualStorePriceFacts = usualStorePriceFacts,
                alternativeStorePriceFacts = alternativeStorePriceFacts,
                lifecycleRegistry = registries.lifecycle,
                dispositionRegistry = registries.disposition,
                evaluatedAtEpochMillis = currentnessEvaluatedAtEpochMillis,
                acceptancePolicy = fixture.acceptancePolicy
            )
        val preconditions =
            StapleWatchEconomicEvidencePreconditions.evaluate(
                identityFacts = identityFacts,
                usualStorePriceFacts = usualStorePriceFacts,
                alternativeStorePriceFacts = alternativeStorePriceFacts,
                additionalTravelFacts = additionalTravelFacts,
                currentnessFacts = currentnessFacts
            )
        val baselineAssembly =
            StapleWatchUsualStoreEconomicInputAssembler.assemble(preconditions)

        return Scenario(
            preconditions = preconditions,
            baselineAssembly = baselineAssembly
        )
    }

    private fun priceCase(
        storeKey: ShoppingStoreKey,
        itemKey: ShoppingItemKey,
        id: String,
        priceMinor: Long,
        observedAtEpochMillis: Long = 4_500L
    ): StapleWatchProductionPriceTestFixture.PriceCase =
        fixture.case(
            requestId = id,
            providerItemId = "${storeKey.value}-${itemKey.value}-product-$id",
            merchantKey = merchantKey(storeKey),
            locationKey = locationKey(storeKey),
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
        return priceCase.copy(request = priceCase.request.copy(record = rewrittenRecord))
    }

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

    private fun merchantKey(storeKey: ShoppingStoreKey): String = "merchant-${storeKey.value}"

    private fun locationKey(storeKey: ShoppingStoreKey): String = "location-${storeKey.value}"

    private fun travel(storeKey: ShoppingStoreKey): ShoppingTravel =
        when (storeKey) {
            altA -> ShoppingTravel(distanceMetres = 1_000L, travelTimeSeconds = 120L)
            altB -> ShoppingTravel(distanceMetres = 2_000L, travelTimeSeconds = 240L)
            else -> error("Unexpected alternative store ${storeKey.value}")
        }

    private fun source(fileName: String): File =
        File(
            System.getProperty("user.dir"),
            "src/main/java/com/valuepilot/app/$fileName"
        )

    private data class Scenario(
        val preconditions: StapleWatchEconomicEvidencePreconditions,
        val baselineAssembly: StapleWatchUsualStoreEconomicInputAssembly
    )
}
