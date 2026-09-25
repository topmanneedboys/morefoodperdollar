package com.valuepilot.app

import com.valuepilot.core.ShoppingBrandKey
import com.valuepilot.core.ShoppingBrandPreference
import com.valuepilot.core.ShoppingItemKey
import com.valuepilot.core.ShoppingItemRequestDetail
import com.valuepilot.core.ShoppingProductSpecificity
import com.valuepilot.core.ShoppingRequest
import com.valuepilot.core.ShoppingRequestDetails
import com.valuepilot.core.ShoppingRequestedQuantity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticalShoppingRequestDetailsSessionTest {

    private val milk = ShoppingItemKey("milk")
    private val eggs = ShoppingItemKey("eggs")
    private val bread = ShoppingItemKey("bread")

    @Test
    fun initialSessionHasNoRequestAndNoLifecyclePayload() {
        val state = PracticalShoppingRequestDetailsSession.initial()

        assertNull(state.request)
        assertNull(state.details)
        assertNull(PracticalShoppingRequestDetailsSession.encodedOrNull(state))
    }

    @Test
    fun openRestoresTypedIntentOnlyForTheExactRequest() {
        val request = ShoppingRequest(listOf(milk, eggs))
        val details =
            ShoppingRequestDetails(
                request = request,
                itemDetails =
                    listOf(
                        ShoppingItemRequestDetail(
                            itemKey = milk,
                            productSpecificity = ShoppingProductSpecificity.EXACT_PRODUCT_REQUIRED,
                            requestedQuantity = ShoppingRequestedQuantity(packageCount = 2),
                            brandPreference =
                                ShoppingBrandPreference.exact(ShoppingBrandKey("brand:milk"))
                        )
                    )
            )
        val source =
            PracticalShoppingRequestDetailsSession.withItemDetail(
                PracticalShoppingRequestDetailsSession.open(request),
                requireNotNull(details.detailFor(milk))
            )
        val encoded = requireNotNull(PracticalShoppingRequestDetailsSession.encodedOrNull(source))

        val restored = PracticalShoppingRequestDetailsSession.open(request, encoded)

        assertEquals(details, restored.details)
        assertEquals(request, restored.request)
    }

    @Test
    fun staleReorderedAndPartiallyOverlappingPayloadsStartEmptyForTheNewRequest() {
        val originalRequest = ShoppingRequest(listOf(milk, eggs))
        val original =
            PracticalShoppingRequestDetailsSession.withItemDetail(
                PracticalShoppingRequestDetailsSession.open(originalRequest),
                ShoppingItemRequestDetail(itemKey = milk)
            )
        val encoded = requireNotNull(PracticalShoppingRequestDetailsSession.encodedOrNull(original))

        val reordered =
            PracticalShoppingRequestDetailsSession.open(
                ShoppingRequest(listOf(eggs, milk)),
                encoded
            )
        val partial =
            PracticalShoppingRequestDetailsSession.open(
                ShoppingRequest(listOf(milk)),
                encoded
            )
        val overlapWithNewItem =
            PracticalShoppingRequestDetailsSession.open(
                ShoppingRequest(listOf(milk, bread)),
                encoded
            )

        assertTrue(requireNotNull(reordered.details).itemDetails.isEmpty())
        assertTrue(requireNotNull(partial.details).itemDetails.isEmpty())
        assertTrue(requireNotNull(overlapWithNewItem.details).itemDetails.isEmpty())
    }

    @Test
    fun editsUseSharedCoreOperationsAndLeavePreviousStateUnchanged() {
        val request = ShoppingRequest(listOf(milk, eggs))
        val initial = PracticalShoppingRequestDetailsSession.open(request)
        val edited =
            PracticalShoppingRequestDetailsSession.withItemDetail(
                initial,
                ShoppingItemRequestDetail(itemKey = milk)
            )
        val cleared = PracticalShoppingRequestDetailsSession.withoutItemDetail(edited, milk)

        assertNull(initial.details?.detailFor(milk))
        assertTrue(requireNotNull(edited.details).detailFor(milk) != null)
        assertNull(cleared.details?.detailFor(milk))
        assertSame(request, requireNotNull(edited.details).request)
    }

    @Test
    fun explicitReconcilePreservesOnlySurvivingDetailsAndLeavesNewItemsUnspecified() {
        val originalRequest = ShoppingRequest(listOf(milk, eggs))
        var state = PracticalShoppingRequestDetailsSession.open(originalRequest)
        state =
            PracticalShoppingRequestDetailsSession.withItemDetail(
                state,
                ShoppingItemRequestDetail(itemKey = milk)
            )
        state =
            PracticalShoppingRequestDetailsSession.withItemDetail(
                state,
                ShoppingItemRequestDetail(itemKey = eggs)
            )
        val newRequest = ShoppingRequest(listOf(eggs, bread))

        val reconciled = PracticalShoppingRequestDetailsSession.reconcileTo(state, newRequest)

        assertSame(newRequest, requireNotNull(reconciled.details).request)
        assertEquals(listOf(eggs), requireNotNull(reconciled.details).itemDetails.map { it.itemKey })
        assertNull(requireNotNull(reconciled.details).detailFor(bread))
        assertEquals(listOf(milk, eggs), requireNotNull(state.details).itemDetails.map { it.itemKey })
    }

    @Test
    fun reconcileFromAnUnopenedSessionCreatesEmptyDetailsForTheRequestedRequest() {
        val request = ShoppingRequest(listOf(bread))

        val reconciled =
            PracticalShoppingRequestDetailsSession.reconcileTo(
                PracticalShoppingRequestDetailsSession.initial(),
                request
            )

        assertSame(request, requireNotNull(reconciled.details).request)
        assertTrue(requireNotNull(reconciled.details).itemDetails.isEmpty())
    }

    @Test
    fun malformedLifecycleStateFailsClosedToEmptyDetailsForRequestedRequest() {
        val request = ShoppingRequest(listOf(milk))

        val restored =
            PracticalShoppingRequestDetailsSession.open(
                request,
                byteArrayOf(0x7f, 0x00, 0x01)
            )

        assertSame(request, requireNotNull(restored.details).request)
        assertTrue(requireNotNull(restored.details).itemDetails.isEmpty())
    }

    @Test
    fun persistenceFailureDoesNotChangeInMemoryIntent() {
        val oversizedKey = ShoppingItemKey("x".repeat(513))
        val request = ShoppingRequest(listOf(oversizedKey))
        val state = PracticalShoppingRequestDetailsSession.open(request)

        assertNull(PracticalShoppingRequestDetailsSession.encodedOrNull(state))
        assertEquals(ShoppingRequestDetails(request), state.details)
    }
}
