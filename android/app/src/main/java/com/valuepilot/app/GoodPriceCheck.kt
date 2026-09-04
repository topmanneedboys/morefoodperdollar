package com.valuepilot.app

import com.valuepilot.core.CompareHereComparisonIntentKey
import com.valuepilot.core.CompareHerePriceSelection
import com.valuepilot.core.UnitRate
import java.math.BigDecimal
import java.math.RoundingMode

/** Presentation tone for a personal-price answer; it is not a ranking score. */
internal enum class GoodPriceCheckAnswerTone {
    POSITIVE,
    NEUTRAL,
    CAUTION
}

/**
 * Immutable consumer result for one exact user-entered price.
 *
 * The result exposes only already-formatted exact facts and clearly scoped personal context. It
 * never claims a live retailer price, stock, availability or a public market benchmark.
 */
internal data class GoodPriceCheckUiState(
    val headline: String,
    val priceModeText: String,
    val productName: String,
    val priceText: String,
    val quantityText: String,
    val unitRateText: String,
    val answerTitle: String,
    val answerGuidance: String,
    val answerTone: GoodPriceCheckAnswerTone,
    val historyText: String?,
    val disclosure: String
) {
    init {
        require(headline.isNotBlank())
        require(priceModeText.isNotBlank())
        require(productName.isNotBlank())
        require(priceText.isNotBlank())
        require(quantityText.isNotBlank())
        require(unitRateText.isNotBlank())
        require(answerTitle.isNotBlank())
        require(answerGuidance.isNotBlank())
        require(historyText == null || historyText.isNotBlank())
        require(disclosure.isNotBlank())
    }
}

enum class GoodPriceCheckRouteStatus {
    NEEDS_PRODUCT,
    INPUT_TOO_LONG,
    PRODUCT_ENTRY_TOO_LONG,
    PRODUCT_REJECTED,
    NEEDS_EXACT_INFORMATION,
    EVALUATED
}

internal data class GoodPriceCheckRouteState(
    val status: GoodPriceCheckRouteStatus,
    val title: String,
    val guidance: String,
    val result: GoodPriceCheckUiState? = null
) {
    init {
        require(title.isNotBlank())
        require(guidance.isNotBlank())
        require((result != null) == (status == GoodPriceCheckRouteStatus.EVALUATED))
    }
}

/** Route state plus the typed exact observation that may be remembered after the user checks it. */
internal data class GoodPriceCheckRouteEvaluation(
    val state: GoodPriceCheckRouteState,
    val privateMemoryCapture: CompareHerePrivatePriceMemoryCapture? = null
)

/**
 * Pure application boundary for the first-class "Is this a good price?" question.
 *
 * The existing manual adapter and Compare Here evaluator remain the authorities for parsing,
 * exact quantity/price math and selected-price semantics. This coordinator only turns their
 * single-candidate result into personal-history presentation and a typed local-memory capture.
 */
internal object GoodPriceCheckRouteCoordinator {
    private val comparisonIntent =
        CompareHereComparisonIntentKey("manual:good-price-check-v1")

