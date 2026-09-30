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

    @Test
    fun `price watch is a Saved-owned route and returns to Saved`() {
        val saved =
            AppShellReducer.reduce(
                AppShellState.initial(),
                AppShellIntent.SelectPrimary(AppPrimaryTab.SAVED)
            )
        val watches = AppShellReducer.reduce(saved, AppShellIntent.OpenPriceWatches)

        assertEquals(AppPrimaryTab.SAVED, watches.selectedPrimaryTab)
        assertEquals(AppRoute.PRICE_WATCHES, watches.route)
        assertTrue(watches.canNavigateBack)
        assertEquals(AppRoute.SAVED, AppShellReducer.reduce(watches, AppShellIntent.NavigateBack).route)
    }

    @Test
    fun `price watch cannot be opened from Home`() {
        val home = AppShellState.initial()
        val unchanged = AppShellReducer.reduce(home, AppShellIntent.OpenPriceWatches)

        assertEquals(home, unchanged)
        assertNotEquals(AppRoute.PRICE_WATCHES, unchanged.route)
    }

    @Test
    fun `trip planner is a Basket-owned route and returns to Basket`() {
        val basket =
            AppShellReducer.reduce(
                AppShellState.initial(),
                AppShellIntent.SelectPrimary(AppPrimaryTab.BASKET)
            )
        val trip = AppShellReducer.reduce(basket, AppShellIntent.OpenTripPlanner)

        assertEquals(AppPrimaryTab.BASKET, trip.selectedPrimaryTab)
        assertEquals(AppRoute.TRIP_PLANNER, trip.route)
        assertTrue(trip.canNavigateBack)
        assertEquals(AppRoute.BASKET, AppShellReducer.reduce(trip, AppShellIntent.NavigateBack).route)
    }

    @Test
    fun `trip planner cannot be opened from Saved`() {
        val saved =
            AppShellReducer.reduce(
                AppShellState.initial(),
                AppShellIntent.SelectPrimary(AppPrimaryTab.SAVED)
            )
        val unchanged = AppShellReducer.reduce(saved, AppShellIntent.OpenTripPlanner)

        assertEquals(saved, unchanged)
        assertNotEquals(AppRoute.TRIP_PLANNER, unchanged.route)
    }
}
