package com.valuepilot.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AppShellStapleWatchPolicyRouteTest {

    @Test
    fun policyRouteOpensOnlyFromStapleWatchSetup() {
        val saved =
            AppShellReducer.reduce(
                AppShellState.initial(),
                AppShellIntent.SelectPrimary(AppPrimaryTab.SAVED)
            )
        val setup = AppShellReducer.reduce(saved, AppShellIntent.OpenStapleWatchSetup)

        val policy = AppShellReducer.reduce(setup, AppShellIntent.OpenStapleWatchPolicy)

        assertEquals(AppPrimaryTab.SAVED, policy.selectedPrimaryTab)
        assertEquals(AppRoute.STAPLE_WATCH_POLICY, policy.route)
        assertEquals(null, policy.compareReturnTab)
        assertTrue(policy.canNavigateBack)

        val fromSaved = AppShellReducer.reduce(saved, AppShellIntent.OpenStapleWatchPolicy)
        assertSame(saved, fromSaved)

        val home = AppShellState.initial()
        val fromHome = AppShellReducer.reduce(home, AppShellIntent.OpenStapleWatchPolicy)
        assertSame(home, fromHome)
    }

    @Test
    fun openingPolicyRouteTwiceIsIdempotent() {
        val setup = setupState()
        val first = AppShellReducer.reduce(setup, AppShellIntent.OpenStapleWatchPolicy)
        val second = AppShellReducer.reduce(first, AppShellIntent.OpenStapleWatchPolicy)

        assertSame(first, second)
    }

    @Test
    fun backFromPolicyReturnsOneStepToStapleWatchSetup() {
        val policy =
            AppShellReducer.reduce(
                setupState(),
                AppShellIntent.OpenStapleWatchPolicy
            )

        val returned = AppShellReducer.reduce(policy, AppShellIntent.NavigateBack)

        assertEquals(AppPrimaryTab.SAVED, returned.selectedPrimaryTab)
        assertEquals(AppRoute.STAPLE_WATCH_SETUP, returned.route)
        assertEquals(null, returned.compareReturnTab)
        assertTrue(returned.canNavigateBack)

        val saved = AppShellReducer.reduce(returned, AppShellIntent.NavigateBack)
        assertEquals(AppRoute.SAVED, saved.route)
        assertFalse(saved.canNavigateBack)
    }

    @Test
    fun selectingPrimaryTabWhilePolicyIsOpenExitsTheWatchSubroute() {
        val policy =
            AppShellReducer.reduce(
                setupState(),
                AppShellIntent.OpenStapleWatchPolicy
            )

        val search =
            AppShellReducer.reduce(
                policy,
                AppShellIntent.SelectPrimary(AppPrimaryTab.SEARCH)
            )

        assertEquals(AppPrimaryTab.SEARCH, search.selectedPrimaryTab)
        assertEquals(AppRoute.SEARCH, search.route)
        assertEquals(null, search.compareReturnTab)
        assertFalse(search.canNavigateBack)
    }

    @Test
    fun compareOpenedFromPolicyReturnsToSavedPrimaryRouteNotInternalWatchStep() {
        val policy =
            AppShellReducer.reduce(
                setupState(),
                AppShellIntent.OpenStapleWatchPolicy
            )
        val compare = AppShellReducer.reduce(policy, AppShellIntent.OpenStandaloneCompare)

        assertEquals(AppPrimaryTab.SAVED, compare.selectedPrimaryTab)
        assertEquals(AppRoute.COMPARE, compare.route)
        assertEquals(AppPrimaryTab.SAVED, compare.compareReturnTab)
        assertTrue(compare.canNavigateBack)

        val returned = AppShellReducer.reduce(compare, AppShellIntent.NavigateBack)
        assertEquals(AppPrimaryTab.SAVED, returned.selectedPrimaryTab)
        assertEquals(AppRoute.SAVED, returned.route)
        assertEquals(null, returned.compareReturnTab)
        assertFalse(returned.canNavigateBack)
    }

    @Test
    fun policyCannotOpenOverCompareEvenWhenSavedOwnsPrimaryTab() {
        val compare =
            AppShellReducer.reduce(
                setupState(),
                AppShellIntent.OpenStandaloneCompare
            )

        val attemptedPolicy =
            AppShellReducer.reduce(compare, AppShellIntent.OpenStapleWatchPolicy)

        assertSame(compare, attemptedPolicy)
    }

    @Test
    fun policyRouteAddsNoNewPrimaryTab() {
        assertEquals(4, AppPrimaryTab.entries.size)
        assertTrue(AppRoute.entries.contains(AppRoute.STAPLE_WATCH_POLICY))
    }

    private fun setupState(): AppShellState {
        val saved =
            AppShellReducer.reduce(
                AppShellState.initial(),
                AppShellIntent.SelectPrimary(AppPrimaryTab.SAVED)
            )
        return AppShellReducer.reduce(saved, AppShellIntent.OpenStapleWatchSetup)
    }
}
