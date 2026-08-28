package com.valuepilot.app

import com.valuepilot.core.AvailabilityState
import com.valuepilot.core.BaseUnit
import com.valuepilot.core.EvidenceFreshness
import com.valuepilot.core.Money
import com.valuepilot.core.NormalizedQuantity
import com.valuepilot.core.ProductionBestValueBlockedPresentationItem
import com.valuepilot.core.ProductionBestValueComparisonKey
import com.valuepilot.core.ProductionBestValuePresentationItem
import com.valuepilot.core.ProductionBestValuePresentationSnapshot
import com.valuepilot.core.RateUnit
import com.valuepilot.core.UnitRate
import java.math.BigDecimal

/**
 * Additive UI state for the verified production-evidence path.
 *
 * This deliberately does not reuse legacy [ValueItem], [RankedItem] or RankMode.
 * The application receives a shared-core point-in-time presentation snapshot and
 * performs formatting only. It never parses evidence, changes eligibility,
 * compares values, resolves conflicts, or re-ranks products.
 *
 * Internal merchant/location/channel scope identifiers, raw provider URLs, and
 * internal blocker enum details are deliberately absent from UI-ready state.
 * Exact source facts and diagnostics remain available only through the opaque
 * lookup maps in [ProductionSearchUiProjection].
 */
data class ProductionSearchRowUiState(
    val candidateId: String,
    val valueRank: Int,
    val name: String,
    val priceText: String,
    val referencePriceText: String?,
    val quantityText: String,
    val unitRateText: String,
    val bestValue: Boolean,
    val offerScopeText: String,
    val sourceSummary: String,
    val availabilityText: String,
    val freshnessText: String
) {
    init {
        require(candidateId.isNotBlank())
        require(valueRank > 0)
        require(name.isNotBlank())
        require(priceText.isNotBlank())
        require(quantityText.isNotBlank())
        require(unitRateText.isNotBlank())
        require(offerScopeText.isNotBlank())
    }
}

data class ProductionSearchComparisonGroupUiState(
    val key: ProductionBestValueComparisonKey,
    val title: String,
    val meaningfulComparison: Boolean,
    val rows: List<ProductionSearchRowUiState>
) {
    init {
        require(title.isNotBlank())
        require(rows.isNotEmpty())
        require(meaningfulComparison == (rows.size >= 2))
        if (!meaningfulComparison) {
            require(rows.none { it.bestValue })
        }
    }
}

data class ProductionSearchBlockedUiState(
    val candidateId: String,
    val notice: String
) {
    init {
        require(candidateId.isNotBlank())
        require(notice.isNotBlank())
    }
}

data class ProductionSearchUiState(
    val evaluatedAtEpochMillis: Long,
    val groups: List<ProductionSearchComparisonGroupUiState>,
    val blocked: List<ProductionSearchBlockedUiState>
) {
    init {
        require(evaluatedAtEpochMillis > 0L)
    }
}

/**
 * UI state plus opaque exact-row lookup for future product/provenance/actions.
 *
 * Presentations should render [state]. Actions or diagnostics that need source
 * evidence, factual scope, raw URLs, or exact blocker enums resolve by candidate
 * ID through these maps instead of reconstructing facts/capabilities from UI text.
 */
data class ProductionSearchUiProjection(
    val state: ProductionSearchUiState,
    val rankedByCandidateId: Map<String, ProductionBestValuePresentationItem>,
    val blockedByCandidateId: Map<String, ProductionBestValueBlockedPresentationItem>
) {
    init {
        val stateRankedIds = state.groups.flatMap { group -> group.rows.map { it.candidateId } }.toSet()
        val stateBlockedIds = state.blocked.map { it.candidateId }.toSet()
        require(stateRankedIds == rankedByCandidateId.keys)
        require(stateBlockedIds == blockedByCandidateId.keys)
        require(stateRankedIds.intersect(stateBlockedIds).isEmpty())
    }
}

object ProductionSearchUiProjector {

    private const val MAX_UI_CANDIDATES = 128

