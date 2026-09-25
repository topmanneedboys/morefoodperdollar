package com.valuepilot.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class UserObservedPriceConfirmationDraftProofContentSelectionSurfaceViewTest {

    @Test
    fun `physical surface renders status only and emits explicit select request`() {
        val source = source().readSourceText()

        listOf(
            "class UserObservedPriceConfirmationDraftProofContentSelectionSurfaceView",
            "visibility = View.GONE",
            "isSaveEnabled = false",
            "var onSelectRequested: (() -> Unit)? = null",
            "setOnClickListener { onSelectRequested?.invoke() }",
            "Choose proof image or PDF",
            "UserObservedPriceConfirmationDraftProofContentSelectionPresentation.Ready",
            "UserObservedPriceConfirmationDraftProofContentSelectionPresentation.Rejected"
        ).forEach { required ->
            assertTrue("Expected proof content surface binding $required", source.contains(required))
        }

        listOf(
            "ByteArray",
            "Uri",
            "ContentResolver",
            "openInputStream",
            "registerForActivityResult",
            "ActivityResultContracts",
            "takePersistableUriPermission",
            "UserObservedPriceProofStreamReader",
            "UserProvidedPriceProofArtifact.fingerprint",
            "UserProvidedPriceProofArtifactLocalStore",
            "UserObservedPriceConfirmationDraftRouteSession",
            "UserObservedPriceConfirmationDraftSubmissionHandoff",
            "UserObservedPriceConfirmationTransaction",
            "System.currentTimeMillis",
            "UUID",
            "ShoppingEvidence(",
            "EvidenceClaim(",
            "CURRENT_PRICE",
            "java.net"
        ).forEach { forbidden ->
            assertFalse("Proof content surface must not own $forbidden", source.contains(forbidden))
        }
    }

    @Test
    fun `shell composes picker transient owner and action invalidation only on exact confirmation route`() {
        val activity = appFile("app/src/main/java/com/valuepilot/app/MainActivity.kt").readSourceText()
        val layout = appFile("app/src/main/res/layout/activity_shell.xml").readSourceText()
        val configureSaved =
            activity
                .substringAfter("private fun configureSavedUi() {")
                .substringBefore("private fun configureQuickSearch")

        listOf(
            "private lateinit var observedPriceConfirmationDraftProofContentSelectionExperience:",
            "UserObservedPriceConfirmationDraftProofContentSelectionSurfaceView",
            "private lateinit var observedPriceConfirmationDraftProofContentSelectionCoordinator:",
            "UserObservedPriceConfirmationDraftProofContentSelectionCoordinator",
            "private lateinit var observedPriceConfirmationDraftProofContentPicker:",
            "AndroidUserObservedPriceProofContentPicker",
            "observedPriceConfirmationDraftProofContentSelectionExperience =\n            findViewById(R.id.observedPriceConfirmationDraftProofContentSelectionExperience)",
            "observedPriceConfirmationDraftProofContentSelectionExperience.onSelectRequested =\n            observedPriceConfirmationDraftProofContentSelectionCoordinator::onSelectRequested",
            "observedPriceConfirmationDraftProofContentSelectionExperience.visibility =\n            if (observedPriceConfirmationDraftVisible) View.VISIBLE else View.GONE",
            "observedPriceConfirmationDraftProofContentSelectionCoordinator\n            .onRouteVisibilityChanged(observedPriceConfirmationDraftVisible)",
            "observedPriceConfirmationDraftProofContentSelectionExperience.onSelectRequested = null",
            "observedPriceConfirmationDraftProofContentPicker.close()",
            "observedPriceConfirmationDraftProofContentSelectionCoordinator.close()"
        ).forEach { required ->
            assertTrue("Expected shell proof-content composition $required", activity.contains(required))
        }

        listOf(
            "AndroidUserObservedPriceProofContentSource(contentResolver)",
            "onReadResult =\n                    observedPriceConfirmationDraftProofContentSelectionCoordinator::onContentReadResult",
            "requestForegroundSelection = {\n                    observedPriceConfirmationDraftProofContentPicker.launch()\n                }",
            "observedPriceConfirmationDraftProofContentSelectionExperience\n                            .onPresentation(presentation)",
            "observedPriceConfirmationActionPresentationController\n                            .onDraftOrProofChanged()"
        ).forEach { required ->
            assertTrue("Expected configureSaved composition $required", configureSaved.contains(required))
        }

        listOf(
            "registerForActivityResult",
            "ActivityResultContracts",
            "openInputStream",
            "takePersistableUriPermission",
            "UserObservedPriceProofStreamReader",
            "UserProvidedPriceProofArtifactLocalStore",
            "UserObservedPriceProofReadSubmissionGate",
            "UserObservedPriceConfirmationTransaction",
            "System.currentTimeMillis",
            "UUID"
        ).forEach { forbidden ->
            assertFalse("MainActivity must not own $forbidden for proof selection", configureSaved.contains(forbidden))
        }

        assertTrue(layout.contains("<com.valuepilot.app.UserObservedPriceConfirmationDraftProofContentSelectionSurfaceView"))
        assertTrue(layout.contains("android:id=\"@+id/observedPriceConfirmationDraftProofContentSelectionExperience\""))
        assertTrue(
            layout
                .substringAfter(
                    "android:id=\"@+id/observedPriceConfirmationDraftProofContentSelectionExperience\""
                )
                .substringBefore("/>")
                .contains("android:visibility=\"gone\"")
        )

        assertFalse(
            activity.contains(
                "observedPriceConfirmationDraftProofContentSelectionExperience.visibility = if (savedVisible) View.VISIBLE else View.GONE"
            )
        )
        assertFalse(
            activity.contains(
                "observedPriceConfirmationDraftProofContentSelectionExperience.visibility = if (observedPriceSelectionVisible) View.VISIBLE else View.GONE"
            )
        )
    }

    private fun source(): File =
        appFile(
            "app/src/main/java/com/valuepilot/app/UserObservedPriceConfirmationDraftProofContentSelectionSurfaceView.kt"
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
