package com.valuepilot.app

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.valuepilot.core.OfflineCatalogDiscoveryResult
import com.valuepilot.core.ShoppingItemKey
import java.util.concurrent.Executors
import java.util.concurrent.Future

class MainActivity : AppCompatActivity() {

    private data class OfflineCatalogLookup(
        val discoveryResult: OfflineCatalogDiscoveryResult,
        val presentation: PracticalShoppingHomeOfflineCatalogPresentation
    )

    private var shellState = AppShellState.initial()
    private var homeSessionState = PracticalShoppingHomeSession.initialState()
    private val homeSessionStore by lazy(LazyThreadSafetyMode.NONE) {
        AndroidPracticalShoppingHomeSessionStore(applicationContext)
    }
    private val homePreferenceStore by lazy(LazyThreadSafetyMode.NONE) {
        AndroidPracticalShoppingHomePreferenceStore(applicationContext)
    }
    private val homePrivateMemoryStore by lazy(LazyThreadSafetyMode.NONE) {
        CompareHerePrivatePriceMemoryAndroidStore(applicationContext)
    }
    private var homePrivateMemoryState = CompareHerePrivatePriceMemoryState.empty()
    private var homePrivateMemoryLoadIssue: CompareHerePrivatePriceMemoryStoreIssue? = null

    private val searchController = UniversalSearchController()
    private var searchState = searchController.initialState()
    private val searchProvider: ProductSearchProvider = LocalSampleProductSearchProvider
    private val searchExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    private lateinit var shellRoot: View
    private lateinit var bottomNavArea: View
    private lateinit var bottomNavigation: BottomNavigationView
    private lateinit var screenEyebrow: TextView
    private lateinit var screenTitle: TextView
    private lateinit var screenBody: TextView
    private lateinit var screenFootnote: TextView
    private lateinit var primaryAction: Button
    private lateinit var sourcesLicencesButton: MaterialButton
    private lateinit var dataStatusButton: MaterialButton
    private lateinit var homeExperience: PracticalShoppingHomeSurfaceView
    private lateinit var basketExperience: PracticalShoppingBasketSurfaceView
    private lateinit var searchExperience: View
    private lateinit var searchInput: TextInputEditText
    private lateinit var searchButton: MaterialButton
    private lateinit var searchIdentityButton: MaterialButton
    private lateinit var searchStatus: TextView
    private lateinit var searchProgress: ProgressBar
    private lateinit var searchResultsHeading: TextView
    private lateinit var searchResultsContainer: LinearLayout
    private lateinit var savedExperience: PracticalShoppingSavedSurfaceView
    private lateinit var savedStapleLaunchExperience: PracticalShoppingSavedStapleLaunchView
    private lateinit var savedObservedPriceLaunchExperience: PracticalShoppingSavedObservedPriceLaunchView
    private lateinit var observedPriceSavedSelectionExperience: UserObservedPriceSavedSelectionSurfaceView
    private lateinit var observedPriceSavedPrefillResultExperience:
        UserObservedPriceSavedPrefillHandoffSurfaceView
    private lateinit var observedPriceConfirmationDraftExperience:
        UserObservedPriceConfirmationDraftSurfaceView
    private lateinit var observedPriceConfirmationDraftPriceInputExperience:
        UserObservedPriceConfirmationDraftPriceInputSurfaceView
    private lateinit var observedPriceConfirmationDraftObservedAtInputExperience:
        UserObservedPriceConfirmationDraftObservedAtInputSurfaceView
    private lateinit var observedPriceConfirmationDraftProofReferenceInputExperience:
        UserObservedPriceConfirmationDraftProofReferenceInputSurfaceView
    private lateinit var observedPriceConfirmationDraftProofContentSelectionExperience:
        UserObservedPriceConfirmationDraftProofContentSelectionSurfaceView
    private lateinit var observedPriceConfirmationActionExperience:
        UserObservedPriceConfirmationActionSurfaceView
    private lateinit var stapleWatchSetupExperience: StapleWatchSavedSelectionSurfaceView
    private lateinit var stapleWatchPolicyExperience: StapleWatchPolicyDraftSurfaceView
    private lateinit var stapleWatchResultExperience: StapleWatchSurfaceView
    private lateinit var savedRouteCoordinator: PracticalShoppingSavedRouteCoordinator
    private lateinit var observedPriceSavedSelectionCoordinator:
        UserObservedPriceSavedSelectionCompositionCoordinator
    private lateinit var observedPriceSavedSelectionSurfaceCoordinator:
        UserObservedPriceSavedSelectionSurfaceCoordinator
    private lateinit var observedPriceSavedPrefillResultSurfaceBinding:
        UserObservedPriceSavedPrefillHandoffResultSurfaceBinding
    private lateinit var observedPriceConfirmationDraftRouteCoordinator:
        UserObservedPriceSavedConfirmationDraftRouteCoordinator
    private lateinit var observedPriceConfirmationDraftProofContentSelectionCoordinator:
        UserObservedPriceConfirmationDraftProofContentSelectionCoordinator
    private lateinit var observedPriceConfirmationDraftProofContentPicker:
        AndroidUserObservedPriceProofContentPicker
    private lateinit var observedPriceConfirmationActionPresentationController:
        UserObservedPriceConfirmationActionPresentationController
    private lateinit var observedPriceConfirmationAndroidSession:
        UserObservedPriceConfirmationAndroidSession
    private lateinit var observedPriceConfirmationActionCoordinator:
        UserObservedPriceConfirmationActionCoordinator
    private lateinit var stapleWatchForegroundEvaluationInputHost: StapleWatchForegroundEvaluationInputHost
    private lateinit var stapleWatchForegroundResultSurfaceBinding:
        StapleWatchForegroundResultSurfaceBinding
    private lateinit var stapleWatchSavedDisplayMetadataCompositionCoordinator:
        StapleWatchSavedDisplayMetadataCompositionCoordinator
    private lateinit var stapleWatchFactResolutionHost: StapleWatchFactResolutionHost
    private lateinit var stapleWatchSetupCoordinator: StapleWatchSavedSetupCompositionCoordinator
    private lateinit var stapleWatchPolicySetupCoordinator: StapleWatchPolicySetupCompositionCoordinator
    private lateinit var rememberConfirmedChoiceAndroidSession:
        PracticalShoppingRememberConfirmedChoiceAndroidSession

