package com.valuepilot.app

import com.valuepilot.core.Money
import com.valuepilot.core.PracticalShoppingProductionPriceBinding
import com.valuepilot.core.PracticalShoppingProductionPriceStoreScope
import com.valuepilot.core.ShoppingItemKey
import com.valuepilot.core.ShoppingRequest
import com.valuepilot.core.ShoppingStoreKey
import com.valuepilot.core.ShoppingTravel
import com.valuepilot.core.StapleWatchPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.lang.reflect.Modifier

class StapleWatchForegroundEvaluationInputSessionTest {

    private val fixture = StapleWatchProductionPriceTestFixture()
    private val milk = ShoppingItemKey("milk")
    private val eggs = ShoppingItemKey("eggs")
    private val usual = ShoppingStoreKey("opaque-input-usual-111111")
    private val alternative = ShoppingStoreKey("opaque-input-alt-222222")
    private val intent =
        StapleWatchFactCheckIntent(
            request = ShoppingRequest(listOf(milk, eggs)),
            usualStoreKey = usual
        )

    @Test
    fun startRetainsExactPreconditionsAndRequiresBothExplicitInputs() {
        val preconditions = preconditions("start")
        val session = StapleWatchForegroundEvaluationInputSession.start(preconditions)

        assertSame(preconditions, session.preconditions)
        assertNull(session.policy)
        assertNull(session.displayMetadata)
        assertNull(session.evaluation)
        assertFalse(session.readyForEvaluation)
    }

    @Test
    fun explicitInputsCanArriveInEitherOrderAndDelegateExactEvaluation() {
        val preconditions = preconditions("order")
        val policy = policy()
        val metadata = metadata("Fresh Mart")

        val policyFirst = StapleWatchForegroundEvaluationInputSession.start(preconditions).withPolicy(policy)
        assertNull(policyFirst.evaluation)
        val completedPolicyFirst = policyFirst.withDisplayMetadata(metadata)

        assertTrue(completedPolicyFirst.readyForEvaluation)
        assertSame(preconditions, completedPolicyFirst.preconditions)
        assertSame(policy, completedPolicyFirst.policy)
        assertSame(metadata, completedPolicyFirst.displayMetadata)
        val policyFirstEvaluation = requireNotNull(completedPolicyFirst.evaluation)
        val policyFirstProjection = requireNotNull(policyFirstEvaluation.projection)
        assertSame(preconditions, policyFirstEvaluation.preconditions)
        assertSame(policy, policyFirstEvaluation.decisionCoordination.policy)
        assertEquals(StapleWatchUiStatus.WORTH_CHECKING, policyFirstProjection.state.status)
        assertEquals("Fresh Mart", policyFirstProjection.state.switchCandidate?.storeName)

        val metadataFirst =
            StapleWatchForegroundEvaluationInputSession.start(preconditions).withDisplayMetadata(metadata)
        assertNull(metadataFirst.evaluation)
        val completedMetadataFirst = metadataFirst.withPolicy(policy)
        val metadataFirstEvaluation = requireNotNull(completedMetadataFirst.evaluation)
        val metadataFirstProjection = requireNotNull(metadataFirstEvaluation.projection)

        assertTrue(completedMetadataFirst.readyForEvaluation)
        assertSame(preconditions, metadataFirstEvaluation.preconditions)
        assertSame(policy, metadataFirstEvaluation.decisionCoordination.policy)
        assertEquals(policyFirstProjection.state, metadataFirstProjection.state)
    }

