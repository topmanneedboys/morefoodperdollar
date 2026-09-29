package com.valuepilot.app

import com.valuepilot.core.ConfirmedPurchase
import com.valuepilot.core.ConfirmedPurchaseStore
import com.valuepilot.core.BaseUnit
import com.valuepilot.core.Money
import com.valuepilot.core.NormalizedQuantity
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

/** Raw text collected by the Android form; no text is treated as a fact until parsing succeeds. */
internal data class ConfirmedPurchaseFormInput(
    val quantityText: String,
    val quantityUnitText: String,
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
        val quantityUnitText = input.quantityUnitText.trim().lowercase(Locale.ROOT)
        val quantity =
            if (quantityText.isBlank() && quantityUnitText.isBlank()) {
                null
            } else {
                if (quantityText.isBlank() || quantityUnitText.isBlank()) {
                    return failure(
                        ConfirmedPurchaseFormField.QUANTITY,
                        "Enter both quantity and unit, or leave both unknown"
                    )
                }
                val number =
                    quantityText.replace(',', '.').takeIf {
                        it.matches(Regex("\\d+(?:\\.\\d+)?"))
                    }?.let { raw ->
                        runCatching { BigDecimal(raw) }.getOrNull()
                    }
                val unitAndFactor =
                    when (quantityUnitText) {
                        "count", "counts", "item", "items", "ct", "ea" ->
                            BaseUnit.COUNT to BigDecimal.ONE
                        "g", "gram", "grams" -> BaseUnit.GRAM to BigDecimal.ONE
                        "kg", "kilogram", "kilograms" -> BaseUnit.GRAM to BigDecimal("1000")
                        "ml", "millilitre", "millilitres", "milliliter", "milliliters" ->
                            BaseUnit.MILLILITRE to BigDecimal.ONE
                        "l", "litre", "litres", "liter", "liters" ->
                            BaseUnit.MILLILITRE to BigDecimal("1000")
                        else -> null
                    }
                if (number == null || number <= BigDecimal.ZERO || unitAndFactor == null) {
                    return failure(
                        ConfirmedPurchaseFormField.QUANTITY,
                        "Use a positive exact quantity and unit (count, g/kg, or ml/l)"
                    )
                }
                runCatching {
                    val micros =
                        number
                            .multiply(unitAndFactor.second)
                            .movePointRight(6)
                            .setScale(0, RoundingMode.UNNECESSARY)
                            .longValueExact()
                    NormalizedQuantity(micros, unitAndFactor.first)
                }.getOrElse {
                    return failure(
                        ConfirmedPurchaseFormField.QUANTITY,
                        "That quantity is too large or not exact enough"
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

    fun displayQuantityText(quantity: NormalizedQuantity): String =
        BigDecimal.valueOf(quantity.amountMicros)
            .movePointLeft(6)
            .stripTrailingZeros()
            .toPlainString()

    fun displayQuantityUnitText(quantity: NormalizedQuantity): String =
        when (quantity.unit) {
            BaseUnit.COUNT -> "count"
            BaseUnit.GRAM -> "g"
            BaseUnit.MILLILITRE -> "ml"
            BaseUnit.SQUARE_INCH -> ""
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
