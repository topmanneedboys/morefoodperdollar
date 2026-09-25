package com.valuepilot.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class UserObservedPriceSavedPrefillHandoffSurfaceViewBoundaryTest {

    @Test
    fun `physical renderer is typed replaceable hidden by default and owner controlled`() {
        val source = source("UserObservedPriceSavedPrefillHandoffSurfaceView.kt").readText()

        assertTrue(source.contains("UserObservedPriceSavedPrefillHandoffSurfaceRenderer"))
        assertTrue(source.contains("visibility = View.GONE"))
        assertTrue(source.contains("isSaveEnabled = false"))
        assertEquals(1, Regex("""visibility\s*=""").findAll(source).count())
        assertTrue(
            source.contains(
                "override fun render(state: UserObservedPriceSavedPrefillHandoffUiState)"
            )
        )
        assertTrue(source.contains("state.headline"))
        assertTrue(source.contains("state.message"))
        assertTrue(source.contains("state.productName"))
        assertTrue(source.contains("state.storeDisplayName"))
        assertFalse(source.contains("Button"))
        assertFalse(source.contains("setOnClickListener"))
    }

    @Test
    fun `physical renderer owns no technical identity execution evidence ranking storage or navigation authority`() {
        val source = source("UserObservedPriceSavedPrefillHandoffSurfaceView.kt").readText()

        listOf(
            "UserObservedPriceSavedPrefillHandoffGate",
            "UserObservedPriceSavedPrefillGate",
            "requestPrefillOrNull",
            ".itemKey",
            ".storeKey",
            ".rawGtin",
            ".storeScope",
            ".merchantKey",
            ".locationKey",
            ".commerceChannelKey",
            "SourceProductIdentity",
            "GtinValidation",
            "PracticalShoppingSavedAndroidSession",
            "PracticalShoppingSavedExactPreferenceLocalStore",
            "PracticalShoppingSavedDisplayMetadataLocalStore",
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
            "EvidenceBackedUnitValuePolicy",
            "Money",
            "ByteArray",
            "System.currentTimeMillis",
            "UUID",
            "SharedPreferences",
            "AppShell",
            "MainActivity",
            "Intent",
            "startActivity",
            "java.net",
            "java.io"
        ).forEach { forbidden ->
            assertFalse(
                "Observed-price prefill handoff result view must not own $forbidden",
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
