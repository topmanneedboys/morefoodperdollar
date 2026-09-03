package com.valuepilot.app

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MainActivityHomeLifecycleBoundaryTest {

    @Test
    fun destroyingActivityReleasesHomeAndBasketOwners() {
        val source = source().readText()

        listOf(
            "if (::homeExperience.isInitialized)",
            "homeExperience.onQueryChanged = null",
            "homeExperience.onSubmit = null",
            "homeExperience.onRemoveItem = null",
            "homeExperience.onRemoveUnknownItem = null",
            "homeExperience.onChickenChoice = null",
            "homeExperience.onExtraStopMinimumSavingsChoice = null",
            "homeExperience.onEditItemDetails = null",
            "homeExperience.onCompare = null",
            "if (::basketExperience.isInitialized)",
            "basketExperience.onAction = null"
        ).forEach { required ->
            assertTrue("Expected detached Home/Basket owner cleanup: $required", source.contains(required))
        }
    }

    @Test
    fun homeDetailsDialogIsDismissedOutsideHomeAndOnActivityTeardown() {
        val source = source().readText()

        listOf(
            "private var homeItemDetailsDialog: AlertDialog? = null",
            "private fun dismissHomeItemDetailsDialog()",
            "dismissHomeItemDetailsDialog()",
            "homeItemDetailsDialog?.dismiss()",
            "homeItemDetailsDialog = null",
            "homeItemDetailsDialog = dialog",
            "dialog.setOnDismissListener {",
            "if (homeItemDetailsDialog === dialog)",
            "if (state.route != AppRoute.HOME)"
        ).forEach { required ->
            assertTrue("Expected Home item-details dialog lifecycle boundary: $required", source.contains(required))
        }
    }

    @Test
    fun homeDetailsDraftRestoresOnlyThroughEphemeralActivityState() {
        val source = source().readText()

        listOf(
            "restoreHomeItemDetailsDialog(savedInstanceState)",
            "if (homeItemDetailsDialog?.isShowing == true)",
            "STATE_HOME_DETAILS_ITEM_KEY",
            "STATE_HOME_DETAILS_PACKAGE_COUNT",
            "STATE_HOME_DETAILS_BRAND",
            "STATE_HOME_DETAILS_EXACT_PRODUCT",
            "homeItemDetailsPackageInput?.text?.toString().orEmpty()",
            "homeItemDetailsBrandInput?.text?.toString().orEmpty()",
            "homeItemDetailsExactProduct?.isChecked == true",
            "if (shellState.route != AppRoute.HOME) return",
            "draftOverride =",
            "draftOverride ?: PracticalShoppingHomeItemDetailsEditor.initialDraft(current)"
        ).forEach { required ->
            assertTrue("Expected ephemeral Home item-details draft restore: $required", source.contains(required))
        }
    }

    private fun source(): File {
        val workingDirectory =
            requireNotNull(System.getProperty("user.dir")) {
                "Missing user.dir for source boundary test"
            }
        return File(
            workingDirectory,
            "src/main/java/com/valuepilot/app/MainActivity.kt"
        ).also {
            assertTrue("Missing source at ${it.absolutePath}", it.isFile)
        }
    }
}
