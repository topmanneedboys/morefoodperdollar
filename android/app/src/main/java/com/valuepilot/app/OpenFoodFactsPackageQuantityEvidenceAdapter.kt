package com.valuepilot.app

import com.valuepilot.core.EvidenceAuthorityClass
import com.valuepilot.core.EvidenceClaimDomain
import com.valuepilot.core.EvidenceFingerprints
import com.valuepilot.core.EvidenceProviderId
import com.valuepilot.core.GtinValidation
import com.valuepilot.core.ProductPackageQuantityEvidenceCandidate
import com.valuepilot.core.ProductionProductEvidenceKeyResolver
import com.valuepilot.core.SourceProductIdentity

enum class OpenFoodFactsPackageQuantityEvidenceFailure {
    SOURCE_IMPORT_REJECTED,
    PROVIDER_ID_MISMATCH,
    SOURCE_GTIN_INVALID,
    CLAIM_ID_MISMATCH,
    CLAIM_DOMAIN_MISMATCH,
    CLAIM_AUTHORITY_MISMATCH,
    CLAIM_SCOPE_MISMATCH,
    CLAIM_VALUE_MISMATCH,
    CLAIM_OBSERVATION_TIME_MISMATCH
}

data class OpenFoodFactsPackageQuantityEvidenceResult(
    val candidate: ProductPackageQuantityEvidenceCandidate?,
    val failures: Set<OpenFoodFactsPackageQuantityEvidenceFailure>,
    val sourceImportFailures: Set<OpenFoodFactsImportFailure> = emptySet()
) {
    init {
        require((candidate != null) == failures.isEmpty())
        require(candidate == null || sourceImportFailures.isEmpty())
        require(
            sourceImportFailures.isEmpty() ||
                OpenFoodFactsPackageQuantityEvidenceFailure.SOURCE_IMPORT_REJECTED in failures
        )
    }

    val accepted: Boolean
        get() = candidate != null
}

/**
 * Network-free provenance bridge from one already-mapped Open Food Facts package quantity into the
 * provider-neutral materialized quantity-candidate boundary.
 *
 * The input type is publicly constructible, so `accepted` alone is not treated as proof that its
 * metadata and claim remain mutually bound. This adapter independently verifies the deterministic
 * tuple that [OpenFoodFactsImportedMetadataMapper] is responsible for producing: source provider,
 * exact source GTIN claim id, canonical cross-source product scope, PACKAGE_QUANTITY domain,
 * SOURCE_ASSERTED_METADATA authority, exact normalized-quantity fingerprint, product-only scope and
 * exact source modification timestamp.
 *
 * No quantity text is reparsed here. On success the exact existing claim and exact existing
 * normalized quantity are preserved, and the canonical Open Food Facts ODbL dataset namespace is
 * attached. The stable evidence id is derived only from dataset namespace plus source claim id.
 *
 * This adapter does not authorize dataset lifecycle, perform network or storage I/O, choose among
 * conflicting quantities, create price/current-price/availability/promotion evidence, or rank any
 * product. Those responsibilities remain with their existing independent boundaries.
 */
object OpenFoodFactsPackageQuantityEvidenceAdapter {

    fun adapt(
        importResult: OpenFoodFactsImportResult
    ): OpenFoodFactsPackageQuantityEvidenceResult {
        if (!importResult.accepted) {
            return OpenFoodFactsPackageQuantityEvidenceResult(
                candidate = null,
                failures =
                    setOf(
                        OpenFoodFactsPackageQuantityEvidenceFailure.SOURCE_IMPORT_REJECTED
                    ),
                sourceImportFailures = importResult.failures
            )
        }

        val metadata = requireNotNull(importResult.metadata)
        val claim = requireNotNull(importResult.quantityClaim)
        val failures = linkedSetOf<OpenFoodFactsPackageQuantityEvidenceFailure>()

        if (metadata.providerId != OpenFoodFactsImportedMetadataMapper.PROVIDER_ID) {
            failures += OpenFoodFactsPackageQuantityEvidenceFailure.PROVIDER_ID_MISMATCH
        }

        val canonicalProductKey = canonicalProductKey(metadata.gtin)
        if (canonicalProductKey == null) {
            failures += OpenFoodFactsPackageQuantityEvidenceFailure.SOURCE_GTIN_INVALID
        }

        val expectedClaimId =
            "${OpenFoodFactsImportedMetadataMapper.PROVIDER_ID}:${metadata.gtin}:package-quantity"
        if (claim.claimId != expectedClaimId) {
            failures += OpenFoodFactsPackageQuantityEvidenceFailure.CLAIM_ID_MISMATCH
        }

        if (claim.domain != EvidenceClaimDomain.PACKAGE_QUANTITY) {
            failures += OpenFoodFactsPackageQuantityEvidenceFailure.CLAIM_DOMAIN_MISMATCH
        }

        if (claim.authority != EvidenceAuthorityClass.SOURCE_ASSERTED_METADATA) {
            failures += OpenFoodFactsPackageQuantityEvidenceFailure.CLAIM_AUTHORITY_MISMATCH
        }

        if (
            canonicalProductKey == null ||
            claim.scope.productKey != canonicalProductKey ||
            claim.scope.merchantKey != null ||
            claim.scope.locationKey != null ||
            claim.scope.commerceChannelKey != null ||
            claim.scope.currencyCode != null
        ) {
            failures += OpenFoodFactsPackageQuantityEvidenceFailure.CLAIM_SCOPE_MISMATCH
        }

        val expectedValueFingerprint =
            EvidenceFingerprints.quantity(metadata.normalizedQuantity)
        if (claim.valueFingerprint != expectedValueFingerprint) {
            failures += OpenFoodFactsPackageQuantityEvidenceFailure.CLAIM_VALUE_MISMATCH
        }

        val expectedObservedAt = metadata.sourceLastModifiedAtEpochMillis ?: 0L
        if (claim.observedAtEpochMillis != expectedObservedAt) {
            failures +=
                OpenFoodFactsPackageQuantityEvidenceFailure.CLAIM_OBSERVATION_TIME_MISMATCH
        }

        if (failures.isNotEmpty()) {
            return OpenFoodFactsPackageQuantityEvidenceResult(
                candidate = null,
                failures = failures
            )
        }

        val namespace = OpenFoodFactsPracticalShoppingIdentityAdapter.DATASET_NAMESPACE
        return OpenFoodFactsPackageQuantityEvidenceResult(
            candidate =
                ProductPackageQuantityEvidenceCandidate(
                    evidenceId = "${namespace.id}:${claim.claimId}",
                    namespace = namespace,
                    claim = claim,
                    quantity = metadata.normalizedQuantity
                ),
            failures = emptySet()
        )
    }

    private fun canonicalProductKey(gtin: String): String? {
        if (!GtinValidation.isValid(gtin)) return null

        val resolved =
            ProductionProductEvidenceKeyResolver.resolve(
                providerId = EvidenceProviderId(OpenFoodFactsImportedMetadataMapper.PROVIDER_ID),
                identity = SourceProductIdentity(gtin = gtin)
            )

        return resolved.takeIf { it.usesCrossSourceRepresentation }?.value
    }
}
