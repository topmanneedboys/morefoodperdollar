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
            "Another stop is not worth it under your current savings and travel limits.",
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
    fun subThresholdSplitSavingsDoNotCreateASecondStopRecommendation() {
        val state = submit("eggs milk").ui
        val result = requireNotNull(state.result)
        val primary = requireNotNull(result.primary)

        assertEquals("Basket 10.28 CAD", primary.basketCostText)
        assertNull(result.secondStop)
        assertEquals(
            "Another stop is not worth it under your current savings and travel limits.",
            result.secondaryMessage
        )
    }

    @Test
    fun queryLengthLimitFailsClosedBeforePlanning() {
        var model = LocalSamplePracticalShoppingDemo.initialModel()
        model = LocalSamplePracticalShoppingDemo.reduce(
            model,
            LocalSamplePracticalShoppingDemo.Intent.QueryChanged("x".repeat(241))
        )

        assertEquals(LocalSamplePracticalShoppingDemo.Status.QUERY_TOO_LONG, model.ui.status)
        assertNull(model.ui.result)
        assertTrue(model.ui.items.isEmpty())
        assertTrue(model.ui.unknownItems.isEmpty())
    }

    @Test
    fun distinctIntentLimitRemainsBoundedAndNeverProducesAPartialPlan() {
        val unknowns = (1..33).joinToString(" ") { "unknown$it" }
        val state = submit("eggs $unknowns").ui

        assertEquals(LocalSamplePracticalShoppingDemo.Status.NEEDS_REFINEMENT, state.status)
        assertEquals(listOf("Eggs"), state.items.map { it.name })
        assertEquals(32, state.unknownItems.size)
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
