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

class StapleWatchPolicyDraftPresentationTest {

    private val fixture = StapleWatchProductionPriceTestFixture()
    private val milk = ShoppingItemKey("milk")
    private val eggs = ShoppingItemKey("eggs")
    private val usual = ShoppingStoreKey("policy-presentation-usual")
    private val intent =
        StapleWatchFactCheckIntent(
            request = ShoppingRequest(listOf(milk, eggs)),
            usualStoreKey = usual
        )

    @Test
    fun initialDraftProjectsEveryMissingChoiceWithoutHiddenDefaultsOrContinuation() {
        val finalization =
            StapleWatchPolicyDraftFinalizer.finalize(
                StapleWatchPolicyDraft.start(moneySpec())
            )

        val state = StapleWatchPolicyDraftUiProjector.project(finalization)

        assertEquals(StapleWatchPolicyDraftUiStatus.NEEDS_POLICY_INPUT, state.status)
        assertEquals("CAD", state.currencyCode)
        assertEquals(2, state.currencyFractionDigits)
        assertEquals("CAD", state.minimumSwitchSavingsUnitLabel)
        assertNull(state.minimumSwitchSavingsMinorUnits)
        assertNull(state.maxAdditionalTravelSeconds)
        assertEquals(StapleWatchPolicyDistanceLimitUiMode.UNANSWERED, state.distanceLimitMode)
        assertNull(state.maxAdditionalDistanceMetres)
        assertNull(state.minimumStapleItemCount)
        assertEquals(
            listOf(
                StapleWatchPolicyDraftRequirement.MINIMUM_SWITCH_SAVINGS,
                StapleWatchPolicyDraftRequirement.MAX_ADDITIONAL_TRAVEL,
                StapleWatchPolicyDraftRequirement.DISTANCE_LIMIT_CHOICE,
                StapleWatchPolicyDraftRequirement.MINIMUM_STAPLE_ITEM_COUNT
            ),
            state.missingRequirements
        )
        assertEquals("Complete all switch preferences to continue.", state.notice)
        assertNull(state.continueAction)
        assertNull(state.continueActionLabel)
    }

    @Test
    fun completeExplicitDraftPreservesThreeDecimalMoneyAndZeroChoicesWithoutPolicyPayload() {
        val draft =
            StapleWatchPolicyDraft.start(moneySpec(currencyCode = "BHD", fractionDigits = 3))
                .withMinimumSwitchSavingsMinorUnits(0L)
                .withMaxAdditionalTravelSeconds(0L)
                .withDistanceLimit(StapleWatchPolicyDistanceLimitDraft.AtMostMetres(0L))
                .withMinimumStapleItemCount(2)
        val finalization = StapleWatchPolicyDraftFinalizer.finalize(draft)
        assertTrue(finalization.finalized)

        val state = StapleWatchPolicyDraftUiProjector.project(finalization)

        assertEquals(StapleWatchPolicyDraftUiStatus.READY_FOR_POLICY_HANDOFF, state.status)
        assertEquals("BHD", state.currencyCode)
        assertEquals(3, state.currencyFractionDigits)
        assertEquals("BHD", state.minimumSwitchSavingsUnitLabel)
        assertEquals(0L, state.minimumSwitchSavingsMinorUnits)
        assertEquals(0L, state.maxAdditionalTravelSeconds)
        assertEquals(StapleWatchPolicyDistanceLimitUiMode.AT_MOST_METRES, state.distanceLimitMode)
        assertEquals(0L, state.maxAdditionalDistanceMetres)
        assertEquals(2, state.minimumStapleItemCount)
        assertTrue(state.missingRequirements.isEmpty())
        assertNull(state.notice)
        assertSame(StapleWatchPolicyHandoffUiAction.Request, state.continueAction)
        assertEquals("Continue", state.continueActionLabel)
    }

