package com.valuepilot.app

private const val MAX_PRIVATE_HISTORY_FORGET_CHOICES = 32

/** One exact, user-selectable observation in the bounded forget flow. */
internal data class PracticalShoppingPrivatePriceHistoryForgetChoice(
    val observationId: String,
    val label: String
) {
    init {
        require(observationId.matches(Regex("[0-9a-f]{64}")))
        require(label.isNotBlank())
    }
}

/**
 * Immutable, bounded choice model for removing one local observation.
 *
 * The choice carries the exact observation fingerprint rather than a display name or list index,
 * so a changed/reordered history cannot delete a different entry. It is presentation-only and
 * does not make any observation current, public, store-specific or rankable.
 */
internal data class PracticalShoppingPrivatePriceHistoryForgetPresentation(
    val title: String,
    val message: String,
    val choices: List<PracticalShoppingPrivatePriceHistoryForgetChoice>,
    val omittedCount: Int
) {
    init {
        require(title.isNotBlank())
        require(message.isNotBlank())
        require(choices.isNotEmpty())
        require(choices.size <= MAX_PRIVATE_HISTORY_FORGET_CHOICES)
        require(choices.map { it.observationId }.distinct().size == choices.size)
        require(omittedCount >= 0)
    }

    companion object {
        const val visibleChoiceLimit: Int = MAX_PRIVATE_HISTORY_FORGET_CHOICES

        fun from(
            state: CompareHerePrivatePriceMemoryState
        ): PracticalShoppingPrivatePriceHistoryForgetPresentation? {
            if (state.entries.isEmpty()) return null

            val ordered =
                state.entries.sortedWith(
                    compareByDescending<CompareHerePrivatePriceMemoryEntry> {
                        it.observedAtEpochMillis
                    }.thenBy { it.observationId }
                )
            val visible = ordered.take(MAX_PRIVATE_HISTORY_FORGET_CHOICES)
            val omitted = (ordered.size - visible.size).coerceAtLeast(0)
            val message = buildString {
                append(
                    "Choose one exact observation to forget from this device. " +
                        "Forgetting removes only that personal historical record; it does not " +
                        "change any live price, store, plan or comparison result."
                )
                if (omitted > 0) {
                    append("\n\nShowing ")
                    append(visible.size)
                    append(" of ")
                    append(ordered.size)
                    append(" observations; ")
                    append(omitted)
                    append(" older observations remain in history and in export; clear-all removes everything.")
                }
            }

            return PracticalShoppingPrivatePriceHistoryForgetPresentation(
                title = "Forget one private observation?",
                message = message,
                choices = visible.map(::toChoice),
                omittedCount = omitted
            )
        }

        private fun toChoice(
            entry: CompareHerePrivatePriceMemoryEntry
        ): PracticalShoppingPrivatePriceHistoryForgetChoice =
            PracticalShoppingPrivatePriceHistoryForgetChoice(
                observationId = entry.observationId,
                label = buildString {
                    append(entry.displayName)
                    append(" · ")
                    append(formatCompareHereMoney(entry.price))
                    append(" · ")
                    append(formatCompareHereQuantity(entry.quantity))
                    append(" · ")
                    append(formatPrivateObservationDate(entry.observedAtEpochMillis))
                    append(" · ")
                    append(sourceLabel(entry.source))
                    entry.promotionLabel?.let { promotion ->
                        append(" · ")
                        append(promotion)
                    }
                }
            )

        private fun sourceLabel(source: CompareHerePrivatePriceMemorySource): String =
            when (source) {
                CompareHerePrivatePriceMemorySource.CONFIRMED_COMPARE_HERE -> "Scan & compare"
                CompareHerePrivatePriceMemorySource.CONFIRMED_GOOD_PRICE_CHECK ->
                    "Is this a good price?"
            }
    }
}
