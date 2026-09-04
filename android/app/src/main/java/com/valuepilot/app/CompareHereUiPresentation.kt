package com.valuepilot.app

import com.valuepilot.core.BaseUnit
import com.valuepilot.core.CompareHereBlockedCandidate
import com.valuepilot.core.CompareHereCandidateBlocker
import com.valuepilot.core.CompareHereComparisonIssue
import com.valuepilot.core.CompareHereComparisonResult
import com.valuepilot.core.CompareHereComparisonStatus
import com.valuepilot.core.CompareHereExactCandidate
import com.valuepilot.core.CompareHerePriceSelection
import com.valuepilot.core.GtinValidation
import com.valuepilot.core.Money
import com.valuepilot.core.NormalizedQuantity
import com.valuepilot.core.RateUnit
import com.valuepilot.core.UnitRate
import java.math.BigDecimal

private const val MAX_COMPARE_HERE_DISPLAY_METADATA = 32
private const val MAX_COMPARE_HERE_RAW_LABEL_LENGTH = 500
private const val MAX_COMPARE_HERE_CONSUMER_LABEL_LENGTH = 160

/** Detached, non-authoritative consumer label for one opaque Compare Here candidate. */
data class CompareHereDisplayMetadataEntry(
    val candidateId: String,
    val displayName: String
) {
    init {
        require(candidateId.isNotBlank() && candidateId.length <= 240)
        require(displayName.length <= MAX_COMPARE_HERE_RAW_LABEL_LENGTH)
    }
}

data class CompareHereDisplayMetadata(
    val entries: List<CompareHereDisplayMetadataEntry> = emptyList()
) {
    init {
        require(entries.size <= MAX_COMPARE_HERE_DISPLAY_METADATA)
        require(entries.map { it.candidateId }.distinct().size == entries.size) {
            "Compare Here display metadata candidate ids must be unique"
        }
    }
}

enum class CompareHereUiStatus {
    READY,
    NOT_ENOUGH_DATA,
    INCOMPATIBLE_DIMENSIONS,
    DISPLAY_METADATA_INCOMPLETE
}

data class CompareHereUiRow(
    val title: String,
    val priceText: String,
    val quantityText: String,
    val unitRateText: String,
    val valueRank: Int?,
    val bestValue: Boolean
) {
    init {
        requireConsumerCompareHereLabel(title)
        require(priceText.isNotBlank())
        require(quantityText.isNotBlank())
        require(unitRateText.isNotBlank())
        require(bestValue == (valueRank == 1))
    }
}

data class CompareHereBlockedUiRow(
    val title: String,
    val reasonText: String
) {
    init {
        requireConsumerCompareHereLabel(title)
        require(reasonText.isNotBlank())
    }
}

data class CompareHereUiState(
    val headline: String,
    val priceModeText: String,
    val status: CompareHereUiStatus,
    val statusTitle: String,
    val guidance: String,
    val rows: List<CompareHereUiRow>,
    val blockedRows: List<CompareHereBlockedUiRow>,
    val omittedDisplayNameCount: Int,
    val notice: String?
) {
    init {
        require(headline.isNotBlank())
        require(priceModeText.isNotBlank())
        require(statusTitle.isNotBlank())
        require(guidance.isNotBlank())
        require(rows.size + blockedRows.size <= MAX_COMPARE_HERE_DISPLAY_METADATA)
        require(omittedDisplayNameCount >= 0)
        require(notice == null || notice.isNotBlank())
        if (status != CompareHereUiStatus.READY) {
            require(rows.none { it.valueRank != null || it.bestValue })
        }
        if (status == CompareHereUiStatus.READY) {
            require(rows.size >= 2)
            require(rows.all { it.valueRank != null })
            require(rows.any { it.bestValue })
        }
    }
}

/**
 * Consumer state plus opaque exact lookup retained outside renderer strings.
 *
 * A physical renderer should receive [state] only. Candidate ids and core comparison objects
 * remain available to future typed edit/capture actions without reconstructing facts from text.
 */
class CompareHereUiProjection internal constructor(
    val state: CompareHereUiState,
    internal val exactByCandidateId: Map<String, CompareHereExactCandidate>,
    internal val blockedByCandidateId: Map<String, CompareHereBlockedCandidate>,
    internal val displayNameByCandidateId: Map<String, String>,
    internal val priceSelection: CompareHerePriceSelection
)

/**
 * Pure presentation projector for the verified Compare Here core result.
 *
 * This object never recalculates unit value or ranking. Human labels are detached metadata and
 * cannot create candidates. Missing/unsafe labels never fall back to candidate ids or other
 * technical identifiers. If a READY core comparison cannot display every rankable candidate
 * safely, all winner/rank claims are suppressed rather than presenting a partial leaderboard.
 */
object CompareHereUiProjector {

