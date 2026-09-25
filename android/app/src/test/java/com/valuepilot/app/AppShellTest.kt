package com.valuepilot.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AppShellTest {

    @Test
    fun initialStateStartsAtHome() {
        val state = AppShellState.initial()

        assertEquals(
            AppPrimaryTab.HOME,
            state.selectedPrimaryTab
        )

        assertEquals(
            AppRoute.HOME,
            state.route
        )

        assertEquals(
            null,
            state.compareReturnTab
        )

        assertFalse(
            state.canNavigateBack
        )
    }

    @Test
    fun everyPrimaryTabMapsToItsStableRoute() {
        val expected = mapOf(
            AppPrimaryTab.HOME to AppRoute.HOME,
            AppPrimaryTab.SEARCH to AppRoute.SEARCH,
            AppPrimaryTab.BASKET to AppRoute.BASKET,
            AppPrimaryTab.SAVED to AppRoute.SAVED
        )

        assertEquals(4, AppPrimaryTab.entries.size)

        for ((tab, route) in expected) {
            val state = AppShellReducer.reduce(
                AppShellState.initial(),
                AppShellIntent.SelectPrimary(tab)
            )

            assertEquals(
                tab,
                state.selectedPrimaryTab
            )

            assertEquals(
                route,
                state.route
            )

            assertEquals(
                null,
                state.compareReturnTab
            )

            assertFalse(
                state.canNavigateBack
            )
        }
    }

    @Test
    fun observedPriceSavedSelectionOpensOnlyFromSavedPrimaryRoute() {
        val saved = AppShellReducer.reduce(
            AppShellState.initial(),
            AppShellIntent.SelectPrimary(AppPrimaryTab.SAVED)
        )

        val selection = AppShellReducer.reduce(
            saved,
            AppShellIntent.OpenObservedPriceSavedSelection
        )

        assertEquals(AppPrimaryTab.SAVED, selection.selectedPrimaryTab)
        assertEquals(AppRoute.OBSERVED_PRICE_SAVED_SELECTION, selection.route)
        assertEquals(null, selection.compareReturnTab)
        assertTrue(selection.canNavigateBack)

        val home = AppShellState.initial()
        val fromHome = AppShellReducer.reduce(
            home,
            AppShellIntent.OpenObservedPriceSavedSelection
        )
        assertSame(home, fromHome)
    }

    @Test
    fun observedPriceSavedSelectionCannotOpenOverCompareEvenWhenSavedOwnsPrimaryTab() {
        val saved = AppShellReducer.reduce(
            AppShellState.initial(),
            AppShellIntent.SelectPrimary(AppPrimaryTab.SAVED)
        )
        val compare = AppShellReducer.reduce(saved, AppShellIntent.OpenStandaloneCompare)

        val attemptedSelection = AppShellReducer.reduce(
            compare,
            AppShellIntent.OpenObservedPriceSavedSelection
        )

        assertSame(compare, attemptedSelection)
    }

    @Test
    fun savedSubroutesCannotOpenOverEachOther() {
        val saved = AppShellReducer.reduce(
            AppShellState.initial(),
            AppShellIntent.SelectPrimary(AppPrimaryTab.SAVED)
        )
        val observedPrice = AppShellReducer.reduce(
            saved,
            AppShellIntent.OpenObservedPriceSavedSelection
        )
        val stapleAttempt = AppShellReducer.reduce(
            observedPrice,
            AppShellIntent.OpenStapleWatchSetup
        )
        assertSame(observedPrice, stapleAttempt)

        val staple = AppShellReducer.reduce(saved, AppShellIntent.OpenStapleWatchSetup)
        val observedPriceAttempt = AppShellReducer.reduce(
            staple,
            AppShellIntent.OpenObservedPriceSavedSelection
        )
        assertSame(staple, observedPriceAttempt)
    }

    @Test
    fun openingObservedPriceSavedSelectionTwiceIsIdempotent() {
        val saved = AppShellReducer.reduce(
            AppShellState.initial(),
            AppShellIntent.SelectPrimary(AppPrimaryTab.SAVED)
        )
        val first = AppShellReducer.reduce(
            saved,
            AppShellIntent.OpenObservedPriceSavedSelection
        )
        val second = AppShellReducer.reduce(
            first,
            AppShellIntent.OpenObservedPriceSavedSelection
        )

        assertSame(first, second)
    }

    @Test
    fun backFromObservedPriceSavedSelectionReturnsToSavedPrimaryRoute() {
        val saved = AppShellReducer.reduce(
            AppShellState.initial(),
            AppShellIntent.SelectPrimary(AppPrimaryTab.SAVED)
        )
        val selection = AppShellReducer.reduce(
            saved,
            AppShellIntent.OpenObservedPriceSavedSelection
        )

        val returned = AppShellReducer.reduce(selection, AppShellIntent.NavigateBack)

        assertEquals(AppPrimaryTab.SAVED, returned.selectedPrimaryTab)
        assertEquals(AppRoute.SAVED, returned.route)
        assertEquals(null, returned.compareReturnTab)
        assertFalse(returned.canNavigateBack)
    }

    @Test
    fun selectingPrimaryTabWhileObservedPriceSavedSelectionIsOpenExitsSubroute() {
        val saved = AppShellReducer.reduce(
            AppShellState.initial(),
            AppShellIntent.SelectPrimary(AppPrimaryTab.SAVED)
        )
        val selection = AppShellReducer.reduce(
            saved,
            AppShellIntent.OpenObservedPriceSavedSelection
        )

        val search = AppShellReducer.reduce(
            selection,
            AppShellIntent.SelectPrimary(AppPrimaryTab.SEARCH)
        )

        assertEquals(AppPrimaryTab.SEARCH, search.selectedPrimaryTab)
        assertEquals(AppRoute.SEARCH, search.route)
        assertEquals(null, search.compareReturnTab)
        assertFalse(search.canNavigateBack)
    }

    @Test
    fun compareOpenedFromObservedPriceSelectionReturnsToSavedNotSelection() {
        val saved = AppShellReducer.reduce(
            AppShellState.initial(),
            AppShellIntent.SelectPrimary(AppPrimaryTab.SAVED)
        )
        val selection = AppShellReducer.reduce(
            saved,
            AppShellIntent.OpenObservedPriceSavedSelection
        )

        val compare = AppShellReducer.reduce(selection, AppShellIntent.OpenStandaloneCompare)

        assertEquals(AppPrimaryTab.SAVED, compare.selectedPrimaryTab)
        assertEquals(AppRoute.COMPARE, compare.route)
        assertEquals(AppPrimaryTab.SAVED, compare.compareReturnTab)
        assertTrue(compare.canNavigateBack)

        val returned = AppShellReducer.reduce(compare, AppShellIntent.NavigateBack)
        assertEquals(AppPrimaryTab.SAVED, returned.selectedPrimaryTab)
        assertEquals(AppRoute.SAVED, returned.route)
        assertFalse(returned.canNavigateBack)
    }

    @Test
    fun stapleWatchSetupOpensOnlyFromSavedPrimaryRoute() {
        val saved = AppShellReducer.reduce(
            AppShellState.initial(),
            AppShellIntent.SelectPrimary(AppPrimaryTab.SAVED)
        )

        val setup = AppShellReducer.reduce(
            saved,
            AppShellIntent.OpenStapleWatchSetup
        )

        assertEquals(AppPrimaryTab.SAVED, setup.selectedPrimaryTab)
        assertEquals(AppRoute.STAPLE_WATCH_SETUP, setup.route)
        assertEquals(null, setup.compareReturnTab)
        assertTrue(setup.canNavigateBack)

        val home = AppShellState.initial()
        val fromHome = AppShellReducer.reduce(
            home,
            AppShellIntent.OpenStapleWatchSetup
        )
        assertSame(home, fromHome)
    }

    @Test
    fun stapleWatchSetupCannotOpenOverCompareEvenWhenSavedOwnsThePrimaryTab() {
        val saved = AppShellReducer.reduce(
            AppShellState.initial(),
            AppShellIntent.SelectPrimary(AppPrimaryTab.SAVED)
        )
        val compare = AppShellReducer.reduce(
            saved,
            AppShellIntent.OpenStandaloneCompare
        )

        val attemptedSetup = AppShellReducer.reduce(
            compare,
            AppShellIntent.OpenStapleWatchSetup
        )

        assertSame(compare, attemptedSetup)
    }

    @Test
    fun openingStapleWatchSetupTwiceIsIdempotent() {
        val saved = AppShellReducer.reduce(
            AppShellState.initial(),
            AppShellIntent.SelectPrimary(AppPrimaryTab.SAVED)
        )
        val first = AppShellReducer.reduce(saved, AppShellIntent.OpenStapleWatchSetup)
        val second = AppShellReducer.reduce(first, AppShellIntent.OpenStapleWatchSetup)

        assertSame(first, second)
    }

    @Test
    fun backFromStapleWatchSetupReturnsToSavedPrimaryRoute() {
        val saved = AppShellReducer.reduce(
            AppShellState.initial(),
            AppShellIntent.SelectPrimary(AppPrimaryTab.SAVED)
        )
        val setup = AppShellReducer.reduce(saved, AppShellIntent.OpenStapleWatchSetup)

        val returned = AppShellReducer.reduce(setup, AppShellIntent.NavigateBack)

        assertEquals(AppPrimaryTab.SAVED, returned.selectedPrimaryTab)
        assertEquals(AppRoute.SAVED, returned.route)
        assertEquals(null, returned.compareReturnTab)
        assertFalse(returned.canNavigateBack)
    }

    @Test
    fun selectingPrimaryTabWhileStapleWatchSetupIsOpenExitsSubroute() {
        val saved = AppShellReducer.reduce(
            AppShellState.initial(),
            AppShellIntent.SelectPrimary(AppPrimaryTab.SAVED)
        )
        val setup = AppShellReducer.reduce(saved, AppShellIntent.OpenStapleWatchSetup)

        val search = AppShellReducer.reduce(
            setup,
            AppShellIntent.SelectPrimary(AppPrimaryTab.SEARCH)
        )

        assertEquals(AppPrimaryTab.SEARCH, search.selectedPrimaryTab)
        assertEquals(AppRoute.SEARCH, search.route)
        assertEquals(null, search.compareReturnTab)
        assertFalse(search.canNavigateBack)
    }

    @Test
    fun compareOpenedFromStapleWatchSetupReturnsToSavedNotBackIntoSetup() {
        val saved = AppShellReducer.reduce(
            AppShellState.initial(),
            AppShellIntent.SelectPrimary(AppPrimaryTab.SAVED)
        )
        val setup = AppShellReducer.reduce(saved, AppShellIntent.OpenStapleWatchSetup)

        val compare = AppShellReducer.reduce(setup, AppShellIntent.OpenStandaloneCompare)

        assertEquals(AppPrimaryTab.SAVED, compare.selectedPrimaryTab)
        assertEquals(AppRoute.COMPARE, compare.route)
        assertEquals(AppPrimaryTab.SAVED, compare.compareReturnTab)
        assertTrue(compare.canNavigateBack)

        val returned = AppShellReducer.reduce(compare, AppShellIntent.NavigateBack)
        assertEquals(AppPrimaryTab.SAVED, returned.selectedPrimaryTab)
        assertEquals(AppRoute.SAVED, returned.route)
        assertFalse(returned.canNavigateBack)
    }

    @Test
    fun compareOpenedFromSearchRemembersSearchAsReturnDestination() {
        val search = AppShellReducer.reduce(
            AppShellState.initial(),
            AppShellIntent.SelectPrimary(
                AppPrimaryTab.SEARCH
            )
        )

        val compare = AppShellReducer.reduce(
            search,
            AppShellIntent.OpenStandaloneCompare
        )

        assertEquals(
            AppPrimaryTab.SEARCH,
            compare.selectedPrimaryTab
        )

        assertEquals(
            AppRoute.COMPARE,
            compare.route
        )

        assertEquals(
            AppPrimaryTab.SEARCH,
            compare.compareReturnTab
        )

        assertTrue(
            compare.canNavigateBack
        )
    }

    @Test
    fun backFromCompareReturnsToOriginTab() {
        for (origin in AppPrimaryTab.entries) {
            val primary = AppShellReducer.reduce(
                AppShellState.initial(),
                AppShellIntent.SelectPrimary(origin)
            )

            val compare = AppShellReducer.reduce(
                primary,
                AppShellIntent.OpenStandaloneCompare
            )

            val returned = AppShellReducer.reduce(
                compare,
                AppShellIntent.NavigateBack
            )

            assertEquals(
                origin,
                returned.selectedPrimaryTab
            )

            assertEquals(
                AppShellReducer.routeFor(origin),
                returned.route
            )

            assertEquals(
                null,
                returned.compareReturnTab
            )

            assertFalse(
                returned.canNavigateBack
            )
        }
    }

    @Test
    fun selectingPrimaryTabWhileCompareIsOpenExitsCompare() {
        val compare = AppShellReducer.reduce(
            AppShellState.initial(),
            AppShellIntent.OpenStandaloneCompare
        )

        val saved = AppShellReducer.reduce(
            compare,
            AppShellIntent.SelectPrimary(
                AppPrimaryTab.SAVED
            )
        )

        assertEquals(
            AppPrimaryTab.SAVED,
            saved.selectedPrimaryTab
        )

        assertEquals(
            AppRoute.SAVED,
            saved.route
        )

        assertEquals(
            null,
            saved.compareReturnTab
        )

        assertFalse(
            saved.canNavigateBack
        )
    }

    @Test
    fun backAtPrimaryDestinationIsNoOp() {
        val basket = AppShellReducer.reduce(
            AppShellState.initial(),
            AppShellIntent.SelectPrimary(
                AppPrimaryTab.BASKET
            )
        )

        val afterBack = AppShellReducer.reduce(
            basket,
            AppShellIntent.NavigateBack
        )

        assertSame(
            basket,
            afterBack
        )
    }

    @Test
    fun openingCompareTwiceIsIdempotent() {
        val first = AppShellReducer.reduce(
            AppShellState.initial(),
            AppShellIntent.OpenStandaloneCompare
        )

        val second = AppShellReducer.reduce(
            first,
            AppShellIntent.OpenStandaloneCompare
        )

        assertSame(
            first,
            second
        )
    }

    @Test
    fun reducerDoesNotMutatePreviousState() {
        val previous = AppShellState.initial()

        val next = AppShellReducer.reduce(
            previous,
            AppShellIntent.SelectPrimary(
                AppPrimaryTab.SEARCH
            )
        )

        assertEquals(
            AppRoute.HOME,
            previous.route
        )

        assertEquals(
            AppPrimaryTab.HOME,
            previous.selectedPrimaryTab
        )

        assertEquals(
            AppRoute.SEARCH,
            next.route
        )
    }
}
