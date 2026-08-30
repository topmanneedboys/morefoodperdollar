package com.valuepilot.app

import com.valuepilot.core.BaseUnit
import com.valuepilot.core.CompareHereCandidate
import com.valuepilot.core.CompareHereComparisonIntentKey
import com.valuepilot.core.ExactScale
import com.valuepilot.core.Money
import com.valuepilot.core.NormalizedQuantity
import com.valuepilot.core.Offer
import com.valuepilot.core.ProductObservation
import com.valuepilot.core.PromotionTerms

private const val MAX_COMPARE_HERE_MANUAL_OBSERVATIONS = 32
private val ISO_CURRENCY = Regex("[A-Z]{3}")

enum class CompareHereManualInputFailure {
    TOO_MANY_OBSERVATIONS,
    DUPLICATE_OBSERVATION_IDS
}

enum class CompareHereManualObservationIssue {
    PARSE_FAILED,
    INVALID_CANDIDATE_ID,
    AMBIGUOUS_OR_MIXED_CURRENCY,
    UNSUPPORTED_PROMOTION,
    QUANTITY_NOT_EXACT_ENOUGH,
    INVALID_EXACT_FACTS,
    DISPLAY_NAME_OMITTED
}

data class CompareHereManualObservationIssueEntry(
    val observationId: String,
    val issue: CompareHereManualObservationIssue
)

data class CompareHereManualInputAdaptation(
    val comparisonIntentKey: CompareHereComparisonIntentKey,
    val candidates: List<CompareHereCandidate>,
    val displayMetadata: CompareHereDisplayMetadata,
    val issues: List<CompareHereManualObservationIssueEntry>
) {
    init {
        require(candidates.size <= MAX_COMPARE_HERE_MANUAL_OBSERVATIONS)
        require(candidates.map { it.candidateId }.distinct().size == candidates.size)
        require(
            displayMetadata.entries.all { metadata ->
                candidates.any { candidate -> candidate.candidateId == metadata.candidateId }
            }
        )
    }
}

sealed interface CompareHereManualInputResult {
    data class Success(
        val adaptation: CompareHereManualInputAdaptation
    ) : CompareHereManualInputResult

    data class Failure(
        val reason: CompareHereManualInputFailure
    ) : CompareHereManualInputResult
}

/**
 * Fail-closed compatibility boundary from the existing manual capture/parser path into Compare Here.
 *
 * This adapter does not infer semantic comparability and does not rank. The caller must supply the
 * already-established [CompareHereComparisonIntentKey]. Candidate identity comes from the capture
 * observation id, never from a product name.
 *
 * Legacy prices are admitted only when the raw block resolves to one concrete ISO-style currency.
 * Legacy quantity confidence is used here only as a narrow compatibility allow-list for the current
 * deterministic parser: its range midpoint is confidence 0.7, while stated mass/volume/count facts
 * are >= 0.9. Derived pizza area is deliberately omitted. An omitted quantity becomes `null`, so
 * the shared-core evaluator can block it rather than rank an estimate.
 *
 * Price-affecting legacy promotions are rejected unless their exact integer terms are known. BOGO
 * is losslessly represented as 2 received / 1 paid. Other multi-buy/percentage-derived promotion
 * forms remain outside this bridge until their source facts can be carried exactly.
 */
object CompareHereManualInputAdapter {

