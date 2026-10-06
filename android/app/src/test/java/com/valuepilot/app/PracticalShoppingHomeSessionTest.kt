package com.valuepilot.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticalShoppingHomeSessionTest {

    private fun identity(
        key: String,
        name: String = "Yerba Mate",
        brand: String? = "Marca",
        source: String? = "Argentina SEPA / Precios Claros",
        quantity: String? = "1 kg"
    ) = PracticalShoppingPrivateIdentity(
        identityKey = key,
        displayName = name,
        brand = brand,
        sourceLabel = source,
        packageQuantityLabel = quantity
    )

    @Test
    fun privateSearchIdentityIsRetainedWithoutChangingFictionalPlanner() {
        val initial = PracticalShoppingHomeSession.initialState()
        val withIdentity =
            PracticalShoppingHomeSession.addPrivateIdentity(initial, identity("sepa:yerba:1"))

        assertEquals(initial.model, withIdentity.model)
        assertEquals(listOf("sepa:yerba:1"), withIdentity.privateIdentities.map { it.identityKey })
        assertEquals("Yerba Mate", withIdentity.privateIdentities.single().displayName)
    }

    @Test
    fun exactKeyDeduplicatesButDistinctVariantsRemainSeparate() {
        val first = identity("sepa:yerba:1", quantity = "1 kg")
        val sameKeyDifferentMetadata = identity("sepa:yerba:1", quantity = "500 g")
        val variant = identity("sepa:yerba:2", quantity = "500 g")

        val withFirst = PracticalShoppingHomeSession.addPrivateIdentity(
            PracticalShoppingHomeSession.initialState(),
            first
        )
        val deduped = PracticalShoppingHomeSession.addPrivateIdentity(withFirst, sameKeyDifferentMetadata)
        val withVariant = PracticalShoppingHomeSession.addPrivateIdentity(deduped, variant)

        assertEquals(withFirst, deduped)
        assertEquals(listOf(first, variant), withVariant.privateIdentities)
    }

    @Test
    fun snapshotRestorePreservesMissingMetadataAndRemovalIsExact() {
        val sourceMissing = identity(
            key = "sepa:unknown-size",
            name = "Product without size",
            brand = null,
            source = "Argentina SEPA / Precios Claros",
            quantity = null
        )
        val state = PracticalShoppingHomeSession.addPrivateIdentity(
            PracticalShoppingHomeSession.queryChanged(
                PracticalShoppingHomeSession.initialState(),
                "milk"
            ),
            sourceMissing
        )

        val restored = PracticalShoppingHomeSession.restoreState(
            PracticalShoppingHomeSession.snapshot(state)
        )
        assertEquals("milk", restored.model.ui.query)
        assertEquals(listOf(sourceMissing), restored.privateIdentities)

        val removed = PracticalShoppingHomeSession.removePrivateIdentity(restored, sourceMissing.identityKey)
        assertTrue(removed.privateIdentities.isEmpty())
        assertEquals(restored.model, removed.model)
    }

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

    @Test
    fun exactExtraStopPreferenceRestoresWithTheCompletedPlan() {
        var model =
            PracticalShoppingHomeSession.submit(
                LocalSamplePracticalShoppingDemo.initialModel(),
                "bananas eggs milk bread rice chicken breast"
            )
        model =
            PracticalShoppingHomeSession.chooseExtraStopMinimumSavings(
                model,
                LocalSamplePracticalShoppingDemo.ExtraStopMinimumSavingsChoice.ONE_CAD
            )

        val snapshot = PracticalShoppingHomeSession.snapshot(model)
        val restored = PracticalShoppingHomeSession.restore(snapshot)

        assertEquals(
            LocalSamplePracticalShoppingDemo.ExtraStopMinimumSavingsChoice.ONE_CAD,
            snapshot.extraStopMinimumSavingsChoice
        )
        assertEquals(
            LocalSamplePracticalShoppingDemo.ExtraStopMinimumSavingsChoice.ONE_CAD,
            restored.ui.extraStopMinimumSavingsChoice
        )
        assertEquals("Could save 2.50 CAD", restored.ui.result?.secondStop?.savingsText)
    }

    @Test
    fun shopAgainReplaysOnlyTheCompletedListAndPreservesItsTypedDetails() {
        val state =
            PracticalShoppingHomeSession.submit(
                PracticalShoppingHomeSession.initialState(),
                "eggs milk"
            )
        val replayed = PracticalShoppingHomeSession.shopAgain(state)

        assertEquals(LocalSamplePracticalShoppingDemo.Status.RESULT, replayed.model.ui.status)
        assertEquals("eggs milk", replayed.model.ui.query)
        assertEquals(state.model.ui.items, replayed.model.ui.items)
        assertEquals(state.model.ui.result, replayed.model.ui.result)
        assertEquals(state.requestDetails, replayed.requestDetails)
    }

    @Test
    fun shopAgainPreservesAnExplicitRefinementChoice() {
        val submitted =
            PracticalShoppingHomeSession.submit(
                PracticalShoppingHomeSession.initialState(),
                "chicken eggs milk"
            )
        val completed =
            PracticalShoppingHomeSession.chooseChicken(
                submitted,
                LocalSamplePracticalShoppingDemo.ChickenChoice.DRUMSTICKS
            )
        val replayed = PracticalShoppingHomeSession.shopAgain(completed)

        assertEquals(LocalSamplePracticalShoppingDemo.Status.RESULT, replayed.model.ui.status)
        assertEquals(completed.model.ui.query, replayed.model.ui.query)
        assertEquals(completed.model.ui.items, replayed.model.ui.items)
        assertEquals(completed.model.ui.result, replayed.model.ui.result)
        assertEquals(completed.model.selectedChicken, replayed.model.selectedChicken)
        assertEquals(completed.requestDetails, replayed.requestDetails)
    }

    @Test
    fun `shop again is a no-op before a completed result`() {
        val idle = PracticalShoppingHomeSession.initialState()
        val draft =
            PracticalShoppingHomeSession.queryChanged(
                idle,
                "eggs milk"
            )

        assertSame(idle, PracticalShoppingHomeSession.shopAgain(idle))
        assertSame(draft, PracticalShoppingHomeSession.shopAgain(draft))

        val refinement =
            PracticalShoppingHomeSession.submit(
                idle,
                "chicken eggs"
            )
        assertSame(refinement, PracticalShoppingHomeSession.shopAgain(refinement))
    }
}
