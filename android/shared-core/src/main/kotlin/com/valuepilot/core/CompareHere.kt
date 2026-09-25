package com.valuepilot.core

private const val MAX_COMPARE_HERE_CANDIDATES = 32
private const val MAX_COMPARE_HERE_KEY_LENGTH = 240

/**
 * Opaque upstream assertion that a set of products is semantically like-for-like.
 *
 * Compare Here never derives this key from product names, prices, barcodes, package units,
 * embeddings, or UI text. A capture/identity layer must establish the comparison intent first.
 */
data class CompareHereComparisonIntentKey(
    val value: String
) {
    init {
        require(value.isNotBlank())
        require(value.length <= MAX_COMPARE_HERE_KEY_LENGTH)
        require(value == value.trim())
        require(value.none { Character.isISOControl(it) })
    }
}

enum class CompareHerePriceSelection {
    CURRENT,
    MEMBER
}

/**
 * Exact, already-normalized facts for one in-store comparison candidate.
 *
 * Consumer display names and provider/source metadata intentionally do not belong here.
 * [quantity] may be unknown; unknown quantity is blocked rather than estimated.
 */
data class CompareHereCandidate(
    val candidateId: String,
    val comparisonIntentKey: CompareHereComparisonIntentKey,
    val offer: Offer,
    val quantity: NormalizedQuantity?
) {
    init {
        require(candidateId.isNotBlank())
        require(candidateId.length <= MAX_COMPARE_HERE_KEY_LENGTH)
    }
}

enum class CompareHereCandidateBlocker {
    COMPARISON_INTENT_MISMATCH,
    QUANTITY_UNKNOWN,
    SELECTED_PRICE_UNAVAILABLE,
    NON_POSITIVE_SELECTED_PRICE,
    UNIT_RATE_NOT_POSITIVE,
    ARITHMETIC_OVERFLOW
}

data class CompareHereBlockedCandidate(
    val candidateId: String,
    val blockers: Set<CompareHereCandidateBlocker>
) {
    init {
        require(candidateId.isNotBlank())
        require(blockers.isNotEmpty())
    }
}

/** Successful exact unit-rate calculation before any cross-candidate ranking. */
data class CompareHereExactCandidate(
    val candidateId: String,
    val comparisonIntentKey: CompareHereComparisonIntentKey,
    val selectedPrice: Money,
    val quantity: NormalizedQuantity,
    val rate: UnitRate
) {
    init {
        require(candidateId.isNotBlank())
        require(selectedPrice.minorUnits > 0L)
        require(rate.currencyMicrosPerUnit > 0L)
        require(rate.currencyCode == selectedPrice.currencyCode)
    }
}

data class CompareHereRankedCandidate(
    val candidate: CompareHereExactCandidate,
    val valueRank: Int,
    val deterministicOrder: Int
) {
    init {
        require(valueRank > 0)
        require(deterministicOrder > 0)
    }
}

enum class CompareHereComparisonIssue {
    MIXED_CURRENCIES,
    MIXED_RATE_UNITS
}

enum class CompareHereComparisonStatus {
    READY,
    NOT_ENOUGH_EXACT_CANDIDATES,
    INCOMPATIBLE_DIMENSIONS
}

