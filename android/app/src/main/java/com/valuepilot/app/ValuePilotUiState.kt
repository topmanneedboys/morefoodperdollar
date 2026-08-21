package com.valuepilot.app

import java.util.Locale

@JvmInline
value class ProductResultId(val value: Long)

data class ValuePilotFilterSettings(
    val maxPrice: Double? = null,
    val foodOnly: Boolean = true,
    val excludePork: Boolean = false,
    val useMemberPrices: Boolean = false,
    val advancedMode: Boolean = false,
    val hapticsEnabled: Boolean = true
)

data class FilterUiState(
    val maxPriceText: String,
    val foodOnly: Boolean,
    val excludePork: Boolean,
    val useMemberPrices: Boolean,
    val advancedMode: Boolean,
    val hapticsEnabled: Boolean
)

data class ProductRowUiState(
    val resultId: ProductResultId,
    val stableId: Long,
    val title: String,
    val quantity: String?,
    val metric: String,
    val exactness: String,
    val estimate: Boolean,
    val priceSummary: String,
    val footer: String,
    val unavailable: Boolean,
    val contentDescription: String
)

data class RankingOptionUiState(val mode: RankMode, val label: String, val selected: Boolean)

data class RankingGroupUiState(val label: String, val options: List<RankingOptionUiState>)

enum class UiFeedback { ITEM_OPENED }

data class UiFeedbackEvent(val id: Long, val feedback: UiFeedback)

/** Stable immutable application state consumed by replaceable presentations. */
data class ValuePilotUiState(
    val contextSummary: String,
    val matchCount: Int,
    val statusText: String,
    val loading: Boolean,
    val collecting: Boolean,
    val overlayVisible: Boolean,
    val selectedRankingLabel: String,
    val rankingGroups: List<RankingGroupUiState>,
    val filters: FilterUiState,
    val results: List<ProductRowUiState>,
    val feedbackEvent: UiFeedbackEvent? = null
) {
    companion object {
        fun initial(settings: ValuePilotFilterSettings = ValuePilotFilterSettings()): ValuePilotUiState = ValuePilotUiState(
            contextSummary = "Current page · 0 matches",
            matchCount = 0,
            statusText = "Experimental live capture is ready",
            loading = false,
            collecting = false,
            overlayVisible = true,
            selectedRankingLabel = ValuePilotUiProjector.rankLabel(RankMode.SMART),
            rankingGroups = ValuePilotUiProjector.rankingGroups(emptySet(), RankMode.SMART),
            filters = ValuePilotUiProjector.filters(settings),
            results = emptyList()
        )
    }
}

/** Typed actions emitted by presentations; views never call core implementations directly. */
sealed interface ValuePilotIntent {
    data object Rescan : ValuePilotIntent
    data object CollectOffscreen : ValuePilotIntent
    data object RunOcr : ValuePilotIntent
    data object ClearResults : ValuePilotIntent
    data object StopCollection : ValuePilotIntent
    data class SelectRankMode(val mode: RankMode) : ValuePilotIntent
    data class OpenProduct(val resultId: ProductResultId) : ValuePilotIntent
    data class UpdateFilters(val settings: ValuePilotFilterSettings) : ValuePilotIntent
}

fun interface ValuePilotUiRenderer {
    fun render(state: ValuePilotUiState)
}

data class ProjectedResults(
    val rows: List<ProductRowUiState>,
    val rankingGroups: List<RankingGroupUiState>,
    val selectedRankingLabel: String
)

/** Pure domain-to-application-state projection. Project collections off the main thread. */
object ValuePilotUiProjector {
    fun projectResults(context: SearchContext?, ranked: List<RankedItem>, selectedMode: RankMode): ProjectedResults {
        val available = RankingModePolicy.availableModes(ranked.asSequence().map(RankedItem::item), context?.query)
        val rows = ranked.map { rankedItem ->
            val item = rankedItem.item
            val prices = buildString {
                append(ValueEngine.money(item.offer.currentPrice, item.offer.currency))
                item.offer.memberPrice?.let { append(" · Member ${ValueEngine.money(it, item.offer.currency)}") }
                item.offer.previousPrice?.takeIf { it > item.offer.currentPrice + .005 }?.let {
                    append(" · Was ${ValueEngine.money(it, item.offer.currency)}")
                }
            }
            ProductRowUiState(
                resultId = ProductResultId(rankedItem.stableId),
                stableId = rankedItem.stableId,
                title = "${rankedItem.rank} · ${item.name}",
                quantity = item.quantity?.display,
                metric = rankedItem.metricLabel,
                exactness = rankedItem.exactnessLabel,
                estimate = rankedItem.exactnessLabel == "Estimate",
                priceSummary = prices,
                footer = item.availability ?: "Tap to open item",
                unavailable = item.availability == "Out of stock",
                contentDescription = buildString {
                    append("Rank ${rankedItem.rank}, ${item.name}, ${rankedItem.metricLabel}, $prices")
                    item.availability?.let { append(", $it") }
                    append(". Double tap to open this product.")
                }
            )
        }
        return ProjectedResults(rows, rankingGroups(available, selectedMode), rankLabel(selectedMode))
    }

    fun contextSummary(context: SearchContext?, matches: Int): String {
        val title = context?.displayQuery ?: "Current page"
        return "$title · $matches ${if (matches == 1) "match" else "matches"}"
    }

