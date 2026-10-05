package com.valuepilot.app

/**
 * Small, bounded state for the first viewport of You.
 *
 * Counts are summaries only. They do not carry product identity, price evidence, or
 * availability authority; the existing Bought, Saved, and Watching routes remain the
 * owners of those facts.
 */
internal data class PracticalShoppingYouOverviewState(
    val boughtCount: Int,
    val savedCount: Int,
    val watchingCount: Int
) {
    init {
        require(boughtCount >= 0)
        require(savedCount >= 0)
        require(watchingCount >= 0)
    }
}

internal sealed interface PracticalShoppingYouOverviewAction {
    data object OpenBought : PracticalShoppingYouOverviewAction
    data object OpenSaved : PracticalShoppingYouOverviewAction
    data object OpenWatching : PracticalShoppingYouOverviewAction
}
