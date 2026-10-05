package com.valuepilot.app

import org.junit.Assert.assertEquals
import org.junit.Test

class AppShellStateRestorationTest {

    @Test
    fun `saved history route survives recreation`() {
        val restored =
            AppShellStateRestoration.restore(
                primaryTabName = AppPrimaryTab.SAVED.name,
                routeName = AppRoute.CONFIRMED_PURCHASE_HISTORY.name
            )

        assertEquals(AppPrimaryTab.SAVED, restored.selectedPrimaryTab)
        assertEquals(AppRoute.CONFIRMED_PURCHASE_HISTORY, restored.route)
        assertEquals(true, restored.canNavigateBack)
    }

    @Test
    fun `saved watching route survives recreation`() {
        val restored =
            AppShellStateRestoration.restore(
                primaryTabName = AppPrimaryTab.SAVED.name,
                routeName = AppRoute.PRICE_WATCHES.name
            )

        assertEquals(AppRoute.PRICE_WATCHES, restored.route)
        assertEquals(true, restored.canNavigateBack)
    }

    @Test
    fun `transient or malformed routes fall back to the selected primary tab`() {
        val transient =
            AppShellStateRestoration.restore(
                primaryTabName = AppPrimaryTab.SAVED.name,
                routeName = AppRoute.OBSERVED_PRICE_CONFIRMATION_DRAFT.name
            )
        val malformed =
            AppShellStateRestoration.restore(
                primaryTabName = AppPrimaryTab.SAVED.name,
                routeName = "not-a-route"
            )

        assertEquals(AppRoute.SAVED, transient.route)
        assertEquals(AppRoute.SAVED, malformed.route)
        assertEquals(false, transient.canNavigateBack)
    }

    @Test
    fun `subroute cannot override a different selected primary tab`() {
        val restored =
            AppShellStateRestoration.restore(
                primaryTabName = AppPrimaryTab.SEARCH.name,
                routeName = AppRoute.PRICE_WATCHES.name
            )

        assertEquals(AppPrimaryTab.SEARCH, restored.selectedPrimaryTab)
        assertEquals(AppRoute.SEARCH, restored.route)
    }

    @Test
    fun `only stable personal routes are serialized`() {
        assertEquals(
            AppRoute.PRICE_WATCHES,
            AppShellStateRestoration.restorableRoute(
                AppShellState(
                    selectedPrimaryTab = AppPrimaryTab.SAVED,
                    route = AppRoute.PRICE_WATCHES,
                    compareReturnTab = null,
                    canNavigateBack = true
                )
            )
        )
        assertEquals(
            AppRoute.SAVED,
            AppShellStateRestoration.restorableRoute(
                AppShellState(
                    selectedPrimaryTab = AppPrimaryTab.SAVED,
                    route = AppRoute.STAPLE_WATCH_SETUP,
                    compareReturnTab = null,
                    canNavigateBack = true
                )
            )
        )
        assertEquals(
            AppRoute.BASKET,
            AppShellStateRestoration.restorableRoute(
                AppShellState(
                    selectedPrimaryTab = AppPrimaryTab.BASKET,
                    route = AppRoute.COMPARE,
                    compareReturnTab = AppPrimaryTab.BASKET,
                    canNavigateBack = true
                )
            )
        )
    }
}
