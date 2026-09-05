package com.valuepilot.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MainActivitySavedPriceHandoffBoundaryTest {

    @Test
    fun savedProductCheckPriceUsesTheExistingGoodPriceRouteWithOnlyANamePrefill() {
        val source = source().readText()

        listOf(
            "is PracticalShoppingSavedSurfaceAction.CheckProductPrice",
            "openGoodPriceForSavedProduct(action.displayName)",
            "private fun openGoodPriceForSavedProduct(displayName: String)",
            "GoodPriceActivityPrefill.sanitize(displayName)",
            "GoodPriceActivity.EXTRA_PRODUCT_NAME",
            "saved identity itself",
            "Good Price still requires fresh user-entered evidence"
        ).forEach { required ->
            assertTrue("Expected saved-product Good Price handoff: $required", source.contains(required))
        }
    }

    @Test
    fun savedProductHandoffDoesNotAddPriceOrNetworkAuthorityToMainActivity() {
        val source = source().readText()

        listOf(
            "PracticalShoppingPlanner",
            "Money.parse",
            "HttpURLConnection",
            "AuthorizedOfferSnapshot",
            "INTERNET"
        ).forEach { forbidden ->
            assertFalse(
                "Saved-product handoff must not own $forbidden",
                source.contains(forbidden)
            )
        }
    }

    private fun source(): File {
        val workingDirectory =
            requireNotNull(System.getProperty("user.dir")) { "Missing user.dir for source boundary test" }
        return File(workingDirectory, "src/main/java/com/valuepilot/app/MainActivity.kt").also {
            assertTrue("Missing source at ${it.absolutePath}", it.isFile)
        }
    }
}
