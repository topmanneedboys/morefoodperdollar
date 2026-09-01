package com.valuepilot.app

import com.valuepilot.core.Money
import java.util.Currency

/** Raw amount and currency text supplied explicitly by the user for one observed-price draft. */
internal data class UserObservedPriceConfirmationDraftPriceTextInput(
    val amountText: String,
    val currencyCodeText: String
)

internal enum class UserObservedPriceConfirmationDraftPriceTextInputFailure {
    BLANK_AMOUNT,
    BLANK_CURRENCY,
    INVALID_CURRENCY_CODE,
    UNSUPPORTED_CURRENCY_PRECISION,
    INVALID_AMOUNT_FORMAT,
    OUT_OF_RANGE
}

internal sealed interface UserObservedPriceConfirmationDraftPriceTextInputResult {
    data class Success(
        val price: Money
    ) : UserObservedPriceConfirmationDraftPriceTextInputResult

    data class Failure(
        val reason: UserObservedPriceConfirmationDraftPriceTextInputFailure
    ) : UserObservedPriceConfirmationDraftPriceTextInputResult
}

/**
 * Pure explicit text-to-[Money] boundary for the route-local observed-price confirmation draft.
 *
 * Both the decimal amount and ISO currency code must be supplied by the user. Currency is never
 * inferred from store identity, device locale, geography, saved-item metadata, or a previous edit.
 * A strict uppercase ISO code is resolved through the runtime ISO currency registry only to obtain
 * that currency's standard fraction digits. The registry does not choose a currency for the user.
 * Currencies whose registry precision is unavailable or outside the shared-core Money range fail
 * closed.
 *
 * Accepted amount syntax is deliberately locale-neutral: optional leading minus, ASCII digits and
 * an optional `.` fraction with no grouping separators, currency symbols, or plus sign. Exact
 * conversion is delegated to [Money.parse]; no Double/Float conversion occurs. Positive-price
 * semantics deliberately remain downstream in [UserConfirmedObservedPrice], so this adapter owns
 * syntax, currency metadata resolution, and numeric overflow only.
 *
 * This adapter owns no Android classes, draft/session mutation, product/store inference, proof,
 * identifiers, timestamps, persistence, evidence, current-price authority, ranking, or networking.
 */
internal object UserObservedPriceConfirmationDraftPriceTextInputAdapter {

    fun adapt(
        input: UserObservedPriceConfirmationDraftPriceTextInput
    ): UserObservedPriceConfirmationDraftPriceTextInputResult {
        val amount = input.amountText.trim()
        if (amount.isEmpty()) {
            return UserObservedPriceConfirmationDraftPriceTextInputResult.Failure(
                UserObservedPriceConfirmationDraftPriceTextInputFailure.BLANK_AMOUNT
            )
        }

        val currencyCode = input.currencyCodeText.trim()
        if (currencyCode.isEmpty()) {
            return UserObservedPriceConfirmationDraftPriceTextInputResult.Failure(
                UserObservedPriceConfirmationDraftPriceTextInputFailure.BLANK_CURRENCY
            )
        }
        if (!ISO_CURRENCY.matches(currencyCode)) {
            return UserObservedPriceConfirmationDraftPriceTextInputResult.Failure(
                UserObservedPriceConfirmationDraftPriceTextInputFailure.INVALID_CURRENCY_CODE
            )
        }

        val fractionDigits =
            try {
                Currency.getInstance(currencyCode).defaultFractionDigits
            } catch (_: IllegalArgumentException) {
                return UserObservedPriceConfirmationDraftPriceTextInputResult.Failure(
                    UserObservedPriceConfirmationDraftPriceTextInputFailure.INVALID_CURRENCY_CODE
                )
            }
        if (fractionDigits !in 0..6) {
            return UserObservedPriceConfirmationDraftPriceTextInputResult.Failure(
                UserObservedPriceConfirmationDraftPriceTextInputFailure.UNSUPPORTED_CURRENCY_PRECISION
            )
        }

        val canonicalDecimal =
            if (fractionDigits == 0) {
                Regex("-?\\d+")
            } else {
                Regex("-?\\d+(?:\\.\\d{1,$fractionDigits})?")
            }
        if (!canonicalDecimal.matches(amount)) {
            return UserObservedPriceConfirmationDraftPriceTextInputResult.Failure(
                UserObservedPriceConfirmationDraftPriceTextInputFailure.INVALID_AMOUNT_FORMAT
            )
        }

        val money =
            try {
                Money.parse(
                    decimal = amount,
                    currencyCode = currencyCode,
                    fractionDigits = fractionDigits
                )
            } catch (_: IllegalArgumentException) {
                return UserObservedPriceConfirmationDraftPriceTextInputResult.Failure(
                    UserObservedPriceConfirmationDraftPriceTextInputFailure.OUT_OF_RANGE
                )
            } catch (_: ArithmeticException) {
                return UserObservedPriceConfirmationDraftPriceTextInputResult.Failure(
                    UserObservedPriceConfirmationDraftPriceTextInputFailure.OUT_OF_RANGE
                )
            }

        return UserObservedPriceConfirmationDraftPriceTextInputResult.Success(money)
    }

    private val ISO_CURRENCY = Regex("[A-Z]{3}")
}
