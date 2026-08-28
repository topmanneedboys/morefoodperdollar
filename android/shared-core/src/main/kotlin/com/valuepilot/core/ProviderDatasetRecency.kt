package com.valuepilot.core

/**
 * Caller-supplied recency policy for provider datasets/files.
 *
 * This is deliberately separate from [EvidenceFreshnessPolicy]. Dataset/file
 * recency is provenance about a feed snapshot; it is not a trustworthy
 * per-product or per-price observation time unless a provider explicitly says so.
 * Shared core owns no clock, so the caller supplies the evaluation instant.
 */
data class ImportedDatasetRecencyPolicy(
    val recentForMillis: Long,
    val staleAfterMillis: Long,
    val futureToleranceMillis: Long = 300_000L
) {
    init {
        require(recentForMillis >= 0L)
        require(staleAfterMillis >= recentForMillis)
        require(futureToleranceMillis >= 0L)
    }
}

/**
 * Age classification for a provider dataset/file snapshot.
 *
 * RECENT intentionally does not mean that any individual offer is fresh.
 */
enum class ImportedDatasetRecency {
    UNKNOWN,
    FUTURE_DATED,
    RECENT,
    AGING,
    STALE
}

/**
 * Deterministic, network-free classifier for dataset/file provenance time.
 *
 * Never use this result as a substitute for ProviderOfferImportRecord's
 * priceObservedAtEpochMillis. It cannot make an offer rankable and cannot prove
 * that a merchant-site price or availability value is live at display time.
 */
object ImportedDatasetRecencyEvaluator {

    fun classify(
        datasetGeneratedAtEpochMillis: Long?,
        evaluatedAtEpochMillis: Long,
        policy: ImportedDatasetRecencyPolicy
    ): ImportedDatasetRecency {
        if (
            datasetGeneratedAtEpochMillis == null ||
            datasetGeneratedAtEpochMillis <= 0L ||
            evaluatedAtEpochMillis <= 0L
        ) {
            return ImportedDatasetRecency.UNKNOWN
        }

        val ageMillis =
            evaluatedAtEpochMillis - datasetGeneratedAtEpochMillis

        if (ageMillis < -policy.futureToleranceMillis) {
            return ImportedDatasetRecency.FUTURE_DATED
        }

        val nonNegativeAge = ageMillis.coerceAtLeast(0L)

        return when {
            nonNegativeAge <= policy.recentForMillis ->
                ImportedDatasetRecency.RECENT

            nonNegativeAge <= policy.staleAfterMillis ->
                ImportedDatasetRecency.AGING

            else ->
                ImportedDatasetRecency.STALE
        }
    }
}

/**
 * Convenience view over the staged provider record's dataset provenance time.
 *
 * This does not read or modify priceObservedAtEpochMillis and does not promote
 * the dataset timestamp into ShoppingEvidence observation freshness.
 */
fun ProviderOfferImportRecord.datasetRecency(
    evaluatedAtEpochMillis: Long,
    policy: ImportedDatasetRecencyPolicy
): ImportedDatasetRecency =
    ImportedDatasetRecencyEvaluator.classify(
        datasetGeneratedAtEpochMillis = datasetGeneratedAtEpochMillis,
        evaluatedAtEpochMillis = evaluatedAtEpochMillis,
        policy = policy
    )
