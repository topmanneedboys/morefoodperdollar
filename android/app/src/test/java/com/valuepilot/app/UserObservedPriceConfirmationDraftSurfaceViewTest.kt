package com.valuepilot.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class UserObservedPriceConfirmationDraftSurfaceViewTest {

    @Test
    fun `physical renderer starts inactive and mechanically renders projected copy`() {
        val source = source().readText()

        listOf(
            "internal class UserObservedPriceConfirmationDraftSurfaceView",
            ") : LinearLayout(context, attrs, defStyleAttr), UserObservedPriceConfirmationDraftSurfaceRenderer",
            "visibility = View.GONE",
            "isSaveEnabled = false",
            "override fun render(state: UserObservedPriceConfirmationDraftUiState)",
            "removeAllViews()",
            "addView(heading(state.headline))",
            "addView(statusTitle(state.statusTitle))",
            "addView(guidance(state.guidance))",
            "state.missingRequirements.forEach { requirement ->",
            "addView(missingRequirement(requirement.label))",
            "addView(notice(state.notice))"
        ).forEach { required ->
            assertTrue("Expected passive draft surface binding $required", source.contains(required))
        }

        assertFalse(
            "Draft surface visibility must remain external to render",
            source.contains("visibility = View.VISIBLE")
        )
    }

    @Test
    fun `physical renderer does not interpret draft completeness or missing field semantics`() {
        val source = source().readText()

        assertFalse(
            "Draft surface must not inspect status",
            Regex("""state\.status\b""").containsMatchIn(source)
        )
        assertFalse(
            "Draft surface must not inspect missing-field identity",
            Regex("""requirement\.field\b""").containsMatchIn(source)
        )

        listOf(
            "UserObservedPriceConfirmationDraftUiStatus.NEEDS_NON_BYTE_INPUT",
            "UserObservedPriceConfirmationDraftUiStatus.NON_BYTE_INPUT_COMPLETE",
            "UserObservedPriceConfirmationDraftMissingField.",
            "when (state",
            "when (requirement"
        ).forEach { forbidden ->
            assertFalse("Draft surface must not interpret $forbidden", source.contains(forbidden))
        }
    }

    @Test
    fun `physical renderer owns no editing action proof storage validation or ranking authority`() {
        val source = source().readText()

        listOf(
            "android.widget.Button",
            "android.widget.EditText",
            "InputType",
            "setOnClickListener",
            "android.content.Intent",
            "startActivity(",
            "registerForActivityResult",
            "ActivityResultContracts",
            "android.net.Uri",
            "ContentResolver",
            "UserObservedPriceConfirmationDraftFinalizer",
            "UserObservedPriceConfirmationDraftRouteSession",
            "UserObservedPriceConfirmationDraftSubmissionHandoff",
            "UserObservedPriceConfirmationTransaction",
            "UserObservedPriceConfirmationAndroidSession",
            "UserObservedPriceProofContentSource",
            "AndroidUserObservedPriceProofContentSource",
            "UserObservedPriceProofReadSubmissionGate",
            "UserProvidedPriceProofArtifactLocalStore",
            "System.currentTimeMillis",
            "UUID",
            "MessageDigest",
            "Money(",
            "PracticalShoppingStoreIdentityScope(",
            "ShoppingEvidence(",
            "EvidenceClaim(",
            "EvidenceBackedUnitValuePolicy",
            "ProductPackageQuantity",
            "CURRENT_PRICE",
            "ProductionBestValue",
            "ProviderProductionAuthorization",
            "OcrScanner",
            "Bitmap",
            "java.net"
        ).forEach { forbidden ->
            assertFalse("Draft surface must not own $forbidden", source.contains(forbidden))
        }
    }

    @Test
    fun `physical renderer remains unattached until a real confirmation route owns visibility`() {
        val className = "UserObservedPriceConfirmationDraftSurfaceView"
        val layout = appFile("app/src/main/res/layout/activity_shell.xml").readText()
        val activity = appFile("app/src/main/java/com/valuepilot/app/MainActivity.kt").readText()

        assertFalse("Shell must not attach the draft surface yet", layout.contains(className))
        assertFalse("MainActivity must not activate the draft surface yet", activity.contains(className))
    }

    private fun source(): File =
        appFile(
            "app/src/main/java/com/valuepilot/app/UserObservedPriceConfirmationDraftSurfaceView.kt"
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
