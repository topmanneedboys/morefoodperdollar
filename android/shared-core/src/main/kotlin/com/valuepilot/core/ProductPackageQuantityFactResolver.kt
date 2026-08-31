package com.valuepilot.core

/** Why an exact product package-quantity fact could not be safely materialized. */
enum class ProductPackageQuantityResolutionBlocker {
    NO_RELEVANT_PACKAGE_QUANTITY,
    CLAIM_ID_COLLISION,
    FACT_RESOLUTION_MISSING,
    UNRESOLVED_CONFLICT,
    RESOLVED_VALUE_NOT_MATERIALIZED
}

/**
 * Provider-neutral result of resolving separately attributed package-quantity candidates for one
 * exact product key.
 *
 * [supportingCandidates] contains only materialized candidates whose claims support the resolved
 * value. It is sorted by stable evidence id but is not authority-filtered for downstream use. A
 * caller that needs a quantity for unit-value math must still run the chosen candidate through the
 * applicable factual/use policy; this resolver never upgrades claim authority or validates that a
 * candidate's materialized [NormalizedQuantity] matches its claim fingerprint.
 */
data class ProductPackageQuantityResolutionResult(
    val factResolution: EvidenceFactResolution?,
    val supportingCandidates: List<ProductPackageQuantityEvidenceCandidate>,
    val blockers: Set<ProductPackageQuantityResolutionBlocker>
) {
    val resolved: Boolean
        get() =
            blockers.isEmpty() &&
                factResolution?.status == EvidenceFactResolutionStatus.RESOLVED &&
                supportingCandidates.isNotEmpty()
}

/**
 * Pure shared-core package-quantity conflict/materialization boundary.
 *
 * The resolver deliberately owns only five responsibilities:
 * 1. bound and uniquely identify the supplied materialized evidence candidates;
 * 2. retain claims for the exact requested product key;
 * 3. fail closed if one dataset namespace reuses a claim id for different claim content;
 * 4. delegate factual precedence/conflict handling to [EvidenceFactResolver];
 * 5. return every materialized candidate that supports the resolved fingerprint.
 *
 * It does not choose a price, evaluate unit-value math, authorize a provider/dataset, infer package
 * quantity, compare products, or select a final Best Value winner. Dataset lifecycle/legal gates
 * stay with source/production orchestration, and downstream factual-use policy remains responsible
 * for authority and materialized-value consistency.
 */
object ProductPackageQuantityFactResolver {

    private const val MAX_QUANTITY_CANDIDATES = 128

    fun validateCandidates(candidates: List<ProductPackageQuantityEvidenceCandidate>) {
        require(candidates.size <= MAX_QUANTITY_CANDIDATES)

        val evidenceIds = candidates.map { it.evidenceId }
        require(evidenceIds.size == evidenceIds.toSet().size) {
            "Package quantity evidence ids must be unique"
        }
    }

    fun resolve(
        productKey: String,
        candidates: List<ProductPackageQuantityEvidenceCandidate>
    ): ProductPackageQuantityResolutionResult {
        validateCandidates(candidates)

        val relevant =
            candidates.filter { candidate ->
                candidate.claim.scope.productKey == productKey
            }

        if (relevant.isEmpty()) {
            return blocked(ProductPackageQuantityResolutionBlocker.NO_RELEVANT_PACKAGE_QUANTITY)
        }

        val groupedByNamespaceClaimId =
            relevant.groupBy { candidate ->
                candidate.namespace.id to candidate.claim.claimId
            }

        val hasClaimIdCollision =
            groupedByNamespaceClaimId.values.any { grouped ->
                grouped.map { it.claim }.distinct().size > 1
            }

        if (hasClaimIdCollision) {
            return blocked(ProductPackageQuantityResolutionBlocker.CLAIM_ID_COLLISION)
        }

        val deduplicatedClaims =
            groupedByNamespaceClaimId
                .values
                .map { grouped ->
                    val candidate = grouped.first()
                    IndexedEvidenceClaim(
                        namespace = candidate.namespace,
                        claim = candidate.claim
                    )
                }

        val factKey =
            EvidenceFactKey(
                domain = EvidenceClaimDomain.PACKAGE_QUANTITY,
                scope = EvidenceClaimScope(productKey = productKey)
            )

        val factResolution =
            EvidenceFactResolver.resolve(deduplicatedClaims)
                .firstOrNull { resolution -> resolution.key == factKey }

        if (factResolution == null) {
            return blocked(ProductPackageQuantityResolutionBlocker.FACT_RESOLUTION_MISSING)
        }

        if (factResolution.status == EvidenceFactResolutionStatus.UNRESOLVED_CONFLICT) {
            return ProductPackageQuantityResolutionResult(
                factResolution = factResolution,
                supportingCandidates = emptyList(),
                blockers = setOf(ProductPackageQuantityResolutionBlocker.UNRESOLVED_CONFLICT)
            )
        }

        val selectedFingerprint = requireNotNull(factResolution.selectedValueFingerprint)
        val supportingCandidates =
            relevant
                .filter { candidate ->
                    candidate.claim.valueFingerprint == selectedFingerprint &&
                        factResolution.supportingClaims.any { supporting ->
                            supporting.namespace == candidate.namespace &&
                                supporting.claim == candidate.claim
                        }
                }
                .sortedBy { it.evidenceId }

        if (supportingCandidates.isEmpty()) {
            return ProductPackageQuantityResolutionResult(
                factResolution = factResolution,
                supportingCandidates = emptyList(),
                blockers =
                    setOf(
                        ProductPackageQuantityResolutionBlocker.RESOLVED_VALUE_NOT_MATERIALIZED
                    )
            )
        }

        return ProductPackageQuantityResolutionResult(
            factResolution = factResolution,
            supportingCandidates = supportingCandidates,
            blockers = emptySet()
        )
    }

    private fun blocked(
        blocker: ProductPackageQuantityResolutionBlocker
    ): ProductPackageQuantityResolutionResult =
        ProductPackageQuantityResolutionResult(
            factResolution = null,
            supportingCandidates = emptyList(),
            blockers = setOf(blocker)
        )
}
