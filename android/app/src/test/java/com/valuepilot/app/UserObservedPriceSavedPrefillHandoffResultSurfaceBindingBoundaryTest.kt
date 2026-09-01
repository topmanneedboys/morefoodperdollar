package com.valuepilot.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class UserObservedPriceSavedPrefillHandoffResultSurfaceBindingBoundaryTest {

    @Test
    fun `binding presents typed attempt and owns only physical result visibility`() {
        val source = source("UserObservedPriceSavedPrefillHandoffResultSurfaceBinding.kt").readText()

        assertTrue(source.contains("UserObservedPriceSavedPrefillHandoffAttemptObserver"))
        assertTrue(source.contains("UserObservedPriceSavedPrefillHandoffSurfacePresenter"))
        assertTrue(source.contains("override fun onAttempt(attempt: UserObservedPriceSavedPrefillHandoffAttempt)"))
        assertTrue(source.contains("presenter.render(attempt)"))
        assertTrue(source.contains("surface.visibility = View.VISIBLE"))
        assertTrue(source.contains("fun clear()"))
        assertTrue(source.contains("surface.visibility = View.GONE"))
        assertTrue(source.contains("if (closed || visible) return"))
    }

    @Test
    fun `binding owns no handoff execution identity draft persistence evidence ranking navigation or price authority`() {
        val source = source("UserObservedPriceSavedPrefillHandoffResultSurfaceBinding.kt").readText()

        listOf(
            "UserObservedPriceSavedPrefillHandoffGate",
            "UserObservedPriceSavedPrefillGate",
            "requestPrefillOrNull",
            "requestPrefillHandoff",
            "SourceProductIdentity",
            ".itemKey",
            ".storeKey",
            ".rawGtin",
            ".storeScope",
            ".merchantKey",
            ".locationKey",
            ".commerceChannelKey",
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
                "Observed-price Saved prefill result binding must not own $forbidden",
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
