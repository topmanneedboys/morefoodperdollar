package com.valuepilot.app

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
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.lang.reflect.Modifier

class StapleWatchEconomicEvidencePreconditionsTest {

    private val fixture = StapleWatchProductionPriceTestFixture()
    private val milk = ShoppingItemKey("milk")
    private val eggs = ShoppingItemKey("eggs")
    private val usual = ShoppingStoreKey("usual")
    private val east = ShoppingStoreKey("east")
    private val west = ShoppingStoreKey("west")
    private val intent =
        StapleWatchFactCheckIntent(
            request = ShoppingRequest(listOf(milk, eggs)),
            usualStoreKey = usual
        )

    @Test
    fun completeBaselinePreservesOnlyPriceAndCurrentnessReadyAlternativesInStableOrder() {
        val facts =
            factSet(
                usualMilk = priceCase("usual-milk", milk, usual),
                usualEggs = priceCase("usual-eggs", eggs, usual),
                eastMilk = priceCase("east-milk", milk, east),
                eastEggs = priceCase("east-eggs", eggs, east),
                westMilk = priceCase("west-milk", milk, west, observedAtEpochMillis = 2_000L),
                westEggs = priceCase("west-eggs", eggs, west, observedAtEpochMillis = 2_000L),
                alternativeStoreKeys = listOf(west, east),
                currentnessRankAging = false
            )

        assertTrue(
            facts.alternativeStorePriceFacts.alternatives
                .single { it.storeKey == west }
                .itemPrices
                .all { it.state == StapleWatchBasketItemPriceState.USABLE_EXACT_PRICE }
        )
        assertTrue(
            facts.currentnessFacts.alternatives
                .single { it.storeKey == west }
                .itemCurrentness
                .all {
                    it.status == StapleWatchEvidenceCurrentnessStatus.PRODUCTION_EVIDENCE_BLOCKED
                }
        )

        val result = evaluate(facts)

        assertSame(intent, result.intent)
        assertTrue(result.satisfied)
        assertNull(result.issue)
        assertEquals(listOf(east), result.priceAndCurrentnessReadyAlternativeStoreKeys)
        assertSame(facts.identityFacts, result.identityFacts)
        assertSame(facts.usualStorePriceFacts, result.usualStorePriceFacts)
        assertSame(facts.alternativeStorePriceFacts, result.alternativeStorePriceFacts)
        assertSame(facts.additionalTravelFacts, result.additionalTravelFacts)
        assertSame(facts.currentnessFacts, result.currentnessFacts)
    }

    @Test
    fun missingUsualStorePriceBlocksBeforeAlternativeEvidenceCanBeExposed() {
        val facts =
            factSet(
                usualMilk = priceCase("usual-milk-only", milk, usual),
                usualEggs = null,
                eastMilk = priceCase("east-milk-complete", milk, east),
                eastEggs = priceCase("east-eggs-complete", eggs, east),
                alternativeStoreKeys = listOf(east)
            )

        val result = evaluate(facts)

        assertFalse(result.satisfied)
        assertEquals(
            StapleWatchEconomicEvidencePreconditionIssue.USUAL_STORE_PRICE_COVERAGE_INCOMPLETE,
            result.issue
        )
        assertTrue(result.priceAndCurrentnessReadyAlternativeStoreKeys.isEmpty())
    }

    @Test
    fun laterBlockedUsualCurrentnessBlocksPreviouslyUsableExactPrices() {
        val facts =
            factSet(
                usualMilk = priceCase("later-usual-milk", milk, usual),
                usualEggs = priceCase("later-usual-eggs", eggs, usual),
                alternativeStoreKeys = emptyList(),
                currentnessEvaluatedAtEpochMillis = 10_000L
            )

        assertTrue(
            facts.usualStorePriceFacts.itemPrices.all {
                it.state == StapleWatchBasketItemPriceState.USABLE_EXACT_PRICE
            }
        )
        assertTrue(
            facts.currentnessFacts.usualStore.itemCurrentness.all {
                it.status == StapleWatchEvidenceCurrentnessStatus.PRODUCTION_EVIDENCE_BLOCKED
            }
        )

        val result = evaluate(facts)

        assertFalse(result.satisfied)
        assertEquals(
            StapleWatchEconomicEvidencePreconditionIssue.USUAL_STORE_CURRENTNESS_INCOMPLETE,
            result.issue
        )
        assertTrue(result.priceAndCurrentnessReadyAlternativeStoreKeys.isEmpty())
    }

