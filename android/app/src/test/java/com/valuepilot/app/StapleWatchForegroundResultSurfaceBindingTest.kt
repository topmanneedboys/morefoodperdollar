package com.valuepilot.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class StapleWatchForegroundResultSurfaceBindingTest {

    @Test
    fun `binding suppresses hidden output forwards visible lifecycle and clears once on exit`() {
        var clearCalls = 0
        var renderCalls = 0
        val binding =
            StapleWatchForegroundResultSurfaceBinding(
                renderer = StapleWatchSurfaceRenderer { renderCalls += 1 },
                clearSurface = { clearCalls += 1 }
            )
        val observer = binding.outputObserver
        val cleared = StapleWatchForegroundEvaluationOutput.Cleared

        observer.onOutputChanged(cleared)
        binding.onPolicyRouteVisibilityChanged(false)

        assertEquals(0, clearCalls)
        assertEquals(0, renderCalls)

        binding.onPolicyRouteVisibilityChanged(true)
        observer.onOutputChanged(cleared)

        assertEquals(1, clearCalls)
        assertEquals(0, renderCalls)
        assertSame(observer, binding.outputObserver)

        binding.onPolicyRouteVisibilityChanged(true)
        assertEquals(1, clearCalls)

        binding.onPolicyRouteVisibilityChanged(false)
        assertEquals(2, clearCalls)

        binding.onPolicyRouteVisibilityChanged(false)
        observer.onOutputChanged(cleared)
        assertEquals(2, clearCalls)

        binding.onPolicyRouteVisibilityChanged(true)
        assertEquals(2, clearCalls)
        assertEquals(0, renderCalls)
    }

    @Test
    fun `binding only composes verified result presentation and route lifecycle boundaries`() {
        val source = source("StapleWatchForegroundResultSurfaceBinding.kt").readText()

        assertTrue(source.contains("StapleWatchSurfacePresenter(renderer)"))
        assertTrue(source.contains("StapleWatchForegroundEvaluationPresentationObserver("))
        assertTrue(source.contains("presenter = StapleWatchSurfacePresenter(renderer)"))
        assertTrue(source.contains("clearSurface = clearSurface"))
        assertTrue(source.contains("StapleWatchPolicyRouteForegroundEvaluationOutputGate("))
        assertTrue(source.contains("val outputObserver: StapleWatchForegroundEvaluationOutputObserver"))
        assertTrue(source.contains("routeGate.onRouteVisibilityChanged(visible)"))

        listOf(
            "onOutputChanged(",
            ".projection",
            "StapleWatchForegroundEvaluationOutput.Cleared",
            "StapleWatchEconomicEvaluator",
            "StapleWatchEconomicDecision",
            "StapleWatchPolicy(",
            "Money(",
            "ShoppingStoreKey",
            "SharedPreferences",
            "WorkManager",
            "NotificationManager",
            "MainActivity",
            "android."
        ).forEach { forbidden ->
            assertFalse("Result surface binding must not own $forbidden", source.contains(forbidden))
        }
    }

    private fun source(fileName: String): File {
        var directory = File(System.getProperty("user.dir") ?: error("user.dir unavailable"))
        repeat(8) {
            val candidate = File(directory, "app/src/main/java/com/valuepilot/app/$fileName")
            if (candidate.isFile) return candidate
            directory = directory.parentFile ?: return@repeat
        }
        error("Could not locate $fileName")
    }
}
