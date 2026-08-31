package com.valuepilot.app

import com.valuepilot.core.Money
import com.valuepilot.core.PracticalShoppingProductionPriceBinding
import com.valuepilot.core.PracticalShoppingProductionPriceStoreScope
import com.valuepilot.core.ShoppingItemKey
import com.valuepilot.core.ShoppingRequest
import com.valuepilot.core.ShoppingStoreKey
import com.valuepilot.core.StapleWatchPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class StapleWatchPolicySetupCompositionCoordinatorTest {

    private val fixture = StapleWatchProductionPriceTestFixture()
    private val milk = ShoppingItemKey("milk")
    private val eggs = ShoppingItemKey("eggs")
    private val usual = ShoppingStoreKey("policy-composition-usual")
    private val intent =
        StapleWatchFactCheckIntent(
            request = ShoppingRequest(listOf(milk, eggs)),
            usualStoreKey = usual
        )

    @Test
    fun visibleRouteWaitsForResolvedEvidenceBeforeCreatingDraftSession() {
        val rendered = mutableListOf<StapleWatchPolicyDraftUiState>()
        val createdSpecs = mutableListOf<StapleWatchPolicyBaselineMoneySpec>()
        val sessions = mutableListOf<StapleWatchPolicyDraftRouteSession>()
        val policies = mutableListOf<StapleWatchPolicy>()
        val coordinator = coordinator(rendered, createdSpecs, sessions, policies)

        coordinator.onRouteVisibilityChanged(true)
        coordinator.onSurfaceAction(
            StapleWatchPolicyDraftUiAction.SetMinimumSwitchSavingsMinorUnits(500L)
        )

        assertTrue(createdSpecs.isEmpty())
        assertTrue(rendered.isEmpty())
        assertTrue(policies.isEmpty())

        val preconditions = resolvedPreconditions("visible")
        coordinator.onPreconditions(preconditions)

        assertEquals(1, createdSpecs.size)
        assertSame(preconditions, createdSpecs.single().baselineAssembly.preconditions)
        assertEquals(1, sessions.size)
        assertEquals(1, rendered.size)
        assertEquals(StapleWatchPolicyDraftUiStatus.NEEDS_POLICY_INPUT, rendered.single().status)
        assertEquals("CAD", rendered.single().currencyCode)
    }

    @Test
    fun hiddenResolvedEvidenceIsCachedWithoutCreatingDraftUntilRouteEntry() {
        val rendered = mutableListOf<StapleWatchPolicyDraftUiState>()
        val createdSpecs = mutableListOf<StapleWatchPolicyBaselineMoneySpec>()
        val sessions = mutableListOf<StapleWatchPolicyDraftRouteSession>()
        val policies = mutableListOf<StapleWatchPolicy>()
        val coordinator = coordinator(rendered, createdSpecs, sessions, policies)
        val preconditions = resolvedPreconditions("hidden")

        coordinator.onPreconditions(preconditions)

        assertTrue(createdSpecs.isEmpty())
        assertTrue(sessions.isEmpty())
        assertTrue(rendered.isEmpty())

        coordinator.onRouteVisibilityChanged(true)

        assertEquals(1, createdSpecs.size)
        assertSame(preconditions, createdSpecs.single().baselineAssembly.preconditions)
        assertEquals(1, sessions.size)
        assertEquals(1, rendered.size)
    }

    @Test
    fun exactDuplicateEvidenceObjectIsIdempotentAndPreservesCurrentDraft() {
        val rendered = mutableListOf<StapleWatchPolicyDraftUiState>()
        val createdSpecs = mutableListOf<StapleWatchPolicyBaselineMoneySpec>()
        val sessions = mutableListOf<StapleWatchPolicyDraftRouteSession>()
        val policies = mutableListOf<StapleWatchPolicy>()
        val coordinator = coordinator(rendered, createdSpecs, sessions, policies)
        val preconditions = resolvedPreconditions("duplicate")
        coordinator.onPreconditions(preconditions)
        coordinator.onRouteVisibilityChanged(true)
        coordinator.onSurfaceAction(
            StapleWatchPolicyDraftUiAction.SetMinimumSwitchSavingsMinorUnits(1_234L)
        )
        val renderCount = rendered.size

        coordinator.onPreconditions(preconditions)

        assertEquals(1, createdSpecs.size)
        assertEquals(1, sessions.size)
        assertEquals(renderCount, rendered.size)
        assertEquals(1_234L, rendered.last().minimumSwitchSavingsMinorUnits)
    }

    @Test
    fun newEvidenceObjectClosesOldDraftBeforeStartingFreshExactMoneyDraft() {
        val rendered = mutableListOf<StapleWatchPolicyDraftUiState>()
        val createdSpecs = mutableListOf<StapleWatchPolicyBaselineMoneySpec>()
        val sessions = mutableListOf<StapleWatchPolicyDraftRouteSession>()
        val policies = mutableListOf<StapleWatchPolicy>()
        val coordinator = coordinator(rendered, createdSpecs, sessions, policies)
        val first = resolvedPreconditions("first")
        coordinator.onPreconditions(first)
        coordinator.onRouteVisibilityChanged(true)
        coordinator.onSurfaceAction(
            StapleWatchPolicyDraftUiAction.SetMinimumSwitchSavingsMinorUnits(1_234L)
        )
        val firstSession = sessions.single()

        val replacement =
            resolvedPreconditions(
                prefix = "replacement",
                currencyCode = "BHD",
                fractionDigits = 3
            )
        coordinator.onPreconditions(replacement)

        assertEquals(2, createdSpecs.size)
        assertEquals(2, sessions.size)
        assertNull(firstSession.currentFinalizationOrNull())
        assertSame(replacement, createdSpecs.last().baselineAssembly.preconditions)
        val state = rendered.last()
        assertEquals("BHD", state.currencyCode)
        assertEquals(3, state.currencyFractionDigits)
        assertNull(state.minimumSwitchSavingsMinorUnits)
        assertNull(state.maxAdditionalTravelSeconds)
        assertEquals(StapleWatchPolicyDistanceLimitUiMode.UNANSWERED, state.distanceLimitMode)
        assertNull(state.minimumStapleItemCount)
        assertTrue(policies.isEmpty())
    }

    @Test
    fun blockedReplacementEvidenceClosesOldDraftAndCreatesNoNewSession() {
        val rendered = mutableListOf<StapleWatchPolicyDraftUiState>()
        val createdSpecs = mutableListOf<StapleWatchPolicyBaselineMoneySpec>()
        val sessions = mutableListOf<StapleWatchPolicyDraftRouteSession>()
        val policies = mutableListOf<StapleWatchPolicy>()
        val coordinator = coordinator(rendered, createdSpecs, sessions, policies)
        coordinator.onPreconditions(resolvedPreconditions("resolved"))
        coordinator.onRouteVisibilityChanged(true)
        val oldSession = sessions.single()
        val renderCount = rendered.size

        val blocked = mixedMoneyPreconditions("blocked")
        assertTrue(blocked.satisfied)
        assertNull(StapleWatchPolicyBaselineMoneySpecResolver.resolve(blocked).moneySpec)
        coordinator.onPreconditions(blocked)
        coordinator.onSurfaceAction(
            StapleWatchPolicyDraftUiAction.SetMinimumSwitchSavingsMinorUnits(999L)
        )
        coordinator.onContinueAction(StapleWatchPolicyHandoffUiAction.Request)

        assertNull(oldSession.currentFinalizationOrNull())
        assertEquals(1, createdSpecs.size)
        assertEquals(1, sessions.size)
        assertEquals(renderCount, rendered.size)
        assertTrue(policies.isEmpty())

        coordinator.onRouteVisibilityChanged(false)
        coordinator.onRouteVisibilityChanged(true)
        assertEquals(1, sessions.size)
        assertEquals(renderCount, rendered.size)
    }

    @Test
    fun completeDraftDoesNotEmitUntilExplicitContinueAndThenForwardsExactPolicy() {
        val rendered = mutableListOf<StapleWatchPolicyDraftUiState>()
        val createdSpecs = mutableListOf<StapleWatchPolicyBaselineMoneySpec>()
        val sessions = mutableListOf<StapleWatchPolicyDraftRouteSession>()
        val policies = mutableListOf<StapleWatchPolicy>()
        val coordinator = coordinator(rendered, createdSpecs, sessions, policies)
        coordinator.onPreconditions(
            resolvedPreconditions(
                prefix = "handoff",
                currencyCode = "BHD",
                fractionDigits = 3
            )
        )
        coordinator.onRouteVisibilityChanged(true)

        coordinator.onSurfaceAction(
            StapleWatchPolicyDraftUiAction.SetMinimumSwitchSavingsMinorUnits(1_500L)
        )
        coordinator.onSurfaceAction(
            StapleWatchPolicyDraftUiAction.SetMaxAdditionalTravelSeconds(600L)
        )
        coordinator.onSurfaceAction(
            StapleWatchPolicyDraftUiAction.SetMaxAdditionalDistanceMetres(2_500L)
        )
        coordinator.onSurfaceAction(
            StapleWatchPolicyDraftUiAction.SetMinimumStapleItemCount(2)
        )

        assertEquals(StapleWatchPolicyDraftUiStatus.READY_FOR_POLICY_HANDOFF, rendered.last().status)
        assertTrue(policies.isEmpty())

        coordinator.onContinueAction(StapleWatchPolicyHandoffUiAction.Request)

        assertEquals(1, policies.size)
        val policy = policies.single()
        assertEquals(Money(1_500L, "BHD", 3), policy.minimumSwitchSavings)
        assertEquals(600L, policy.maxAdditionalTravelSeconds)
        assertEquals(2_500L, policy.maxAdditionalDistanceMetres)
        assertEquals(2, policy.minimumStapleItemCount)
    }

    @Test
    fun incompleteOrHiddenExplicitRequestsEmitNoPolicy() {
        val rendered = mutableListOf<StapleWatchPolicyDraftUiState>()
        val createdSpecs = mutableListOf<StapleWatchPolicyBaselineMoneySpec>()
        val sessions = mutableListOf<StapleWatchPolicyDraftRouteSession>()
        val policies = mutableListOf<StapleWatchPolicy>()
        val coordinator = coordinator(rendered, createdSpecs, sessions, policies)
        coordinator.onPreconditions(resolvedPreconditions("visibility"))
        coordinator.onRouteVisibilityChanged(true)
        coordinator.onSurfaceAction(
            StapleWatchPolicyDraftUiAction.SetMinimumSwitchSavingsMinorUnits(500L)
        )

        coordinator.onContinueAction(StapleWatchPolicyHandoffUiAction.Request)
        assertTrue(policies.isEmpty())

        complete(coordinator)
        assertEquals(StapleWatchPolicyDraftUiStatus.READY_FOR_POLICY_HANDOFF, rendered.last().status)
        coordinator.onRouteVisibilityChanged(false)
        coordinator.onContinueAction(StapleWatchPolicyHandoffUiAction.Request)
        assertTrue(policies.isEmpty())

        coordinator.onRouteVisibilityChanged(true)
        coordinator.onContinueAction(StapleWatchPolicyHandoffUiAction.Request)
        assertEquals(1, policies.size)
    }

    @Test
    fun mismatchedFactorySessionCannotHandOffPolicyForCurrentEvidence() {
        val current = resolvedPreconditions("current")
        val stale = resolvedPreconditions("stale")
        val staleSpec = requireNotNull(StapleWatchPolicyBaselineMoneySpecResolver.resolve(stale).moneySpec)
        val rendered = mutableListOf<StapleWatchPolicyDraftUiState>()
        val policies = mutableListOf<StapleWatchPolicy>()
        val coordinator =
            StapleWatchPolicySetupCompositionCoordinator(
                policyObserver = StapleWatchPolicyObserver { policy -> policies += policy },
                sessionFactory = {
                    StapleWatchPolicyDraftRouteSession(
                        moneySpec = staleSpec,
                        presenter =
                            StapleWatchPolicyDraftSurfacePresenter(
                                StapleWatchPolicyDraftSurfaceRenderer { state -> rendered += state }
                            )
                    )
                }
            )
        coordinator.onPreconditions(current)
        coordinator.onRouteVisibilityChanged(true)
        complete(coordinator)

        assertEquals(StapleWatchPolicyDraftUiStatus.READY_FOR_POLICY_HANDOFF, rendered.last().status)
        coordinator.onContinueAction(StapleWatchPolicyHandoffUiAction.Request)

        assertTrue(policies.isEmpty())
    }

    @Test
    fun closeDropsEvidenceMoneySpecAndSessionAndIgnoresFutureInputs() {
        val rendered = mutableListOf<StapleWatchPolicyDraftUiState>()
        val createdSpecs = mutableListOf<StapleWatchPolicyBaselineMoneySpec>()
        val sessions = mutableListOf<StapleWatchPolicyDraftRouteSession>()
        val policies = mutableListOf<StapleWatchPolicy>()
        val coordinator = coordinator(rendered, createdSpecs, sessions, policies)
        coordinator.onPreconditions(resolvedPreconditions("before-close"))
        coordinator.onRouteVisibilityChanged(true)
        val currentSession = sessions.single()
        val renderCount = rendered.size

        coordinator.close()
        coordinator.onPreconditions(resolvedPreconditions("after-close"))
        coordinator.onRouteVisibilityChanged(false)
        coordinator.onRouteVisibilityChanged(true)
        coordinator.onSurfaceAction(
            StapleWatchPolicyDraftUiAction.SetMinimumSwitchSavingsMinorUnits(500L)
        )
        coordinator.onContinueAction(StapleWatchPolicyHandoffUiAction.Request)

        assertTrue(coordinator.isClosed())
        assertNull(currentSession.currentFinalizationOrNull())
        assertEquals(1, sessions.size)
        assertEquals(renderCount, rendered.size)
        assertTrue(policies.isEmpty())
    }

    @Test
    fun compositionOwnsEvidenceLifecycleAndExplicitPolicyHandoffOnly() {
        val source = source().readText()

        assertTrue(source.contains("StapleWatchEconomicEvidencePreconditionsObserver"))
        assertTrue(source.contains("StapleWatchPolicyBaselineMoneySpecResolver.resolve(preconditions).moneySpec"))
        assertTrue(source.contains("session?.close()"))
        assertTrue(source.contains("preconditions === latestPreconditions"))
        assertTrue(
            source.contains(
                "finalization.draft.moneySpec.baselineAssembly.preconditions !== preconditions"
            )
        )
        assertTrue(source.contains("StapleWatchPolicyHandoffUiAction.Request -> requestPolicyHandoff()"))
        assertTrue(source.contains("policyObserver.onPolicy(policy)"))

        listOf(
            "StapleWatchPolicy(",
            "Money(",
            "withMinimumSwitchSavingsMinorUnits",
            "withMaxAdditionalTravelSeconds",
            "StapleWatchPolicyDistanceLimitDraft.",
            "withMinimumStapleItemCount",
            "toLong(",
            "toInt(",
            "parseLong",
            "parseInt",
            "Locale.",
            "Currency.getInstance",
            "StapleWatchEconomicEvaluator",
            "StapleWatchEconomicDecisionCoordinator",
            "StapleWatchForegroundEvaluationCoordinator",
            "StapleWatchForegroundEvaluationInputHost",
            "StapleWatchFactResolutionHost",
            "PracticalShoppingSaved",
            "OpenPrices",
            "OpenStreetMap",
            "Http",
            "URL(",
            "System.currentTimeMillis",
            "SharedPreferences",
            "WorkManager",
            "NotificationManager",
            "MainActivity",
            "android."
        ).forEach { forbidden ->
            assertFalse("Policy setup composition must not own $forbidden", source.contains(forbidden))
        }
    }

    private fun coordinator(
        rendered: MutableList<StapleWatchPolicyDraftUiState>,
        createdSpecs: MutableList<StapleWatchPolicyBaselineMoneySpec>,
        sessions: MutableList<StapleWatchPolicyDraftRouteSession>,
        policies: MutableList<StapleWatchPolicy>
    ): StapleWatchPolicySetupCompositionCoordinator =
        StapleWatchPolicySetupCompositionCoordinator(
            policyObserver = StapleWatchPolicyObserver { policy -> policies += policy },
            sessionFactory = { moneySpec ->
                createdSpecs += moneySpec
                StapleWatchPolicyDraftRouteSession(
                    moneySpec = moneySpec,
                    presenter =
                        StapleWatchPolicyDraftSurfacePresenter(
                            StapleWatchPolicyDraftSurfaceRenderer { state -> rendered += state }
                        )
                ).also { session -> sessions += session }
            }
        )

    private fun complete(coordinator: StapleWatchPolicySetupCompositionCoordinator) {
        coordinator.onSurfaceAction(
            StapleWatchPolicyDraftUiAction.SetMinimumSwitchSavingsMinorUnits(1_000L)
        )
        coordinator.onSurfaceAction(
            StapleWatchPolicyDraftUiAction.SetMaxAdditionalTravelSeconds(600L)
        )
        coordinator.onSurfaceAction(StapleWatchPolicyDraftUiAction.SetDistanceUnlimited)
        coordinator.onSurfaceAction(
            StapleWatchPolicyDraftUiAction.SetMinimumStapleItemCount(2)
        )
    }

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
            merchantKey = "merchant-policy-composition",
            locationKey = "location-policy-composition",
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
            merchantKey = "merchant-policy-composition",
            locationKey = "location-policy-composition",
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
