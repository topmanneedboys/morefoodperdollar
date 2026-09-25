package com.valuepilot.app

import com.valuepilot.core.AvailabilityState
import com.valuepilot.core.EvidenceFreshness
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
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.lang.reflect.Modifier

class StapleWatchEvidenceCurrentnessFactsTest {

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
    fun currentnessReevaluatesExactPriceProvenanceInStableStoreAndRequestOrder() {
        val usualMilk = fixture.case("usual-milk", "usual-milk-product", priceMinor = 500L)
        val usualEggs =
            fixture.case(
                requestId = "usual-eggs",
                providerItemId = "usual-eggs-product",
                priceMinor = 400L,
                observedAtEpochMillis = 2_000L
            )
        val eastEggs =
            fixture.case(
                requestId = "east-eggs",
                providerItemId = "east-eggs-product",
                merchantKey = "merchant-east",
                locationKey = "location-east",
                priceMinor = 390L
            )
        val westMilk =
            fixture.case(
                requestId = "west-milk",
                providerItemId = "west-milk-product",
                merchantKey = "merchant-west",
                locationKey = "location-west",
                priceMinor = 480L,
                observedAtEpochMillis = 2_000L
            )
        val westEggsBlocked =
            fixture.case(
                requestId = "west-eggs-blocked",
                providerItemId = "west-eggs-blocked-product",
                merchantKey = "merchant-west",
                locationKey = "location-west",
                priceMinor = 380L,
                availability = AvailabilityState.OUT_OF_STOCK
            )
        val allCases = listOf(usualMilk, usualEggs, eastEggs, westMilk, westEggsBlocked)
        val registries = fixture.registries(allCases)

        val usualPrices =
            StapleWatchUsualStoreBasketPriceFacts.resolve(
                intent = intent,
                store = store(usual),
                priceBindings =
                    listOf(
                        binding(eggs, usual, usualEggs),
                        binding(milk, usual, usualMilk)
                    ),
                priceRequests = listOf(usualEggs.request, usualMilk.request),
                lifecycleRegistry = registries.lifecycle,
                dispositionRegistry = registries.disposition,
                evaluatedAtEpochMillis = fixture.evaluatedAtEpochMillis,
                acceptancePolicy = fixture.acceptancePolicy
            )
        val alternativePrices =
            StapleWatchAlternativeStoreBasketPriceFacts.resolve(
                identityFacts = identities(intent, listOf(west, east)),
                stores = listOf(store(west), store(east)),
                priceBindings =
                    listOf(
                        binding(eggs, west, westEggsBlocked),
                        binding(eggs, east, eastEggs),
                        binding(milk, west, westMilk)
                    ),
                priceRequests =
                    listOf(
                        westMilk.request,
                        eastEggs.request,
                        westEggsBlocked.request
                    ),
                lifecycleRegistry = registries.lifecycle,
                dispositionRegistry = registries.disposition,
                evaluatedAtEpochMillis = fixture.evaluatedAtEpochMillis,
                acceptancePolicy = fixture.acceptancePolicy
            )

        val facts =
            StapleWatchEvidenceCurrentnessFacts.resolve(
                usualStorePriceFacts = usualPrices,
                alternativeStorePriceFacts = alternativePrices,
                lifecycleRegistry = registries.lifecycle,
                dispositionRegistry = registries.disposition,
                evaluatedAtEpochMillis = fixture.evaluatedAtEpochMillis,
                acceptancePolicy = fixture.acceptancePolicy
            )

        assertSame(usualPrices, facts.usualStorePriceFacts)
        assertSame(alternativePrices, facts.alternativeStorePriceFacts)
        assertEquals(intent, facts.intent)
        assertEquals(
            StapleWatchFactResolutionRequirement.EVIDENCE_CURRENTNESS_METADATA,
            facts.resolvedRequirement
        )
        assertEquals(usual, facts.usualStore.storeKey)
        assertEquals(listOf(milk, eggs), facts.usualStore.itemCurrentness.map { it.itemKey })
        assertEquals(
            listOf(
                StapleWatchEvidenceCurrentnessStatus.CURRENTNESS_ESTABLISHED,
                StapleWatchEvidenceCurrentnessStatus.CURRENTNESS_ESTABLISHED
            ),
            facts.usualStore.itemCurrentness.map { it.status }
        )
        assertEquals(
            listOf(EvidenceFreshness.FRESH, EvidenceFreshness.AGING),
            facts.usualStore.itemCurrentness.map { it.freshness }
        )

        assertEquals(listOf(east, west), facts.alternatives.map { it.storeKey })
        assertEquals(
            listOf(
                StapleWatchEvidenceCurrentnessStatus.NO_BOUND_PRODUCTION_EVIDENCE,
                StapleWatchEvidenceCurrentnessStatus.CURRENTNESS_ESTABLISHED
            ),
            facts.alternatives[0].itemCurrentness.map { it.status }
        )
        assertEquals(
            listOf(null, EvidenceFreshness.FRESH),
            facts.alternatives[0].itemCurrentness.map { it.freshness }
        )
        assertEquals(
            listOf(
                StapleWatchEvidenceCurrentnessStatus.CURRENTNESS_ESTABLISHED,
                StapleWatchEvidenceCurrentnessStatus.PRODUCTION_EVIDENCE_BLOCKED
            ),
            facts.alternatives[1].itemCurrentness.map { it.status }
        )
        assertEquals(
            listOf(EvidenceFreshness.AGING, null),
            facts.alternatives[1].itemCurrentness.map { it.freshness }
        )
    }

