package com.valuepilot.app.next

import org.junit.Assert.assertEquals
import org.junit.Test

class ValuePilotNextHtmlPreviewAccessibilityTest {

    @Test
    fun `web preview keeps default geometry but follows larger font scales`() {
        assertEquals(100, valuePilotNextWebTextZoomPercent(1f))
        assertEquals(125, valuePilotNextWebTextZoomPercent(1.25f))
        assertEquals(200, valuePilotNextWebTextZoomPercent(2f))
    }

    @Test
    fun `web preview clamps invalid or reduced font scales to the designed minimum`() {
        assertEquals(100, valuePilotNextWebTextZoomPercent(0.8f))
        assertEquals(100, valuePilotNextWebTextZoomPercent(Float.NaN))
        assertEquals(100, valuePilotNextWebTextZoomPercent(Float.POSITIVE_INFINITY))
    }

    @Test
    fun `web preview converts physical system insets to css pixels`() {
        assertEquals(32, valuePilotNextWebCssInsetPx(96, 3f))
        assertEquals(96, valuePilotNextWebCssInsetPx(96, 1f))
        assertEquals(96, valuePilotNextWebCssInsetPx(96, 0f))
        assertEquals(0, valuePilotNextWebCssInsetPx(-1, 3f))
    }
}