    fun filters(settings: ValuePilotFilterSettings): FilterUiState = FilterUiState(
        maxPriceText = settings.maxPrice?.let { String.format(Locale.US, "%.2f", it) }.orEmpty(),
        foodOnly = settings.foodOnly,
        excludePork = settings.excludePork,
        useMemberPrices = settings.useMemberPrices,
        advancedMode = settings.advancedMode,
        hapticsEnabled = settings.hapticsEnabled
    )

    fun rankingGroups(availableModes: Set<RankMode>, selectedMode: RankMode): List<RankingGroupUiState> {
        val available = availableModes + RankMode.SMART
        fun options(modes: List<RankMode>) = modes.filter(available::contains).map { mode ->
            RankingOptionUiState(mode, rankLabel(mode), mode == selectedMode)
        }
        return buildList {
            add(RankingGroupUiState("Recommended", options(listOf(RankMode.SMART))))
            val grocery = options(listOf(RankMode.MASS, RankMode.VOLUME, RankMode.UNIT))
            if (grocery.isNotEmpty()) add(RankingGroupUiState("Grocery", grocery))
            val restaurant = options(listOf(RankMode.CALORIE, RankMode.PORTION, RankMode.MEAT, RankMode.PIZZA))
            if (restaurant.isNotEmpty()) add(RankingGroupUiState("Restaurant", restaurant))
        }
    }

    fun selectMode(groups: List<RankingGroupUiState>, mode: RankMode): List<RankingGroupUiState> =
        groups.map { group -> group.copy(options = group.options.map { it.copy(selected = it.mode == mode) }) }

    fun rankLabel(mode: RankMode): String = when (mode) {
        RankMode.SMART -> "Smart Value"
        RankMode.MASS -> "Price per kg"
        RankMode.VOLUME -> "Price per litre"
        RankMode.UNIT -> "Price per item"
        RankMode.CALORIE -> "Calories per dollar"
        RankMode.PIZZA -> "Pizza size per dollar"
        RankMode.PORTION -> "Food amount/$ · Estimate"
        RankMode.MEAT -> "Meat value/$ · Estimate"
    }
}

/**
 * Application-facing state holder. Canonical products remain in [ProductRepository]; only the
 * currently projected ranked generation is retained for opaque result-ID resolution.
 */
class ValuePilotApplicationState(settings: ValuePilotFilterSettings = ValuePilotFilterSettings()) {
    private var renderer: ValuePilotUiRenderer? = null
    private var context: SearchContext? = null
    private var rankedItems: List<RankedItem> = emptyList()
    private var feedbackSequence = 0L

    var state: ValuePilotUiState = ValuePilotUiState.initial(settings)
        private set

    fun attach(value: ValuePilotUiRenderer) {
        renderer = value
        value.render(state)
    }

    fun detach(value: ValuePilotUiRenderer) {
        if (renderer === value) renderer = null
    }

    fun updateContext(value: SearchContext?, clearResults: Boolean) {
        context = value
        if (clearResults) {
            rankedItems = emptyList()
            mutate {
                it.copy(
                    contextSummary = ValuePilotUiProjector.contextSummary(value, 0),
                    matchCount = 0,
                    results = emptyList(),
                    rankingGroups = ValuePilotUiProjector.rankingGroups(emptySet(), RankMode.SMART),
                    selectedRankingLabel = ValuePilotUiProjector.rankLabel(RankMode.SMART)
                )
            }
        } else mutate { it.copy(contextSummary = ValuePilotUiProjector.contextSummary(value, it.matchCount)) }
    }

    fun showResults(context: SearchContext?, ranked: List<RankedItem>, projected: ProjectedResults) {
        this.context = context
        rankedItems = ranked
        state = state.copy(
            contextSummary = ValuePilotUiProjector.contextSummary(context, projected.rows.size),
            matchCount = projected.rows.size,
            results = projected.rows,
            rankingGroups = projected.rankingGroups,
            selectedRankingLabel = projected.selectedRankingLabel
        )
        dispatch()
    }

    fun clearResults() = updateContext(context, clearResults = true)
    fun updateSelectedMode(mode: RankMode) = mutate {
        it.copy(
            selectedRankingLabel = ValuePilotUiProjector.rankLabel(mode),
            rankingGroups = ValuePilotUiProjector.selectMode(it.rankingGroups, mode)
        )
    }
    fun updateFilters(settings: ValuePilotFilterSettings) = mutate { it.copy(filters = ValuePilotUiProjector.filters(settings)) }
    fun setStatus(value: String) = mutate { it.copy(statusText = value) }
    fun setLoading(value: Boolean) = mutate { it.copy(loading = value) }
    fun setCollecting(value: Boolean) = mutate { it.copy(collecting = value) }
    fun setOverlayVisible(value: Boolean) = mutate { it.copy(overlayVisible = value) }
    fun emitFeedback(value: UiFeedback) = mutate { it.copy(feedbackEvent = UiFeedbackEvent(++feedbackSequence, value)) }

    fun resolveProduct(resultId: ProductResultId): ValueItem? {
        var match: ValueItem? = null
        for (ranked in rankedItems) {
            if (ranked.stableId != resultId.value) continue
            if (match != null) return null
            match = ranked.item
        }
        return match
    }

    private inline fun mutate(transform: (ValuePilotUiState) -> ValuePilotUiState) {
        val next = transform(state)
        if (next == state) return
        state = next
        dispatch()
    }

    private fun dispatch() = renderer?.render(state)
}
