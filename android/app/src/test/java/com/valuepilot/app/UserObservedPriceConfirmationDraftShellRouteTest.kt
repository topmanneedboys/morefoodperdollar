package com.valuepilot.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class UserObservedPriceConfirmationDraftShellRouteTest {

    @Test
    fun `confirmation draft opens only from observed price Saved selection`() {
        val saved = AppShellReducer.reduce(
            AppShellState.initial(),
            AppShellIntent.SelectPrimary(AppPrimaryTab.SAVED)
        )
        val selection = AppShellReducer.reduce(
            saved,
            AppShellIntent.OpenObservedPriceSavedSelection
        )

        val draft = AppShellReducer.reduce(
            selection,
            AppShellIntent.OpenObservedPriceConfirmationDraft
        )

        assertEquals(AppPrimaryTab.SAVED, draft.selectedPrimaryTab)
        assertEquals(AppRoute.OBSERVED_PRICE_CONFIRMATION_DRAFT, draft.route)
        assertEquals(null, draft.compareReturnTab)
        assertTrue(draft.canNavigateBack)

        val fromSaved = AppShellReducer.reduce(
            saved,
            AppShellIntent.OpenObservedPriceConfirmationDraft
        )
        assertSame(saved, fromSaved)

        val home = AppShellState.initial()
        val fromHome = AppShellReducer.reduce(
            home,
            AppShellIntent.OpenObservedPriceConfirmationDraft
        )
        assertSame(home, fromHome)
    }

    @Test
    fun `opening confirmation draft twice is idempotent`() {
        val saved = AppShellReducer.reduce(
            AppShellState.initial(),
            AppShellIntent.SelectPrimary(AppPrimaryTab.SAVED)
        )
        val selection = AppShellReducer.reduce(
            saved,
            AppShellIntent.OpenObservedPriceSavedSelection
        )
        val first = AppShellReducer.reduce(
            selection,
            AppShellIntent.OpenObservedPriceConfirmationDraft
        )
        val second = AppShellReducer.reduce(
            first,
            AppShellIntent.OpenObservedPriceConfirmationDraft
        )

        assertSame(first, second)
    }

    @Test
    fun `back from confirmation draft returns to selection then Saved`() {
        val saved = AppShellReducer.reduce(
            AppShellState.initial(),
            AppShellIntent.SelectPrimary(AppPrimaryTab.SAVED)
        )
        val selection = AppShellReducer.reduce(
            saved,
            AppShellIntent.OpenObservedPriceSavedSelection
        )
        val draft = AppShellReducer.reduce(
            selection,
            AppShellIntent.OpenObservedPriceConfirmationDraft
        )

        val returnedToSelection = AppShellReducer.reduce(draft, AppShellIntent.NavigateBack)
        assertEquals(AppPrimaryTab.SAVED, returnedToSelection.selectedPrimaryTab)
        assertEquals(AppRoute.OBSERVED_PRICE_SAVED_SELECTION, returnedToSelection.route)
        assertTrue(returnedToSelection.canNavigateBack)

        val returnedToSaved = AppShellReducer.reduce(
            returnedToSelection,
            AppShellIntent.NavigateBack
        )
        assertEquals(AppPrimaryTab.SAVED, returnedToSaved.selectedPrimaryTab)
        assertEquals(AppRoute.SAVED, returnedToSaved.route)
        assertFalse(returnedToSaved.canNavigateBack)
    }

    @Test
    fun `selecting a primary tab exits confirmation draft`() {
        val saved = AppShellReducer.reduce(
            AppShellState.initial(),
            AppShellIntent.SelectPrimary(AppPrimaryTab.SAVED)
        )
        val selection = AppShellReducer.reduce(
            saved,
            AppShellIntent.OpenObservedPriceSavedSelection
        )
        val draft = AppShellReducer.reduce(
            selection,
            AppShellIntent.OpenObservedPriceConfirmationDraft
        )

        val search = AppShellReducer.reduce(
            draft,
            AppShellIntent.SelectPrimary(AppPrimaryTab.SEARCH)
        )

        assertEquals(AppPrimaryTab.SEARCH, search.selectedPrimaryTab)
        assertEquals(AppRoute.SEARCH, search.route)
        assertEquals(null, search.compareReturnTab)
        assertFalse(search.canNavigateBack)
    }
}