    fun adapt(
        comparisonIntentKey: CompareHereComparisonIntentKey,
        observations: List<ProductObservation>,
        parser: ProductParser = DeterministicProductParser
    ): CompareHereManualInputResult {
        if (observations.size > MAX_COMPARE_HERE_MANUAL_OBSERVATIONS) {
            return CompareHereManualInputResult.Failure(
                CompareHereManualInputFailure.TOO_MANY_OBSERVATIONS
            )
        }

        val observationIds = observations.map { it.id.value }
        if (observationIds.distinct().size != observationIds.size) {
            return CompareHereManualInputResult.Failure(
                CompareHereManualInputFailure.DUPLICATE_OBSERVATION_IDS
            )
        }

        val candidates = mutableListOf<CompareHereCandidate>()
        val metadata = mutableListOf<CompareHereDisplayMetadataEntry>()
        val issues = mutableListOf<CompareHereManualObservationIssueEntry>()

        observations.forEach { observation ->
            val observationId = observation.id.value
            if (!isSafeCandidateId(observationId)) {
                issues += issue(observationId, CompareHereManualObservationIssue.INVALID_CANDIDATE_ID)
                return@forEach
            }

            val parsed = parser.parse(observation.rawText, observation.sourceId)
            if (parsed == null) {
                issues += issue(observationId, CompareHereManualObservationIssue.PARSE_FAILED)
                return@forEach
            }

            val currency = concreteSingleCurrency(observation.rawText, parsed)
            if (currency == null) {
                issues += issue(
                    observationId,
                    CompareHereManualObservationIssue.AMBIGUOUS_OR_MIXED_CURRENCY
                )
                return@forEach
            }

            val promotionTerms = exactPromotionTerms(parsed.promotion)
            if (promotionTerms == null) {
                issues += issue(
                    observationId,
                    CompareHereManualObservationIssue.UNSUPPORTED_PROMOTION
                )
                return@forEach
            }

            val exactQuantity =
                parsed.quantity?.let { quantity ->
                    if (!isLegacyStatedQuantity(quantity)) {
                        issues += issue(
                            observationId,
                            CompareHereManualObservationIssue.QUANTITY_NOT_EXACT_ENOUGH
                        )
                        null
                    } else {
                        try {
                            exactQuantity(quantity)
                        } catch (_: IllegalArgumentException) {
                            issues += issue(
                                observationId,
                                CompareHereManualObservationIssue.QUANTITY_NOT_EXACT_ENOUGH
                            )
                            null
                        } catch (_: ArithmeticException) {
                            issues += issue(
                                observationId,
                                CompareHereManualObservationIssue.QUANTITY_NOT_EXACT_ENOUGH
                            )
                            null
                        }
                    }
                }

            val candidate =
                try {
                    val current = Money.fromMajorUnits(parsed.offer.currentPrice, currency)
                    val member = parsed.offer.memberPrice?.let { Money.fromMajorUnits(it, currency) }
                    CompareHereCandidate(
                        candidateId = observationId,
                        comparisonIntentKey = comparisonIntentKey,
                        offer =
                            Offer(
                                current = current,
                                member = member,
                                promotion = promotionTerms
                            ),
                        quantity = exactQuantity
                    )
                } catch (_: IllegalArgumentException) {
                    issues += issue(
                        observationId,
                        CompareHereManualObservationIssue.INVALID_EXACT_FACTS
                    )
                    return@forEach
                } catch (_: ArithmeticException) {
                    issues += issue(
                        observationId,
                        CompareHereManualObservationIssue.INVALID_EXACT_FACTS
                    )
                    return@forEach
                }

            candidates += candidate

            if (parsed.name.length <= 500) {
                metadata +=
                    CompareHereDisplayMetadataEntry(
                        candidateId = observationId,
                        displayName = parsed.name
                    )
            } else {
                issues += issue(
                    observationId,
                    CompareHereManualObservationIssue.DISPLAY_NAME_OMITTED
                )
            }
        }

        return CompareHereManualInputResult.Success(
            CompareHereManualInputAdaptation(
                comparisonIntentKey = comparisonIntentKey,
                candidates = candidates.toList(),
                displayMetadata = CompareHereDisplayMetadata(metadata.toList()),
                issues = issues.toList()
            )
        )
    }

    private fun isSafeCandidateId(value: String): Boolean =
        value.isNotBlank() &&
            value.length <= 240 &&
            value == value.trim() &&
            value.none { Character.isISOControl(it) }

    private fun concreteSingleCurrency(rawText: String, parsed: ValueItem): String? {
        val currencies = ValueEngine.prices(rawText).map { it.currency }.toSet()
        if (currencies.size != 1) return null
        val currency = currencies.single()
        if (!ISO_CURRENCY.matches(currency)) return null
        if (parsed.currency != currency || parsed.offer.currency != currency) return null
        return currency
    }

    private fun exactPromotionTerms(promotion: Promotion): PromotionTerms? =
        when (promotion.type) {
            "none", "percent-off-shown", "free-delivery" -> PromotionTerms()
            "bogo" ->
                if (promotion.receivedMultiplier == 2.0 && promotion.minPaidUnits == 1) {
                    PromotionTerms(
                        label = promotion.label,
                        receivedUnits = 2,
                        paidUnits = 1
                    )
                } else {
                    null
                }
            else -> null
        }

    private fun isLegacyStatedQuantity(quantity: Quantity): Boolean =
        quantity.kind != Quantity.Kind.PIZZA_AREA_SQIN &&
            quantity.confidence >= 0.9 &&
            !quantity.display.contains(" avg", ignoreCase = true)

    private fun exactQuantity(quantity: Quantity): NormalizedQuantity {
        val amountMicros = ExactScale.fromDouble(quantity.amountBase, 6)
        require(amountMicros > 0L)
        val unit =
            when (quantity.kind) {
                Quantity.Kind.MASS_G -> BaseUnit.GRAM
                Quantity.Kind.VOLUME_ML -> BaseUnit.MILLILITRE
                Quantity.Kind.COUNT -> BaseUnit.COUNT
                Quantity.Kind.PIZZA_AREA_SQIN -> error("Derived pizza area is not admitted here")
            }
        return NormalizedQuantity(amountMicros = amountMicros, unit = unit)
    }

    private fun issue(
        observationId: String,
        value: CompareHereManualObservationIssue
    ): CompareHereManualObservationIssueEntry =
        CompareHereManualObservationIssueEntry(observationId = observationId, issue = value)
}
