package com.valuepilot.app

/**
 * Pure, bounded edit used by Home's offline identity-suggestion action.
 *
 * Selecting a catalog name only replaces the first whole-token occurrence of the unresolved
 * Home word. It never creates a product identity, submits the list, reads catalog evidence,
 * changes a planner result, or claims that the selected name is an exact product. The shopper
 * must review the edited list and explicitly press Plan My Shop.
 */
object PracticalShoppingHomeOfflineCatalogSelection {

    private const val MAX_QUERY_CHARACTERS = 240
    private const val MAX_REPLACEMENT_LENGTH = 240

    /**
     * Returns a query with one matching unresolved token replaced, or null when the edit would
     * be ambiguous, malformed, or exceed Home's existing bounded query length.
     */
    fun replaceUnknownToken(
        rawQuery: String,
        unknownToken: String,
        replacementName: String
    ): String? {
        if (rawQuery.isBlank() || unknownToken.isBlank()) return null

        val replacement = replacementName.trim()
        if (!isSafeReplacement(replacement)) return null

        val token = unknownToken.trim()
        if (token.isEmpty() || token.any { it.isWhitespace() }) return null

        val tokenPattern =
            Regex(
                "(?i)(?<![\\p{L}\\p{N}])${Regex.escape(token)}(?![\\p{L}\\p{N}])"
            )
        val match = tokenPattern.find(rawQuery) ?: return null
        val edited =
            buildString(rawQuery.length - match.value.length + replacement.length) {
                append(rawQuery, 0, match.range.first)
                append(replacement)
                append(rawQuery, match.range.last + 1, rawQuery.length)
            }
        return edited.takeIf { it.isNotBlank() && it.length <= MAX_QUERY_CHARACTERS }
    }

    private fun isSafeReplacement(value: String): Boolean =
        value.isNotBlank() &&
            value.length <= MAX_REPLACEMENT_LENGTH &&
            value.none { character -> Character.isISOControl(character.code) }
}
