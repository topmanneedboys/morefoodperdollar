package com.valuepilot.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ShareToValuePilotBoundaryTest {

    @Test
    fun `share activity reviews untrusted text before explicit Compare handoff`() {
        val source = source("ShareToValuePilotActivity.kt").readText()

        listOf(
            "Intent.EXTRA_TEXT",
            "ShareToValuePilotUiProjector.project(rawText)",
            "val sharedText = uiState.sharedText ?: return@setOnClickListener",
            "ComparisonActivity.EXTRA_SHARED_TEXT",
            "startActivity(",
            "finish()"
        ).forEach { required ->
            assertTrue("Expected Share-to-ValuePilot boundary: $required", source.contains(required))
        }

        listOf(
            "HttpURLConnection",
            "URL(",
            "UniversalSearchController",
            "PracticalShoppingPlanner",
            "Money.parse",
            "System.currentTimeMillis",
            "UserProvidedPriceProofArtifactLocalStore"
        ).forEach { forbidden ->
            assertFalse("Share activity must not own $forbidden", source.contains(forbidden))
        }
    }

    @Test
    fun `manifest exposes only an intentional text share target`() {
        val manifest = manifest().readText()
        val activityStart = manifest.indexOf("android:name=\".ShareToValuePilotActivity\"")
        assertTrue(activityStart >= 0)
        val activityEnd = manifest.indexOf("</activity>", activityStart)
        assertTrue(activityEnd > activityStart)
        val block = manifest.substring(activityStart, activityEnd)

        assertTrue(block.contains("android:exported=\"true\""))
        assertTrue(block.contains("android.intent.action.SEND"))
        assertTrue(block.contains("android.intent.category.DEFAULT"))
        assertTrue(block.contains("android:mimeType=\"text/plain\""))
        assertFalse(block.contains("android.intent.action.VIEW"))
    }

    @Test
    fun `comparison handoff uses bounded insertion and never overwrites entries`() {
        val source = source("ComparisonActivity.kt").readText()

        listOf(
            "const val EXTRA_SHARED_TEXT",
            "applySharedTextIfPresent(savedInstanceState)",
            "CompareHereSharedTextDraft.apply(",
            "if (!result.added) return result.issue",
            "renderProductInputs(result.blocks)",
            "showSharedTextImportFailure"
        ).forEach { required ->
            assertTrue("Expected shared-text Compare boundary: $required", source.contains(required))
        }

        listOf(
            "HttpURLConnection",
            "PracticalShoppingPlanner",
            "Money.parse",
            "UniversalSearchController"
        ).forEach { forbidden ->
            assertFalse("Shared-text handoff must not add $forbidden", source.contains(forbidden))
        }
    }

    @Test
    fun `shared text handoff focuses the populated editable block and reopens the keyboard`() {
        val source = source("ComparisonActivity.kt").readText()
        val handoff =
            source
                .substringAfter("CompareHereSharedTextDraft.apply(")
                .substringBefore("private fun showSharedTextImportFailure")

        assertTrue(handoff.contains("onProductsChanged()"))
        assertTrue(handoff.contains("focusProductInput(result.addedIndex)"))
        assertTrue(
            handoff.indexOf("onProductsChanged()") <
                handoff.indexOf("focusProductInput(result.addedIndex)")
        )
        assertTrue(
            source.contains(
                "manager?.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)"
            )
        )
    }

    @Test
    fun `layout and strings disclose untrusted review before comparison`() {
        val layout = layout().readText()
        val strings = strings().readText()

        assertTrue(layout.contains("@+id/shareToValuePilotPreview"))
        assertTrue(layout.contains("@string/share_to_valuepilot_open_comparison"))
        assertTrue(strings.contains("name=\"share_to_valuepilot_ready_guidance\""))
        assertTrue(strings.contains("name=\"share_to_valuepilot_too_large_guidance\""))
        assertTrue(strings.contains("name=\"compare_shared_text_no_empty_slot_body\""))
    }

    private fun source(name: String): File =
        File(
            requireNotNull(System.getProperty("user.dir")),
            "src/main/java/com/valuepilot/app/$name"
        ).also { assertTrue("Missing source at ${it.absolutePath}", it.isFile) }

    private fun manifest(): File =
        File(
            requireNotNull(System.getProperty("user.dir")),
            "src/main/AndroidManifest.xml"
        ).also { assertTrue("Missing manifest at ${it.absolutePath}", it.isFile) }

    private fun layout(): File =
        File(
            requireNotNull(System.getProperty("user.dir")),
            "src/main/res/layout/activity_share_to_valuepilot.xml"
        ).also { assertTrue("Missing layout at ${it.absolutePath}", it.isFile) }

    private fun strings(): File =
        File(
            requireNotNull(System.getProperty("user.dir")),
            "src/main/res/values/strings.xml"
        ).also { assertTrue("Missing strings at ${it.absolutePath}", it.isFile) }
}
