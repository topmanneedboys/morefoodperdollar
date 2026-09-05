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
            "homeExperience.onChooseExactProduct = null",
            "homeExperience.onChickenChoice = null",
            "homeExperience.onExtraStopMinimumSavingsChoice = null",
            "homeExperience.onEditItemDetails = null",
            "homeExperience.onAddObservedPrice = null",
            "homeExperience.onCompare = null",
            "homeExperience.onReviewPrivateMemory = null",
            "homeExperience.onGoodPrice = null",
            "homeExperience.onShopAgain = null",
            "if (::rememberConfirmedChoiceAndroidSession.isInitialized)",
            "rememberConfirmedChoiceAndroidSession.close()",
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
            "privatePriceHistoryDialog?.dismiss()",
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
            "private fun showOfflineCatalogMatches(",
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
            "PracticalShoppingHomeOfflineCatalogComparisonSelection.displayNameFor(",
            "home_offline_catalog_open_compare",
            "home_offline_catalog_open_compare_description",
            "getButton(AlertDialog.BUTTON_NEUTRAL)",
            "openComparisonWithSharedText(displayName)",
            "setPositiveButton(",
            "home_offline_catalog_replace_list_word",
            "home_offline_catalog_replace_list_word_description",
            "home_offline_catalog_match_apply_failed",
            "resultDialog.setMessage(getString(R.string.home_offline_catalog_match_apply_failed))",
            "resultDialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false",
            "exactProductItemKey: ShoppingItemKey?",
            "OfflineCatalogLookup(",
            "PracticalShoppingHomeOfflineCatalogExactSelection.confirm(",
            "rememberConfirmedChoiceAndroidSession.remember(chosen.rememberRequest)",
            "home_offline_catalog_confirm_exact_message",
            "home_offline_catalog_confirm_exact_product",
            "home_offline_catalog_saved_title",
            "home_offline_catalog_save_failed_title",
            "requestId != offlineCatalogRequestId",
            "offlineCatalogDialog !== dialog",
            "OFFLINE_CATALOG_MAX_AGE_MILLIS",
            "home_offline_catalog_unavailable"
        ).forEach { required ->
            assertTrue("Expected safe offline catalog Home lookup boundary: $required", source.contains(required))
        }
        assertTrue(
            "The catalog action must not use an ambiguous exact-product-sounding label",
            !source.contains("home_offline_catalog_use_match")
        )

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
            "homeExperience.onShopAgain = {",
            "PracticalShoppingHomeSession.shopAgain(homeSessionState)",
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

    @Test
    fun missingHomePriceActionPrefillsOnlyAnUntrustedProductName() {
        val source = source().readText()

        listOf(
            "homeExperience.onAddObservedPrice = { itemKey ->",
            "openGoodPriceForHomeItem(itemKey)",
            "private fun openGoodPriceForHomeItem(itemKey: ShoppingItemKey)",
            "homeSessionState.model.ui.items.firstOrNull { it.key == itemKey }",
            "GoodPriceActivity.EXTRA_PRODUCT_NAME",
            "item.name",
            "The name is an untrusted prefill only"
        ).forEach { required ->
            assertTrue("Expected actionable missing-price handoff: $required", source.contains(required))
        }

        listOf(
            "PracticalShoppingPlanner",
            "Money.parse",
            "HttpURLConnection",
            "AuthorizedOfferSnapshot"
        ).forEach { forbidden ->
            assertTrue(
                "Missing-price handoff must not own shopping authority: $forbidden",
                !source.contains(forbidden)
            )
        }
    }

    @Test
    fun homePrivateMemoryReviewActionUsesTheLocalHistoryRoute() {
        val source = source().readText()

        listOf(
            "homeExperience.onReviewPrivateMemory = { reviewPrivatePriceHistory() }",
            "homeExperience.onReviewPrivateMemory = null",
            "private fun openComparison()",
            "Intent(this, ComparisonActivity::class.java)",
            "private fun reviewPrivatePriceHistory()",
            "PracticalShoppingPrivatePriceHistoryPresentation.from(homePrivateMemoryState)",
            ".setNeutralButton(R.string.home_private_memory_clear, null)",
            "privatePriceHistoryDialog?.dismiss()",
            ".setMessage(presentation.message)",
            ".setPositiveButton(R.string.home_compare_secondary)",
            "dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener",
            "confirmClearPrivatePriceHistory()"
        ).forEach { required ->
            assertTrue("Expected Home private-history review boundary: $required", source.contains(required))
        }

        listOf(
            "PracticalShoppingPlanner",
            "Money.parse",
            "HttpURLConnection"
        ).forEach { forbidden ->
            assertTrue(
                "Home private-history review must not own shopping authority: $forbidden",
                !source.contains(forbidden)
            )
        }
    }

    @Test
    fun homePrivateMemoryClearRequiresConfirmationAndOnlyResetsLocalContext() {
        val source = source().readText()

        listOf(
            "private var privatePriceHistoryClearDialog: AlertDialog? = null",
            "private fun confirmClearPrivatePriceHistory()",
            "homePrivateMemoryState.entries.isEmpty()",
            "private fun clearPrivatePriceHistory()",
            "val result = homePrivateMemoryStore.clear()",
            "setPositiveButton(R.string.home_private_memory_clear_confirm)",
            "homePrivateMemoryState = CompareHerePrivatePriceMemoryState.empty()",
            "homePrivateMemoryLoadIssue = null",
            "privatePriceHistoryDialog?.dismiss()",
            "private fun showPrivatePriceHistoryClearError()",
            "R.string.home_private_memory_clear_error"
        ).forEach { required ->
            assertTrue("Expected explicit, local private-history deletion boundary: $required", source.contains(required))
        }

        listOf(
            "PracticalShoppingPlanner",
            "Money.parse",
            "HttpURLConnection",
            "AuthorizedOfferSnapshot"
        ).forEach { forbidden ->
            assertTrue(
                "Private-history deletion must not add shopping/network authority: $forbidden",
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
