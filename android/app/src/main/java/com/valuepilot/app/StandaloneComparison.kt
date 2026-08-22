package com.valuepilot.app

import com.valuepilot.core.ProductObservation
import com.valuepilot.core.ProductObservationId

enum class ManualCaptureFailure {
    INPUT_TOO_LONG,
    TOO_MANY_BLOCKS,
    BLOCK_TOO_LONG
}

sealed interface ManualCaptureResult {
    data class Success(
        val observations: List<ProductObservation>
    ) : ManualCaptureResult

    data class Failure(
        val reason: ManualCaptureFailure
    ) : ManualCaptureResult
}

object ManualProductObservationAdapter {
    const val MAX_INPUT_CHARS = 65_536
    const val MAX_PRODUCT_BLOCKS = 100
    const val MAX_BLOCK_CHARS = 4_096

    private val blankLineRegex = Regex("\\n(?:[\\t ]*\\n)+")

    fun capture(
        rawInput: String,
        observedAtEpochMillis: Long
    ): ManualCaptureResult {
        if (rawInput.length > MAX_INPUT_CHARS) {
            return ManualCaptureResult.Failure(
                ManualCaptureFailure.INPUT_TOO_LONG
            )
        }

        val normalized = rawInput
            .replace("\r\n", "\n")
            .replace('\r', '\n')

        val blocks = normalized
            .split(blankLineRegex)
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        if (blocks.size > MAX_PRODUCT_BLOCKS) {
            return ManualCaptureResult.Failure(
                ManualCaptureFailure.TOO_MANY_BLOCKS
            )
        }

        if (blocks.any { it.length > MAX_BLOCK_CHARS }) {
            return ManualCaptureResult.Failure(
                ManualCaptureFailure.BLOCK_TOO_LONG
            )
        }

        val observations = blocks.mapIndexed { index, block ->
            ProductObservation(
                id = ProductObservationId("manual-${index + 1}"),
                sourceId = "manual",
                rawText = block,
                observedAtEpochMillis = observedAtEpochMillis
            )
        }

        return ManualCaptureResult.Success(observations)
    }
}

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
    ): StandaloneComparisonState {
        return when (intent) {
            StandaloneComparisonIntent.Clear -> initialState()
            is StandaloneComparisonIntent.Compare -> compare(intent)
        }
    }

    private fun compare(
        intent: StandaloneComparisonIntent.Compare
    ): StandaloneComparisonState {
        return when (
            val capture = ManualProductObservationAdapter.capture(
                rawInput = intent.rawInput,
                observedAtEpochMillis = intent.observedAtEpochMillis
            )
        ) {
            is ManualCaptureResult.Failure -> captureFailureState(capture.reason)
            is ManualCaptureResult.Success -> compareCaptured(capture.observations)
        }
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
