package com.valuepilot.app

import com.valuepilot.core.BaseUnit
import com.valuepilot.core.EvidenceAuthorityClass
import com.valuepilot.core.EvidenceClaimDomain
import com.valuepilot.core.EvidenceClaimScope
import com.valuepilot.core.EvidenceFingerprints
import com.valuepilot.core.EvidenceStorageBoundary
import com.valuepilot.core.NormalizedQuantity
import com.valuepilot.core.ProductPackageQuantityFactResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class OpenFoodFactsPackageQuantityEvidenceAdapterTest {

    @Test
    fun `accepted mapped import preserves exact claim quantity and canonical OFF namespace`() {
        val mapped = mapped("036000291452")
        val expectedClaim = requireNotNull(mapped.quantityClaim)
        val expectedQuantity = requireNotNull(mapped.metadata).normalizedQuantity

        val result = OpenFoodFactsPackageQuantityEvidenceAdapter.adapt(mapped)

        assertTrue(result.accepted)
        assertTrue(result.failures.isEmpty())
        assertTrue(result.sourceImportFailures.isEmpty())
        val candidate = requireNotNull(result.candidate)
        assertSame(expectedClaim, candidate.claim)
        assertEquals(expectedQuantity, candidate.quantity)
        assertSame(
            OpenFoodFactsPracticalShoppingIdentityAdapter.DATASET_NAMESPACE,
            candidate.namespace
        )
        assertEquals("open-food-facts-products", candidate.namespace.id)
        assertEquals("ODbL-1.0", candidate.namespace.licenseId)
        assertEquals(EvidenceStorageBoundary.OPEN_SHARE_ALIKE, candidate.namespace.storageBoundary)
        assertEquals(
            "open-food-facts-products:open-food-facts:036000291452:package-quantity",
            candidate.evidenceId
        )
        assertEquals("gtin:0036000291452", candidate.claim.scope.productKey)
    }

    @Test
    fun `rejected source import stays rejected and preserves source failures`() {
        val rejected =
            OpenFoodFactsImportedMetadataMapper.map(
                row(code = "036000291453")
            )

        val result = OpenFoodFactsPackageQuantityEvidenceAdapter.adapt(rejected)

        assertFalse(result.accepted)
        assertNull(result.candidate)
        assertEquals(
            setOf(OpenFoodFactsPackageQuantityEvidenceFailure.SOURCE_IMPORT_REJECTED),
            result.failures
        )
        assertEquals(rejected.failures, result.sourceImportFailures)
    }

    @Test
    fun `forged accepted provider or source GTIN tuple fails closed`() {
        val mapped = mapped()
        val metadata = requireNotNull(mapped.metadata)

        val providerMismatch =
            OpenFoodFactsPackageQuantityEvidenceAdapter.adapt(
                mapped.copy(metadata = metadata.copy(providerId = "forged-provider"))
            )
        assertFalse(providerMismatch.accepted)
        assertTrue(
            OpenFoodFactsPackageQuantityEvidenceFailure.PROVIDER_ID_MISMATCH in
                providerMismatch.failures
        )

        val invalidGtin =
            OpenFoodFactsPackageQuantityEvidenceAdapter.adapt(
                mapped.copy(metadata = metadata.copy(gtin = "00000000"))
            )
        assertFalse(invalidGtin.accepted)
        assertTrue(
            OpenFoodFactsPackageQuantityEvidenceFailure.SOURCE_GTIN_INVALID in
                invalidGtin.failures
        )
        assertTrue(
            OpenFoodFactsPackageQuantityEvidenceFailure.CLAIM_ID_MISMATCH in
                invalidGtin.failures
        )
        assertTrue(
            OpenFoodFactsPackageQuantityEvidenceFailure.CLAIM_SCOPE_MISMATCH in
                invalidGtin.failures
        )
    }

    @Test
    fun `forged accepted claim identity domain and authority fail closed`() {
        val mapped = mapped()
        val claim = requireNotNull(mapped.quantityClaim)

        val claimId =
            OpenFoodFactsPackageQuantityEvidenceAdapter.adapt(
                mapped.copy(quantityClaim = claim.copy(claimId = "forged-claim"))
            )
        assertEquals(
            setOf(OpenFoodFactsPackageQuantityEvidenceFailure.CLAIM_ID_MISMATCH),
            claimId.failures
        )

        val domain =
            OpenFoodFactsPackageQuantityEvidenceAdapter.adapt(
                mapped.copy(quantityClaim = claim.copy(domain = EvidenceClaimDomain.CURRENT_PRICE))
            )
        assertEquals(
            setOf(OpenFoodFactsPackageQuantityEvidenceFailure.CLAIM_DOMAIN_MISMATCH),
            domain.failures
        )

        val authority =
            OpenFoodFactsPackageQuantityEvidenceAdapter.adapt(
                mapped.copy(
                    quantityClaim =
                        claim.copy(authority = EvidenceAuthorityClass.MERCHANT_AUTHORITATIVE)
                )
            )
        assertEquals(
            setOf(OpenFoodFactsPackageQuantityEvidenceFailure.CLAIM_AUTHORITY_MISMATCH),
            authority.failures
        )
    }

    @Test
    fun `forged accepted scope value and observation time fail closed`() {
        val mapped = mapped()
        val metadata = requireNotNull(mapped.metadata)
        val claim = requireNotNull(mapped.quantityClaim)

        val wrongScope =
            OpenFoodFactsPackageQuantityEvidenceAdapter.adapt(
                mapped.copy(
                    quantityClaim =
                        claim.copy(
                            scope = EvidenceClaimScope(productKey = "gtin:4006381333931")
                        )
                )
            )
        assertEquals(
            setOf(OpenFoodFactsPackageQuantityEvidenceFailure.CLAIM_SCOPE_MISMATCH),
            wrongScope.failures
        )

        val merchantScoped =
            OpenFoodFactsPackageQuantityEvidenceAdapter.adapt(
                mapped.copy(
                    quantityClaim =
                        claim.copy(
                            scope = claim.scope.copy(merchantKey = "merchant-a")
                        )
                )
            )
        assertEquals(
            setOf(OpenFoodFactsPackageQuantityEvidenceFailure.CLAIM_SCOPE_MISMATCH),
            merchantScoped.failures
        )

        val wrongQuantity =
            NormalizedQuantity(
                amountMicros = 900_000_000L,
                unit = BaseUnit.GRAM
            )
        val wrongValue =
            OpenFoodFactsPackageQuantityEvidenceAdapter.adapt(
                mapped.copy(
                    quantityClaim =
                        claim.copy(
                            valueFingerprint = EvidenceFingerprints.quantity(wrongQuantity)
                        )
                )
            )
        assertEquals(
            setOf(OpenFoodFactsPackageQuantityEvidenceFailure.CLAIM_VALUE_MISMATCH),
            wrongValue.failures
        )

        val expectedObservedAt = requireNotNull(metadata.sourceLastModifiedAtEpochMillis)
        val wrongTime =
            OpenFoodFactsPackageQuantityEvidenceAdapter.adapt(
                mapped.copy(
                    quantityClaim =
                        claim.copy(observedAtEpochMillis = expectedObservedAt + 1L)
                )
            )
        assertEquals(
            setOf(
                OpenFoodFactsPackageQuantityEvidenceFailure.CLAIM_OBSERVATION_TIME_MISMATCH
            ),
            wrongTime.failures
        )
    }

    @Test
    fun `adapted UPC quantity resolves under canonical cross source product key`() {
        val result = OpenFoodFactsPackageQuantityEvidenceAdapter.adapt(mapped("036000291452"))
        val candidate = requireNotNull(result.candidate)

        val resolution =
            ProductPackageQuantityFactResolver.resolve(
                productKey = "gtin:0036000291452",
                candidates = listOf(candidate)
            )

        assertTrue(resolution.resolved)
        assertEquals(listOf(candidate.evidenceId), resolution.supportingCandidates.map { it.evidenceId })
    }

    @Test
    fun `equivalent UPC and GTIN13 candidates coexist as same value supporters`() {
        val upc =
            requireNotNull(
                OpenFoodFactsPackageQuantityEvidenceAdapter
                    .adapt(mapped("036000291452"))
                    .candidate
            )
        val gtin13 =
            requireNotNull(
                OpenFoodFactsPackageQuantityEvidenceAdapter
                    .adapt(mapped("0036000291452"))
                    .candidate
            )

        assertEquals(upc.claim.scope.productKey, gtin13.claim.scope.productKey)
        assertEquals(upc.claim.valueFingerprint, gtin13.claim.valueFingerprint)
        assertFalse(upc.evidenceId == gtin13.evidenceId)

        val resolution =
            ProductPackageQuantityFactResolver.resolve(
                productKey = upc.claim.scope.productKey,
                candidates = listOf(upc, gtin13)
            )

        assertTrue(resolution.resolved)
        assertEquals(
            setOf(upc.evidenceId, gtin13.evidenceId),
            resolution.supportingCandidates.map { it.evidenceId }.toSet()
        )
    }

    @Test
    fun `adapter source owns provenance binding only and no price network clock or lifecycle authority`() {
        val source = source("OpenFoodFactsPackageQuantityEvidenceAdapter.kt").readText()

        listOf(
            "OpenFoodFactsPracticalShoppingIdentityAdapter.DATASET_NAMESPACE",
            "ProductPackageQuantityEvidenceCandidate(",
            "EvidenceFingerprints.quantity(metadata.normalizedQuantity)",
            "ProductionProductEvidenceKeyResolver.resolve(",
            "EvidenceClaimDomain.PACKAGE_QUANTITY",
            "EvidenceAuthorityClass.SOURCE_ASSERTED_METADATA"
        ).forEach { required ->
            assertTrue("Expected OFF quantity provenance boundary $required", source.contains(required))
        }

        listOf(
            "EvidenceClaimDomain.CURRENT_PRICE",
            "EvidenceClaimDomain.OBSERVED_PRICE",
            "EvidenceAuthorityClass.MERCHANT_AUTHORITATIVE",
            "Offer(",
            "Money(",
            "AvailabilityState",
            "PromotionEvidence",
            "ProductionCurrentPrice",
            "ProductionDatasetLifecycleRegistry",
            "EvidenceAcceptanceEvaluator",
            "System.currentTimeMillis",
            "java.net",
            "android.permission",
            "SharedPreferences",
            "WorkManager"
        ).forEach { forbidden ->
            assertFalse("OFF quantity adapter must not own $forbidden", source.contains(forbidden))
        }
    }

    private fun mapped(code: String = "036000291452"): OpenFoodFactsImportResult =
        OpenFoodFactsImportedMetadataMapper.map(row(code))

    private fun row(code: String): OpenFoodFactsImportedProduct =
        OpenFoodFactsImportedProduct(
            code = code,
            productName = "Rolled Oats",
            brands = "Example Brand",
            rawQuantity = "1 kg",
            productQuantity = "1000",
            productQuantityUnit = "g",
            lastModifiedEpochSeconds = 1_790_000_000L
        )

    private fun source(fileName: String): File {
        var directory = File(System.getProperty("user.dir") ?: error("user.dir unavailable"))
        repeat(8) {
            val candidate = File(directory, "app/src/main/java/com/valuepilot/app/$fileName")
            if (candidate.isFile) return candidate
            directory = directory.parentFile ?: return@repeat
        }
        error("Could not locate $fileName")
    }
}