data class CompareHereComparisonResult internal constructor(
    val comparisonIntentKey: CompareHereComparisonIntentKey,
    val priceSelection: CompareHerePriceSelection,
    val status: CompareHereComparisonStatus,
    val exactCandidates: List<CompareHereExactCandidate>,
    val rankedCandidates: List<CompareHereRankedCandidate>,
    val blockedCandidates: List<CompareHereBlockedCandidate>,
    val comparisonIssues: Set<CompareHereComparisonIssue>,
    val bestValueCandidateIds: List<String>
) {
    init {
        val exactIds = exactCandidates.map { it.candidateId }
        val blockedIds = blockedCandidates.map { it.candidateId }
        require(exactIds.distinct().size == exactIds.size)
        require(blockedIds.distinct().size == blockedIds.size)
        require(exactIds.toSet().intersect(blockedIds.toSet()).isEmpty())
        require(exactCandidates.all { it.comparisonIntentKey == comparisonIntentKey })
        require(bestValueCandidateIds.distinct().size == bestValueCandidateIds.size)

        val expectedDimensionIssues = linkedSetOf<CompareHereComparisonIssue>()
        if (exactCandidates.map { it.rate.currencyCode }.toSet().size > 1) {
            expectedDimensionIssues += CompareHereComparisonIssue.MIXED_CURRENCIES
        }
        if (exactCandidates.map { it.rate.unit }.toSet().size > 1) {
            expectedDimensionIssues += CompareHereComparisonIssue.MIXED_RATE_UNITS
        }

        when (status) {
            CompareHereComparisonStatus.READY -> {
                require(exactCandidates.size >= 2)
                require(expectedDimensionIssues.isEmpty())
                require(comparisonIssues.isEmpty())
                require(rankedCandidates.size == exactCandidates.size)
                require(
                    rankedCandidates.map { it.deterministicOrder } ==
                        (1..rankedCandidates.size).toList()
                )
                require(
                    rankedCandidates.map { it.candidate.candidateId }.toSet() ==
                        exactIds.toSet()
                )
                require(
                    rankedCandidates.zipWithNext().all { (left, right) ->
                        left.candidate.rate.currencyMicrosPerUnit <=
                            right.candidate.rate.currencyMicrosPerUnit
                    }
                )
                require(bestValueCandidateIds.isNotEmpty())
                require(
                    bestValueCandidateIds.toSet() ==
                        rankedCandidates
                            .filter { it.valueRank == 1 }
                            .map { it.candidate.candidateId }
                            .toSet()
                )
            }

            CompareHereComparisonStatus.NOT_ENOUGH_EXACT_CANDIDATES -> {
                require(exactCandidates.size < 2)
                require(rankedCandidates.isEmpty())
                require(comparisonIssues.isEmpty())
                require(bestValueCandidateIds.isEmpty())
            }

            CompareHereComparisonStatus.INCOMPATIBLE_DIMENSIONS -> {
                require(exactCandidates.size >= 2)
                require(rankedCandidates.isEmpty())
                require(expectedDimensionIssues.isNotEmpty())
                require(comparisonIssues == expectedDimensionIssues)
                require(bestValueCandidateIds.isEmpty())
            }
        }
    }

    val hasMeaningfulComparison: Boolean
        get() = status == CompareHereComparisonStatus.READY
}

/**
 * Provider-neutral, network-free exact comparison boundary for Compare Here.
 *
 * This evaluator assumes only that upstream supplied an explicit semantic comparison intent.
 * It does not decide whether "milk" is comparable to "juice", whether two barcodes are
 * substitutes, or whether a provider/source is authoritative. It performs deterministic unit
 * math over already-normalized user/source facts and fails closed when those facts are
 * insufficient or dimensionally incompatible.
 *
 * MEMBER selection is strict: a missing member price is blocked and never falls back to current
 * price. Promotion terms remain explicit on [Offer] and are applied by [DeterministicValueMath].
 * Affiliate/provider economics are absent from the API.
 */
object CompareHereEvaluator {

