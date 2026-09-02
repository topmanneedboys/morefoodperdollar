package com.valuepilot.app

import com.valuepilot.core.Money
import com.valuepilot.core.PracticalShoppingPlanner
import com.valuepilot.core.PracticalShoppingPolicy
import com.valuepilot.core.ShoppingItemKey
import com.valuepilot.core.ShoppingPlanEvidenceSummary
import com.valuepilot.core.ShoppingRequest
import com.valuepilot.core.ShoppingStoreKey
import com.valuepilot.core.ShoppingTravel
import com.valuepilot.core.SingleStorePlanCandidate
import com.valuepilot.core.TwoStorePlanCandidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticalShoppingUiProjectorTest {

    private val eggs = ShoppingItemKey("internal-item-eggs")
    private val milk = ShoppingItemKey("internal-item-milk")
    private val chicken = ShoppingItemKey("internal-item-chicken")
    private val request = ShoppingRequest(listOf(eggs, milk, chicken))
    private val requested = request.itemKeys.toSet()

    private val primaryKey = ShoppingStoreKey("internal-store-primary")
    private val secondKey = ShoppingStoreKey("internal-store-second")

    private val storeNames = mapOf(
        primaryKey to "Sample Market",
        secondKey to "Example Grocer"
    )

    private val itemNames = mapOf(
        eggs to "Eggs",
        milk to "Milk",
        chicken to "Chicken"
    )

    private val policy = PracticalShoppingPolicy(
        minimumSecondStopSavings = Money.parse("15.00", "CAD"),
        maxAdditionalTravelSeconds = 600L,
        maxAdditionalDistanceMetres = 5_000L
    )

    @Test
    fun completeDecisionProjectsOneSimplePrimaryCardWithoutInternalStoreId() {
        val primary = single(
            key = primaryKey,
            cost = "54.80",
            covered = requested,
            travel = ShoppingTravel(2_400L, 391L)
        )

        val decision = PracticalShoppingPlanner.evaluate(
            request,
            listOf(primary),
            emptyList(),
            policy
        )
        val projection = PracticalShoppingUiProjector.project(
            request,
            decision,
            storeNames,
            itemNames,
            policy
        )

        val card = requireNotNull(projection.state.primary)
        assertEquals("Your best practical shop", projection.state.headline)
        assertEquals("BEST ONE-STORE OPTION", card.badge)
        assertEquals("Sample Market", card.storeName)
        assertEquals("Basket 54.80 CAD", card.basketCostText)
        assertEquals("3 of 3 items priced", card.coverageText)
        assertNull(card.missingItemsText)
        assertEquals("7 min · 2.4 km", card.travelText)
        assertEquals("Price freshness: 3 fresh · 0 stale · 0 unknown", card.evidenceText)
        assertEquals(
            "Lowest known complete basket among the one-store options compared.",
            card.whyText
        )
        assertNull(card.notice)
        assertFalse(projection.state.toString().contains(primaryKey.value))
        assertEquals(primaryKey, projection.primaryStoreKey)
    }

    @Test
    fun incompleteDecisionNeverPresentsKnownSubtotalAsCompleteBasket() {
        val primary = single(
            key = primaryKey,
            cost = "32.10",
            covered = setOf(eggs, milk),
            travel = ShoppingTravel(900L, 180L)
        )

        val decision = PracticalShoppingPlanner.evaluate(
            request,
            listOf(primary),
            emptyList(),
            policy
        )
        val state = PracticalShoppingUiProjector.project(
            request,
            decision,
            storeNames,
            itemNames,
            policy
        ).state

        val card = requireNotNull(state.primary)
        assertEquals("Best option with the prices we know", state.headline)
        assertEquals("BEST COVERAGE FOUND", card.badge)
        assertEquals("Known subtotal 32.10 CAD", card.basketCostText)
        assertEquals("2 of 3 items priced", card.coverageText)
        assertEquals("Missing price: Chicken", card.missingItemsText)
        assertEquals(
            "No complete basket is priced yet; this option covers the most requested items.",
            card.whyText
        )
        assertEquals(
            "1 item still has an unknown price. This is not a complete basket total.",
            card.notice
        )
        assertEquals(
            "Not enough complete price coverage to judge another stop fairly.",
            state.secondaryMessage
        )
        assertNull(state.secondStop)
    }

    @Test
    fun recommendedSecondStopProjectsOnlyAlreadyDecidedExactSavings() {
        val primary = single(
            key = primaryKey,
            cost = "70.00",
            covered = requested,
            travel = ShoppingTravel(2_000L, 300L)
        )
        val pair = TwoStorePlanCandidate(
            baseStoreKey = primaryKey,
            addedStoreKey = secondKey,
            coveredItemKeys = requested,
            addedStoreItemKeys = setOf(milk, chicken),
            knownCombinedBasketCost = Money.parse("52.50", "CAD"),
            additionalTravel = ShoppingTravel(1_200L, 181L),
            evidence = freshEvidence(3)
        )

        val decision = PracticalShoppingPlanner.evaluate(
            request,
            listOf(primary),
            listOf(pair),
            policy
        )
        val projection = PracticalShoppingUiProjector.project(
            request,
            decision,
            storeNames,
            itemNames,
            policy
        )

        val second = requireNotNull(projection.state.secondStop)
        assertEquals("OPTIONAL EXTRA STOP", second.badge)
        assertEquals("Example Grocer", second.storeName)
        assertEquals("Combined basket 52.50 CAD", second.combinedBasketCostText)
        assertEquals("Save 17.50 CAD", second.savingsText)
        assertEquals("Adds 4 min · 1.2 km", second.additionalTravelText)
        assertEquals("Price freshness: 3 fresh · 0 stale · 0 unknown", second.evidenceText)
        assertEquals("Buy at Sample Market: Eggs", second.baseItemsText)
        assertEquals("Then buy at Example Grocer: Milk, Chicken", second.addedItemsText)
        assertNull(projection.state.secondaryMessage)
        assertEquals(secondKey, projection.addedStoreKey)
        assertFalse(projection.state.toString().contains(secondKey.value))
    }

    @Test
    fun rejectedSecondStopBecomesOneShortNonRecommendationMessage() {
        val primary = single(
            key = primaryKey,
            cost = "60.00",
            covered = requested,
            travel = ShoppingTravel(1_500L, 240L)
        )
        val pair = TwoStorePlanCandidate(
            baseStoreKey = primaryKey,
            addedStoreKey = secondKey,
            coveredItemKeys = requested,
            addedStoreItemKeys = setOf(chicken),
            knownCombinedBasketCost = Money.parse("53.00", "CAD"),
            additionalTravel = ShoppingTravel(800L, 120L),
            evidence = freshEvidence(3)
        )

        val decision = PracticalShoppingPlanner.evaluate(
            request,
            listOf(primary),
            listOf(pair),
            policy
        )
        val state = PracticalShoppingUiProjector.project(
            request,
            decision,
            storeNames,
            itemNames,
            policy
        ).state

        assertNull(state.secondStop)
        assertEquals(
            "Another stop is not worth it: your current rule requires at least " +
                "15.00 CAD savings and caps extra travel at 10 min and 5 km.",
            state.secondaryMessage
        )
    }

    @Test
    fun secondStopAllocationUsesRequestOrderWhenEveryItemMovesToAddedStore() {
        val primary = single(
            key = primaryKey,
            cost = "60.00",
            covered = requested,
            travel = ShoppingTravel(2_000L, 300L)
        )
        val pair = TwoStorePlanCandidate(
            baseStoreKey = primaryKey,
            addedStoreKey = secondKey,
            coveredItemKeys = requested,
            addedStoreItemKeys = setOf(chicken, eggs, milk),
            knownCombinedBasketCost = Money.parse("40.00", "CAD"),
            additionalTravel = ShoppingTravel(1_200L, 181L),
            evidence = freshEvidence(3)
        )

        val decision = PracticalShoppingPlanner.evaluate(
            request,
            listOf(primary),
            listOf(pair),
            policy
        )
        val second = requireNotNull(
            PracticalShoppingUiProjector.project(
                request,
                decision,
                storeNames,
                itemNames,
                policy
            ).state.secondStop
        )

        assertEquals("Buy at Sample Market: none", second.baseItemsText)
        assertEquals("Then buy at Example Grocer: Eggs, Milk, Chicken", second.addedItemsText)
    }

    @Test
    fun rejectedSecondStopExplainsAnExactTimeOnlyRuleWithoutInventingDistance() {
        val timeOnlyPolicy = policy.copy(
            minimumSecondStopSavings = Money.parse("20.25", "CAD"),
            maxAdditionalTravelSeconds = 61L,
            maxAdditionalDistanceMetres = null
        )
        val primary = single(
            key = primaryKey,
            cost = "60.00",
            covered = requested,
            travel = ShoppingTravel(1_500L, 240L)
        )
        val decision = PracticalShoppingPlanner.evaluate(
            request,
            listOf(primary),
            emptyList(),
            timeOnlyPolicy
        )

        val state = PracticalShoppingUiProjector.project(
            request,
            decision,
            storeNames,
            itemNames,
            timeOnlyPolicy
        ).state

        assertEquals(
            "Another stop is not worth it: your current rule requires at least " +
                "20.25 CAD savings and caps extra travel at 61 sec.",
            state.secondaryMessage
        )
    }

    @Test
    fun noCoverageProjectsAUsefulEmptyStateWithoutFakeBasket() {
        val empty = single(
            key = primaryKey,
            cost = "0.00",
            covered = emptySet(),
            travel = ShoppingTravel(0L, 0L)
        )

        val decision = PracticalShoppingPlanner.evaluate(
            request,
            listOf(empty),
            emptyList(),
            policy
        )
        val projection = PracticalShoppingUiProjector.project(
            request,
            decision,
            storeNames,
            itemNames,
            policy
        )

        assertEquals("Not enough price coverage yet", projection.state.headline)
        assertNull(projection.state.primary)
        assertNull(projection.state.secondStop)
        assertEquals(
            "No requested item has a usable price yet.",
            projection.state.secondaryMessage
        )
        assertNull(projection.primaryStoreKey)
    }

    @Test
    fun missingConsumerStoreNameFailsClosedInsteadOfLeakingInternalKey() {
        val primary = single(
            key = primaryKey,
            cost = "54.80",
            covered = requested,
            travel = ShoppingTravel(1_000L, 120L)
        )
        val decision = PracticalShoppingPlanner.evaluate(
            request,
            listOf(primary),
            emptyList(),
            policy
        )

        assertThrows(IllegalArgumentException::class.java) {
            PracticalShoppingUiProjector.project(
                request,
                decision,
                emptyMap(),
                itemNames,
                policy
            )
        }
    }

    @Test
    fun missingConsumerItemNameFailsClosedInsteadOfLeakingInternalKey() {
        val primary = single(
            key = primaryKey,
            cost = "32.10",
            covered = setOf(eggs, milk),
            travel = ShoppingTravel(900L, 180L)
        )
        val decision = PracticalShoppingPlanner.evaluate(
            request,
            listOf(primary),
            emptyList(),
            policy
        )

        assertThrows(IllegalArgumentException::class.java) {
            PracticalShoppingUiProjector.project(
                request,
                decision,
                storeNames,
                itemNames - chicken,
                policy
            )
        }
    }

    @Test
    fun moneyAndTravelFormattingStayExactAndDeterministic() {
        assertEquals(
            "90071992547409.91 CAD",
            PracticalShoppingUiProjector.formatMoney(
                Money(9_007_199_254_740_991L, "CAD")
            )
        )
        assertEquals(
            "1 min · 999 m",
            PracticalShoppingUiProjector.formatTravel(
                ShoppingTravel(999L, 1L)
            )
        )
        assertEquals(
            "1 min · 1 km",
            PracticalShoppingUiProjector.formatTravel(
                ShoppingTravel(1_000L, 60L)
            )
        )
        assertEquals(
            "2 min · 1.5 km",
            PracticalShoppingUiProjector.formatTravel(
                ShoppingTravel(1_500L, 61L)
            )
        )
    }

    @Test
    fun evidenceFormattingDoesNotUpgradeUnknownOrStaleFacts() {
        val text = PracticalShoppingUiProjector.formatEvidence(
            ShoppingPlanEvidenceSummary(
                freshItemCount = 1,
                staleItemCount = 1,
                unknownFreshnessItemCount = 1
            )
        )

        assertEquals("1 fresh · 1 stale · 1 unknown", text)
        assertTrue(text.contains("unknown"))
        assertTrue(text.contains("stale"))
    }

    @Test
    fun homeEvidenceTextLabelsFreshnessInsteadOfLeavingUnknownAmbiguous() {
        val primary = SingleStorePlanCandidate(
            storeKey = primaryKey,
            coveredItemKeys = setOf(eggs, milk),
            knownBasketCost = Money.parse("32.10", "CAD"),
            travel = ShoppingTravel(900L, 180L),
            evidence = ShoppingPlanEvidenceSummary(
                freshItemCount = 0,
                staleItemCount = 0,
                unknownFreshnessItemCount = 2
            )
        )

        val decision = PracticalShoppingPlanner.evaluate(
            request,
            listOf(primary),
            emptyList(),
            policy
        )
        val card = requireNotNull(
            PracticalShoppingUiProjector.project(
                request,
                decision,
                storeNames,
                itemNames,
                policy
            ).state.primary
        )

        assertEquals("Price freshness: 0 fresh · 0 stale · 2 unknown", card.evidenceText)
    }

    private fun single(
        key: ShoppingStoreKey,
        cost: String,
        covered: Set<ShoppingItemKey>,
        travel: ShoppingTravel
    ): SingleStorePlanCandidate =
        SingleStorePlanCandidate(
            storeKey = key,
            coveredItemKeys = covered,
            knownBasketCost = Money.parse(cost, "CAD"),
            travel = travel,
            evidence = freshEvidence(covered.size)
        )

    private fun freshEvidence(count: Int): ShoppingPlanEvidenceSummary =
        ShoppingPlanEvidenceSummary(
            freshItemCount = count,
            staleItemCount = 0,
            unknownFreshnessItemCount = 0
        )
}
