package com.valuepilot.app

/**
 * The single presentation readiness gate shared by Search's visible button
 * and keyboard/quick-entry submission paths.
 *
 * This only reflects immutable controller state. It does not start, cancel or
 * otherwise own a search request.
 */
internal fun practicalShoppingSearchSubmitEnabled(
    state: UniversalSearchState,
    rawQuery: String = state.query
): Boolean =
    rawQuery.isNotBlank() &&
        state.status != UniversalSearchStatus.LOADING &&
        state.status != UniversalSearchStatus.QUERY_TOO_LONG

/**
 * Prevents a quick-entry chip from restarting the exact request already in
 * flight. A different quick query remains an explicit replacement choice.
 */
internal fun practicalShoppingSearchQuickEntryBlocked(
    state: UniversalSearchState,
    rawQuery: String
): Boolean =
    state.status == UniversalSearchStatus.LOADING &&
        state.query == rawQuery

/**
 * Mirrors the quick-entry click guard in the physical chip state. A different
 * quick query remains enabled during loading because it is an explicit
 * replacement choice.
 */
internal fun practicalShoppingSearchQuickEntryEnabled(
    state: UniversalSearchState,
    rawQuery: String
): Boolean = !practicalShoppingSearchQuickEntryBlocked(state, rawQuery)
