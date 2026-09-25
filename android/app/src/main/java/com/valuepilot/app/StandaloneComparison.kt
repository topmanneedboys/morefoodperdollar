package com.valuepilot.app

import com.valuepilot.core.ProductObservation

enum class StandaloneComparisonStatus {
    EMPTY,
    READY,
    INPUT_TOO_LONG,
    TOO_MANY_BLOCKS,
    BLOCK_TOO_LONG,
    NOT_ENOUGH_VALID_PRODUCTS,
    MIXED_CURRENCIES
}

data class StandaloneComparisonRow(
    val rank: Int,
    val name: String,
    val quantity: String?,
    val priceSummary: String,
    val metricLabel: String,
    val exactnessLabel: String,
    val best: Boolean
)

data class StandaloneComparisonState(
    val submittedCount: Int,
    val parsedCount: Int,
    val rejectedCount: Int,
    val status: StandaloneComparisonStatus,
    val statusText: String,
    val results: List<StandaloneComparisonRow>,
    val comparisonSucceeded: Boolean
)

sealed interface StandaloneComparisonIntent {
    data class Compare(
        val rawInput: String,
        val observedAtEpochMillis: Long
    ) : StandaloneComparisonIntent

    data class CompareBlocks(
        val productBlocks: List<String>,
        val observedAtEpochMillis: Long
    ) : StandaloneComparisonIntent

    data object Clear : StandaloneComparisonIntent
}

class StandaloneComparisonController(
    private val parser: ProductParser = DeterministicProductParser,
    private val rankingEngine: RankingEngine = DeterministicRankingEngine
) {
    fun initialState(): StandaloneComparisonState =
        StandaloneComparisonState(
            submittedCount = 0,
            parsedCount = 0,
            rejectedCount = 0,
            status = StandaloneComparisonStatus.EMPTY,
            statusText = "Enter at least two products to compare",
            results = emptyList(),
            comparisonSucceeded = false
        )

    @Suppress("UNUSED_PARAMETER")
    fun reduce(
        previous: StandaloneComparisonState,
        intent: StandaloneComparisonIntent
    ): StandaloneComparisonState =
        when (intent) {
            StandaloneComparisonIntent.Clear ->
                initialState()

            is StandaloneComparisonIntent.Compare ->
                compareCapture(
                    ManualProductObservationAdapter.capture(
                        rawInput = intent.rawInput,
                        observedAtEpochMillis = intent.observedAtEpochMillis
                    )
                )

            is StandaloneComparisonIntent.CompareBlocks ->
                compareCapture(
                    ManualProductObservationAdapter.captureBlocks(
                        rawBlocks = intent.productBlocks,
                        observedAtEpochMillis = intent.observedAtEpochMillis
                    )
                )
        }

    private fun compareCapture(
        capture: ManualCaptureResult
    ): StandaloneComparisonState =
        when (capture) {
            is ManualCaptureResult.Failure ->
                captureFailureState(capture.reason)

            is ManualCaptureResult.Success ->
                compareCaptured(capture.observations)
        }

    private fun captureFailureState(
        reason: ManualCaptureFailure
    ): StandaloneComparisonState {
        val status = when (reason) {
            ManualCaptureFailure.INPUT_TOO_LONG ->
                StandaloneComparisonStatus.INPUT_TOO_LONG

            ManualCaptureFailure.TOO_MANY_BLOCKS ->
                StandaloneComparisonStatus.TOO_MANY_BLOCKS

            ManualCaptureFailure.BLOCK_TOO_LONG ->
                StandaloneComparisonStatus.BLOCK_TOO_LONG
        }

        val text = when (reason) {
            ManualCaptureFailure.INPUT_TOO_LONG ->
                "Input is too long"

            ManualCaptureFailure.TOO_MANY_BLOCKS ->
                "Too many products were submitted"

            ManualCaptureFailure.BLOCK_TOO_LONG ->
                "A product block is too long"
        }

        return StandaloneComparisonState(
            submittedCount = 0,
            parsedCount = 0,
            rejectedCount = 0,
            status = status,
            statusText = text,
            results = emptyList(),
            comparisonSucceeded = false
        )
    }

    private fun compareCaptured(
        observations: List<ProductObservation>
    ): StandaloneComparisonState {
        val parsedProducts = mutableListOf<ValueItem>()
        var rejectedCount = 0

        for (observation in observations) {
            val parsed = parser.parse(
                observation.rawText,
                observation.sourceId
            )

            if (parsed == null) {
                rejectedCount++
            } else {
                parsedProducts += parsed
            }
        }

        if (parsedProducts.size < 2) {
            return StandaloneComparisonState(
                submittedCount = observations.size,
                parsedCount = parsedProducts.size,
                rejectedCount = rejectedCount,
                status = StandaloneComparisonStatus.NOT_ENOUGH_VALID_PRODUCTS,
                statusText = "Enter at least two valid products to compare",
                results = emptyList(),
                comparisonSucceeded = false
            )
        }

        val currencies = parsedProducts
            .map { it.currency }
            .toSet()

        if (currencies.size > 1) {
            return StandaloneComparisonState(
                submittedCount = observations.size,
                parsedCount = parsedProducts.size,
                rejectedCount = rejectedCount,
                status = StandaloneComparisonStatus.MIXED_CURRENCIES,
                statusText = "Products with different currencies cannot be compared",
                results = emptyList(),
                comparisonSucceeded = false
            )
        }

        val ranked = rankingEngine.rank(
            RankingRequest(
                context = null,
                products = parsedProducts,
                mode = RankMode.SMART,
                maxPrice = null,
                foodOnly = false,
                excludePork = false,
                useMemberPrices = false
            )
        )

        val rows = ranked.map { rankedItem ->
            val item = rankedItem.item

            StandaloneComparisonRow(
                rank = rankedItem.rank,
                name = item.name,
                quantity = item.quantity?.display,
                priceSummary = ValueEngine.money(
                    item.offer.currentPrice,
                    item.offer.currency
                ),
                metricLabel = rankedItem.metricLabel,
                exactnessLabel = rankedItem.exactnessLabel,
                best = rankedItem.rank == 1
            )
        }

        return StandaloneComparisonState(
            submittedCount = observations.size,
            parsedCount = parsedProducts.size,
            rejectedCount = rejectedCount,
            status = StandaloneComparisonStatus.READY,
            statusText = "Comparison ready",
            results = rows,
            comparisonSucceeded = true
        )
    }
}
