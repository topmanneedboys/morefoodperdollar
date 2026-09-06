package com.valuepilot.app

/**
 * Conservative text gate for the user-triggered photo/OCR path.
 *
 * This is only a recall hint: a matching string is still an untrusted OCR suggestion and must
 * pass the existing review, editable-draft and exact comparison gates. Bare decimal prices are
 * included because many shelf labels omit a currency symbol; common package units are excluded so
 * values such as `1.75 L` do not become price suggestions solely because they contain a decimal.
 */
internal object CompareHerePhotoTextHints {
    private val explicitPriceHint =
        Regex(
            "(?:\\b(?:CA\\$|C\\$|US\\$|A\\$)|[$€£₹৳])\\s*(?:\\d{1,3}(?:[ ,.]\\d{3})+|\\d{1,6})(?:[.,]\\d{1,2})?|" +
                "\\b(?:\\d{1,3}(?:[ ,.]\\d{3})+|\\d{1,6})(?:[.,]\\d{1,2})?\\s*(?:CAD|USD|EUR|GBP|INR|BDT|AUD)\\b",
            RegexOption.IGNORE_CASE
        )

    /** A two-decimal number that is not immediately followed by a package unit. */
    private val bareDecimalPriceHint =
        Regex(
            "(?<![\\d.,])\\d{1,3}(?:[ ,]\\d{3})*[.,]\\d{2}" +
                "(?!\\s*(?:mg|g|kg|oz|lb|ml|l|fl\\s*oz|ct|count|pack|pk|unit(?:s)?|" +
                "ea|each|dozen|dz|litre(?:s)?|liter(?:s)?)\\b)",
            RegexOption.IGNORE_CASE
        )

    fun containsPriceLikeText(value: String): Boolean =
        value.isNotBlank() &&
            (explicitPriceHint.containsMatchIn(value) || bareDecimalPriceHint.containsMatchIn(value))

    /**
     * Returns one conservative bare-decimal signal for review copy. It is never an exact price
     * and deliberately returns no currency; the existing comparison route must still validate it.
     */
    fun bareDecimalPriceText(value: String): String? =
        bareDecimalPriceHint.find(value)?.value?.takeIf { it.length <= 24 }
}
