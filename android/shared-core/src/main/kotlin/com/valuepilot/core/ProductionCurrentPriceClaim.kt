package com.valuepilot.core

/**
 * Explicit factual scope and authority for one production current-price claim.
 *
 * Nothing here is inferred from an affiliate network, provider name, URL, or
 * feed transport. The adapter/application must supply the merchant/channel scope
 * and an auditable authority basis after the source semantics have been reviewed.
 */
data class ProductionCurrentPriceClaimDescriptor(
    val claimId: String,
    val merchantKey: String,
    val commerceChannelKey: String,
    val locationKey: String? = null,
    val authority: EvidenceAuthorityClass,
    val authorityBasisId: String
) {
    init {
        require(claimId.isNotBlank() && claimId.length <= 320)
        require(merchantKey.isNotBlank() && merchantKey.length <= 240)
        require(commerceChannelKey.isNotBlank() && commerceChannelKey.length <= 160)
        require(locationKey == null || (locationKey.isNotBlank() && locationKey.length <= 240))
        require(authorityBasisId.isNotBlank() && authorityBasisId.length <= 320)
    }
}

enum class ProductionCurrentPriceClaimBlocker {
    PRODUCTION_VIEW_BLOCKED,
    PRODUCT_KEY_UNAVAILABLE,
    UNSUPPORTED_CURRENT_PRICE_AUTHORITY,
    AUTHORITY_CLAIM_KIND_MISMATCH
}

/**
 * Provenance-preserving production wrapper around the generic [EvidenceClaim].
 *
 * The constructor is private so production code cannot manufacture or copy a
 * lifecycle-bound claim wrapper without passing [ProductionCurrentPriceClaimEvaluator].
 * [claim] is the factual object consumed by the existing conflict engine; the
 * surrounding fields retain the authority basis, acquisition provenance and
 * production lifecycle/disposition revisions that the generic claim does not own.
 *
 * This wrapper grants no rankability. Evidence acceptance, factual conflict
 * resolution, quantity authority, unit value and Best Value remain downstream.
 */
class ProductionCurrentPriceEvidence private constructor(
    val claim: EvidenceClaim,
    val productKey: ProductionProductEvidenceKey,
    val authorityBasisId: String,
    val provider: EvidenceProvider,
    val source: ShoppingSource,
    val dataset: EvidenceDatasetNamespace,
    val snapshot: ProductionDatasetSnapshotRef,
    val sourceProductIdentity: SourceProductIdentity,
    val acquisitionChannel: EvidenceChannel,
    val sourceClaimKind: EvidenceClaimKind,
    val currentPriceSourceFieldName: String,
    val offerCountryCode: String,
    val productionEvaluatedAtEpochMillis: Long,
    val lifecycleRevision: Long,
    val dispositionRevision: Long
) {
    init {
        require(claim.domain == EvidenceClaimDomain.CURRENT_PRICE)
        require(claim.scope.productKey == productKey.value)
        require(authorityBasisId.isNotBlank())
        require(currentPriceSourceFieldName.isNotBlank())
        require(offerCountryCode.matches(Regex("[A-Z]{2}")))
        require(productionEvaluatedAtEpochMillis > 0L)
        require(lifecycleRevision > 0L)
        require(dispositionRevision > 0L)
    }

    companion object {
        internal fun create(
            claim: EvidenceClaim,
            productKey: ProductionProductEvidenceKey,
            authorityBasisId: String,
            provider: EvidenceProvider,
            source: ShoppingSource,
            dataset: EvidenceDatasetNamespace,
            snapshot: ProductionDatasetSnapshotRef,
            sourceProductIdentity: SourceProductIdentity,
            acquisitionChannel: EvidenceChannel,
            sourceClaimKind: EvidenceClaimKind,
            currentPriceSourceFieldName: String,
            offerCountryCode: String,
            productionEvaluatedAtEpochMillis: Long,
            lifecycleRevision: Long,
            dispositionRevision: Long
        ): ProductionCurrentPriceEvidence =
            ProductionCurrentPriceEvidence(
                claim = claim,
                productKey = productKey,
                authorityBasisId = authorityBasisId,
                provider = provider,
                source = source,
                dataset = dataset,
                snapshot = snapshot,
                sourceProductIdentity = sourceProductIdentity,
                acquisitionChannel = acquisitionChannel,
                sourceClaimKind = sourceClaimKind,
                currentPriceSourceFieldName = currentPriceSourceFieldName,
                offerCountryCode = offerCountryCode,
                productionEvaluatedAtEpochMillis = productionEvaluatedAtEpochMillis,
                lifecycleRevision = lifecycleRevision,
                dispositionRevision = dispositionRevision
            )
    }
}

data class ProductionCurrentPriceClaimResult(
    val evidence: ProductionCurrentPriceEvidence?,
    val blockers: Set<ProductionCurrentPriceClaimBlocker>,
    val productionViewDecision: ProductionOfferViewResult
) {
    init {
        require((evidence != null) == blockers.isEmpty()) {
            "Current-price evidence exists if and only if all claim gates pass"
        }
    }

    val accepted: Boolean
        get() = evidence != null
}

/**
 * Lifecycle-bound bridge from raw provider evidence into the existing factual
 * claim/conflict engine.
 *
 * The evaluator re-runs [ProductionOfferViewEvaluator] from the raw import row.
 * It therefore never trusts a detached Offer, staged candidate, bound candidate,
 * activation result, or free-standing namespace disposition as authority.
 *
 * Passing this bridge creates only a CURRENT_PRICE factual claim with explicit
 * merchant/channel scope and explicit authority. It does NOT decide rankability,
 * package quantity, promotion, unit value or Best Value participation.
 */