    private var comparisonActivityOpen = false
    private var homeItemDetailsDialog: AlertDialog? = null
    private var homeItemDetailsItemKey: ShoppingItemKey? = null
    private var homeItemDetailsPackageInput: TextInputEditText? = null
    private var homeItemDetailsBrandInput: TextInputEditText? = null
    private var homeItemDetailsExactProduct: CheckBox? = null
    private var offlineCatalogDialog: AlertDialog? = null
    private var pendingExactProductLabel: String? = null
    private var dataStatusDialog: AlertDialog? = null
    private var privatePriceHistoryDialog: AlertDialog? = null
    private var privatePriceHistoryClearDialog: AlertDialog? = null
    private var privatePriceHistoryExportDialog: AlertDialog? = null
    private var offlineCatalogLookup: Future<*>? = null
    private var offlineCatalogRequestId = 0L
    private var searchIdentityDialog: AlertDialog? = null
    private var searchIdentityLookup: Future<*>? = null
    private var searchIdentityRequestId = 0L
    private var suppressSearchInputCallback = false
    private var restoreSearchOnNextOpen = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_shell)

        shellRoot = findViewById(R.id.shellRoot)
        bottomNavArea = findViewById(R.id.bottomNavArea)
        bottomNavigation = findViewById(R.id.bottomNavigation)
        screenEyebrow = findViewById(R.id.screenEyebrow)
        screenTitle = findViewById(R.id.screenTitle)
        screenBody = findViewById(R.id.screenBody)
        screenFootnote = findViewById(R.id.screenFootnote)
        primaryAction = findViewById(R.id.primaryAction)
        sourcesLicencesButton = findViewById(R.id.sourcesLicencesButton)
        dataStatusButton = findViewById(R.id.dataStatusButton)
        homeExperience = findViewById(R.id.homeExperience)
        basketExperience = findViewById(R.id.basketExperience)
        searchExperience = findViewById(R.id.searchExperience)
        searchInput = findViewById(R.id.searchInput)
        searchButton = findViewById(R.id.searchButton)
        searchIdentityButton = findViewById(R.id.searchIdentityButton)
        searchStatus = findViewById(R.id.searchStatus)
        searchProgress = findViewById(R.id.searchProgress)
        searchResultsHeading = findViewById(R.id.searchResultsHeading)
        searchResultsContainer = findViewById(R.id.searchResultsContainer)
        savedExperience = findViewById(R.id.savedExperience)
        savedStapleLaunchExperience = findViewById(R.id.savedStapleLaunchExperience)
        savedObservedPriceLaunchExperience = findViewById(R.id.savedObservedPriceLaunchExperience)
        observedPriceSavedSelectionExperience = findViewById(R.id.observedPriceSavedSelectionExperience)
        observedPriceSavedPrefillResultExperience =
            findViewById(R.id.observedPriceSavedPrefillResultExperience)
        observedPriceConfirmationDraftExperience =
            findViewById(R.id.observedPriceConfirmationDraftExperience)
        observedPriceConfirmationDraftPriceInputExperience =
            findViewById(R.id.observedPriceConfirmationDraftPriceInputExperience)
        observedPriceConfirmationDraftObservedAtInputExperience =
            findViewById(R.id.observedPriceConfirmationDraftObservedAtInputExperience)
        observedPriceConfirmationDraftProofReferenceInputExperience =
            findViewById(R.id.observedPriceConfirmationDraftProofReferenceInputExperience)
        observedPriceConfirmationDraftProofContentSelectionExperience =
            findViewById(R.id.observedPriceConfirmationDraftProofContentSelectionExperience)
        observedPriceConfirmationActionExperience =
            findViewById(R.id.observedPriceConfirmationActionExperience)
        stapleWatchSetupExperience = findViewById(R.id.stapleWatchSetupExperience)
        stapleWatchPolicyExperience = findViewById(R.id.stapleWatchPolicyExperience)
        stapleWatchResultExperience = findViewById(R.id.stapleWatchResultExperience)

        installSystemBarInsets()
        shellState = restoreShellState(savedInstanceState)
        homeSessionState = restoreHomeState(savedInstanceState)
        refreshHomePrivateMemory()
        searchState = restoreSearchState(savedInstanceState)
        configureHomeUi()
        configureBasketUi()
        configureSearchUi()
        configureSavedUi()

        bottomNavigation.setOnItemSelectedListener { item ->
            val tab = when (item.itemId) {
                R.id.navHome -> AppPrimaryTab.HOME
                R.id.navSearch -> AppPrimaryTab.SEARCH
                R.id.navBasket -> AppPrimaryTab.BASKET
                R.id.navSaved -> AppPrimaryTab.SAVED
                else -> null
            } ?: return@setOnItemSelectedListener false

            dispatch(AppShellIntent.SelectPrimary(tab))
            true
        }

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (
                        shellState.route == AppRoute.OBSERVED_PRICE_SAVED_SELECTION ||
                        shellState.route == AppRoute.OBSERVED_PRICE_CONFIRMATION_DRAFT ||
                        shellState.route == AppRoute.STAPLE_WATCH_SETUP ||
                        shellState.route == AppRoute.STAPLE_WATCH_POLICY
                    ) {
                        dispatch(AppShellIntent.NavigateBack)
                    } else {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                        isEnabled = true
                    }
                }
            }
        )

        primaryAction.setOnClickListener { openComparison() }
        sourcesLicencesButton.setOnClickListener { showSourcesLicences() }
        dataStatusButton.setOnClickListener { showDataStatus() }

        bottomNavigation.selectedItemId = menuIdFor(shellState.selectedPrimaryTab)
        renderShell(shellState)
        restoreHomeItemDetailsDialog(savedInstanceState)
    }

    override fun onResume() {
        super.onResume()

        refreshHomePrivateMemory()

        if (comparisonActivityOpen && shellState.route == AppRoute.COMPARE) {
            comparisonActivityOpen = false
            dispatch(AppShellIntent.NavigateBack)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_PRIMARY_TAB, shellState.selectedPrimaryTab.name)

        val homeSnapshot = PracticalShoppingHomeSession.snapshot(homeSessionState)
        outState.putString(STATE_HOME_QUERY, homeSnapshot.query)
        outState.putBoolean(STATE_HOME_WAS_SUBMITTED, homeSnapshot.wasSubmitted)
        outState.putString(STATE_HOME_CHICKEN_CHOICE, homeSnapshot.chickenChoice?.name)
        outState.putString(
            STATE_HOME_EXTRA_STOP_MINIMUM_SAVINGS,
            homeSnapshot.extraStopMinimumSavingsChoice.name
        )
        homeSnapshot.requestDetailsLifecycleState?.let { encoded ->
            outState.putByteArray(STATE_HOME_REQUEST_DETAILS, encoded)
        }
        if (homeItemDetailsDialog?.isShowing == true) {
            homeItemDetailsItemKey?.let { itemKey ->
                outState.putString(STATE_HOME_DETAILS_ITEM_KEY, itemKey.value)
                outState.putString(
                    STATE_HOME_DETAILS_PACKAGE_COUNT,
                    homeItemDetailsPackageInput?.text?.toString().orEmpty()
                )
                outState.putString(
                    STATE_HOME_DETAILS_BRAND,
                    homeItemDetailsBrandInput?.text?.toString().orEmpty()
                )
                outState.putBoolean(
                    STATE_HOME_DETAILS_EXACT_PRODUCT,
                    homeItemDetailsExactProduct?.isChecked == true
                )
            }
        }

        outState.putString(STATE_SEARCH_QUERY, searchState.query)
        outState.putBoolean(
            STATE_SEARCH_WAS_SUBMITTED,
            searchState.query.isNotBlank() &&
                searchState.status !in setOf(
                    UniversalSearchStatus.IDLE,
                    UniversalSearchStatus.READY,
                    UniversalSearchStatus.QUERY_TOO_LONG
                )
        )
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        dismissHomeItemDetailsDialog()
        cancelOfflineCatalogLookup()
        offlineCatalogDialog?.dismiss()
        offlineCatalogDialog = null
        pendingExactProductLabel = null
        dismissSearchIdentityDialog()
        dataStatusDialog?.dismiss()
        dataStatusDialog = null
        privatePriceHistoryDialog?.dismiss()
        privatePriceHistoryDialog = null
        privatePriceHistoryClearDialog?.dismiss()
        privatePriceHistoryClearDialog = null
        privatePriceHistoryExportDialog?.dismiss()
        privatePriceHistoryExportDialog = null
        if (::homeExperience.isInitialized) {
            homeExperience.onQueryChanged = null
            homeExperience.onSubmit = null
            homeExperience.onRemoveItem = null
            homeExperience.onRemoveUnknownItem = null
            homeExperience.onFindOfflineCatalogMatch = null
            homeExperience.onChooseExactProduct = null
            homeExperience.onChickenChoice = null
            homeExperience.onExtraStopMinimumSavingsChoice = null
            homeExperience.onEditItemDetails = null
            homeExperience.onAddObservedPrice = null
            homeExperience.onCompare = null
            homeExperience.onReviewPrivateMemory = null
            homeExperience.onExportPrivateMemory = null
            homeExperience.onGoodPrice = null
            homeExperience.onShopAgain = null
        }
        if (::basketExperience.isInitialized) {
            basketExperience.onAction = null
        }
        if (::savedRouteCoordinator.isInitialized) {
            savedExperience.onAction = null
            savedStapleLaunchExperience.onAction = null
            savedObservedPriceLaunchExperience.onAction = null
            observedPriceConfirmationDraftPriceInputExperience.onCommit = null
            observedPriceConfirmationDraftObservedAtInputExperience.onCommit = null
            observedPriceConfirmationDraftProofReferenceInputExperience.onCommit = null
            observedPriceConfirmationDraftProofContentSelectionExperience.onSelectRequested = null
            observedPriceConfirmationActionExperience.onAction = null
            observedPriceConfirmationActionPresentationController.close()
            observedPriceConfirmationAndroidSession.close()
            observedPriceConfirmationDraftProofContentPicker.close()
            observedPriceConfirmationDraftProofContentSelectionCoordinator.close()
            observedPriceSavedSelectionSurfaceCoordinator.close()
            observedPriceSavedPrefillResultSurfaceBinding.close()
            observedPriceConfirmationDraftRouteCoordinator.close()
            stapleWatchSetupExperience.onAction = null
            stapleWatchSetupExperience.onContinueAction = null
            stapleWatchPolicyExperience.onAction = null
            stapleWatchPolicyExperience.onContinueAction = null
            observedPriceSavedSelectionCoordinator.close()
            stapleWatchPolicySetupCoordinator.close()
            stapleWatchSetupCoordinator.close()
            stapleWatchFactResolutionHost.close()
            stapleWatchSavedDisplayMetadataCompositionCoordinator.close()
            stapleWatchForegroundEvaluationInputHost.close()
            savedRouteCoordinator.close()
        }
        if (::rememberConfirmedChoiceAndroidSession.isInitialized) {
            rememberConfirmedChoiceAndroidSession.close()
        }
        searchExecutor.shutdownNow()
        mainHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun installSystemBarInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(shellRoot) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, 0)
            bottomNavArea.setPadding(0, 0, 0, bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(shellRoot)
    }

    private fun restoreShellState(savedInstanceState: Bundle?): AppShellState {
        val savedTab = savedInstanceState
            ?.getString(STATE_PRIMARY_TAB)
            ?.let { runCatching { AppPrimaryTab.valueOf(it) }.getOrNull() }
            ?: AppPrimaryTab.HOME

        return AppShellReducer.reduce(
            AppShellState.initial(),
            AppShellIntent.SelectPrimary(savedTab)
        )
    }

    private fun restoreHomeState(
        savedInstanceState: Bundle?
    ): PracticalShoppingHomeSession.State {
        if (savedInstanceState?.containsKey(STATE_HOME_QUERY) != true) {
            homeSessionStore.load()?.let { persisted ->
                return PracticalShoppingHomeSession.restoreState(persisted)
            }
        }

        val choice =
            savedInstanceState
                ?.getString(STATE_HOME_CHICKEN_CHOICE)
                ?.let { saved ->
                    runCatching {
                        LocalSamplePracticalShoppingDemo.ChickenChoice.valueOf(saved)
                    }.getOrNull()
                }

        val extraStopMinimumSavingsChoice =
            if (savedInstanceState?.containsKey(STATE_HOME_EXTRA_STOP_MINIMUM_SAVINGS) == true) {
                PracticalShoppingHomePreferenceCodec.decode(
                    savedInstanceState.getString(STATE_HOME_EXTRA_STOP_MINIMUM_SAVINGS)
                )
            } else {
                homePreferenceStore.loadExtraStopMinimumSavingsChoice()
            }

        return PracticalShoppingHomeSession.restoreState(
            PracticalShoppingHomeSession.Snapshot(
                query = savedInstanceState?.getString(STATE_HOME_QUERY).orEmpty(),
                wasSubmitted = savedInstanceState?.getBoolean(STATE_HOME_WAS_SUBMITTED, false) ?: false,
                chickenChoice = choice,
                extraStopMinimumSavingsChoice = extraStopMinimumSavingsChoice,
                requestDetailsLifecycleState =
                    savedInstanceState?.getByteArray(STATE_HOME_REQUEST_DETAILS)
            )
        )
    }

    private fun configureHomeUi() {
        homeExperience.onQueryChanged = { rawQuery ->
            homeSessionState =
                PracticalShoppingHomeSession.queryChanged(homeSessionState, rawQuery)
            renderHome()
        }
        homeExperience.onSubmit = { rawQuery ->
            homeSessionState = PracticalShoppingHomeSession.submit(homeSessionState, rawQuery)
            renderHome()
        }
        homeExperience.onRemoveItem = { itemKey ->
            homeSessionState = PracticalShoppingHomeSession.removeItem(homeSessionState, itemKey)
            renderHome()
        }
        homeExperience.onRemoveUnknownItem = { token ->
            homeSessionState =
                PracticalShoppingHomeSession.removeUnknownItem(homeSessionState, token)
            renderHome()
        }
        homeExperience.onFindOfflineCatalogMatch = { token ->
            showOfflineCatalogMatches(token)
        }
        homeExperience.onChooseExactProduct = { itemKey ->
            homeSessionState.model.ui.items.firstOrNull { it.key == itemKey }?.let { item ->
                showOfflineCatalogMatches(item.name, exactProductItemKey = itemKey)
            }
        }
        homeExperience.onChickenChoice = { choice ->
            homeSessionState =
                PracticalShoppingHomeSession.chooseChicken(homeSessionState, choice)
            renderHome()
        }
        homeExperience.onExtraStopMinimumSavingsChoice = { choice ->
            homePreferenceStore.saveExtraStopMinimumSavingsChoice(choice)
            homeSessionState =
                PracticalShoppingHomeSession.chooseExtraStopMinimumSavings(
                    homeSessionState,
                    choice
                )
            renderHome()
        }
        homeExperience.onEditItemDetails = { itemKey ->
            showHomeItemDetails(itemKey)
        }
        homeExperience.onAddObservedPrice = { itemKey ->
            openGoodPriceForHomeItem(itemKey)
        }
        homeExperience.onCompare = { openComparison() }
        homeExperience.onReviewPrivateMemory = { reviewPrivatePriceHistory() }
        homeExperience.onExportPrivateMemory = { exportPrivatePriceHistory() }
        homeExperience.onGoodPrice = { openGoodPriceCheck() }
        homeExperience.onShopAgain = {
            homeSessionState = PracticalShoppingHomeSession.shopAgain(homeSessionState)
            renderHome()
        }
        rememberConfirmedChoiceAndroidSession =
            PracticalShoppingRememberConfirmedChoiceAndroidSession.create(
                context = this,
                completionListener = ::onHomeExactProductRemembered
            )
        renderHome()
    }

    private fun renderHome() {
        homeSessionStore.save(PracticalShoppingHomeSession.snapshot(homeSessionState))
        val homeState =
            PracticalShoppingHomeRenderer.render(
                homeSessionState.model.ui,
                homeSessionState.requestDetails.details,
                homePrivateMemoryState,
                privateMemoryStatus =
                    if (homePrivateMemoryLoadIssue == null) {
                        PracticalShoppingHomePrivateMemoryStatus.AVAILABLE
                    } else {
                        PracticalShoppingHomePrivateMemoryStatus.UNAVAILABLE
                    }
            )
        if (
            homeItemDetailsDialog?.isShowing == true &&
                practicalShoppingHomeItemDetailsDialogShouldDismiss(
                    activeItemKey = homeItemDetailsItemKey,
                    visibleItemKeys = homeState.items.map { it.key }
                )
        ) {
            // A list edit can remove or replace the item while its editor is
            // still open. Close the stale editor before it can save details
            // against a request that no longer contains that identity.
            dismissHomeItemDetailsDialog()
        }
        homeExperience.render(homeState)
        basketExperience.render(PracticalShoppingBasketRenderer.render(homeState))
    }

    /**
     * Refreshes bounded local comparison memory when Home resumes, including after Compare Here
     * returns. A corrupt/read-failed store is treated as no usable context, but the issue is
     * retained so Home can distinguish unavailable history from an empty history. It never
     * becomes planner or offer authority and never blocks the already-usable Home surface.
     */
    private fun refreshHomePrivateMemory() {
        val loaded = homePrivateMemoryStore.load()
        val next = loaded.state ?: CompareHerePrivatePriceMemoryState.empty()
        val nextIssue = loaded.issue
        if (next == homePrivateMemoryState && nextIssue == homePrivateMemoryLoadIssue) return

        homePrivateMemoryState = next
        homePrivateMemoryLoadIssue = nextIssue
        dataStatusDialog?.dismiss()
        dataStatusDialog = null
        privatePriceHistoryDialog?.dismiss()
        privatePriceHistoryDialog = null
        privatePriceHistoryClearDialog?.dismiss()
        privatePriceHistoryClearDialog = null
        privatePriceHistoryExportDialog?.dismiss()
        privatePriceHistoryExportDialog = null
        if (
            ::homeExperience.isInitialized &&
                shellState.route != AppRoute.COMPARE
        ) {
            renderHome()
        }
    }

    private fun dismissHomeItemDetailsDialog() {
        homeItemDetailsDialog?.dismiss()
        homeItemDetailsDialog = null
        homeItemDetailsItemKey = null
        homeItemDetailsPackageInput = null
        homeItemDetailsBrandInput = null
        homeItemDetailsExactProduct = null
    }

    private fun cancelOfflineCatalogLookup() {
        offlineCatalogLookup?.cancel(true)
        offlineCatalogLookup = null
    }

    private fun showOfflineCatalogMatches(
        token: String,
        exactProductItemKey: ShoppingItemKey? = null
    ) {
        val query = token.trim()
        if (query.isBlank()) return

        // A newer unresolved item replaces the previous dialog and work. Do
        // not let stale catalog scans accumulate behind the single executor.
        cancelOfflineCatalogLookup()
        offlineCatalogDialog?.dismiss()
        val requestId = Math.addExact(offlineCatalogRequestId, 1L)
        offlineCatalogRequestId = requestId
        val dialog =
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.home_unknown_find_matches_title, query))
                .setMessage(R.string.home_offline_catalog_loading)
                .setNegativeButton(R.string.cancel, null)
                .create()
        offlineCatalogDialog = dialog
        dialog.setOnDismissListener {
            if (offlineCatalogDialog === dialog) {
                offlineCatalogDialog = null
                cancelOfflineCatalogLookup()
            }
        }
        dialog.show()

        offlineCatalogLookup = searchExecutor.submit {
            val lookup =
                try {
                    val discoveryResult = BundledOfflineCatalog.discoverSupportedRegions(
                            context = applicationContext,
                            rawQuery = query,
                            canonicalizer = JvmTextCanonicalizer,
                            evaluatedAtEpochMillis = System.currentTimeMillis(),
                            maximumSnapshotAgeMillis = OFFLINE_CATALOG_MAX_AGE_MILLIS
                        )
                    OfflineCatalogLookup(
                        discoveryResult = discoveryResult,
                        presentation =
                            PracticalShoppingHomeOfflineCatalogPresentation.from(
                                query,
                                discoveryResult
                            )
                    )
                } catch (ignored: Exception) {
                    null
                }

            mainHandler.post {
                if (
                    isFinishing ||
                        isDestroyed ||
                        requestId != offlineCatalogRequestId ||
                        offlineCatalogDialog !== dialog ||
                        !dialog.isShowing
                ) {
                    return@post
                }
                offlineCatalogLookup = null
                if (lookup == null) {
                    dialog.setMessage(getString(R.string.home_offline_catalog_unavailable))
                } else {
                    showOfflineCatalogResult(
                        token = query,
                        presentation = lookup.presentation,
                        discoveryResult = lookup.discoveryResult,
                        requestId = requestId,
                        exactProductItemKey = exactProductItemKey
                    )
                }
            }
        }
    }

    /**
     * Replaces the loading dialog with an explicit, reversible identity-choice surface.
     * Unknown Home words remain query-only. A resolved item may additionally be saved as an
     * exact product preference, but only after the existing source-revalidated confirmation
     * boundary accepts the selected catalog row.
     */
    private fun showOfflineCatalogResult(
        token: String,
        presentation: PracticalShoppingHomeOfflineCatalogPresentation,
        discoveryResult: OfflineCatalogDiscoveryResult,
        requestId: Long,
        exactProductItemKey: ShoppingItemKey?
    ) {
        if (requestId != offlineCatalogRequestId || offlineCatalogDialog == null) return

        offlineCatalogDialog?.dismiss()
        offlineCatalogDialog = null

        if (presentation.matches.isEmpty()) {
            val dialog =
                AlertDialog.Builder(this)
                    .setTitle(getString(R.string.home_unknown_find_matches_title, token))
                    .setMessage(presentation.message)
                    .setNegativeButton(R.string.cancel, null)
                    .create()
            offlineCatalogDialog = dialog
            dialog.setOnDismissListener {
                if (offlineCatalogDialog === dialog) offlineCatalogDialog = null
            }
            dialog.show()
            return
        }

        var selectedIndex = -1
        lateinit var resultDialog: AlertDialog
        val labels =
            presentation.matches
                .map { match ->
                    buildString {
                        append(match.displayName)
                        match.brand?.let { append(" · ").append(it) }
                        append(" (").append(match.matchLabel).append(")")
                    }
                }
                .toTypedArray()
        val builder =
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.home_unknown_find_matches_title, token))
                .setMessage(
                    presentation.summaryMessage +
                        "\n\n" +
                        getString(
                            if (exactProductItemKey == null) {
                                R.string.home_offline_catalog_select_match
                            } else {
                                R.string.home_offline_catalog_confirm_exact_message
                            }
                        )
                )
                .setSingleChoiceItems(labels, -1) { _, which ->
                    selectedIndex = which
                    resultDialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
                    if (exactProductItemKey == null) {
                        resultDialog.getButton(AlertDialog.BUTTON_NEUTRAL).isEnabled =
                            PracticalShoppingHomeOfflineCatalogComparisonSelection.displayNameFor(
                                matches = presentation.matches,
                                selectedIndex = selectedIndex
                            ) != null
                    }
                }
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(
                    if (exactProductItemKey == null) {
                        R.string.home_offline_catalog_replace_list_word
                    } else {
                        R.string.home_offline_catalog_confirm_exact_product
                    },
                    null
                )

        if (exactProductItemKey == null) {
            builder.setNeutralButton(R.string.home_offline_catalog_open_compare, null)
        }

        resultDialog = builder.create()
        offlineCatalogDialog = resultDialog
        resultDialog.setOnDismissListener {
            if (offlineCatalogDialog === resultDialog) offlineCatalogDialog = null
        }
        resultDialog.setOnShowListener {
            resultDialog.getButton(AlertDialog.BUTTON_POSITIVE).apply {
                isEnabled = false
                contentDescription =
                    getString(
                        if (exactProductItemKey == null) {
                            R.string.home_offline_catalog_replace_list_word_description
                        } else {
                            R.string.home_offline_catalog_confirm_exact_description
                        }
                    )
            }
            if (exactProductItemKey == null) {
                resultDialog.getButton(AlertDialog.BUTTON_NEUTRAL).apply {
                    isEnabled = false
                    contentDescription =
                        getString(R.string.home_offline_catalog_open_compare_description)
                }
            }
            resultDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val match = presentation.matches.getOrNull(selectedIndex) ?: return@setOnClickListener
                if (exactProductItemKey != null) {
                    confirmHomeExactProduct(
                        itemKey = exactProductItemKey,
                        discoveryResult = discoveryResult,
                        matchIndex = selectedIndex,
                        presentationGeneration = requestId,
                        displayName = match.displayName,
                        resultDialog = resultDialog
                    )
                    return@setOnClickListener
                }
                val editedQuery =
                    PracticalShoppingHomeOfflineCatalogSelection.replaceUnknownToken(
                        rawQuery = homeSessionState.model.ui.query,
                        unknownToken = token,
                        replacementName = match.displayName
                    ) ?: run {
                        // The Home list may have changed while the bounded lookup was open.
                        // Explain the stale/unsafe selection instead of silently ignoring it.
                        resultDialog.setMessage(getString(R.string.home_offline_catalog_match_apply_failed))
                        resultDialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
                        return@setOnClickListener
                    }
                homeSessionState =
                    PracticalShoppingHomeSession.queryChanged(homeSessionState, editedQuery)
                renderHome()
                resultDialog.dismiss()
            }
            if (exactProductItemKey == null) {
                resultDialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                    if (
                        requestId != offlineCatalogRequestId ||
                            offlineCatalogDialog !== resultDialog ||
                            !resultDialog.isShowing
                    ) {
                        resultDialog.dismiss()
                        return@setOnClickListener
                    }
                    val displayName =
                        PracticalShoppingHomeOfflineCatalogComparisonSelection.displayNameFor(
                            matches = presentation.matches,
                            selectedIndex = selectedIndex
                        ) ?: return@setOnClickListener
                    resultDialog.dismiss()
                    openComparisonWithSharedText(displayName)
                }
            }
        }
        resultDialog.show()
    }

    private fun confirmHomeExactProduct(
        itemKey: ShoppingItemKey,
        discoveryResult: OfflineCatalogDiscoveryResult,
        matchIndex: Int,
        presentationGeneration: Long,
        displayName: String,
        resultDialog: AlertDialog
    ) {
        if (
            presentationGeneration != offlineCatalogRequestId ||
                offlineCatalogDialog !== resultDialog ||
                !resultDialog.isShowing
        ) {
            return
        }

        val currentItem = homeSessionState.model.ui.items.firstOrNull { it.key == itemKey }
        if (currentItem == null) {
            resultDialog.setMessage(getString(R.string.home_offline_catalog_confirm_unavailable))
            resultDialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
            return
        }

        val selection =
            PracticalShoppingHomeOfflineCatalogExactSelection.confirm(
                itemKey = itemKey,
                result = discoveryResult,
                matchIndex = matchIndex,
                presentationGeneration = presentationGeneration,
                confirmedCandidateId = "home-confirmed-$presentationGeneration-${matchIndex + 1}"
            )
        val chosen = selection.selection
        if (chosen == null) {
            resultDialog.setMessage(getString(R.string.home_offline_catalog_confirm_unavailable))
            resultDialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
            return
        }

        if (!rememberConfirmedChoiceAndroidSession.remember(chosen.rememberRequest)) {
            resultDialog.setMessage(getString(R.string.home_offline_catalog_save_progress))
            resultDialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
            return
        }

        pendingExactProductLabel = displayName
        resultDialog.setMessage(getString(R.string.home_offline_catalog_save_progress))
        resultDialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
    }

    private fun onHomeExactProductRemembered(
        completion: PracticalShoppingRememberConfirmedChoiceCompletion
    ) {
        val label = pendingExactProductLabel
        pendingExactProductLabel = null
        val dialog = offlineCatalogDialog
        offlineCatalogDialog = null
        dialog?.dismiss()

        val outcome = completion.outcome
        val title: Int
        val message: String
        when (outcome) {
            is PracticalShoppingRememberConfirmedChoiceExecutionOutcome.Completed -> {
                val result = outcome.result
                if (result.exactSaved) {
                    title = R.string.home_offline_catalog_saved_title
                    message =
                        if (result.fullyLabeled && label != null) {
                            getString(R.string.home_offline_catalog_saved_message, label)
                        } else {
                            getString(R.string.home_offline_catalog_saved_unlabelled_message)
                        }
                } else {
                    title = R.string.home_offline_catalog_save_failed_title
                    message = getString(R.string.home_offline_catalog_save_failed_message)
                }
            }

            PracticalShoppingRememberConfirmedChoiceExecutionOutcome.Failed -> {
                title = R.string.home_offline_catalog_save_failed_title
                message = getString(R.string.home_offline_catalog_save_failed_message)
            }
        }

        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(R.string.nav_saved) { _, _ ->
                dispatch(AppShellIntent.SelectPrimary(AppPrimaryTab.SAVED))
            }
            .setNegativeButton(R.string.home_stay, null)
            .show()
    }

    private fun restoreHomeItemDetailsDialog(savedInstanceState: Bundle?) {
        if (shellState.route != AppRoute.HOME) return
        val state = savedInstanceState ?: return

        val itemKey =
            state
                .getString(STATE_HOME_DETAILS_ITEM_KEY)
                ?.takeIf(String::isNotBlank)
                ?.let(::ShoppingItemKey)
                ?: return

        showHomeItemDetails(
            itemKey = itemKey,
            draftOverride =
                PracticalShoppingHomeItemDetailsEditor.Draft(
                    packageCountText =
                        state
                            .getString(STATE_HOME_DETAILS_PACKAGE_COUNT)
                            .orEmpty(),
                    brandText =
                        state
                            .getString(STATE_HOME_DETAILS_BRAND)
                            .orEmpty(),
                    exactProduct =
                        state.getBoolean(STATE_HOME_DETAILS_EXACT_PRODUCT, false)
                )
        )
    }

    private fun showHomeItemDetails(
        itemKey: ShoppingItemKey,
        draftOverride: PracticalShoppingHomeItemDetailsEditor.Draft? = null
    ) {
        dismissHomeItemDetailsDialog()
        val item = homeSessionState.model.ui.items.firstOrNull { it.key == itemKey } ?: return
        val current = homeSessionState.requestDetails.details?.detailFor(itemKey)
        if (homeSessionState.requestDetails.details == null) return

        val draft =
            draftOverride ?: PracticalShoppingHomeItemDetailsEditor.initialDraft(current)
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(4), dp(4), 0)
        }
        val packageLayout = TextInputLayout(this).apply {
            hint = getString(R.string.home_item_details_package_count_hint)
            isCounterEnabled = true
            counterMaxLength = 7
        }
        val packageInput = TextInputEditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            imeOptions = EditorInfo.IME_ACTION_NEXT
            filters = arrayOf(InputFilter.LengthFilter(7))
            setText(draft.packageCountText)
            setSelectAllOnFocus(false)
        }
        packageLayout.addView(packageInput)

        val brandLayout = TextInputLayout(this).apply {
            hint = getString(R.string.home_item_details_brand_hint)
            isCounterEnabled = true
            counterMaxLength = 160
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(12) }
        }
        val brandInput = TextInputEditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            imeOptions = EditorInfo.IME_ACTION_DONE
            filters = arrayOf(InputFilter.LengthFilter(160))
            setText(draft.brandText)
            setSingleLine(true)
        }
        brandLayout.addView(brandInput)

        val exactProduct = CheckBox(this).apply {
            text = getString(R.string.home_item_details_exact_product)
            isChecked = draft.exactProduct
            setPadding(0, dp(8), 0, 0)
        }
        body.addView(packageLayout)
        body.addView(brandLayout)
        body.addView(exactProduct)
        body.addView(
            TextView(this).apply {
                text = getString(R.string.home_item_details_exact_product_note)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setTextColor(Color.parseColor("#6B7280"))
                setPadding(dp(4), 0, dp(4), 0)
            }
        )

        val dialogBuilder =
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.home_item_details_title, item.name))
                .setMessage(getString(R.string.home_item_details_body))
                .setView(body)
                .setNegativeButton(R.string.cancel, null)

        if (current != null) {
            dialogBuilder.setNeutralButton(R.string.home_item_details_clear) { _, _ ->
                homeSessionState =
                    PracticalShoppingHomeSession.withoutItemDetail(
                        homeSessionState,
                        itemKey
                    )
                renderHome()
            }
        }

        val dialog =
            dialogBuilder
                .setPositiveButton(R.string.home_item_details_save, null)
                .create()
        homeItemDetailsDialog = dialog
        homeItemDetailsItemKey = itemKey
        homeItemDetailsPackageInput = packageInput
        homeItemDetailsBrandInput = brandInput
        homeItemDetailsExactProduct = exactProduct
        dialog.setOnDismissListener {
            if (homeItemDetailsDialog === dialog) {
                homeItemDetailsDialog = null
                homeItemDetailsItemKey = null
                homeItemDetailsPackageInput = null
                homeItemDetailsBrandInput = null
                homeItemDetailsExactProduct = null
            }
        }

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                packageLayout.error = null
                brandLayout.error = null
                when (
                    val outcome =
                        PracticalShoppingHomeItemDetailsEditor.apply(
                            itemKey = itemKey,
                            current = current,
                            draft =
                                PracticalShoppingHomeItemDetailsEditor.Draft(
                                    packageCountText = packageInput.text?.toString().orEmpty(),
                                    brandText = brandInput.text?.toString().orEmpty(),
                                    exactProduct = exactProduct.isChecked
                                )
                        )
                ) {
                    is PracticalShoppingHomeItemDetailsEditor.Outcome.Accepted -> {
                        homeSessionState =
                            outcome.detail?.let { detail ->
                                PracticalShoppingHomeSession.withItemDetail(
                                    homeSessionState,
                                    detail
                                )
                            } ?: PracticalShoppingHomeSession.withoutItemDetail(
                                homeSessionState,
                                itemKey
                            )
                        renderHome()
                        dialog.dismiss()
                    }

                    is PracticalShoppingHomeItemDetailsEditor.Outcome.Rejected -> {
                        when (outcome.field) {
                            PracticalShoppingHomeItemDetailsEditor.Field.PACKAGE_COUNT -> {
                                packageLayout.error = outcome.message
                                packageInput.requestFocus()
                            }

                            PracticalShoppingHomeItemDetailsEditor.Field.BRAND -> {
                                brandLayout.error = outcome.message
                                brandInput.requestFocus()
                            }
                        }
                    }
                }
            }
        }
        dialog.show()
    }

    private fun configureBasketUi() {
        basketExperience.onAction = { action ->
            when (action) {
                PracticalShoppingBasketUiAction.OpenHome ->
                    dispatch(AppShellIntent.SelectPrimary(AppPrimaryTab.HOME))
            }
        }
    }

    private fun restoreSearchState(savedInstanceState: Bundle?): UniversalSearchState {
        var restored = searchController.initialState()
        val savedQuery = savedInstanceState?.getString(STATE_SEARCH_QUERY).orEmpty()

        if (savedQuery.isNotBlank()) {
            restored = searchController.reduce(
                restored,
                UniversalSearchIntent.QueryChanged(savedQuery)
            ).state
        }

        restoreSearchOnNextOpen = savedInstanceState
            ?.getBoolean(STATE_SEARCH_WAS_SUBMITTED, false)
            ?: false

        return restored
    }

    private fun configureSearchUi() {
        suppressSearchInputCallback = true
        searchInput.setText(searchState.query)
        searchInput.setSelection(searchInput.text?.length ?: 0)
        suppressSearchInputCallback = false

        searchInput.addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) = Unit

                override fun onTextChanged(
                    s: CharSequence?,
                    start: Int,
                    before: Int,
                    count: Int
                ) = Unit

                override fun afterTextChanged(s: Editable?) {
                    if (suppressSearchInputCallback) return

                    dismissSearchIdentityDialog()
                    searchState = searchController.reduce(
                        searchState,
                        UniversalSearchIntent.QueryChanged(s?.toString().orEmpty())
                    ).state
                    renderSearch(searchState)
                }
            }
        )

        searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                submitSearch()
                true
            } else {
                false
            }
        }

        searchButton.setOnClickListener { submitSearch() }
        searchIdentityButton.setOnClickListener { showSearchIdentityMatches() }

        quickSearchEntries().forEach { (chipId, query) ->
            configureQuickSearch(chipId, query)
        }
    }

    private fun configureSavedUi() {
        val savedPresenter = PracticalShoppingSavedSurfacePresenter(savedExperience)
        val stapleLaunchPresenter =
            PracticalShoppingSavedStapleLaunchPresenter(savedStapleLaunchExperience)
        val observedPriceLaunchPresenter =
            PracticalShoppingSavedObservedPriceLaunchPresenter(savedObservedPriceLaunchExperience)
        observedPriceSavedPrefillResultSurfaceBinding =
            UserObservedPriceSavedPrefillHandoffResultSurfaceBinding(
                surface = observedPriceSavedPrefillResultExperience
            )
        val observedPriceSelectionRenderer =
            UserObservedPriceSavedSelectionSurfaceRenderer { state ->
                observedPriceSavedPrefillResultSurfaceBinding.clear()
                observedPriceSavedSelectionExperience.render(state)
            }
        val observedPriceSelectionPresenter =
            UserObservedPriceSavedSelectionSurfacePresenter(observedPriceSelectionRenderer)
        val observedPriceConfirmationDraftPresenter =
            UserObservedPriceConfirmationDraftSurfacePresenter(
                observedPriceConfirmationDraftExperience
            )
        observedPriceConfirmationActionPresentationController =
            UserObservedPriceConfirmationActionPresentationController(
                renderer = observedPriceConfirmationActionExperience,
                submitAction = { observedPriceConfirmationActionCoordinator.submit() }
            )
        observedPriceConfirmationAndroidSession =
            UserObservedPriceConfirmationAndroidSession.create(
                context = this,
                completionListener = observedPriceConfirmationActionPresentationController
            )
        val observedPriceConfirmationDraftRouteShellAdapter =
            UserObservedPriceConfirmationDraftRouteShellAdapter(
                currentRoute = { shellState.route },
                emitIntent = ::dispatch
            )
        observedPriceConfirmationDraftRouteCoordinator =
            UserObservedPriceSavedConfirmationDraftRouteCoordinator(
                routeOpenObserver = observedPriceConfirmationDraftRouteShellAdapter,
                sessionFactory = {
                    UserObservedPriceConfirmationDraftRouteSession(
                        observer =
                            UserObservedPriceConfirmationDraftObserver { finalization ->
                                observedPriceConfirmationDraftPresenter.render(finalization)
                                observedPriceConfirmationActionPresentationController
                                    .onDraftOrProofChanged()
                            }
                    )
                }
            )
        observedPriceConfirmationDraftPriceInputExperience.onCommit =
            observedPriceConfirmationDraftRouteCoordinator::onPriceInput
        observedPriceConfirmationDraftObservedAtInputExperience.onCommit =
            observedPriceConfirmationDraftRouteCoordinator::onObservedAtInput
        observedPriceConfirmationDraftProofReferenceInputExperience.onCommit =
            observedPriceConfirmationDraftRouteCoordinator::onProofReferenceInput
        observedPriceConfirmationDraftProofContentSelectionCoordinator =
            UserObservedPriceConfirmationDraftProofContentSelectionCoordinator(
                requestForegroundSelection = {
                    observedPriceConfirmationDraftProofContentPicker.launch()
                },
                observer =
                    UserObservedPriceConfirmationDraftProofContentSelectionObserver { presentation ->
                        observedPriceConfirmationDraftProofContentSelectionExperience
                            .onPresentation(presentation)
                        observedPriceConfirmationActionPresentationController
                            .onDraftOrProofChanged()
                    }
            )
        observedPriceConfirmationDraftProofContentPicker =
            AndroidUserObservedPriceProofContentPicker(
                activity = this,
                contentSource = AndroidUserObservedPriceProofContentSource(contentResolver),
                onReadResult =
                    observedPriceConfirmationDraftProofContentSelectionCoordinator::onContentReadResult
            )
        observedPriceConfirmationDraftProofContentSelectionExperience.onSelectRequested =
            observedPriceConfirmationDraftProofContentSelectionCoordinator::onSelectRequested
        observedPriceConfirmationActionCoordinator =
            UserObservedPriceConfirmationActionCoordinator(
                routeCoordinator = observedPriceConfirmationDraftRouteCoordinator,
                proofContentCoordinator =
                    observedPriceConfirmationDraftProofContentSelectionCoordinator,
                target =
                    UserObservedPriceConfirmationAndroidSubmissionTarget(
                        observedPriceConfirmationAndroidSession
                    )
            )
        observedPriceConfirmationActionExperience.onAction =
            observedPriceConfirmationActionPresentationController::onSubmitRequested
        val observedPricePrefillAttemptFanout =
            UserObservedPriceSavedPrefillHandoffAttemptFanout(
                resultObserver = observedPriceSavedPrefillResultSurfaceBinding,
                confirmationDraftObserver = observedPriceConfirmationDraftRouteCoordinator
            )
        val stapleSetupPresenter =
            StapleWatchSavedSelectionSurfacePresenter(stapleWatchSetupExperience)
        val staplePolicyPresenter =
            StapleWatchPolicyDraftSurfacePresenter(stapleWatchPolicyExperience)

        observedPriceSavedSelectionCoordinator =
            UserObservedPriceSavedSelectionCompositionCoordinator(
                prefillHandoffAttemptObserver = observedPricePrefillAttemptFanout
            ) { snapshot ->
                UserObservedPriceSavedSelectionRouteSession(
                    initialSnapshot = snapshot,
                    presenter = observedPriceSelectionPresenter
                )
            }
        observedPriceSavedSelectionSurfaceCoordinator =
            UserObservedPriceSavedSelectionSurfaceCoordinator(
                surface = observedPriceSavedSelectionExperience,
                compositionCoordinator = observedPriceSavedSelectionCoordinator
            )
        stapleWatchForegroundResultSurfaceBinding =
            StapleWatchForegroundResultSurfaceBinding(
                renderer = stapleWatchResultExperience,
                clearSurface = stapleWatchResultExperience::clear
            )
        stapleWatchForegroundEvaluationInputHost =
            StapleWatchForegroundEvaluationInputHost(
                outputObserver = stapleWatchForegroundResultSurfaceBinding.outputObserver
            )
        val stapleWatchPolicyAvailabilityShellAdapter =
            StapleWatchPolicyRouteAvailabilityShellAdapter(
                currentRoute = { shellState.route },
                emitIntent = ::dispatch
            )
        stapleWatchPolicySetupCoordinator =
            StapleWatchPolicySetupCompositionCoordinator(
                policyObserver = stapleWatchForegroundEvaluationInputHost,
                routeAvailabilityObserver = stapleWatchPolicyAvailabilityShellAdapter,
                sessionFactory = { moneySpec ->
                    StapleWatchPolicyDraftRouteSession(
                        moneySpec = moneySpec,
                        presenter = staplePolicyPresenter
                    )
                }
            )
        val stapleWatchEvidencePreconditionsFanout =
            StapleWatchEconomicEvidencePreconditionsFanout(
                foregroundInputObserver = stapleWatchForegroundEvaluationInputHost,
                policySetupObserver = stapleWatchPolicySetupCoordinator
            )
        stapleWatchSavedDisplayMetadataCompositionCoordinator =
            StapleWatchSavedDisplayMetadataCompositionCoordinator(
                preconditionsObserver = stapleWatchEvidencePreconditionsFanout,
                displayMetadataObserver = stapleWatchForegroundEvaluationInputHost
            )
        stapleWatchFactResolutionHost =
            StapleWatchFactResolutionHost(
                preconditionsObserver = stapleWatchSavedDisplayMetadataCompositionCoordinator,
                readinessObserver = stapleSetupPresenter::onFactResolutionReadiness
            )
        stapleWatchSetupCoordinator =
            StapleWatchSavedSetupCompositionCoordinator(
                handoffAttemptObserver = stapleSetupPresenter::onHandoffAttempt,
                factCheckIntentObserver = stapleWatchFactResolutionHost,
                sessionFactory = { snapshot ->
                    StapleWatchSavedSelectionRouteSession(
                        initialSnapshot = snapshot,
                        presenter = stapleSetupPresenter
                    )
                }
            )

        val savedRenderer =
            PracticalShoppingSavedLifecycleRenderer { state ->
                savedPresenter.render(state)
                stapleLaunchPresenter.render(state)
                observedPriceLaunchPresenter.render(state)
            }
        val savedSnapshotObserver =
            PracticalShoppingSavedValidatedSnapshotObserver { snapshot ->
                observedPriceSavedSelectionCoordinator.onSnapshot(snapshot)
                stapleWatchSetupCoordinator.onSnapshot(snapshot)
                stapleWatchSavedDisplayMetadataCompositionCoordinator.onSnapshot(snapshot)
            }

        savedRouteCoordinator =
            PracticalShoppingSavedRouteCoordinator(
                sessionFactory = {
                    PracticalShoppingSavedAndroidSession.create(
                        context = this,
                        renderer = savedRenderer,
                        snapshotObserver = savedSnapshotObserver
                    )
                }
            )

        savedExperience.onAction = { action ->
            when (action) {
                is PracticalShoppingSavedSurfaceAction.CheckProductPrice ->
                    openGoodPriceForSavedProduct(action.displayName)

                else -> savedRouteCoordinator.onSurfaceAction(action)
            }
        }
        savedStapleLaunchExperience.onAction = { action ->
            when (action) {
                PracticalShoppingSavedStapleLaunchAction.OpenStapleWatchSetup ->
                    dispatch(AppShellIntent.OpenStapleWatchSetup)
            }
        }
        savedObservedPriceLaunchExperience.onAction = { action ->
            when (action) {
                PracticalShoppingSavedObservedPriceLaunchAction.OpenObservedPriceSavedSelection ->
                    dispatch(AppShellIntent.OpenObservedPriceSavedSelection)
            }
        }
        stapleWatchSetupExperience.onAction = stapleWatchSetupCoordinator::onSurfaceAction
        stapleWatchSetupExperience.onContinueAction = stapleWatchSetupCoordinator::onContinueAction
        stapleWatchPolicyExperience.onAction = stapleWatchPolicySetupCoordinator::onSurfaceAction
        stapleWatchPolicyExperience.onContinueAction =
            stapleWatchPolicySetupCoordinator::onContinueAction
    }

    private fun configureQuickSearch(chipId: Int, query: String) {
        findViewById<Chip>(chipId).setOnClickListener {
            if (practicalShoppingSearchQuickEntryBlocked(searchState, query)) {
                hideKeyboard()
                return@setOnClickListener
            }
            setSearchQuery(query)
            submitSearch()
        }
    }

    private fun quickSearchEntries(): List<Pair<Int, String>> =
        listOf(
            R.id.quickEggs to getString(R.string.search_quick_eggs),
            R.id.quickMilk to getString(R.string.search_quick_milk),
            R.id.quickChicken to getString(R.string.search_quick_chicken),
            R.id.quickRice to getString(R.string.search_quick_rice),
            R.id.quickPizza to getString(R.string.search_quick_pizza)
        )

    private fun setSearchQuery(query: String) {
        dismissSearchIdentityDialog()
        suppressSearchInputCallback = true
        searchInput.setText(query)
        searchInput.setSelection(query.length)
        suppressSearchInputCallback = false

        searchState = searchController.reduce(
            searchState,
            UniversalSearchIntent.QueryChanged(query)
        ).state
        renderSearch(searchState)
    }

    private fun submitSearch() {
        dismissSearchIdentityDialog()
        val rawQuery = searchInput.text?.toString().orEmpty()

        if (!practicalShoppingSearchSubmitEnabled(searchState, rawQuery)) {
            hideKeyboard()
            return
        }

        searchState = searchController.reduce(
            searchState,
            UniversalSearchIntent.QueryChanged(rawQuery)
        ).state

        val transition = searchController.reduce(
            searchState,
            UniversalSearchIntent.Submit
        )

        searchState = transition.state
        renderSearch(searchState)
        hideKeyboard()
        transition.request?.let(::executeSearch)
    }

    /**
     * Searches the signed, bundled identity rail without routing identity rows through the
     * fictional price/ranking provider. The result is deliberately a reviewable dialog; only a
     * selected display name can be handed to the existing Scan & compare draft route.
     */
    private fun showSearchIdentityMatches() {
        val query = searchState.query.trim()
        if (!practicalShoppingSearchIdentityEnabled(searchState, query)) return

        dismissSearchIdentityDialog()
        val requestId = Math.addExact(searchIdentityRequestId, 1L)
        searchIdentityRequestId = requestId
        val dialog =
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.search_identity_dialog_title, query))
                .setMessage(R.string.search_identity_loading)
                .setNegativeButton(R.string.cancel, null)
                .create()
        searchIdentityDialog = dialog
        dialog.setOnDismissListener {
            if (searchIdentityDialog === dialog) {
                searchIdentityDialog = null
                cancelSearchIdentityLookup()
            }
        }
        dialog.show()

        searchIdentityLookup = searchExecutor.submit {
            val presentation =
                try {
                    PracticalShoppingSearchIdentityPresentation.from(
                        query = query,
                        result =
                            BundledOfflineCatalog.discoverSupportedRegions(
                                context = applicationContext,
                                rawQuery = query,
                                canonicalizer = JvmTextCanonicalizer,
                                evaluatedAtEpochMillis = System.currentTimeMillis(),
                                maximumSnapshotAgeMillis = OFFLINE_CATALOG_MAX_AGE_MILLIS
                            )
                    )
                } catch (ignored: Exception) {
                    null
                }

            mainHandler.post {
                if (
                    isFinishing ||
                        isDestroyed ||
                        requestId != searchIdentityRequestId ||
                        searchIdentityDialog !== dialog ||
                        !dialog.isShowing
                ) {
                    return@post
                }
                searchIdentityLookup = null
                if (presentation == null) {
                    dialog.setMessage(getString(R.string.search_identity_unavailable))
                } else {
                    showSearchIdentityResult(
                        query = query,
                        presentation = presentation,
                        requestId = requestId
                    )
                }
            }
        }
    }

    private fun cancelSearchIdentityLookup() {
        searchIdentityLookup?.cancel(true)
        searchIdentityLookup = null
    }

    private fun dismissSearchIdentityDialog() {
        cancelSearchIdentityLookup()
        searchIdentityDialog?.dismiss()
        searchIdentityDialog = null
    }

    /**
     * Replaces the loading dialog with a bounded, explicit identity choice. A positive selection
     * uses the existing untrusted text handoff so Scan & compare still owns all exact evidence
     * entry and validation.
     */
    private fun showSearchIdentityResult(
        query: String,
        presentation: PracticalShoppingSearchIdentityPresentation,
        requestId: Long
    ) {
        if (requestId != searchIdentityRequestId || searchIdentityDialog == null) return

        searchIdentityDialog?.dismiss()
        searchIdentityDialog = null

        if (presentation.matches.isEmpty()) {
            val dialog =
                AlertDialog.Builder(this)
                    .setTitle(getString(R.string.search_identity_dialog_title, query))
                    .setMessage(presentation.message)
                    .setNegativeButton(R.string.cancel, null)
                    .create()
            searchIdentityDialog = dialog
            dialog.setOnDismissListener {
                if (searchIdentityDialog === dialog) searchIdentityDialog = null
            }
            dialog.show()
            return
        }

        var selectedIndex = -1
        lateinit var resultDialog: AlertDialog
        val labels =
            presentation.matches
                .map { match ->
                    buildString {
                        append(match.displayName)
                        match.brand?.let { append(" · ").append(it) }
                        append(" (").append(match.matchLabel).append(")")
                    }
                }
                .toTypedArray()
        val builder =
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.search_identity_dialog_title, query))
                .setMessage(
                    presentation.summaryMessage +
                        "\n\n" +
                        getString(R.string.search_identity_select)
                )
                .setSingleChoiceItems(labels, -1) { _, which ->
                    selectedIndex = which
                    resultDialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
                }
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.search_identity_open_compare, null)

        resultDialog = builder.create()
        searchIdentityDialog = resultDialog
        resultDialog.setOnDismissListener {
            if (searchIdentityDialog === resultDialog) searchIdentityDialog = null
        }
        resultDialog.setOnShowListener {
            resultDialog.getButton(AlertDialog.BUTTON_POSITIVE).apply {
                isEnabled = false
                contentDescription =
                    getString(R.string.search_identity_open_compare_description)
            }
            resultDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val match = presentation.matches.getOrNull(selectedIndex) ?: return@setOnClickListener
                resultDialog.dismiss()
                openComparisonWithSharedText(match.displayName)
            }
        }
        resultDialog.show()
    }

    private fun openComparisonWithSharedText(displayName: String) {
        dismissHomeItemDetailsDialog()
        shellState =
            AppShellReducer.reduce(
                shellState,
                AppShellIntent.OpenStandaloneCompare
            )
        comparisonActivityOpen = true
        startActivity(
            Intent(this, ComparisonActivity::class.java).apply {
                putExtra(ComparisonActivity.EXTRA_SHARED_TEXT, displayName)
            }
        )
    }

    private fun executeSearch(request: ProductSearchRequest) {
        searchExecutor.execute {
            val intent = try {
                UniversalSearchIntent.ResultsReceived(
                    batch =
                        searchProvider.search(
                            request
                        ),
                    evaluatedAtEpochMillis =
                        System.currentTimeMillis()
                )
            } catch (ignored: Exception) {
                UniversalSearchIntent.ProviderFailed(request.requestId)
            }

            mainHandler.post {
                if (isFinishing || isDestroyed) return@post

                searchState = searchController.reduce(searchState, intent).state
                renderSearch(searchState)
            }
        }
    }

    private fun hideKeyboard() {
        val manager = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
        manager?.hideSoftInputFromWindow(searchInput.windowToken, 0)
        searchInput.clearFocus()
    }

    private fun openComparison() {
        dismissHomeItemDetailsDialog()
        shellState = AppShellReducer.reduce(
            shellState,
            AppShellIntent.OpenStandaloneCompare
        )
        comparisonActivityOpen = true
        startActivity(Intent(this, ComparisonActivity::class.java))
    }

    /**
     * Shows the existing device-only comparison memory without making the user re-run a
     * comparison. An unavailable store still goes to the existing recovery route so its clear
     * controls remain reachable; no history-derived value is shown in that state.
     */
    private fun reviewPrivatePriceHistory() {
        if (homePrivateMemoryLoadIssue != null) {
            openComparison()
            return
        }

        val presentation =
            PracticalShoppingPrivatePriceHistoryPresentation.from(homePrivateMemoryState)
        if (presentation.rows.isEmpty()) {
            openComparison()
            return
        }

        privatePriceHistoryExportDialog?.dismiss()
        privatePriceHistoryExportDialog = null
        privatePriceHistoryClearDialog?.dismiss()
        privatePriceHistoryClearDialog = null
        privatePriceHistoryDialog?.dismiss()
        val dialog =
            AlertDialog.Builder(this)
                .setTitle(presentation.title)
                .setMessage(presentation.message)
                .setNegativeButton(android.R.string.cancel, null)
                .setNeutralButton(R.string.home_private_memory_clear, null)
                .setPositiveButton(R.string.home_compare_secondary) { _, _ ->
                    openComparison()
                }
                .create()
        dialog.setOnDismissListener {
            if (privatePriceHistoryDialog === dialog) privatePriceHistoryDialog = null
        }
        privatePriceHistoryDialog = dialog
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
            confirmClearPrivatePriceHistory()
        }
    }

    /**
     * Offers an explicit, human-readable copy of the device-only history. The preview and the
     * outgoing text are generated by a bounded pure exporter; this route never turns personal
     * observations into offers, store facts or planner input. The store is re-read immediately
     * before sharing so a paused/resumed Activity cannot export an erased or stale snapshot.
     */
    private fun exportPrivatePriceHistory() {
        if (homePrivateMemoryLoadIssue != null) {
            openComparison()
            return
        }

        val export = PracticalShoppingPrivatePriceHistoryExport.from(homePrivateMemoryState)
        if (!export.accepted) {
            showPrivatePriceHistoryExportError(export.issue)
            return
        }

        privatePriceHistoryDialog?.dismiss()
        privatePriceHistoryDialog = null
        privatePriceHistoryClearDialog?.dismiss()
        privatePriceHistoryClearDialog = null

        val dialog =
            AlertDialog.Builder(this)
                .setTitle(R.string.home_private_memory_export_title)
                .setMessage(
                    requireNotNull(export.preview) +
                        "\n\n" +
                        getString(R.string.home_private_memory_export_body)
                )
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.home_private_memory_export_send, null)
                .create()
        dialog.setOnDismissListener {
            if (privatePriceHistoryExportDialog === dialog) {
                privatePriceHistoryExportDialog = null
            }
        }
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val loaded = homePrivateMemoryStore.load()
                val latestState = loaded.state
                val latestExport =
                    if (loaded.accepted && latestState != null) {
                        PracticalShoppingPrivatePriceHistoryExport.from(latestState)
                    } else {
                        null
                    }
                if (latestExport?.accepted != true) {
                    dialog.dismiss()
                    refreshHomePrivateMemory()
                    showPrivatePriceHistoryExportError(
                        latestExport?.issue ?: PracticalShoppingPrivatePriceHistoryExportIssue.EMPTY
                    )
                    return@setOnClickListener
                }

                val sendIntent =
                    Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(
                            Intent.EXTRA_SUBJECT,
                            getString(R.string.home_private_memory_export_subject)
                        )
                        putExtra(Intent.EXTRA_TEXT, requireNotNull(latestExport.text))
                    }
                try {
                    startActivity(
                        Intent.createChooser(
                            sendIntent,
                            getString(R.string.home_private_memory_export_send)
                        )
                    )
                    dialog.dismiss()
                } catch (_: ActivityNotFoundException) {
                    dialog.dismiss()
                    showPrivatePriceHistoryExportError(null)
                }
            }
        }
        privatePriceHistoryExportDialog = dialog
        dialog.show()
    }

    private fun showPrivatePriceHistoryExportError(
        issue: PracticalShoppingPrivatePriceHistoryExportIssue?
    ) {
        val messageRes =
            when (issue) {
                PracticalShoppingPrivatePriceHistoryExportIssue.EMPTY ->
                    R.string.home_private_memory_export_empty

                PracticalShoppingPrivatePriceHistoryExportIssue.OUTPUT_TOO_LARGE ->
                    R.string.home_private_memory_export_too_large

                null -> R.string.home_private_memory_export_unavailable
            }
        AlertDialog.Builder(this)
            .setTitle(R.string.home_private_memory_export_error_title)
            .setMessage(messageRes)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    /**
     * Confirms deletion of only the bounded, device-local comparison memory. This action never
     * treats the memory as a current offer, changes the Home request, or changes planner output.
     */
    private fun confirmClearPrivatePriceHistory() {
        if (
            homePrivateMemoryLoadIssue != null ||
                homePrivateMemoryState.entries.isEmpty()
        ) {
            return
        }

        privatePriceHistoryClearDialog?.dismiss()
        val dialog =
            AlertDialog.Builder(this)
                .setTitle(R.string.home_private_memory_clear_title)
                .setMessage(R.string.home_private_memory_clear_body)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.home_private_memory_clear_confirm) { _, _ ->
                    clearPrivatePriceHistory()
                }
                .create()
        dialog.setOnDismissListener {
            if (privatePriceHistoryClearDialog === dialog) {
                privatePriceHistoryClearDialog = null
            }
        }
        privatePriceHistoryClearDialog = dialog
        dialog.show()
    }

    private fun clearPrivatePriceHistory() {
        val result = homePrivateMemoryStore.clear()
        if (!result.accepted) {
            showPrivatePriceHistoryClearError()
            return
        }

        homePrivateMemoryState = CompareHerePrivatePriceMemoryState.empty()
        homePrivateMemoryLoadIssue = null
        privatePriceHistoryDialog?.dismiss()
        privatePriceHistoryDialog = null
        if (::homeExperience.isInitialized && shellState.route != AppRoute.COMPARE) {
            renderHome()
        }
    }

    private fun showPrivatePriceHistoryClearError() {
        privatePriceHistoryClearDialog?.dismiss()
        val dialog =
            AlertDialog.Builder(this)
                .setTitle(R.string.home_private_memory_clear_error_title)
                .setMessage(R.string.home_private_memory_clear_error)
                .setPositiveButton(android.R.string.ok, null)
                .create()
        dialog.setOnDismissListener {
            if (privatePriceHistoryClearDialog === dialog) {
                privatePriceHistoryClearDialog = null
            }
        }
        privatePriceHistoryClearDialog = dialog
        dialog.show()
    }

    private fun showSourcesLicences() {
        AlertDialog.Builder(this)
            .setTitle(R.string.sources_licences_title)
            .setMessage(R.string.sources_licences_body)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun showDataStatus() {
        dataStatusDialog?.dismiss()
        val storeDirectorySummary =
            runCatching {
                BundledStoreDirectory.loadSummary(
                    context = applicationContext,
                    evaluatedAtEpochMillis = System.currentTimeMillis()
                )
            }.getOrNull()
        val presentation =
            PracticalShoppingDataStatusPresentation.from(
                privateMemory = homePrivateMemoryState,
                privateMemoryAvailable = homePrivateMemoryLoadIssue == null,
                storeDirectorySummary = storeDirectorySummary
            )
        val dialog =
            AlertDialog.Builder(this)
                .setTitle(R.string.data_status_title)
                .setMessage(presentation.message)
                .setPositiveButton(android.R.string.ok, null)
                .create()
        dialog.setOnDismissListener {
            if (dataStatusDialog === dialog) dataStatusDialog = null
        }
        dataStatusDialog = dialog
        dialog.show()
    }

    private fun openGoodPriceCheck() {
        dismissHomeItemDetailsDialog()
        startActivity(Intent(this, GoodPriceActivity::class.java))
    }

    /**
     * Starts the existing local observed-price flow for one unresolved Home item.
     * The name is an untrusted prefill only; Good Price still requires the shopper
     * to enter an exact package quantity and observed price before remembering it.
     */
    private fun openGoodPriceForHomeItem(itemKey: ShoppingItemKey) {
        dismissHomeItemDetailsDialog()
        val item = homeSessionState.model.ui.items.firstOrNull { it.key == itemKey } ?: return
        startActivity(
            Intent(this, GoodPriceActivity::class.java).putExtra(
                GoodPriceActivity.EXTRA_PRODUCT_NAME,
                item.name
            )
        )
    }

    /**
     * Reuses a saved display label as an untrusted Good Price prefill. The saved identity itself
     * remains in the Saved lifecycle; Good Price still requires fresh user-entered evidence.
     */
    private fun openGoodPriceForSavedProduct(displayName: String) {
        val safeName = GoodPriceActivityPrefill.sanitize(displayName) ?: return
        startActivity(
            Intent(this, GoodPriceActivity::class.java).putExtra(
                GoodPriceActivity.EXTRA_PRODUCT_NAME,
                safeName
            )
        )
    }

    private fun dispatch(intent: AppShellIntent) {
        val next = AppShellReducer.reduce(shellState, intent)
        if (next == shellState) return
        shellState = next
        renderShell(next)
    }

    private fun renderShell(state: AppShellState) {
        if (state.route != AppRoute.HOME) {
            dismissHomeItemDetailsDialog()
            cancelOfflineCatalogLookup()
            offlineCatalogDialog?.dismiss()
        }
        if (state.route != AppRoute.SEARCH) {
            dismissSearchIdentityDialog()
        }
        if (state.route == AppRoute.COMPARE) {
            savedExperience.onRouteVisibilityChanged(false)
            return
        }

        val copy = copyFor(state.selectedPrimaryTab)
        screenEyebrow.text = copy.eyebrow
        screenTitle.text = copy.title
        screenBody.text = copy.body
        screenFootnote.text = copy.footnote
        primaryAction.visibility = if (copy.showCompareAction) View.VISIBLE else View.GONE

        val homeVisible = state.selectedPrimaryTab == AppPrimaryTab.HOME
        homeExperience.visibility = if (homeVisible) View.VISIBLE else View.GONE
        if (homeVisible) renderHome()

        val basketVisible = state.selectedPrimaryTab == AppPrimaryTab.BASKET
        basketExperience.visibility = if (basketVisible) View.VISIBLE else View.GONE
        if (basketVisible) renderHome()

        val searchVisible = state.selectedPrimaryTab == AppPrimaryTab.SEARCH
        searchExperience.visibility = if (searchVisible) View.VISIBLE else View.GONE

        if (searchVisible) {
            renderSearch(searchState)

            if (restoreSearchOnNextOpen && searchState.query.isNotBlank()) {
                restoreSearchOnNextOpen = false
                searchInput.post { submitSearch() }
            }
        }

        val savedVisible = state.route == AppRoute.SAVED
        val observedPriceSelectionVisible =
            state.route == AppRoute.OBSERVED_PRICE_SAVED_SELECTION
        val observedPriceConfirmationDraftVisible =
            state.route == AppRoute.OBSERVED_PRICE_CONFIRMATION_DRAFT
        val stapleSetupVisible = state.route == AppRoute.STAPLE_WATCH_SETUP
        val staplePolicyVisible = state.route == AppRoute.STAPLE_WATCH_POLICY
        savedExperience.visibility = if (savedVisible) View.VISIBLE else View.GONE
        savedStapleLaunchExperience.visibility = if (savedVisible) View.VISIBLE else View.GONE
        savedObservedPriceLaunchExperience.visibility = if (savedVisible) View.VISIBLE else View.GONE
        observedPriceSavedSelectionExperience.visibility =
            if (observedPriceSelectionVisible) View.VISIBLE else View.GONE
        observedPriceConfirmationDraftExperience.visibility =
            if (observedPriceConfirmationDraftVisible) View.VISIBLE else View.GONE
        observedPriceConfirmationDraftPriceInputExperience.visibility =
            if (observedPriceConfirmationDraftVisible) View.VISIBLE else View.GONE
        observedPriceConfirmationDraftObservedAtInputExperience.visibility =
            if (observedPriceConfirmationDraftVisible) View.VISIBLE else View.GONE
        observedPriceConfirmationDraftProofReferenceInputExperience.visibility =
            if (observedPriceConfirmationDraftVisible) View.VISIBLE else View.GONE
        observedPriceConfirmationDraftProofContentSelectionExperience.visibility =
            if (observedPriceConfirmationDraftVisible) View.VISIBLE else View.GONE
        observedPriceConfirmationActionExperience.visibility =
            if (observedPriceConfirmationDraftVisible) View.VISIBLE else View.GONE
        if (!observedPriceConfirmationDraftVisible) {
            observedPriceConfirmationDraftPriceInputExperience.clearInput()
            observedPriceConfirmationDraftProofReferenceInputExperience.clearInput()
        }
        if (!observedPriceConfirmationDraftVisible) {
            observedPriceConfirmationDraftObservedAtInputExperience.clearInput()
        }
        stapleWatchSetupExperience.visibility =
            if (stapleSetupVisible) View.VISIBLE else View.GONE
        stapleWatchPolicyExperience.visibility =
            if (staplePolicyVisible) View.VISIBLE else View.GONE
        savedExperience.onRouteVisibilityChanged(savedVisible)
        savedRouteCoordinator.onRouteVisibilityChanged(savedVisible)
        observedPriceSavedSelectionCoordinator.onRouteVisibilityChanged(observedPriceSelectionVisible)
        observedPriceSavedPrefillResultSurfaceBinding
            .onRouteVisibilityChanged(observedPriceSelectionVisible)
        observedPriceConfirmationDraftRouteCoordinator
            .onRouteVisibilityChanged(observedPriceConfirmationDraftVisible)
        observedPriceConfirmationDraftProofContentSelectionCoordinator
            .onRouteVisibilityChanged(observedPriceConfirmationDraftVisible)
        observedPriceConfirmationActionPresentationController
            .onRouteVisibilityChanged(observedPriceConfirmationDraftVisible)
        stapleWatchSetupCoordinator.onRouteVisibilityChanged(stapleSetupVisible)
        stapleWatchPolicySetupCoordinator.onRouteVisibilityChanged(staplePolicyVisible)
        stapleWatchForegroundResultSurfaceBinding
            .onPolicyRouteVisibilityChanged(staplePolicyVisible)

        val expectedMenuItem = menuIdFor(state.selectedPrimaryTab)
        if (bottomNavigation.selectedItemId != expectedMenuItem) {
            bottomNavigation.selectedItemId = expectedMenuItem
        }
    }

    private fun renderSearch(state: UniversalSearchState) {
        searchStatus.text = state.statusText
        searchStatus.setTextColor(
            when (state.status) {
                UniversalSearchStatus.RESULTS -> Color.parseColor("#047857")
                UniversalSearchStatus.QUERY_TOO_LONG,
                UniversalSearchStatus.MIXED_CURRENCIES,
                UniversalSearchStatus.PROVIDER_ERROR -> Color.parseColor("#B42318")
                else -> Color.parseColor("#6B7280")
            }
        )

        searchProgress.visibility =
            if (state.status == UniversalSearchStatus.LOADING) View.VISIBLE else View.GONE

        searchButton.isEnabled = practicalShoppingSearchSubmitEnabled(state)
        searchIdentityButton.isEnabled = practicalShoppingSearchIdentityEnabled(state)
        quickSearchEntries().forEach { (chipId, query) ->
            findViewById<Chip>(chipId).isEnabled =
                practicalShoppingSearchQuickEntryEnabled(state, query)
        }

        searchResultsContainer.removeAllViews()
        searchResultsHeading.visibility =
            if (state.results.isEmpty()) View.GONE else View.VISIBLE

        state.results.forEach { row ->
            searchResultsContainer.addView(createSearchResultView(row))
        }
    }

    private fun createSearchResultView(row: UniversalSearchRow): View {
        val card = MaterialCardView(this).apply {
            radius = dp(18).toFloat()
            cardElevation = 0f
            setCardBackgroundColor(
                Color.parseColor(if (row.best) "#ECFDF5" else "#FFFFFF")
            )
            strokeColor = Color.parseColor(if (row.best) "#A7F3D0" else "#E5E7EB")
            strokeWidth = dp(1)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(12) }
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            contentDescription = searchResultContentDescription(row)
        }

        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(15), dp(16), dp(15))
            // The card exposes one complete projected summary. Keep the
            // visible child labels decorative for assistive technology so the
            // same fields are not announced repeatedly.
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }

        val rank = TextView(this).apply {
            text = searchResultRankLabel(row)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(Color.parseColor(if (row.best) "#047857" else "#6B7280"))
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        }

        val name = TextView(this).apply {
            text = row.name
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTextColor(Color.parseColor("#111827"))
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            setPadding(0, dp(5), 0, 0)
        }

        val price = TextView(this).apply {
            text = listOfNotNull(
                row.priceSummary,
                row.quantity?.takeIf { it.isNotBlank() }
            ).joinToString("  •  ")
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTextColor(Color.parseColor("#374151"))
            setPadding(0, dp(7), 0, 0)
        }

        val metric = TextView(this).apply {
            text = row.metricLabel
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(Color.parseColor("#111827"))
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            setPadding(0, dp(7), 0, 0)
        }

        val exactness = TextView(this).apply {
            text = row.exactnessLabel
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(Color.parseColor("#6B7280"))
            setPadding(0, dp(3), 0, 0)
        }

        val notice =
            row.evidenceNotice
                ?.takeIf {
                    it.isNotBlank()
                }
                ?.let { noticeText ->
                    TextView(this).apply {
                        text = noticeText
                        setTextSize(
                            TypedValue.COMPLEX_UNIT_SP,
                            12f
                        )
                        setTextColor(
                            Color.parseColor("#92400E")
                        )
                        setPadding(
                            0,
                            dp(5),
                            0,
                            0
                        )
                    }
                }

        val source = TextView(this).apply {
            text = row.sourceSummary
            gravity = Gravity.START
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(Color.parseColor("#6B7280"))
            setPadding(0, dp(9), 0, 0)
        }

        body.addView(rank)
        body.addView(name)
        body.addView(price)
        body.addView(metric)
        body.addView(exactness)
        notice?.let(body::addView)
        body.addView(source)
        card.addView(body)
        return card
    }

    private fun searchResultRankLabel(row: UniversalSearchRow): String =
        when {
            row.best -> getString(R.string.best_value_rank, row.rank)
            row.rank != null -> getString(R.string.rank_number, row.rank)
            else -> "REFERENCE ONLY"
        }

    private fun searchResultContentDescription(row: UniversalSearchRow): String {
        val sampleNotice =
            if (row.sampleEvidence) {
                "Fictional sample data only — not live retailer prices or availability"
            } else {
                null
            }
        return listOfNotNull(
            searchResultRankLabel(row),
            row.name,
            row.priceSummary,
            row.quantity?.takeIf(String::isNotBlank),
            row.metricLabel,
            row.exactnessLabel,
            row.evidenceNotice?.takeIf(String::isNotBlank),
            row.sourceSummary,
            sampleNotice
        ).joinToString(". ") { it.trim().trimEnd('.', '!', '?') } + "."
    }

    private fun copyFor(tab: AppPrimaryTab): ScreenCopy =
        when (tab) {
            AppPrimaryTab.HOME -> ScreenCopy(
                getString(R.string.home_eyebrow),
                getString(R.string.home_title),
                getString(R.string.home_body),
                getString(R.string.home_footnote),
                false
            )
            AppPrimaryTab.SEARCH -> ScreenCopy(
                getString(R.string.search_eyebrow),
                getString(R.string.search_title),
                getString(R.string.search_body),
                getString(R.string.search_footnote),
                false
            )
            AppPrimaryTab.BASKET -> ScreenCopy(
                getString(R.string.basket_eyebrow),
                getString(R.string.basket_title),
                getString(R.string.basket_body),
                getString(R.string.basket_footnote),
                false
            )
            AppPrimaryTab.SAVED -> ScreenCopy(
                getString(R.string.saved_eyebrow),
                getString(R.string.saved_title),
                getString(R.string.saved_body),
                getString(R.string.saved_footnote),
                false
            )
        }

    private fun menuIdFor(tab: AppPrimaryTab): Int =
        when (tab) {
            AppPrimaryTab.HOME -> R.id.navHome
            AppPrimaryTab.SEARCH -> R.id.navSearch
            AppPrimaryTab.BASKET -> R.id.navBasket
            AppPrimaryTab.SAVED -> R.id.navSaved
        }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private data class ScreenCopy(
        val eyebrow: String,
        val title: String,
        val body: String,
        val footnote: String,
        val showCompareAction: Boolean
    )

    companion object {
        private const val STATE_PRIMARY_TAB = "app_shell.primary_tab"
        private const val STATE_HOME_QUERY = "app_shell.home_query"
        private const val STATE_HOME_WAS_SUBMITTED = "app_shell.home_was_submitted"
        private const val STATE_HOME_CHICKEN_CHOICE = "app_shell.home_chicken_choice"
        private const val STATE_HOME_EXTRA_STOP_MINIMUM_SAVINGS =
            "app_shell.home_extra_stop_minimum_savings"
        private const val STATE_HOME_REQUEST_DETAILS = "app_shell.home_request_details"
        private const val STATE_HOME_DETAILS_ITEM_KEY = "app_shell.home_details_item_key"
        private const val STATE_HOME_DETAILS_PACKAGE_COUNT = "app_shell.home_details_package_count"
        private const val STATE_HOME_DETAILS_BRAND = "app_shell.home_details_brand"
        private const val STATE_HOME_DETAILS_EXACT_PRODUCT = "app_shell.home_details_exact_product"
        private const val STATE_SEARCH_QUERY = "app_shell.search_query"
        private const val STATE_SEARCH_WAS_SUBMITTED = "app_shell.search_was_submitted"
        private const val OFFLINE_CATALOG_MAX_AGE_MILLIS = 8L * 24L * 60L * 60L * 1_000L
    }
}
