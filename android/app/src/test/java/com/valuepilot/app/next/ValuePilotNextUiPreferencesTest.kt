package com.valuepilot.app.next

import androidx.compose.ui.unit.dp
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
            ValuePilotNextUiPreferencesCodec.decode(
                appearance = "BROKEN",
                motion = "UNKNOWN",
                density = "WIDE"
            )
        )
    }

    @Test
    fun `density preference decodes compact and comfortable values`() {
        assertEquals(
            ValuePilotNextDensity.COMPACT,
            ValuePilotNextUiPreferencesCodec.decode(
                appearance = null,
                motion = null,
                density = "COMPACT"
            ).density
        )
        assertEquals(
            ValuePilotNextDensity.COMFORTABLE,
            ValuePilotNextUiPreferencesCodec.decode(
                appearance = null,
                motion = null,
                density = null
            ).density
        )
    }

    @Test
    fun `preference reset is offered only after a choice differs from defaults`() {
        assertFalse(shouldOfferPresentationPreferenceReset(ValuePilotNextUiPreferences()))
        assertTrue(
            shouldOfferPresentationPreferenceReset(
                ValuePilotNextUiPreferences(density = ValuePilotNextDensity.COMPACT)
            )
        )
    }

    @Test
    fun `compact spacing preserves touch targets while fitting more content`() {
        val comfortable = valuePilotSpacingScaleFor(ValuePilotNextDensity.COMFORTABLE)
        val compact = valuePilotSpacingScaleFor(ValuePilotNextDensity.COMPACT)

        assertTrue(compact.page < comfortable.page)
        assertTrue(compact.section < comfortable.section)
        assertTrue(compact.medium < comfortable.medium)
        assertEquals(48.dp, comfortable.minimumTouchTarget)
        assertEquals(comfortable.minimumTouchTarget, compact.minimumTouchTarget)
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
