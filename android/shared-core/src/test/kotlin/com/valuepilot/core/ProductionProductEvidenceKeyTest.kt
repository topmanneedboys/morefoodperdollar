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

    @Test
    fun `canonical gtin key joins exact cross source unit value evidence`() {
        val priceKey =
            requireNotNull(
                ProductionProductEvidenceKeyResolver.resolve(
                    providerId = EvidenceProviderId("merchant-feed-provider"),
                    identity = SourceProductIdentity(gtin = "036000291452")
                )
            )
        val quantityKey =
            requireNotNull(
                ProductionProductEvidenceKeyResolver.resolve(
                    providerId = EvidenceProviderId("metadata-provider"),
                    identity = SourceProductIdentity(gtin = "0036000291452")
                )
            )
        assertEquals(priceKey, quantityKey)

        val offer = Offer(current = Money.parse("4.99", "CAD"))
        val quantity = QuantityNormalization.grams(1000)

        val result =
            EvidenceBackedUnitValuePolicy.evaluate(
                EvidenceBackedUnitValueInput(
                    priceClaim =
                        EvidenceClaim(
                            claimId = "merchant-feed:current-price",
                            domain = EvidenceClaimDomain.CURRENT_PRICE,
                            valueFingerprint = EvidenceFingerprints.money(offer.current),
                            authority = EvidenceAuthorityClass.SOURCE_ASSERTED_METADATA,
                            scope =
                                EvidenceClaimScope(
                                    productKey = priceKey.value,
                                    merchantKey = "merchant-a",
                                    currencyCode = "CAD"
                                ),
                            observedAtEpochMillis = 2_000L
                        ),
                    quantityClaim =
                        EvidenceClaim(
                            claimId = "metadata:package-quantity",
                            domain = EvidenceClaimDomain.PACKAGE_QUANTITY,
                            valueFingerprint = EvidenceFingerprints.quantity(quantity),
                            authority = EvidenceAuthorityClass.SOURCE_ASSERTED_METADATA,
                            scope = EvidenceClaimScope(productKey = quantityKey.value),
                            observedAtEpochMillis = 1_900L
                        ),
                    offer = offer,
                    quantity = quantity,
                    priceDisposition = EvidenceDisposition.RANKABLE
                )
            )

        assertTrue(result.rankable)
        assertTrue(result.blockReasons.isEmpty())
    }

    @Test
    fun `same sku text from different providers cannot join unit value evidence`() {
        val priceKey =
            requireNotNull(
                ProductionProductEvidenceKeyResolver.resolve(
                    providerId = EvidenceProviderId("provider-a"),
                    identity = SourceProductIdentity(sku = "shared-sku")
                )
            )
        val quantityKey =
            requireNotNull(
                ProductionProductEvidenceKeyResolver.resolve(
                    providerId = EvidenceProviderId("provider-b"),
                    identity = SourceProductIdentity(sku = "shared-sku")
                )
            )
        assertTrue(priceKey != quantityKey)

        val offer = Offer(current = Money.parse("4.99", "CAD"))
        val quantity = QuantityNormalization.grams(1000)

        val result =
            EvidenceBackedUnitValuePolicy.evaluate(
                EvidenceBackedUnitValueInput(
                    priceClaim =
                        EvidenceClaim(
                            claimId = "provider-a:price",
                            domain = EvidenceClaimDomain.CURRENT_PRICE,
                            valueFingerprint = EvidenceFingerprints.money(offer.current),
                            authority = EvidenceAuthorityClass.SOURCE_ASSERTED_METADATA,
                            scope = EvidenceClaimScope(productKey = priceKey.value),
                            observedAtEpochMillis = 2_000L
                        ),
                    quantityClaim =
                        EvidenceClaim(
                            claimId = "provider-b:quantity",
                            domain = EvidenceClaimDomain.PACKAGE_QUANTITY,
                            valueFingerprint = EvidenceFingerprints.quantity(quantity),
                            authority = EvidenceAuthorityClass.SOURCE_ASSERTED_METADATA,
                            scope = EvidenceClaimScope(productKey = quantityKey.value),
                            observedAtEpochMillis = 1_900L
                        ),
                    offer = offer,
                    quantity = quantity,
                    priceDisposition = EvidenceDisposition.RANKABLE
                )
            )

        assertFalse(result.rankable)
        assertTrue(
            EvidenceBackedUnitValueBlockReason.PRODUCT_IDENTITY_MISMATCH in
                result.blockReasons
        )
    }
}