    fun project(snapshot: ProductionBestValuePresentationSnapshot): ProductionSearchUiProjection {
        val allRanked = snapshot.groups.flatMap { it.items }
        val totalCandidateCount = allRanked.size + snapshot.blockedItems.size

        require(totalCandidateCount <= MAX_UI_CANDIDATES) {
            "Production UI projection exceeds the bounded candidate limit"
        }

        val rankedIds = allRanked.map { it.candidateId }
        val blockedIds = snapshot.blockedItems.map { it.candidateId }

        require(rankedIds.size == rankedIds.toSet().size) {
            "Production presentation candidate ids must be unique across ranking groups"
        }
        require(blockedIds.size == blockedIds.toSet().size) {
            "Blocked production presentation candidate ids must be unique"
        }
        require(rankedIds.toSet().intersect(blockedIds.toSet()).isEmpty()) {
            "A production candidate cannot be both ranked and blocked"
        }

        val groups =
            snapshot.groups.map { group ->
                ProductionSearchComparisonGroupUiState(
                    key = group.key,
                    title = comparisonTitle(group.key),
                    meaningfulComparison = group.meaningfulComparison,
                    rows = group.items.map(::row)
                )
            }

        val blocked =
            snapshot.blockedItems.map { item ->
                ProductionSearchBlockedUiState(
                    candidateId = item.candidateId,
                    notice = "Reference only — not eligible for Best Value"
                )
            }

        return ProductionSearchUiProjection(
            state =
                ProductionSearchUiState(
                    evaluatedAtEpochMillis = snapshot.evaluatedAtEpochMillis,
                    groups = groups,
                    blocked = blocked
                ),
            rankedByCandidateId = allRanked.associateBy { it.candidateId },
            blockedByCandidateId = snapshot.blockedItems.associateBy { it.candidateId }
        )
    }

    private fun row(item: ProductionBestValuePresentationItem): ProductionSearchRowUiState =
        ProductionSearchRowUiState(
            candidateId = item.candidateId,
            valueRank = item.valueRank,
            name = item.productName,
            priceText = formatMoney(item.currentPrice),
            referencePriceText = item.referencePrice?.let { "Reference ${formatMoney(it)}" },
            quantityText = formatQuantity(item.quantity),
            unitRateText = formatRate(item.unitRate),
            bestValue = item.bestValue,
            offerScopeText = "Offer country: ${item.offerCountryCode}",
            sourceSummary = "${item.providerDisplayName} · ${item.sourceDisplayName}",
            availabilityText = availabilityText(item.availabilityState),
            freshnessText = freshnessText(item.currentFreshness)
        )

    /** Exact decimal formatting only; no Double conversion or value calculation. */
    internal fun formatMoney(money: Money): String =
        "${BigDecimal.valueOf(money.minorUnits).movePointLeft(money.fractionDigits).toPlainString()} ${money.currencyCode}"

    /** Keep shared-core base units explicit; do not infer a different package unit. */
    internal fun formatQuantity(quantity: NormalizedQuantity): String {
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

    /** UnitRate is already authoritative deterministic math from shared core. */
    internal fun formatRate(rate: UnitRate): String {
        val amount =
            BigDecimal.valueOf(rate.currencyMicrosPerUnit)
                .movePointLeft(6)
                .stripTrailingZeros()
                .toPlainString()

        return "$amount ${rate.currencyCode}/${rateUnitSuffix(rate.unit)}"
    }

    private fun comparisonTitle(key: ProductionBestValueComparisonKey): String =
        "${key.currencyCode} · Price per ${rateUnitLongLabel(key.rateUnit)}"

    private fun rateUnitSuffix(unit: RateUnit): String =
        when (unit) {
            RateUnit.KILOGRAM -> "kg"
            RateUnit.LITRE -> "L"
            RateUnit.ITEM -> "item"
            RateUnit.SQUARE_INCH -> "in²"
        }

    private fun rateUnitLongLabel(unit: RateUnit): String =
        when (unit) {
            RateUnit.KILOGRAM -> "kilogram"
            RateUnit.LITRE -> "litre"
            RateUnit.ITEM -> "item"
            RateUnit.SQUARE_INCH -> "square inch"
        }

    private fun availabilityText(state: AvailabilityState): String =
        when (state) {
            AvailabilityState.IN_STOCK -> "In stock"
            AvailabilityState.LOW_STOCK -> "Low stock"
            AvailabilityState.OUT_OF_STOCK -> "Out of stock"
            AvailabilityState.UNAVAILABLE -> "Unavailable"
            AvailabilityState.UNKNOWN -> "Availability unknown"
        }

    private fun freshnessText(freshness: EvidenceFreshness): String =
        when (freshness) {
            EvidenceFreshness.FRESH -> "Fresh price evidence"
            EvidenceFreshness.AGING -> "Price evidence is aging"
            EvidenceFreshness.STALE -> "Stale price evidence"
            EvidenceFreshness.UNKNOWN -> "Price freshness unknown"
            EvidenceFreshness.FUTURE_DATED -> "Invalid future-dated evidence"
        }
}
