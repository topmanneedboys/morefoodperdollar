package com.valuepilot.core

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class StapleWatchNativeBasketCandidateTest {

    private val milk = ShoppingItemKey("milk")
    private val eggs = ShoppingItemKey("eggs")
    private val bread = ShoppingItemKey("bread")
    private val request = ShoppingRequest(listOf(milk, eggs, bread))
    private val policy =
        StapleWatchPolicy(
            minimumSwitchSavings = Money.parse("15.00", "CAD"),
            maxAdditionalTravelSeconds = 600L,
            maxAdditionalDistanceMetres = 5_000L
        )

    @Test
    fun watchNativeCandidateEvaluatesWithoutAnyAbsoluteTravelFact() {
        val baseline = nativeBasket("normal", "50.00", request.itemKeySet)
        val alternative = nativeAlternative("other", "31.00", 300L, 2_000L)

        val decision =
            StapleWatchEconomicEvaluator.evaluate(
                request = request,
                baseline = baseline,
                alternatives = listOf(alternative),
                policy = policy
            )

        assertEquals(StapleWatchEconomicStatus.SWITCH_WORTHWHILE, decision.status)
        assertSame(baseline, decision.baseline)
        assertSame(alternative, decision.recommendedAlternative)
        assertEquals(Money.parse("19.00", "CAD"), decision.switchSavings)
        assertSame(alternative.basket.evidence, decision.recommendedAlternative?.basket?.evidence)
    }

    @Test
    fun legacyEntryPointIgnoresAbsoluteTravelAndPreservesOriginalObjectIdentity() {
        val baseline =
            legacySingle(
                store = "normal",
                cost = "50.00",
                absoluteTravel = ShoppingTravel(9_999_999L, 9_999_999L)
            )
        val alternative =
            StapleWatchAlternativeCandidate(
                storePlan =
                    legacySingle(
                        store = "other",
                        cost = "31.00",
                        absoluteTravel = ShoppingTravel(8_888_888L, 8_888_888L)
                    ),
                additionalTravel = ShoppingTravel(2_000L, 300L)
            )

        val decision =
            StapleWatchEconomicEvaluator.evaluate(
                request = request,
                baseline = baseline,
                alternatives = listOf(alternative),
                policy = policy
            )

        assertEquals(StapleWatchEconomicStatus.SWITCH_WORTHWHILE, decision.status)
        assertSame(baseline, decision.baseline)
        assertSame(alternative, decision.recommendedAlternative)
        assertEquals(Money.parse("19.00", "CAD"), decision.switchSavings)
    }

    @Test
    fun nativeAndLegacyEntryPointsStayEquivalentForRouteRejection() {
        val nativeBaseline = nativeBasket("normal", "50.00", request.itemKeySet)
        val nativeAlternative = nativeAlternative("other", "1.00", 601L, 1_000L)
        val nativeDecision =
            StapleWatchEconomicEvaluator.evaluate(
                request,
                nativeBaseline,
                listOf(nativeAlternative),
                policy
            )

        val legacyBaseline = legacySingle("normal", "50.00")
        val legacyAlternative =
            StapleWatchAlternativeCandidate(
                storePlan = legacySingle("other", "1.00"),
                additionalTravel = ShoppingTravel(1_000L, 601L)
            )
        val legacyDecision =
            StapleWatchEconomicEvaluator.evaluate(
                request,
                legacyBaseline,
                listOf(legacyAlternative),
                policy
            )

        assertEquals(StapleWatchEconomicStatus.NOT_WORTH_SWITCHING, nativeDecision.status)
        assertEquals(nativeDecision.status, legacyDecision.status)
        assertNull(nativeDecision.switchSavings)
        assertEquals(nativeDecision.switchSavings, legacyDecision.switchSavings)
    }

    @Test
    fun watchNativeCandidatePreservesMoneyCoverageAndEvidenceInvariants() {
        assertFailsWith<IllegalArgumentException> {
            StapleWatchBasketCandidate(
                storeKey = ShoppingStoreKey("negative"),
                coveredItemKeys = request.itemKeySet,
                knownBasketCost = Money.parse("-1.00", "CAD"),
                evidence = evidence(request.itemKeys.size)
            )
        }

        assertFailsWith<IllegalArgumentException> {
            StapleWatchBasketCandidate(
                storeKey = ShoppingStoreKey("mismatch"),
                coveredItemKeys = request.itemKeySet,
                knownBasketCost = Money.parse("1.00", "CAD"),
                evidence = evidence(request.itemKeys.size - 1)
            )
        }
    }

    @Test
    fun watchNativeBoundaryHasNoAbsoluteTravelFieldOrZeroTravelSentinel() {
        assertFalse(
            StapleWatchBasketCandidate::class.java.declaredFields.any { field ->
                field.name == "travel"
            }
        )

        val source =
            File(
                System.getProperty("user.dir"),
                "src/main/kotlin/com/valuepilot/core/StapleWatch.kt"
            )
        assertTrue(source.isFile)
        val text = source.readText()
        assertTrue(text.contains("data class StapleWatchBasketCandidate("))
        assertTrue(text.contains("baseline.toStapleWatchBasketCandidate()"))
        assertTrue(text.contains("alternative.storePlan.toStapleWatchBasketCandidate()"))
        assertFalse(text.contains("ShoppingTravel(0L, 0L)"))
        assertFalse(text.contains("ShoppingTravel(0L,0L)"))
    }

    private fun nativeBasket(
        store: String,
        cost: String,
        covered: Set<ShoppingItemKey>
    ): StapleWatchBasketCandidate =
        StapleWatchBasketCandidate(
            storeKey = ShoppingStoreKey(store),
            coveredItemKeys = covered,
            knownBasketCost = Money.parse(cost, "CAD"),
            evidence = evidence(covered.size)
        )

    private fun nativeAlternative(
        store: String,
        cost: String,
        additionalTravelSeconds: Long,
        additionalDistanceMetres: Long
    ): StapleWatchBasketAlternativeCandidate =
        StapleWatchBasketAlternativeCandidate(
            basket = nativeBasket(store, cost, request.itemKeySet),
            additionalTravel =
                ShoppingTravel(
                    distanceMetres = additionalDistanceMetres,
                    travelTimeSeconds = additionalTravelSeconds
                )
        )

    private fun legacySingle(
        store: String,
        cost: String,
        absoluteTravel: ShoppingTravel = ShoppingTravel(1_000L, 300L)
    ): SingleStorePlanCandidate =
        SingleStorePlanCandidate(
            storeKey = ShoppingStoreKey(store),
            coveredItemKeys = request.itemKeySet,
            knownBasketCost = Money.parse(cost, "CAD"),
            travel = absoluteTravel,
            evidence = evidence(request.itemKeys.size)
        )

    private fun evidence(itemCount: Int): ShoppingPlanEvidenceSummary =
        ShoppingPlanEvidenceSummary(
            freshItemCount = itemCount,
            staleItemCount = 0,
            unknownFreshnessItemCount = 0
        )
}