    @Test
    fun explicitNoAlternativeStoresIsValidWhenUsualStoreEvidenceIsCompleteAndCurrent() {
        val facts =
            factSet(
                usualMilk = priceCase("usual-only-milk", milk, usual),
                usualEggs = priceCase("usual-only-eggs", eggs, usual),
                alternativeStoreKeys = emptyList()
            )

        val result = evaluate(facts)

        assertTrue(result.satisfied)
        assertNull(result.issue)
        assertTrue(result.priceAndCurrentnessReadyAlternativeStoreKeys.isEmpty())
    }

    @Test
    fun detachedEquivalentIdentityObjectFailsClosed() {
        val facts =
            factSet(
                usualMilk = priceCase("identity-usual-milk", milk, usual),
                usualEggs = priceCase("identity-usual-eggs", eggs, usual),
                eastMilk = priceCase("identity-east-milk", milk, east),
                eastEggs = priceCase("identity-east-eggs", eggs, east),
                alternativeStoreKeys = listOf(east)
            )
        val detachedIdentity =
            StapleWatchAlternativeStoreIdentityFacts.fromUnordered(
                intent = intent,
                alternativeStoreKeys = listOf(east)
            )
        assertEquals(facts.identityFacts, detachedIdentity)
        assertFalse(facts.identityFacts === detachedIdentity)
        val detachedTravel =
            StapleWatchAlternativeAdditionalTravelFacts.fromUnordered(
                identityFacts = detachedIdentity,
                additionalTravelByStore = mapOf(east to ShoppingTravel(1_000L, 300L))
            )

        expectIllegalArgument {
            StapleWatchEconomicEvidencePreconditions.evaluate(
                identityFacts = facts.identityFacts,
                usualStorePriceFacts = facts.usualStorePriceFacts,
                alternativeStorePriceFacts = facts.alternativeStorePriceFacts,
                additionalTravelFacts = detachedTravel,
                currentnessFacts = facts.currentnessFacts
            )
        }
    }

    @Test
    fun currentnessFromDifferentSameIntentPriceObjectFailsClosed() {
        val authoritative =
            factSet(
                usualMilk = priceCase("source-usual-milk", milk, usual),
                usualEggs = priceCase("source-usual-eggs", eggs, usual),
                eastMilk = priceCase("source-east-milk", milk, east),
                eastEggs = priceCase("source-east-eggs", eggs, east),
                alternativeStoreKeys = listOf(east)
            )
        val detached =
            factSet(
                usualMilk = priceCase("detached-usual-milk", milk, usual),
                usualEggs = priceCase("detached-usual-eggs", eggs, usual),
                alternativeStoreKeys = emptyList()
            )

        assertEquals(authoritative.usualStorePriceFacts.intent, detached.usualStorePriceFacts.intent)
        assertFalse(authoritative.usualStorePriceFacts === detached.usualStorePriceFacts)

        expectIllegalArgument {
            StapleWatchEconomicEvidencePreconditions.evaluate(
                identityFacts = authoritative.identityFacts,
                usualStorePriceFacts = detached.usualStorePriceFacts,
                alternativeStorePriceFacts = authoritative.alternativeStorePriceFacts,
                additionalTravelFacts = authoritative.additionalTravelFacts,
                currentnessFacts = authoritative.currentnessFacts
            )
        }
    }