    fun evaluate(
        comparisonIntentKey: CompareHereComparisonIntentKey,
        priceSelection: CompareHerePriceSelection,
        candidates: List<CompareHereCandidate>
    ): CompareHereComparisonResult {
        require(candidates.size <= MAX_COMPARE_HERE_CANDIDATES) {
            "Compare Here candidate limit exceeded"
        }
        require(candidates.map { it.candidateId }.distinct().size == candidates.size) {
            "Compare Here candidate ids must be unique"
        }

        val exact = mutableListOf<CompareHereExactCandidate>()
        val blocked = mutableListOf<CompareHereBlockedCandidate>()

        candidates.forEach { candidate ->
            val blockers = linkedSetOf<CompareHereCandidateBlocker>()

            if (candidate.comparisonIntentKey != comparisonIntentKey) {
                blockers += CompareHereCandidateBlocker.COMPARISON_INTENT_MISMATCH
            }

            val quantity = candidate.quantity
            if (quantity == null) {
                blockers += CompareHereCandidateBlocker.QUANTITY_UNKNOWN
            }

            val selectedPrice =
                when (priceSelection) {
                    CompareHerePriceSelection.CURRENT -> candidate.offer.current
                    CompareHerePriceSelection.MEMBER -> candidate.offer.member
                }

            if (selectedPrice == null) {
                blockers += CompareHereCandidateBlocker.SELECTED_PRICE_UNAVAILABLE
            } else if (selectedPrice.minorUnits <= 0L) {
                blockers += CompareHereCandidateBlocker.NON_POSITIVE_SELECTED_PRICE
            }

            if (blockers.isNotEmpty()) {
                blocked += CompareHereBlockedCandidate(candidate.candidateId, blockers.toSet())
                return@forEach
            }

            val exactQuantity = requireNotNull(quantity)
            val exactPrice = requireNotNull(selectedPrice)
            val rate =
                try {
                    // Rebuild the arithmetic offer around the explicitly selected price so
                    // MEMBER mode can never inherit DeterministicValueMath's compatibility
                    // fallback to current price.
                    DeterministicValueMath.pricePerBaseUnit(
                        offer =
                            Offer(
                                current = exactPrice,
                                promotion = candidate.offer.promotion
                            ),
                        quantity = exactQuantity,
                        useMemberPrice = false
                    )
                } catch (_: ArithmeticException) {
                    blocked +=
                        CompareHereBlockedCandidate(
                            candidateId = candidate.candidateId,
                            blockers = setOf(CompareHereCandidateBlocker.ARITHMETIC_OVERFLOW)
                        )
                    return@forEach
                }

            if (rate.currencyMicrosPerUnit <= 0L) {
                blocked +=
                    CompareHereBlockedCandidate(
                        candidateId = candidate.candidateId,
                        blockers = setOf(CompareHereCandidateBlocker.UNIT_RATE_NOT_POSITIVE)
                    )
                return@forEach
            }

            exact +=
                CompareHereExactCandidate(
                    candidateId = candidate.candidateId,
                    comparisonIntentKey = comparisonIntentKey,
                    selectedPrice = exactPrice,
                    quantity = exactQuantity,
                    rate = rate
                )
        }

        val stableExact = exact.sortedBy { it.candidateId }
        val stableBlocked = blocked.sortedBy { it.candidateId }

        if (stableExact.size < 2) {
            return result(
                comparisonIntentKey = comparisonIntentKey,
                priceSelection = priceSelection,
                status = CompareHereComparisonStatus.NOT_ENOUGH_EXACT_CANDIDATES,
                exact = stableExact,
                blocked = stableBlocked
            )
        }

        val currencies = stableExact.map { it.rate.currencyCode }.toSet()
        val rateUnits = stableExact.map { it.rate.unit }.toSet()
        val issues = linkedSetOf<CompareHereComparisonIssue>()
        if (currencies.size > 1) {
            issues += CompareHereComparisonIssue.MIXED_CURRENCIES
        }
        if (rateUnits.size > 1) {
            issues += CompareHereComparisonIssue.MIXED_RATE_UNITS
        }

        if (issues.isNotEmpty()) {
            return result(
                comparisonIntentKey = comparisonIntentKey,
                priceSelection = priceSelection,
                status = CompareHereComparisonStatus.INCOMPATIBLE_DIMENSIONS,
                exact = stableExact,
                blocked = stableBlocked,
                issues = issues
            )
        }

        val sorted =
            stableExact.sortedWith(
                compareBy<CompareHereExactCandidate>(
                    { it.rate.currencyMicrosPerUnit },
                    { it.candidateId }
                )
            )

        var denseRank = 0
        var previousRate: Long? = null
        val ranked =
            sorted.mapIndexed { index, candidate ->
                if (previousRate == null || previousRate != candidate.rate.currencyMicrosPerUnit) {
                    denseRank += 1
                    previousRate = candidate.rate.currencyMicrosPerUnit
                }
                CompareHereRankedCandidate(
                    candidate = candidate,
                    valueRank = denseRank,
                    deterministicOrder = index + 1
                )
            }

        return CompareHereComparisonResult(
            comparisonIntentKey = comparisonIntentKey,
            priceSelection = priceSelection,
            status = CompareHereComparisonStatus.READY,
            exactCandidates = stableExact,
            rankedCandidates = ranked,
            blockedCandidates = stableBlocked,
            comparisonIssues = emptySet(),
            bestValueCandidateIds = ranked.filter { it.valueRank == 1 }.map { it.candidate.candidateId }
        )
    }

    private fun result(
        comparisonIntentKey: CompareHereComparisonIntentKey,
        priceSelection: CompareHerePriceSelection,
        status: CompareHereComparisonStatus,
        exact: List<CompareHereExactCandidate>,
        blocked: List<CompareHereBlockedCandidate>,
        issues: Set<CompareHereComparisonIssue> = emptySet()
    ): CompareHereComparisonResult =
        CompareHereComparisonResult(
            comparisonIntentKey = comparisonIntentKey,
            priceSelection = priceSelection,
            status = status,
            exactCandidates = exact,
            rankedCandidates = emptyList(),
            blockedCandidates = blocked,
            comparisonIssues = issues,
            bestValueCandidateIds = emptyList()
        )
}
