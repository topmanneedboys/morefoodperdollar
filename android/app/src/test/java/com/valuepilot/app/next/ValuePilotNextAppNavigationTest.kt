package com.valuepilot.app.next

import com.valuepilot.app.AppPrimaryTab
import org.junit.Assert.assertEquals
import org.junit.Test

class ValuePilotNextAppNavigationTest {

    @Test
    fun `Compare Here keeps Saved selected when opened from private price history`() {
        assertEquals(
            AppPrimaryTab.SAVED,
            primaryTabForNextDestination(
                destination = NextDestination.COMPARE,
                originDestination = NextDestination.PRIVATE_PRICE_HISTORY
            )
        )
    }

    @Test
    fun `Compare Here keeps Home selected when opened from Home`() {
        assertEquals(
            AppPrimaryTab.HOME,
            primaryTabForNextDestination(
                destination = NextDestination.COMPARE,
                originDestination = NextDestination.HOME
            )
        )
    }

    @Test
    fun `Settings keeps the originating tab selected`() {
        assertEquals(
            AppPrimaryTab.SAVED,
            primaryTabForNextDestination(
                destination = NextDestination.SETTINGS,
                originDestination = NextDestination.PRIVATE_PRICE_HISTORY
            )
        )
        assertEquals(
            AppPrimaryTab.HOME,
            primaryTabForNextDestination(
                destination = NextDestination.SETTINGS,
                originDestination = NextDestination.HOME
            )
        )
    }

    @Test
    fun `a Saved handoff starts with Saved selected`() {
        assertEquals(
            AppPrimaryTab.SAVED,
            initialShellStateForNextDestination(NextDestination.SAVED).selectedPrimaryTab
        )
    }

    @Test
    fun `an external Saved tool handoff keeps Saved as the back destination`() {
        assertEquals(
            listOf(NextDestination.SAVED, NextDestination.PRIVATE_PRICE_HISTORY),
            initialRouteStackForNextDestination(
                destination = NextDestination.PRIVATE_PRICE_HISTORY,
                originDestination = NextDestination.SAVED
            )
        )
        assertEquals(
            AppPrimaryTab.SAVED,
            initialShellStateForNextDestination(
                destination = NextDestination.COMPARE,
                originDestination = NextDestination.SAVED
            ).selectedPrimaryTab
        )
    }

    @Test
    fun `an external Product Detail handoff keeps Search as the back destination`() {
        assertEquals(
            listOf(NextDestination.SEARCH, NextDestination.PRODUCT_DETAIL),
            initialRouteStackForNextDestination(
                destination = NextDestination.PRODUCT_DETAIL,
                originDestination = NextDestination.SEARCH
            )
        )
    }

    @Test
    fun `a duplicate origin does not add an extra route`() {
        assertEquals(
            listOf(NextDestination.SAVED),
            initialRouteStackForNextDestination(
                destination = NextDestination.SAVED,
                originDestination = NextDestination.SAVED
            )
        )
    }

    @Test
    fun `a fresh observed price attempt clears old form state before opening`() {
        val events = mutableListOf<String>()

        prepareAndOpenObservedPriceConfirmation(
            prepare = {
                events += "prepare"
                true
            },
            resetSavedFormState = { events += "reset" },
            navigate = { events += "navigate" }
        )

        assertEquals(listOf("prepare", "reset", "navigate"), events)
    }

    @Test
    fun `a rejected observed price attempt preserves current form state`() {
        val events = mutableListOf<String>()

        prepareAndOpenObservedPriceConfirmation(
            prepare = {
                events += "prepare"
                false
            },
            resetSavedFormState = { events += "reset" },
            navigate = { events += "navigate" }
        )

        assertEquals(listOf("prepare"), events)
    }
}
