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
}