    @Test
    fun unlimitedDistanceIsExplicitlyDifferentFromUnansweredAndNeedsNoMetreValue() {
        val base =
            StapleWatchPolicyDraft.start(moneySpec())
                .withMinimumSwitchSavingsMinorUnits(1_500L)
                .withMaxAdditionalTravelSeconds(600L)
                .withMinimumStapleItemCount(2)

        val unanswered =
            StapleWatchPolicyDraftUiProjector.project(
                StapleWatchPolicyDraftFinalizer.finalize(base)
            )
        val unlimited =
            StapleWatchPolicyDraftUiProjector.project(
                StapleWatchPolicyDraftFinalizer.finalize(
                    base.withDistanceLimit(StapleWatchPolicyDistanceLimitDraft.Unlimited)
                )
            )

        assertEquals(StapleWatchPolicyDistanceLimitUiMode.UNANSWERED, unanswered.distanceLimitMode)
        assertEquals(
            listOf(StapleWatchPolicyDraftRequirement.DISTANCE_LIMIT_CHOICE),
            unanswered.missingRequirements
        )
        assertNull(unanswered.maxAdditionalDistanceMetres)
        assertNull(unanswered.continueAction)

        assertEquals(StapleWatchPolicyDistanceLimitUiMode.UNLIMITED, unlimited.distanceLimitMode)
        assertTrue(unlimited.missingRequirements.isEmpty())
        assertNull(unlimited.maxAdditionalDistanceMetres)
        assertSame(StapleWatchPolicyHandoffUiAction.Request, unlimited.continueAction)
    }

    @Test
    fun typedEditActionsCarryOnlyAlreadyParsedExactValues() {
        assertEquals(
            1_234L,
            StapleWatchPolicyDraftUiAction.SetMinimumSwitchSavingsMinorUnits(1_234L).minorUnits
        )
        assertEquals(
            900L,
            StapleWatchPolicyDraftUiAction.SetMaxAdditionalTravelSeconds(900L).seconds
        )
        assertSame(
            StapleWatchPolicyDraftUiAction.SetDistanceUnlimited,
            StapleWatchPolicyDraftUiAction.SetDistanceUnlimited
        )
        assertEquals(
            4_321L,
            StapleWatchPolicyDraftUiAction.SetMaxAdditionalDistanceMetres(4_321L).metres
        )
        assertEquals(
            3,
            StapleWatchPolicyDraftUiAction.SetMinimumStapleItemCount(3).count
        )
    }

    @Test
    fun presenterForwardsOnlyProjectedConsumerState() {
        val finalization =
            StapleWatchPolicyDraftFinalizer.finalize(
                StapleWatchPolicyDraft.start(moneySpec())
            )
        var rendered: StapleWatchPolicyDraftUiState? = null
        val presenter =
            StapleWatchPolicyDraftSurfacePresenter(
                StapleWatchPolicyDraftSurfaceRenderer { state -> rendered = state }
            )

        presenter.render(finalization)

        assertEquals(StapleWatchPolicyDraftUiProjector.project(finalization), rendered)
    }

    @Test
    fun presentationReadsFinalizationButOwnsNoFinalizerPolicyEconomicsParsingOrPlatformAuthority() {
        val source = source().readText()

        assertTrue(source.contains("finalization.draft"))
        assertTrue(source.contains("finalization.missingRequirements"))
        assertTrue(source.contains("finalization.finalized"))
        assertTrue(source.contains("StapleWatchPolicyHandoffUiAction.Request"))
        assertTrue(source.contains("StapleWatchPolicyDraftSurfaceRenderer"))
        assertTrue(source.contains("minorUnits: Long"))
        assertTrue(source.contains("seconds: Long"))
        assertTrue(source.contains("metres: Long"))

        listOf(
            "StapleWatchPolicyDraftFinalizer",
            "finalization.policy",
            "StapleWatchPolicy(",
            "Money(",
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
            "Locale.",
            "Currency.getInstance",
            "SharedPreferences",
            "WorkManager",
            "NotificationManager",
            "MainActivity",
            "android."
        ).forEach { forbidden ->
            assertFalse("Staple-watch policy presentation must not own $forbidden", source.contains(forbidden))
        }
    }

    private fun moneySpec(
        currencyCode: String = "CAD",
        fractionDigits: Int = 2
    ): StapleWatchPolicyBaselineMoneySpec {
        val milkPrice =
            withMoneySpec(
                priceCase("presentation-milk-$fractionDigits", milk, 500L),
                currencyCode = currencyCode,
                fractionDigits = fractionDigits
            )
        val eggsPrice =
            withMoneySpec(
                priceCase("presentation-eggs-$fractionDigits", eggs, 700L),
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
            merchantKey = "merchant-policy-presentation",
            locationKey = "location-policy-presentation",
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
            merchantKey = "merchant-policy-presentation",
            locationKey = "location-policy-presentation",
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
            "src/main/java/com/valuepilot/app/StapleWatchPolicyDraftPresentation.kt"
        )
}