    @Test
    fun replacingExplicitMetadataReprojectsWithoutChangingEvidenceOrPolicy() {
        val preconditions = preconditions("metadata")
        val policy = policy()
        val originalMetadata = metadata("Original Market")
        val replacementMetadata = metadata("Updated Market")
        val original =
            StapleWatchForegroundEvaluationInputSession.start(preconditions)
                .withPolicy(policy)
                .withDisplayMetadata(originalMetadata)
        val originalEvaluation = requireNotNull(original.evaluation)
        val originalProjection = requireNotNull(originalEvaluation.projection)

        val replacement = original.withDisplayMetadata(replacementMetadata)
        val replacementEvaluation = requireNotNull(replacement.evaluation)
        val replacementProjection = requireNotNull(replacementEvaluation.projection)

        assertFalse(replacement === original)
        assertSame(preconditions, replacement.preconditions)
        assertSame(policy, replacement.policy)
        assertSame(replacementMetadata, replacement.displayMetadata)
        assertSame(preconditions, replacementEvaluation.preconditions)
        assertSame(policy, replacementEvaluation.decisionCoordination.policy)
        assertEquals("Original Market", originalProjection.state.switchCandidate?.storeName)
        assertEquals("Updated Market", replacementProjection.state.switchCandidate?.storeName)
    }

    @Test
    fun replacingExplicitPolicyReevaluatesEconomicsWithoutChangingEvidenceOrMetadata() {
        val preconditions = preconditions("policy")
        val metadata = metadata("Policy Market")
        val permissive = policy(minimumSavingsMinor = 100L)
        val strict = policy(minimumSavingsMinor = 2_000L)
        val original =
            StapleWatchForegroundEvaluationInputSession.start(preconditions)
                .withDisplayMetadata(metadata)
                .withPolicy(permissive)
        val originalEvaluation = requireNotNull(original.evaluation)
        val originalProjection = requireNotNull(originalEvaluation.projection)
        assertEquals(StapleWatchUiStatus.WORTH_CHECKING, originalProjection.state.status)

        val replacement = original.withPolicy(strict)
        val replacementEvaluation = requireNotNull(replacement.evaluation)
        val replacementProjection = requireNotNull(replacementEvaluation.projection)

        assertFalse(replacement === original)
        assertSame(preconditions, replacement.preconditions)
        assertSame(metadata, replacement.displayMetadata)
        assertSame(strict, replacement.policy)
        assertSame(strict, replacementEvaluation.decisionCoordination.policy)
        assertEquals(StapleWatchUiStatus.NOT_WORTH_SWITCHING, replacementProjection.state.status)
        assertNull(replacementProjection.state.switchCandidate)
    }

    @Test
    fun aNewPreconditionsSessionCarriesNoPolicyMetadataOrEvaluationImplicitly() {
        val firstPreconditions = preconditions("first")
        val first =
            StapleWatchForegroundEvaluationInputSession.start(firstPreconditions)
                .withPolicy(policy())
                .withDisplayMetadata(metadata("First Market"))
        assertTrue(first.readyForEvaluation)
        assertTrue(first.evaluation != null)

        val secondPreconditions = preconditions("second")
        val second = StapleWatchForegroundEvaluationInputSession.start(secondPreconditions)

        assertFalse(secondPreconditions === firstPreconditions)
        assertSame(secondPreconditions, second.preconditions)
        assertNull(second.policy)
        assertNull(second.displayMetadata)
        assertNull(second.evaluation)
        assertFalse(second.readyForEvaluation)
    }

    @Test
    fun sessionOwnsOnlyExplicitForegroundInputComposition() {
        val constructors =
            StapleWatchForegroundEvaluationInputSession::class.java.declaredConstructors
                .filterNot { constructor -> constructor.isSynthetic }
        assertTrue(constructors.isNotEmpty())
        assertTrue(constructors.all { constructor -> Modifier.isPrivate(constructor.modifiers) })
        assertFalse(
            StapleWatchForegroundEvaluationInputSession::class.java.methods.any { method ->
                method.name == "copy" || method.name.startsWith("copy$")
            }
        )

        val source = source("StapleWatchForegroundEvaluationInputSession.kt").readText()
        assertTrue(source.contains("StapleWatchForegroundEvaluationCoordinator.evaluate("))
        assertTrue(source.contains("preconditions = preconditions"))
        assertTrue(source.contains("policy = policy"))
        assertTrue(source.contains("metadata = displayMetadata"))

        listOf(
            "StapleWatchPolicy(",
            "Money(",
            "minimumSwitchSavings =",
            "maxAdditionalTravelSeconds =",
            "StapleWatchStoreDisplayMetadata(",
            "PracticalShoppingProduction",
            "ProductionCurrentPrice",
            "EvidenceProvider",
            "OpenPrices",
            "OpenStreetMap",
            "OpenFoodFacts",
            "System.currentTimeMillis",
            "SharedPreferences",
            "WorkManager",
            "NotificationManager",
            "renderer.render",
            "StapleWatchSurfacePresenter",
            "MainActivity",
            "android."
        ).forEach { forbidden ->
            assertFalse("Foreground input session must not own $forbidden", source.contains(forbidden))
        }
    }

