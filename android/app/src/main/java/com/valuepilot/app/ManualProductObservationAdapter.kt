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

/**
 * Ranking-free manual text capture adapter shared by legacy and exact comparison routes.
 *
 * This adapter only bounds and normalizes user-provided product blocks into observations. It does
 * not parse prices, infer comparability, select a ranking mode, or decide a winner.
 */
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

        val normalized = normalizeLineEndings(rawInput)

        val blocks = normalized
            .split(blankLineRegex)

        return captureBlocks(
            rawBlocks = blocks,
            observedAtEpochMillis = observedAtEpochMillis
        )
    }

    fun captureBlocks(
        rawBlocks: List<String>,
        observedAtEpochMillis: Long
    ): ManualCaptureResult {
        var totalChars = 0L

        for (rawBlock in rawBlocks) {
            totalChars += rawBlock.length.toLong()

            if (totalChars > MAX_INPUT_CHARS) {
                return ManualCaptureResult.Failure(
                    ManualCaptureFailure.INPUT_TOO_LONG
                )
            }
        }

        val blocks = rawBlocks
            .map(::normalizeLineEndings)
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

    private fun normalizeLineEndings(value: String): String =
        value
            .replace("\r\n", "\n")
            .replace('\r', '\n')
}
