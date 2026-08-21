package com.valuepilot.app

/**
 * Permanent application contracts. This file deliberately has no Android imports so capture,
 * parsing, storage, ranking, and matching implementations can be replaced independently.
 */
fun interface ProductObservationProvider<Input> {
    fun capture(input: Input, sourceId: String): ScanBatch?
}

fun interface ProductParser {
    fun parse(rawText: String, sourceId: String?): ValueItem?
}

interface ProductRepository {
    fun beginContext(context: SearchContext): Boolean
    fun reserveChanged(observations: Collection<ProductCardSnapshot>): List<ProductCardSnapshot>
    fun apply(parsedObservations: Collection<ParsedCard>): StoreApplyResult
    fun release(observations: Collection<ProductCardSnapshot>)
    fun clear()
    fun snapshot(): List<ValueItem>
    fun size(): Int
    fun stats(): ProductStoreStats
}

data class RankingRequest(
    val context: SearchContext?,
    val products: List<ValueItem>,
    val mode: RankMode,
    val maxPrice: Double?,
    val foodOnly: Boolean,
    val excludePork: Boolean,
    val useMemberPrices: Boolean
)

fun interface RankingEngine {
    fun rank(request: RankingRequest): List<RankedItem>
}

fun interface ProductMatchEngine {
    fun choose(target: ItemLocator, candidates: List<ItemMatchCandidate>): ItemMatchDecision
}

object DeterministicProductParser : ProductParser {
    override fun parse(rawText: String, sourceId: String?): ValueItem? = ValueEngine.analyze(rawText, sourceId)
}

object DeterministicRankingEngine : RankingEngine {
    override fun rank(request: RankingRequest): List<RankedItem> {
        val filtered = ValueEngine.filterItems(
            items = request.products,
            maxPrice = request.maxPrice,
            foodOnly = request.foodOnly,
            excludePork = request.excludePork,
            query = request.context?.query,
            useMemberPrice = request.useMemberPrices
        )
        val mode = RankingModePolicy.resolveRequested(request.mode, filtered, request.context?.query)
        return ValueEngine.rank(filtered, mode, request.useMemberPrices)
    }
}

object DeterministicProductMatcher : ProductMatchEngine {
    override fun choose(target: ItemLocator, candidates: List<ItemMatchCandidate>): ItemMatchDecision =
        ItemMatcher.choose(target, candidates)
}
