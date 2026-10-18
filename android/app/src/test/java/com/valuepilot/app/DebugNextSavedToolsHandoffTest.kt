package com.valuepilot.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugNextSavedToolsHandoffTest {

    @Test
    fun acceptsTheSavedToolsActionOnlyInDebugBuilds() {
        assertTrue(
            DebugNextSavedToolsHandoff.shouldOpenSavedTools(
                debugBuild = true,
                action = DebugNextSavedToolsHandoff.ACTION_OPEN_SAVED_TOOLS
            )
        )
        assertFalse(
            DebugNextSavedToolsHandoff.shouldOpenSavedTools(
                debugBuild = false,
                action = DebugNextSavedToolsHandoff.ACTION_OPEN_SAVED_TOOLS
            )
        )
    }

    @Test
    fun savedToolsHandoffResolvesToTheSavedPrimaryRoute() {
        assertEquals(
            AppPrimaryTab.SAVED,
            DebugNextSavedToolsHandoff.savedToolsPrimaryTabOrNull(
                debugBuild = true,
                action = DebugNextSavedToolsHandoff.ACTION_OPEN_SAVED_TOOLS
            )
        )
        assertNull(
            DebugNextSavedToolsHandoff.savedToolsPrimaryTabOrNull(
                debugBuild = false,
                action = DebugNextSavedToolsHandoff.ACTION_OPEN_SAVED_TOOLS
            )
        )
    }

    @Test
    fun ignoresUnrecognizedAndMissingActions() {
        assertFalse(
            DebugNextSavedToolsHandoff.shouldOpenSavedTools(
                debugBuild = true,
                action = "com.valuepilot.app.action.UNRELATED"
            )
        )
        assertFalse(
            DebugNextSavedToolsHandoff.shouldOpenSavedTools(
                debugBuild = true,
                action = null
            )
        )
    }

    @Test
    fun observedPriceConfirmationHandoffIsDebugOnly() {
        assertTrue(
            DebugNextSavedToolsHandoff.shouldOpenObservedPriceConfirmation(
                debugBuild = true,
                action = DebugNextSavedToolsHandoff.ACTION_OPEN_OBSERVED_PRICE_CONFIRMATION
            )
        )
        assertFalse(
            DebugNextSavedToolsHandoff.shouldOpenObservedPriceConfirmation(
                debugBuild = false,
                action = DebugNextSavedToolsHandoff.ACTION_OPEN_OBSERVED_PRICE_CONFIRMATION
            )
        )
        assertFalse(
            DebugNextSavedToolsHandoff.shouldOpenObservedPriceConfirmation(
                debugBuild = true,
                action = DebugNextSavedToolsHandoff.ACTION_OPEN_SAVED_TOOLS
            )
        )
    }
}
