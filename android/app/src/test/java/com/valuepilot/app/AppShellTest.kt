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
