package com.valuepilot.app

/** Raw product-name text owned by a replaceable foreground adapter. */
internal data class UserObservedPriceConfirmationProductNameTextInput(
    val text: String
)

enum class UserObservedPriceConfirmationProductNameTextInputFailure {
    BLANK
}

sealed interface UserObservedPriceConfirmationProductNameUiAction {
    data class SetProductName(
        val productName: String
    ) : UserObservedPriceConfirmationProductNameUiAction
}

sealed interface UserObservedPriceConfirmationProductNameTextInputResult {
    data class Success(
        val action: UserObservedPriceConfirmationProductNameUiAction
    ) : UserObservedPriceConfirmationProductNameTextInputResult

    data class Failure(
        val reason: UserObservedPriceConfirmationProductNameTextInputFailure
    ) : UserObservedPriceConfirmationProductNameTextInputResult
}

/** Receives already-adapted product-name edits from a replaceable foreground input surface. */
internal fun interface UserObservedPriceConfirmationProductNameActionObserver {
    fun onProductNameAction(action: UserObservedPriceConfirmationProductNameUiAction)
}

/**
 * Pure raw-text to typed-action boundary for the optional Saved product-name completion step.
 *
 * Whitespace surrounding the user's text is removed because it is transport/UI syntax rather than
 * product identity. Blank input is not emitted as an answered field. All remaining semantic rules
 * stay downstream in [UserConfirmedObservedPrice], including minimum/maximum length and single-line
 * validation. In particular this adapter does not decide whether a name is true, equivalent to a
 * GTIN, proof-backed, retailer-confirmed, or suitable for current-price evidence.
 *
 * This object owns no Android classes, route/session state, GTIN/store identity, price/currency,
 * proof, identifiers, clocks, persistence, evidence, ranking, provider access, or network work.
 */
internal object UserObservedPriceConfirmationProductNameTextInputAdapter {

    fun adapt(
        input: UserObservedPriceConfirmationProductNameTextInput
    ): UserObservedPriceConfirmationProductNameTextInputResult {
        val productName = input.text.trim()
        if (productName.isEmpty()) {
            return UserObservedPriceConfirmationProductNameTextInputResult.Failure(
                UserObservedPriceConfirmationProductNameTextInputFailure.BLANK
            )
        }

        return UserObservedPriceConfirmationProductNameTextInputResult.Success(
            UserObservedPriceConfirmationProductNameUiAction.SetProductName(productName)
        )
    }
}