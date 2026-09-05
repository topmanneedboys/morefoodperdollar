package com.valuepilot.app

import com.valuepilot.core.CompareHerePriceSelection
import java.nio.charset.StandardCharsets

private const val MAX_PRIVATE_HISTORY_EXPORT_BYTES = 262_144
private const val MAX_PRIVATE_HISTORY_EXPORT_PREVIEW_ROWS = 5

internal enum class PracticalShoppingPrivatePriceHistoryExportIssue {
    EMPTY,
    OUTPUT_TOO_LARGE
}

/**
 * A user-requested, human-readable copy of device-only comparison history.
 *
 * The export is intentionally separate from the private-memory codec. The codec is a bounded
 * recovery format; this copy is for a person to inspect or move to a destination they choose.
 * It contains no store or availability claim because private comparison memory does not establish
 * one. The caller must show the preview and an explicit privacy warning before sharing [text].
 */
internal data class PracticalShoppingPrivatePriceHistoryExportResult(
    val text: String? = null,
    val preview: String? = null,
    val observationCount: Int,
    val issue: PracticalShoppingPrivatePriceHistoryExportIssue? = null
) {
    init {
        require((text != null) == (issue == null))
        require((preview != null) == (issue == null))
        require(observationCount >= 0)
        require(
            when (issue) {
                null -> observationCount > 0
                PracticalShoppingPrivatePriceHistoryExportIssue.EMPTY -> observationCount == 0
                PracticalShoppingPrivatePriceHistoryExportIssue.OUTPUT_TOO_LARGE ->
                    observationCount > 0
            }
        )
    }

    val accepted: Boolean
        get() = text != null
}

/**
 * Deterministic and bounded export of exact personal observations.
 *
 * This is not a current-offer artifact and is never consumed by the planner. Entries are ordered
 * newest-first with the stable observation fingerprint as a tie-breaker, so the same state always
 * produces the same copy regardless of insertion order or locale.
 */
internal object PracticalShoppingPrivatePriceHistoryExport {

    const val maximumExportBytes: Int = MAX_PRIVATE_HISTORY_EXPORT_BYTES
    const val previewRowLimit: Int = MAX_PRIVATE_HISTORY_EXPORT_PREVIEW_ROWS

    fun from(
        state: CompareHerePrivatePriceMemoryState
    ): PracticalShoppingPrivatePriceHistoryExportResult {
        if (state.entries.isEmpty()) {
            return PracticalShoppingPrivatePriceHistoryExportResult(
                observationCount = 0,
                issue = PracticalShoppingPrivatePriceHistoryExportIssue.EMPTY
            )
        }

        val ordered =
            state.entries.sortedWith(
                compareByDescending<CompareHerePrivatePriceMemoryEntry> { it.observedAtEpochMillis }
                    .thenBy { it.observationId }
            )
        val text = render(ordered, totalCount = ordered.size)
        if (text.toByteArray(StandardCharsets.UTF_8).size > MAX_PRIVATE_HISTORY_EXPORT_BYTES) {
            return PracticalShoppingPrivatePriceHistoryExportResult(
                observationCount = ordered.size,
                issue = PracticalShoppingPrivatePriceHistoryExportIssue.OUTPUT_TOO_LARGE
            )
        }

        val previewEntries = ordered.take(MAX_PRIVATE_HISTORY_EXPORT_PREVIEW_ROWS)
        return PracticalShoppingPrivatePriceHistoryExportResult(
            text = text,
            preview = render(previewEntries, totalCount = ordered.size),
            observationCount = ordered.size
        )
    }

    private fun render(
        entries: List<CompareHerePrivatePriceMemoryEntry>,
        totalCount: Int
    ): String = buildString {
        append("ValuePilot private price history\n")
        append(
            "These are exact comparison observations exported from this device. " +
                "They are personal historical context, not live store prices, retailer offers, " +
                "inventory, availability, and not a guarantee. Store information is not included " +
                "because these observations do not establish a store."
        )
        append("\n\nObservation count: ")
        append(totalCount)
        if (entries.size < totalCount) {
            append(" (preview shows ")
            append(entries.size)
            append("; the shared copy contains all observations)")
        }
        append("\n\n")

        entries.forEachIndexed { index, entry ->
            append("Observation ")
            append(index + 1)
            append("\nProduct: ")
            append(entry.displayName)
            append("\nPrice: ")
            append(formatCompareHereMoney(entry.price))
            append("\nPackage: ")
            append(formatCompareHereQuantity(entry.quantity))
            append("\nUnit rate: ")
            append(formatCompareHereRate(entry.rate))
            append("\nPrice basis: ")
            append(priceBasisLabel(entry.priceSelection))
            entry.promotionLabel?.let { promotion ->
                append("\nPromotion: ")
                append(promotion)
                append(" (received ")
                append(entry.promotionReceivedUnits)
                append(", paid ")
                append(entry.promotionPaidUnits)
                append(")")
            }
            append("\nObserved: ")
            append(formatPrivateObservationDate(entry.observedAtEpochMillis))
            append("\nSource: ")
            append(sourceLabel(entry.source))
            if (index != entries.lastIndex) append("\n\n")
        }
    }

    private fun priceBasisLabel(selection: CompareHerePriceSelection): String =
        when (selection) {
            CompareHerePriceSelection.CURRENT -> "Current price"
            CompareHerePriceSelection.MEMBER -> "Member price"
        }

    private fun sourceLabel(source: CompareHerePrivatePriceMemorySource): String =
        when (source) {
            CompareHerePrivatePriceMemorySource.CONFIRMED_COMPARE_HERE -> "Scan & compare"
            CompareHerePrivatePriceMemorySource.CONFIRMED_GOOD_PRICE_CHECK ->
                "Is this a good price?"
        }
}
