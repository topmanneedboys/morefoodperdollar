package com.valuepilot.app

import com.valuepilot.core.Money
import com.valuepilot.core.PracticalShoppingProductionPriceBinding
import com.valuepilot.core.PracticalShoppingProductionPriceStoreScope
import com.valuepilot.core.ShoppingItemKey
import com.valuepilot.core.ShoppingRequest
import com.valuepilot.core.ShoppingStoreKey
import com.valuepilot.core.ShoppingTravel
import com.valuepilot.core.StapleWatchEconomicEvaluator
import com.valuepilot.core.StapleWatchEconomicStatus
import com.valuepilot.core.StapleWatchPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.lang.reflect.Modifier

class StapleWatchEconomicDecisionCoordinatorTest {

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
    fun delegatesExactAssembledCandidatesAndReturnsSharedCoreDecision() {
        val scenario =
            scenario(
                alternativeStoreKeys = listOf(altB, altA),
                alternativeCases =
                    mapOf(
                        altA to
                            mapOf(
                                milk to priceCase(altA, milk, "delegate-a-milk", 400L),
                                eggs to priceCase(altA, eggs, "delegate-a-eggs", 500L)
                            ),
                        altB to
                            mapOf(
                                milk to priceCase(altB, milk, "delegate-b-milk", 300L),
                                eggs to priceCase(altB, eggs, "delegate-b-eggs", 400L)
                            )
                    )
            )
        val policy = policy(minimumSavingsMinor = 100L)
        val baseline = requireNotNull(scenario.baselineAssembly.candidate)
        assertEquals(listOf(altA, altB), scenario.alternativeAssembly.outcomes.map { it.storeKey })
        assertEquals(2, scenario.alternativeAssembly.assembledCandidates.size)

        val expected =
            StapleWatchEconomicEvaluator.evaluate(
                request = intent.request,
                baseline = baseline,
                alternatives = scenario.alternativeAssembly.assembledCandidates,
                policy = policy
            )
        val result =
            StapleWatchEconomicDecisionCoordinator.evaluate(
                baselineAssembly = scenario.baselineAssembly,
                alternativeAssembly = scenario.alternativeAssembly,
                policy = policy
            )
        val decision = requireNotNull(result.decision)

        assertTrue(result.evaluated)
        assertNull(result.blocker)
        assertSame(scenario.baselineAssembly, result.baselineAssembly)
        assertSame(scenario.alternativeAssembly, result.alternativeAssembly)
        assertSame(policy, result.policy)
        assertEquals(expected, decision)
        assertSame(baseline, decision.baseline)
        assertEquals(StapleWatchEconomicStatus.SWITCH_WORTHWHILE, decision.status)
        assertEquals(Money(1_300L, "CAD"), decision.switchSavings)
        val altBCandidate =
            scenario.alternativeAssembly.assembledCandidates.single { candidate ->
                candidate.basket.storeKey == altB
            }
        assertSame(altBCandidate, decision.recommendedAlternative)
    }

    @Test
    fun blockedStoreOutcomesRemainDiagnosticsWhileOnlyUsableCandidatesReachCore() {
        val scenario =
            scenario(
                alternativeStoreKeys = listOf(altA, altB),
                alternativeCases =
                    mapOf(
                        altA to
                            mapOf(
                                milk to priceCase(altA, milk, "diagnostic-a-milk", 400L),
                                eggs to priceCase(altA, eggs, "diagnostic-a-eggs", 500L)
                            ),
                        altB to
                            mapOf(
                                milk to priceCase(altB, milk, "diagnostic-b-milk", 300L),
                                eggs to null
                            )
                    )
            )
        val policy = policy(minimumSavingsMinor = 1_200L)
        assertEquals(1, scenario.alternativeAssembly.assembledCandidates.size)
        assertEquals(
            StapleWatchAlternativeEconomicInputBlocker.PRICE_COVERAGE_INCOMPLETE,
            scenario.alternativeAssembly.outcomes.single { it.storeKey == altB }.blocker
        )

        val expected =
            StapleWatchEconomicEvaluator.evaluate(
                request = intent.request,
                baseline = requireNotNull(scenario.baselineAssembly.candidate),
                alternatives = scenario.alternativeAssembly.assembledCandidates,
                policy = policy
            )
        val result =
            StapleWatchEconomicDecisionCoordinator.evaluate(
                scenario.baselineAssembly,
                scenario.alternativeAssembly,
                policy
            )
        val decision = requireNotNull(result.decision)

        assertEquals(expected, decision)
        assertEquals(StapleWatchEconomicStatus.NOT_WORTH_SWITCHING, decision.status)
        assertNull(decision.recommendedAlternative)
        assertNull(decision.switchSavings)
        assertSame(scenario.alternativeAssembly, result.alternativeAssembly)
        assertEquals(
            StapleWatchAlternativeEconomicInputBlocker.PRICE_COVERAGE_INCOMPLETE,
            result.alternativeAssembly.outcomes.single { it.storeKey == altB }.blocker
        )
    }

