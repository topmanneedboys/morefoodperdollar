package com.valuepilot.app.next

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ValuePilotNextUiPreferencesTest {

    @Test
    fun `missing or invalid values fall back to system preferences`() {
        assertEquals(
            ValuePilotNextUiPreferences(),
            ValuePilotNextUiPreferencesCodec.decode(appearance = null, motion = null)
        )
        assertEquals(
            ValuePilotNextUiPreferences(),
            ValuePilotNextUiPreferencesCodec.decode(appearance = "BROKEN", motion = "UNKNOWN")
        )
    }

    @Test
    fun `appearance choice overrides system only when explicitly selected`() {
        assertTrue(
            ValuePilotNextUiPreferencesCodec.resolvesDarkTheme(
                ValuePilotNextAppearance.SYSTEM,
                systemDarkTheme = true
            )
        )
        assertFalse(
            ValuePilotNextUiPreferencesCodec.resolvesDarkTheme(
                ValuePilotNextAppearance.LIGHT,
                systemDarkTheme = true
            )
        )
        assertTrue(
            ValuePilotNextUiPreferencesCodec.resolvesDarkTheme(
                ValuePilotNextAppearance.DARK,
                systemDarkTheme = false
            )
        )
    }

    @Test
    fun `motion preference can reduce or retain system animations`() {
        assertTrue(
            ValuePilotNextUiPreferencesCodec.resolvesReducedMotion(
                ValuePilotNextMotionPreference.SYSTEM,
                systemReducedMotion = true
            )
        )
        assertTrue(
            ValuePilotNextUiPreferencesCodec.resolvesReducedMotion(
                ValuePilotNextMotionPreference.REDUCED,
                systemReducedMotion = false
            )
        )
        assertFalse(
            ValuePilotNextUiPreferencesCodec.resolvesReducedMotion(
                ValuePilotNextMotionPreference.FULL,
                systemReducedMotion = true
            )
        )
    }
}
