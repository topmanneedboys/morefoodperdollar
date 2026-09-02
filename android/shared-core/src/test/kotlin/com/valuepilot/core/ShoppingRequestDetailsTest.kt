package com.valuepilot.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ShoppingRequestDetailsTest {

    private val milk = ShoppingItemKey("milk")
    private val eggs = ShoppingItemKey("eggs")
    private val request = ShoppingRequest(listOf(milk, eggs))

    @Test
    fun `default item detail stays broad brand flexible and quantity unspecified`() {
        val detail = ShoppingItemRequestDetail(itemKey = milk)

        assertEquals(ShoppingProductSpecificity.BROAD_INTENT, detail.productSpecificity)
        assertEquals(ShoppingBrandFlexibility.FLEXIBLE, detail.brandPreference.flexibility)
        assertNull(detail.brandPreference.exactBrandKey)
        assertNull(detail.requestedQuantity)
    }

    @Test
    fun `exact product requirement remains a shopper constraint with explicit detail only`() {
        val detail =
            ShoppingItemRequestDetail(
                itemKey = milk,
                productSpecificity = ShoppingProductSpecificity.EXACT_PRODUCT_REQUIRED
            )
        val details = ShoppingRequestDetails(request, listOf(detail))

        assertSame(request, details.request)
        assertEquals(detail, details.detailFor(milk))
        assertNull(details.detailFor(eggs))
    }

    @Test
    fun `exact brand requires an opaque bounded brand key`() {
        val brand = ShoppingBrandKey("brand:neilson")
        val preference = ShoppingBrandPreference.exact(brand)

        assertEquals(ShoppingBrandFlexibility.EXACT_REQUIRED, preference.flexibility)
        assertEquals(brand, preference.exactBrandKey)

        assertThrows(IllegalArgumentException::class.java) {
            ShoppingBrandPreference(ShoppingBrandFlexibility.EXACT_REQUIRED)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ShoppingBrandPreference(
                ShoppingBrandFlexibility.FLEXIBLE,
                exactBrandKey = brand
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            ShoppingBrandKey(" ")
        }
        assertThrows(IllegalArgumentException::class.java) {
            ShoppingBrandKey("b".repeat(161))
        }
    }

    @Test
    fun `requested quantity can preserve total package count and preferred package without inference`() {
        val quantity =
            ShoppingRequestedQuantity(
                totalQuantity = QuantityNormalization.litres(2),
                packageCount = 2,
                preferredPackageQuantity = QuantityNormalization.litres(1)
            )

        assertEquals(QuantityNormalization.litres(2), quantity.totalQuantity)
        assertEquals(2L, quantity.packageCount)
        assertEquals(QuantityNormalization.litres(1), quantity.preferredPackageQuantity)
    }

    @Test
    fun `requested quantity fails closed for empty invalid count and incompatible content units`() {
        assertThrows(IllegalArgumentException::class.java) {
            ShoppingRequestedQuantity()
        }
        assertThrows(IllegalArgumentException::class.java) {
            ShoppingRequestedQuantity(packageCount = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ShoppingRequestedQuantity(packageCount = 1_000_001)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ShoppingRequestedQuantity(
                totalQuantity = QuantityNormalization.litres(2),
                preferredPackageQuantity = QuantityNormalization.count(1)
            )
        }
    }

    @Test
    fun `package count alone is valid when package content is unknown`() {
        val quantity = ShoppingRequestedQuantity(packageCount = 3)

        assertEquals(3L, quantity.packageCount)
        assertNull(quantity.totalQuantity)
        assertNull(quantity.preferredPackageQuantity)
    }

    @Test
    fun `request details reject duplicate and outside item keys`() {
        val milkDetail = ShoppingItemRequestDetail(milk)

        assertThrows(IllegalArgumentException::class.java) {
            ShoppingRequestDetails(request, listOf(milkDetail, milkDetail))
        }
        assertThrows(IllegalArgumentException::class.java) {
            ShoppingRequestDetails(
                request,
                listOf(ShoppingItemRequestDetail(ShoppingItemKey("bread")))
            )
        }
    }

    @Test
    fun `details are bounded by the already bounded shopping request`() {
        val keys = (1..128).map { ShoppingItemKey("item-$it") }
        val boundedRequest = ShoppingRequest(keys)
        val details =
            ShoppingRequestDetails(
                request = boundedRequest,
                itemDetails = keys.map { ShoppingItemRequestDetail(it) }
            )

        assertEquals(128, details.itemDetails.size)
        assertTrue(keys.all { details.detailFor(it) != null })
    }
}