    fun checkBlock(
        rawBlock: String,
        observedAtEpochMillis: Long,
        priceSelection: CompareHerePriceSelection,
        privateMemory: CompareHerePrivatePriceMemoryState,
        parser: ProductParser = DeterministicProductParser
    ): GoodPriceCheckRouteEvaluation {
        if (rawBlock.isBlank()) {
            return message(
                status = GoodPriceCheckRouteStatus.NEEDS_PRODUCT,
                title = "Enter one product",
                guidance = "Add the product name, package quantity, and price before checking it."
            )
        }

        val capture =
            ManualProductObservationAdapter.captureBlocks(
                rawBlocks = listOf(rawBlock),
                observedAtEpochMillis = observedAtEpochMillis
            )

        val observations =
            when (capture) {
                is ManualCaptureResult.Success -> capture.observations
                is ManualCaptureResult.Failure ->
                    return message(
                        status =
                            when (capture.reason) {
                                ManualCaptureFailure.INPUT_TOO_LONG ->
                                    GoodPriceCheckRouteStatus.INPUT_TOO_LONG

                                ManualCaptureFailure.BLOCK_TOO_LONG ->
                                    GoodPriceCheckRouteStatus.PRODUCT_ENTRY_TOO_LONG

                                ManualCaptureFailure.TOO_MANY_BLOCKS ->
                                    GoodPriceCheckRouteStatus.PRODUCT_REJECTED
                            },
                        title =
                            when (capture.reason) {
                                ManualCaptureFailure.INPUT_TOO_LONG -> "Product details are too long"
                                ManualCaptureFailure.BLOCK_TOO_LONG ->
                                    "The product entry is too long"

                                ManualCaptureFailure.TOO_MANY_BLOCKS ->
                                    "Enter one product at a time"
                            },
                        guidance =
                            when (capture.reason) {
                                ManualCaptureFailure.INPUT_TOO_LONG,
                                ManualCaptureFailure.BLOCK_TOO_LONG ->
                                    "Shorten the product details and try again."

                                ManualCaptureFailure.TOO_MANY_BLOCKS ->
                                    "Use one product block for this question."
                            }
                    )
            }

        val comparison =
            CompareHereManualComparisonService.compare(
                comparisonIntentKey = comparisonIntent,
                priceSelection = priceSelection,
                observations = observations,
                parser = parser
            )

        return when (comparison) {
            is CompareHereManualComparisonResult.InputFailure ->
                message(
                    status = GoodPriceCheckRouteStatus.PRODUCT_REJECTED,
                    title = "This product could not be checked safely",
                    guidance = "Check the product details and try again."
                )

            is CompareHereManualComparisonResult.RejectedObservations ->
                message(
                    status = GoodPriceCheckRouteStatus.PRODUCT_REJECTED,
                    title = "This product needs clearer information",
                    guidance =
                        "Include one clear currency, price, package quantity, and any exact promotion terms."
                )

            is CompareHereManualComparisonResult.Success ->
                evaluatedComparison(
                    comparison = comparison,
                    observedAtEpochMillis = observedAtEpochMillis,
                    privateMemory = privateMemory,
                    priceSelection = priceSelection
                )
        }
    }