    fun project(
        result: CompareHereComparisonResult,
        metadata: CompareHereDisplayMetadata
    ): CompareHereUiProjection {
        val rawLabels = metadata.entries.associate { it.candidateId to it.displayName }
        val exactById = result.exactCandidates.associateBy { it.candidateId }
        val blockedById = result.blockedCandidates.associateBy { it.candidateId }
        val allCandidateIds = exactById.keys + blockedById.keys

        val labels =
            allCandidateIds.associateWith { candidateId ->
                safeCompareHereLabel(
                    raw = rawLabels[candidateId],
                    candidateId = candidateId
                )
            }
        val omittedCount = labels.values.count { it == null }

        val coreReadyAndFullyLabeled =
            result.status == CompareHereComparisonStatus.READY &&
                result.exactCandidates.all { labels[it.candidateId] != null }

        val uiStatus =
            when {
                result.status == CompareHereComparisonStatus.READY && !coreReadyAndFullyLabeled ->
                    CompareHereUiStatus.DISPLAY_METADATA_INCOMPLETE

                result.status == CompareHereComparisonStatus.READY ->
                    CompareHereUiStatus.READY

                result.status == CompareHereComparisonStatus.NOT_ENOUGH_EXACT_CANDIDATES ->
                    CompareHereUiStatus.NOT_ENOUGH_DATA

                else -> CompareHereUiStatus.INCOMPATIBLE_DIMENSIONS
            }

        val exactRows =
            if (uiStatus == CompareHereUiStatus.READY) {
                result.rankedCandidates.map { ranked ->
                    val candidate = ranked.candidate
                    CompareHereUiRow(
                        title = requireNotNull(labels[candidate.candidateId]),
                        priceText = formatCompareHereMoney(candidate.selectedPrice),
                        quantityText = formatCompareHereQuantity(candidate.quantity),
                        unitRateText = formatCompareHereRate(candidate.rate),
                        valueRank = ranked.valueRank,
                        bestValue = ranked.valueRank == 1
                    )
                }
            } else {
                result.exactCandidates.mapNotNull { candidate ->
                    val label = labels[candidate.candidateId] ?: return@mapNotNull null
                    CompareHereUiRow(
                        title = label,
                        priceText = formatCompareHereMoney(candidate.selectedPrice),
                        quantityText = formatCompareHereQuantity(candidate.quantity),
                        unitRateText = formatCompareHereRate(candidate.rate),
                        valueRank = null,
                        bestValue = false
                    )
                }
            }

        val blockedRows =
            result.blockedCandidates.mapNotNull { blocked ->
                val label = labels[blocked.candidateId] ?: return@mapNotNull null
                CompareHereBlockedUiRow(
                    title = label,
                    reasonText = blockerText(blocked, result.priceSelection)
                )
            }

        return CompareHereUiProjection(
            state =
                CompareHereUiState(
                    headline = "Compare here",
                    priceModeText = priceModeText(result.priceSelection),
                    status = uiStatus,
                    statusTitle = statusTitle(uiStatus, result),
                    guidance = guidance(uiStatus, result),
                    rows = exactRows,
                    blockedRows = blockedRows,
                    omittedDisplayNameCount = omittedCount,
                    notice = omittedNotice(omittedCount)
                ),
            exactByCandidateId = exactById,
            blockedByCandidateId = blockedById,
            displayNameByCandidateId = labels.filterValues { it != null }.mapValues { requireNotNull(it.value) },
            priceSelection = result.priceSelection
        )
    }

    private fun statusTitle(
        status: CompareHereUiStatus,
        result: CompareHereComparisonResult
    ): String =
        when (status) {
            CompareHereUiStatus.READY ->
                if (result.bestValueCandidateIds.size > 1) "Best value tie" else "Best value"

            CompareHereUiStatus.NOT_ENOUGH_DATA -> "Need more exact information"
            CompareHereUiStatus.INCOMPATIBLE_DIMENSIONS -> "Cannot rank these together"
            CompareHereUiStatus.DISPLAY_METADATA_INCOMPLETE -> "Product names needed"
        }

    private fun guidance(
        status: CompareHereUiStatus,
        result: CompareHereComparisonResult
    ): String =
        when (status) {
            CompareHereUiStatus.READY ->
                "Lower exact unit price wins within this comparison."

            CompareHereUiStatus.NOT_ENOUGH_DATA ->
                selectedPriceGuidance(result.priceSelection)

            CompareHereUiStatus.DISPLAY_METADATA_INCOMPLETE ->
                "Some product names cannot be shown safely, so no winner is displayed."

            CompareHereUiStatus.INCOMPATIBLE_DIMENSIONS ->
                incompatibleGuidance(result.comparisonIssues)
        }

