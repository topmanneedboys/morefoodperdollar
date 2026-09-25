package com.valuepilot.app

import com.valuepilot.core.Money
import com.valuepilot.core.PracticalShoppingProductionPriceBinding
import com.valuepilot.core.PracticalShoppingProductionPriceStoreScope
import com.valuepilot.core.ShoppingItemKey
import com.valuepilot.core.ShoppingRequest
import com.valuepilot.core.ShoppingStoreKey
import com.valuepilot.core.ShoppingTravel
import com.valuepilot.core.StapleWatchEconomicStatus
import com.valuepilot.core.StapleWatchPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.lang.reflect.Modifier

class StapleWatchForegroundEvaluationCoordinatorTest {

    private val fixture = StapleWatchProductionPriceTestFixture()
    private val milk = ShoppingItemKey("milk")
    private val eggs = ShoppingItemKey("eggs")
    private val usual = ShoppingStoreKey("opaque-usual-store-111111")
    private val alternative = ShoppingStoreKey("opaque-alt-store-222222")
    private val intent =
        StapleWatchFactCheckIntent(
            request = ShoppingRequest(listOf(milk, eggs)),
            usualStoreKey = usual
        )

    @Test
    fun composesExactVerifiedChainAndProjectsNativeConsumerState() {
        val preconditions = preconditions()
        val policy = policy()
        val result =
            StapleWatchForegroundEvaluationCoordinator.evaluate(
                preconditions = preconditions,
                policy = policy,
                metadata = safeMetadata()
            )
        val projection = requireNotNull(result.projection)
        val decision = requireNotNull(result.decisionCoordination.decision)
        val candidate = requireNotNull(projection.state.switchCandidate)

        assertTrue(result.evaluated)
        assertSame(preconditions, result.preconditions)
        assertSame(preconditions, result.baselineAssembly.preconditions)
        assertSame(preconditions, result.alternativeAssembly.preconditions)
        assertSame(result.baselineAssembly, result.alternativeAssembly.baselineAssembly)
        assertSame(result.baselineAssembly, result.decisionCoordination.baselineAssembly)
        assertSame(result.alternativeAssembly, result.decisionCoordination.alternativeAssembly)
        assertSame(policy, result.decisionCoordination.policy)
        assertSame(decision, projection.exactDecision)
        assertEquals(StapleWatchEconomicStatus.SWITCH_WORTHWHILE, decision.status)
        assertEquals(Money(1_100L, "CAD"), decision.switchSavings)
        assertEquals(StapleWatchUiStatus.WORTH_CHECKING, projection.state.status)
        assertEquals("Example Grocer", candidate.storeName)
        assertEquals("Could save 11.00 CAD", candidate.savingsText)
        assertFalse(projection.state.toString().contains(alternative.value))
    }

    @Test
    fun blockedBaselineRetainsEveryTypedDiagnosticAndProducesNoProjection() {
        val preconditions =
            preconditions(
                usualCases =
                    mapOf(
                        milk to priceCase(usual, milk, "blocked-usual-milk", 1_000L),
                        eggs to null
                    )
            )

        val result =
            StapleWatchForegroundEvaluationCoordinator.evaluate(
                preconditions = preconditions,
                policy = policy(),
                metadata = safeMetadata()
            )

        assertFalse(result.evaluated)
        assertNull(result.projection)
        assertEquals(
            StapleWatchEconomicEvidencePreconditionIssue.USUAL_STORE_PRICE_COVERAGE_INCOMPLETE,
            preconditions.issue
        )
        assertEquals(
            StapleWatchUsualStoreEconomicInputBlocker.EVIDENCE_PRECONDITIONS_NOT_SATISFIED,
            result.baselineAssembly.blocker
        )
        assertEquals(
            StapleWatchAlternativeEconomicInputAssemblyBlocker.BASELINE_INPUT_NOT_ASSEMBLED,
            result.alternativeAssembly.blocker
        )
        assertTrue(result.alternativeAssembly.outcomes.isEmpty())
        assertEquals(
            StapleWatchEconomicDecisionCoordinationBlocker.BASELINE_INPUT_NOT_ASSEMBLED,
            result.decisionCoordination.blocker
        )
    }

