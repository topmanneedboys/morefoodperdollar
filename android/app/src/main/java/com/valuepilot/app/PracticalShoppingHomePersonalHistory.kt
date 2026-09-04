package com.valuepilot.app

import com.valuepilot.core.NormalizedQuantity

/**
 * Read-only Home context derived from the shopper's private Compare Here memory.
 *
 * Home items currently carry broad/sample intent rather than an exact package identity, so this
 * boundary reports a normalized display-label count by default. When the Home fixture also carries
 * a normalized package quantity, it may expose a read-only last price/unit-rate context only for a
 * single comparable name/package/basis/promotion shape. It never treats a label as an exact product
 * or changes the fictional planner result. The copy keeps identity, package, promotion and
 * non-live limitations visible so private memory cannot look like a retailer offer.
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
        memory: CompareHerePrivatePriceMemoryState,
        itemQuantity: NormalizedQuantity? = null
    ): String? {
        val canonicalName = canonicalLabel(itemDisplayName)
        if (canonicalName.isBlank()) return null

        val matchingEntries =
            memory.entries.filter { entry ->
                canonicalLabel(entry.displayName) == canonicalName
            }
        if (matchingEntries.isEmpty()) return null

        val exactPackageEntries =
            itemQuantity?.let { quantity ->
                matchingEntries.filter { entry -> entry.quantity == quantity }
            }.orEmpty()
        comparableExactPackageNotice(exactPackageEntries)?.let { notice ->
            return notice
        }

        return genericNotice(matchingEntries.size)
    }

    /**
     * Shows useful price context only when every displayed rate belongs to one
     * comparable name/package/basis/promotion shape. A mixed set falls back to
     * the conservative name-only notice instead of implying a false range.
     */
    private fun comparableExactPackageNotice(
        entries: List<CompareHerePrivatePriceMemoryEntry>
    ): String? {
        if (entries.isEmpty()) return null

        val latest = latestEntry(entries)
        val comparable =
            entries.filter { entry ->
                entry.price.currencyCode == latest.price.currencyCode &&
                    entry.price.fractionDigits == latest.price.fractionDigits &&
                    entry.rate.currencyCode == latest.rate.currencyCode &&
                    entry.rate.unit == latest.rate.unit &&
                    entry.priceSelection == latest.priceSelection &&
                    entry.promotionLabel == latest.promotionLabel &&
                    entry.promotionReceivedUnits == latest.promotionReceivedUnits &&
                    entry.promotionPaidUnits == latest.promotionPaidUnits
            }
        if (comparable.size != entries.size) return null

        val lowest =
            comparable.minWith(
                compareBy<CompareHerePrivatePriceMemoryEntry> { it.rate.currencyMicrosPerUnit }
                    .thenByDescending { it.observedAtEpochMillis }
                    .thenBy { it.observationId }
            )
        val highest =
            comparable.maxWith(
                compareBy<CompareHerePrivatePriceMemoryEntry> { it.rate.currencyMicrosPerUnit }
                    .thenByDescending { it.observedAtEpochMillis }
                    .thenBy { it.observationId }
            )
        val noun = if (comparable.size == 1) "observation" else "observations"
        return "Personal history: ${comparable.size} $noun for this name and package. " +
            "Last recorded ${formatCompareHereMoney(latest.price)} " +
            "(${formatCompareHereRate(latest.rate)}). " +
            "Remembered range ${formatCompareHereRate(lowest.rate)}–" +
            "${formatCompareHereRate(highest.rate)}. " +
            "Product identity, brand, promotion and store may differ; not live store pricing."
    }

    private fun genericNotice(observationCount: Int): String {
        val noun = if (observationCount == 1) "observation" else "observations"
        return "Private comparison history: $observationCount $noun for this name. " +
            "Package and promotion details may differ; not live store pricing."
    }

    private fun latestEntry(
        entries: List<CompareHerePrivatePriceMemoryEntry>
    ): CompareHerePrivatePriceMemoryEntry =
        entries.maxWith(
            compareBy<CompareHerePrivatePriceMemoryEntry> { it.observedAtEpochMillis }
                .thenBy { it.observationId }
        )

    internal fun canonicalLabel(value: String): String =
        value.trim().lowercase().split(Regex("\\s+")).joinToString(" ")
}
