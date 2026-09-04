package com.valuepilot.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticalShoppingSearchSurfacePresentationTest {

    private val controller = UniversalSearchController()

    @Test
    fun blankAndWhitespaceQueriesCannotSubmit() {
        val initial = controller.initialState()
        val ready =
            controller.reduce(
                initial,
                UniversalSearchIntent.QueryChanged("eggs")
            ).state

        assertFalse(practicalShoppingSearchSubmitEnabled(initial))
        assertFalse(practicalShoppingSearchSubmitEnabled(ready, ""))
        assertFalse(practicalShoppingSearchSubmitEnabled(ready, "  "))
    }

    @Test
    fun loadingSearchCannotBeSubmittedAgain() {
        val ready =
            controller.reduce(
                controller.initialState(),
                UniversalSearchIntent.QueryChanged("eggs")
            ).state
        val loading =
            controller.reduce(
                ready,
                UniversalSearchIntent.Submit
            ).state

        assertTrue(loading.status == UniversalSearchStatus.LOADING)
        assertFalse(practicalShoppingSearchSubmitEnabled(loading))
        assertFalse(practicalShoppingSearchSubmitEnabled(loading, "eggs"))
    }

    @Test
    fun readyQueryAndOverlongQueryUseTheSameGate() {
        val initial = controller.initialState()
        val ready =
            controller.reduce(
                initial,
                UniversalSearchIntent.QueryChanged("eggs")
            ).state
        val tooLong =
            controller.reduce(
                initial,
                UniversalSearchIntent.QueryChanged(
                    "x".repeat(UniversalSearchController.MAX_QUERY_CHARS + 1)
                )
            ).state

        assertTrue(practicalShoppingSearchSubmitEnabled(ready))
        assertFalse(practicalShoppingSearchSubmitEnabled(tooLong))
    }

    @Test
    fun offlineIdentityLookupUsesTheSameSafeQueryBoundary() {
        val initial = controller.initialState()
        val ready =
            controller.reduce(
                initial,
                UniversalSearchIntent.QueryChanged("milk")
            ).state
        val loading =
            controller.reduce(
                ready,
                UniversalSearchIntent.Submit
            ).state
        val tooLong =
            controller.reduce(
                initial,
                UniversalSearchIntent.QueryChanged(
                    "x".repeat(UniversalSearchController.MAX_QUERY_CHARS + 1)
                )
            ).state

        assertFalse(practicalShoppingSearchIdentityEnabled(initial))
        assertTrue(practicalShoppingSearchIdentityEnabled(ready))
        assertFalse(practicalShoppingSearchIdentityEnabled(loading))
        assertFalse(practicalShoppingSearchIdentityEnabled(tooLong))
    }

    @Test
    fun identicalQuickEntryIsBlockedOnlyWhileThatQueryIsLoading() {
        val ready =
            controller.reduce(
                controller.initialState(),
                UniversalSearchIntent.QueryChanged("eggs")
            ).state
        val loading =
            controller.reduce(
                ready,
                UniversalSearchIntent.Submit
            ).state

        assertTrue(practicalShoppingSearchQuickEntryBlocked(loading, "eggs"))
        assertFalse(practicalShoppingSearchQuickEntryBlocked(loading, "milk"))
        assertFalse(practicalShoppingSearchQuickEntryBlocked(ready, "eggs"))
    }

    @Test
    fun quickEntryEnabledStateMatchesTheClickGuard() {
        val ready =
            controller.reduce(
                controller.initialState(),
                UniversalSearchIntent.QueryChanged("eggs")
            ).state
        val loading =
            controller.reduce(
                ready,
                UniversalSearchIntent.Submit
            ).state

        assertFalse(practicalShoppingSearchQuickEntryEnabled(loading, "eggs"))
        assertTrue(practicalShoppingSearchQuickEntryEnabled(loading, "milk"))
        assertTrue(practicalShoppingSearchQuickEntryEnabled(ready, "eggs"))
    }
}
