package com.valuepilot.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class UserObservedPriceConfirmationDraftPriceInputSurfaceViewTest {

    @Test
    fun `physical price editor starts inactive and delegates raw text through exact adapter`() {
        val source = source().readText()

        listOf(
            "class UserObservedPriceConfirmationDraftPriceInputSurfaceView",
            "visibility = View.GONE",
            "isSaveEnabled = false",
            "var onCommit: ((Money) -> Unit)? = null",
            "UserObservedPriceConfirmationDraftPriceTextInputAdapter.adapt(",
            "amountText = amountEditor.text.toString()",
            "currencyCodeText = currencyEditor.text.toString()",
            "onCommit?.invoke(result.price)",
            "fun clearInput()",
            "amountEditor.setText(\"\")",
            "currencyEditor.setText(\"\")"
        ).forEach { required ->
            assertTrue("Expected explicit price input binding $required", source.contains(required))
        }

        assertFalse("Amount must not be prefilled", source.contains("amountEditor.setText(\"5.99\")"))
        assertFalse("Currency must not be defaulted", source.contains("currencyEditor.setText(\"CAD\")"))
    }

    @Test
    fun `physical price editor owns no money parsing store inference proof metadata or factual authority`() {
        val source = source().readText()

        listOf(
            "Currency.getInstance",
            "Money.parse(",
            "Money(",
            "PracticalShoppingStoreIdentityScope",
            "merchantKey",
            "locationKey",
            "UserObservedPriceConfirmationDraftRouteSession",
            "UserObservedPriceConfirmationDraftFinalizer",
            "UserObservedPriceConfirmationTransaction",
            "UserObservedPriceConfirmationAndroidSession",
            "UserProvidedPriceProofArtifact",
            "System.currentTimeMillis",
            "UUID",
            "MessageDigest",
            "ShoppingEvidence(",
            "EvidenceClaim(",
            "CURRENT_PRICE",
            "ProductionCurrentPrice",
            "ProviderProductionAuthorization",
            "OcrScanner",
            "Bitmap",
            "SharedPreferences",
            "Locale.",
            "java.net"
        ).forEach { forbidden ->
            assertFalse("Price input surface must not own $forbidden", source.contains(forbidden))
        }
    }

    @Test
    fun `shell binds editor only to exact confirmation route and clears raw text when route leaves`() {
        val activity = appFile("app/src/main/java/com/valuepilot/app/MainActivity.kt").readText()
        val layout = appFile("app/src/main/res/layout/activity_shell.xml").readText()

        assertTrue(
            activity.contains("private lateinit var observedPriceConfirmationDraftPriceInputExperience:") &&
                activity.contains("UserObservedPriceConfirmationDraftPriceInputSurfaceView")
        )
        assertTrue(
            activity.contains(
                "observedPriceConfirmationDraftPriceInputExperience =\n            findViewById(R.id.observedPriceConfirmationDraftPriceInputExperience)"
            )
        )
        assertTrue(
            activity.contains(
                "observedPriceConfirmationDraftPriceInputExperience.onCommit =\n            observedPriceConfirmationDraftRouteCoordinator::onPriceInput"
            )
        )
        assertTrue(
            activity.contains(
                "observedPriceConfirmationDraftPriceInputExperience.visibility =\n            if (observedPriceConfirmationDraftVisible) View.VISIBLE else View.GONE"
            )
        )
        assertTrue(
            activity.contains(
                "if (!observedPriceConfirmationDraftVisible) {\n            observedPriceConfirmationDraftPriceInputExperience.clearInput()\n        }"
            )
        )
        assertTrue(activity.contains("observedPriceConfirmationDraftPriceInputExperience.onCommit = null"))

        assertTrue(layout.contains("<com.valuepilot.app.UserObservedPriceConfirmationDraftPriceInputSurfaceView"))
        assertTrue(layout.contains("android:id=\"@+id/observedPriceConfirmationDraftPriceInputExperience\""))
        assertTrue(
            layout
                .substringAfter(
                    "android:id=\"@+id/observedPriceConfirmationDraftPriceInputExperience\""
                )
                .substringBefore("/>")
                .contains("android:visibility=\"gone\"")
        )

        assertFalse(
            "Saved route must not activate the editor",
            activity.contains(
                "observedPriceConfirmationDraftPriceInputExperience.visibility = if (savedVisible) View.VISIBLE else View.GONE"
            )
        )
        assertFalse(
            "Selection route must not activate the editor",
            activity.contains(
                "observedPriceConfirmationDraftPriceInputExperience.visibility = if (observedPriceSelectionVisible) View.VISIBLE else View.GONE"
            )
        )
        assertFalse(
            "MainActivity must not bypass the route coordinator into the private draft session",
            activity.contains(".onPriceChanged(")
        )
    }

    private fun source(): File =
        appFile(
            "app/src/main/java/com/valuepilot/app/UserObservedPriceConfirmationDraftPriceInputSurfaceView.kt"
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
