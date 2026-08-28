package com.valuepilot.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductionProductEvidenceKeyTest {

    @Test
    fun `equivalent leading zero gtin representations resolve to same shared key`() {
        val providerA = EvidenceProviderId("provider-a")
        val providerB = EvidenceProviderId("provider-b")

        val upcKey =
            ProductionProductEvidenceKeyResolver.resolve(
                providerId = providerA,
                identity = SourceProductIdentity(gtin = "036000291452")
            )
        val gtin13Key =
            ProductionProductEvidenceKeyResolver.resolve(
                providerId = providerB,
                identity = SourceProductIdentity(gtin = "0036000291452")
            )

        assertEquals(upcKey, gtin13Key)
        assertEquals("gtin:0036000291452", upcKey?.value)
        assertEquals(ProductionProductKeyScope.CROSS_SOURCE_GTIN, upcKey?.scope)
        assertTrue(upcKey?.usesCrossSourceRepresentation == true)
    }

    @Test
    fun `provider item id stays provider scoped when gtin is absent`() {
        val left =
            ProductionProductEvidenceKeyResolver.resolve(
                providerId = EvidenceProviderId("provider-a"),
                identity = SourceProductIdentity(providerItemId = "same-item")
            )
        val right =
            ProductionProductEvidenceKeyResolver.resolve(
                providerId = EvidenceProviderId("provider-b"),
                identity = SourceProductIdentity(providerItemId = "same-item")
            )

        assertEquals(ProductionProductKeyScope.PROVIDER_ITEM, left?.scope)
        assertFalse(left?.usesCrossSourceRepresentation == true)
        assertTrue(left != right)
    }

    @Test
    fun `sku stays provider scoped and is not a cross source identity`() {
        val key =
            ProductionProductEvidenceKeyResolver.resolve(
                providerId = EvidenceProviderId("provider-a"),
                identity = SourceProductIdentity(sku = "sku-1")
            )

        assertEquals(ProductionProductKeyScope.PROVIDER_SKU, key?.scope)
        assertFalse(key?.usesCrossSourceRepresentation == true)
        assertTrue(key?.value?.contains("sku") == true)
    }

    @Test
    fun `provider item id wins over sku when no valid gtin exists`() {
        val key =
            ProductionProductEvidenceKeyResolver.resolve(
                providerId = EvidenceProviderId("provider-a"),
                identity =
                    SourceProductIdentity(
                        providerItemId = "item-1",
                        sku = "sku-1"
                    )
            )

        assertEquals(ProductionProductKeyScope.PROVIDER_ITEM, key?.scope)
        assertTrue(key?.value?.contains("item") == true)
    }

    @Test
    fun `checksum invalid gtin is ignored rather than repaired`() {
        val key =
            ProductionProductEvidenceKeyResolver.resolve(
                providerId = EvidenceProviderId("provider-a"),
                identity =
                    SourceProductIdentity(
                        providerItemId = "item-1",
                        gtin = "036000291453"
                    )
            )

        assertEquals(ProductionProductKeyScope.PROVIDER_ITEM, key?.scope)
        assertFalse(key?.value?.startsWith("gtin:") == true)
    }

    @Test
    fun `checksum invalid gtin without another safe id resolves to no key`() {
        val key =
            ProductionProductEvidenceKeyResolver.resolve(
                providerId = EvidenceProviderId("provider-a"),
                identity = SourceProductIdentity(gtin = "036000291453")
            )

        assertNull(key)
    }

    @Test
    fun `provider scoped length prefixes prevent delimiter collision`() {
        val first =
            ProductionProductEvidenceKeyResolver.resolve(
                providerId = EvidenceProviderId("a:b"),
                identity = SourceProductIdentity(providerItemId = "c")
            )
        val second =
            ProductionProductEvidenceKeyResolver.resolve(
                providerId = EvidenceProviderId("a"),
                identity = SourceProductIdentity(providerItemId = "b:c")
            )

        assertTrue(first != second)
    }
}
