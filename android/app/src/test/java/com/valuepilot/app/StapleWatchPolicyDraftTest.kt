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

class StapleWatchPolicyDraftTest {

    private val fixture = StapleWatchProductionPriceTestFixture()
    private val milk = ShoppingItemKey("milk")
    private val eggs = ShoppingItemKey("eggs")
    private val usual = ShoppingStoreKey("opaque-policy-draft-usual")
    private val intent =
        StapleWatchFactCheckIntent(
            request = ShoppingRequest(listOf(milk, eggs)),
            usualStoreKey = usual
        )

    @Test
    fun newDraftHasNoEconomicOrStapleDefaultsAndFailsClosedWithEveryRequirement() {
        val draft = StapleWatchPolicyDraft.start(moneySpec())

        val finalization = StapleWatchPolicyDraftFinalizer.finalize(draft)

        assertNull(draft.minimumSwitchSavingsMinorUnits)
        assertNull(draft.maxAdditionalTravelSeconds)
        assertEquals(StapleWatchPolicyDistanceLimitDraft.Unanswered, draft.distanceLimit)
        assertNull(draft.minimumStapleItemCount)
        assertFalse(finalization.finalized)
        assertNull(finalization.policy)
        assertSame(draft, finalization.draft)
        assertEquals(
            listOf(
                StapleWatchPolicyDraftRequirement.MINIMUM_SWITCH_SAVINGS,
                StapleWatchPolicyDraftRequirement.MAX_ADDITIONAL_TRAVEL,
                StapleWatchPolicyDraftRequirement.DISTANCE_LIMIT_CHOICE,
                StapleWatchPolicyDraftRequirement.MINIMUM_STAPLE_ITEM_COUNT
            ),
            finalization.missingRequirements
        )
    }

    @Test
    fun explicitZeroChoicesFinalizeUnderExactThreeDecimalBaselineMoneySpecification() {
        val moneySpec = moneySpec(currencyCode = "BHD", fractionDigits = 3)
        val draft =
            StapleWatchPolicyDraft.start(moneySpec)
                .withMinimumSwitchSavingsMinorUnits(0L)
                .withMaxAdditionalTravelSeconds(0L)
                .withDistanceLimit(StapleWatchPolicyDistanceLimitDraft.AtMostMetres(0L))
                .withMinimumStapleItemCount(2)

        val finalization = StapleWatchPolicyDraftFinalizer.finalize(draft)
        val policy = requireNotNull(finalization.policy)

        assertTrue(finalization.finalized)
        assertTrue(finalization.missingRequirements.isEmpty())
        assertSame(draft, finalization.draft)
        assertEquals(Money(0L, "BHD", 3), policy.minimumSwitchSavings)
        assertEquals(0L, policy.maxAdditionalTravelSeconds)
        assertEquals(0L, policy.maxAdditionalDistanceMetres)
        assertEquals(2, policy.minimumStapleItemCount)
    }

    @Test
    fun explicitUnlimitedDistanceIsCompleteAndDistinctFromUnansweredDistance() {
        val base =
            StapleWatchPolicyDraft.start(moneySpec())
                .withMinimumSwitchSavingsMinorUnits(1_500L)
                .withMaxAdditionalTravelSeconds(600L)
                .withMinimumStapleItemCount(2)

        val unanswered = StapleWatchPolicyDraftFinalizer.finalize(base)
        val unlimitedDraft =
            base.withDistanceLimit(StapleWatchPolicyDistanceLimitDraft.Unlimited)
        val unlimited = StapleWatchPolicyDraftFinalizer.finalize(unlimitedDraft)

        assertFalse(unanswered.finalized)
        assertEquals(
            listOf(StapleWatchPolicyDraftRequirement.DISTANCE_LIMIT_CHOICE),
            unanswered.missingRequirements
        )
        assertTrue(unlimited.finalized)
        assertNull(requireNotNull(unlimited.policy).maxAdditionalDistanceMetres)
    }

