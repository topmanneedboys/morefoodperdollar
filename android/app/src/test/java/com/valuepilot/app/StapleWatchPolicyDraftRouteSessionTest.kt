package com.valuepilot.app

import com.valuepilot.core.Money
import com.valuepilot.core.PracticalShoppingProductionPriceBinding
import com.valuepilot.core.PracticalShoppingProductionPriceStoreScope
import com.valuepilot.core.ShoppingItemKey
import com.valuepilot.core.ShoppingRequest
import com.valuepilot.core.ShoppingStoreKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class StapleWatchPolicyDraftRouteSessionTest {

    private val fixture = StapleWatchProductionPriceTestFixture()
    private val milk = ShoppingItemKey("milk")
    private val eggs = ShoppingItemKey("eggs")
    private val usual = ShoppingStoreKey("policy-session-usual")
    private val intent =
        StapleWatchFactCheckIntent(
            request = ShoppingRequest(listOf(milk, eggs)),
            usualStoreKey = usual
        )

    @Test
    fun routeEntryStartsWithEveryPolicyChoiceUnansweredAndRendersOnce() {
        val rendered = mutableListOf<StapleWatchPolicyDraftUiState>()
        val session = session(rendered = rendered)

        assertTrue(rendered.isEmpty())
        assertNull(session.currentFinalizationOrNull())

        session.onRouteVisibilityChanged(true)
        session.onRouteVisibilityChanged(true)

        assertEquals(1, rendered.size)
        val state = rendered.single()
        assertEquals(StapleWatchPolicyDraftUiStatus.NEEDS_POLICY_INPUT, state.status)
        assertEquals("CAD", state.currencyCode)
        assertEquals(2, state.currencyFractionDigits)
        assertNull(state.minimumSwitchSavingsMinorUnits)
        assertNull(state.maxAdditionalTravelSeconds)
        assertEquals(StapleWatchPolicyDistanceLimitUiMode.UNANSWERED, state.distanceLimitMode)
        assertNull(state.maxAdditionalDistanceMetres)
        assertNull(state.minimumStapleItemCount)
        assertNull(session.currentFinalizationOrNull())
    }

    @Test
    fun visibleTypedActionsRemainTemporaryAndCompleteExactThreeDecimalPolicy() {
        val rendered = mutableListOf<StapleWatchPolicyDraftUiState>()
        val session =
            session(
                moneySpec = moneySpec(currencyCode = "BHD", fractionDigits = 3),
                rendered = rendered
            )
        session.onRouteVisibilityChanged(true)

        session.onSurfaceAction(
            StapleWatchPolicyDraftUiAction.SetMinimumSwitchSavingsMinorUnits(0L)
        )
        session.onSurfaceAction(
            StapleWatchPolicyDraftUiAction.SetMaxAdditionalTravelSeconds(0L)
        )
        session.onSurfaceAction(
            StapleWatchPolicyDraftUiAction.SetMaxAdditionalDistanceMetres(0L)
        )
        session.onSurfaceAction(
            StapleWatchPolicyDraftUiAction.SetMinimumStapleItemCount(2)
        )

        assertEquals(5, rendered.size)
        val state = rendered.last()
        assertEquals(StapleWatchPolicyDraftUiStatus.READY_FOR_POLICY_HANDOFF, state.status)
        assertEquals("BHD", state.currencyCode)
        assertEquals(3, state.currencyFractionDigits)
        assertEquals(0L, state.minimumSwitchSavingsMinorUnits)
        assertEquals(0L, state.maxAdditionalTravelSeconds)
        assertEquals(StapleWatchPolicyDistanceLimitUiMode.AT_MOST_METRES, state.distanceLimitMode)
        assertEquals(0L, state.maxAdditionalDistanceMetres)
        assertEquals(2, state.minimumStapleItemCount)

        val finalization = session.currentFinalizationOrNull()
        assertNotNull(finalization)
        assertTrue(requireNotNull(finalization).finalized)
        assertEquals(
            Money(minorUnits = 0L, currencyCode = "BHD", fractionDigits = 3),
            finalization.policy?.minimumSwitchSavings
        )
        assertEquals(0L, finalization.policy?.maxAdditionalTravelSeconds)
        assertEquals(0L, finalization.policy?.maxAdditionalDistanceMetres)
        assertEquals(2, finalization.policy?.minimumStapleItemCount)
    }

    @Test
    fun explicitUnlimitedDistanceCanCompletePolicyWithoutMetreValue() {
        val rendered = mutableListOf<StapleWatchPolicyDraftUiState>()
        val session = session(rendered = rendered)
        session.onRouteVisibilityChanged(true)
        session.onSurfaceAction(
            StapleWatchPolicyDraftUiAction.SetMinimumSwitchSavingsMinorUnits(1_500L)
        )
        session.onSurfaceAction(
            StapleWatchPolicyDraftUiAction.SetMaxAdditionalTravelSeconds(600L)
        )
        session.onSurfaceAction(StapleWatchPolicyDraftUiAction.SetDistanceUnlimited)
        session.onSurfaceAction(
            StapleWatchPolicyDraftUiAction.SetMinimumStapleItemCount(2)
        )

        val state = rendered.last()
        assertEquals(StapleWatchPolicyDraftUiStatus.READY_FOR_POLICY_HANDOFF, state.status)
        assertEquals(StapleWatchPolicyDistanceLimitUiMode.UNLIMITED, state.distanceLimitMode)
        assertNull(state.maxAdditionalDistanceMetres)

        val finalization = requireNotNull(session.currentFinalizationOrNull())
        assertNull(finalization.policy?.maxAdditionalDistanceMetres)
    }

    @Test
    fun hiddenActionsAreIgnoredWhileHideShowPreservesTemporaryDraft() {
        val rendered = mutableListOf<StapleWatchPolicyDraftUiState>()
        val session = session(rendered = rendered)
        session.onRouteVisibilityChanged(true)
        session.onSurfaceAction(
            StapleWatchPolicyDraftUiAction.SetMinimumSwitchSavingsMinorUnits(1_234L)
        )
        session.onRouteVisibilityChanged(false)
        val renderCountWhileHidden = rendered.size

        session.onSurfaceAction(
            StapleWatchPolicyDraftUiAction.SetMaxAdditionalTravelSeconds(900L)
        )

        assertEquals(renderCountWhileHidden, rendered.size)
        assertNull(session.currentFinalizationOrNull())

        session.onRouteVisibilityChanged(true)

        val state = rendered.last()
        assertEquals(renderCountWhileHidden + 1, rendered.size)
        assertEquals(1_234L, state.minimumSwitchSavingsMinorUnits)
        assertNull(state.maxAdditionalTravelSeconds)
    }

    @Test
    fun invalidTypedEditsFailClosedWithoutReplacingCurrentDraft() {
        val rendered = mutableListOf<StapleWatchPolicyDraftUiState>()
        val session = session(rendered = rendered)
        session.onRouteVisibilityChanged(true)

        session.onSurfaceAction(
            StapleWatchPolicyDraftUiAction.SetMinimumSwitchSavingsMinorUnits(-1L)
        )
        session.onSurfaceAction(
            StapleWatchPolicyDraftUiAction.SetMaxAdditionalTravelSeconds(-1L)
        )
        session.onSurfaceAction(
            StapleWatchPolicyDraftUiAction.SetMaxAdditionalDistanceMetres(-1L)
        )
        session.onSurfaceAction(
            StapleWatchPolicyDraftUiAction.SetMinimumStapleItemCount(1)
        )
        session.onSurfaceAction(
            StapleWatchPolicyDraftUiAction.SetMinimumStapleItemCount(129)
        )

        assertEquals(6, rendered.size)
        val state = rendered.last()
        assertEquals(StapleWatchPolicyDraftUiStatus.NEEDS_POLICY_INPUT, state.status)
        assertNull(state.minimumSwitchSavingsMinorUnits)
        assertNull(state.maxAdditionalTravelSeconds)
        assertEquals(StapleWatchPolicyDistanceLimitUiMode.UNANSWERED, state.distanceLimitMode)
        assertNull(state.maxAdditionalDistanceMetres)
        assertNull(state.minimumStapleItemCount)
        assertNull(session.currentFinalizationOrNull())
    }

    @Test
    fun completedFinalizationIsReadableOnlyWhileRouteIsVisible() {
        val rendered = mutableListOf<StapleWatchPolicyDraftUiState>()
        val session = session(rendered = rendered)
        session.onRouteVisibilityChanged(true)
        complete(session)
        assertNotNull(session.currentFinalizationOrNull())

        session.onRouteVisibilityChanged(false)
        assertNull(session.currentFinalizationOrNull())

        session.onRouteVisibilityChanged(true)
        assertNotNull(session.currentFinalizationOrNull())
        assertEquals(StapleWatchPolicyDraftUiStatus.READY_FOR_POLICY_HANDOFF, rendered.last().status)
    }

    @Test
    fun closeDiscardsDraftAndStopsFurtherRouteOrActionRendering() {
        val rendered = mutableListOf<StapleWatchPolicyDraftUiState>()
        val session = session(rendered = rendered)
        session.onRouteVisibilityChanged(true)
        session.onSurfaceAction(
            StapleWatchPolicyDraftUiAction.SetMinimumSwitchSavingsMinorUnits(500L)
        )
        val beforeClose = rendered.size

        session.close()
        session.onSurfaceAction(
            StapleWatchPolicyDraftUiAction.SetMaxAdditionalTravelSeconds(300L)
        )
        session.onRouteVisibilityChanged(false)
        session.onRouteVisibilityChanged(true)

        assertEquals(beforeClose, rendered.size)
        assertNull(session.currentFinalizationOrNull())
    }

    @Test
    fun routeSessionOwnsOnlyTemporaryDraftMappingAndNoParsingEconomicOrPlatformAuthority() {
        val source = source().readText()

        assertTrue(source.contains("StapleWatchPolicyDraft.start(moneySpec)"))
        assertTrue(source.contains("StapleWatchPolicyDraftSurfacePresenter"))
        assertTrue(source.contains("StapleWatchPolicyDraftFinalizer.finalize(current)"))
        assertTrue(source.contains("withMinimumSwitchSavingsMinorUnits(action.minorUnits)"))
        assertTrue(source.contains("withMaxAdditionalTravelSeconds(action.seconds)"))
        assertTrue(source.contains("StapleWatchPolicyDistanceLimitDraft.Unlimited"))
        assertTrue(source.contains("StapleWatchPolicyDistanceLimitDraft.AtMostMetres(action.metres)"))
        assertTrue(source.contains("withMinimumStapleItemCount(action.count)"))
        assertTrue(source.contains("catch (_: IllegalArgumentException)"))
        assertTrue(source.contains("draft = null"))

        listOf(
            "StapleWatchPolicy(",
            "Money(",
            "finalization.policy",
            "StapleWatchPolicyDraftUiStatus",
            "StapleWatchPolicyHandoffUiAction",
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
            "StapleWatchPolicyObserver",
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
            assertFalse("Policy draft route session must not own $forbidden", source.contains(forbidden))
        }
    }

    private fun complete(session: StapleWatchPolicyDraftRouteSession) {
        session.onSurfaceAction(
            StapleWatchPolicyDraftUiAction.SetMinimumSwitchSavingsMinorUnits(1_000L)
        )
        session.onSurfaceAction(
            StapleWatchPolicyDraftUiAction.SetMaxAdditionalTravelSeconds(600L)
        )
        session.onSurfaceAction(StapleWatchPolicyDraftUiAction.SetDistanceUnlimited)
        session.onSurfaceAction(
            StapleWatchPolicyDraftUiAction.SetMinimumStapleItemCount(2)
        )
    }

    private fun session(
        moneySpec: StapleWatchPolicyBaselineMoneySpec = moneySpec(),
        rendered: MutableList<StapleWatchPolicyDraftUiState>
    ): StapleWatchPolicyDraftRouteSession =
        StapleWatchPolicyDraftRouteSession(
            moneySpec = moneySpec,
            presenter =
                StapleWatchPolicyDraftSurfacePresenter(
                    StapleWatchPolicyDraftSurfaceRenderer { state -> rendered += state }
                )
        )

    private fun moneySpec(
        currencyCode: String = "CAD",
        fractionDigits: Int = 2
    ): StapleWatchPolicyBaselineMoneySpec {
        val milkPrice =
            withMoneySpec(
                priceCase("session-milk-$fractionDigits", milk, 500L),
                currencyCode = currencyCode,
                fractionDigits = fractionDigits
            )
        val eggsPrice =
            withMoneySpec(
                priceCase("session-eggs-$fractionDigits", eggs, 700L),
                currencyCode = currencyCode,
                fractionDigits = fractionDigits
            )
        return requireNotNull(
            StapleWatchPolicyBaselineMoneySpecResolver.resolve(
                preconditions(milkPrice, eggsPrice)
            ).moneySpec
        )
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
            intent.request.itemKeys.map { itemKey ->
                requireNotNull(casesByItem[itemKey]).request
            }
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
            merchantKey = "merchant-policy-session",
            locationKey = "location-policy-session",
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
            merchantKey = "merchant-policy-session",
            locationKey = "location-policy-session",
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
            "src/main/java/com/valuepilot/app/StapleWatchPolicyDraftRouteSession.kt"
        )
}
