package com.valuepilot.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShoppingRequestDetailsCodecTest {

    @Test
    fun `full typed shopper intent round trips deterministically without inference`() {
        val milk = ShoppingItemKey("milk:whole:é")
        val eggs = ShoppingItemKey("eggs")
        val request = ShoppingRequest(listOf(milk, eggs))
        val details =
            ShoppingRequestDetails(
                request = request,
                itemDetails =
                    listOf(
                        ShoppingItemRequestDetail(
                            itemKey = milk,
                            productSpecificity = ShoppingProductSpecificity.EXACT_PRODUCT_REQUIRED,
                            requestedQuantity =
                                ShoppingRequestedQuantity(
                                    totalQuantity = QuantityNormalization.litres(2),
                                    packageCount = 2,
                                    preferredPackageQuantity = QuantityNormalization.litres(1)
                                ),
                            brandPreference =
                                ShoppingBrandPreference.exact(
                                    ShoppingBrandKey("brand:Québec-dairy")
                                )
                        )
                    )
            )

        val first = ShoppingRequestDetailsCodec.encode(details)
        val second = ShoppingRequestDetailsCodec.encode(details)

        assertTrue(first.accepted)
        assertNull(first.issue)
        assertArrayEquals(first.bytes, second.bytes)

        val decoded = ShoppingRequestDetailsCodec.decode(requireNotNull(first.bytes))

        assertTrue(decoded.accepted)
        assertEquals(details, decoded.details)
        assertNull(decoded.details?.detailFor(eggs))
        assertEquals(
            QuantityNormalization.litres(2),
            decoded.details?.detailFor(milk)?.requestedQuantity?.totalQuantity
        )
        assertEquals(
            2L,
            decoded.details?.detailFor(milk)?.requestedQuantity?.packageCount
        )
    }

    @Test
    fun `explicit default detail remains distinct from an item with no detail`() {
        val milk = ShoppingItemKey("milk")
        val eggs = ShoppingItemKey("eggs")
        val details =
            ShoppingRequestDetails(
                request = ShoppingRequest(listOf(milk, eggs)),
                itemDetails = listOf(ShoppingItemRequestDetail(milk))
            )

        val decoded = decodeAccepted(details)

        assertEquals(ShoppingItemRequestDetail(milk), decoded.detailFor(milk))
        assertNull(decoded.detailFor(eggs))
    }

    @Test
    fun `package count alone survives without inventing package content or total quantity`() {
        val rice = ShoppingItemKey("rice")
        val details =
            ShoppingRequestDetails(
                request = ShoppingRequest(listOf(rice)),
                itemDetails =
                    listOf(
                        ShoppingItemRequestDetail(
                            itemKey = rice,
                            requestedQuantity = ShoppingRequestedQuantity(packageCount = 3)
                        )
                    )
            )

        val decodedQuantity = decodeAccepted(details).detailFor(rice)?.requestedQuantity

        assertEquals(3L, decodedQuantity?.packageCount)
        assertNull(decodedQuantity?.totalQuantity)
        assertNull(decodedQuantity?.preferredPackageQuantity)
    }

    @Test
    fun `maximum bounded request item count round trips in request order`() {
        val keys = (1..128).map { ShoppingItemKey("item-$it") }
        val details =
            ShoppingRequestDetails(
                request = ShoppingRequest(keys),
                itemDetails =
                    keys.mapIndexed { index, key ->
                        ShoppingItemRequestDetail(
                            itemKey = key,
                            requestedQuantity =
                                if (index % 2 == 0) {
                                    ShoppingRequestedQuantity(packageCount = (index + 1).toLong())
                                } else {
                                    null
                                }
                        )
                    }
            )

        val decoded = decodeAccepted(details)

        assertEquals(keys, decoded.request.itemKeys)
        assertEquals(details, decoded)
    }

    @Test
    fun `encode rejects oversized stable item identity before producing bytes`() {
        val details =
            ShoppingRequestDetails(
                request = ShoppingRequest(listOf(ShoppingItemKey("x".repeat(513))))
            )

        val encoded = ShoppingRequestDetailsCodec.encode(details)

        assertFalse(encoded.accepted)
        assertNull(encoded.bytes)
        assertEquals(ShoppingRequestDetailsCodecIssue.FIELD_TOO_LARGE, encoded.issue)
    }

    @Test
    fun `decode rejects oversized input before parsing`() {
        val bytes = ByteArray(ShoppingRequestDetailsCodec.maximumEncodedBytes + 1) { 'A'.code.toByte() }

        val decoded = ShoppingRequestDetailsCodec.decode(bytes)

        assertFalse(decoded.accepted)
        assertEquals(ShoppingRequestDetailsCodecIssue.INPUT_TOO_LARGE, decoded.issue)
    }

    @Test
    fun `decode rejects more than 128 records before domain construction`() {
        val absentRecord = "I|61|0|~|~|~|~|~|~|~|~"
        val encoded =
            buildString {
                append("VALUEPILOT_SHOPPING_REQUEST_DETAILS|1")
                repeat(129) {
                    append('\n')
                    append(absentRecord)
                }
            }.toByteArray(Charsets.US_ASCII)

        val decoded = ShoppingRequestDetailsCodec.decode(encoded)

        assertFalse(decoded.accepted)
        assertEquals(ShoppingRequestDetailsCodecIssue.TOO_MANY_ITEMS, decoded.issue)
    }

    @Test
    fun `decode fails closed for invalid header malformed utf8 enum and invalid quantity`() {
        assertEquals(
            ShoppingRequestDetailsCodecIssue.INVALID_HEADER,
            ShoppingRequestDetailsCodec.decode("wrong|1".toByteArray()).issue
        )

        val malformedUtf8 =
            "VALUEPILOT_SHOPPING_REQUEST_DETAILS|1\n" +
                "I|ff|0|~|~|~|~|~|~|~|~"
        assertEquals(
            ShoppingRequestDetailsCodecIssue.MALFORMED_INPUT,
            ShoppingRequestDetailsCodec.decode(malformedUtf8.toByteArray()).issue
        )

        val badEnum =
            "VALUEPILOT_SHOPPING_REQUEST_DETAILS|1\n" +
                "I|6d696c6b|1|NOT_A_SPECIFICITY|~|~|~|~|~|FLEXIBLE|~"
        assertEquals(
            ShoppingRequestDetailsCodecIssue.MALFORMED_INPUT,
            ShoppingRequestDetailsCodec.decode(badEnum.toByteArray()).issue
        )

        val invalidPackageCount =
            "VALUEPILOT_SHOPPING_REQUEST_DETAILS|1\n" +
                "I|6d696c6b|1|BROAD_INTENT|~|~|0|~|~|FLEXIBLE|~"
        assertEquals(
            ShoppingRequestDetailsCodecIssue.MALFORMED_INPUT,
            ShoppingRequestDetailsCodec.decode(invalidPackageCount.toByteArray()).issue
        )
    }

    @Test
    fun `decode rejects hidden fields on a record that declares no detail`() {
        val encoded =
            "VALUEPILOT_SHOPPING_REQUEST_DETAILS|1\n" +
                "I|6d696c6b|0|BROAD_INTENT|~|~|~|~|~|~|~"

        val decoded = ShoppingRequestDetailsCodec.decode(encoded.toByteArray())

        assertFalse(decoded.accepted)
        assertEquals(ShoppingRequestDetailsCodecIssue.MALFORMED_INPUT, decoded.issue)
    }

    private fun decodeAccepted(details: ShoppingRequestDetails): ShoppingRequestDetails {
        val encoded = ShoppingRequestDetailsCodec.encode(details)
        assertTrue(encoded.accepted)
        val decoded = ShoppingRequestDetailsCodec.decode(requireNotNull(encoded.bytes))
        assertTrue(decoded.accepted)
        return requireNotNull(decoded.details)
    }
}
