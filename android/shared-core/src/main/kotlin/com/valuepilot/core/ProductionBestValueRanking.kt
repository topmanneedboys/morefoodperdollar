package com.valuepilot.core

/**
 * Stable non-economic identifier for one production offer candidate to compare.
 *
 * The id exists only for deterministic result linkage/order. It must never encode
 * commission, EPC, payout, sponsorship, affiliate priority or provider preference.
 */
data class ProductionBestValueCandidate(
    val candidateId: String,
    val candidatePriceRequestId: String
) {
    init {
        require(candidateId.isNotBlank() && candidateId.length <= 240)
        require(candidatePriceRequestId.isNotBlank() && candidatePriceRequestId.length <= 240)
    }
}

/** Exact comparison dimension. Different keys are never cross-ranked. */
data class ProductionBestValueComparisonKey(
    val currencyCode: String,
    val rateUnit: RateUnit
) {
    init {
        require(currencyCode.matches(Regex("[A-Z]{3}")))
    }
}

data class ProductionBestValueRankedCandidate(
    val candidateId: String,
    val valueRank: Int,
    val deterministicOrder: Int,
    val rate: UnitRate,
    val unitValueEligibility: ProductionUnitValueEligibilityResult
) {
    init {
        require(candidateId.isNotBlank())
        require(valueRank > 0)
        require(deterministicOrder > 0)
        require(rate.currencyMicrosPerUnit > 0L)
        require(unitValueEligibility.rankable)
        require(unitValueEligibility.unitValueResult?.rate == rate)
    }
}

data class ProductionBestValueBlockedCandidate(
    val candidateId: String,
    val unitValueEligibility: ProductionUnitValueEligibilityResult
) {
    init {
        require(candidateId.isNotBlank())
        require(!unitValueEligibility.rankable)
    }
}

/**
 * One like-for-like Best Value group.
 *
 * valueRank is dense by exact unit rate. Equal rates share the same value rank.
 * deterministicOrder exists only to keep output stable when exact rates tie.
 *
 * A group with only one rankable candidate is not a meaningful comparison, so it
 * intentionally has no bestValueCandidateIds even though that candidate remains
 * rankable/displayable.
 */
data class ProductionBestValueGroup(
    val key: ProductionBestValueComparisonKey,
    val rankedCandidates: List<ProductionBestValueRankedCandidate>,
    val bestValueCandidateIds: List<String>
) {
    init {
        require(rankedCandidates.isNotEmpty())
        require(rankedCandidates.map { it.candidateId }.distinct().size == rankedCandidates.size)
        require(
            rankedCandidates.all {
                it.rate.currencyCode == key.currencyCode &&
                    it.rate.unit == key.rateUnit
            }
        )
        require(rankedCandidates.map { it.deterministicOrder } == (1..rankedCandidates.size).toList())
        require(bestValueCandidateIds.distinct().size == bestValueCandidateIds.size)
        require(bestValueCandidateIds.all { bestId -> rankedCandidates.any { it.candidateId == bestId } })
        require((rankedCandidates.size >= 2) == bestValueCandidateIds.isNotEmpty())
        if (bestValueCandidateIds.isNotEmpty()) {
            require(bestValueCandidateIds.toSet() == rankedCandidates.filter { it.valueRank == 1 }.map { it.candidateId }.toSet())
        }
    }

    val hasMeaningfulComparison: Boolean
        get() = rankedCandidates.size >= 2
}

data class ProductionBestValueRankingResult(
    val groups: List<ProductionBestValueGroup>,
    val blockedCandidates: List<ProductionBestValueBlockedCandidate>
) {
    val rankedCandidateCount: Int
        get() = groups.sumOf { it.rankedCandidates.size }

    val blockedCandidateCount: Int
        get() = blockedCandidates.size
}

/**
 * Bounded provider-neutral final unit-value ranking boundary.
 *
 * This evaluator never trusts a detached UnitRate as proof that an offer may rank.
 * Every candidate re-runs ProductionUnitValueEligibilityEvaluator from the same raw
 * bounded price/quantity evidence and current lifecycle/disposition registries.
 *
 * Only exact same-currency + same-RateUnit outcomes are comparable. Within one
 * comparison group, lower currencyMicrosPerUnit is better. Equal exact rates share
 * valueRank; candidateId is used only for deterministic display order.
 *
 * The evaluator performs no I/O, owns no clock, infers no promotion, converts no
 * exact values to Double, and has no affiliate/provider-economic ranking input.
 */
object ProductionBestValueRankingEvaluator {

