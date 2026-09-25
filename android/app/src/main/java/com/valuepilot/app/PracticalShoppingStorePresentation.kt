package com.valuepilot.app

/**
 * Presentation-only wording for a store assignment that already came from an
 * upstream plan projection.
 *
 * A planned stop is not a stock or availability claim. Keeping this wording in
 * one helper prevents Home, Basket, share cards and the future production row
 * from drifting into "available here" language while leaving all shopping
 * authority in the planner and evidence layers.
 */
internal const val VALUEPILOT_STORE_AVAILABILITY_NOTICE =
    "product availability is not confirmed."

internal const val PRACTICAL_SHOPPING_PLANNED_STORE_NOTICE =
    "Planned stop only — $VALUEPILOT_STORE_AVAILABILITY_NOTICE"

private const val MAX_SAFE_STORE_PRESENTATION_CHARS = 160

private fun requireSafeStorePresentationName(storeName: String) {
    require(storeName.isNotBlank())
    require(storeName.length <= MAX_SAFE_STORE_PRESENTATION_CHARS)
    require(storeName.none { character -> character.isISOControl() })
}

internal fun practicalShoppingPlannedStoreLabel(storeName: String): String {
    requireSafeStorePresentationName(storeName)
    return "Planned store: $storeName"
}

internal fun practicalShoppingPlannedStoreAccessibility(storeName: String): String =
    practicalShoppingPlannedStoreLabel(storeName) +
        ". " +
        PRACTICAL_SHOPPING_PLANNED_STORE_NOTICE

internal fun valuePilotCandidateStoreLabel(storeName: String): String {
    requireSafeStorePresentationName(storeName)
    return "Candidate store: $storeName"
}

internal fun valuePilotCandidateStoreAccessibility(storeName: String): String =
    valuePilotCandidateStoreLabel(storeName) +
        ". Candidate store only — " +
        VALUEPILOT_STORE_AVAILABILITY_NOTICE
