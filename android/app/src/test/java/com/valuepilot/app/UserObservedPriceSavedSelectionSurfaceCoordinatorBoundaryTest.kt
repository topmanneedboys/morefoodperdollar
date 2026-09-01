package com.valuepilot.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class UserObservedPriceSavedSelectionSurfaceCoordinatorBoundaryTest {

    @Test
    fun `coordinator only wires typed surface actions to route session and external prefill owner`() {
        val source = source("UserObservedPriceSavedSelectionSurfaceCoordinator.kt").readText()

        assertTrue(source.contains("UserObservedPriceSavedSelectionSurfaceView"))
        assertTrue(source.contains("UserObservedPriceSavedSelectionRouteSession"))
        assertTrue(source.contains("surface.onSelectionAction = ::forwardSelectionAction"))
        assertTrue(source.contains("routeSession.onSelectionAction(action)"))
        assertTrue(source.contains("surface.onCheckPrefillAction = ::forwardCheckPrefillAction"))
        assertTrue(source.contains("onCheckPrefillAction(action)"))
        assertTrue(source.contains("surface.onSelectionAction = null"))
        assertTrue(source.contains("surface.onCheckPrefillAction = null"))
        assertFalse(source.contains("routeSession.close()"))
    }

    @Test
    fun `coordinator owns no route presentation prefill execution persistence evidence ranking navigation or price authority`() {
        val source = source("UserObservedPriceSavedSelectionSurfaceCoordinator.kt").readText()

        listOf(
            "onRouteVisibilityChanged",
            "onSavedSnapshotChanged",
            "requestPrefillOrNull",
            "UserObservedPriceSavedPrefillHandoffGate",
            "UserObservedPriceSavedPrefillGate",
            "UserObservedPriceSavedSelectionSurfacePresenter",
            "UserObservedPriceSavedSelectionUiProjector",
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
            "java.net"
        ).forEach { forbidden ->
            assertFalse(
                "Observed-price Saved selection coordinator must not own $forbidden",
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
