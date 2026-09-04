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

    /**
     * Gives Home a compact, non-price summary when private comparison memory exists.
     *
     * The count is deliberately the only aggregate exposed here. It makes the automatic,
     * device-only memory discoverable even when no current Home row has a matching display label,
     * without implying that a broad list name is an exact product or a live offer.
     */
    fun summaryFor(
        memory: CompareHerePrivatePriceMemoryState,
        requestedItemNames: List<String> = emptyList()
    ): String? {
        val observationCount = memory.entries.size
        if (observationCount == 0) return null

        val noun = if (observationCount == 1) "observation" else "observations"
        val summary = "Private comparison history: $observationCount $noun on this device. " +
            "Home shows matching context only; package and promotion details may differ. " +
            "This is not live store pricing. Open Scan & compare prices to review it."

        val distinctNames =
            requestedItemNames
                .map(::canonicalLabel)
                .filter(String::isNotBlank)
                .distinct()
        if (distinctNames.isEmpty()) return summary

        val matchedItemCount =
            distinctNames.count { name ->
                memory.entries.any { entry -> canonicalLabel(entry.displayName) == name }
            }
        return summary +
            "\nName-matched personal history: $matchedItemCount of " +
            "${distinctNames.size} list items. This is not current-price coverage."
    }

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
