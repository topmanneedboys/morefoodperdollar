package com.valuepilot.app

import com.valuepilot.core.ShoppingItemKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticalShoppingBasketProgressSessionTest {

    private val eggs = ShoppingItemKey("eggs")
    private val milk = ShoppingItemKey("milk")
    private val coffee = ShoppingItemKey("coffee")

    @Test
    fun emptySessionHasNoEligibilityOrCollectedState() {
        val state = PracticalShoppingBasketProgressSession.initial()

        assertTrue(state.eligibleItemKeys.isEmpty())
        assertTrue(state.collectedItemKeys.isEmpty())
    }

    @Test
    fun reconcileDeduplicatesIdentityAndPreservesOnlyStillEligibleCollectedItems() {
        var state =
            PracticalShoppingBasketProgressSession.reconcile(
                PracticalShoppingBasketProgressSession.initial(),
                listOf(eggs, milk)
            )
        state = PracticalShoppingBasketProgressSession.toggle(state, eggs)

        val reconciled =
            PracticalShoppingBasketProgressSession.reconcile(
                state,
                listOf(eggs, eggs, coffee)
            )

        assertEquals(linkedSetOf(eggs, coffee), reconciled.eligibleItemKeys)
        assertEquals(setOf(eggs), reconciled.collectedItemKeys)
    }

    @Test
    fun togglingEligibleItemTwiceReturnsToNotCollected() {
        val initial =
            PracticalShoppingBasketProgressSession.reconcile(
                PracticalShoppingBasketProgressSession.initial(),
                listOf(eggs)
            )

        val collected = PracticalShoppingBasketProgressSession.toggle(initial, eggs)
        val restored = PracticalShoppingBasketProgressSession.toggle(collected, eggs)

        assertEquals(setOf(eggs), collected.collectedItemKeys)
        assertTrue(restored.collectedItemKeys.isEmpty())
        assertEquals(initial.eligibleItemKeys, restored.eligibleItemKeys)
    }

    @Test
    fun clearingCollectedMarksPreservesEligibilityAndResetsOnlyForegroundProgress() {
        var state =
            PracticalShoppingBasketProgressSession.reconcile(
                PracticalShoppingBasketProgressSession.initial(),
                listOf(eggs, milk)
            )
        state = PracticalShoppingBasketProgressSession.toggle(state, eggs)
        state = PracticalShoppingBasketProgressSession.toggle(state, milk)

        val cleared = PracticalShoppingBasketProgressSession.clearCollected(state)

        assertEquals(state.eligibleItemKeys, cleared.eligibleItemKeys)
        assertTrue(cleared.collectedItemKeys.isEmpty())
    }

    @Test
    fun unknownIdentityFailsClosedWithoutMutation() {
        val state =
            PracticalShoppingBasketProgressSession.reconcile(
                PracticalShoppingBasketProgressSession.initial(),
                listOf(eggs)
            )

        val result = PracticalShoppingBasketProgressSession.toggle(state, coffee)

        assertSame(state, result)
        assertTrue(result.collectedItemKeys.isEmpty())
    }

    @Test
    fun planBecomingIneligibleClearsCollectedProgress() {
        var state =
            PracticalShoppingBasketProgressSession.reconcile(
                PracticalShoppingBasketProgressSession.initial(),
                listOf(eggs, milk)
            )
        state = PracticalShoppingBasketProgressSession.toggle(state, eggs)

        val cleared = PracticalShoppingBasketProgressSession.reconcile(state, emptyList())

        assertTrue(cleared.eligibleItemKeys.isEmpty())
        assertTrue(cleared.collectedItemKeys.isEmpty())
    }

    @Test
    fun repeatedRenderEligibilityPreservesForegroundProgress() {
        var state =
            PracticalShoppingBasketProgressSession.reconcile(
                PracticalShoppingBasketProgressSession.initial(),
                listOf(eggs, milk)
            )
        state = PracticalShoppingBasketProgressSession.toggle(state, milk)

        val rerendered =
            PracticalShoppingBasketProgressSession.reconcile(state, listOf(eggs, milk))

        assertEquals(setOf(milk), rerendered.collectedItemKeys)
    }

    @Test
    fun snapshotIsStableAndRestoreIntersectsWithCurrentEligibility() {
        var state =
            PracticalShoppingBasketProgressSession.reconcile(
                PracticalShoppingBasketProgressSession.initial(),
                listOf(milk, eggs)
            )
        state = PracticalShoppingBasketProgressSession.toggle(state, milk)
        state = PracticalShoppingBasketProgressSession.toggle(state, eggs)

        val snapshot = PracticalShoppingBasketProgressSession.snapshot(state)
        val restored =
            PracticalShoppingBasketProgressSession.restore(
                collectedItemKeyValues = snapshot,
                eligibleItemKeys = listOf(eggs, coffee)
            )

        assertEquals(listOf("eggs", "milk"), snapshot)
        assertEquals(setOf(eggs, coffee), restored.eligibleItemKeys)
        assertEquals(setOf(eggs), restored.collectedItemKeys)
    }

    @Test
    fun malformedSavedStateFailsClosed() {
        val eligible = listOf(eggs, milk)

        val blank =
            PracticalShoppingBasketProgressSession.restore(
                collectedItemKeyValues = listOf("eggs", ""),
                eligibleItemKeys = eligible
            )
        val duplicate =
            PracticalShoppingBasketProgressSession.restore(
                collectedItemKeyValues = listOf("eggs", "eggs"),
                eligibleItemKeys = eligible
            )
        val oversized =
            PracticalShoppingBasketProgressSession.restore(
                collectedItemKeyValues = (0..128).map { "item-$it" },
                eligibleItemKeys = eligible
            )

        assertTrue(blank.collectedItemKeys.isEmpty())
        assertTrue(duplicate.collectedItemKeys.isEmpty())
        assertTrue(oversized.collectedItemKeys.isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun oversizedCurrentEligibilityIsRejected() {
        PracticalShoppingBasketProgressSession.reconcile(
            PracticalShoppingBasketProgressSession.initial(),
            (0..128).map { ShoppingItemKey("item-$it") }
        )
    }
}
