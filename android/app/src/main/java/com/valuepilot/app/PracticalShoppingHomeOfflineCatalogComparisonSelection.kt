package com.valuepilot.app

/**
 * Pure, bounded handoff from one selected identity-only Home catalog match into Scan & Compare.
 *
 * The returned value is still untrusted display text. This helper only makes sure the selected
 * label is safe for the existing share-text-style draft boundary; it does not confirm a product,
 * package quantity, price, store, availability, evidence or ranking result.
 */
internal object PracticalShoppingHomeOfflineCatalogComparisonSelection {

    fun displayNameFor(
        matches: List<PracticalShoppingHomeOfflineCatalogPresentation.Match>,
        selectedIndex: Int
    ): String? {
        val displayName = matches.getOrNull(selectedIndex)?.displayName?.trim() ?: return null
        if (displayName.isBlank()) return null
        if (displayName.length > ShareToValuePilotInput.MAX_CHARS) return null
        if (displayName.any { Character.isISOControl(it.code) }) return null
        return displayName
    }
}
