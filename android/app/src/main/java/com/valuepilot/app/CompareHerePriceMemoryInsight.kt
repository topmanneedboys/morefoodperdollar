package com.valuepilot.app

/**
 * Conservative answer from private history for one newly observed exact comparison entry.
 *
 * Matching is intentionally strict: a label, package quantity, price basis, promotion terms,
 * currency and rate unit must all agree. This is personal context only; it is not a current-store
 * claim and never changes organic ranking.
 */
internal enum class CompareHerePriceMemoryAssessment {
    NO_MATCHING_HISTORY,
    LOWER_THAN_LAST,
    SAME_AS_LAST,
    HIGHER_THAN_LAST,
    BELOW_PERSONAL_RANGE,
    WITHIN_PERSONAL_RANGE,
    ABOVE_PERSONAL_RANGE
}

internal data class CompareHerePriceMemoryInsight(
    val displayName: String,
    val assessment: CompareHerePriceMemoryAssessment,
    val historicalObservationCount: Int,
    val lastRate: com.valuepilot.core.UnitRate? = null,
    val minimumRate: com.valuepilot.core.UnitRate? = null,
    val maximumRate: com.valuepilot.core.UnitRate? = null
) {
    init {
        require(displayName.isNotBlank())
        require(historicalObservationCount >= 0)
        if (assessment == CompareHerePriceMemoryAssessment.NO_MATCHING_HISTORY) {
            require(historicalObservationCount == 0)
            require(lastRate == null && minimumRate == null && maximumRate == null)
        } else {
            require(historicalObservationCount > 0)
            requireNotNull(lastRate)
            requireNotNull(minimumRate)
            requireNotNull(maximumRate)
            require(minimumRate.currencyCode == maximumRate.currencyCode)
            require(minimumRate.unit == maximumRate.unit)
        }
    }
}

internal data class CompareHerePriceMemoryHistorySummary(
    val displayName: String,
    val observationCount: Int,
    val lastRate: com.valuepilot.core.UnitRate,
    val lowestRate: com.valuepilot.core.UnitRate,
    val highestRate: com.valuepilot.core.UnitRate
) {
    init {
        require(displayName.isNotBlank())
        require(observationCount > 0)
        require(lastRate.currencyCode == lowestRate.currencyCode)
        require(lastRate.currencyCode == highestRate.currencyCode)
        require(lastRate.unit == lowestRate.unit)
        require(lastRate.unit == highestRate.unit)
    }
}

internal object CompareHerePriceMemoryEvaluator {
    // This is private display context only; it never becomes offer, availability, or ranking authority.
    fun assess(
        current: CompareHerePrivatePriceMemoryEntry,
        history: CompareHerePrivatePriceMemoryState
    ): CompareHerePriceMemoryInsight {
        val matches =
            history.entries
                .asSequence()
                .filterNot { entry -> entry.observationId == current.observationId }
                .filter { entry -> CompareHerePriceMemoryEvaluator.entriesMatch(entry, current) }
                .sortedWith(
                    compareByDescending<CompareHerePrivatePriceMemoryEntry> { it.observedAtEpochMillis }
                        .thenBy { it.observationId }
                )
                .toList()

        if (matches.isEmpty()) {
            return CompareHerePriceMemoryInsight(
                displayName = current.displayName,
                assessment = CompareHerePriceMemoryAssessment.NO_MATCHING_HISTORY,
                historicalObservationCount = 0
            )
        }

        val last = matches.first()
        val minimum = matches.minBy { entry -> entry.rate.currencyMicrosPerUnit }
        val maximum = matches.maxBy { entry -> entry.rate.currencyMicrosPerUnit }
        val assessment =
            if (matches.size == 1) {
                when {
                    current.rate.currencyMicrosPerUnit < last.rate.currencyMicrosPerUnit ->
                        CompareHerePriceMemoryAssessment.LOWER_THAN_LAST

                    current.rate.currencyMicrosPerUnit > last.rate.currencyMicrosPerUnit ->
                        CompareHerePriceMemoryAssessment.HIGHER_THAN_LAST

                    else -> CompareHerePriceMemoryAssessment.SAME_AS_LAST
                }
            } else {
                when {
                    current.rate.currencyMicrosPerUnit < minimum.rate.currencyMicrosPerUnit ->
                        CompareHerePriceMemoryAssessment.BELOW_PERSONAL_RANGE

                    current.rate.currencyMicrosPerUnit > maximum.rate.currencyMicrosPerUnit ->
                        CompareHerePriceMemoryAssessment.ABOVE_PERSONAL_RANGE

                    else -> CompareHerePriceMemoryAssessment.WITHIN_PERSONAL_RANGE
                }
            }

        return CompareHerePriceMemoryInsight(
            displayName = current.displayName,
            assessment = assessment,
            historicalObservationCount = matches.size,
            lastRate = last.rate,
            minimumRate = minimum.rate,
            maximumRate = maximum.rate
        )
    }

