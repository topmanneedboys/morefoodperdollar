package com.valuepilot.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class StapleWatchPolicyRouteAvailabilityShellAdapterTest {

    @Test
    fun availableFromWatchSetupEmitsOnlyOpenPolicyIntent() {
        val emitted = mutableListOf<AppShellIntent>()
        val adapter = adapter(AppRoute.STAPLE_WATCH_SETUP, emitted)

        adapter.onAvailabilityChanged(StapleWatchPolicyRouteAvailability.AVAILABLE)

        assertEquals(listOf(AppShellIntent.OpenStapleWatchPolicy), emitted)
    }

    @Test
    fun availableOutsideWatchSetupDoesNotHijackCurrentRoute() {
        for (route in AppRoute.entries.filter { it != AppRoute.STAPLE_WATCH_SETUP }) {
            val emitted = mutableListOf<AppShellIntent>()
            val adapter = adapter(route, emitted)

            adapter.onAvailabilityChanged(StapleWatchPolicyRouteAvailability.AVAILABLE)

            assertTrue("AVAILABLE must not navigate from $route", emitted.isEmpty())
        }
    }

    @Test
    fun unavailableFromPolicyEmitsOnlyOneStepBackIntent() {
        val emitted = mutableListOf<AppShellIntent>()
        val adapter = adapter(AppRoute.STAPLE_WATCH_POLICY, emitted)

        adapter.onAvailabilityChanged(StapleWatchPolicyRouteAvailability.UNAVAILABLE)

        assertEquals(listOf(AppShellIntent.NavigateBack), emitted)
    }

    @Test
    fun unavailableOutsidePolicyNeverBacksOutOfAnotherRoute() {
        for (route in AppRoute.entries.filter { it != AppRoute.STAPLE_WATCH_POLICY }) {
            val emitted = mutableListOf<AppShellIntent>()
            val adapter = adapter(route, emitted)

            adapter.onAvailabilityChanged(StapleWatchPolicyRouteAvailability.UNAVAILABLE)

            assertTrue("UNAVAILABLE must not navigate from $route", emitted.isEmpty())
        }
    }

    @Test
    fun eachAvailabilityCallbackReadsTheCurrentRouteInsteadOfCapturingOldRoute() {
        var route = AppRoute.SAVED
        val emitted = mutableListOf<AppShellIntent>()
        val adapter =
            StapleWatchPolicyRouteAvailabilityShellAdapter(
                currentRoute = { route },
                emitIntent = emitted::add
            )

        adapter.onAvailabilityChanged(StapleWatchPolicyRouteAvailability.AVAILABLE)
        assertTrue(emitted.isEmpty())

        route = AppRoute.STAPLE_WATCH_SETUP
        adapter.onAvailabilityChanged(StapleWatchPolicyRouteAvailability.AVAILABLE)
        assertEquals(listOf(AppShellIntent.OpenStapleWatchPolicy), emitted)

        route = AppRoute.STAPLE_WATCH_POLICY
        adapter.onAvailabilityChanged(StapleWatchPolicyRouteAvailability.UNAVAILABLE)
        assertEquals(
            listOf(
                AppShellIntent.OpenStapleWatchPolicy,
                AppShellIntent.NavigateBack
            ),
            emitted
        )
    }

    @Test
    fun adapterOwnsOnlyTypedShellIntentMapping() {
        val source = source().readText()

        assertTrue(source.contains("StapleWatchPolicyRouteAvailabilityObserver"))
        assertTrue(source.contains("currentRoute() == AppRoute.STAPLE_WATCH_SETUP"))
        assertTrue(source.contains("emitIntent(AppShellIntent.OpenStapleWatchPolicy)"))
        assertTrue(source.contains("currentRoute() == AppRoute.STAPLE_WATCH_POLICY"))
        assertTrue(source.contains("emitIntent(AppShellIntent.NavigateBack)"))

        listOf(
            "StapleWatchPolicyBaselineMoneySpecResolver",
            "StapleWatchEconomicEvidencePreconditions",
            "StapleWatchPolicy(",
            "StapleWatchPolicyDraft(",
            "StapleWatchEconomicEvaluator",
            "StapleWatchEconomicDecision",
            "Money(",
            "MainActivity",
            "SharedPreferences",
            "WorkManager",
            "NotificationManager",
            "android."
        ).forEach { forbidden ->
            assertFalse("Shell adapter must not own $forbidden", source.contains(forbidden))
        }
    }

    private fun adapter(
        route: AppRoute,
        emitted: MutableList<AppShellIntent>
    ): StapleWatchPolicyRouteAvailabilityShellAdapter =
        StapleWatchPolicyRouteAvailabilityShellAdapter(
            currentRoute = { route },
            emitIntent = emitted::add
        )

    private fun source(): File =
        File(
            System.getProperty("user.dir"),
            "src/main/java/com/valuepilot/app/StapleWatchPolicyRouteAvailabilityShellAdapter.kt"
        ).also {
            assertTrue("Missing shell adapter source at ${it.absolutePath}", it.isFile)
        }
}