    private const val MAX_PRICE_REQUESTS = 128
    private const val MAX_QUANTITY_CANDIDATES = 128
    private const val MAX_RANKING_CANDIDATES = 128

    fun evaluate(
        priceRequests: List<ProductionCurrentPriceEligibilityRequest>,
        candidates: List<ProductionBestValueCandidate>,
        lifecycleRegistry: ProductionDatasetLifecycleRegistry,
        dispositionRegistry: ProductionDatasetDispositionRegistry,
        evaluatedAtEpochMillis: Long,
        acceptancePolicy: EvidenceAcceptancePolicy,
        quantityCandidates: List<ProductPackageQuantityEvidenceCandidate>
    ): ProductionBestValueRankingResult {
        require(priceRequests.size <= MAX_PRICE_REQUESTS)
        require(quantityCandidates.size <= MAX_QUANTITY_CANDIDATES)
        require(candidates.size <= MAX_RANKING_CANDIDATES)

        val candidateIds = candidates.map { it.candidateId }
        require(candidateIds.size == candidateIds.toSet().size) {
            "Best Value candidate ids must be unique"
        }

        val priceRequestIds = candidates.map { it.candidatePriceRequestId }
        require(priceRequestIds.size == priceRequestIds.toSet().size) {
            "Each Best Value candidate must reference a unique price request"
        }

        data class EvaluatedCandidate(
            val candidate: ProductionBestValueCandidate,
            val eligibility: ProductionUnitValueEligibilityResult
        )

        val evaluated =
            candidates.map { candidate ->
                EvaluatedCandidate(
                    candidate = candidate,
                    eligibility =
                        ProductionUnitValueEligibilityEvaluator.evaluate(
                            priceRequests = priceRequests,
                            candidatePriceRequestId = candidate.candidatePriceRequestId,
                            lifecycleRegistry = lifecycleRegistry,
                            dispositionRegistry = dispositionRegistry,
                            evaluatedAtEpochMillis = evaluatedAtEpochMillis,
                            acceptancePolicy = acceptancePolicy,
                            quantityCandidates = quantityCandidates
                        )
                )
            }

        val blocked =
            evaluated
                .filterNot { it.eligibility.rankable }
                .map {
                    ProductionBestValueBlockedCandidate(
                        candidateId = it.candidate.candidateId,
                        unitValueEligibility = it.eligibility
                    )
                }

        val rankable =
            evaluated.mapNotNull { item ->
                if (!item.eligibility.rankable) return@mapNotNull null
                val rate = requireNotNull(item.eligibility.unitValueResult?.rate)
                require(rate.currencyMicrosPerUnit > 0L)
                Triple(item.candidate, item.eligibility, rate)
            }

        val groups =
            rankable
                .groupBy { (_, _, rate) ->
                    ProductionBestValueComparisonKey(
                        currencyCode = rate.currencyCode,
                        rateUnit = rate.unit
                    )
                }
                .entries
                .sortedWith(
                    compareBy<Map.Entry<ProductionBestValueComparisonKey, List<Triple<ProductionBestValueCandidate, ProductionUnitValueEligibilityResult, UnitRate>>>>(
                        { it.key.currencyCode },
                        { it.key.rateUnit.ordinal }
                    )
                )
                .map { (key, groupItems) ->
                    val sorted =
                        groupItems.sortedWith(
                            compareBy<Triple<ProductionBestValueCandidate, ProductionUnitValueEligibilityResult, UnitRate>>(
                                { it.third.currencyMicrosPerUnit },
                                { it.first.candidateId }
                            )
                        )

                    var denseRank = 0
                    var previousRate: Long? = null
                    val ranked =
                        sorted.mapIndexed { index, (candidate, eligibility, rate) ->
                            if (previousRate == null || previousRate != rate.currencyMicrosPerUnit) {
                                denseRank += 1
                                previousRate = rate.currencyMicrosPerUnit
                            }
                            ProductionBestValueRankedCandidate(
                                candidateId = candidate.candidateId,
                                valueRank = denseRank,
                                deterministicOrder = index + 1,
                                rate = rate,
                                unitValueEligibility = eligibility
                            )
                        }

                    val bestIds =
                        if (ranked.size < 2) {
                            emptyList()
                        } else {
                            ranked.filter { it.valueRank == 1 }.map { it.candidateId }
                        }

                    ProductionBestValueGroup(
                        key = key,
                        rankedCandidates = ranked,
                        bestValueCandidateIds = bestIds
                    )
                }

        return ProductionBestValueRankingResult(
            groups = groups,
            blockedCandidates = blocked
        )
    }
}
