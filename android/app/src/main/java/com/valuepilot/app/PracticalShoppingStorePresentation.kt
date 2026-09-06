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
internal const val PRACTICAL_SHOPPING_PLANNED_STORE_NOTICE =
    "Planned stop only — product availability is not confirmed."

internal fun practicalShoppingPlannedStoreLabel(storeName: String): String {
    require(storeName.isNotBlank())
    require(storeName.none { character -> character.isISOControl() })
    return "Planned store: $storeName"
}

internal fun practicalShoppingPlannedStoreAccessibility(storeName: String): String =
    practicalShoppingPlannedStoreLabel(storeName) +
        ". " +
        PRACTICAL_SHOPPING_PLANNED_STORE_NOTICE
