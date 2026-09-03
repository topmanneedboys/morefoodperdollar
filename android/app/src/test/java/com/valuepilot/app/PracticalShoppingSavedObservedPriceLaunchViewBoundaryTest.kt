package com.valuepilot.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PracticalShoppingSavedObservedPriceLaunchViewBoundaryTest {

    @Test
    fun `physical launcher is typed replaceable hidden by default and owner controlled`() {
        val source = source("PracticalShoppingSavedObservedPriceLaunchView.kt").readText()

        assertTrue(source.contains("PracticalShoppingSavedObservedPriceLaunchRenderer"))
        assertTrue(source.contains("visibility = View.GONE"))
        assertTrue(source.contains("isSaveEnabled = false"))
        assertEquals(1, Regex("""visibility\s*=""").findAll(source).count())
        assertFalse(source.contains("visibility = View.VISIBLE"))
        assertTrue(
            source.contains(
                "override fun render(state: PracticalShoppingSavedObservedPriceLaunchUiState)"
            )
        )
        assertTrue(source.contains("state.action?.let { action ->"))
        assertTrue(source.contains("state.title?.let"))
        assertTrue(source.contains("state.supportingText?.let"))
        assertTrue(source.contains("state.notice?.let"))
        assertTrue(source.contains("text = requireNotNull(state.actionLabel)"))
        assertTrue(source.contains("isEnabled = onAction != null"))
        assertTrue(source.contains("setOnClickListener { onAction?.invoke(action) }"))
        assertTrue(source.contains("accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE"))
    }

    @Test
    fun `physical launcher owns no readiness identity prefill confirmation storage or route authority`() {
        val source = source("PracticalShoppingSavedObservedPriceLaunchView.kt").readText()

        listOf(
            "PracticalShoppingSavedObservedPriceLaunchUiProjector",
            "PracticalShoppingSavedLifecycleState",
            "PracticalShoppingSavedLifecycleStatus",
            "PracticalShoppingSavedExactPreferenceUiProjection",
            "UserObservedPriceSavedSelectionRouteSession",
            "UserObservedPriceSavedPrefillHandoffGate",
            "UserObservedPriceSavedPrefillGate",
            "requestPrefillOrNull",
            "PracticalShoppingSavedAndroidSession",
            "PracticalShoppingSavedExactPreferenceLocalStore",
            "PracticalShoppingSavedDisplayMetadataLocalStore",
            "SourceProductIdentity",
            ".itemKey",
            ".storeKey",
            ".rawGtin",
            "UserObservedPriceConfirmationDraft",
            "UserObservedPriceConfirmationTransaction",
            "UserObservedPriceConfirmationExecution",
            "UserObservedPriceConfirmationAndroidSession",
            "UserProvidedPriceProof",
            "UserConfirmedObservedPrice",
            "UserProofBackedObservedPrice",
            "UserObservedPriceUnitValue",
            "ProductPackageQuantity",
            "EvidenceFreshness",
            "ProductionCurrentPrice",
            "Money",
            "ByteArray",
            "System.currentTimeMillis",
            "UUID",
            "AppShell",
            "MainActivity",
            "Intent",
            "startActivity",
            "SharedPreferences",
            "java.net",
            "java.io"
        ).forEach { forbidden ->
            assertFalse(
                "Observed-price Saved launch view must not own $forbidden",
                source.contains(forbidden)
            )
        }
    }

    private fun source(name: String): File {
        val workingDirectory =
            requireNotNull(System.getProperty("user.dir")) {
                "Missing user.dir for source boundary test"
            }
        return File(workingDirectory, "src/main/java/com/valuepilot/app/$name").also {
            assertTrue("Missing source $name at ${it.absolutePath}", it.isFile)
        }
    }
}
