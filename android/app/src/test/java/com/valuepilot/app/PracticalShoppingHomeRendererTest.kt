package com.valuepilot.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticalShoppingHomeRendererTest {

    @Test
    fun blankDraftStaysSimpleAndCannotSubmit() {
        val rendered =
            PracticalShoppingHomeRenderer.render(
                LocalSamplePracticalShoppingDemo.initialModel().ui
            )

        assertEquals("", rendered.query)
        assertFalse(rendered.submitEnabled)
        assertEquals(PracticalShoppingHomeMessageTone.NEUTRAL, rendered.messageTone)
        assertTrue(rendered.items.isEmpty())
        assertNull(rendered.refinement)
        assertTrue(rendered.unknownItems.isEmpty())
        assertNull(rendered.result)
        assertEquals("Extra-stop rule · Save at least 15.00 CAD", rendered.extraStopSettings.summary)
        assertEquals(
            listOf("1.00 CAD", "15.00 CAD", "25.00 CAD"),
            rendered.extraStopSettings.choices.map { it.label }
        )
        assertEquals(
            listOf(false, true, false),
            rendered.extraStopSettings.choices.map { it.selected }
        )
        assertTrue(rendered.sampleNotice.contains("Fictional sample data"))
    }

    @Test
    fun sampleDisclosureExplicitlyRejectsLiveInterpretation() {
        val rendered =
            PracticalShoppingHomeRenderer.render(
                LocalSamplePracticalShoppingDemo.initialModel().ui
            )

        assertEquals(
            "Fictional sample data only — not live retailer prices or availability.",
            rendered.sampleNotice
        )
    }

    @Test
    fun chickenRefinementKeepsResolvedItemsAndOneTapChoicesVisible() {
        val model =
            PracticalShoppingHomeSession.submit(
                LocalSamplePracticalShoppingDemo.initialModel(),
                "chicken eggs milk"
            )
        val rendered = PracticalShoppingHomeRenderer.render(model.ui)

        assertTrue(rendered.submitEnabled)
        assertEquals(
            PracticalShoppingHomeMessageTone.ACTION_REQUIRED,
            rendered.messageTone
        )
        assertEquals(listOf("Eggs", "Milk"), rendered.items.map { it.name })
        val refinement = requireNotNull(rendered.refinement)
        assertEquals("Chicken", refinement.prompt)
        assertEquals(
            listOf("Breast", "Thighs", "Drumsticks", "Whole chicken", "Ground chicken"),
            refinement.choices.map { it.label }
        )
        assertEquals(
            LocalSamplePracticalShoppingDemo.ChickenChoice.entries,
            refinement.choices.map { it.choice }
        )
        assertNull(rendered.result)
    }

    @Test
    fun unknownItemsRemainVisibleInsteadOfProducingAFalseCompleteResult() {
        val model =
            PracticalShoppingHomeSession.submit(
                LocalSamplePracticalShoppingDemo.initialModel(),
                "eggs dragonfruit"
            )
        val rendered = PracticalShoppingHomeRenderer.render(model.ui)

        assertEquals(listOf("Eggs"), rendered.items.map { it.name })
        assertEquals(listOf("dragonfruit"), rendered.unknownItems)
        assertEquals(
            PracticalShoppingHomeMessageTone.ACTION_REQUIRED,
            rendered.messageTone
        )
        assertNull(rendered.result)
    }

    @Test
    fun completedHomeResultPassesTheAlreadyProjectedDecisionThroughUnchanged() {
        var model =
            PracticalShoppingHomeSession.submit(
                LocalSamplePracticalShoppingDemo.initialModel(),
                "chicken eggs milk"
            )
        model =
            PracticalShoppingHomeSession.chooseChicken(
                model,
                LocalSamplePracticalShoppingDemo.ChickenChoice.DRUMSTICKS
            )

        val sourceResult = requireNotNull(model.ui.result)
        val rendered = PracticalShoppingHomeRenderer.render(model.ui)

        assertSame(sourceResult, rendered.result)
        assertEquals("Your best practical shop", rendered.result?.headline)
        assertEquals("Example Grocer East", rendered.result?.primary?.storeName)
        assertEquals("Basket 18.77 CAD", rendered.result?.primary?.basketCostText)
        assertNull(rendered.refinement)
        assertTrue(rendered.unknownItems.isEmpty())
    }

    @Test
    fun incompleteHomeResultKeepsTheMissingPriceItemExplicit() {
        val model =
            PracticalShoppingHomeSession.submit(
                LocalSamplePracticalShoppingDemo.initialModel(),
                "eggs coffee"
            )

        val sourceResult = requireNotNull(model.ui.result)
        val rendered = PracticalShoppingHomeRenderer.render(model.ui)

        assertSame(sourceResult, rendered.result)
        assertEquals("Known subtotal 4.49 CAD", rendered.result?.primary?.basketCostText)
        assertEquals("Missing price: Coffee", rendered.result?.primary?.missingItemsText)
        assertTrue(rendered.sampleNotice.contains("Fictional sample data"))
    }

    @Test
    fun selectedExactExtraStopPreferenceIsUiReadyAndRemainsTyped() {
        var model = LocalSamplePracticalShoppingDemo.initialModel()
        model =
            PracticalShoppingHomeSession.chooseExtraStopMinimumSavings(
                model,
                LocalSamplePracticalShoppingDemo.ExtraStopMinimumSavingsChoice.TWENTY_FIVE_CAD
            )

        val rendered = PracticalShoppingHomeRenderer.render(model.ui)

        assertEquals("Extra-stop rule · Save at least 25.00 CAD", rendered.extraStopSettings.summary)
        val selected = rendered.extraStopSettings.choices.single { it.selected }
        assertEquals("25.00 CAD", selected.label)
        assertEquals(
            LocalSamplePracticalShoppingDemo.ExtraStopMinimumSavingsChoice.TWENTY_FIVE_CAD,
            selected.choice
        )
    }

    @Test
    fun overlongQueryIsRenderedAsAnErrorAndCannotSubmit() {
        val tooLong = "x".repeat(241)
        val model =
            LocalSamplePracticalShoppingDemo.reduce(
                LocalSamplePracticalShoppingDemo.initialModel(),
                LocalSamplePracticalShoppingDemo.Intent.QueryChanged(tooLong)
            )
        val rendered = PracticalShoppingHomeRenderer.render(model.ui)

        assertEquals(tooLong, rendered.query)
        assertFalse(rendered.submitEnabled)
        assertEquals(PracticalShoppingHomeMessageTone.ERROR, rendered.messageTone)
        assertTrue(rendered.items.isEmpty())
        assertTrue(rendered.unknownItems.isEmpty())
        assertNull(rendered.result)
    }
}
