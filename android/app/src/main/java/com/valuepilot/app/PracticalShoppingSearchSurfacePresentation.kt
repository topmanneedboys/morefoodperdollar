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
