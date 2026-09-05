package com.valuepilot.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticalShoppingHomeShareCardTest {

    @Test
    fun completePlanSharesOnlyProjectedFactsAndExplicitDemoDisclosure() {
        val card = project("eggs milk")

        assertNotNull(card)
        val text = requireNotNull(card).text
        assertTrue(text.contains("Basket 10.28 CAD"))
        assertTrue(text.contains("Example Grocer East"))
        assertTrue(text.contains("2 of 2 items priced"))
        assertTrue(text.contains("Fictional sample data only — not live retailer prices or availability."))
        assertTrue(text.contains("no item names or private price history"))
        assertFalse(text.contains("Eggs"))
        assertFalse(text.contains("Milk"))
        assertFalse(text.contains("private price history:"))
    }

    @Test
    fun incompletePlanKeepsKnownSubtotalAndIncompleteDisclosureWithoutItemNames() {
        val card = project("eggs coffee")

        assertNotNull(card)
        val text = requireNotNull(card).text
        assertTrue(text.contains("Known subtotal 4.49 CAD"))
        assertTrue(text.contains("1 of 2 items priced"))
        assertTrue(text.contains("This is not a complete basket total."))
        assertTrue(text.contains("Fictional sample data only"))
        assertFalse(text.contains("Coffee"))
        assertFalse(text.contains("Eggs"))
    }

    @Test
    fun noCoverageResultCannotProduceAStorePlanShare() {
        assertNull(project("coffee"))
    }

    @Test
    fun optionalSecondStopUsesExistingSavingsAndTravelFactsWithoutItemNames() {
        var model =
            PracticalShoppingHomeSession.submit(
                PracticalShoppingHomeSession.initialState(),
                "bananas eggs milk bread rice chicken breast"
            )
        model =
            PracticalShoppingHomeSession.chooseExtraStopMinimumSavings(
                model,
                LocalSamplePracticalShoppingDemo.ExtraStopMinimumSavingsChoice.ONE_CAD
            )

        val rendered = PracticalShoppingHomeRenderer.render(model.model.ui)
        val result = requireNotNull(rendered.result)
        val secondStop = requireNotNull(result.secondStop)
        val card =
            PracticalShoppingHomeShareCardProjector.project(
                result,
                rendered.sampleNotice
            )

        assertNotNull(card)
        val text = requireNotNull(card).text
        assertTrue(text.contains("Optional second stop at Example Grocer East"))
        assertTrue(text.contains("Could save 2.50 CAD"))
        assertTrue(text.contains(secondStop.additionalTravelText))
        assertTrue(text.contains(secondStop.evidenceText))
        assertFalse(text.contains("Bananas"))
        assertFalse(text.contains("Chicken breast"))
    }

    @Test
    fun unsafeDisclosureOrFactFailsClosedInsteadOfProducingAmbiguousShare() {
        val rendered = PracticalShoppingHomeRenderer.render(
            PracticalShoppingHomeSession.submit(
                PracticalShoppingHomeSession.initialState(),
                "eggs coffee"
            ).model.ui
        )
        val result = requireNotNull(rendered.result)

        assertNull(
            PracticalShoppingHomeShareCardProjector.project(
                result,
                "Fictional sample data only\u0000"
            )
        )
        assertNull(
            PracticalShoppingHomeShareCardProjector.project(
                result.copy(
                    primary = requireNotNull(result.primary).copy(
                        notice = "This is not a complete basket total.\u0000"
                    )
                ),
                rendered.sampleNotice
            )
        )
    }

    private fun project(query: String): PracticalShoppingHomeShareCard? {
        val rendered =
            PracticalShoppingHomeRenderer.render(
                PracticalShoppingHomeSession.submit(
                    PracticalShoppingHomeSession.initialState(),
                    query
                ).model.ui
            )
        return rendered.result?.let { result ->
            PracticalShoppingHomeShareCardProjector.project(result, rendered.sampleNotice)
        }
    }
}
