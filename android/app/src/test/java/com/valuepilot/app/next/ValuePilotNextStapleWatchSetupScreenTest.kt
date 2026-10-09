package com.valuepilot.app.next

import com.valuepilot.app.StapleWatchForegroundFactCheckCapability
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ValuePilotNextStapleWatchSetupScreenTest {

    @Test
    fun `configured Compose checks keep the setup controls available`() {
        assertFalse(
            requiresClassicStapleWatchHandoff(
                StapleWatchForegroundFactCheckCapability.CONFIGURED
            )
        )
    }

    @Test
    fun `unconfigured Compose checks use the classic handoff`() {
        assertTrue(
            requiresClassicStapleWatchHandoff(
                StapleWatchForegroundFactCheckCapability.NOT_CONFIGURED
            )
        )
    }
}