    @Test
    fun policyMoneySpecificationMismatchBlocksBeforeCoreDecision() {
        val scenario =
            scenario(
                alternativeStoreKeys = listOf(altA),
                alternativeCases =
                    mapOf(
                        altA to
                            mapOf(
                                milk to priceCase(altA, milk, "policy-a-milk", 400L),
                                eggs to priceCase(altA, eggs, "policy-a-eggs", 500L)
                            )
                    )
            )
        val policy =
            StapleWatchPolicy(
                minimumSwitchSavings = Money(100L, "USD"),
                maxAdditionalTravelSeconds = 1_000L,
                maxAdditionalDistanceMetres = 5_000L
            )

        val result =
            StapleWatchEconomicDecisionCoordinator.evaluate(
                scenario.baselineAssembly,
                scenario.alternativeAssembly,
                policy
            )

        assertFalse(result.evaluated)
        assertNull(result.decision)
        assertEquals(
            StapleWatchEconomicDecisionCoordinationBlocker.POLICY_MONEY_SPEC_DIFFERS_FROM_BASELINE,
            result.blocker
        )
        assertSame(scenario.baselineAssembly, result.baselineAssembly)
        assertSame(scenario.alternativeAssembly, result.alternativeAssembly)
        assertSame(policy, result.policy)
    }

    @Test
    fun blockedBaselineStopsBeforeEconomicEvaluation() {
        val scenario =
            scenario(
                alternativeStoreKeys = listOf(altA),
                usualCases =
                    mapOf(
                        milk to priceCase(usual, milk, "blocked-usual-milk", 1_000L),
                        eggs to null
                    ),
                alternativeCases =
                    mapOf(
                        altA to
                            mapOf(
                                milk to priceCase(altA, milk, "blocked-alt-milk", 400L),
                                eggs to priceCase(altA, eggs, "blocked-alt-eggs", 500L)
                            )
                    )
            )
        assertFalse(scenario.baselineAssembly.assembled)
        assertEquals(
            StapleWatchAlternativeEconomicInputAssemblyBlocker.BASELINE_INPUT_NOT_ASSEMBLED,
            scenario.alternativeAssembly.blocker
        )

        val result =
            StapleWatchEconomicDecisionCoordinator.evaluate(
                scenario.baselineAssembly,
                scenario.alternativeAssembly,
                policy()
            )

        assertFalse(result.evaluated)
        assertNull(result.decision)
        assertEquals(
            StapleWatchEconomicDecisionCoordinationBlocker.BASELINE_INPUT_NOT_ASSEMBLED,
            result.blocker
        )
        assertTrue(result.alternativeAssembly.assembledCandidates.isEmpty())
    }

    @Test
    fun detachedAlternativeAssemblyCannotBePairedWithAnotherBaseline() {
        val first =
            scenario(
                alternativeStoreKeys = listOf(altA),
                alternativeCases =
                    mapOf(
                        altA to
                            mapOf(
                                milk to priceCase(altA, milk, "first-a-milk", 400L),
                                eggs to priceCase(altA, eggs, "first-a-eggs", 500L)
                            )
                    )
            )
        val second =
            scenario(
                alternativeStoreKeys = listOf(altA),
                alternativeCases =
                    mapOf(
                        altA to
                            mapOf(
                                milk to priceCase(altA, milk, "second-a-milk", 400L),
                                eggs to priceCase(altA, eggs, "second-a-eggs", 500L)
                            )
                    )
            )

        assertThrows(IllegalArgumentException::class.java) {
            StapleWatchEconomicDecisionCoordinator.evaluate(
                baselineAssembly = first.baselineAssembly,
                alternativeAssembly = second.alternativeAssembly,
                policy = policy()
            )
        }
    }

