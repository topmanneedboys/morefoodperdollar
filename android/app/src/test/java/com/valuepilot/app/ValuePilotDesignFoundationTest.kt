package com.valuepilot.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ValuePilotDesignFoundationTest {

    @Test
    fun `tokens centralize the shared consumer contract`() {
        assertEquals(48, ValuePilotDesignTokens.TouchTarget.MINIMUM_DP)
        assertEquals(18, ValuePilotDesignTokens.Shape.CARD_RADIUS_DP)
        assertEquals(180L, ValuePilotDesignTokens.Motion.DISCLOSURE_DURATION_MS)
        assertEquals("#FCF8F3", ValuePilotDesignTokens.Colors.BACKGROUND)
        assertEquals("#B34B2E", ValuePilotDesignTokens.Colors.PRIMARY)
    }

    @Test
    fun `shared button factory consumes tokens rather than duplicating dimensions`() {
        val source = source("ValuePilotProgrammaticButton.kt")
        assertTrue(source.contains("ValuePilotDesignTokens.Shape.CONTROL_RADIUS_DP"))
        assertTrue(source.contains("ValuePilotDesignTokens.TouchTarget.MINIMUM_DP"))
        assertTrue(source.contains("ValuePilotDesignTokens.Colors.ACTION_FILLED"))
        assertTrue(source.contains("ValuePilotDesignTokens.Colors.PRIMARY_DARK"))
    }

    @Test
    fun `context menu is presentation-only and bounded`() {
        val source = source("ValuePilotProductContextMenu.kt")
        assertTrue(source.contains("const val MAX_ACTIONS = 3"))
        assertTrue(source.contains("ValuePilotProductContextAction"))
        assertTrue(source.contains("R.string.product_context_more_actions"))
        assertTrue(source.contains("ValuePilotDesignTokens.TouchTarget.MINIMUM_DP"))
        assertTrue(source.contains("onAction?.invoke(action)"))
    }

    private fun source(name: String): String {
        var directory = File(System.getProperty("user.dir") ?: error("user.dir unavailable"))
        repeat(8) {
            val candidate = File(directory, "app/src/main/java/com/valuepilot/app/$name")
            if (candidate.isFile) return candidate.readText()
            directory = directory.parentFile ?: return@repeat
        }
        error("Could not locate $name")
    }
}
