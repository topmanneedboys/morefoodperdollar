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
            "homeExperience.onFindOfflineCatalogMatch = null",
            "homeExperience.onChickenChoice = null",
            "homeExperience.onExtraStopMinimumSavingsChoice = null",
            "homeExperience.onEditItemDetails = null",
            "homeExperience.onCompare = null",
            "homeExperience.onGoodPrice = null",
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
            "private var offlineCatalogDialog: AlertDialog? = null",
            "private fun dismissHomeItemDetailsDialog()",
            "dismissHomeItemDetailsDialog()",
            "offlineCatalogDialog?.dismiss()",
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
    fun offlineCatalogLookupIsBoundedToHomeAndIgnoresStaleCompletions() {
        val source = source().readText()

        listOf(
            "private fun showOfflineCatalogMatches(token: String)",
            "private fun cancelOfflineCatalogLookup()",
            "private var offlineCatalogLookup: Future<*>? = null",
            "offlineCatalogLookup?.cancel(true)",
            "offlineCatalogLookup = searchExecutor.submit",
            "cancelOfflineCatalogLookup()",
            "BundledOfflineCatalog.discoverSupportedRegions(",
            "rawQuery = query",
            "canonicalizer = JvmTextCanonicalizer",
            "PracticalShoppingHomeOfflineCatalogPresentation.from(",
            "showOfflineCatalogResult(",
            "home_unknown_find_matches_title",
            "getString(R.string.home_unknown_find_matches_title, query)",
            "getString(R.string.home_unknown_find_matches_title, token)",
            "setSingleChoiceItems(labels, -1)",
            "PracticalShoppingHomeOfflineCatalogSelection.replaceUnknownToken(",
            "home_offline_catalog_use_match",
            "home_offline_catalog_match_apply_failed",
            "resultDialog.setMessage(getString(R.string.home_offline_catalog_match_apply_failed))",
            "resultDialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false",
            "requestId != offlineCatalogRequestId",
            "offlineCatalogDialog !== dialog",
            "OFFLINE_CATALOG_MAX_AGE_MILLIS",
            "home_offline_catalog_unavailable"
        ).forEach { required ->
            assertTrue("Expected safe offline catalog Home lookup boundary: $required", source.contains(required))
        }

        assertTrue(
            "Home should expose every supported metro snapshot through one bounded lookup",
            source.contains("BundledOfflineCatalog.discoverSupportedRegions(")
        )
        assertTrue(
            "Home must not silently pin offline discovery to the GTA snapshot",
            !source.contains("BundledOfflineCatalogRegion.GTA")
        )

        listOf(
            "URL(",
            "HttpURLConnection",
            "startActivity(Intent.ACTION_VIEW",
            "PracticalShoppingPlanner",
            "Money.parse"
        ).forEach { forbidden ->
            assertTrue("Expected no network/planner authority in Home lookup binding: $forbidden", !source.contains(forbidden))
        }
    }

    @Test
    fun homeDetailsDialogClosesWhenItsItemDisappearsFromTheLatestProjection() {
        val source = source().readText()

        listOf(
            "homeItemDetailsDialog?.isShowing == true",
            "practicalShoppingHomeItemDetailsDialogShouldDismiss(",
            "activeItemKey = homeItemDetailsItemKey",
            "visibleItemKeys = homeState.items.map { it.key }",
            "dismissHomeItemDetailsDialog()"
        ).forEach { required ->
            assertTrue("Expected stale Home item-details dismissal: $required", source.contains(required))
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

    @Test
    fun homeRefreshesPrivateComparisonMemoryOnResumeWithoutOwningPlannerAuthority() {
        val source = source().readText()

        listOf(
            "homePrivateMemoryStore",
            "homePrivateMemoryState = CompareHerePrivatePriceMemoryState.empty()",
            "homePrivateMemoryLoadIssue",
            "refreshHomePrivateMemory()",
            "val loaded = homePrivateMemoryStore.load()",
            "val next = loaded.state ?: CompareHerePrivatePriceMemoryState.empty()",
            "val nextIssue = loaded.issue",
            "nextIssue == homePrivateMemoryLoadIssue",
            "PracticalShoppingHomeRenderer.render(",
            "homePrivateMemoryState",
            "privateMemoryStatus =",
            "PracticalShoppingHomePrivateMemoryStatus.UNAVAILABLE"
        ).forEach { required ->
            assertTrue("Expected Home private-history refresh boundary: $required", source.contains(required))
        }

        listOf(
            "PracticalShoppingPlanner",
            "Money.parse",
            "HttpURLConnection",
            "INTERNET"
        ).forEach { forbidden ->
            assertTrue(
                "Home private-history refresh must not add business/network authority: $forbidden",
                !source.contains(forbidden)
            )
        }
    }

    @Test
    fun homeGoodPriceActionOpensTheDedicatedLocalScreen() {
        val source = source().readText()

        listOf(
            "homeExperience.onGoodPrice = { openGoodPriceCheck() }",
            "private fun openGoodPriceCheck()",
            "Intent(this, GoodPriceActivity::class.java)",
            "dismissHomeItemDetailsDialog()"
        ).forEach { required ->
            assertTrue("Expected Home good-price navigation boundary: $required", source.contains(required))
        }

        listOf(
            "GoodPriceCheckRouteCoordinator",
            "CompareHerePriceMemoryEvaluator",
            "HttpURLConnection"
        ).forEach { forbidden ->
            assertTrue(
                "MainActivity must not own good-price business/network authority: $forbidden",
                !source.contains(forbidden)
            )
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
