package com.valuepilot.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalSamplePracticalShoppingDemoTest {

    @Test
    fun naturalListKeepsChickenAmbiguousAndShowsSafeDefaultsForOtherItems() {
        val state = submit("chicken eggs milk").ui

        assertEquals(LocalSamplePracticalShoppingDemo.Status.NEEDS_REFINEMENT, state.status)
        assertEquals(listOf("Eggs", "Milk"), state.items.map { it.name })
        assertEquals(
            listOf("12 large · sample default", "2% · 4 L · sample default"),
            state.items.map { it.detail }
        )
        assertEquals("Chicken", state.chickenClarification?.prompt)
        assertEquals(
            listOf("Breast", "Thighs", "Drumsticks", "Whole chicken", "Ground chicken"),
            state.chickenClarification?.choices?.map { it.label }
        )
        assertTrue(state.unknownItems.isEmpty())
        assertNull(state.result)
        assertEquals("Which chicken do you want?", state.message)
        assertTrue(state.sampleNotice.contains("Fictional sample data only"))
    }

    @Test
    fun explicitChickenChoiceCompletesTheSameListWithoutChangingOtherItems() {
        var model = submit("chicken eggs milk")
        model = LocalSamplePracticalShoppingDemo.reduce(
            model,
            LocalSamplePracticalShoppingDemo.Intent.ChooseChicken(
                LocalSamplePracticalShoppingDemo.ChickenChoice.THIGHS
            )
        )

        val state = model.ui
        val result = requireNotNull(state.result)
        val primary = requireNotNull(result.primary)

        assertEquals(LocalSamplePracticalShoppingDemo.Status.RESULT, state.status)
        assertEquals(listOf("Chicken thighs", "Eggs", "Milk"), state.items.map { it.name })
        assertNull(state.chickenClarification)
        assertTrue(state.unknownItems.isEmpty())
        assertEquals("Example Grocer East", primary.storeName)
        assertEquals("Basket 20.77 CAD", primary.basketCostText)
        assertEquals("3 of 3 items priced", primary.coverageText)
        assertNull(result.secondStop)
        assertEquals(
            "Another stop is not worth it: your current rule requires at least " +
                "15.00 CAD savings and caps extra travel at 10 min and 5 km.",
            result.secondaryMessage
        )
    }

    @Test
    fun explicitChickenPhraseNeedsNoExtraRefinement() {
        val state = submit("chicken breast eggs milk").ui
        val result = requireNotNull(state.result)
        val primary = requireNotNull(result.primary)

        assertEquals(LocalSamplePracticalShoppingDemo.Status.RESULT, state.status)
        assertEquals(listOf("Chicken breast", "Eggs", "Milk"), state.items.map { it.name })
        assertNull(state.chickenClarification)
        assertEquals("Example Grocer East", primary.storeName)
        assertEquals("Basket 24.27 CAD", primary.basketCostText)
        assertEquals("3 of 3 items priced", primary.coverageText)
    }

    @Test
    fun duplicateWordsCollapseBeforeBuildingTheShoppingRequest() {
        val state = submit("eggs eggs, milk milk eggs").ui
        val result = requireNotNull(state.result)
        val primary = requireNotNull(result.primary)

        assertEquals(LocalSamplePracticalShoppingDemo.Status.RESULT, state.status)
        assertEquals(listOf("Eggs", "Milk"), state.items.map { it.name })
        assertEquals("2 of 2 items priced", primary.coverageText)
        assertEquals("Basket 10.28 CAD", primary.basketCostText)
    }

    @Test
    fun unknownItemsRemainVisibleAndBlockAFalseCompleteResult() {
        val state = submit("eggs dragonfruit milk mystery").ui

        assertEquals(LocalSamplePracticalShoppingDemo.Status.NEEDS_REFINEMENT, state.status)
        assertEquals(listOf("Eggs", "Milk"), state.items.map { it.name })
        assertEquals(listOf("dragonfruit", "mystery"), state.unknownItems)
        assertNull(state.result)
        assertEquals(
            "Fix the items this small sample does not recognize.",
            state.message
        )
    }

    @Test
    fun completeStoreCoverageBeatsAnIncompleteCheaperKnownSubtotal() {
        val state = submit("bananas eggs milk").ui
        val result = requireNotNull(state.result)
        val primary = requireNotNull(result.primary)

        assertEquals(LocalSamplePracticalShoppingDemo.Status.RESULT, state.status)
        assertEquals("Your best practical shop", result.headline)
        assertEquals("Sample Market West", primary.storeName)
        assertEquals("Basket 12.27 CAD", primary.basketCostText)
        assertEquals("3 of 3 items priced", primary.coverageText)
        assertNull(primary.notice)
        assertFalse(primary.basketCostText.startsWith("Known subtotal"))
    }

    @Test
    fun incompleteSampleNamesTheItemWhosePriceIsUnknown() {
        val state = submit("eggs coffee").ui
        val result = requireNotNull(state.result)
        val primary = requireNotNull(result.primary)

        assertEquals(LocalSamplePracticalShoppingDemo.Status.RESULT, state.status)
        assertEquals(listOf("Eggs", "Coffee"), state.items.map { it.name })
        assertEquals("Best option with the prices we know", result.headline)
        assertEquals("Known subtotal 4.49 CAD", primary.basketCostText)
        assertEquals("1 of 2 items priced", primary.coverageText)
        assertEquals("Missing price: Coffee", primary.missingItemsText)
        assertEquals(
            "1 item still has an unknown price. This is not a complete basket total.",
            primary.notice
        )
        assertNull(result.secondStop)
    }

    @Test
    fun subThresholdSplitSavingsDoNotCreateASecondStopRecommendation() {
        val state = submit("eggs milk").ui
        val result = requireNotNull(state.result)
        val primary = requireNotNull(result.primary)

        assertEquals("Basket 10.28 CAD", primary.basketCostText)
        assertNull(result.secondStop)
        assertEquals(
            "Another stop is not worth it: your current rule requires at least " +
                "15.00 CAD savings and caps extra travel at 10 min and 5 km.",
            result.secondaryMessage
        )
    }

    @Test
    fun exactMinimumSavingsChoiceReplansWithoutMovingPolicyIntoTheView() {
        var model =
            submit("bananas eggs milk bread rice chicken breast")

        assertNull(model.ui.result?.secondStop)

        model =
            PracticalShoppingHomeSession.chooseExtraStopMinimumSavings(
                model,
                LocalSamplePracticalShoppingDemo.ExtraStopMinimumSavingsChoice.ONE_CAD
            )

        val lowThresholdResult = requireNotNull(model.ui.result)
        val secondStop = requireNotNull(lowThresholdResult.secondStop)
        assertEquals(
            LocalSamplePracticalShoppingDemo.ExtraStopMinimumSavingsChoice.ONE_CAD,
            model.ui.extraStopMinimumSavingsChoice
        )
        assertEquals("Example Grocer East", secondStop.storeName)
        assertEquals("Save 2.50 CAD", secondStop.savingsText)
        assertEquals("Combined basket 43.04 CAD", secondStop.combinedBasketCostText)

        model =
            PracticalShoppingHomeSession.chooseExtraStopMinimumSavings(
                model,
                LocalSamplePracticalShoppingDemo.ExtraStopMinimumSavingsChoice.TWENTY_FIVE_CAD
            )

        val highThresholdResult = requireNotNull(model.ui.result)
        assertNull(highThresholdResult.secondStop)
        assertEquals(
            "Another stop is not worth it: your current rule requires at least " +
                "25.00 CAD savings and caps extra travel at 10 min and 5 km.",
            highThresholdResult.secondaryMessage
        )
    }

    @Test
    fun editingTheListKeepsTheExplicitExtraStopPreference() {
        var model = submit("eggs milk")
        model =
            PracticalShoppingHomeSession.chooseExtraStopMinimumSavings(
                model,
                LocalSamplePracticalShoppingDemo.ExtraStopMinimumSavingsChoice.TWENTY_FIVE_CAD
            )
        model =
            LocalSamplePracticalShoppingDemo.reduce(
                model,
                LocalSamplePracticalShoppingDemo.Intent.QueryChanged("eggs milk bread")
            )

        assertEquals(LocalSamplePracticalShoppingDemo.Status.IDLE, model.ui.status)
        assertEquals(
            LocalSamplePracticalShoppingDemo.ExtraStopMinimumSavingsChoice.TWENTY_FIVE_CAD,
            model.ui.extraStopMinimumSavingsChoice
        )
        assertNull(model.ui.result)
    }

    @Test
    fun queryLengthLimitFailsClosedBeforePlanning() {
        var model = LocalSamplePracticalShoppingDemo.initialModel()
        model = LocalSamplePracticalShoppingDemo.reduce(
            model,
            LocalSamplePracticalShoppingDemo.Intent.QueryChanged("x".repeat(241))
        )

        assertEquals(LocalSamplePracticalShoppingDemo.Status.QUERY_TOO_LONG, model.ui.status)
        assertEquals(241, model.ui.query.length)
        assertNull(model.ui.result)
        assertTrue(model.ui.items.isEmpty())
        assertTrue(model.ui.unknownItems.isEmpty())
    }

    @Test
    fun veryLargeQueryIsBoundedBeforeItCanEnterLifecycleState() {
        val model =
            PracticalShoppingHomeSession.restore(
                PracticalShoppingHomeSession.Snapshot(
                    query = "x".repeat(100_000),
                    wasSubmitted = true,
                    chickenChoice = null
                )
            )

        assertEquals(LocalSamplePracticalShoppingDemo.Status.QUERY_TOO_LONG, model.ui.status)
        assertEquals(241, model.ui.query.length)
        assertEquals(241, PracticalShoppingHomeSession.snapshot(model).query.length)
        assertNull(model.ui.result)
        assertTrue(model.ui.items.isEmpty())
        assertTrue(model.ui.unknownItems.isEmpty())
    }

    @Test
    fun distinctIntentLimitRemainsBoundedAndNeverProducesAPartialPlan() {
        val unknowns = (1..33).joinToString(" ") { "u$it" }
        val state = submit("eggs $unknowns").ui

        assertEquals(LocalSamplePracticalShoppingDemo.Status.NEEDS_REFINEMENT, state.status)
        assertEquals(listOf("Eggs"), state.items.map { it.name })
        assertEquals(31, state.unknownItems.size)
        assertEquals(32, state.items.size + state.unknownItems.size)
        assertNull(state.result)
        assertEquals(
            "Keep this sample list to 32 distinct items or fewer.",
            state.message
        )
    }

    private fun submit(query: String): LocalSamplePracticalShoppingDemo.Model {
        var model = LocalSamplePracticalShoppingDemo.initialModel()
        model = LocalSamplePracticalShoppingDemo.reduce(
            model,
            LocalSamplePracticalShoppingDemo.Intent.QueryChanged(query)
        )
        return LocalSamplePracticalShoppingDemo.reduce(
            model,
            LocalSamplePracticalShoppingDemo.Intent.Submit
        )
    }
}
