package com.valuepilot.app

import com.valuepilot.core.RateUnit
import com.valuepilot.core.UnitRate
import java.math.BigDecimal

enum class UserObservedPriceUnitValueUiStatus {
    READY_FOR_VALUE_COMPARISON,
    HISTORICAL_PRICE_ONLY,
    OBSERVED_PRICE_AGING,
    OBSERVED_PRICE_STALE,
    OBSERVED_PRICE_FRESHNESS_UNKNOWN,
    OBSERVATION_TIME_INVALID,
    PRICE_PROOF_UNAVAILABLE,
    PACKAGE_QUANTITY_NEEDED,
    PACKAGE_QUANTITY_CONFLICT,
    EVIDENCE_BLOCKED,
    UNIT_VALUE_BLOCKED
}

/**
 * Immutable consumer-facing projection of one already-evaluated proof-backed observed-price unit
 * value result.
 *
 * [valueComparisonEligible] means only that the upstream proof/freshness policy, quantity resolver,
 * and evidence-backed unit-value policy accepted this one price/quantity pair. It is not a final
 * product rank, a Best Value winner, merchant-authoritative current-price evidence, availability,
 * promotion, or dataset activation.
 */
data class UserObservedPriceUnitValueUiState(
    val headline: String,
    val evidenceLabel: String,
    val status: UserObservedPriceUnitValueUiStatus,
    val statusTitle: String,
    val guidance: String,
    val unitRateText: String?,
    val valueComparisonEligible: Boolean,
    val notice: String
) {
    init {
        require(headline.isNotBlank())
        require(evidenceLabel.isNotBlank())
        require(statusTitle.isNotBlank())
        require(guidance.isNotBlank())
        require(notice.isNotBlank())
        require(
            valueComparisonEligible ==
                (status == UserObservedPriceUnitValueUiStatus.READY_FOR_VALUE_COMPARISON)
        )
        require((unitRateText != null) == valueComparisonEligible)
    }
}

/**
 * Pure presentation projector for the verified observed-price multi-quantity evaluator result.
 *
 * This projector consumes typed decisions only. It never reads proof, classifies freshness, resolves
 * package quantity, recalculates unit value, authorizes a source/dataset, or performs ranking. A
 * renderer therefore cannot turn display-only or rejected evidence into value-comparison authority.
 */
object UserObservedPriceUnitValueUiProjector {

    fun project(
        result: UserProofBackedObservedPriceUnitValueEligibilityResult
    ): UserObservedPriceUnitValueUiState {
        val status = status(result)
        val copy = copyFor(status, result.priceUse.reason)
        val eligible = result.rankable

        return UserObservedPriceUnitValueUiState(
            headline = "Observed price unit value",
            evidenceLabel = "Observed price",
            status = status,
            statusTitle = copy.first,
            guidance = copy.second,
            unitRateText =
                if (eligible) {
                    formatObservedPriceUnitRate(requireNotNull(result.unitValueResult?.rate))
                } else {
                    null
                },
            valueComparisonEligible = eligible,
            notice = "Observed prices are not retailer-confirmed current prices."
        )
    }

    private fun status(
        result: UserProofBackedObservedPriceUnitValueEligibilityResult
    ): UserObservedPriceUnitValueUiStatus {
        if (result.rankable) {
            return UserObservedPriceUnitValueUiStatus.READY_FOR_VALUE_COMPARISON
        }

        val blockers = result.blockers
        if (UserObservedPriceUnitValueEligibilityBlocker.PRICE_CLAIM_UNAVAILABLE in blockers) {
            return UserObservedPriceUnitValueUiStatus.PRICE_PROOF_UNAVAILABLE
        }

        if (
            UserObservedPriceUnitValueEligibilityBlocker.PACKAGE_QUANTITY_CLAIM_ID_COLLISION in blockers ||
            UserObservedPriceUnitValueEligibilityBlocker.PACKAGE_QUANTITY_FACT_RESOLUTION_MISSING in blockers ||
            UserObservedPriceUnitValueEligibilityBlocker.RESOLVED_PACKAGE_QUANTITY_NOT_MATERIALIZED in blockers
        ) {
            return UserObservedPriceUnitValueUiStatus.EVIDENCE_BLOCKED
        }

        if (UserObservedPriceUnitValueEligibilityBlocker.UNRESOLVED_PACKAGE_QUANTITY_CONFLICT in blockers) {
            return UserObservedPriceUnitValueUiStatus.PACKAGE_QUANTITY_CONFLICT
        }

        if (UserObservedPriceUnitValueEligibilityBlocker.NO_RELEVANT_PACKAGE_QUANTITY in blockers) {
            return UserObservedPriceUnitValueUiStatus.PACKAGE_QUANTITY_NEEDED
        }

        if (
            UserObservedPriceUnitValueEligibilityBlocker.UNIT_VALUE_POLICY_BLOCKED in blockers &&
            !result.priceUse.rankable
        ) {
            return when (result.priceUse.reason) {
                UserObservedPriceUseReason.RECEIPT_HISTORICAL_ONLY ->
                    UserObservedPriceUnitValueUiStatus.HISTORICAL_PRICE_ONLY

                UserObservedPriceUseReason.PRICE_TAG_AGING ->
                    UserObservedPriceUnitValueUiStatus.OBSERVED_PRICE_AGING

                UserObservedPriceUseReason.PRICE_TAG_STALE ->
                    UserObservedPriceUnitValueUiStatus.OBSERVED_PRICE_STALE

                UserObservedPriceUseReason.PRICE_TAG_UNKNOWN_FRESHNESS ->
                    UserObservedPriceUnitValueUiStatus.OBSERVED_PRICE_FRESHNESS_UNKNOWN

                UserObservedPriceUseReason.FUTURE_DATED ->
                    UserObservedPriceUnitValueUiStatus.OBSERVATION_TIME_INVALID

                UserObservedPriceUseReason.PROOF_NOT_RETAINED,
                UserObservedPriceUseReason.PROOF_VERIFICATION_FAILED ->
                    UserObservedPriceUnitValueUiStatus.PRICE_PROOF_UNAVAILABLE

                UserObservedPriceUseReason.VERIFIED_FRESH_PRICE_TAG ->
                    UserObservedPriceUnitValueUiStatus.UNIT_VALUE_BLOCKED
            }
        }

        return UserObservedPriceUnitValueUiStatus.UNIT_VALUE_BLOCKED
    }

