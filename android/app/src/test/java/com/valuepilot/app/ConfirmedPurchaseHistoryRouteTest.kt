package com.valuepilot.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfirmedPurchaseHistoryRouteTest {

    @Test
    fun `history is a Saved-owned route and returns to Saved`() {
        val saved =
            AppShellReducer.reduce(
                AppShellState.initial(),
                AppShellIntent.SelectPrimary(AppPrimaryTab.SAVED)
            )
        val history =
            AppShellReducer.reduce(saved, AppShellIntent.OpenConfirmedPurchaseHistory)

        assertEquals(AppPrimaryTab.SAVED, history.selectedPrimaryTab)
        assertEquals(AppRoute.CONFIRMED_PURCHASE_HISTORY, history.route)
        assertTrue(history.canNavigateBack)
        assertEquals(AppRoute.SAVED, AppShellReducer.reduce(history, AppShellIntent.NavigateBack).route)
    }

    @Test
    fun `history cannot be opened from another primary tab`() {
        val home = AppShellState.initial()
        val unchanged =
            AppShellReducer.reduce(home, AppShellIntent.OpenConfirmedPurchaseHistory)

        assertEquals(home, unchanged)
        assertNotEquals(AppRoute.CONFIRMED_PURCHASE_HISTORY, unchanged.route)
    }
}
