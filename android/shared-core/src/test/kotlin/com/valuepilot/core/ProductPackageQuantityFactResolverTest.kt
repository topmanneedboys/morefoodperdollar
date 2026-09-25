package com.valuepilot.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ProductPackageQuantityFactResolverTest {

    @Test
    fun `one exact product quantity resolves with its materialized supporter`() {
        val quantity = QuantityNormalization.count(100)
        val candidate = candidate("quantity-a", quantity = quantity)

        val result = ProductPackageQuantityFactResolver.resolve(PRODUCT_KEY, listOf(candidate))

        assertTrue(result.resolved)
        assertTrue(result.blockers.isEmpty())
        assertEquals(EvidenceFactResolutionStatus.RESOLVED, result.factResolution?.status)
        assertEquals(candidate.claim.valueFingerprint, result.factResolution?.selectedValueFingerprint)
        assertEquals(listOf("quantity-a"), result.supportingCandidates.map { it.evidenceId })
    }

    @Test
    fun `other product quantities are excluded before factual resolution`() {
        val wrong =
            candidate(
                evidenceId = "wrong",
                quantity = QuantityNormalization.count(100),
                productKey = "gtin:4006381333931"
            )

        val result = ProductPackageQuantityFactResolver.resolve(PRODUCT_KEY, listOf(wrong))

        assertFalse(result.resolved)
        assertNull(result.factResolution)
        assertTrue(result.supportingCandidates.isEmpty())
        assertEquals(
            setOf(ProductPackageQuantityResolutionBlocker.NO_RELEVANT_PACKAGE_QUANTITY),
            result.blockers
        )
    }

    @Test
    fun `equal authority disagreement remains an explicit unresolved conflict`() {
        val left =
            candidate(
                evidenceId = "left",
                namespaceId = "left-source",
                claimId = "left-claim",
                quantity = QuantityNormalization.count(100)
            )
        val right =
            candidate(
                evidenceId = "right",
                namespaceId = "right-source",
                claimId = "right-claim",
                quantity = QuantityNormalization.count(90)
            )

        val result = ProductPackageQuantityFactResolver.resolve(PRODUCT_KEY, listOf(left, right))

        assertFalse(result.resolved)
        assertEquals(EvidenceFactResolutionStatus.UNRESOLVED_CONFLICT, result.factResolution?.status)
        assertTrue(result.supportingCandidates.isEmpty())
        assertEquals(
            setOf(ProductPackageQuantityResolutionBlocker.UNRESOLVED_CONFLICT),
            result.blockers
        )
    }

    @Test
    fun `stronger factual authority selects only materialized supporters of winning value`() {
        val weaker =
            candidate(
                evidenceId = "weaker",
                namespaceId = "weaker-source",
                claimId = "weaker-claim",
                quantity = QuantityNormalization.count(90),
                authority = EvidenceAuthorityClass.SOURCE_ASSERTED_METADATA
            )
        val stronger =
            candidate(
                evidenceId = "stronger",
                namespaceId = "stronger-source",
                claimId = "stronger-claim",
                quantity = QuantityNormalization.count(100),
                authority = EvidenceAuthorityClass.MERCHANT_AUTHORITATIVE
            )

        val result = ProductPackageQuantityFactResolver.resolve(PRODUCT_KEY, listOf(weaker, stronger))

        assertTrue(result.resolved)
        assertEquals(stronger.claim.valueFingerprint, result.factResolution?.selectedValueFingerprint)
        assertEquals(listOf("stronger"), result.supportingCandidates.map { it.evidenceId })
    }

    @Test
    fun `same namespace claim id mutation fails closed before conflict resolution`() {
        val first =
            candidate(
                evidenceId = "first",
                namespaceId = "same-source",
                claimId = "same-claim",
                quantity = QuantityNormalization.count(100)
            )
        val mutated =
            candidate(
                evidenceId = "mutated",
                namespaceId = "same-source",
                claimId = "same-claim",
                quantity = QuantityNormalization.count(90)
            )

        val result = ProductPackageQuantityFactResolver.resolve(PRODUCT_KEY, listOf(first, mutated))

        assertFalse(result.resolved)
        assertNull(result.factResolution)
        assertTrue(result.supportingCandidates.isEmpty())
        assertEquals(
            setOf(ProductPackageQuantityResolutionBlocker.CLAIM_ID_COLLISION),
            result.blockers
        )
    }

    @Test
    fun `same resolved claim can preserve multiple materializations for downstream policy`() {
        val claimed = QuantityNormalization.count(100)
        val first =
            candidate(
                evidenceId = "z-valid",
                namespaceId = "same-source",
                claimId = "same-claim",
                quantity = claimed,
                fingerprintQuantity = claimed
            )
        val second =
            candidate(
                evidenceId = "a-mismaterialized",
                namespaceId = "same-source",
                claimId = "same-claim",
                quantity = QuantityNormalization.count(90),
                fingerprintQuantity = claimed
            )

        val result = ProductPackageQuantityFactResolver.resolve(PRODUCT_KEY, listOf(first, second))

        assertTrue(result.resolved)
        assertEquals(
            listOf("a-mismaterialized", "z-valid"),
            result.supportingCandidates.map { it.evidenceId }
        )
        assertEquals(QuantityNormalization.count(90), result.supportingCandidates[0].quantity)
        assertEquals(claimed, result.supportingCandidates[1].quantity)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `candidate count is bounded independent of price orchestration`() {
        val candidates =
            (0..128).map { index ->
                candidate(
                    evidenceId = "quantity-$index",
                    namespaceId = "source-$index",
                    claimId = "claim-$index",
                    quantity = QuantityNormalization.count(100)
                )
            }

        ProductPackageQuantityFactResolver.resolve(PRODUCT_KEY, candidates)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `evidence ids must be unique independent of claim identity`() {
        val quantity = QuantityNormalization.count(100)
        val first =
            candidate(
                evidenceId = "duplicate",
                namespaceId = "source-a",
                claimId = "claim-a",
                quantity = quantity
            )
        val second =
            candidate(
                evidenceId = "duplicate",
                namespaceId = "source-b",
                claimId = "claim-b",
                quantity = quantity
            )

        ProductPackageQuantityFactResolver.resolve(PRODUCT_KEY, listOf(first, second))
    }

    @Test
    fun `resolver source owns no price arithmetic provider authorization or clock`() {
        val source = source("ProductPackageQuantityFactResolver.kt").readText()

        listOf(
            "EvidenceFactResolver.resolve(",
            "EvidenceClaimDomain.PACKAGE_QUANTITY",
            "ProductPackageQuantityResolutionBlocker.UNRESOLVED_CONFLICT",
            ".sortedBy { it.evidenceId }"
        ).forEach { required ->
            assertTrue("Expected package-quantity resolver boundary $required", source.contains(required))
        }

        listOf(
            "ProductionCurrentPriceEligibilityEvaluator",
            "EvidenceBackedUnitValuePolicy",
            "EvidenceAcceptanceEvaluator",
            "EvidenceClaimDomain.CURRENT_PRICE",
            "EvidenceClaimDomain.OBSERVED_PRICE",
            "Offer(",
            "Money(",
            "System.currentTimeMillis",
            "ProviderProductionAuthorization",
            "ProductionDatasetLifecycleRegistry",
            "java.net",
            "android.permission"
        ).forEach { forbidden ->
            assertFalse("Package-quantity resolver must not own $forbidden", source.contains(forbidden))
        }
    }

    private fun candidate(
        evidenceId: String,
        quantity: NormalizedQuantity,
        namespaceId: String = "quantity-source",
        claimId: String = "quantity-claim",
        productKey: String = PRODUCT_KEY,
        authority: EvidenceAuthorityClass = EvidenceAuthorityClass.SOURCE_ASSERTED_METADATA,
        fingerprintQuantity: NormalizedQuantity = quantity
    ): ProductPackageQuantityEvidenceCandidate {
        val namespace =
            EvidenceDatasetNamespace(
                id = namespaceId,
                displayName = namespaceId,
                licenseId = "quantity-rights-reviewed",
                storageBoundary = EvidenceStorageBoundary.OPEN_SHARE_ALIKE
            )
        val claim =
            EvidenceClaim(
                claimId = claimId,
                domain = EvidenceClaimDomain.PACKAGE_QUANTITY,
                valueFingerprint = EvidenceFingerprints.quantity(fingerprintQuantity),
                authority = authority,
                scope = EvidenceClaimScope(productKey = productKey),
                observedAtEpochMillis = 1_000L
            )

        return ProductPackageQuantityEvidenceCandidate(
            evidenceId = evidenceId,
            namespace = namespace,
            claim = claim,
            quantity = quantity
        )
    }

    private fun source(fileName: String): File {
        var directory = File(System.getProperty("user.dir") ?: error("user.dir unavailable"))
        repeat(8) {
            val candidate = File(directory, "shared-core/src/main/kotlin/com/valuepilot/core/$fileName")
            if (candidate.isFile) return candidate
            directory = directory.parentFile ?: return@repeat
        }
        error("Could not locate $fileName")
    }

    private companion object {
        const val PRODUCT_KEY = "gtin:0036000291452"
    }
}
