package com.valuepilot.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class UserObservedPriceConfirmationMainActivityCompositionTest {

    @Test
    fun `foreground activity composes explicit action through existing Android execution session`() {
        val source = source("main/java/com/valuepilot/app/MainActivity.kt").readText()
        val layout = source("main/res/layout/activity_shell.xml").readText()

        listOf(
            "UserObservedPriceConfirmationActionSurfaceView",
            "UserObservedPriceConfirmationActionPresentationController(",
            "UserObservedPriceConfirmationAndroidSession.create(",
            "completionListener = observedPriceConfirmationActionPresentationController",
            "UserObservedPriceConfirmationActionCoordinator(",
            "UserObservedPriceConfirmationAndroidSubmissionTarget(",
            "observedPriceConfirmationActionExperience.onAction =",
            "observedPriceConfirmationActionPresentationController::onSubmitRequested",
            ".onRouteVisibilityChanged(observedPriceConfirmationDraftVisible)",
            "observedPriceConfirmationActionPresentationController.close()",
            "observedPriceConfirmationAndroidSession.close()"
        ).forEach { required ->
            assertTrue("Expected foreground confirmation composition $required", source.contains(required))
        }

        assertTrue(
            layout.contains(
                "<com.valuepilot.app.UserObservedPriceConfirmationActionSurfaceView"
            )
        )
        assertTrue(layout.contains("@+id/observedPriceConfirmationActionExperience"))

        assertTrue(
            "Draft/proof mutations must invalidate stale action success",
            source.windowed(
                size = "onDraftOrProofChanged()".length,
                step = 1,
                partialWindows = false
            ).count { it == "onDraftOrProofChanged()" } >= 2
        )

        listOf(
            "confirmAndRetain(",
            "UserProvidedPriceProofArtifactLocalStore(",
            "UserProvidedPriceProofArtifact.fingerprint(",
            "UserConfirmedObservedPrice.confirm(",
            "ShoppingEvidence(",
            "EvidenceClaim(",
            "EvidenceClaimDomain.CURRENT_PRICE"
        ).forEach { forbidden ->
            assertFalse("MainActivity must not acquire confirmation authority $forbidden", source.contains(forbidden))
        }
    }

    private fun source(relativePath: String): File {
        var directory = File(System.getProperty("user.dir") ?: error("user.dir unavailable"))
        repeat(8) {
            val candidate = File(directory, "app/src/$relativePath")
            if (candidate.isFile) return candidate
            directory = directory.parentFile ?: return@repeat
        }
        error("Could not locate $relativePath")
    }
}