    @Test
    fun policyMoneyMismatchRemainsEconomicCoordinationBlockerWithoutProjection() {
        val preconditions = preconditions()
        val policy =
            StapleWatchPolicy(
                minimumSwitchSavings = Money(1_000L, "USD"),
                maxAdditionalTravelSeconds = 600L,
                maxAdditionalDistanceMetres = 5_000L
            )

        val result =
            StapleWatchForegroundEvaluationCoordinator.evaluate(
                preconditions = preconditions,
                policy = policy,
                metadata = safeMetadata()
            )

        assertFalse(result.evaluated)
        assertNull(result.projection)
        assertTrue(result.baselineAssembly.assembled)
        assertNull(result.alternativeAssembly.blocker)
        assertEquals(
            StapleWatchEconomicDecisionCoordinationBlocker.POLICY_MONEY_SPEC_DIFFERS_FROM_BASELINE,
            result.decisionCoordination.blocker
        )
        assertSame(policy, result.decisionCoordination.policy)
    }

    @Test
    fun blockedAlternativeOutcomeRemainsDiagnosticWhileEconomicsAndProjectionContinue() {
        val preconditions =
            preconditions(
                alternativeCases =
                    mapOf(
                        milk to priceCase(alternative, milk, "partial-alt-milk", 400L),
                        eggs to null
                    )
            )

        val result =
            StapleWatchForegroundEvaluationCoordinator.evaluate(
                preconditions = preconditions,
                policy = policy(),
                metadata = safeMetadata()
            )
        val projection = requireNotNull(result.projection)
        val outcome = result.alternativeAssembly.outcomes.single()
        val decision = requireNotNull(result.decisionCoordination.decision)

        assertTrue(result.evaluated)
        assertFalse(outcome.assembled)
        assertEquals(
            StapleWatchAlternativeEconomicInputBlocker.PRICE_COVERAGE_INCOMPLETE,
            outcome.blocker
        )
        assertTrue(result.alternativeAssembly.assembledCandidates.isEmpty())
        assertEquals(StapleWatchEconomicStatus.NOT_WORTH_SWITCHING, decision.status)
        assertEquals(StapleWatchUiStatus.NOT_WORTH_SWITCHING, projection.state.status)
        assertNull(projection.state.switchCandidate)
    }

    @Test
    fun unsafeDisplayMetadataFailsClosedInProjectionWithoutRewritingEconomicResult() {
        val result =
            StapleWatchForegroundEvaluationCoordinator.evaluate(
                preconditions = preconditions(),
                policy = policy(),
                metadata = StapleWatchStoreDisplayMetadata()
            )
        val projection = requireNotNull(result.projection)
        val decision = requireNotNull(result.decisionCoordination.decision)

        assertTrue(result.evaluated)
        assertEquals(StapleWatchEconomicStatus.SWITCH_WORTHWHILE, decision.status)
        assertEquals(StapleWatchUiStatus.DISPLAY_METADATA_INCOMPLETE, projection.state.status)
        assertNull(projection.state.switchCandidate)
        assertNull(projection.recommendedStoreKey)
        assertFalse(projection.state.toString().contains("11.00"))
        assertFalse(projection.state.toString().contains(alternative.value))
    }

