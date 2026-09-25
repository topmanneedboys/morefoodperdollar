package com.valuepilot.core

/**
 * Canonical exact fingerprints for factual values that may be compared across
 * independent providers. Adapters should use these instead of ad-hoc strings
 * so conflict handling never depends on formatting differences.
 */
object EvidenceFingerprints {
    fun money(value: Money): String =
        "money:${value.currencyCode}:${value.fractionDigits}:${value.minorUnits}"

    fun quantity(value: NormalizedQuantity): String =
        "quantity:${value.unit.name}:${value.amountMicros}"
}
