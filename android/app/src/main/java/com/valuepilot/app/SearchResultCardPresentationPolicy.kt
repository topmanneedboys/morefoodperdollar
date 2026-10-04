package com.valuepilot.app

/**
 * Presentation-only decisions for a Search result card.
 *
 * Product identity, store eligibility, purchase history and watch state are
 * supplied by the existing owners. This policy only decides which controls
 * are meaningful enough to reveal for this exact result.
 */
internal enum class SearchResultPrimaryAction {
    ADD_TO_BASKET,
    REMOVE_FROM_BASKET,
    NONE
}

internal data class SearchResultCardPresentation(
    val primaryAction: SearchResultPrimaryAction,
    val showPhotos: Boolean,
    val showWatch: Boolean,
    val showNearby: Boolean,
    val showHistory: Boolean,
    val watching: Boolean
)

internal object SearchResultCardPresentationPolicy {

    fun project(
        match: PracticalShoppingSearchIdentityPresentation.Match,
        mapEligible: Boolean,
        historyAvailable: Boolean,
        saved: Boolean,
        watched: Boolean
    ): SearchResultCardPresentation =
        SearchResultCardPresentation(
            primaryAction = when {
                saved -> SearchResultPrimaryAction.REMOVE_FROM_BASKET
                match.sourceLabel.isNullOrBlank() -> SearchResultPrimaryAction.NONE
                else -> SearchResultPrimaryAction.ADD_TO_BASKET
            },
            showPhotos = !match.sourceLabel.isNullOrBlank(),
            // Watch is an exact-key private action and does not require a
            // current offer. The current state only changes its label.
            showWatch = match.identityKey.isNotBlank(),
            showNearby = mapEligible,
            showHistory = historyAvailable,
            watching = watched
        )
}
