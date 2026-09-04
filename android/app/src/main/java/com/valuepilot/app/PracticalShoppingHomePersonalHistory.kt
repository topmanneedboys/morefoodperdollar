package com.valuepilot.app

/**
 * Read-only Home context derived from the shopper's private Compare Here memory.
 *
 * Home items currently carry broad/sample intent rather than an exact package identity, so this
 * boundary intentionally reports only a normalized display-label count. It never exposes a rate,
 * treats a label as an exact product, or changes the fictional planner result. The copy keeps the
 * package/promotion mismatch and non-live nature visible so private memory cannot look like a
 * retailer offer.
 */
internal object PracticalShoppingHomePersonalHistory {

    fun noticeFor(
        itemDisplayName: String,
        memory: CompareHerePrivatePriceMemoryState
    ): String? {
        val canonicalName = canonicalLabel(itemDisplayName)
        if (canonicalName.isBlank()) return null

        val observationCount =
            memory.entries.count { entry ->
                canonicalLabel(entry.displayName) == canonicalName
            }
        if (observationCount == 0) return null

        val noun = if (observationCount == 1) "observation" else "observations"
        return "Private comparison history: $observationCount $noun for this name. " +
            "Package and promotion details may differ; not live store pricing."
    }

    internal fun canonicalLabel(value: String): String =
        value.trim().lowercase().split(Regex("\\s+")).joinToString(" ")
}