    private fun preconditions(prefix: String): StapleWatchEconomicEvidencePreconditions {
        val identityFacts =
            StapleWatchAlternativeStoreIdentityFacts.fromUnordered(
                intent = intent,
                alternativeStoreKeys = listOf(alternative)
            )
        val usualMilk = priceCase(usual, milk, "$prefix-usual-milk", 1_000L)
        val usualEggs = priceCase(usual, eggs, "$prefix-usual-eggs", 1_000L)
        val altMilk = priceCase(alternative, milk, "$prefix-alt-milk", 400L)
        val altEggs = priceCase(alternative, eggs, "$prefix-alt-eggs", 500L)
        val cases = listOf(usualMilk, usualEggs, altMilk, altEggs)
        val registries = fixture.registries(cases)

        val usualStorePriceFacts =
            StapleWatchUsualStoreBasketPriceFacts.resolve(
                intent = intent,
                store = storeScope(usual),
                priceBindings =
                    listOf(
                        binding(usual, milk, usualMilk),
                        binding(usual, eggs, usualEggs)
                    ),
                priceRequests = listOf(usualMilk.request, usualEggs.request),
                lifecycleRegistry = registries.lifecycle,
                dispositionRegistry = registries.disposition,
                evaluatedAtEpochMillis = fixture.evaluatedAtEpochMillis,
                acceptancePolicy = fixture.acceptancePolicy
            )
        val alternativeStorePriceFacts =
            StapleWatchAlternativeStoreBasketPriceFacts.resolve(
                identityFacts = identityFacts,
                stores = listOf(storeScope(alternative)),
                priceBindings =
                    listOf(
                        binding(alternative, milk, altMilk),
                        binding(alternative, eggs, altEggs)
                    ),
                priceRequests = listOf(altMilk.request, altEggs.request),
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
                                distanceMetres = 500L,
                                travelTimeSeconds = 120L
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
            providerItemId = "${storeKey.value}-${itemKey.value}-$id",
            merchantKey = merchantKey(storeKey),
            locationKey = locationKey(storeKey),
            priceMinor = priceMinor,
            observedAtEpochMillis = 4_500L
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

    private fun storeScope(storeKey: ShoppingStoreKey): PracticalShoppingProductionPriceStoreScope =
        PracticalShoppingProductionPriceStoreScope(
            storeKey = storeKey,
            merchantKey = merchantKey(storeKey),
            locationKey = locationKey(storeKey),
            commerceChannelKey = "IN_STORE"
        )

    private fun policy(minimumSavingsMinor: Long = 100L): StapleWatchPolicy =
        StapleWatchPolicy(
            minimumSwitchSavings = Money(minimumSavingsMinor, "CAD"),
            maxAdditionalTravelSeconds = 600L,
            maxAdditionalDistanceMetres = 5_000L
        )

    private fun metadata(displayName: String): StapleWatchStoreDisplayMetadata =
        StapleWatchStoreDisplayMetadata(
            entries =
                listOf(
                    StapleWatchStoreDisplayMetadataEntry(
                        storeKey = alternative,
                        displayName = displayName
                    )
                )
        )

    private fun merchantKey(storeKey: ShoppingStoreKey): String = "merchant-${storeKey.value}"

    private fun locationKey(storeKey: ShoppingStoreKey): String = "location-${storeKey.value}"

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
