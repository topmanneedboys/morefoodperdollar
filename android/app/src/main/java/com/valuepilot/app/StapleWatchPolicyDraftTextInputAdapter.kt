package com.valuepilot.app

import com.valuepilot.core.Money

/** Raw text fields owned by a replaceable physical policy-entry adapter. */
sealed interface StapleWatchPolicyDraftTextInput {
    val text: String

    data class MinimumSwitchSavings(
        override val text: String
    ) : StapleWatchPolicyDraftTextInput

    data class MaxAdditionalTravelSeconds(
        override val text: String
    ) : StapleWatchPolicyDraftTextInput

    data class MaxAdditionalDistanceMetres(
        override val text: String
    ) : StapleWatchPolicyDraftTextInput

    data class MinimumStapleItemCount(
        override val text: String
    ) : StapleWatchPolicyDraftTextInput
}

enum class StapleWatchPolicyDraftTextInputFailure {
    BLANK,
    INVALID_FORMAT,
    OUT_OF_RANGE
}

sealed interface StapleWatchPolicyDraftTextInputResult {
    data class Success(
        val action: StapleWatchPolicyDraftUiAction
    ) : StapleWatchPolicyDraftTextInputResult

    data class Failure(
        val reason: StapleWatchPolicyDraftTextInputFailure
    ) : StapleWatchPolicyDraftTextInputResult
}

/**
 * Pure text-to-typed-action compatibility boundary for a future physical policy surface.
 *
 * Savings text is parsed only under the exact currency code and fraction digits carried by the
 * currently rendered [StapleWatchPolicyDraftUiState]. The accepted textual decimal form is
 * deliberately locale-neutral: optional leading minus, ASCII digits and an optional `.` fraction
 * with no grouping separators or currency symbols. [Money.parse] performs the exact checked
 * conversion to minor units; no floating-point conversion is used.
 *
 * Travel seconds, distance metres and staple count are parsed as signed base-10 whole numbers.
 * This adapter owns syntax and numeric overflow only. It deliberately does not reject negative
 * values or enforce staple-count/economic ranges; the immutable policy draft remains the sole
 * domain-validation owner and the route session already fails closed when it rejects a typed edit.
 *
 * The adapter owns no Android classes, device locale, defaults, policy construction, draft
 * mutation, evaluation, persistence, provider/network access, background work or notifications.
 */
object StapleWatchPolicyDraftTextInputAdapter {

    fun adapt(
        state: StapleWatchPolicyDraftUiState,
        input: StapleWatchPolicyDraftTextInput
    ): StapleWatchPolicyDraftTextInputResult {
        val value = input.text.trim()
        if (value.isEmpty()) {
            return StapleWatchPolicyDraftTextInputResult.Failure(
                StapleWatchPolicyDraftTextInputFailure.BLANK
            )
        }

        return when (input) {
            is StapleWatchPolicyDraftTextInput.MinimumSwitchSavings ->
                adaptSavings(state = state, value = value)
            is StapleWatchPolicyDraftTextInput.MaxAdditionalTravelSeconds ->
                adaptLong(value) { seconds ->
                    StapleWatchPolicyDraftUiAction.SetMaxAdditionalTravelSeconds(seconds)
                }
            is StapleWatchPolicyDraftTextInput.MaxAdditionalDistanceMetres ->
                adaptLong(value) { metres ->
                    StapleWatchPolicyDraftUiAction.SetMaxAdditionalDistanceMetres(metres)
                }
            is StapleWatchPolicyDraftTextInput.MinimumStapleItemCount ->
                adaptInt(value) { count ->
                    StapleWatchPolicyDraftUiAction.SetMinimumStapleItemCount(count)
                }
        }
    }

    private fun adaptSavings(
        state: StapleWatchPolicyDraftUiState,
        value: String
    ): StapleWatchPolicyDraftTextInputResult {
        val fractionDigits = state.currencyFractionDigits
        val canonicalDecimal =
            if (fractionDigits == 0) {
                Regex("-?\\d+")
            } else {
                Regex("-?\\d+(?:\\.\\d{1,$fractionDigits})?")
            }
        if (!canonicalDecimal.matches(value)) {
            return StapleWatchPolicyDraftTextInputResult.Failure(
                StapleWatchPolicyDraftTextInputFailure.INVALID_FORMAT
            )
        }

        val money =
            try {
                Money.parse(
                    decimal = value,
                    currencyCode = state.currencyCode,
                    fractionDigits = fractionDigits
                )
            } catch (_: IllegalArgumentException) {
                return StapleWatchPolicyDraftTextInputResult.Failure(
                    StapleWatchPolicyDraftTextInputFailure.OUT_OF_RANGE
                )
            } catch (_: ArithmeticException) {
                return StapleWatchPolicyDraftTextInputResult.Failure(
                    StapleWatchPolicyDraftTextInputFailure.OUT_OF_RANGE
                )
            }

        return StapleWatchPolicyDraftTextInputResult.Success(
            StapleWatchPolicyDraftUiAction.SetMinimumSwitchSavingsMinorUnits(money.minorUnits)
        )
    }

    private fun adaptLong(
        value: String,
        action: (Long) -> StapleWatchPolicyDraftUiAction
    ): StapleWatchPolicyDraftTextInputResult {
        if (!WHOLE_NUMBER.matches(value)) {
            return StapleWatchPolicyDraftTextInputResult.Failure(
                StapleWatchPolicyDraftTextInputFailure.INVALID_FORMAT
            )
        }
        val parsed = value.toLongOrNull()
            ?: return StapleWatchPolicyDraftTextInputResult.Failure(
                StapleWatchPolicyDraftTextInputFailure.OUT_OF_RANGE
            )
        return StapleWatchPolicyDraftTextInputResult.Success(action(parsed))
    }

    private fun adaptInt(
        value: String,
        action: (Int) -> StapleWatchPolicyDraftUiAction
    ): StapleWatchPolicyDraftTextInputResult {
        if (!WHOLE_NUMBER.matches(value)) {
            return StapleWatchPolicyDraftTextInputResult.Failure(
                StapleWatchPolicyDraftTextInputFailure.INVALID_FORMAT
            )
        }
        val parsed = value.toIntOrNull()
            ?: return StapleWatchPolicyDraftTextInputResult.Failure(
                StapleWatchPolicyDraftTextInputFailure.OUT_OF_RANGE
            )
        return StapleWatchPolicyDraftTextInputResult.Success(action(parsed))
    }

    private val WHOLE_NUMBER = Regex("-?\\d+")
}
