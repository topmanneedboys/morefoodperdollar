package com.valuepilot.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticalShoppingHomeSessionTest {

    @Test
    fun typedButUnsubmittedQueryRestoresAsIdleDraft() {
        var model = LocalSamplePracticalShoppingDemo.initialModel()
        model =
            LocalSamplePracticalShoppingDemo.reduce(
                model,
                LocalSamplePracticalShoppingDemo.Intent.QueryChanged("chicken eggs milk")
            )

        val restored =
            PracticalShoppingHomeSession.restore(
                PracticalShoppingHomeSession.snapshot(model)
            )

        assertEquals("chicken eggs milk", restored.ui.query)
        assertEquals(LocalSamplePracticalShoppingDemo.Status.IDLE, restored.ui.status)
        assertNull(restored.ui.result)
        assertNull(restored.ui.chickenClarification)
    }

    @Test
    fun submittedAmbiguousListRestoresItsRefinementState() {
        val model =
            PracticalShoppingHomeSession.submit(
                LocalSamplePracticalShoppingDemo.initialModel(),
                "chicken eggs milk"
            )

        val restored =
            PracticalShoppingHomeSession.restore(
                PracticalShoppingHomeSession.snapshot(model)
            )

        assertEquals(LocalSamplePracticalShoppingDemo.Status.NEEDS_REFINEMENT, restored.ui.status)
        assertEquals("Chicken", restored.ui.chickenClarification?.prompt)
        assertEquals(listOf("Eggs", "Milk"), restored.ui.items.map { it.name })
        assertNull(restored.ui.result)
    }

    @Test
    fun chosenChickenRestoresTheExactCompletedHomeFlow() {
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

        val snapshot = PracticalShoppingHomeSession.snapshot(model)
        val restored = PracticalShoppingHomeSession.restore(snapshot)

        assertEquals(LocalSamplePracticalShoppingDemo.ChickenChoice.DRUMSTICKS, snapshot.chickenChoice)
        assertTrue(snapshot.wasSubmitted)
        assertEquals(LocalSamplePracticalShoppingDemo.Status.RESULT, restored.ui.status)
        assertEquals(
            listOf("Chicken drumsticks", "Eggs", "Milk"),
            restored.ui.items.map { it.name }
        )
        assertEquals("Example Grocer East", restored.ui.result?.primary?.storeName)
        assertEquals("Basket 18.77 CAD", restored.ui.result?.primary?.basketCostText)
    }

    @Test
    fun changingTheQueryAfterAChoiceDropsTheOldChickenSelection() {
        var model =
            PracticalShoppingHomeSession.submit(
                LocalSamplePracticalShoppingDemo.initialModel(),
                "chicken eggs milk"
            )
        model =
            PracticalShoppingHomeSession.chooseChicken(
                model,
                LocalSamplePracticalShoppingDemo.ChickenChoice.BREAST
            )
        model =
            LocalSamplePracticalShoppingDemo.reduce(
                model,
                LocalSamplePracticalShoppingDemo.Intent.QueryChanged("eggs milk")
            )

        val snapshot = PracticalShoppingHomeSession.snapshot(model)

        assertNull(snapshot.chickenChoice)
        assertEquals(LocalSamplePracticalShoppingDemo.Status.IDLE, model.ui.status)
        assertNull(model.ui.result)
    }
}