object ProductionCurrentPriceClaimEvaluator {

    fun evaluate(
        record: ProviderOfferImportRecord,
        priceRoles: ProductionPriceFieldRoles,
        currentAuthorizationAssessment: ProviderProductionAuthorizationAssessment,
        activationProfile: ProductionActivationProfile,
        geography: ProviderDatasetOfferGeography,
        targetCountryCode: String,
        snapshot: ProductionDatasetSnapshotRef,
        lifecycleRegistry: ProductionDatasetLifecycleRegistry,
        dispositionRegistry: ProductionDatasetDispositionRegistry,
        descriptor: ProductionCurrentPriceClaimDescriptor,
        evaluatedAtEpochMillis: Long,
        offerFreshnessPolicy: EvidenceFreshnessPolicy
    ): ProductionCurrentPriceClaimResult {
        val productionViewDecision =
            ProductionOfferViewEvaluator.evaluate(
                record = record,
                priceRoles = priceRoles,
                currentAuthorizationAssessment = currentAuthorizationAssessment,
                activationProfile = activationProfile,
                geography = geography,
                targetCountryCode = targetCountryCode,
                snapshot = snapshot,
                lifecycleRegistry = lifecycleRegistry,
                dispositionRegistry = dispositionRegistry,
                evaluatedAtEpochMillis = evaluatedAtEpochMillis,
                offerFreshnessPolicy = offerFreshnessPolicy
            )

        val blockers = linkedSetOf<ProductionCurrentPriceClaimBlocker>()
        val view = productionViewDecision.view
        if (view == null) {
            blockers += ProductionCurrentPriceClaimBlocker.PRODUCTION_VIEW_BLOCKED
        }

        val productKey =
            view?.let {
                ProductionProductEvidenceKeyResolver.resolve(
                    providerId = it.provider.id,
                    identity = it.sourceProductIdentity
                )
            }
        if (view != null && productKey == null) {
            blockers += ProductionCurrentPriceClaimBlocker.PRODUCT_KEY_UNAVAILABLE
        }

        if (!isSupportedCurrentPriceAuthority(descriptor.authority)) {
            blockers += ProductionCurrentPriceClaimBlocker.UNSUPPORTED_CURRENT_PRICE_AUTHORITY
        } else if (!authorityMatchesClaimKind(descriptor.authority, record.claimKind)) {
            blockers += ProductionCurrentPriceClaimBlocker.AUTHORITY_CLAIM_KIND_MISMATCH
        }

        if (blockers.isNotEmpty()) {
            return ProductionCurrentPriceClaimResult(
                evidence = null,
                blockers = blockers,
                productionViewDecision = productionViewDecision
            )
        }

        val acceptedView = requireNotNull(view)
        val acceptedKey = requireNotNull(productKey)
        val stagedCandidate = requireNotNull(productionViewDecision.stagingDecision.candidate)
        val claim =
            EvidenceClaim(
                claimId = descriptor.claimId,
                domain = EvidenceClaimDomain.CURRENT_PRICE,
                valueFingerprint = EvidenceFingerprints.money(acceptedView.currentPrice),
                authority = descriptor.authority,
                scope =
                    EvidenceClaimScope(
                        productKey = acceptedKey.value,
                        merchantKey = descriptor.merchantKey,
                        locationKey = descriptor.locationKey,
                        commerceChannelKey = descriptor.commerceChannelKey,
                        currencyCode = acceptedView.currentPrice.currencyCode
                    ),
                observedAtEpochMillis = acceptedView.priceObservedAtEpochMillis
            )

        return ProductionCurrentPriceClaimResult(
            evidence =
                ProductionCurrentPriceEvidence.create(
                    claim = claim,
                    productKey = acceptedKey,
                    authorityBasisId = descriptor.authorityBasisId,
                    provider = acceptedView.provider,
                    source = acceptedView.source,
                    dataset = acceptedView.dataset,
                    snapshot = acceptedView.snapshot,
                    sourceProductIdentity = acceptedView.sourceProductIdentity,
                    acquisitionChannel = record.channel,
                    sourceClaimKind = record.claimKind,
                    currentPriceSourceFieldName = stagedCandidate.currentPriceSourceFieldName,
                    offerCountryCode = acceptedView.offerCountryCode,
                    productionEvaluatedAtEpochMillis = acceptedView.evaluatedAtEpochMillis,
                    lifecycleRevision = acceptedView.lifecycleRevision,
                    dispositionRevision = acceptedView.dispositionRevision
                ),
            blockers = emptySet(),
            productionViewDecision = productionViewDecision
        )
    }

    private fun isSupportedCurrentPriceAuthority(
        authority: EvidenceAuthorityClass
    ): Boolean =
        authority == EvidenceAuthorityClass.MERCHANT_AUTHORITATIVE ||
            authority == EvidenceAuthorityClass.PROOF_BACKED_DIRECT_OBSERVATION ||
            authority == EvidenceAuthorityClass.SOURCE_ASSERTED_METADATA

    private fun authorityMatchesClaimKind(
        authority: EvidenceAuthorityClass,
        claimKind: EvidenceClaimKind
    ): Boolean =
        when (authority) {
            EvidenceAuthorityClass.MERCHANT_AUTHORITATIVE,
            EvidenceAuthorityClass.SOURCE_ASSERTED_METADATA ->
                claimKind == EvidenceClaimKind.SOURCE_ASSERTED

            EvidenceAuthorityClass.PROOF_BACKED_DIRECT_OBSERVATION ->
                claimKind == EvidenceClaimKind.DIRECT_OBSERVATION

            else -> false
        }
}
