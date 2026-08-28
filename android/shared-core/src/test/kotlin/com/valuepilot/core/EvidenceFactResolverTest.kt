package com.valuepilot.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EvidenceFactResolverTest {

    @Test
    fun differentRetailerScopesRemainSeparateResolvedFacts() {
        val claims = listOf(
            indexed("merchant-a", currentPrice("a", "merchant-a", "4.99")),
            indexed("merchant-b", currentPrice("b", "merchant-b", "5.49"))
        )

        val resolutions = EvidenceFactResolver.resolve(claims)

        assertEquals(2, resolutions.size)
        assertTrue(resolutions.all { it.status == EvidenceFactResolutionStatus.RESOLVED })
        assertTrue(resolutions.none { it.blocksRanking })
    }

    @Test
    fun strongestAuthorityCanResolveThreeSourceConflictWithoutWeakSourceDeadlock() {
        val scope = productScope()
        val merchant = metadataClaim(
            id = "merchant",
            value = "quantity:GRAM:1000000000",
            authority = EvidenceAuthorityClass.MERCHANT_AUTHORITATIVE,
            scope = scope
        )
        val communityA = metadataClaim(
            id = "community-a",
            value = "quantity:GRAM:900000000",
            authority = EvidenceAuthorityClass.SOURCE_ASSERTED_METADATA,
            scope = scope
        )
        val communityB = metadataClaim(
            id = "community-b",
            value = "quantity:GRAM:800000000",
            authority = EvidenceAuthorityClass.SOURCE_ASSERTED_METADATA,
            scope = scope
        )

        val resolution = EvidenceFactResolver.resolve(
            listOf(
                indexed("merchant-feed", merchant),
                indexed("community-a", communityA),
                indexed("community-b", communityB)
            )
        ).single()

        assertEquals(EvidenceFactResolutionStatus.RESOLVED, resolution.status)
        assertEquals("quantity:GRAM:1000000000", resolution.selectedValueFingerprint)
        assertEquals(listOf("merchant"), resolution.supportingClaims.map { it.claim.claimId })
        assertFalse(resolution.blocksRanking)
    }

    @Test
    fun equalAuthorityEqualTimeDifferentValuesStayUnresolved() {
        val scope = productScope()
        val a = metadataClaim(
            id = "a",
            value = "quantity:GRAM:1000000000",
            authority = EvidenceAuthorityClass.SOURCE_ASSERTED_METADATA,
            scope = scope,
            observedAt = 1000L
        )
        val b = metadataClaim(
            id = "b",
            value = "quantity:GRAM:900000000",
            authority = EvidenceAuthorityClass.SOURCE_ASSERTED_METADATA,
            scope = scope,
            observedAt = 1000L
        )

        val resolution = EvidenceFactResolver.resolve(
            listOf(indexed("source-a", a), indexed("source-b", b))
        ).single()

        assertEquals(EvidenceFactResolutionStatus.UNRESOLVED_CONFLICT, resolution.status)
        assertNull(resolution.selectedValueFingerprint)
        assertTrue(resolution.supportingClaims.isEmpty())
        assertEquals(2, resolution.conflictingClaims.size)
        assertTrue(resolution.blocksRanking)
    }

    @Test
    fun equalAuthorityNewerTimeResolvesTimeSensitiveCurrentPrice() {
        val old = currentPrice(
            id = "old",
            merchant = "merchant-a",
            amount = "5.99",
            observedAt = 1000L
        )
        val newer = currentPrice(
            id = "new",
            merchant = "merchant-a",
            amount = "4.99",
            observedAt = 2000L
        )

        val resolution = EvidenceFactResolver.resolve(
            listOf(indexed("feed-a", old), indexed("feed-b", newer))
        ).single()

        assertEquals(EvidenceFactResolutionStatus.RESOLVED, resolution.status)
        assertEquals(
            EvidenceFingerprints.money(Money.parse("4.99", "CAD")),
            resolution.selectedValueFingerprint
        )
        assertEquals(listOf("new"), resolution.supportingClaims.map { it.claim.claimId })
    }

    @Test
    fun agreeingSourcesRemainVisibleAsMultipleSupporters() {
        val scope = productScope()
        val value = "quantity:GRAM:1000000000"

        val resolution = EvidenceFactResolver.resolve(
            listOf(
                indexed(
                    "open-food-facts",
                    metadataClaim(
                        id = "off",
                        value = value,
                        authority = EvidenceAuthorityClass.SOURCE_ASSERTED_METADATA,
                        scope = scope
                    )
                ),
                indexed(
                    "merchant-feed",
                    metadataClaim(
                        id = "merchant",
                        value = value,
                        authority = EvidenceAuthorityClass.MERCHANT_AUTHORITATIVE,
                        scope = scope
                    )
                )
            )
        ).single()

        assertEquals(EvidenceFactResolutionStatus.RESOLVED, resolution.status)
        assertEquals(value, resolution.selectedValueFingerprint)
        assertEquals(2, resolution.supportingClaims.size)
        assertTrue(resolution.conflictingClaims.isEmpty())
    }

    @Test
    fun priceAndQuantityNeverEnterTheSameConflictSet() {
        val claims = listOf(
            indexed("open-prices", observedPrice("price", "4.99")),
            indexed(
                "open-food-facts",
                metadataClaim(
                    id = "quantity",
                    value = "quantity:GRAM:1000000000",
                    authority = EvidenceAuthorityClass.SOURCE_ASSERTED_METADATA,
                    scope = productScope()
                )
            )
        )

        val resolutions = EvidenceFactResolver.resolve(claims)

        assertEquals(2, resolutions.size)
        assertEquals(
            setOf(EvidenceClaimDomain.OBSERVED_PRICE, EvidenceClaimDomain.PACKAGE_QUANTITY),
            resolutions.map { it.key.domain }.toSet()
        )
    }

    private fun indexed(namespaceId: String, claim: EvidenceClaim) = IndexedEvidenceClaim(
        namespace = EvidenceDatasetNamespace(
            id = namespaceId,
            displayName = namespaceId,
            licenseId = "test-license",
            storageBoundary = EvidenceStorageBoundary.UNKNOWN
        ),
        claim = claim
    )

    private fun currentPrice(
        id: String,
        merchant: String,
        amount: String,
        observedAt: Long = 1000L
    ) = EvidenceClaim(
        claimId = id,
        domain = EvidenceClaimDomain.CURRENT_PRICE,
        valueFingerprint = EvidenceFingerprints.money(Money.parse(amount, "CAD")),
        authority = EvidenceAuthorityClass.MERCHANT_AUTHORITATIVE,
        scope = EvidenceClaimScope(
            productKey = PRODUCT_KEY,
            merchantKey = merchant,
            locationKey = "ca-national",
            commerceChannelKey = "RETAILER_WEB",
            currencyCode = "CAD"
        ),
        observedAtEpochMillis = observedAt
    )

    private fun observedPrice(id: String, amount: String) = EvidenceClaim(
        claimId = id,
        domain = EvidenceClaimDomain.OBSERVED_PRICE,
        valueFingerprint = EvidenceFingerprints.money(Money.parse(amount, "CAD")),
        authority = EvidenceAuthorityClass.PROOF_BACKED_DIRECT_OBSERVATION,
        scope = EvidenceClaimScope(
            productKey = PRODUCT_KEY,
            locationKey = "store-1",
            commerceChannelKey = "PHYSICAL_STORE",
            currencyCode = "CAD"
        ),
        observedAtEpochMillis = 1000L
    )

    private fun metadataClaim(
        id: String,
        value: String,
        authority: EvidenceAuthorityClass,
        scope: EvidenceClaimScope,
        observedAt: Long = 1000L
    ) = EvidenceClaim(
        claimId = id,
        domain = EvidenceClaimDomain.PACKAGE_QUANTITY,
        valueFingerprint = value,
        authority = authority,
        scope = scope,
        observedAtEpochMillis = observedAt
    )

    private fun productScope() = EvidenceClaimScope(productKey = PRODUCT_KEY)

    companion object {
        private const val PRODUCT_KEY = "gtin:036000291452"
    }
}