    @Test
    fun laterEvaluationBlocksPreviouslyUsablePriceInsteadOfTrustingDetachedFreshness() {
        val milkPrice = fixture.case("later-milk", "later-milk-product", priceMinor = 525L)
        val registries = fixture.registries(listOf(milkPrice))
        val usualPrices =
            StapleWatchUsualStoreBasketPriceFacts.resolve(
                intent = intent,
                store = store(usual),
                priceBindings = listOf(binding(milk, usual, milkPrice)),
                priceRequests = listOf(milkPrice.request),
                lifecycleRegistry = registries.lifecycle,
                dispositionRegistry = registries.disposition,
                evaluatedAtEpochMillis = fixture.evaluatedAtEpochMillis,
                acceptancePolicy = fixture.acceptancePolicy
            )
        val alternativePrices = emptyAlternativePrices(intent, registries)

        assertEquals(
            StapleWatchBasketItemPriceState.USABLE_EXACT_PRICE,
            usualPrices.itemPrices.first().state
        )

        val currentness =
            StapleWatchEvidenceCurrentnessFacts.resolve(
                usualStorePriceFacts = usualPrices,
                alternativeStorePriceFacts = alternativePrices,
                lifecycleRegistry = registries.lifecycle,
                dispositionRegistry = registries.disposition,
                evaluatedAtEpochMillis = 10_000L,
                acceptancePolicy = fixture.acceptancePolicy
            )

        assertEquals(
            StapleWatchEvidenceCurrentnessStatus.PRODUCTION_EVIDENCE_BLOCKED,
            currentness.usualStore.itemCurrentness.first().status
        )
        assertNull(currentness.usualStore.itemCurrentness.first().freshness)
    }

    @Test
    fun currentAcceptancePolicyCanBlockPreviouslyUsableAgingEvidence() {
        val agingMilk =
            fixture.case(
                requestId = "aging-milk",
                providerItemId = "aging-milk-product",
                observedAtEpochMillis = 2_000L
            )
        val registries = fixture.registries(listOf(agingMilk))
        val usualPrices =
            StapleWatchUsualStoreBasketPriceFacts.resolve(
                intent = intent,
                store = store(usual),
                priceBindings = listOf(binding(milk, usual, agingMilk)),
                priceRequests = listOf(agingMilk.request),
                lifecycleRegistry = registries.lifecycle,
                dispositionRegistry = registries.disposition,
                evaluatedAtEpochMillis = fixture.evaluatedAtEpochMillis,
                acceptancePolicy = fixture.acceptancePolicy
            )
        val alternativePrices = emptyAlternativePrices(intent, registries)

        assertEquals(
            StapleWatchBasketItemPriceState.USABLE_EXACT_PRICE,
            usualPrices.itemPrices.first().state
        )

        val currentness =
            StapleWatchEvidenceCurrentnessFacts.resolve(
                usualStorePriceFacts = usualPrices,
                alternativeStorePriceFacts = alternativePrices,
                lifecycleRegistry = registries.lifecycle,
                dispositionRegistry = registries.disposition,
                evaluatedAtEpochMillis = fixture.evaluatedAtEpochMillis,
                acceptancePolicy = fixture.acceptancePolicy.copy(rankAgingRealWorld = false)
            )

        assertEquals(
            StapleWatchEvidenceCurrentnessStatus.PRODUCTION_EVIDENCE_BLOCKED,
            currentness.usualStore.itemCurrentness.first().status
        )
        assertNull(currentness.usualStore.itemCurrentness.first().freshness)
    }

