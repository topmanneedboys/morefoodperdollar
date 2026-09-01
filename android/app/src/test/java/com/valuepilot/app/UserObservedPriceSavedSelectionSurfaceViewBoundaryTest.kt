package com.valuepilot.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class UserObservedPriceSavedSelectionSurfaceViewBoundaryTest {

    @Test
    fun `physical renderer is typed replaceable hidden by default and owner controlled`() {
        val source = source("UserObservedPriceSavedSelectionSurfaceView.kt").readText()

        assertTrue(source.contains("UserObservedPriceSavedSelectionSurfaceRenderer"))
        assertTrue(source.contains("visibility = View.GONE"))
        assertTrue(source.contains("isSaveEnabled = false"))
        assertEquals(1, Regex("""visibility\s*=""").findAll(source).count())
        assertTrue(
            source.contains(
                "override fun render(state: UserObservedPriceSavedSelectionUiState)"
            )
        )
        assertTrue(source.contains("state.productRows.forEach"))
        assertTrue(source.contains("state.storeRows.forEach"))
        assertTrue(source.contains("action = row.action"))
        assertTrue(source.contains("onSelectionAction?.invoke(action)"))
        assertTrue(source.contains("state.clearSelectionAction"))
        assertTrue(source.contains("state.checkPrefillAction"))
        assertTrue(source.contains("takeIf { onCheckPrefillAction != null }"))
        assertTrue(source.contains("onCheckPrefillAction?.invoke(action)"))
    }

    @Test
    fun `physical renderer owns no identity prefill draft evidence ranking storage or navigation authority`() {
        val source = source("UserObservedPriceSavedSelectionSurfaceView.kt").readText()

        listOf(
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
            "java.net",
            "java.io"
        ).forEach { forbidden ->
            assertFalse(
                "Observed-price Saved selection view must not own $forbidden",
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
