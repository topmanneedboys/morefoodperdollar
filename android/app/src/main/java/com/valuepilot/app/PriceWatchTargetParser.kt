package com.valuepilot.app

import com.valuepilot.core.Money

internal data class PriceWatchTargetParseResult(
    val target: Money?,
    val accepted: Boolean,
    val message: String? = null
)

/** Exact target-price parsing kept outside MainActivity's shell owner. */
internal object PriceWatchTargetParser {
    fun parse(amountText: String, currencyText: String): PriceWatchTargetParseResult {
        val amount = amountText.trim()
        val currency = currencyText.trim()
        if (amount.isBlank() && currency.isBlank()) {
            return PriceWatchTargetParseResult(target = null, accepted = true)
        }
        if (amount.isBlank() || currency.isBlank()) {
            return PriceWatchTargetParseResult(
                target = null,
                accepted = false,
                message = "Enter both amount and currency, or clear both"
            )
        }
        val target =
            runCatching {
                Money.parse(amount, currency).also { require(it.minorUnits >= 0L) }
            }.getOrNull()
                ?: return PriceWatchTargetParseResult(
                    target = null,
                    accepted = false,
                    message = "Use an exact non-negative amount and currency"
                )
        return PriceWatchTargetParseResult(target = target, accepted = true)
    }
}
