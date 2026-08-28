package com.valuepilot.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EvidenceConflictPolicyTest {

    @Test
    fun sameProductDifferentRetailersCoexistInsteadOfOverwritingPrices() {
        val left = currentPrice(
            id = "retailer-a",
            value = "CAD:499",
            merchant = "merchant-a"
        )
        val right = currentPrice(
            id = "retailer-b",
            value = "CAD:529",
            merchant = "merchant-b"
        )

        val decision = EvidenceConflictPolicy.resolve(left, right)

        assertEquals(EvidenceConflictRelationship.COEXISTS, decision.relationship)
        assertNull(decision.selectedClaimId)
        assertFalse(decision.blocksRanking)
    }

    @Test
    fun sameMerchantDifferentCommerceChannelsCoexist() {
        val web = currentPrice(
            id = "web",
            value = "CAD:499",
            merchant = "merchant-a",
            channel = "RETAILER_WEB"
        )
        val marketplace = currentPrice(
            id = "marketplace",
            value = "CAD:599",
            merchant = "merchant-a",
            channel = "MARKETPLACE"
        )

        val decision = EvidenceConflictPolicy.resolve(web, marketplace)

        assertEquals(EvidenceConflictRelationship.COEXISTS, decision.relationship)
        assertFalse(decision.blocksRanking)
    }

    @Test
    fun merchantCurrentPriceAndHistoricalProofObservationNeverOverwriteEachOther() {
        val merchant = currentPrice(
            id = "merchant-current",
            value = "CAD:499",
            merchant = "merchant-a",
            observedAt = 2_000L
        )
        val receipt = EvidenceClaim(
            claimId = "receipt-observation",
            domain = EvidenceClaimDomain.OBSERVED_PRICE,
            valueFingerprint = "CAD:399",
            authority = EvidenceAuthorityClass.PROOF_BACKED_DIRECT_OBSERVATION,
            scope = merchant.scope,
            observedAtEpochMillis = 1_000L
        )

        val decision = EvidenceConflictPolicy.resolve(merchant, receipt)

        assertEquals(EvidenceConflictRelationship.COEXISTS, decision.relationship)
        assertNull(decision.selectedClaimId)
        assertFalse(decision.blocksRanking)
    }

    @Test
    fun newerCurrentOfferSupersedesOlderPriceOnlyWithinExactSameScope() {
        val old = currentPrice(
            id = "old",
            value = "CAD:599",
            merchant = "merchant-a",
            observedAt = 1_000L
        )
        val newer = currentPrice(
            id = "new",
            value = "CAD:499",
            merchant = "merchant-a",
            observedAt = 2_000L
        )

        val decision = EvidenceConflictPolicy.resolve(old, newer)

        assertEquals(EvidenceConflictRelationship.PREFER_RIGHT, decision.relationship)
        assertEquals("new", decision.selectedClaimId)
        assertFalse(decision.blocksRanking)
    }

    @Test
    fun equalScopeEqualTimeConflictingPricesFailClosed() {
        val left = currentPrice(
            id = "source-a",
            value = "CAD:499",
            merchant = "merchant-a",
            observedAt = 2_000L
        )
        val right = currentPrice(
            id = "source-b",
            value = "CAD:599",
            merchant = "merchant-a",
            observedAt = 2_000L
        )

        val decision = EvidenceConflictPolicy.resolve(left, right)

        assertEquals(EvidenceConflictRelationship.UNRESOLVED_CONFLICT, decision.relationship)
        assertNull(decision.selectedClaimId)
        assertTrue(decision.blocksRanking)
    }

    @Test
    fun authoritativePackageQuantityBeatsWeakerCommunityMetadata() {
        val merchantQuantity = metadataClaim(
            id = "merchant",
            domain = EvidenceClaimDomain.PACKAGE_QUANTITY,
            value = "1000:g",
            authority = EvidenceAuthorityClass.MERCHANT_AUTHORITATIVE
        )
        val communityQuantity = metadataClaim(
            id = "community",
            domain = EvidenceClaimDomain.PACKAGE_QUANTITY,
            value = "900:g",
            authority = EvidenceAuthorityClass.SOURCE_ASSERTED_METADATA
        )

        val decision = EvidenceConflictPolicy.resolve(merchantQuantity, communityQuantity)

        assertEquals(EvidenceConflictRelationship.PREFER_LEFT, decision.relationship)
        assertEquals("merchant", decision.selectedClaimId)
        assertFalse(decision.blocksRanking)
    }

    @Test
    fun equalAuthorityConflictingPackageQuantityBlocksUnitValueRanking() {
        val left = metadataClaim(
            id = "metadata-a",
            domain = EvidenceClaimDomain.PACKAGE_QUANTITY,
            value = "1000:g",
            authority = EvidenceAuthorityClass.SOURCE_ASSERTED_METADATA
        )
        val right = metadataClaim(
            id = "metadata-b",
            domain = EvidenceClaimDomain.PACKAGE_QUANTITY,
            value = "900:g",
            authority = EvidenceAuthorityClass.SOURCE_ASSERTED_METADATA
        )

        val decision = EvidenceConflictPolicy.resolve(left, right)

        assertEquals(EvidenceConflictRelationship.UNRESOLVED_CONFLICT, decision.relationship)
        assertTrue(decision.blocksRanking)
    }

    @Test
    fun governmentMarketBenchmarkNeverBecomesRetailerCurrentPrice() {
        val current = currentPrice(
            id = "merchant-current",
            value = "CAD:499",
            merchant = "merchant-a"
        )
        val benchmark = metadataClaim(
            id = "statcan-average",
            domain = EvidenceClaimDomain.MARKET_BENCHMARK,
            value = "CAD:450",
            authority = EvidenceAuthorityClass.GOVERNMENT_RECORD
        )

        val decision = EvidenceConflictPolicy.resolve(current, benchmark)

        assertEquals(EvidenceConflictRelationship.COEXISTS, decision.relationship)
        assertNull(decision.selectedClaimId)
        assertFalse(decision.blocksRanking)
    }

    @Test
    fun sameCanonicalFactDeduplicatesDeterministically() {
        val older = currentPrice(
            id = "older",
            value = "CAD:499",
            merchant = "merchant-a",
            observedAt = 1_000L
        )
        val newer = currentPrice(
            id = "newer",
            value = "CAD:499",
            merchant = "merchant-a",
            observedAt = 2_000L
        )

        val decision = EvidenceConflictPolicy.resolve(older, newer)

        assertEquals(EvidenceConflictRelationship.SAME_CLAIM, decision.relationship)
        assertEquals("newer", decision.selectedClaimId)
        assertFalse(decision.blocksRanking)
    }

    private fun currentPrice(
        id: String,
        value: String,
        merchant: String,
        channel: String = "RETAILER_WEB",
        observedAt: Long = 0L
    ) =
        EvidenceClaim(
            claimId = id,
            domain = EvidenceClaimDomain.CURRENT_PRICE,
            valueFingerprint = value,
            authority = EvidenceAuthorityClass.MERCHANT_AUTHORITATIVE,
            scope = EvidenceClaimScope(
                productKey = "gtin:036000291452",
                merchantKey = merchant,
                locationKey = "ca-national",
                commerceChannelKey = channel,
                currencyCode = "CAD"
            ),
            observedAtEpochMillis = observedAt
        )

    private fun metadataClaim(
        id: String,
        domain: EvidenceClaimDomain,
        value: String,
        authority: EvidenceAuthorityClass
    ) =
        EvidenceClaim(
            claimId = id,
            domain = domain,
            valueFingerprint = value,
            authority = authority,
            scope = EvidenceClaimScope(
                productKey = "gtin:036000291452"
            )
        )
}
