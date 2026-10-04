package com.valuepilot.app

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Presentation-only guardrails for the final calm consumer surfaces.
 *
 * These checks intentionally inspect the source boundary: the renderers may
 * choose layout and copy, but control sizing and colors must come from the
 * shared presentation system rather than a second local button language.
 */
class GlobalUiPolishBoundaryTest {

    @Test
    fun home_actions_share_the_button_factory_and_compact_touch_budget() {
        val source = source("PracticalShoppingHomeSurfaceView.kt")

        assertTrue(source.contains("ValuePilotProgrammaticButton.filled(context)"))
        assertTrue(source.contains("ValuePilotProgrammaticButton.outlined(context, compact = true)"))
        assertTrue(source.contains("ValuePilotProgrammaticButton.outlined(context).apply"))
        assertFalse(source.contains("MaterialButton(context).apply"))
        assertFalse(source.contains("dp(40)"))
        assertTrue(source.contains("private var lastRenderedItems"))
        assertTrue(source.contains("if (lastRenderedItems == items) return"))
        assertTrue(source.contains("itemPriceWatchOwnerControls"))
    }

    @Test
    fun personal_and_production_surfaces_use_shared_cards_and_controls() {
        val personal = listOf(
            source("PracticalShoppingSavedSurfaceView.kt"),
            source("ConfirmedPurchaseHistorySurfaceView.kt"),
            source("PriceWatchSurfaceView.kt")
        )
        personal.forEach { surface ->
            assertTrue(surface.contains("ValuePilotProductCard(context)"))
            assertTrue(surface.contains("ValuePilotProgrammaticButton.outlined"))
            assertFalse(surface.contains("MaterialButton(context).apply"))
            assertFalse(surface.contains("dp(40)"))
        }

        val production = source("ProductionSearchSurfaceView.kt")
        assertTrue(production.contains("ValuePilotProductCard(context)"))
        assertTrue(production.contains("ValuePilotDesignTokens.Colors.SUCCESS_SURFACE"))
        assertTrue(production.contains("ValuePilotDesignTokens.Colors.TEXT_PRIMARY"))

        val map = source("ProductStoreMapSurfaceView.kt")
        assertTrue(map.contains("ValuePilotDesignTokens.Colors.SURFACE_SUBTLE"))
        assertTrue(map.contains("ValuePilotDesignTokens.Colors.ON_PRIMARY"))
        assertFalse(map.contains("Color.rgb("))
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
