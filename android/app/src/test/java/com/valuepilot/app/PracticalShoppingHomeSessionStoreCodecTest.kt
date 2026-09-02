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
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticalShoppingHomeSessionStoreCodecTest {

    private val milk = ShoppingItemKey("sample-milk-2pct-4l")

    @Test
    fun validDurableEnvelopeRestoresTypedSnapshotAndDetachesBytes() {
        val details =
            ShoppingRequestDetails(
                request = ShoppingRequest(listOf(milk)),
                itemDetails =
                    listOf(
                        ShoppingItemRequestDetail(
                            itemKey = milk,
                            productSpecificity = ShoppingProductSpecificity.EXACT_PRODUCT_REQUIRED,
                            requestedQuantity = ShoppingRequestedQuantity(packageCount = 2),
                            brandPreference = ShoppingBrandPreference.exact(ShoppingBrandKey("Neilson"))
                        )
                    )
            )
        val bytes = requireNotNull(ShoppingRequestDetailsCodec.encode(details).bytes)

        val decoded =
            requireNotNull(
                PracticalShoppingHomeSessionStoreCodec.decode(
                    query = "milk",
                    wasSubmitted = true,
                    chickenChoice = null,
                    extraStopMinimumSavingsChoice =
                        LocalSamplePracticalShoppingDemo.ExtraStopMinimumSavingsChoice.TWENTY_FIVE_CAD.name,
                    requestDetailsLifecycleState = bytes
                )
            )

        assertEquals("milk", decoded.query)
        assertTrue(decoded.wasSubmitted)
        assertEquals(
            LocalSamplePracticalShoppingDemo.ExtraStopMinimumSavingsChoice.TWENTY_FIVE_CAD,
            decoded.extraStopMinimumSavingsChoice
        )
        assertArrayEquals(bytes, requireNotNull(decoded.requestDetailsLifecycleState))
        bytes[0] = (bytes[0].toInt() xor 0x01).toByte()
        assertArrayEquals(
            requireNotNull(ShoppingRequestDetailsCodec.encode(details).bytes),
            requireNotNull(decoded.requestDetailsLifecycleState)
        )
    }

    @Test
    fun missingOrOversizedQueryFailsClosedBeforeRestoration() {
        assertNull(
            PracticalShoppingHomeSessionStoreCodec.decode(
                query = null,
                wasSubmitted = true,
                chickenChoice = null,
                extraStopMinimumSavingsChoice = null,
                requestDetailsLifecycleState = null
            )
        )
        assertNull(
            PracticalShoppingHomeSessionStoreCodec.decode(
                query = "x".repeat(242),
                wasSubmitted = true,
                chickenChoice = null,
                extraStopMinimumSavingsChoice = null,
                requestDetailsLifecycleState = null
            )
        )
    }

    @Test
    fun unknownEnumValuesUseSafeDefaultsAndOversizedDetailsAreDropped() {
        val decoded =
            requireNotNull(
                PracticalShoppingHomeSessionStoreCodec.decode(
                    query = "eggs",
                    wasSubmitted = true,
                    chickenChoice = "not-a-choice",
                    extraStopMinimumSavingsChoice = "not-a-threshold",
                    requestDetailsLifecycleState =
                        ByteArray(ShoppingRequestDetailsCodec.maximumEncodedBytes + 1)
                )
            )

        assertNull(decoded.chickenChoice)
        assertEquals(
            LocalSamplePracticalShoppingDemo.ExtraStopMinimumSavingsChoice.DEFAULT,
            decoded.extraStopMinimumSavingsChoice
        )
        assertNull(decoded.requestDetailsLifecycleState)
    }
}
