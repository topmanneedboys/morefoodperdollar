package com.valuepilot.app

/**
 * Small, deterministic shortcuts for starting the offline fictional Home example.
 *
 * These are text-entry conveniences only. They do not identify a product, create
 * evidence, choose a store or change planner policy. The visible Home disclosure
 * remains the source of truth that the resulting plan is fictional sample data.
 */
enum class PracticalShoppingHomeQuickAdd(
    val label: String,
    internal val queryToken: String
) {
    EGGS("Eggs", "eggs"),
    MILK("Milk", "milk"),
    BANANAS("Bananas", "bananas"),
    BREAD("Bread", "bread"),
    CHICKEN("Chicken", "chicken")
}

internal object PracticalShoppingHomeQuickAddPolicy {

    val choices: List<PracticalShoppingHomeQuickAdd> =
        PracticalShoppingHomeQuickAdd.entries.toList()

    /**
     * Adds one shortcut token to a bounded Home draft. Existing exact tokens are
     * left alone so repeated taps cannot silently inflate a list.
     */
    fun appendToQuery(
        rawQuery: String,
        choice: PracticalShoppingHomeQuickAdd
    ): String? {
        val trimmed = rawQuery.trim()
        val tokens = trimmed
            .split(Regex("\\s+"))
            .filter(String::isNotBlank)
            .map(String::lowercase)
        if (choice.queryToken in tokens) return trimmed

        val next =
            if (trimmed.isBlank()) {
                choice.queryToken
            } else {
                "$trimmed ${choice.queryToken}"
            }
        return next.takeIf { it.length <= LocalSamplePracticalShoppingDemo.MAX_QUERY_CHARACTERS }
    }

    /**
     * Adds a real Search identity to the private Home draft without treating it as a
     * recognised sample product. Quoting keeps a multi-word identity together as one
     * unresolved item, so the shopper can review or resolve it instead of seeing one
     * unknown chip for every word.
     */
    fun appendIdentityNameToQuery(rawQuery: String, identityName: String): String? {
        val trimmedQuery = rawQuery.trim()
        val trimmedName = identityName.trim()
        if (trimmedName.isBlank()) return null
        if (LocalSamplePracticalShoppingDemo.queryContainsItemName(trimmedQuery, trimmedName)) {
            return trimmedQuery
        }

        val encodedName =
            trimmedName
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
        val next =
            if (trimmedQuery.isBlank()) {
                "\"$encodedName\""
            } else {
                "$trimmedQuery \"$encodedName\""
            }
        return next.takeIf { it.length <= LocalSamplePracticalShoppingDemo.MAX_QUERY_CHARACTERS }
    }
}