    @Test
    fun boundaryOwnsOnlyEvidencePreconditionsNotArithmeticTravelEconomicsOrDelivery() {
        val constructors = StapleWatchEconomicEvidencePreconditions::class.java.declaredConstructors
        assertTrue(constructors.isNotEmpty())
        assertTrue(
            constructors
                .filterNot { constructor -> constructor.isSynthetic }
                .all { constructor -> Modifier.isPrivate(constructor.modifiers) }
        )
        assertFalse(
            StapleWatchEconomicEvidencePreconditions::class.java.methods.any { method ->
                method.name == "copy" || method.name.startsWith("copy$")
            }
        )

        val source = source("StapleWatchEconomicEvidencePreconditions.kt").readText()
        assertTrue(
            source.contains("class StapleWatchEconomicEvidencePreconditions private constructor(")
        )
        assertTrue(source.contains("alternativeStorePriceFacts.identityFacts === identityFacts"))
        assertTrue(source.contains("currentnessFacts.usualStorePriceFacts === usualStorePriceFacts"))
        assertTrue(source.contains("StapleWatchBasketItemPriceState.USABLE_EXACT_PRICE"))
        assertTrue(source.contains("StapleWatchEvidenceCurrentnessStatus.CURRENTNESS_ESTABLISHED"))

        listOf(
            "StapleWatchFactResolutionReadiness",
            "SingleStorePlanCandidate",
            "StapleWatchAlternativeCandidate",
            "StapleWatchEconomicEvaluator",
            "StapleWatchPolicy",
            "exactPrice",
            ".freshness",
            "ShoppingTravel",
            ".additionalTravel",
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
            assertFalse("Economic evidence preconditions must not own $forbidden", source.contains(forbidden))
        }
    }

    private fun evaluate(
        facts: FactSet
    ): StapleWatchEconomicEvidencePreconditions =
        StapleWatchEconomicEvidencePreconditions.evaluate(
            identityFacts = facts.identityFacts,
            usualStorePriceFacts = facts.usualStorePriceFacts,
            alternativeStorePriceFacts = facts.alternativeStorePriceFacts,
            additionalTravelFacts = facts.additionalTravelFacts,
            currentnessFacts = facts.currentnessFacts
        )

