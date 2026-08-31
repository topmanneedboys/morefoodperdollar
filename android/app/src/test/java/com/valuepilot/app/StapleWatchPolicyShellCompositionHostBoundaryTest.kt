package com.valuepilot.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class StapleWatchPolicyShellCompositionHostBoundaryTest {

    @Test
    fun `host composes verified policy setup boundaries without business authority`() {
        val source = hostSource().readText()

        assertTrue(source.contains("StapleWatchPolicyDraftSurfacePresenter(policyRenderer)"))
        assertTrue(source.contains("StapleWatchPolicyRouteAvailabilityShellAdapter("))
        assertTrue(source.contains("StapleWatchPolicySetupCompositionCoordinator("))
        assertTrue(source.contains("StapleWatchPolicyDraftRouteSession("))
        assertTrue(source.contains("StapleWatchEconomicEvidencePreconditionsFanout("))
        assertTrue(source.contains("foregroundInputObserver = foregroundPreconditionsObserver"))
        assertTrue(source.contains("policySetupObserver = policySetupCoordinator"))

        assertFalse(source.contains("StapleWatchPolicyBaselineMoneySpecResolver"))
        assertFalse(source.contains("StapleWatchPolicy("))
        assertFalse(source.contains("StapleWatchEconomicEvaluator"))
        assertFalse(source.contains("StapleWatchEconomicDecision"))
        assertFalse(source.contains("Money("))
        assertFalse(source.contains("WorkManager"))
        assertFalse(source.contains("NotificationManager"))
        assertFalse(source.contains("SharedPreferences"))
    }

    @Test
    fun `host exposes only typed shell lifecycle and policy actions`() {
        val source = hostSource().readText()

        assertTrue(
            source.contains(
                "val preconditionsObserver: StapleWatchEconomicEvidencePreconditionsObserver ="
            )
        )
        assertTrue(source.contains("fun onRouteVisibilityChanged(visible: Boolean)"))
        assertTrue(source.contains("fun onSurfaceAction(action: StapleWatchPolicyDraftUiAction)"))
        assertTrue(source.contains("fun onContinueAction(action: StapleWatchPolicyHandoffUiAction)"))
        assertTrue(source.contains("override fun close()"))

        assertFalse(source.contains("android."))
        assertFalse(source.contains("EditText"))
        assertFalse(source.contains("String)"))
        assertFalse(source.contains("parse("))
        assertFalse(source.contains("System.currentTimeMillis"))
    }

    @Test
    fun `policy completion remains delegated to setup coordinator`() {
        val source = hostSource().readText()

        assertTrue(source.contains("policyObserver = policyObserver"))
        assertTrue(source.contains("policySetupCoordinator.onContinueAction(action)"))

        assertFalse(source.contains("currentFinalizationOrNull"))
        assertFalse(source.contains("requestPolicyHandoff"))
        assertFalse(source.contains("finalization.policy"))
        assertFalse(source.contains("onPolicy("))
    }

    private fun hostSource(): File {
        val root = File(System.getProperty("user.dir"))
        return File(root, "src/main/java/com/valuepilot/app/StapleWatchPolicyShellCompositionHost.kt").also {
            assertTrue("Missing shell composition host at ${it.absolutePath}", it.isFile)
        }
    }
}