    internal fun entriesMatch(
        left: CompareHerePrivatePriceMemoryEntry,
        right: CompareHerePrivatePriceMemoryEntry
    ): Boolean =
        canonicalLabel(left.displayName) == canonicalLabel(right.displayName) &&
            left.price.currencyCode == right.price.currencyCode &&
            left.price.fractionDigits == right.price.fractionDigits &&
            left.quantity == right.quantity &&
            left.priceSelection == right.priceSelection &&
            left.promotionLabel == right.promotionLabel &&
            left.promotionReceivedUnits == right.promotionReceivedUnits &&
            left.promotionPaidUnits == right.promotionPaidUnits &&
            left.rate.unit == right.rate.unit

    private fun canonicalLabel(value: String): String =
        value.trim().lowercase().split(Regex("\\s+")).joinToString(" ")
}

internal object CompareHerePriceMemoryHistory {
    fun summarize(
        current: CompareHerePrivatePriceMemoryEntry,
        state: CompareHerePrivatePriceMemoryState
    ): CompareHerePriceMemoryHistorySummary {
        val matches =
            (state.entries + current)
                .filter { entry -> CompareHerePriceMemoryEvaluator.entriesMatch(entry, current) }
                .sortedWith(
                    compareByDescending<CompareHerePrivatePriceMemoryEntry> { it.observedAtEpochMillis }
                        .thenBy { it.observationId }
                )
        require(matches.isNotEmpty())
        return CompareHerePriceMemoryHistorySummary(
            displayName = current.displayName,
            observationCount = matches.size,
            lastRate = matches.first().rate,
            lowestRate = matches.minBy { it.rate.currencyMicrosPerUnit }.rate,
            highestRate = matches.maxBy { it.rate.currencyMicrosPerUnit }.rate
        )
    }
}

internal object CompareHerePriceMemoryInsightPresenter {
    fun describe(insight: CompareHerePriceMemoryInsight): String =
        when (insight.assessment) {
            CompareHerePriceMemoryAssessment.NO_MATCHING_HISTORY ->
                "No personal history for this exact package yet; ValuePilot will remember this comparison."

            CompareHerePriceMemoryAssessment.LOWER_THAN_LAST ->
                "Below your last remembered rate (${formatCompareHereRate(requireNotNull(insight.lastRate))})."

            CompareHerePriceMemoryAssessment.SAME_AS_LAST ->
                "The same as your last remembered rate (${formatCompareHereRate(requireNotNull(insight.lastRate))})."

            CompareHerePriceMemoryAssessment.HIGHER_THAN_LAST ->
                "Above your last remembered rate (${formatCompareHereRate(requireNotNull(insight.lastRate))})."

            CompareHerePriceMemoryAssessment.BELOW_PERSONAL_RANGE ->
                "Below your remembered range (${formatCompareHereRate(requireNotNull(insight.minimumRate))}–${formatCompareHereRate(requireNotNull(insight.maximumRate))})."

            CompareHerePriceMemoryAssessment.WITHIN_PERSONAL_RANGE ->
                "Within your remembered range (${formatCompareHereRate(requireNotNull(insight.minimumRate))}–${formatCompareHereRate(requireNotNull(insight.maximumRate))})."

            CompareHerePriceMemoryAssessment.ABOVE_PERSONAL_RANGE ->
                "Above your remembered range (${formatCompareHereRate(requireNotNull(insight.minimumRate))}–${formatCompareHereRate(requireNotNull(insight.maximumRate))})."
        }

    fun describeHistory(summary: CompareHerePriceMemoryHistorySummary): String =
        "Personal history: ${summary.observationCount} observation${if (summary.observationCount == 1) "" else "s"} · " +
            "lowest ${formatCompareHereRate(summary.lowestRate)} · " +
            "highest ${formatCompareHereRate(summary.highestRate)} · " +
            "latest ${formatCompareHereRate(summary.lastRate)}."
}