    private fun factSet(
        usualMilk: StapleWatchProductionPriceTestFixture.PriceCase,
        usualEggs: StapleWatchProductionPriceTestFixture.PriceCase?,
        eastMilk: StapleWatchProductionPriceTestFixture.PriceCase? = null,
        eastEggs: StapleWatchProductionPriceTestFixture.PriceCase? = null,
        westMilk: StapleWatchProductionPriceTestFixture.PriceCase? = null,
        westEggs: StapleWatchProductionPriceTestFixture.PriceCase? = null,
        alternativeStoreKeys: List<ShoppingStoreKey>,
        currentnessEvaluatedAtEpochMillis: Long = fixture.evaluatedAtEpochMillis,
        currentnessRankAging: Boolean = true
    ): FactSet {
        val casesByStoreItem =
            mapOf(
                usual to mapOf(milk to usualMilk, eggs to usualEggs),
                east to mapOf(milk to eastMilk, eggs to eastEggs),
                west to mapOf(milk to westMilk, eggs to westEggs)
            )
        val allCases =
            casesByStoreItem.values
                .flatMap { byItem -> byItem.values }
                .filterNotNull()
        val registries = fixture.registries(allCases)
        val identityFacts =
            StapleWatchAlternativeStoreIdentityFacts.fromUnordered(
                intent = intent,
                alternativeStoreKeys = alternativeStoreKeys
            )

        val usualCases = requireNotNull(casesByStoreItem[usual])
        val usualBindings =
            intent.request.itemKeys.mapNotNull { itemKey ->
                usualCases[itemKey]?.let { priceCase -> binding(itemKey, usual, priceCase) }
            }
        val usualRequests =
            intent.request.itemKeys.mapNotNull { itemKey -> usualCases[itemKey]?.request }
        val usualStorePriceFacts =
            StapleWatchUsualStoreBasketPriceFacts.resolve(
                intent = intent,
                store = store(usual),
                priceBindings = usualBindings,
                priceRequests = usualRequests,
                lifecycleRegistry = registries.lifecycle,
                dispositionRegistry = registries.disposition,
                evaluatedAtEpochMillis = fixture.evaluatedAtEpochMillis,
                acceptancePolicy = fixture.acceptancePolicy
            )

        val alternativeBindings =
            alternativeStoreKeys.flatMap { storeKey ->
                val byItem = requireNotNull(casesByStoreItem[storeKey])
                intent.request.itemKeys.mapNotNull { itemKey ->
                    byItem[itemKey]?.let { priceCase -> binding(itemKey, storeKey, priceCase) }
                }
            }
        val alternativeRequests =
            alternativeStoreKeys.flatMap { storeKey ->
                val byItem = requireNotNull(casesByStoreItem[storeKey])
                intent.request.itemKeys.mapNotNull { itemKey -> byItem[itemKey]?.request }
            }
        val alternativeStorePriceFacts =
            StapleWatchAlternativeStoreBasketPriceFacts.resolve(
                identityFacts = identityFacts,
                stores = alternativeStoreKeys.map(::store),
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
                    alternativeStoreKeys.associateWith { ShoppingTravel(1_000L, 300L) }
            )
        val currentnessFacts =
            StapleWatchEvidenceCurrentnessFacts.resolve(
                usualStorePriceFacts = usualStorePriceFacts,
                alternativeStorePriceFacts = alternativeStorePriceFacts,
                lifecycleRegistry = registries.lifecycle,
                dispositionRegistry = registries.disposition,
                evaluatedAtEpochMillis = currentnessEvaluatedAtEpochMillis,
                acceptancePolicy =
                    fixture.acceptancePolicy.copy(rankAgingRealWorld = currentnessRankAging)
            )

        return FactSet(
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
        storeKey: ShoppingStoreKey,
        observedAtEpochMillis: Long = 4_500L
    ): StapleWatchProductionPriceTestFixture.PriceCase =
        fixture.case(
            requestId = id,
            providerItemId = "${storeKey.value}-${itemKey.value}-product-$id",
            merchantKey = if (storeKey == usual) "merchant-a" else "merchant-${storeKey.value}",
            locationKey = if (storeKey == usual) "location-a" else "location-${storeKey.value}",
            observedAtEpochMillis = observedAtEpochMillis
        )

    private fun store(
        storeKey: ShoppingStoreKey
    ): PracticalShoppingProductionPriceStoreScope =
        PracticalShoppingProductionPriceStoreScope(
            storeKey = storeKey,
            merchantKey = if (storeKey == usual) "merchant-a" else "merchant-${storeKey.value}",
            locationKey = if (storeKey == usual) "location-a" else "location-${storeKey.value}",
            commerceChannelKey = "IN_STORE"
        )

    private fun binding(
        itemKey: ShoppingItemKey,
        storeKey: ShoppingStoreKey,
        priceCase: StapleWatchProductionPriceTestFixture.PriceCase
    ): PracticalShoppingProductionPriceBinding =
        PracticalShoppingProductionPriceBinding(
            itemKey = itemKey,
            productKey = priceCase.productKey,
            storeKey = storeKey,
            currentPriceRequestId = priceCase.request.requestId
        )

    private fun expectIllegalArgument(block: () -> Unit) {
        try {
            block()
            fail("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // Expected fail-closed boundary.
        }
    }

    private fun source(fileName: String): File {
        val workingDirectory =
            requireNotNull(System.getProperty("user.dir")) { "Missing user.dir for source boundary test" }
        return File(workingDirectory, "src/main/java/com/valuepilot/app/$fileName").also {
            assertTrue("Missing source $fileName at ${it.absolutePath}", it.isFile)
        }
    }

    private data class FactSet(
        val identityFacts: StapleWatchAlternativeStoreIdentityFacts,
        val usualStorePriceFacts: StapleWatchUsualStoreBasketPriceFacts,
        val alternativeStorePriceFacts: StapleWatchAlternativeStoreBasketPriceFacts,
        val additionalTravelFacts: StapleWatchAlternativeAdditionalTravelFacts,
        val currentnessFacts: StapleWatchEvidenceCurrentnessFacts
    )
}
