package com.valuepilot.app

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private const val MAX_PRIVATE_HISTORY_ROWS = 32

/**
 * One exact package history projected for a consumer-facing read-only summary.
 *
 * Rows are grouped only when the existing private-memory equivalence rule agrees on name,
 * package quantity, currency, price basis and promotion terms. This presentation has no store,
 * availability, current-offer or ranking authority because the underlying private memory has none.
 */
internal data class PracticalShoppingPrivatePriceHistoryRow(
    val displayName: String,
    val observationCount: Int,
    val latestPriceText: String,
    val latestUnitRateText: String,
    val rangeText: String,
    val packageText: String,
    val priceBasisText: String,
    val latestObservedText: String,
    val sourceText: String,
    val promotionText: String? = null
) {
    init {
        require(displayName.isNotBlank())
        require(observationCount > 0)
        listOf(
            latestPriceText,
            latestUnitRateText,
            rangeText,
            packageText,
            priceBasisText,
            latestObservedText,
            sourceText
        ).forEach { require(it.isNotBlank()) }
        require(promotionText == null || promotionText.isNotBlank())
    }

    fun asText(): String = buildString {
        append(displayName)
        append("\nLatest: ")
        append(latestPriceText)
        append(" · ")
        append(latestUnitRateText)
        append("\nRange: ")
        append(rangeText)
        append("\nPackage: ")
        append(packageText)
        append(" · ")
        append(priceBasisText)
        append("\n")
        append(observationCount)
        append(if (observationCount == 1) " observation" else " observations")
        append(" · Last observed ")
        append(latestObservedText)
        append(" · Source: ")
        append(sourceText)
        promotionText?.let {
            append(" · ")
            append(it)
        }
    }
}

/**
 * Immutable summary for Home's private-history review action.
 *
 * The message deliberately says what the data is not. A private observation is useful context,
 * but it is not a live shelf price, stock signal, retailer offer or guarantee. The row cap keeps
 * the dialog bounded even when the underlying local memory reaches its safety ceiling.
 */
internal data class PracticalShoppingPrivatePriceHistoryPresentation(
    val title: String,
    val intro: String,
    val rows: List<PracticalShoppingPrivatePriceHistoryRow>,
    val omittedRowCount: Int
) {
    init {
        require(title.isNotBlank())
        require(intro.isNotBlank())
        require(rows.size <= MAX_PRIVATE_HISTORY_ROWS)
        require(omittedRowCount >= 0)
    }

    val message: String
        get() = buildString {
            append(intro)
            if (rows.isEmpty()) {
                append("\n\nNo private comparison observations are available yet.")
                return@buildString
            }

            append("\n\n")
            append(
                if (omittedRowCount == 0) {
                    "Exact package histories"
                } else {
                    "Showing ${rows.size} exact package histories; " +
                        "$omittedRowCount more are stored on this device"
                }
            )
            append("\n\n")
            append(rows.joinToString("\n\n") { row -> row.asText() })
        }

    companion object {
        fun from(
            state: CompareHerePrivatePriceMemoryState
        ): PracticalShoppingPrivatePriceHistoryPresentation {
            val groups = groupExactHistories(state)
            val orderedGroups =
                groups.sortedWith(
                    compareByDescending<List<CompareHerePrivatePriceMemoryEntry>> {
                        latestEntry(it).observedAtEpochMillis
                    }.thenBy { latestEntry(it).displayName.lowercase(Locale.ROOT) }
                        .thenBy { latestEntry(it).observationId }
                )
            val visibleGroups = orderedGroups.take(MAX_PRIVATE_HISTORY_ROWS)

            return PracticalShoppingPrivatePriceHistoryPresentation(
                title = "Private price history",
                intro =
                    "These are your exact comparison observations stored on this device. " +
                        "They are personal context, not live store prices, inventory, retailer " +
                        "offers and not a guarantee. Matching requires the same name, package, " +
                        "currency, price basis and promotion terms.",
                rows = visibleGroups.map(::toRow),
                omittedRowCount = (orderedGroups.size - visibleGroups.size).coerceAtLeast(0)
            )
        }

        private fun groupExactHistories(
            state: CompareHerePrivatePriceMemoryState
        ): List<List<CompareHerePrivatePriceMemoryEntry>> {
            val remaining = state.entries.toMutableList()
            val groups = mutableListOf<List<CompareHerePrivatePriceMemoryEntry>>()

            while (remaining.isNotEmpty()) {
                val seed = remaining.first()
                val group =
                    remaining.filter { candidate ->
                        CompareHerePriceMemoryEvaluator.entriesMatch(seed, candidate)
                    }
                groups += group
                remaining.removeAll(group.toSet())
            }

            return groups
        }

        private fun toRow(
            entries: List<CompareHerePrivatePriceMemoryEntry>
        ): PracticalShoppingPrivatePriceHistoryRow {
            val latest = latestEntry(entries)
            val lowest =
                entries.minWith(
                    compareBy<CompareHerePrivatePriceMemoryEntry> {
                        it.rate.currencyMicrosPerUnit
                    }.thenByDescending { it.observedAtEpochMillis }
                        .thenBy { it.observationId }
                )
            val highest =
                entries.maxWith(
                    compareBy<CompareHerePrivatePriceMemoryEntry> {
                        it.rate.currencyMicrosPerUnit
                    }.thenByDescending { it.observedAtEpochMillis }
                        .thenBy { it.observationId }
                )

            val sources =
                entries
                    .map { sourceLabel(it.source) }
                    .distinct()
                    .sorted()
                    .joinToString(" + ")

            return PracticalShoppingPrivatePriceHistoryRow(
                displayName = latest.displayName,
                observationCount = entries.size,
                latestPriceText = formatCompareHereMoney(latest.price),
                latestUnitRateText = formatCompareHereRate(latest.rate),
                rangeText =
                    "${formatCompareHereRate(lowest.rate)}–${formatCompareHereRate(highest.rate)}",
                packageText = formatCompareHereQuantity(latest.quantity),
                priceBasisText = priceBasisLabel(latest.priceSelection),
                latestObservedText = observedAtLabel(latest.observedAtEpochMillis),
                sourceText = sources,
                promotionText = latest.promotionLabel?.let { "Promotion: $it" }
            )
        }

        private fun latestEntry(
            entries: List<CompareHerePrivatePriceMemoryEntry>
        ): CompareHerePrivatePriceMemoryEntry =
            entries.maxWith(
                compareBy<CompareHerePrivatePriceMemoryEntry> { it.observedAtEpochMillis }
                    .thenBy { it.observationId }
            )

        private fun sourceLabel(source: CompareHerePrivatePriceMemorySource): String =
            when (source) {
                CompareHerePrivatePriceMemorySource.CONFIRMED_COMPARE_HERE ->
                    "Scan & compare"

                CompareHerePrivatePriceMemorySource.CONFIRMED_GOOD_PRICE_CHECK ->
                    "Is this a good price?"
            }

        private fun priceBasisLabel(selection: com.valuepilot.core.CompareHerePriceSelection): String =
            when (selection) {
                com.valuepilot.core.CompareHerePriceSelection.CURRENT -> "Current price"
                com.valuepilot.core.CompareHerePriceSelection.MEMBER -> "Member price"
            }

        private fun observedAtLabel(epochMillis: Long): String {
            if (epochMillis <= 0L) return "date not recorded"
            return SimpleDateFormat("yyyy-MM-dd HH:mm 'UTC'", Locale.ROOT).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.format(Date(epochMillis))
        }
    }
}