    @Test
    fun foregroundBoundaryOnlyComposesVerifiedOwnersWithoutAcquisitionOrDeliveryAuthority() {
        val constructors =
            StapleWatchForegroundEvaluation::class.java.declaredConstructors
                .filterNot { constructor -> constructor.isSynthetic }
        assertTrue(constructors.isNotEmpty())
        assertTrue(constructors.all { constructor -> Modifier.isPrivate(constructor.modifiers) })
        assertFalse(
            StapleWatchForegroundEvaluation::class.java.methods.any { method ->
                method.name == "copy" || method.name.startsWith("copy$")
            }
        )

        val source = source("StapleWatchForegroundEvaluationCoordinator.kt").readText()
        assertTrue(source.contains("StapleWatchUsualStoreEconomicInputAssembler.assemble(preconditions)"))
        assertTrue(source.contains("StapleWatchAlternativeEconomicInputAssembler.assemble("))
        assertTrue(source.contains("StapleWatchEconomicDecisionCoordinator.evaluate("))
        assertTrue(source.contains("StapleWatchUiProjector.project("))
        assertTrue(source.contains("decisionCoordination.decision?.let"))

        listOf(
            "StapleWatchFactResolutionReadiness",
            "StapleWatchEconomicEvidencePreconditions.evaluate(",
            "StapleWatchEconomicEvaluator",
            "PracticalShoppingProduction",
            "ProductionCurrentPrice",
            "EvidenceProvider",
            "Math.addExact",
            "Math.subtractExact",
            ".minorUnits",
            "sortedBy",
            "sortedWith",
            "System.currentTimeMillis",
            "SharedPreferences",
            "WorkManager",
            "NotificationManager",
            "StapleWatchSurfacePresenter",
            "renderer.render",
            "OpenPrices",
            "OpenStreetMap",
            "Http",
            "URL(",
            "android."
        ).forEach { forbidden ->
            assertFalse("Watch foreground coordinator must not own $forbidden", source.contains(forbidden))
        }
    }

    private fun preconditions(
        usualCases: Map<ShoppingItemKey, StapleWatchProductionPriceTestFixture.PriceCase?> =
            mapOf(
                milk to priceCase(usual, milk, "usual-milk", 1_000L),
                eggs to priceCase(usual, eggs, "usual-eggs", 1_000L)
            ),
        alternativeCases: Map<ShoppingItemKey, StapleWatchProductionPriceTestFixture.PriceCase?> =
            mapOf(
                milk to priceCase(alternative, milk, "alternative-milk", 400L),
                eggs to priceCase(alternative, eggs, "alternative-eggs", 500L)
            )
    ): StapleWatchEconomicEvidencePreconditions {
        val identityFacts =
            StapleWatchAlternativeStoreIdentityFacts.fromUnordered(
                intent = intent,
                alternativeStoreKeys = listOf(alternative)
            )
        val allCases =
            buildList {
                usualCases.values.filterNotNull().forEach(::add)
                alternativeCases.values.filterNotNull().forEach(::add)
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
                priceRequests =
                    intent.request.itemKeys.mapNotNull { itemKey ->
                        usualCases[itemKey]?.request
                    },
                lifecycleRegistry = registries.lifecycle,
                dispositionRegistry = registries.disposition,
                evaluatedAtEpochMillis = fixture.evaluatedAtEpochMillis,
                acceptancePolicy = fixture.acceptancePolicy
            )

        val alternativeBindings =
            intent.request.itemKeys.mapNotNull { itemKey ->
                alternativeCases[itemKey]?.let { priceCase ->
                    binding(alternative, itemKey, priceCase)
                }
            }
        val alternativeStorePriceFacts =
            StapleWatchAlternativeStoreBasketPriceFacts.resolve(
                identityFacts = identityFacts,
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
        val additionalTravelFacts =
            StapleWatchAlternativeAdditionalTravelFacts.fromUnordered(
                identityFacts = identityFacts,
                additionalTravelByStore =
                    mapOf(
                        alternative to
                            ShoppingTravel(
                                distanceMetres = 2_000L,
                                travelTimeSeconds = 300L
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

    private fun policy(): StapleWatchPolicy =
        StapleWatchPolicy(
            minimumSwitchSavings = Money(1_000L, "CAD"),
            maxAdditionalTravelSeconds = 600L,
            maxAdditionalDistanceMetres = 5_000L
        )

    private fun safeMetadata(): StapleWatchStoreDisplayMetadata =
        StapleWatchStoreDisplayMetadata(
            listOf(
                StapleWatchStoreDisplayMetadataEntry(
                    storeKey = alternative,
                    displayName = "Example Grocer"
                )
            )
        )

    private fun merchantKey(storeKey: ShoppingStoreKey): String =
        "merchant-${storeKey.value}"

    private fun locationKey(storeKey: ShoppingStoreKey): String =
        "location-${storeKey.value}"

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
