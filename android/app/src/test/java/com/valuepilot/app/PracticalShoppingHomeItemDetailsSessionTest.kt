package com.valuepilot.app

import com.valuepilot.core.ShoppingBrandKey
import com.valuepilot.core.ShoppingBrandPreference
import com.valuepilot.core.ShoppingItemKey
import com.valuepilot.core.ShoppingItemRequestDetail
import com.valuepilot.core.ShoppingProductSpecificity
import com.valuepilot.core.ShoppingRequestedQuantity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticalShoppingHomeItemDetailsSessionTest {

    private val eggs = ShoppingItemKey("sample-eggs-large-12")
    private val milk = ShoppingItemKey("sample-milk-2pct-4l")

    private val milkDetail =
        ShoppingItemRequestDetail(
            itemKey = milk,
            productSpecificity = ShoppingProductSpecificity.EXACT_PRODUCT_REQUIRED,
            requestedQuantity = ShoppingRequestedQuantity(packageCount = 2L),
            brandPreference = ShoppingBrandPreference.exact(ShoppingBrandKey("sample-brand"))
        )

    private val eggsDetail =
        ShoppingItemRequestDetail(
            itemKey = eggs,
            requestedQuantity = ShoppingRequestedQuantity(packageCount = 3L)
        )

    @Test
    fun explicitDetailsStaySeparateFromTheExistingPlanArithmetic() {
        val planned =
            PracticalShoppingHomeSession.submit(
                PracticalShoppingHomeSession.initialState(),
                "eggs milk"
            )
        val before = requireNotNull(planned.model.ui.result)

        val detailed = PracticalShoppingHomeSession.withItemDetail(planned, milkDetail)

        assertSame(before, detailed.model.ui.result)
        assertEquals(milkDetail, detailed.requestDetails.details?.detailFor(milk))
        assertEquals(ShoppingRequestedQuantity(packageCount = 2L), milkDetail.requestedQuantity)
    }

    @Test
    fun explicitDetailsRoundTripThroughHomeLifecycleSnapshotForExactRequest() {
        var state =
            PracticalShoppingHomeSession.submit(
                PracticalShoppingHomeSession.initialState(),
                "eggs milk"
            )
        state = PracticalShoppingHomeSession.withItemDetail(state, milkDetail)
        state = PracticalShoppingHomeSession.withItemDetail(state, eggsDetail)

        val restored =
            PracticalShoppingHomeSession.restoreState(
                PracticalShoppingHomeSession.snapshot(state)
            )

        assertEquals(state.model.ui, restored.model.ui)
        assertEquals(milkDetail, restored.requestDetails.details?.detailFor(milk))
        assertEquals(eggsDetail, restored.requestDetails.details?.detailFor(eggs))
    }

    @Test
    fun removingAnItemPrunesOnlyThatItemDetail() {
        var state =
            PracticalShoppingHomeSession.submit(
                PracticalShoppingHomeSession.initialState(),
                "eggs milk"
            )
        state = PracticalShoppingHomeSession.withItemDetail(state, milkDetail)
        state = PracticalShoppingHomeSession.withItemDetail(state, eggsDetail)

        val removed = PracticalShoppingHomeSession.removeItem(state, eggs)

        assertNull(removed.requestDetails.details?.detailFor(eggs))
        assertEquals(milkDetail, removed.requestDetails.details?.detailFor(milk))
        assertEquals(listOf(milk), removed.requestDetails.details?.request?.itemKeys)
    }

    @Test
    fun submittingARevisedListPrunesRemovedKeysAndRetainsSurvivingKeys() {
        var state =
            PracticalShoppingHomeSession.submit(
                PracticalShoppingHomeSession.initialState(),
                "eggs milk"
            )
        state = PracticalShoppingHomeSession.withItemDetail(state, milkDetail)
        state = PracticalShoppingHomeSession.withItemDetail(state, eggsDetail)

        val revised = PracticalShoppingHomeSession.submit(state, "milk")

        assertEquals(listOf(milk), revised.requestDetails.details?.request?.itemKeys)
        assertEquals(milkDetail, revised.requestDetails.details?.detailFor(milk))
        assertNull(revised.requestDetails.details?.detailFor(eggs))
    }

    @Test
    fun unsubmittedDraftDoesNotResurrectDetailsFromAnOlderRequest() {
        var state =
            PracticalShoppingHomeSession.submit(
                PracticalShoppingHomeSession.initialState(),
                "eggs milk"
            )
        state = PracticalShoppingHomeSession.withItemDetail(state, milkDetail)

        val restored =
            PracticalShoppingHomeSession.restoreState(
                PracticalShoppingHomeSession.snapshot(
                    PracticalShoppingHomeSession.queryChanged(state, "milk")
                )
            )

        assertEquals(LocalSamplePracticalShoppingDemo.Status.IDLE, restored.model.ui.status)
        assertNull(restored.model.ui.result)
        assertNull(restored.requestDetails.details)
    }

    @Test
    fun unsubmittedDraftSnapshotDoesNotPersistOlderRequestDetailsBytes() {
        var state =
            PracticalShoppingHomeSession.submit(
                PracticalShoppingHomeSession.initialState(),
                "eggs milk"
            )
        state = PracticalShoppingHomeSession.withItemDetail(state, milkDetail)

        val draft = PracticalShoppingHomeSession.queryChanged(state, "milk")

        assertNull(PracticalShoppingHomeSession.snapshot(draft).requestDetailsLifecycleState)
    }

    @Test
    fun malformedLifecycleDetailsFailClosedWithoutChangingRestoredPlan() {
        val state =
            PracticalShoppingHomeSession.submit(
                PracticalShoppingHomeSession.initialState(),
                "eggs milk"
            )
        val snapshot =
            PracticalShoppingHomeSession.snapshot(state).copy(
                requestDetailsLifecycleState = "not-a-valid-details-payload".toByteArray()
            )

        val restored = PracticalShoppingHomeSession.restoreState(snapshot)

        assertEquals(state.model.ui, restored.model.ui)
        assertTrue(restored.requestDetails.details?.itemDetails?.isEmpty() == true)
    }
}
