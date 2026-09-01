package com.valuepilot.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class UserObservedPriceConfirmationAndroidSubmissionTargetTest {

    @Test
    fun `runtime target only unwraps immutable submission into existing Android session`() {
        val source = source().readText()

        listOf(
            "session.submit(",
            "artifactId = submission.artifactId",
            "proofType = submission.proofType",
            "artifactBytes = artifactBytes",
            "fields = submission.fields"
        ).forEach { required ->
            assertTrue("Expected narrow runtime forwarding $required", source.contains(required))
        }

        listOf(
            "Money.parse",
            "Currency.getInstance",
            "System.currentTimeMillis",
            "UUID",
            "MessageDigest",
            "ContentResolver",
            "openInputStream",
            "UserProvidedPriceProofArtifactLocalStore(",
            "UserProvidedPriceProofArtifact.fingerprint",
            "UserConfirmedObservedPrice.confirm",
            "UserObservedPriceConfirmationTransaction(",
            "ShoppingEvidence(",
            "EvidenceClaim(",
            "CURRENT_PRICE",
            "OcrScanner",
            "java.net",
            "android.view.",
            "android.widget."
        ).forEach { forbidden ->
            assertFalse("Runtime target must not own $forbidden", source.contains(forbidden))
        }
    }

    private fun source(): File {
        var directory = File(System.getProperty("user.dir") ?: error("user.dir unavailable"))
        repeat(8) {
            val candidate =
                File(
                    directory,
                    "app/src/main/java/com/valuepilot/app/" +
                        "UserObservedPriceConfirmationAndroidSubmissionTarget.kt"
                )
            if (candidate.isFile) return candidate
            directory = directory.parentFile ?: return@repeat
        }
        error("Could not locate UserObservedPriceConfirmationAndroidSubmissionTarget.kt")
    }
}