    @Test
    fun missingBindingsRemainExplicitlyUnboundWithoutSyntheticFreshness() {
        val registries = fixture.registries(emptyList())
        val usualPrices =
            StapleWatchUsualStoreBasketPriceFacts.resolve(
                intent = intent,
                store = store(usual),
                priceBindings = emptyList(),
                priceRequests = emptyList(),
                lifecycleRegistry = registries.lifecycle,
                dispositionRegistry = registries.disposition,
                evaluatedAtEpochMillis = fixture.evaluatedAtEpochMillis,
                acceptancePolicy = fixture.acceptancePolicy
            )
        val alternativePrices =
            StapleWatchAlternativeStoreBasketPriceFacts.resolve(
                identityFacts = identities(intent, listOf(east)),
                stores = listOf(store(east)),
                priceBindings = emptyList(),
                priceRequests = emptyList(),
                lifecycleRegistry = registries.lifecycle,
                dispositionRegistry = registries.disposition,
                evaluatedAtEpochMillis = fixture.evaluatedAtEpochMillis,
                acceptancePolicy = fixture.acceptancePolicy
            )

        val currentness =
            StapleWatchEvidenceCurrentnessFacts.resolve(
                usualStorePriceFacts = usualPrices,
                alternativeStorePriceFacts = alternativePrices,
                lifecycleRegistry = registries.lifecycle,
                dispositionRegistry = registries.disposition,
                evaluatedAtEpochMillis = fixture.evaluatedAtEpochMillis,
                acceptancePolicy = fixture.acceptancePolicy
            )

        val allCells =
            currentness.usualStore.itemCurrentness +
                currentness.alternatives.flatMap { storeFact -> storeFact.itemCurrentness }
        assertTrue(
            allCells.all { fact ->
                fact.status ==
                    StapleWatchEvidenceCurrentnessStatus.NO_BOUND_PRODUCTION_EVIDENCE
            }
        )
        assertTrue(allCells.all { fact -> fact.freshness == null })
    }

    @Test
    fun explicitNoAlternativeStoresProducesExplicitEmptyAlternativeCurrentness() {
        val milkPrice = fixture.case("usual-only-milk", "usual-only-milk-product")
        val registries = fixture.registries(listOf(milkPrice))
        val usualPrices =
            StapleWatchUsualStoreBasketPriceFacts.resolve(
                intent = intent,
                store = store(usual),
                priceBindings = listOf(binding(milk, usual, milkPrice)),
                priceRequests = listOf(milkPrice.request),
                lifecycleRegistry = registries.lifecycle,
                dispositionRegistry = registries.disposition,
                evaluatedAtEpochMillis = fixture.evaluatedAtEpochMillis,
                acceptancePolicy = fixture.acceptancePolicy
            )
        val alternativePrices = emptyAlternativePrices(intent, registries)

        val currentness =
            StapleWatchEvidenceCurrentnessFacts.resolve(
                usualStorePriceFacts = usualPrices,
                alternativeStorePriceFacts = alternativePrices,
                lifecycleRegistry = registries.lifecycle,
                dispositionRegistry = registries.disposition,
                evaluatedAtEpochMillis = fixture.evaluatedAtEpochMillis,
                acceptancePolicy = fixture.acceptancePolicy
            )

        assertTrue(currentness.alternatives.isEmpty())
        assertEquals(
            StapleWatchEvidenceCurrentnessStatus.CURRENTNESS_ESTABLISHED,
            currentness.usualStore.itemCurrentness.first().status
        )
    }

    @Test
    fun priceFactsForDifferentIntentsFailClosed() {
        val otherIntent =
            StapleWatchFactCheckIntent(
                request = intent.request,
                usualStoreKey = ShoppingStoreKey("different-usual")
            )
        val registries = fixture.registries(emptyList())
        val usualPrices =
            StapleWatchUsualStoreBasketPriceFacts.resolve(
                intent = intent,
                store = store(usual),
                priceBindings = emptyList(),
                priceRequests = emptyList(),
                lifecycleRegistry = registries.lifecycle,
                dispositionRegistry = registries.disposition,
                evaluatedAtEpochMillis = fixture.evaluatedAtEpochMillis,
                acceptancePolicy = fixture.acceptancePolicy
            )
        val otherAlternativePrices = emptyAlternativePrices(otherIntent, registries)

        expectIllegalArgument {
            StapleWatchEvidenceCurrentnessFacts.resolve(
                usualStorePriceFacts = usualPrices,
                alternativeStorePriceFacts = otherAlternativePrices,
                lifecycleRegistry = registries.lifecycle,
                dispositionRegistry = registries.disposition,
                evaluatedAtEpochMillis = fixture.evaluatedAtEpochMillis,
                acceptancePolicy = fixture.acceptancePolicy
            )
        }
    }

