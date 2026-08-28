package com.valuepilot.core

/** Audit linkage retained by an immutable production presentation row. */
data class ProductionBestValuePresentationEvidenceLink(
    val priceProviderId: EvidenceProviderId,
    val priceSourceId: ShoppingSourceId,
    val priceDatasetNamespaceId: String,
    val priceSnapshotId: String,
    val priceClaimId: String,
    val quantityDatasetNamespaceId: String,
    val quantityClaimId: String,
    val quantityEvidenceId: String,
    val lifecycleRevision: Long,
    val dispositionRevision: Long
) {
    init {
        require(priceDatasetNamespaceId.isNotBlank())
        require(priceSnapshotId.isNotBlank())
        require(priceClaimId.isNotBlank())
        require(quantityDatasetNamespaceId.isNotBlank())
        require(quantityClaimId.isNotBlank())
        require(quantityEvidenceId.isNotBlank())
        require(lifecycleRevision > 0L)
        require(dispositionRevision > 0L)
    }
}

/**
 * Exact immutable row for a replaceable production presentation.
 *
 * This deliberately carries exact domain values rather than formatted strings or
 * legacy Android Double metrics. Locale-specific formatting belongs to the final
 * presentation adapter. referencePrice remains a provider reference/list price,
 * not a historical previous price.
 */
data class ProductionBestValuePresentationItem(
    val candidateId: String,
    val productKey: ProductionProductEvidenceKey,
    val productName: String,
    val providerDisplayName: String,
    val sourceDisplayName: String,
    val merchantKey: String,
    val locationKey: String?,
    val commerceChannelKey: String,
    val offerCountryCode: String,
    val currentPrice: Money,
    val referencePrice: Money?,
    val quantity: NormalizedQuantity,
    val unitRate: UnitRate,
    val availabilityState: AvailabilityState,
    val currentFreshness: EvidenceFreshness,
    val priceObservedAtEpochMillis: Long,
    val valueRank: Int,
    val deterministicOrder: Int,
    val bestValue: Boolean,
    val productUrl: String?,
    val imageUrl: String?,
    val evidenceLink: ProductionBestValuePresentationEvidenceLink
) {
    init {
        require(candidateId.isNotBlank())
        require(productName.isNotBlank())
        require(providerDisplayName.isNotBlank())
        require(sourceDisplayName.isNotBlank())
        require(merchantKey.isNotBlank())
        require(commerceChannelKey.isNotBlank())
        require(offerCountryCode.matches(Regex("[A-Z]{2}")))
        require(currentPrice.minorUnits > 0L)
        require(unitRate.currencyMicrosPerUnit > 0L)
        require(currentPrice.currencyCode == unitRate.currencyCode)
        require(valueRank > 0)
        require(deterministicOrder > 0)
        require(priceObservedAtEpochMillis > 0L)
    }
}

data class ProductionBestValuePresentationGroup(
    val key: ProductionBestValueComparisonKey,
    val meaningfulComparison: Boolean,
    val items: List<ProductionBestValuePresentationItem>
) {
    init {
        require(items.isNotEmpty())
        require(meaningfulComparison == (items.size >= 2))
        require(items.map { it.deterministicOrder } == (1..items.size).toList())
        require(
            items.all {
                it.unitRate.currencyCode == key.currencyCode &&
                    it.unitRate.unit == key.rateUnit
            }
        )
        require(items.none { it.bestValue } || meaningfulComparison)
    }
}

data class ProductionBestValueBlockedPresentationItem(
    val candidateId: String,
    val unitValueBlockers: Set<ProductionUnitValueEligibilityBlocker>,
    val priceBlockers: Set<ProductionCurrentPriceEligibilityBlocker>,
    val unitValuePolicyBlockReasons: Set<EvidenceBackedUnitValueBlockReason>
) {
    init {
        require(candidateId.isNotBlank())
        require(unitValueBlockers.isNotEmpty())
    }
}

/**
 * Point-in-time immutable projection for replaceable UIs.
 *
 * It is not a durable authorization token. A later display decision must run this
 * evaluator again because lifecycle, namespace disposition, authorization and
 * freshness can change.
 */
data class ProductionBestValuePresentationSnapshot(
    val evaluatedAtEpochMillis: Long,
    val groups: List<ProductionBestValuePresentationGroup>,
    val blockedItems: List<ProductionBestValueBlockedPresentationItem>
) {
    init {
        require(evaluatedAtEpochMillis > 0L)
    }
}

data class ProductionBestValuePresentationResult(
    val rankingDecision: ProductionBestValueRankingResult,
    val snapshot: ProductionBestValuePresentationSnapshot
)

/**
 * Raw-evidence-to-presentation boundary.
 *
 * The evaluator deliberately re-runs ProductionBestValueRankingEvaluator instead
 * of accepting a caller-supplied ranking result. Therefore each presentation
 * snapshot is tied to the same current lifecycle/disposition/freshness decision
 * instant used for ranking.
 *
 * No formatting, I/O, hidden clock, Android dependency, AI inference, promotion
 * inference or affiliate/provider-economic signal exists here.
 */
