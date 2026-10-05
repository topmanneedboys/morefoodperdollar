package com.valuepilot.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Keeps the personal shopping area visually coherent without moving any
 * persistence, identity, price or ranking authority into the renderers.
 */
class PersonalShoppingSurfaceBoundaryTest {

    @Test
    fun personal_surfaces_use_shared_cards_keyed_rows_and_progressive_actions() {
        val saved = source("PracticalShoppingSavedSurfaceView.kt")
        val history = source("ConfirmedPurchaseHistorySurfaceView.kt")
        val watch = source("PriceWatchSurfaceView.kt")

        listOf(saved, history, watch).forEach { surface ->
            assertTrue(surface.contains("ValuePilotProductCard(context)"))
            assertTrue(surface.contains("ValuePilotMotion.reveal"))
        }
        assertTrue(saved.contains("text = \"More\""))
        assertTrue(history.contains("private val rowViews = LinkedHashMap<String, PurchaseRow>()"))
        assertTrue(history.contains("text = \"More\""))
        assertTrue(watch.contains("private val rowViews = LinkedHashMap<String, WatchRow>()"))
        assertTrue(watch.contains("text = \"More\""))
        assertTrue(watch.contains("Last paid: \${latest.priceText}"))
        assertTrue(watch.contains("Last observed: \${latest.priceText}"))
        assertFalse(history.contains("removeAllViews()"))
        assertFalse(watch.contains("rowsContainer.removeAllViews()"))
    }

    @Test
    fun personal_surface_actions_remain_typed_and_private() {
        val history = source("ConfirmedPurchaseHistorySurfaceView.kt")
        val watch = source("PriceWatchSurfaceView.kt")

        listOf(
            "onAction?.invoke(current.repeatAction)",
            "onAction?.invoke(current.editAction)",
            "onAction?.invoke(current.deleteAction)",
            "onPriceWatchAction?.invoke(action)",
            "onAction?.invoke(current.addObservationAction)",
            "onAction?.invoke(current.editTargetAction)",
            "onAction?.invoke(current.toggleAction)"
        ).forEach { required ->
            assertTrue("Missing typed personal action binding $required", history.contains(required) || watch.contains(required))
        }
        listOf(
            "SharedPreferences",
            "PracticalShoppingPlanner",
            "Money.parse",
            "Http",
            "URL(",
            "System.currentTimeMillis"
        ).forEach { forbidden ->
            assertFalse(history.contains(forbidden))
            assertFalse(watch.contains(forbidden))
        }
    }

    @Test
    fun personal_routes_preserve_shell_scroll_across_refresh_and_rotation() {
        val activity = source("MainActivity.kt")
        listOf(
            "private var personalScrollY = 0",
            "private var personalRouteActive = false",
            "STATE_PERSONAL_SCROLL_Y",
            "if (personalRouteActive) personalScrollY = shellScrollView.scrollY.coerceAtLeast(0)",
            "restorePersonalScrollPosition()",
            "state.route == AppRoute.CONFIRMED_PURCHASE_HISTORY",
            "state.route == AppRoute.PRICE_WATCHES"
        ).forEach { required -> assertTrue(activity.contains(required)) }
    }

    private fun source(name: String): String {
        var directory = File(System.getProperty("user.dir") ?: error("user.dir unavailable"))
        repeat(8) {
            val candidate = File(directory, "src/main/java/com/valuepilot/app/$name")
            if (candidate.isFile) return candidate.readText()
            directory = directory.parentFile ?: return@repeat
        }
        error("Could not locate $name")
    }
}