    @Test
    fun draftUpdatesAreImmutableAndCannotBackfillEarlierUnansweredState() {
        val initial = StapleWatchPolicyDraft.start(moneySpec())
        val withSavings = initial.withMinimumSwitchSavingsMinorUnits(1_500L)
        val withTime = withSavings.withMaxAdditionalTravelSeconds(900L)

        assertNull(initial.minimumSwitchSavingsMinorUnits)
        assertNull(initial.maxAdditionalTravelSeconds)
        assertEquals(1_500L, withSavings.minimumSwitchSavingsMinorUnits)
        assertNull(withSavings.maxAdditionalTravelSeconds)
        assertEquals(1_500L, withTime.minimumSwitchSavingsMinorUnits)
        assertEquals(900L, withTime.maxAdditionalTravelSeconds)
    }

    @Test
    fun negativeCapsAndUnsupportedStapleCountsAreRejectedBeforeFinalization() {
        val draft = StapleWatchPolicyDraft.start(moneySpec())

        expectIllegalArgument { draft.withMinimumSwitchSavingsMinorUnits(-1L) }
        expectIllegalArgument { draft.withMaxAdditionalTravelSeconds(-1L) }
        expectIllegalArgument { StapleWatchPolicyDistanceLimitDraft.AtMostMetres(-1L) }
        expectIllegalArgument { draft.withMinimumStapleItemCount(1) }
        expectIllegalArgument { draft.withMinimumStapleItemCount(129) }
    }

    @Test
    fun draftOwnsExplicitPolicyAssemblyButNoUiFactProviderPersistenceOrDeliveryAuthority() {
        val draftConstructors =
            StapleWatchPolicyDraft::class.java.declaredConstructors
                .filterNot { constructor -> constructor.isSynthetic }
        val finalizationConstructors =
            StapleWatchPolicyDraftFinalization::class.java.declaredConstructors
                .filterNot { constructor -> constructor.isSynthetic }
        assertTrue(draftConstructors.isNotEmpty())
        assertTrue(finalizationConstructors.isNotEmpty())
        assertTrue(draftConstructors.all { constructor -> Modifier.isPrivate(constructor.modifiers) })
        assertTrue(finalizationConstructors.all { constructor -> Modifier.isPrivate(constructor.modifiers) })

        val source = source().readText()
        assertTrue(source.contains("StapleWatchPolicyBaselineMoneySpec"))
        assertTrue(source.contains("StapleWatchPolicyDistanceLimitDraft.Unanswered"))
        assertTrue(source.contains("StapleWatchPolicyDistanceLimitDraft.Unlimited"))
        assertTrue(source.contains("Money("))
        assertTrue(source.contains("StapleWatchPolicy("))
        assertTrue(source.contains("currencyCode = draft.moneySpec.currencyCode"))
        assertTrue(source.contains("fractionDigits = draft.moneySpec.fractionDigits"))

        listOf(
            "StapleWatchEconomicEvaluator",
            "StapleWatchEconomicDecisionCoordinator",
            "StapleWatchForegroundEvaluationCoordinator",
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
            "renderer.render",
            "MainActivity",
            "android."
        ).forEach { forbidden ->
            assertFalse("Staple-watch policy draft must not own $forbidden", source.contains(forbidden))
        }
    }

    private fun moneySpec(
        currencyCode: String = "CAD",
        fractionDigits: Int = 2
    ): StapleWatchPolicyBaselineMoneySpec {
        val milkPrice =
            withMoneySpec(
                priceCase("draft-milk-$currencyCode-$fractionDigits", milk, 500L),
                currencyCode = currencyCode,
                fractionDigits = fractionDigits
            )
        val eggsPrice =
            withMoneySpec(
                priceCase("draft-eggs-$currencyCode-$fractionDigits", eggs, 700L),
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
        val requests = intent.request.itemKeys.map { itemKey -> requireNotNull(casesByItem[itemKey]).request }
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
            merchantKey = "merchant-policy-draft",
            locationKey = "location-policy-draft",
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
            merchantKey = "merchant-policy-draft",
            locationKey = "location-policy-draft",
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

    private fun expectIllegalArgument(block: () -> Unit) {
        try {
            block()
            throw AssertionError("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }

    private fun source(): File =
        File(
            System.getProperty("user.dir"),
            "src/main/java/com/valuepilot/app/StapleWatchPolicyDraft.kt"
        )
}
