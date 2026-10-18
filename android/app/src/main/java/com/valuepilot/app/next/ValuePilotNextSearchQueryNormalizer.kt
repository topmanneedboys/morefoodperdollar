package com.valuepilot.app.next

import java.util.Locale

/**
 * Converts a small, reviewed set of common English grocery words to the
 * Spanish terms used by the qualified Argentina search release.
 *
 * This is query interpretation only. It does not create identities, prices,
 * stores, stock, or availability. Unknown input is passed through unchanged
 * so the backend remains the authority for the result.
 */
internal object ValuePilotNextSearchQueryNormalizer {
    private val exactAliases = mapOf(
        "rice" to "arroz",
        "milk" to "leche",
        "oil" to "aceite",
        "strawberry" to "frutilla",
        "strawberries" to "frutilla",
        "lychee" to "lichi",
        "litchi" to "lichi",
        "bread" to "pan",
        "flour" to "harina",
        "sugar" to "azucar",
        "coffee" to "cafe",
        "tea" to "te",
        "water" to "agua",
        "soda" to "gaseosa",
        "soft drink" to "gaseosa",
        "yogurt" to "yogur",
        "cheese" to "queso",
        "butter" to "manteca",
        "tuna" to "atun",
        "chicken" to "pollo",
        "cookies" to "galletitas",
        "cookie" to "galletita",
        "detergent" to "detergente",
        "shampoo" to "shampoo",
        "soap" to "jabon",
        "diaper" to "panal",
        "diapers" to "panales"
    )

    fun canonical(rawQuery: String): String {
        val trimmed = rawQuery.trim()
        return exactAliases[trimmed.lowercase(Locale.ROOT)] ?: trimmed
    }
}
