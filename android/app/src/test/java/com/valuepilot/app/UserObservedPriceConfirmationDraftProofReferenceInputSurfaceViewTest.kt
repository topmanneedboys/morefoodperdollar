package com.valuepilot.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class UserObservedPriceConfirmationDraftProofReferenceInputSurfaceViewTest {

    @Test
    fun `physical proof reference editor requires explicit reference and explicit proof type`() {
        val source = source().readText()

        listOf(
            "class UserObservedPriceConfirmationDraftProofReferenceInputSurfaceView",
            "visibility = View.GONE",
            "isSaveEnabled = false",
            "var onCommit: ((String, UserProvidedPriceProofType) -> Unit)? = null",
            "artifactIdEditor.text.toString()",
            "artifactId.isBlank()",
            "receiptOption.isChecked -> UserProvidedPriceProofType.RECEIPT",
            "priceTagOption.isChecked -> UserProvidedPriceProofType.PRICE_TAG",
            "onCommit?.invoke(artifactId, proofType)",
            "fun clearInput()",
            "artifactIdEditor.setText(\"\")",
            "proofTypeGroup.clearCheck()"
        ).forEach { required ->
            assertTrue("Expected explicit proof-reference input binding $required", source.contains(required))
        }

        assertFalse("Artifact reference must not be prefilled", source.contains("artifactIdEditor.setText("))
        assertFalse("Receipt must not be selected by default", source.contains("receiptOption.isChecked = true"))
        assertFalse("Price tag must not be selected by default", source.contains("priceTagOption.isChecked = true"))
    }

    @Test
    fun `proof reference editor owns no byte picker fingerprint storage submission or factual authority`() {
        val source = source().readText()

        listOf(
            "android.net.Uri",
            "ContentResolver",
            "registerForActivityResult",
            "ActivityResultContracts",
            "openInputStream",
            "ByteArray",
            "InputStream",
            "UserObservedPriceProofStreamReader",
            "AndroidUserObservedPriceProofContentSource",
            "UserProvidedPriceProofArtifact.fingerprint",
            "UserProvidedPriceProofArtifactLocalStore",
            "UserObservedPriceProofReadSubmissionGate",
            "UserObservedPriceConfirmationDraftSubmissionHandoff",
            "UserObservedPriceConfirmationTransaction",
            "UserObservedPriceConfirmationAndroidSession",
            "System.currentTimeMillis",
            "UUID",
            "MessageDigest",
            "ShoppingEvidence(",
            "EvidenceClaim(",
            "CURRENT_PRICE",
            "ProductionCurrentPrice",
            "ProviderProductionAuthorization",
            "OcrScanner",
            "Bitmap",
            "Camera",
            "SharedPreferences",
            "java.net"
        ).forEach { forbidden ->
            assertFalse("Proof-reference surface must not own $forbidden", source.contains(forbidden))
        }
    }

    private fun source(): File =
        appFile(
            "app/src/main/java/com/valuepilot/app/UserObservedPriceConfirmationDraftProofReferenceInputSurfaceView.kt"
        )

    private fun appFile(relativePath: String): File {
        var directory = File(System.getProperty("user.dir") ?: error("user.dir unavailable"))
        repeat(8) {
            val candidate = File(directory, relativePath)
            if (candidate.isFile) return candidate
            directory = directory.parentFile ?: return@repeat
        }
        error("Could not locate $relativePath")
    }
}
