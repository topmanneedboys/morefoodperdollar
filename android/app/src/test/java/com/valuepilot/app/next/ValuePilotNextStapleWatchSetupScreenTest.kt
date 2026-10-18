package com.valuepilot.app.next

import com.valuepilot.app.StapleWatchForegroundFactCheckCapability
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ValuePilotNextStapleWatchSetupScreenTest {

    @Test
    fun `configured Compose checks keep the setup controls available`() {
        assertFalse(
            requiresClassicStapleWatchFallback(
                StapleWatchForegroundFactCheckCapability.CONFIGURED
            )
        )
    }

    @Test
    fun `unconfigured Compose checks retain a classic fallback`() {
        assertTrue(
            requiresClassicStapleWatchFallback(
                StapleWatchForegroundFactCheckCapability.NOT_CONFIGURED
            )
        )
    }
}
