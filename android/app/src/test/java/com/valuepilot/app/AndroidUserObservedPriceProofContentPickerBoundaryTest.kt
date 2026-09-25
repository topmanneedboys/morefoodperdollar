package com.valuepilot.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AndroidUserObservedPriceProofContentPickerBoundaryTest {

    @Test
    fun `picker uses explicit foreground system document selection and bounded source only`() {
        val source = source().readText()

        listOf(
            "ActivityResultContracts.OpenDocument()",
            "activity.registerForActivityResult(",
            "if (closed || uri == null) return@registerForActivityResult",
            "onReadResult(contentSource.read(uri))",
            "launcher.launch(arrayOf(\"image/*\", \"application/pdf\"))",
            "launcher.unregister()"
        ).forEach { required ->
            assertTrue("Expected explicit picker boundary $required", source.contains(required))
        }

        listOf(
            "takePersistableUriPermission",
            "ContentResolver",
            "openInputStream",
            "openOutputStream",
            "UserObservedPriceProofStreamReader",
            "UserProvidedPriceProofArtifact.fingerprint",
            "UserProvidedPriceProofArtifactLocalStore",
            "UserObservedPriceConfirmationDraftRouteSession",
            "UserObservedPriceConfirmationDraftSubmissionHandoff",
            "UserObservedPriceProofReadSubmissionGate",
            "UserObservedPriceConfirmationTransaction",
            "UserObservedPriceConfirmationExecution",
            "System.currentTimeMillis",
            "UUID",
            "ShoppingEvidence(",
            "EvidenceClaim(",
            "CURRENT_PRICE",
            "OcrScanner",
            "Camera",
            "Bitmap",
            "java.net",
            "android.permission.INTERNET",
            "android.permission.ACCESS_NETWORK_STATE"
        ).forEach { forbidden ->
            assertFalse("Picker must not own $forbidden", source.contains(forbidden))
        }
    }

    private fun source(): File =
        appFile("app/src/main/java/com/valuepilot/app/AndroidUserObservedPriceProofContentPicker.kt")

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
