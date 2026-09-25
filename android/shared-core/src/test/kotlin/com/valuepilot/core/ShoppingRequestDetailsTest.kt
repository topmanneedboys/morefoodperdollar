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

    @Test
    fun `upsert replaces only target canonicalizes request order and leaves source unchanged`() {
        val originalMilk =
            ShoppingItemRequestDetail(
                itemKey = milk,
                requestedQuantity = ShoppingRequestedQuantity(packageCount = 2)
            )
        val eggsDetail =
            ShoppingItemRequestDetail(
                itemKey = eggs,
                brandPreference = ShoppingBrandPreference.exact(ShoppingBrandKey("brand:eggs"))
            )
        val source = ShoppingRequestDetails(request, listOf(eggsDetail, originalMilk))
        val replacement =
            ShoppingItemRequestDetail(
                itemKey = milk,
                productSpecificity = ShoppingProductSpecificity.EXACT_PRODUCT_REQUIRED
            )

        val updated = source.withItemDetail(replacement)

        assertSame(request, updated.request)
        assertEquals(listOf(replacement, eggsDetail), updated.itemDetails)
        assertEquals(replacement, updated.detailFor(milk))
        assertEquals(eggsDetail, updated.detailFor(eggs))
        assertEquals(listOf(eggsDetail, originalMilk), source.itemDetails)
        assertEquals(originalMilk, source.detailFor(milk))
    }

    @Test
    fun `explicit default detail remains distinct from absent detail when editing`() {
        val exact =
            ShoppingItemRequestDetail(
                itemKey = milk,
                productSpecificity = ShoppingProductSpecificity.EXACT_PRODUCT_REQUIRED
            )
        val source = ShoppingRequestDetails(request, listOf(exact))
        val explicitDefault = ShoppingItemRequestDetail(itemKey = milk)

        val updated = source.withItemDetail(explicitDefault)
        val cleared = updated.withoutItemDetail(milk)

        assertEquals(explicitDefault, updated.detailFor(milk))
        assertEquals(listOf(explicitDefault), updated.itemDetails)
        assertNull(cleared.detailFor(milk))
        assertTrue(cleared.itemDetails.isEmpty())
        assertEquals(explicitDefault, updated.detailFor(milk))
        assertEquals(exact, source.detailFor(milk))
    }

    @Test
    fun `edit operations reject item keys outside the current request`() {
        val bread = ShoppingItemKey("bread")

        assertThrows(IllegalArgumentException::class.java) {
            ShoppingRequestDetails(request).withItemDetail(ShoppingItemRequestDetail(bread))
        }
        assertThrows(IllegalArgumentException::class.java) {
            ShoppingRequestDetails(request).withoutItemDetail(bread)
        }
    }

    @Test
    fun `reconcile preserves only surviving explicit details in new request order without inference`() {
        val rice = ShoppingItemKey("rice")
        val bread = ShoppingItemKey("bread")
        val oldRequest = ShoppingRequest(listOf(milk, eggs, rice))
        val milkDetail =
            ShoppingItemRequestDetail(
                itemKey = milk,
                productSpecificity = ShoppingProductSpecificity.EXACT_PRODUCT_REQUIRED
            )
        val eggsDetail =
            ShoppingItemRequestDetail(
                itemKey = eggs,
                requestedQuantity = ShoppingRequestedQuantity(packageCount = 3),
                brandPreference = ShoppingBrandPreference.exact(ShoppingBrandKey("brand:eggs"))
            )
        val riceDetail = ShoppingItemRequestDetail(itemKey = rice)
        val source = ShoppingRequestDetails(oldRequest, listOf(milkDetail, eggsDetail, riceDetail))
        val newRequest = ShoppingRequest(listOf(eggs, milk, bread))

        val reconciled = source.reconciledTo(newRequest)

        assertSame(newRequest, reconciled.request)
        assertEquals(listOf(eggsDetail, milkDetail), reconciled.itemDetails)
        assertNull(reconciled.detailFor(rice))
        assertNull(reconciled.detailFor(bread))

        val preservedQuantity = reconciled.detailFor(eggs)?.requestedQuantity
        assertEquals(3L, preservedQuantity?.packageCount)
        assertNull(preservedQuantity?.totalQuantity)
        assertNull(preservedQuantity?.preferredPackageQuantity)

        assertEquals(riceDetail, source.detailFor(rice))
        assertEquals(listOf(milkDetail, eggsDetail, riceDetail), source.itemDetails)
    }
}
