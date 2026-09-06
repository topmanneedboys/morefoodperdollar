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
}