object ProductionBestValuePresentationEvaluator {

    fun evaluate(
        priceRequests: List<ProductionCurrentPriceEligibilityRequest>,
        candidates: List<ProductionBestValueCandidate>,
        lifecycleRegistry: ProductionDatasetLifecycleRegistry,
        dispositionRegistry: ProductionDatasetDispositionRegistry,
        evaluatedAtEpochMillis: Long,
        acceptancePolicy: EvidenceAcceptancePolicy,
        quantityCandidates: List<ProductPackageQuantityEvidenceCandidate>
    ): ProductionBestValuePresentationResult {
        require(evaluatedAtEpochMillis > 0L)

        val ranking =
            ProductionBestValueRankingEvaluator.evaluate(
                priceRequests = priceRequests,
                candidates = candidates,
                lifecycleRegistry = lifecycleRegistry,
                dispositionRegistry = dispositionRegistry,
                evaluatedAtEpochMillis = evaluatedAtEpochMillis,
                acceptancePolicy = acceptancePolicy,
                quantityCandidates = quantityCandidates
            )

        val groups =
            ranking.groups.map { group ->
                val bestIds = group.bestValueCandidateIds.toSet()
                val items =
                    group.rankedCandidates.map { ranked ->
                        val eligibility = ranked.unitValueEligibility
                        val priceEvidence = requireNotNull(eligibility.priceEligibility.eligibleEvidence)
                        val quantityEvidence = requireNotNull(eligibility.selectedQuantityEvidence)
                        val view =
                            requireNotNull(
                                eligibility
                                    .priceEligibility
                                    .candidateEvaluation
                                    ?.acceptanceResult
                                    ?.claimDecision
                                    ?.productionViewDecision
                                    ?.view
                            )
                        val scope = priceEvidence.claim.scope

                        ProductionBestValuePresentationItem(
                            candidateId = ranked.candidateId,
                            productKey = priceEvidence.productKey,
                            productName = view.productName,
                            providerDisplayName = priceEvidence.provider.displayName,
                            sourceDisplayName = priceEvidence.source.displayName,
                            merchantKey = requireNotNull(scope.merchantKey),
                            locationKey = scope.locationKey,
                            commerceChannelKey = requireNotNull(scope.commerceChannelKey),
                            offerCountryCode = priceEvidence.offerCountryCode,
                            currentPrice = view.currentPrice,
                            referencePrice = view.referencePrice,
                            quantity = quantityEvidence.quantity,
                            unitRate = ranked.rate,
                            availabilityState = view.availability.state,
                            currentFreshness = view.currentFreshness,
                            priceObservedAtEpochMillis = view.priceObservedAtEpochMillis,
                            valueRank = ranked.valueRank,
                            deterministicOrder = ranked.deterministicOrder,
                            bestValue = ranked.candidateId in bestIds,
                            productUrl = view.productUrl,
                            imageUrl = view.imageUrl,
                            evidenceLink =
                                ProductionBestValuePresentationEvidenceLink(
                                    priceProviderId = priceEvidence.provider.id,
                                    priceSourceId = priceEvidence.source.id,
                                    priceDatasetNamespaceId = priceEvidence.dataset.id,
                                    priceSnapshotId = priceEvidence.snapshot.snapshotId,
                                    priceClaimId = priceEvidence.claim.claimId,
                                    quantityDatasetNamespaceId = quantityEvidence.namespace.id,
                                    quantityClaimId = quantityEvidence.claim.claimId,
                                    quantityEvidenceId = quantityEvidence.evidenceId,
                                    lifecycleRevision = priceEvidence.lifecycleRevision,
                                    dispositionRevision = priceEvidence.dispositionRevision
                                )
                        )
                    }

                ProductionBestValuePresentationGroup(
                    key = group.key,
                    meaningfulComparison = group.hasMeaningfulComparison,
                    items = items
                )
            }

        val blocked =
            ranking.blockedCandidates.map { blockedCandidate ->
                val eligibility = blockedCandidate.unitValueEligibility
                ProductionBestValueBlockedPresentationItem(
                    candidateId = blockedCandidate.candidateId,
                    unitValueBlockers = eligibility.blockers,
                    priceBlockers = eligibility.priceEligibility.blockers,
                    unitValuePolicyBlockReasons =
                        eligibility.policyAttempts
                            .flatMap { it.result.blockReasons }
                            .toSet()
                )
            }

        return ProductionBestValuePresentationResult(
            rankingDecision = ranking,
            snapshot =
                ProductionBestValuePresentationSnapshot(
                    evaluatedAtEpochMillis = evaluatedAtEpochMillis,
                    groups = groups,
                    blockedItems = blocked
                )
        )
    }
}
