package com.valuepilot.app

/**
 * Bounded, presentation-only sanitization for a Home-to-Good-Price name handoff.
 *
 * The value is never treated as a product identity or evidence. An empty or oversized external
 * intent is ignored so a stale/deep-link caller cannot fill the input with an unbounded payload.
 */
internal object GoodPriceActivityPrefill {

    const val MAX_PRODUCT_NAME_LENGTH = 240

    fun sanitize(rawValue: String?): String? =
        rawValue
            ?.trim()
            ?.takeIf { value ->
                value.isNotBlank() && value.length <= MAX_PRODUCT_NAME_LENGTH
            }
}