    private fun copyFor(
        status: UserObservedPriceUnitValueUiStatus,
        priceReason: UserObservedPriceUseReason
    ): Pair<String, String> =
        when (status) {
            UserObservedPriceUnitValueUiStatus.READY_FOR_VALUE_COMPARISON ->
                "Exact observed unit value" to
                    "Verified fresh price-tag evidence and resolved package quantity support this exact unit value."

            UserObservedPriceUnitValueUiStatus.HISTORICAL_PRICE_ONLY ->
                "Historical observed price" to
                    "Receipt evidence is kept for history and display, not value comparison."

            UserObservedPriceUnitValueUiStatus.OBSERVED_PRICE_AGING ->
                "Observed price is aging" to
                    "This price-tag observation is no longer fresh enough for value comparison under the current policy."

            UserObservedPriceUnitValueUiStatus.OBSERVED_PRICE_STALE ->
                "Observed price is stale" to
                    "This price-tag observation is stale and is not used for value comparison."

            UserObservedPriceUnitValueUiStatus.OBSERVED_PRICE_FRESHNESS_UNKNOWN ->
                "Observed price freshness is unknown" to
                    "Freshness cannot be established under the current policy, so this observation is display-only."

            UserObservedPriceUnitValueUiStatus.OBSERVATION_TIME_INVALID ->
                "Observation time needs review" to
                    "The observation time is later than the allowed evaluation window, so this price is rejected."

            UserObservedPriceUnitValueUiStatus.PRICE_PROOF_UNAVAILABLE ->
                if (priceReason == UserObservedPriceUseReason.PROOF_NOT_RETAINED) {
                    "Price proof unavailable" to
                        "The retained proof for this observed price is missing, so no unit value is shown."
                } else {
                    "Price proof could not be verified" to
                        "The retained proof could not be verified, so no unit value is shown."
                }

            UserObservedPriceUnitValueUiStatus.PACKAGE_QUANTITY_NEEDED ->
                "Package quantity needed" to
                    "No eligible package quantity was resolved for this product, so unit value cannot be shown yet."

            UserObservedPriceUnitValueUiStatus.PACKAGE_QUANTITY_CONFLICT ->
                "Package quantity conflict" to
                    "Available package-quantity evidence conflicts, so no unit value is shown."

            UserObservedPriceUnitValueUiStatus.EVIDENCE_BLOCKED ->
                "Package quantity evidence blocked" to
                    "Package-quantity evidence could not be resolved safely, so no unit value is shown."

            UserObservedPriceUnitValueUiStatus.UNIT_VALUE_BLOCKED ->
                "Unit value unavailable" to
                    "The observed price and resolved package quantity could not be combined safely for value comparison."
        }
}

internal fun formatObservedPriceUnitRate(rate: UnitRate): String {
    val amount =
        BigDecimal.valueOf(rate.currencyMicrosPerUnit)
            .movePointLeft(6)
            .stripTrailingZeros()
            .toPlainString()
    val suffix =
        when (rate.unit) {
            RateUnit.KILOGRAM -> "kg"
            RateUnit.LITRE -> "L"
            RateUnit.ITEM -> "item"
            RateUnit.SQUARE_INCH -> "in²"
        }
    return "$amount ${rate.currencyCode}/$suffix"
}