    private fun selectedPriceGuidance(selection: CompareHerePriceSelection): String =
        when (selection) {
            CompareHerePriceSelection.CURRENT ->
                "Add at least two products with an exact current price and package quantity."

            CompareHerePriceSelection.MEMBER ->
                "Add at least two products with an exact member price and package quantity. " +
                    "Current prices are not used as substitutes."
        }

    private fun incompatibleGuidance(issues: Set<CompareHereComparisonIssue>): String =
        when (issues) {
            setOf(CompareHereComparisonIssue.MIXED_CURRENCIES) ->
                "These prices use different currencies."

            setOf(CompareHereComparisonIssue.MIXED_RATE_UNITS) ->
                "These products use incompatible quantity units."

            else -> "These products use different currencies and quantity units."
        }

    private fun blockerText(
        blocked: CompareHereBlockedCandidate,
        priceSelection: CompareHerePriceSelection
    ): String =
        blocked.blockers
            .sortedBy { it.ordinal }
            .joinToString(separator = " · ") { blocker ->
                when (blocker) {
                    CompareHereCandidateBlocker.COMPARISON_INTENT_MISMATCH ->
                        "Not part of this comparison"

                    CompareHereCandidateBlocker.QUANTITY_UNKNOWN ->
                        "Package quantity needed"

                    CompareHereCandidateBlocker.SELECTED_PRICE_UNAVAILABLE ->
                        if (priceSelection == CompareHerePriceSelection.MEMBER) {
                            "Member price unavailable"
                        } else {
                            "Selected price unavailable"
                        }

                    CompareHereCandidateBlocker.NON_POSITIVE_SELECTED_PRICE ->
                        "Price must be greater than zero"

                    CompareHereCandidateBlocker.UNIT_RATE_NOT_POSITIVE ->
                        "Unit rate cannot be represented safely"

                    CompareHereCandidateBlocker.ARITHMETIC_OVERFLOW ->
                        "Values are too large to compare safely"
                }
            }

    private fun priceModeText(selection: CompareHerePriceSelection): String =
        when (selection) {
            CompareHerePriceSelection.CURRENT -> "Current prices"
            CompareHerePriceSelection.MEMBER -> "Member prices"
        }

    private fun omittedNotice(count: Int): String? =
        when (count) {
            0 -> null
            1 -> "1 product name could not be shown safely."
            else -> "$count product names could not be shown safely."
        }
}

internal fun formatCompareHereMoney(money: Money): String =
    "${BigDecimal.valueOf(money.minorUnits).movePointLeft(money.fractionDigits).toPlainString()} ${money.currencyCode}"

internal fun formatCompareHereQuantity(quantity: NormalizedQuantity): String {
    val amount =
        BigDecimal.valueOf(quantity.amountMicros)
            .movePointLeft(6)
            .stripTrailingZeros()
            .toPlainString()
    val suffix =
        when (quantity.unit) {
            BaseUnit.GRAM -> "g"
            BaseUnit.MILLILITRE -> "mL"
            BaseUnit.COUNT -> "items"
            BaseUnit.SQUARE_INCH -> "in²"
        }
    return "$amount $suffix"
}

internal fun formatCompareHereRate(rate: UnitRate): String {
    val amount =
        BigDecimal.valueOf(rate.currencyMicrosPerUnit)
            .movePointLeft(6)
            .stripTrailingZeros()
            .toPlainString()
    val suffix =
        when (rate.unit) {
            RateUnit.KILOGRAM -> "kg"
            RateUnit.LITRE -> "L"
            RateUnit.ITEM -> "item"
            RateUnit.SQUARE_INCH -> "in²"
        }
    return "$amount ${rate.currencyCode}/$suffix"
}

private fun safeCompareHereLabel(
    raw: String?,
    candidateId: String
): String? {
    val value = raw?.trim() ?: return null
    if (value.isBlank() || value.length > MAX_COMPARE_HERE_CONSUMER_LABEL_LENGTH) return null
    if (value.any { Character.isISOControl(it.code) }) return null

    if (value.equals(candidateId, ignoreCase = true)) return null
    if (candidateId.length >= 6 && value.contains(candidateId, ignoreCase = true)) return null

    val lower = value.lowercase()
    val technicalPrefixes =
        listOf("gtin:", "sku:", "provider:", "merchant:", "location:", "osm:", "wikidata:")
    if (technicalPrefixes.any(lower::contains)) return null

    val compact = value.filterNot(Char::isWhitespace)
    if (
        value.all { character -> character.isDigit() || character.isWhitespace() } &&
            GtinValidation.canonicalOrNull(compact) != null
    ) {
        return null
    }

    return value
}

private fun requireConsumerCompareHereLabel(value: String) {
    require(value.isNotBlank())
    require(value.length <= MAX_COMPARE_HERE_CONSUMER_LABEL_LENGTH)
    require(value.none { Character.isISOControl(it.code) })
}
