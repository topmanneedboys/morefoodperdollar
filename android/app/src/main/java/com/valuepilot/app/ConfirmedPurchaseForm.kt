package com.valuepilot.app

import com.valuepilot.core.ConfirmedPurchase
import com.valuepilot.core.ConfirmedPurchaseStore
import com.valuepilot.core.Money
import com.valuepilot.core.NormalizedQuantity
import com.valuepilot.core.QuantityNormalization
import java.util.Locale

/** Raw text collected by the Android form; no text is treated as a fact until parsing succeeds. */
internal data class ConfirmedPurchaseFormInput(
    val quantityText: String,
    val amountText: String,
    val currencyText: String,
    val dateText: String,
    val storeText: String
)

internal enum class ConfirmedPurchaseFormField {
    QUANTITY,
    AMOUNT,
    CURRENCY,
    DATE,
    STORE
}

internal data class ConfirmedPurchaseFormFailure(
    val field: ConfirmedPurchaseFormField,
    val message: String
)

internal data class ConfirmedPurchaseFormValues(
    val quantity: NormalizedQuantity?,
    val amountPaid: Money?,
    val purchaseDateIso: String?,
    val store: ConfirmedPurchaseStore?
)

internal data class ConfirmedPurchaseFormParseResult(
    val values: ConfirmedPurchaseFormValues?,
    val failure: ConfirmedPurchaseFormFailure? = null
) {
    init {
        require((values != null) != (failure != null))
    }

    val accepted: Boolean
        get() = values != null
}

/** Exact, clock-free form parsing kept outside MainActivity's navigation owner. */
internal object ConfirmedPurchaseFormParser {
    fun parse(input: ConfirmedPurchaseFormInput): ConfirmedPurchaseFormParseResult {
        val quantityText = input.quantityText.trim()
        val quantity =
            if (quantityText.isBlank()) {
                null
            } else {
                val count = quantityText.toLongOrNull()
                if (count == null || count <= 0L) {
                    return failure(
                        ConfirmedPurchaseFormField.QUANTITY,
                        "Use a positive whole-number quantity or leave it blank"
                    )
                }
                runCatching { QuantityNormalization.count(count) }.getOrElse {
                    return failure(
                        ConfirmedPurchaseFormField.QUANTITY,
                        "That quantity is too large"
                    )
                }
            }

        val amountText = input.amountText.trim()
        val currencyText = input.currencyText.trim().uppercase(Locale.ROOT)
        if (amountText.isBlank() != currencyText.isBlank()) {
            return failure(
                ConfirmedPurchaseFormField.AMOUNT,
                "Enter both amount and currency, or leave both unknown"
            )
        }
        val amountPaid =
            if (amountText.isBlank()) {
                null
            } else {
                runCatching {
                    Money.parse(amountText, currencyText).also { require(it.minorUnits >= 0L) }
                }.getOrElse {
                    return failure(
                        ConfirmedPurchaseFormField.AMOUNT,
                        "Use an exact decimal amount and a three-letter currency code"
                    )
                }
            }

        val dateText = input.dateText.trim()
        if (dateText.isNotBlank() && !ConfirmedPurchase.isValidIsoDate(dateText)) {
            return failure(
                ConfirmedPurchaseFormField.DATE,
                "Use a real date in YYYY-MM-DD format"
            )
        }

        val storeText = input.storeText.trim()
        val store =
            if (storeText.isBlank()) {
                null
            } else {
                runCatching { ConfirmedPurchaseStore(displayName = storeText) }.getOrElse {
                    return failure(
                        ConfirmedPurchaseFormField.STORE,
                        "Use a shorter single-line store name"
                    )
                }
            }

        return ConfirmedPurchaseFormParseResult(
            values =
                ConfirmedPurchaseFormValues(
                    quantity = quantity,
                    amountPaid = amountPaid,
                    purchaseDateIso = dateText.takeIf(String::isNotBlank),
                    store = store
                )
        )
    }

    private fun failure(
        field: ConfirmedPurchaseFormField,
        message: String
    ): ConfirmedPurchaseFormParseResult =
        ConfirmedPurchaseFormParseResult(
            values = null,
            failure = ConfirmedPurchaseFormFailure(field, message)
        )
}
