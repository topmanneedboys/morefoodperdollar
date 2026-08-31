package com.valuepilot.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class StapleWatchPolicyRouteForegroundEvaluationOutputGateTest {

    @Test
    fun `hidden route suppresses output visible route forwards exact output and leaving clears once`() {
        val outputs = mutableListOf<StapleWatchForegroundEvaluationOutput>()
        val downstream =
            StapleWatchForegroundEvaluationOutputObserver { output -> outputs += output }
        val gate = StapleWatchPolicyRouteForegroundEvaluationOutputGate(downstream)
        val cleared = StapleWatchForegroundEvaluationOutput.Cleared

        gate.onOutputChanged(cleared)
        gate.onRouteVisibilityChanged(false)

        assertTrue(outputs.isEmpty())

        gate.onRouteVisibilityChanged(true)
        gate.onOutputChanged(cleared)

        assertEquals(1, outputs.size)
        assertSame(cleared, outputs.single())

        gate.onRouteVisibilityChanged(true)
        assertEquals(1, outputs.size)

        gate.onRouteVisibilityChanged(false)

        assertEquals(2, outputs.size)
        assertSame(cleared, outputs.last())

        gate.onRouteVisibilityChanged(false)
        gate.onOutputChanged(cleared)
        assertEquals(2, outputs.size)

        gate.onRouteVisibilityChanged(true)
        assertEquals(2, outputs.size)
    }

    @Test
    fun `route gate only owns typed output visibility lifecycle`() {
        val source = source("StapleWatchPolicyRouteForegroundEvaluationOutputGate.kt").readText()

        assertTrue(source.contains("StapleWatchForegroundEvaluationOutputObserver"))
        assertTrue(source.contains("StapleWatchForegroundEvaluationOutput.Cleared"))
        assertTrue(source.contains("if (!routeVisible) return"))
        assertTrue(source.contains("downstream.onOutputChanged(output)"))

        listOf(
            "AppRoute",
            "MainActivity",
            "StapleWatchSurfacePresenter",
            "StapleWatchUiProjection",
            "decisionCoordination",
            "StapleWatchEconomicEvaluator",
            "StapleWatchPolicy(",
            "Money(",
            "ShoppingStoreKey",
            "SharedPreferences",
            "WorkManager",
            "NotificationManager",
            "android."
        ).forEach { forbidden ->
            assertFalse("Route output gate must not own $forbidden", source.contains(forbidden))
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