    private fun evaluatedComparison(
        comparison: CompareHereManualComparisonResult.Success,
        observedAtEpochMillis: Long,
        privateMemory: CompareHerePrivatePriceMemoryState,
        priceSelection: CompareHerePriceSelection
    ): GoodPriceCheckRouteEvaluation {
        val projected = comparison.projection
        val exact = projected.exactByCandidateId.values.singleOrNull()
        if (exact == null) {
            val reason = projected.state.blockedRows.singleOrNull()?.reasonText
            return message(
                status = GoodPriceCheckRouteStatus.NEEDS_EXACT_INFORMATION,
                title = "Need an exact price and package",
                guidance = reason ?: projected.state.guidance
            )
        }

        val displayName =
            projected.displayNameByCandidateId[exact.candidateId]
                ?: return message(
                    status = GoodPriceCheckRouteStatus.NEEDS_EXACT_INFORMATION,
                    title = "Need a clear product name",
                    guidance = "Add a short product name, package quantity, and price, then try again."
                )
        val candidate =
            comparison.adaptation.candidates.singleOrNull { it.candidateId == exact.candidateId }
                ?: return message(
                    status = GoodPriceCheckRouteStatus.PRODUCT_REJECTED,
                    title = "This product could not be checked safely",
                    guidance = "Check the product details and try again."
                )

        val promotion = candidate.offer.promotion
        val entry =
            runCatching {
                CompareHerePrivatePriceMemoryEntry.fromExactCandidate(
                    candidate = exact,
                    displayName = displayName,
                    priceSelection = priceSelection,
                    promotionLabel = promotion.label,
                    promotionReceivedUnits = promotion.receivedUnits,
                    promotionPaidUnits = promotion.paidUnits,
                    observedAtEpochMillis = observedAtEpochMillis,
                    source = CompareHerePrivatePriceMemorySource.CONFIRMED_GOOD_PRICE_CHECK
                )
            }.getOrNull()
                ?: return message(
                    status = GoodPriceCheckRouteStatus.PRODUCT_REJECTED,
                    title = "This product could not be remembered safely",
                    guidance = "Check the product details and try again."
                )

        val insight = CompareHerePriceMemoryEvaluator.assess(entry, privateMemory)
        val (answerTitle, answerGuidance, tone) = answerFor(insight, entry.rate)
        val historyText =
            if (insight.assessment == CompareHerePriceMemoryAssessment.NO_MATCHING_HISTORY) {
                null
            } else {
                CompareHerePriceMemoryHistory.summarize(entry, privateMemory).let {
                    CompareHerePriceMemoryInsightPresenter.describeHistory(it)
                }
            }

        return GoodPriceCheckRouteEvaluation(
            state =
                GoodPriceCheckRouteState(
                    status = GoodPriceCheckRouteStatus.EVALUATED,
                    title = "Price checked",
                    guidance = "This answer uses your private history only; it is not a live store claim.",
                    result =
                        GoodPriceCheckUiState(
                            headline = "Is this a good price?",
                            priceModeText =
                                if (priceSelection == CompareHerePriceSelection.MEMBER) {
                                    "Member price"
                                } else {
                                    "Price you entered"
                                },
                            productName = displayName,
                            priceText = formatCompareHereMoney(exact.selectedPrice),
                            quantityText = formatCompareHereQuantity(exact.quantity),
                            unitRateText = formatCompareHereRate(exact.rate),
                            answerTitle = answerTitle,
                            answerGuidance = answerGuidance,
                            answerTone = tone,
                            historyText = historyText,
                            disclosure =
                                "Not live store pricing. Personal history matches exact package quantity, currency, price basis, and promotion terms."
                        )
                ),
            privateMemoryCapture = CompareHerePrivatePriceMemoryCapture(listOf(entry))
        )
    }

    private fun answerFor(
        insight: CompareHerePriceMemoryInsight,
        currentRate: UnitRate
    ): Triple<String, String, GoodPriceCheckAnswerTone> =
        when (insight.assessment) {
            CompareHerePriceMemoryAssessment.NO_MATCHING_HISTORY ->
                Triple(
                    "Not enough history yet",
                    "There is not enough matching evidence to tell whether this is unusually good. ValuePilot will remember this exact package on this device.",
                    GoodPriceCheckAnswerTone.NEUTRAL
                )

            CompareHerePriceMemoryAssessment.LOWER_THAN_LAST ->
                Triple(
                    "Below your last remembered price",
                    lowerRateGuidance(
                        currentRate = currentRate,
                        referenceRate = requireNotNull(insight.lastRate),
                        referenceDescription = "your last matching observation"
                    ),
                    GoodPriceCheckAnswerTone.POSITIVE
                )

            CompareHerePriceMemoryAssessment.SAME_AS_LAST ->
                Triple(
                    "Matches your last remembered price",
                    "This matches your last matching observation. ValuePilot cannot call it unusually good from this evidence alone.",
                    GoodPriceCheckAnswerTone.NEUTRAL
                )

            CompareHerePriceMemoryAssessment.HIGHER_THAN_LAST ->
                Triple(
                    "Above your last remembered price",
                    higherRateGuidance(
                        currentRate = currentRate,
                        referenceRate = requireNotNull(insight.lastRate),
                        referenceDescription = "your last matching observation"
                    ),
                    GoodPriceCheckAnswerTone.CAUTION
                )

            CompareHerePriceMemoryAssessment.BELOW_PERSONAL_RANGE ->
                Triple(
                    "Below your remembered range",
                    lowerRateGuidance(
                        currentRate = currentRate,
                        referenceRate = requireNotNull(insight.minimumRate),
                        referenceDescription = "your previous personal low",
                        suffix = "It looks better for you, but it is not a live-market guarantee."
                    ),
                    GoodPriceCheckAnswerTone.POSITIVE
                )

            CompareHerePriceMemoryAssessment.WITHIN_PERSONAL_RANGE ->
                Triple(
                    "Within your remembered range",
                    "This falls inside your matching personal history. ValuePilot does not have enough evidence to call it unusually good.",
                    GoodPriceCheckAnswerTone.NEUTRAL
                )

            CompareHerePriceMemoryAssessment.ABOVE_PERSONAL_RANGE ->
                Triple(
                    "Above your remembered range",
                    higherRateGuidance(
                        currentRate = currentRate,
                        referenceRate = requireNotNull(insight.maximumRate),
                        referenceDescription = "your previous personal high",
                        suffix = "Check the package and promotion details before deciding."
                    ),
                    GoodPriceCheckAnswerTone.CAUTION
                )
        }

