package com.valuepilot.app

import com.valuepilot.core.ShoppingBrandKey
import com.valuepilot.core.ShoppingBrandPreference
import com.valuepilot.core.ShoppingItemKey
import com.valuepilot.core.ShoppingItemRequestDetail
import com.valuepilot.core.ShoppingProductSpecificity
import com.valuepilot.core.ShoppingRequest
import com.valuepilot.core.ShoppingRequestDetails
import com.valuepilot.core.ShoppingRequestDetailsCodec
import com.valuepilot.core.ShoppingRequestedQuantity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PracticalShoppingRequestDetailsLifecycleCapsuleTest {

    @Test
    fun exactRequestCanRecoverTypedDetailsAcrossTheBytesBoundary() {
        val milk = ShoppingItemKey("milk:whole")
        val bread = ShoppingItemKey("bread")
        val request = ShoppingRequest(listOf(milk, bread))
        val details =
            ShoppingRequestDetails(
                request = request,
                itemDetails =
                    listOf(
                        ShoppingItemRequestDetail(
                            itemKey = milk,
                            productSpecificity = ShoppingProductSpecificity.EXACT_PRODUCT_REQUIRED,
                            requestedQuantity = ShoppingRequestedQuantity(packageCount = 2L),
                            brandPreference =
                                ShoppingBrandPreference.exact(
                                    ShoppingBrandKey("brand:exact-dairy")
                                )
                        )
                    )
            )

        val encoded =
            requireNotNull(
                PracticalShoppingRequestDetailsLifecycleCapsule
                    .fromDetails(details)
                    .encodedOrNull()
            )
        val restored = PracticalShoppingRequestDetailsLifecycleCapsule.restore(encoded)

        assertEquals(details, restored.detailsForExactRequest(request))
    }

    @Test
    fun differentRequestCannotClaimRestoredDetailsEvenWithItemOverlap() {
        val milk = ShoppingItemKey("milk")
        val bread = ShoppingItemKey("bread")
        val originalRequest = ShoppingRequest(listOf(milk, bread))
        val details =
            ShoppingRequestDetails(
                request = originalRequest,
                itemDetails = listOf(ShoppingItemRequestDetail(itemKey = milk))
            )
        val encoded =
            requireNotNull(
                PracticalShoppingRequestDetailsLifecycleCapsule
                    .fromDetails(details)
                    .encodedOrNull()
            )
        val restored = PracticalShoppingRequestDetailsLifecycleCapsule.restore(encoded)

        assertNull(restored.detailsForExactRequest(ShoppingRequest(listOf(milk))))
    }

    @Test
    fun reorderedRequestCannotClaimRestoredDetails() {
        val milk = ShoppingItemKey("milk")
        val bread = ShoppingItemKey("bread")
        val originalRequest = ShoppingRequest(listOf(milk, bread))
        val details = ShoppingRequestDetails(request = originalRequest)
        val encoded =
            requireNotNull(
                PracticalShoppingRequestDetailsLifecycleCapsule
                    .fromDetails(details)
                    .encodedOrNull()
            )
        val restored = PracticalShoppingRequestDetailsLifecycleCapsule.restore(encoded)

        assertNull(restored.detailsForExactRequest(ShoppingRequest(listOf(bread, milk))))
    }

    @Test
    fun malformedPayloadFailsClosedWithoutLeakingPartialIntent() {
        val request = ShoppingRequest(listOf(ShoppingItemKey("milk")))
        val restored =
            PracticalShoppingRequestDetailsLifecycleCapsule.restore(
                byteArrayOf(0x7f, 0x00, 0x01)
            )

        assertNull(restored.detailsForExactRequest(request))
        assertNull(restored.encodedOrNull())
    }

    @Test
    fun oversizedPayloadFailsClosedBeforeAnyRequestCanClaimIt() {
        val request = ShoppingRequest(listOf(ShoppingItemKey("milk")))
        val oversized = ByteArray(ShoppingRequestDetailsCodec.maximumEncodedBytes + 1) { 'A'.code.toByte() }
        val restored = PracticalShoppingRequestDetailsLifecycleCapsule.restore(oversized)

        assertNull(restored.detailsForExactRequest(request))
        assertNull(restored.encodedOrNull())
    }

    @Test
    fun nullAndEmptyPayloadsRestoreAsEmptyCapsules() {
        val request = ShoppingRequest(listOf(ShoppingItemKey("milk")))

        assertNull(
            PracticalShoppingRequestDetailsLifecycleCapsule
                .restore(null)
                .detailsForExactRequest(request)
        )
        assertNull(
            PracticalShoppingRequestDetailsLifecycleCapsule
                .restore(ByteArray(0))
                .detailsForExactRequest(request)
        )
    }

    @Test
    fun restoredStateDoesNotRetainMutableInputBytes() {
        val request = ShoppingRequest(listOf(ShoppingItemKey("milk")))
        val details = ShoppingRequestDetails(request = request)
        val encoded =
            requireNotNull(
                PracticalShoppingRequestDetailsLifecycleCapsule
                    .fromDetails(details)
                    .encodedOrNull()
            )
        val restored = PracticalShoppingRequestDetailsLifecycleCapsule.restore(encoded)

        encoded.fill(0)

        assertEquals(details, restored.detailsForExactRequest(request))
    }

    @Test
    fun codecByteLimitFailureRefusesPersistenceWithoutTruncation() {
        val oversizedItemKey = ShoppingItemKey("x".repeat(513))
        val request = ShoppingRequest(listOf(oversizedItemKey))
        val capsule =
            PracticalShoppingRequestDetailsLifecycleCapsule.fromDetails(
                ShoppingRequestDetails(request = request)
            )

        assertNull(capsule.encodedOrNull())
        assertEquals(
            ShoppingRequestDetails(request = request),
            capsule.detailsForExactRequest(request)
        )
    }
}