    @Test
    fun itemCurrentnessInvariantRejectsFabricatedFreshnessCombinations() {
        expectIllegalArgument {
            StapleWatchBasketItemCurrentnessFact(
                itemKey = milk,
                status = StapleWatchEvidenceCurrentnessStatus.NO_BOUND_PRODUCTION_EVIDENCE,
                freshness = EvidenceFreshness.FRESH
            )
        }
        expectIllegalArgument {
            StapleWatchBasketItemCurrentnessFact(
                itemKey = milk,
                status = StapleWatchEvidenceCurrentnessStatus.CURRENTNESS_ESTABLISHED,
                freshness = EvidenceFreshness.STALE
            )
        }
        expectIllegalArgument {
            StapleWatchBasketItemCurrentnessFact(
                itemKey = milk,
                status = StapleWatchEvidenceCurrentnessStatus.CURRENTNESS_ESTABLISHED,
                freshness = null
            )
        }
    }

    @Test
    fun authoritativeBoundaryReusesProductionRailAndOwnsNoSecondFreshnessEconomicsOrDeliveryEngine() {
        val constructors = StapleWatchEvidenceCurrentnessFacts::class.java.declaredConstructors
        assertTrue(constructors.isNotEmpty())
        assertTrue(
            constructors
                .filterNot { constructor -> constructor.isSynthetic }
                .all { constructor -> Modifier.isPrivate(constructor.modifiers) }
        )
        assertFalse(
            StapleWatchEvidenceCurrentnessFacts::class.java.methods.any { method ->
                method.name == "copy" || method.name.startsWith("copy$")
            }
        )

        val source = source("StapleWatchEvidenceCurrentnessFacts.kt").readText()
        assertTrue(
            source.contains("class StapleWatchEvidenceCurrentnessFacts private constructor(")
        )
        assertTrue(source.contains("PracticalShoppingProductionCandidateBridge.evaluatePrices"))
        assertTrue(source.contains("EVIDENCE_CURRENTNESS_METADATA"))
        assertTrue(source.contains("EvidenceFreshness"))
        assertTrue(source.contains("usualStorePriceFacts.productionPriceRequests"))
        assertTrue(source.contains("alternativeStorePriceFacts.productionPriceRequests"))

        listOf(
            "EvidenceFreshnessEvaluator",
            "EvidenceAcceptanceEvaluator",
            "ProductionCurrentPriceEligibilityEvaluator",
            "StapleWatchPolicy",
            "StapleWatchEconomicEvaluator",
            "StapleWatchEconomicDecision",
            "ShoppingTravel",
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
            assertFalse("Currentness fact boundary must not own $forbidden", source.contains(forbidden))
        }
    }

    private fun emptyAlternativePrices(
        targetIntent: StapleWatchFactCheckIntent,
        registries: StapleWatchProductionPriceTestFixture.Registries
    ): StapleWatchAlternativeStoreBasketPriceFacts =
        StapleWatchAlternativeStoreBasketPriceFacts.resolve(
            identityFacts = identities(targetIntent, emptyList()),
            stores = emptyList(),
            priceBindings = emptyList(),
            priceRequests = emptyList(),
            lifecycleRegistry = registries.lifecycle,
            dispositionRegistry = registries.disposition,
            evaluatedAtEpochMillis = fixture.evaluatedAtEpochMillis,
            acceptancePolicy = fixture.acceptancePolicy
        )

    private fun identities(
        targetIntent: StapleWatchFactCheckIntent,
        storeKeys: Collection<ShoppingStoreKey>
    ): StapleWatchAlternativeStoreIdentityFacts =
        StapleWatchAlternativeStoreIdentityFacts.fromUnordered(
            intent = targetIntent,
            alternativeStoreKeys = storeKeys
        )

    private fun store(storeKey: ShoppingStoreKey): PracticalShoppingProductionPriceStoreScope =
        if (storeKey == usual) {
            PracticalShoppingProductionPriceStoreScope(
                storeKey = storeKey,
                merchantKey = "merchant-a",
                locationKey = "location-a",
                commerceChannelKey = "IN_STORE"
            )
        } else {
            PracticalShoppingProductionPriceStoreScope(
                storeKey = storeKey,
                merchantKey = "merchant-${storeKey.value}",
                locationKey = "location-${storeKey.value}",
                commerceChannelKey = "IN_STORE"
            )
        }

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
}