    @Test
    fun boundaryDelegatesEconomicsOnlyWithoutReimplementingRankingTravelOrDelivery() {
        val constructors =
            StapleWatchEconomicDecisionCoordination::class.java.declaredConstructors
                .filterNot { constructor -> constructor.isSynthetic }
        assertTrue(constructors.isNotEmpty())
        assertTrue(constructors.all { constructor -> Modifier.isPrivate(constructor.modifiers) })
        assertFalse(
            StapleWatchEconomicDecisionCoordination::class.java.methods.any { method ->
                method.name == "copy" || method.name.startsWith("copy$")
            }
        )

        val source = source("StapleWatchEconomicDecisionCoordinator.kt").readText()
        assertTrue(source.contains("StapleWatchEconomicEvaluator.evaluate("))
        assertTrue(source.contains("alternatives = alternativeAssembly.assembledCandidates"))
        assertTrue(source.contains("request = baselineAssembly.preconditions.intent.request"))
        assertTrue(source.contains("decision = decision"))

        listOf(
            "StapleWatchFactResolutionReadiness",
            "SingleStorePlanCandidate",
            "Math.addExact",
            "Math.subtractExact",
            ".minorUnits",
            "sortedBy",
            "sortedWith",
            "compareBy",
            "travelTimeSeconds",
            "distanceMetres",
            "maxAdditionalTravelSeconds",
            "maxAdditionalDistanceMetres",
            "StapleWatchEvidenceCurrentnessFacts",
            "PracticalShoppingProduction",
            "OpenPrices",
            "OpenStreetMap",
            "Http",
            "URL(",
            "System.currentTimeMillis",
            "SharedPreferences",
            "WorkManager",
            "NotificationManager",
            "StapleWatchUi",
            "android."
        ).forEach { forbidden ->
            assertFalse("Watch economic coordinator must not own $forbidden", source.contains(forbidden))
        }
    }

    private fun scenario(
        alternativeStoreKeys: Collection<ShoppingStoreKey>,
        alternativeCases: Map<ShoppingStoreKey, Map<ShoppingItemKey, StapleWatchProductionPriceTestFixture.PriceCase?>>,
        usualCases: Map<ShoppingItemKey, StapleWatchProductionPriceTestFixture.PriceCase?> =
            mapOf(
                milk to priceCase(usual, milk, "default-usual-milk", 1_000L),
                eggs to priceCase(usual, eggs, "default-usual-eggs", 1_000L)
            )
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
        val usualStorePriceFacts =
            StapleWatchUsualStoreBasketPriceFacts.resolve(
                intent = intent,
                store = storeScope(usual),
                priceBindings = usualBindings,
                priceRequests = intent.request.itemKeys.mapNotNull { itemKey -> usualCases[itemKey]?.request },
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
                requireNotNull(alternativeCases[binding.storeKey]?.get(binding.itemKey)).request
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
                additionalTravelByStore = identityFacts.alternativeStoreKeys.associateWith(::travel)
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
        val preconditions =
            StapleWatchEconomicEvidencePreconditions.evaluate(
                identityFacts = identityFacts,
                usualStorePriceFacts = usualStorePriceFacts,
                alternativeStorePriceFacts = alternativeStorePriceFacts,
                additionalTravelFacts = additionalTravelFacts,
                currentnessFacts = currentnessFacts
            )
        val baselineAssembly = StapleWatchUsualStoreEconomicInputAssembler.assemble(preconditions)
        val alternativeAssembly =
            StapleWatchAlternativeEconomicInputAssembler.assemble(
                preconditions = preconditions,
                baselineAssembly = baselineAssembly
            )

        return Scenario(
            preconditions = preconditions,
            baselineAssembly = baselineAssembly,
            alternativeAssembly = alternativeAssembly
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

    private fun policy(minimumSavingsMinor: Long = 100L): StapleWatchPolicy =
        StapleWatchPolicy(
            minimumSwitchSavings = Money(minimumSavingsMinor, "CAD"),
            maxAdditionalTravelSeconds = 1_000L,
            maxAdditionalDistanceMetres = 5_000L
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
        val baselineAssembly: StapleWatchUsualStoreEconomicInputAssembly,
        val alternativeAssembly: StapleWatchAlternativeEconomicInputAssembly
    )
}
