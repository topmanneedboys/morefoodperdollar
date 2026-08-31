package com.valuepilot.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AppShellStapleWatchResultRouteTest {

    @Test
    fun resultRouteOpensOnlyFromStapleWatchPolicy() {
        val policy = policyState()

        val result = AppShellReducer.reduce(policy, AppShellIntent.OpenStapleWatchResult)

        assertEquals(AppPrimaryTab.SAVED, result.selectedPrimaryTab)
        assertEquals(AppRoute.STAPLE_WATCH_RESULT, result.route)
        assertEquals(null, result.compareReturnTab)
        assertTrue(result.canNavigateBack)

        val setup = setupState()
        assertSame(setup, AppShellReducer.reduce(setup, AppShellIntent.OpenStapleWatchResult))

        val saved = savedState()
        assertSame(saved, AppShellReducer.reduce(saved, AppShellIntent.OpenStapleWatchResult))

        val home = AppShellState.initial()
        assertSame(home, AppShellReducer.reduce(home, AppShellIntent.OpenStapleWatchResult))
    }

    @Test
    fun openingResultRouteTwiceIsIdempotent() {
        val first = AppShellReducer.reduce(policyState(), AppShellIntent.OpenStapleWatchResult)
        val second = AppShellReducer.reduce(first, AppShellIntent.OpenStapleWatchResult)

        assertSame(first, second)
    }

    @Test
    fun backTraversesResultPolicySetupAndSavedOneStepAtATime() {
        val result = AppShellReducer.reduce(policyState(), AppShellIntent.OpenStapleWatchResult)

        val policy = AppShellReducer.reduce(result, AppShellIntent.NavigateBack)
        assertEquals(AppPrimaryTab.SAVED, policy.selectedPrimaryTab)
        assertEquals(AppRoute.STAPLE_WATCH_POLICY, policy.route)
        assertEquals(null, policy.compareReturnTab)
        assertTrue(policy.canNavigateBack)

        val setup = AppShellReducer.reduce(policy, AppShellIntent.NavigateBack)
        assertEquals(AppPrimaryTab.SAVED, setup.selectedPrimaryTab)
        assertEquals(AppRoute.STAPLE_WATCH_SETUP, setup.route)
        assertEquals(null, setup.compareReturnTab)
        assertTrue(setup.canNavigateBack)

        val saved = AppShellReducer.reduce(setup, AppShellIntent.NavigateBack)
        assertEquals(AppPrimaryTab.SAVED, saved.selectedPrimaryTab)
        assertEquals(AppRoute.SAVED, saved.route)
        assertEquals(null, saved.compareReturnTab)
        assertFalse(saved.canNavigateBack)
    }

    @Test
    fun selectingPrimaryTabWhileResultIsOpenExitsTheWatchSubroute() {
        val result = AppShellReducer.reduce(policyState(), AppShellIntent.OpenStapleWatchResult)

        val search =
            AppShellReducer.reduce(
                result,
                AppShellIntent.SelectPrimary(AppPrimaryTab.SEARCH)
            )

        assertEquals(AppPrimaryTab.SEARCH, search.selectedPrimaryTab)
        assertEquals(AppRoute.SEARCH, search.route)
        assertEquals(null, search.compareReturnTab)
        assertFalse(search.canNavigateBack)
    }

    @Test
    fun compareOpenedFromResultReturnsToSavedPrimaryRouteNotInternalWatchStep() {
        val result = AppShellReducer.reduce(policyState(), AppShellIntent.OpenStapleWatchResult)
        val compare = AppShellReducer.reduce(result, AppShellIntent.OpenStandaloneCompare)

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
    fun resultCannotOpenOverCompareEvenWhenSavedOwnsPrimaryTab() {
        val compare =
            AppShellReducer.reduce(
                policyState(),
                AppShellIntent.OpenStandaloneCompare
            )

        assertSame(compare, AppShellReducer.reduce(compare, AppShellIntent.OpenStapleWatchResult))
    }

    @Test
    fun resultRouteAddsNoNewPrimaryTabOrBusinessAuthority() {
        assertEquals(4, AppPrimaryTab.entries.size)
        assertTrue(AppRoute.entries.contains(AppRoute.STAPLE_WATCH_RESULT))
    }

    private fun policyState(): AppShellState =
        AppShellReducer.reduce(setupState(), AppShellIntent.OpenStapleWatchPolicy)

    private fun setupState(): AppShellState =
        AppShellReducer.reduce(savedState(), AppShellIntent.OpenStapleWatchSetup)

    private fun savedState(): AppShellState =
        AppShellReducer.reduce(
            AppShellState.initial(),
            AppShellIntent.SelectPrimary(AppPrimaryTab.SAVED)
        )
}
