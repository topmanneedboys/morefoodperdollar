package com.valuepilot.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class UserObservedPriceConfirmationDraftSurfaceViewTest {

    @Test
    fun `physical renderer starts inactive and mechanically renders projected copy`() {
        val source = source().readSourceText()

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
        val source = source().readSourceText()

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
        val source = source().readSourceText()

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
    fun `physical renderer is attached only through the real confirmation route that owns visibility`() {
        val className = "UserObservedPriceConfirmationDraftSurfaceView"
        val layout = appFile("app/src/main/res/layout/activity_shell.xml").readSourceText()
        val activity = appFile("app/src/main/java/com/valuepilot/app/MainActivity.kt").readSourceText()

        assertTrue("Shell must now contain the passive draft surface", layout.contains(className))
        assertTrue(
            "Draft surface must remain hidden by default in the shell layout",
            layout
                .substringAfter("android:id=\"@+id/observedPriceConfirmationDraftExperience\"")
                .substringBefore("/>")
                .contains("android:visibility=\"gone\"")
        )
        assertTrue(
            "MainActivity must bind the passive draft surface",
            activity.contains("private lateinit var observedPriceConfirmationDraftExperience:") &&
                activity.contains(className)
        )
        assertTrue(
            "Only the exact confirmation route may own foreground visibility",
            activity.contains(
                "val observedPriceConfirmationDraftVisible =\n            state.route == AppRoute.OBSERVED_PRICE_CONFIRMATION_DRAFT"
            )
        )
        assertTrue(
            "Physical visibility must follow only the exact confirmation-route boolean",
            activity.contains(
                "observedPriceConfirmationDraftExperience.visibility =\n            if (observedPriceConfirmationDraftVisible) View.VISIBLE else View.GONE"
            )
        )
        assertTrue(
            "Route-local coordinator must receive the same exact visibility ownership",
            activity.contains(
                "observedPriceConfirmationDraftRouteCoordinator\n            .onRouteVisibilityChanged(observedPriceConfirmationDraftVisible)"
            )
        )
        assertFalse(
            "Saved primary visibility must not directly activate the confirmation draft surface",
            activity.contains(
                "observedPriceConfirmationDraftExperience.visibility = if (savedVisible) View.VISIBLE else View.GONE"
            )
        )
        assertFalse(
            "Selection-route visibility must not directly activate the confirmation draft surface",
            activity.contains(
                "observedPriceConfirmationDraftExperience.visibility = if (observedPriceSelectionVisible) View.VISIBLE else View.GONE"
            )
        )
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
