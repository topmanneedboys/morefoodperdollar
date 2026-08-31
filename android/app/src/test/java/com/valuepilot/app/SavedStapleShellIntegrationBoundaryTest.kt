package com.valuepilot.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SavedStapleShellIntegrationBoundaryTest {

    @Test
    fun `main activity composes saved lifecycle into staple setup without business authority`() {
        val source = activitySource().readText()

        assertTrue(source.contains("PracticalShoppingSavedStapleLaunchPresenter"))
        assertTrue(source.contains("StapleWatchSavedSetupCompositionCoordinator"))
        assertTrue(source.contains("StapleWatchSavedSelectionSurfacePresenter"))
        assertTrue(source.contains("StapleWatchSavedSelectionRouteSession("))
        assertTrue(source.contains("PracticalShoppingSavedLifecycleRenderer { state ->"))
        assertTrue(source.contains("snapshotObserver = savedSnapshotObserver"))
        assertTrue(
            source.contains(
                "stapleWatchSetupExperience.onContinueAction = stapleWatchSetupCoordinator::onContinueAction"
            )
        )
        assertFalse(source.contains("StapleWatchSavedIdentityHandoffUiAction.Request"))

        assertFalse(source.contains("StapleWatchEconomicEvaluator"))
        assertFalse(source.contains("StapleWatchEconomicDecision"))
        assertFalse(source.contains("StapleWatchAlternativeCandidate"))
        assertFalse(source.contains("WorkManager"))
        assertFalse(source.contains("NotificationManager"))
    }

    @Test
    fun `explicit staple continuation composes evidence and saved display metadata without policy authority`() {
        val source = activitySource().readText()

        assertTrue(
            source.contains(
                "private lateinit var stapleWatchForegroundEvaluationInputHost: StapleWatchForegroundEvaluationInputHost"
            )
        )
        assertTrue(
            source.contains(
                "private lateinit var stapleWatchSavedDisplayMetadataCompositionCoordinator:"
            )
        )
        assertTrue(
            source.contains(
                "private lateinit var stapleWatchFactResolutionHost: StapleWatchFactResolutionHost"
            )
        )
        assertTrue(
            source.contains(
                "stapleWatchForegroundEvaluationInputHost = StapleWatchForegroundEvaluationInputHost()"
            )
        )
        assertTrue(
            source.contains(
                "stapleWatchSavedDisplayMetadataCompositionCoordinator =\n            StapleWatchSavedDisplayMetadataCompositionCoordinator("
            )
        )
        assertTrue(
            source.contains(
                "preconditionsObserver = stapleWatchForegroundEvaluationInputHost"
            )
        )
        assertTrue(
            source.contains(
                "displayMetadataObserver = stapleWatchForegroundEvaluationInputHost"
            )
        )
        assertTrue(
            source.contains(
                "preconditionsObserver = stapleWatchSavedDisplayMetadataCompositionCoordinator"
            )
        )
        assertTrue(source.contains("factCheckIntentObserver = stapleWatchFactResolutionHost"))

        assertFalse(source.contains("stapleWatchFactResolutionHost.accept("))
        assertFalse(source.contains("stapleWatchForegroundEvaluationInputHost.accept("))
        assertFalse(source.contains("StapleWatchEconomicEvidencePreconditions"))
        assertFalse(source.contains("StapleWatchPolicy("))
        assertFalse(source.contains("StapleWatchStoreDisplayMetadata("))
        assertFalse(source.contains("StapleWatchSavedAlternativeStoreDisplayMetadataAdapter"))
        assertFalse(source.contains("StapleWatchForegroundEvaluationCoordinator"))
        assertFalse(source.contains("PracticalShoppingProduction"))
        assertFalse(source.contains("ProductionCurrentPrice"))
    }

    @Test
    fun `validated saved snapshot fans out only to setup and display metadata composition`() {
        val source = activitySource().readText()

        assertTrue(
            source.contains(
                "val savedSnapshotObserver =\n            PracticalShoppingSavedValidatedSnapshotObserver { snapshot ->"
            )
        )
        assertTrue(source.contains("stapleWatchSetupCoordinator.onSnapshot(snapshot)"))
        assertTrue(
            source.contains(
                "stapleWatchSavedDisplayMetadataCompositionCoordinator.onSnapshot(snapshot)"
            )
        )
        assertTrue(source.contains("snapshotObserver = savedSnapshotObserver"))
    }

    @Test
    fun `saved and staple setup physical visibility is owned by exact shell routes`() {
        val source = activitySource().readText()

        assertTrue(source.contains("val savedVisible = state.route == AppRoute.SAVED"))
        assertTrue(
            source.contains(
                "val stapleSetupVisible = state.route == AppRoute.STAPLE_WATCH_SETUP"
            )
        )
        assertTrue(
            source.contains(
                "savedStapleLaunchExperience.visibility = if (savedVisible) View.VISIBLE else View.GONE"
            )
        )
        assertTrue(source.contains("stapleWatchSetupCoordinator.onRouteVisibilityChanged(stapleSetupVisible)"))
        assertTrue(source.contains("dispatch(AppShellIntent.OpenStapleWatchSetup)"))
    }

    @Test
    fun `android back returns staple setup to saved through shell reducer`() {
        val source = activitySource().readText()

        assertTrue(source.contains("onBackPressedDispatcher.addCallback("))
        assertTrue(source.contains("shellState.route == AppRoute.STAPLE_WATCH_SETUP"))
        assertTrue(source.contains("dispatch(AppShellIntent.NavigateBack)"))
    }

    @Test
    fun `activity teardown disconnects saved and staple physical actions`() {
        val source = activitySource().readText()

        assertTrue(source.contains("savedExperience.onAction = null"))
        assertTrue(source.contains("savedStapleLaunchExperience.onAction = null"))
        assertTrue(source.contains("stapleWatchSetupExperience.onAction = null"))
        assertTrue(source.contains("stapleWatchSetupExperience.onContinueAction = null"))
        assertTrue(source.contains("stapleWatchSetupCoordinator.close()"))
        assertTrue(source.contains("stapleWatchFactResolutionHost.close()"))
        assertTrue(source.contains("stapleWatchSavedDisplayMetadataCompositionCoordinator.close()"))
        assertTrue(source.contains("stapleWatchForegroundEvaluationInputHost.close()"))
        assertTrue(source.contains("savedRouteCoordinator.close()"))
    }

    @Test
    fun `shell layout contains replaceable saved launcher and staple setup surfaces hidden by default`() {
        val layout = layoutSource().readText()

        assertTrue(layout.contains("<com.valuepilot.app.PracticalShoppingSavedStapleLaunchView"))
        assertTrue(layout.contains("android:id=\"@+id/savedStapleLaunchExperience\""))
        assertTrue(layout.contains("<com.valuepilot.app.StapleWatchSavedSelectionSurfaceView"))
        assertTrue(layout.contains("android:id=\"@+id/stapleWatchSetupExperience\""))
        assertTrue(
            layout.substringAfter("android:id=\"@+id/savedStapleLaunchExperience\"")
                .substringBefore("/>")
                .contains("android:visibility=\"gone\"")
        )
        assertTrue(
            layout.substringAfter("android:id=\"@+id/stapleWatchSetupExperience\"")
                .substringBefore("/>")
                .contains("android:visibility=\"gone\"")
        )
    }

    private fun activitySource(): File {
        val root = File(System.getProperty("user.dir"))
        return File(root, "src/main/java/com/valuepilot/app/MainActivity.kt").also {
            assertTrue("Missing MainActivity.kt at ${it.absolutePath}", it.isFile)
        }
    }

    private fun layoutSource(): File {
        val root = File(System.getProperty("user.dir"))
        return File(root, "src/main/res/layout/activity_shell.xml").also {
            assertTrue("Missing activity_shell.xml at ${it.absolutePath}", it.isFile)
        }
    }
}