    private fun lowerRateGuidance(
        currentRate: UnitRate,
        referenceRate: UnitRate,
        referenceDescription: String,
        suffix: String =
            "That is useful personal context, not a guarantee of the cheapest price."
    ): String {
        val percentage = relativeRateGapPercentage(currentRate, referenceRate)
        val comparison =
            percentage?.let {
                "This is about $it% lower per unit than $referenceDescription."
            } ?: "This is lower per unit than $referenceDescription."
        return "$comparison $suffix"
    }

    private fun higherRateGuidance(
        currentRate: UnitRate,
        referenceRate: UnitRate,
        referenceDescription: String,
        suffix: String =
            "Check the package and promotion details before deciding."
    ): String {
        val percentage = relativeRateGapPercentage(currentRate, referenceRate)
        val comparison =
            percentage?.let {
                "This is about $it% higher per unit than $referenceDescription."
            } ?: "This is higher per unit than $referenceDescription."
        return "$comparison $suffix"
    }

    /**
     * Formats an exact relative unit-rate gap for personal context only. The caller has already
     * established that both rates describe the same package/history match; this helper never
     * creates a ranking or market claim. A rounded zero stays unquantified rather than implying
     * a meaningful difference that the one-decimal display cannot show.
     */
    private fun relativeRateGapPercentage(
        currentRate: UnitRate,
        referenceRate: UnitRate
    ): String? {
        if (
            currentRate.currencyCode != referenceRate.currencyCode ||
            currentRate.unit != referenceRate.unit ||
            currentRate.currencyMicrosPerUnit <= 0L ||
            referenceRate.currencyMicrosPerUnit <= 0L ||
            currentRate.currencyMicrosPerUnit == referenceRate.currencyMicrosPerUnit
        ) {
            return null
        }

        val percentage =
            BigDecimal.valueOf(currentRate.currencyMicrosPerUnit)
                .subtract(BigDecimal.valueOf(referenceRate.currencyMicrosPerUnit))
                .abs()
                .multiply(BigDecimal.valueOf(100L))
                .divide(
                    BigDecimal.valueOf(referenceRate.currencyMicrosPerUnit),
                    1,
                    RoundingMode.HALF_UP
                )
        return percentage.takeIf { it.signum() > 0 }?.toPlainString()
    }

    private fun message(
        status: GoodPriceCheckRouteStatus,
        title: String,
        guidance: String
    ): GoodPriceCheckRouteEvaluation =
        GoodPriceCheckRouteEvaluation(
            state =
                GoodPriceCheckRouteState(
                    status = status,
                    title = title,
                    guidance = guidance
                )
        )
}
