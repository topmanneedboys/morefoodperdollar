package com.valuepilot.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class UserObservedPriceConfirmationActionSurfaceViewBoundaryTest {

    @Test
    fun `confirmation action renderer fails closed when owner callback is detached`() {
        val source = source().readText()

        assertTrue(source.contains("actionButton.isEnabled = state.actionEnabled && onAction != null"))
        assertTrue(source.contains("setOnClickListener { onAction?.invoke() }"))
        assertTrue(source.contains("accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE"))
        assertFalse(source.contains("actionButton.isEnabled = state.actionEnabled\n"))

        listOf(
            "UserObservedPriceConfirmationTransactionResult",
            "UserProvidedPriceProofArtifact",
            "System.currentTimeMillis",
            "ContentResolver",
            "ShoppingEvidence",
            "Money.parse",
            "EvidenceAcceptance"
        ).forEach { forbidden ->
            assertFalse("Confirmation action View must not own $forbidden", source.contains(forbidden))
        }
    }

    private fun source(): File {
        var directory = File(System.getProperty("user.dir") ?: error("user.dir unavailable"))
        repeat(8) {
            val candidate =
                File(
                    directory,
                    "app/src/main/java/com/valuepilot/app/UserObservedPriceConfirmationActionSurfaceView.kt"
                )
            if (candidate.isFile) return candidate
            directory = directory.parentFile ?: return@repeat
        }
        error("Could not locate confirmation action surface source")
    }
}
