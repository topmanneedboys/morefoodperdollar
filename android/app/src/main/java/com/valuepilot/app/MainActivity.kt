package com.valuepilot.app

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.location.LocationManager
import android.net.Uri
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
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.valuepilot.core.ConfirmedPurchase
import com.valuepilot.core.ConfirmedPurchaseConfirmation
import com.valuepilot.core.ConfirmedPurchaseConfirmationInput
import com.valuepilot.core.ConfirmedPurchaseIdentityOrigin
import com.valuepilot.core.PriceObservation
import com.valuepilot.core.PriceWatch
import com.valuepilot.core.PriceWatchState
import com.valuepilot.core.ShoppingItemKey
import com.valuepilot.core.TripDistanceBasis
import com.valuepilot.core.TripOriginKind
import com.valuepilot.core.TripOriginReference
import com.valuepilot.core.ConfirmedPurchaseStore
import com.valuepilot.core.ProductStoreMapCoordinate
import java.math.BigDecimal
import java.util.UUID
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private var shellState = AppShellState.initial()
    private var homeSessionPersistenceBlocked = false
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
    private val confirmedPurchaseStore by lazy(LazyThreadSafetyMode.NONE) {
        AndroidConfirmedPurchaseHistoryStore(applicationContext)
    }
    private var confirmedPurchaseHistoryState = ConfirmedPurchaseHistoryState.empty()
    private var confirmedPurchaseHistoryLoadIssue: ConfirmedPurchaseHistoryStoreIssue? = null
    private val priceWatchStore by lazy(LazyThreadSafetyMode.NONE) {
        AndroidPriceWatchStore(applicationContext)
    }
    private var priceWatchState = PriceWatchState.empty()
    private var priceWatchLoadIssue: PriceWatchStoreIssue? = null
    private var savedProductCount = 0
    private val tripPlannerStore by lazy(LazyThreadSafetyMode.NONE) {
        AndroidTripPlannerStore(applicationContext)
    }
    private var tripPlannerState = TripPlannerState()
    private var tripPlannerLoadIssue: TripPlannerStoreIssue? = null
    private val searchExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var argentinaSearchHost: ArgentinaLiveSearchSurfaceHost
    private val argentinaSearchRenderGate = ArgentinaLiveSearchMainThreadRenderGate()
    private var argentinaSearchSurfaceState: ArgentinaLiveSearchSurfaceState? = null
    private val argentinaLaunchArea = ArgentinaQualifiedLaunchAreas.BuenosAiresCaba
    private var searchQuery = ""
    private var searchWasSubmitted = false
    private var searchScrollY = 0
    private var basketScrollY = 0
    private var personalScrollY = 0
    private var tripPlannerScrollY = 0
    private var googlePhotoViewerLaunchInFlight = false
    private var renderedSearchMatches:
        List<PracticalShoppingSearchIdentityPresentation.Match>? = null
    private var productMapDialog: AlertDialog? = null
    private var productMapRequestId = 0L
    private var productMapSelectedKey: String? = null
    private var productMapSelectedLabel: String? = null
    private var productMapContext: ProductMapContext? = null
    private var reopenProductMapAfterTrip = false
    private var pendingProductMapLocation: (() -> Unit)? = null

    private lateinit var shellRoot: View
    private lateinit var shellScrollView: ScrollView
    private lateinit var bottomNavArea: View
    private lateinit var bottomNavigation: BottomNavigationView
    private lateinit var shellBrand: TextView
    private lateinit var shellTagline: TextView
    private lateinit var screenHeaderCard: View
    private lateinit var screenBackAction: MaterialButton
    private lateinit var screenEyebrow: TextView
    private lateinit var screenTitle: TextView
    private lateinit var screenBody: TextView
    private lateinit var screenFootnote: TextView
    private lateinit var primaryAction: Button
    private lateinit var sourcesLicencesButton: MaterialButton
    private lateinit var dataStatusButton: MaterialButton
    private lateinit var homeExperience: PracticalShoppingHomeSurfaceView
    private lateinit var basketExperience: PracticalShoppingBasketSurfaceView
    private lateinit var tripPlannerExperience: TripPlannerSurfaceView
    private lateinit var searchExperience: View
    private lateinit var searchInput: TextInputEditText
    private lateinit var searchButton: MaterialButton
    private lateinit var searchAreaLabel: TextView
    private lateinit var searchStatus: TextView
    private lateinit var searchProgress: ProgressBar
    private lateinit var searchResultsHeading: TextView
    private lateinit var searchResultsCount: TextView
    private lateinit var searchResultsContainer: LinearLayout
    private lateinit var searchLoadMoreButton: MaterialButton
    private lateinit var searchResultsEnd: TextView
    private lateinit var youOverviewExperience: PracticalShoppingYouOverviewView
    private lateinit var savedExperience: PracticalShoppingSavedSurfaceView
    private lateinit var confirmedPurchaseHistoryButton: MaterialButton
    private lateinit var confirmedPurchaseHistoryExperience: ConfirmedPurchaseHistorySurfaceView
    private lateinit var priceWatchButton: MaterialButton
    private lateinit var priceWatchExperience: PriceWatchSurfaceView
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

    private var comparisonActivityOpen = false
    private var homeItemDetailsDialog: AlertDialog? = null
    private var homeItemDetailsItemKey: ShoppingItemKey? = null
    private var homeItemDetailsPackageInput: TextInputEditText? = null
    private var homeItemDetailsBrandInput: TextInputEditText? = null
    private var homeItemDetailsExactProduct: CheckBox? = null
    private var homeRenderState: PracticalShoppingHomeRenderState? = null
    private var homeSavedExactProductContext: PracticalShoppingHomeSavedExactProductContext? = null
    private var homeShareCard: PracticalShoppingHomeShareCard? = null
    private var homeShareDialog: AlertDialog? = null
    private var dataStatusDialog: AlertDialog? = null
    private var privatePriceHistoryDialog: AlertDialog? = null
    private var privatePriceHistoryClearDialog: AlertDialog? = null
    private var privatePriceHistoryExportDialog: AlertDialog? = null
    private var privatePriceHistoryForgetDialog: AlertDialog? = null
    private var confirmedPurchaseDialog: AlertDialog? = null
    private var priceWatchDialog: AlertDialog? = null
    private var priceObservationDialog: AlertDialog? = null
    private var tripOriginDialog: AlertDialog? = null
    private var tripAssumptionsDialog: AlertDialog? = null
    private var tripStorePickerDialog: AlertDialog? = null
    private var suppressSearchInputCallback = false
    private var searchRouteActive = false
    private var basketRouteActive = false
    private var personalRouteActive = false
    private var tripPlannerRouteActive = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_shell)
        shellRoot = findViewById(R.id.shellRoot)
        shellScrollView = findViewById(R.id.shellScrollView)
        bottomNavArea = findViewById(R.id.bottomNavArea)
        bottomNavigation = findViewById(R.id.bottomNavigation)
        shellBrand = findViewById(R.id.shellBrand)
        shellTagline = findViewById(R.id.shellTagline)
        screenHeaderCard = findViewById(R.id.screenHeaderCard)
        screenBackAction = findViewById(R.id.screenBackAction)
        screenEyebrow = findViewById(R.id.screenEyebrow)
        screenTitle = findViewById(R.id.screenTitle)
        screenBody = findViewById(R.id.screenBody)
        screenFootnote = findViewById(R.id.screenFootnote)
        primaryAction = findViewById(R.id.primaryAction)
        sourcesLicencesButton = findViewById(R.id.sourcesLicencesButton)
        dataStatusButton = findViewById(R.id.dataStatusButton)
        homeExperience = findViewById(R.id.homeExperience)
        basketExperience = findViewById(R.id.basketExperience)
        tripPlannerExperience = findViewById(R.id.tripPlannerExperience)
        searchExperience = findViewById(R.id.searchExperience)
        searchInput = findViewById(R.id.searchInput)
        searchButton = findViewById(R.id.searchButton)
        searchAreaLabel = findViewById(R.id.searchAreaLabel)
        searchStatus = findViewById(R.id.searchStatus)
        searchProgress = findViewById(R.id.searchProgress)
        searchResultsHeading = findViewById(R.id.searchResultsHeading)
        searchResultsCount = findViewById(R.id.searchResultsCount)
        searchResultsContainer = findViewById(R.id.searchResultsContainer)
        searchLoadMoreButton = findViewById(R.id.searchLoadMoreButton)
        searchResultsEnd = findViewById(R.id.searchResultsEnd)
        youOverviewExperience = findViewById(R.id.youOverviewExperience)
        savedExperience = findViewById(R.id.savedExperience)
        confirmedPurchaseHistoryButton = findViewById(R.id.confirmedPurchaseHistoryButton)
        confirmedPurchaseHistoryExperience = findViewById(R.id.confirmedPurchaseHistoryExperience)
        priceWatchButton = findViewById(R.id.priceWatchButton)
        priceWatchExperience = findViewById(R.id.priceWatchExperience)
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
        refreshConfirmedPurchaseHistory()
        refreshPriceWatchState()
        refreshTripPlannerState()
        searchQuery = savedInstanceState?.getString(STATE_SEARCH_QUERY).orEmpty()
        searchWasSubmitted = savedInstanceState?.getBoolean(STATE_SEARCH_WAS_SUBMITTED) == true
        searchScrollY = savedInstanceState?.getInt(STATE_SEARCH_SCROLL_Y, 0)?.coerceAtLeast(0) ?: 0
        basketScrollY = savedInstanceState?.getInt(STATE_BASKET_SCROLL_Y, 0)?.coerceAtLeast(0) ?: 0
        personalScrollY = savedInstanceState?.getInt(STATE_PERSONAL_SCROLL_Y, 0)?.coerceAtLeast(0) ?: 0
        tripPlannerScrollY = savedInstanceState?.getInt(STATE_TRIP_PLANNER_SCROLL_Y, 0)?.coerceAtLeast(0) ?: 0
        productMapSelectedKey = savedInstanceState?.getString(STATE_PRODUCT_MAP_SELECTED_KEY)
        productMapSelectedLabel = savedInstanceState?.getString(STATE_PRODUCT_MAP_SELECTED_LABEL)
        argentinaSearchHost = ArgentinaLiveSearchSurfaceHost.createProduction(
            area = argentinaLaunchArea,
            render = ::renderArgentinaSearchState
        )
        if (searchWasSubmitted && searchQuery.trim().isNotBlank()) {
            argentinaSearchHost.restoreForRevalidation(searchQuery)
        }
        configureHomeUi()
        configureBasketUi()
        tripPlannerExperience.onAction = ::handleTripPlannerAction
        configureSearchUi()
        configureSavedUi()
        youOverviewExperience.onAction = { action ->
            when (action) {
                PracticalShoppingYouOverviewAction.OpenBought ->
                    dispatch(AppShellIntent.OpenConfirmedPurchaseHistory)

                PracticalShoppingYouOverviewAction.OpenSaved ->
                    savedExperience.post {
                        shellScrollView.smoothScrollTo(0, savedExperience.top.coerceAtLeast(0))
                    }

                PracticalShoppingYouOverviewAction.OpenWatching ->
                    dispatch(AppShellIntent.OpenPriceWatches)
            }
        }
        confirmedPurchaseHistoryButton.setOnClickListener {
            dispatch(AppShellIntent.OpenConfirmedPurchaseHistory)
        }
        confirmedPurchaseHistoryExperience.onAction = ::handleConfirmedPurchaseHistoryAction
        confirmedPurchaseHistoryExperience.onPriceWatchAction = ::handleTogglePriceWatch
        priceWatchButton.setOnClickListener { dispatch(AppShellIntent.OpenPriceWatches) }
        priceWatchExperience.onAction = ::handlePriceWatchAction

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
                    if (productMapDialog?.isShowing == true) {
                        productMapDialog?.dismiss()
                        productMapDialog = null
                        pendingProductMapLocation = null
                    } else if (
                        shellState.route == AppRoute.OBSERVED_PRICE_SAVED_SELECTION ||
                        shellState.route == AppRoute.OBSERVED_PRICE_CONFIRMATION_DRAFT ||
                        shellState.route == AppRoute.STAPLE_WATCH_SETUP ||
                        shellState.route == AppRoute.STAPLE_WATCH_POLICY ||
                        shellState.route == AppRoute.CONFIRMED_PURCHASE_HISTORY ||
                        shellState.route == AppRoute.PRICE_WATCHES ||
                        shellState.route == AppRoute.TRIP_PLANNER
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
        screenBackAction.setOnClickListener { dispatch(AppShellIntent.NavigateBack) }
        sourcesLicencesButton.setOnClickListener { showSourcesLicences() }
        dataStatusButton.setOnClickListener { showDataStatus() }

        bottomNavigation.selectedItemId = menuIdFor(shellState.selectedPrimaryTab)
        renderShell(shellState)
        restoreHomeItemDetailsDialog(savedInstanceState)
    }

    override fun onResume() {
        super.onResume()

        // A Custom Tab owns the external page. When the shopper returns, the
        // next explicit tap is allowed to open it again.
        googlePhotoViewerLaunchInFlight = false
        refreshHomePrivateMemory()
        refreshConfirmedPurchaseHistory()
        refreshPriceWatchState()

        if (comparisonActivityOpen && shellState.route == AppRoute.COMPARE) {
            comparisonActivityOpen = false
            dispatch(AppShellIntent.NavigateBack)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_PRIMARY_TAB, shellState.selectedPrimaryTab.name)
        outState.putString(
            STATE_ROUTE,
            AppShellStateRestoration.restorableRoute(shellState).name
        )

        val homeSnapshot = PracticalShoppingHomeSession.snapshot(homeSessionState)
        outState.putString(STATE_HOME_QUERY, homeSnapshot.query)
        outState.putBoolean(STATE_HOME_WAS_SUBMITTED, homeSnapshot.wasSubmitted)
        outState.putString(STATE_HOME_CHICKEN_CHOICE, homeSnapshot.chickenChoice?.name)
        outState.putString(
            STATE_HOME_EXTRA_STOP_MINIMUM_SAVINGS,
            homeSnapshot.extraStopMinimumSavingsChoice.name
        )
        // The presence bit distinguishes an intentional empty private list from an older or
        // partial Bundle that never carried private-list fields.
        outState.putBoolean(STATE_HOME_PRIVATE_STATE_PRESENT, true)
        PracticalShoppingHomeSessionStoreCodec
            .encodePrivateIdentities(homeSnapshot.privateIdentities)
            ?.let { encoded -> outState.putString(STATE_HOME_PRIVATE_IDENTITIES, encoded) }
        PracticalShoppingHomeSessionStoreCodec
            .encodePrivateBasketCollectedIdentityKeys(homeSnapshot.privateBasketCollectedIdentityKeys)
            ?.let { encoded -> outState.putString(STATE_HOME_PRIVATE_BASKET_KEYS, encoded) }
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

        if (searchRouteActive) searchScrollY = shellScrollView.scrollY.coerceAtLeast(0)
        if (basketRouteActive) basketScrollY = shellScrollView.scrollY.coerceAtLeast(0)
        if (personalRouteActive) personalScrollY = shellScrollView.scrollY.coerceAtLeast(0)
        if (tripPlannerRouteActive) tripPlannerScrollY = shellScrollView.scrollY.coerceAtLeast(0)
        outState.putString(STATE_SEARCH_QUERY, searchQuery)
        outState.putBoolean(STATE_SEARCH_WAS_SUBMITTED, searchWasSubmitted)
        outState.putInt(STATE_SEARCH_SCROLL_Y, searchScrollY)
        outState.putInt(STATE_BASKET_SCROLL_Y, basketScrollY)
        outState.putInt(STATE_PERSONAL_SCROLL_Y, personalScrollY)
        outState.putInt(STATE_TRIP_PLANNER_SCROLL_Y, tripPlannerScrollY)
        outState.putString(STATE_PRODUCT_MAP_SELECTED_KEY, productMapSelectedKey)
        outState.putString(STATE_PRODUCT_MAP_SELECTED_LABEL, productMapSelectedLabel)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        dismissHomeItemDetailsDialog()
        dismissHomeShareDialog()
        productMapDialog?.dismiss()
        productMapDialog = null
        pendingProductMapLocation = null
        if (::argentinaSearchHost.isInitialized) {
            argentinaSearchHost.close()
        }
        dataStatusDialog?.dismiss()
        dataStatusDialog = null
        privatePriceHistoryDialog?.dismiss()
        privatePriceHistoryDialog = null
        privatePriceHistoryClearDialog?.dismiss()
        privatePriceHistoryClearDialog = null
        privatePriceHistoryForgetDialog?.dismiss()
        privatePriceHistoryForgetDialog = null
        privatePriceHistoryExportDialog?.dismiss()
        privatePriceHistoryExportDialog = null
        confirmedPurchaseDialog?.dismiss()
        confirmedPurchaseDialog = null
        priceWatchDialog?.dismiss()
        priceWatchDialog = null
        priceObservationDialog?.dismiss()
        priceObservationDialog = null
        tripOriginDialog?.dismiss()
        tripOriginDialog = null
        tripAssumptionsDialog?.dismiss()
        tripAssumptionsDialog = null
        tripStorePickerDialog?.dismiss()
        tripStorePickerDialog = null
        if (::homeExperience.isInitialized) {
            homeExperience.onQueryChanged = null
            homeExperience.onSubmit = null
            homeExperience.onQuickAdd = null
            homeExperience.onRemoveItem = null
            homeExperience.onRemoveUnknownItem = null
            homeExperience.onRemovePrivateIdentity = null
            homeExperience.onOpenPrivateIdentityPhotos = null
            homeExperience.onChickenChoice = null
            homeExperience.onExtraStopMinimumSavingsChoice = null
            homeExperience.onEditItemDetails = null
            homeExperience.onAddObservedPrice = null
            homeExperience.onCompare = null
            homeExperience.onReviewPrivateMemory = null
            homeExperience.onExportPrivateMemory = null
            homeExperience.onForgetPrivateMemory = null
            homeExperience.onGoodPrice = null
            homeExperience.onShopAgain = null
            homeExperience.onSharePlan = null
            homeExperience.onTogglePriceWatch = null
            homeExperience.onOpenSearch = null
            homeExperience.onOpenBasket = null
        }
        if (::basketExperience.isInitialized) {
            basketExperience.onAction = null
            basketExperience.onPrivateIdentityToggle = null
            basketExperience.onOpenPrivateIdentityPhotos = null
            basketExperience.onConfirmPurchase = null
            basketExperience.onTogglePriceWatch = null
            basketExperience.onOpenTripPlanner = null
        }
        if (::confirmedPurchaseHistoryExperience.isInitialized) {
            confirmedPurchaseHistoryExperience.onAction = null
            confirmedPurchaseHistoryExperience.onPriceWatchAction = null
        }
        if (::priceWatchExperience.isInitialized) {
            priceWatchExperience.onAction = null
        }
        if (::tripPlannerExperience.isInitialized) {
            tripPlannerExperience.onAction = null
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
        return AppShellStateRestoration.restore(
            primaryTabName = savedInstanceState?.getString(STATE_PRIMARY_TAB),
            routeName = savedInstanceState?.getString(STATE_ROUTE)
        )
    }

    private fun restoreHomeState(
        savedInstanceState: Bundle?
    ): PracticalShoppingHomeSession.State {
        val durableLoad = homeSessionStore.loadResult()
        if (durableLoad.issue != PracticalShoppingHomeSessionStoreIssue.NONE) {
            homeSessionPersistenceBlocked = true
        }
        if (savedInstanceState?.containsKey(STATE_HOME_QUERY) != true) {
            durableLoad.snapshot?.let { persisted ->
                return PracticalShoppingHomeSession.restoreState(persisted)
            }
        }

        val privateIdentityState =
            PracticalShoppingHomeSessionStoreCodec.recoverPrivateIdentityState(
                savedStateComplete =
                    savedInstanceState?.getBoolean(STATE_HOME_PRIVATE_STATE_PRESENT, false) == true,
                savedPrivateIdentities =
                    savedInstanceState?.getString(STATE_HOME_PRIVATE_IDENTITIES),
                savedPrivateBasketCollectedIdentityKeys =
                    savedInstanceState?.getString(STATE_HOME_PRIVATE_BASKET_KEYS),
                durableSnapshot = durableLoad.snapshot,
                durableStateTrusted =
                    durableLoad.issue == PracticalShoppingHomeSessionStoreIssue.NONE
            )
        if (privateIdentityState == null) {
            // Keep the unreadable durable record intact until a user mutation supplies a
            // replacement. Rendering an empty fallback must not overwrite evidence of a read
            // failure.
            homeSessionPersistenceBlocked = true
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
                    savedInstanceState?.getByteArray(STATE_HOME_REQUEST_DETAILS),
                privateIdentities =
                    privateIdentityState?.identities.orEmpty(),
                privateBasketCollectedIdentityKeys =
                    privateIdentityState?.collectedIdentityKeys.orEmpty()
            )
        )
    }

    private fun configureHomeUi() {
        homeExperience.onOpenSearch = {
            dispatch(AppShellIntent.SelectPrimary(AppPrimaryTab.SEARCH))
        }
        homeExperience.onOpenBasket = {
            dispatch(AppShellIntent.SelectPrimary(AppPrimaryTab.BASKET))
        }
        homeExperience.onQueryChanged = { rawQuery ->
            homeSessionState =
                PracticalShoppingHomeSession.queryChanged(homeSessionState, rawQuery)
            renderHome()
        }
        homeExperience.onSubmit = { rawQuery ->
            homeSessionState = PracticalShoppingHomeSession.submit(homeSessionState, rawQuery)
            renderHome(revealResult = true)
        }
        homeExperience.onQuickAdd = { choice ->
            homeSessionState = PracticalShoppingHomeSession.addQuickItem(homeSessionState, choice)
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
        homeExperience.onRemovePrivateIdentity = { identityKey ->
            val previousState = homeSessionState
            homeSessionState =
                PracticalShoppingHomeSession.removePrivateIdentity(
                    homeSessionState,
                    identityKey
                )
            if (homeSessionState != previousState) {
                authorizePrivateListPersistence()
            }
            renderHome()
        }
        homeExperience.onOpenPrivateIdentityPhotos = { identityKey ->
            homeSessionState.privateIdentities
                .firstOrNull { identity -> identity.identityKey == identityKey }
                ?.let(::openGoogleProductPhotos)
        }
        homeExperience.onChickenChoice = { choice ->
            homeSessionState =
                PracticalShoppingHomeSession.chooseChicken(homeSessionState, choice)
            renderHome(revealResult = true)
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
        homeExperience.onOpenConfirmedPurchaseHistory = {
            dispatch(AppShellIntent.SelectPrimary(AppPrimaryTab.SAVED))
            dispatch(AppShellIntent.OpenConfirmedPurchaseHistory)
        }
        homeExperience.onExportPrivateMemory = { exportPrivatePriceHistory() }
        homeExperience.onForgetPrivateMemory = { forgetPrivatePriceHistoryObservation() }
        homeExperience.onGoodPrice = { openGoodPriceCheck() }
        homeExperience.onShopAgain = {
            homeSessionState = PracticalShoppingHomeSession.shopAgain(homeSessionState)
            renderHome(revealResult = true)
        }
        homeExperience.onSharePlan = { shareHomePlan() }
        homeExperience.onTogglePriceWatch = ::handleTogglePriceWatch
        renderHome()
    }

    private fun renderHome(revealResult: Boolean = false) {
        if (!homeSessionPersistenceBlocked) {
            homeSessionStore.save(PracticalShoppingHomeSession.snapshot(homeSessionState))
        }
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
                    },
                savedExactProductContext = homeSavedExactProductContext,
                privateIdentities = homeSessionState.privateIdentities,
                privateIdentityCollectedKeys = homeSessionState.privateBasketCollectedIdentityKeys,
                watchedProductIdentityKeys = watchedProductIdentityKeys(),
                confirmedPurchaseCount = confirmedPurchaseHistoryState.purchases.size
            )
        // A render invalidates any pending share preview. This prevents a dialog opened for an
        // earlier list/result from sending text after the Home projection has changed.
        dismissHomeShareDialog()
        homeRenderState = homeState
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
        if (revealResult) {
            homeExperience.revealProjectedResult()
        }
        basketExperience.render(PracticalShoppingBasketRenderer.render(homeState))
        if (shellState.route == AppRoute.TRIP_PLANNER) {
            renderTripPlanner()
        }
    }

    private fun authorizePrivateListPersistence() {
        // Only an explicit private-list mutation may replace a durable record that could not be
        // read. Ordinary query edits/navigation must not erase that record as a side effect.
        homeSessionPersistenceBlocked = false
    }

    private fun dismissHomeShareDialog() {
        homeShareDialog?.dismiss()
        homeShareDialog = null
        homeShareCard = null
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
        privatePriceHistoryForgetDialog?.dismiss()
        privatePriceHistoryForgetDialog = null
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
                setTextColor(ValuePilotPalette.color("#6B7280"))
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
                PracticalShoppingBasketUiAction.OpenSearch ->
                    dispatch(AppShellIntent.SelectPrimary(AppPrimaryTab.SEARCH))
                PracticalShoppingBasketUiAction.OpenTripPlanner ->
                    dispatch(AppShellIntent.OpenTripPlanner)
                is PracticalShoppingBasketUiAction.ConfirmPurchase ->
                    openConfirmedPurchaseDialog(action)
            }
        }
        basketExperience.onPrivateIdentityToggle = { identityKey ->
            val previousState = homeSessionState
            val nextState =
                PracticalShoppingHomeSession.togglePrivateIdentityCollected(
                    homeSessionState,
                    identityKey
                )
            homeSessionState = nextState
            if (nextState != previousState) {
                authorizePrivateListPersistence()
                homeSessionStore.save(PracticalShoppingHomeSession.snapshot(nextState))
            }
            homeRenderState =
                homeRenderState?.copy(
                    privateIdentityCollectedKeys = nextState.privateBasketCollectedIdentityKeys
                )
            val updatedInPlace =
                basketExperience.updatePrivateIdentityCollected(
                    identityKey = identityKey,
                    collected = identityKey in nextState.privateBasketCollectedIdentityKeys
                )
            if (!updatedInPlace || shellState.selectedPrimaryTab != AppPrimaryTab.BASKET) {
                // A stale or detached Basket surface must still converge through
                // the normal immutable render path. The common tap path above
                // never rebuilds Home or private identity rows.
                renderHome()
            }
        }
        basketExperience.onOpenPrivateIdentityPhotos = { identityKey ->
            homeSessionState.privateIdentities
                .firstOrNull { identity -> identity.identityKey == identityKey }
                ?.let(::openGoogleProductPhotos)
        }
        basketExperience.onConfirmPurchase = { action ->
            openConfirmedPurchaseDialog(action)
        }
        basketExperience.onTogglePriceWatch = ::handleTogglePriceWatch
        basketExperience.onOpenTripPlanner = { dispatch(AppShellIntent.OpenTripPlanner) }
    }

    /**
     * Opens the only Android-side entry point that can create a confirmed purchase. Basket
     * check-off and Search/Home identity actions never call the store directly.
     */
    private fun openConfirmedPurchaseDialog(
        action: PracticalShoppingBasketUiAction.ConfirmPurchase,
        existing: ConfirmedPurchase? = null
    ) {
        confirmedPurchaseDialog?.dismiss()

        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(4), dp(4), 0)
        }
        val quantityLayout = TextInputLayout(this).apply {
            hint = "Quantity purchased (optional)"
            isCounterEnabled = true
            counterMaxLength = 24
        }
        val quantityInput = TextInputEditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            filters = arrayOf(InputFilter.LengthFilter(24))
            setSingleLine(true)
            setText(existing?.quantity?.let(ConfirmedPurchaseFormParser::displayQuantityText).orEmpty())
        }
        quantityLayout.addView(quantityInput)

        val quantityUnitLayout = TextInputLayout(this).apply {
            hint = "Quantity unit (count, g/kg, or ml/l; optional)"
            isCounterEnabled = true
            counterMaxLength = 16
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(12) }
        }
        val quantityUnitInput = TextInputEditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            filters = arrayOf(InputFilter.LengthFilter(16))
            setSingleLine(true)
            setText(existing?.quantity?.let(ConfirmedPurchaseFormParser::displayQuantityUnitText).orEmpty())
        }
        quantityUnitLayout.addView(quantityUnitInput)

        val amountLayout = TextInputLayout(this).apply {
            hint = "Amount paid (optional, exact decimal)"
            isCounterEnabled = true
            counterMaxLength = 32
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(12) }
        }
        val amountInput = TextInputEditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            filters = arrayOf(InputFilter.LengthFilter(32))
            setSingleLine(true)
            setText(existing?.amountPaid?.let { amount ->
                java.math.BigDecimal.valueOf(amount.minorUnits)
                    .movePointLeft(amount.fractionDigits)
                    .toPlainString()
            }.orEmpty())
        }
        amountLayout.addView(amountInput)

        val currencyLayout = TextInputLayout(this).apply {
            hint = "Currency code (optional, e.g. CAD)"
            isCounterEnabled = true
            counterMaxLength = 3
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(12) }
        }
        val currencyInput = TextInputEditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            filters = arrayOf(InputFilter.LengthFilter(3))
            setSingleLine(true)
            setText(existing?.amountPaid?.currencyCode.orEmpty())
        }
        currencyLayout.addView(currencyInput)

        val dateLayout = TextInputLayout(this).apply {
            hint = "Purchase date (YYYY-MM-DD, optional)"
            isCounterEnabled = true
            counterMaxLength = 10
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(12) }
        }
        val dateInput = TextInputEditText(this).apply {
            inputType = InputType.TYPE_CLASS_DATETIME
            filters = arrayOf(InputFilter.LengthFilter(10))
            setSingleLine(true)
            setText(existing?.purchaseDateIso.orEmpty())
        }
        dateLayout.addView(dateInput)

        val storeLayout = TextInputLayout(this).apply {
            hint = "Store (optional)"
            isCounterEnabled = true
            counterMaxLength = 240
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(12) }
        }
        val storeInput = TextInputEditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            filters = arrayOf(InputFilter.LengthFilter(240))
            setSingleLine(true)
            setText(existing?.store?.displayName.orEmpty())
        }
        storeLayout.addView(storeInput)

        // A private preferred store is an explicit convenience association only. It does not
        // turn a purchase into a live offer or infer a store when the shopper leaves it blank.
        val preferredStore = preferredTripStoreSelection()
        val associatePreferredStore = CheckBox(this).apply {
            text = preferredStore?.let { "Associate with preferred store: ${it.displayName}" }
            isChecked = preferredStore != null && existing?.store?.storeKey == preferredStore.storeKey
            visibility = if (preferredStore == null) View.GONE else View.VISIBLE
            setPadding(0, dp(8), 0, 0)
        }

        val explicitConfirmation = CheckBox(this).apply {
            text = "I explicitly confirm this purchase"
            isChecked = false
            setPadding(0, dp(10), 0, 0)
        }
        val note = TextView(this).apply {
            text = "${action.displayName}. ${action.plannedPriceText?.let { "The plan price ($it) is only a hint; enter what you actually paid." } ?: "Enter only facts you know; blank fields stay unknown."}"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(ValuePilotPalette.color("#6B7280"))
            setPadding(dp(4), dp(8), dp(4), 0)
        }
        body.addView(note)
        body.addView(quantityLayout)
        body.addView(quantityUnitLayout)
        body.addView(amountLayout)
        body.addView(currencyLayout)
        body.addView(dateLayout)
        body.addView(storeLayout)
        body.addView(associatePreferredStore)
        body.addView(explicitConfirmation)

        val dialog =
            AlertDialog.Builder(this)
                .setTitle(if (existing == null) "Confirm purchase" else "Correct confirmed purchase")
                .setView(body)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(if (existing == null) "Save purchase" else "Save correction", null)
                .create()
        confirmedPurchaseDialog = dialog
        dialog.setOnDismissListener {
            if (confirmedPurchaseDialog === dialog) {
                confirmedPurchaseDialog = null
            }
        }
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                quantityLayout.error = null
                quantityUnitLayout.error = null
                amountLayout.error = null
                currencyLayout.error = null
                dateLayout.error = null
                storeLayout.error = null
                if (!explicitConfirmation.isChecked) {
                    explicitConfirmation.error = "Explicit confirmation is required"
                    return@setOnClickListener
                }
                explicitConfirmation.error = null

                val parsed =
                    ConfirmedPurchaseFormParser.parse(
                        ConfirmedPurchaseFormInput(
                            quantityText = quantityInput.text?.toString().orEmpty(),
                            quantityUnitText = quantityUnitInput.text?.toString().orEmpty(),
                            amountText = amountInput.text?.toString().orEmpty(),
                            currencyText = currencyInput.text?.toString().orEmpty(),
                            dateText = dateInput.text?.toString().orEmpty(),
                            storeText = storeInput.text?.toString().orEmpty()
                        )
                    )
                if (!parsed.accepted || parsed.values == null) {
                    val failure = requireNotNull(parsed.failure)
                    when (failure.field) {
                        ConfirmedPurchaseFormField.QUANTITY -> {
                            quantityLayout.error = failure.message
                            quantityUnitLayout.error = failure.message
                        }
                        ConfirmedPurchaseFormField.AMOUNT -> {
                            amountLayout.error = failure.message
                            currencyLayout.error = failure.message
                        }
                        ConfirmedPurchaseFormField.CURRENCY -> currencyLayout.error = failure.message
                        ConfirmedPurchaseFormField.DATE -> dateLayout.error = failure.message
                        ConfirmedPurchaseFormField.STORE -> storeLayout.error = failure.message
                    }
                    return@setOnClickListener
                }
                val values = requireNotNull(parsed.values)
                val associatedStore =
                    if (associatePreferredStore.isChecked && preferredStore != null) {
                        ConfirmedPurchaseStore(
                            displayName = preferredStore.displayName,
                            storeKey = preferredStore.storeKey
                        )
                    } else {
                        values.store
                    }
                val input =
                    ConfirmedPurchaseConfirmationInput(
                        purchaseId = existing?.purchaseId ?: "purchase-${UUID.randomUUID()}",
                        productIdentityKey = action.productIdentityKey,
                        identityOrigin = existing?.identityOrigin ?: action.identityOrigin,
                        displayName = existing?.displayName ?: action.displayName,
                        brand = existing?.brand ?: action.brand,
                        packageQuantityLabel = existing?.packageQuantityLabel ?: action.packageQuantityLabel,
                        quantity = values.quantity,
                        amountPaid = values.amountPaid,
                        purchaseDateIso = values.purchaseDateIso,
                        store = associatedStore,
                        explicitlyConfirmed = true
                    )
                val confirmed = ConfirmedPurchaseConfirmation.confirm(input)
                val purchase = confirmed.purchase
                if (!confirmed.accepted || purchase == null) {
                    dialog.dismiss()
                    showConfirmedPurchaseError("The purchase details were not valid, so nothing was saved.")
                    return@setOnClickListener
                }
                val result =
                    if (existing == null) {
                        confirmedPurchaseStore.append(purchase)
                    } else {
                        confirmedPurchaseStore.replace(purchase)
                    }
                if (!result.accepted || result.state == null) {
                    dialog.dismiss()
                    refreshConfirmedPurchaseHistory()
                    showConfirmedPurchaseError("ValuePilot could not save this private purchase. Existing history was not replaced.")
                    return@setOnClickListener
                }
                confirmedPurchaseHistoryState = result.state
                confirmedPurchaseHistoryLoadIssue = null
                dialog.dismiss()
                if (shellState.route == AppRoute.CONFIRMED_PURCHASE_HISTORY || shellState.route == AppRoute.PRICE_WATCHES) {
                    renderShell(shellState)
                }
            }
        }
        dialog.show()
    }

    private fun showConfirmedPurchaseError(message: String) {
        AlertDialog.Builder(this)
            .setTitle("Purchase not saved")
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun handleConfirmedPurchaseHistoryAction(action: ConfirmedPurchaseHistoryUiAction) {
        val purchase = confirmedPurchaseHistoryState.purchases.firstOrNull {
            it.purchaseId == when (action) {
                is ConfirmedPurchaseHistoryUiAction.Repeat -> action.purchaseId
                is ConfirmedPurchaseHistoryUiAction.Edit -> action.purchaseId
                is ConfirmedPurchaseHistoryUiAction.Delete -> action.purchaseId
            }
        }
        when (action) {
            is ConfirmedPurchaseHistoryUiAction.Repeat -> {
                if (purchase == null) {
                    refreshConfirmedPurchaseHistory()
                    renderShell(shellState)
                    return
                }
                val identity =
                    PracticalShoppingPrivateIdentity(
                        identityKey = purchase.productIdentityKey,
                        displayName = purchase.displayName,
                        brand = purchase.brand,
                        sourceLabel = "Private purchase history",
                        packageQuantityLabel = purchase.packageQuantityLabel
                    )
                val next = PracticalShoppingHomeSession.addPrivateIdentity(homeSessionState, identity)
                if (next != homeSessionState) {
                    homeSessionState = next
                    authorizePrivateListPersistence()
                    homeSessionStore.save(PracticalShoppingHomeSession.snapshot(next))
                }
                dispatch(AppShellIntent.SelectPrimary(AppPrimaryTab.HOME))
                renderHome()
            }
            is ConfirmedPurchaseHistoryUiAction.Edit -> {
                if (purchase == null) {
                    refreshConfirmedPurchaseHistory()
                    renderShell(shellState)
                } else {
                    openConfirmedPurchaseDialog(
                        action = PracticalShoppingBasketUiAction.ConfirmPurchase(
                            productIdentityKey = purchase.productIdentityKey,
                            displayName = purchase.displayName,
                            brand = purchase.brand,
                            packageQuantityLabel = purchase.packageQuantityLabel,
                            plannedPriceText = null,
                            identityOrigin = purchase.identityOrigin
                        ),
                        existing = purchase
                    )
                }
            }
            is ConfirmedPurchaseHistoryUiAction.Delete -> {
                if (purchase == null) {
                    refreshConfirmedPurchaseHistory()
                    renderShell(shellState)
                    return
                }
                AlertDialog.Builder(this)
                    .setTitle("Delete confirmed purchase?")
                    .setMessage("This removes only this private history record. It does not change your Home list or price memory.")
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton("Delete") { _, _ ->
                        val latest = confirmedPurchaseStore.load()
                        val result =
                            if (latest.accepted) {
                                confirmedPurchaseStore.remove(purchase.purchaseId)
                            } else {
                                null
                            }
                        if (result?.accepted == true && result.state != null) {
                            confirmedPurchaseHistoryState = result.state
                            confirmedPurchaseHistoryLoadIssue = null
                            renderShell(shellState)
                        } else {
                            refreshConfirmedPurchaseHistory()
                            renderShell(shellState)
                            showConfirmedPurchaseError("The record was not deleted; existing history was kept.")
                        }
                    }
                    .show()
            }
        }
    }

    private fun configureSearchUi() {
        suppressSearchInputCallback = true
        searchInput.setText(searchQuery)
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

                    searchQuery = s?.toString().orEmpty()
                    searchWasSubmitted = false
                    argentinaSearchHost.queryChanged(searchQuery)
                }
            }
        )

        searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                submitArgentinaSearch()
                true
            } else {
                false
            }
        }

        searchButton.setOnClickListener { submitArgentinaSearch() }
        searchLoadMoreButton.setOnClickListener { argentinaSearchHost.loadMore() }

        quickSearchEntries().forEach { (chipId, query) ->
            configureQuickSearch(chipId, query)
        }
    }

    private fun configureSavedUi() {
        val savedPresenter =
            PracticalShoppingSavedSurfacePresenter(
                renderer = savedExperience,
                watchedProductIdentityKeys = ::watchedProductIdentityKeys,
                onProductCount = { count ->
                    savedProductCount = count
                }
            )
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
                if (
                    state.status !in setOf(
                        PracticalShoppingSavedLifecycleStatus.READY,
                        PracticalShoppingSavedLifecycleStatus.DEGRADED
                    )
                ) {
                    // A Saved refresh/mutation is authoritative for whether a choice still
                    // exists. Do not leave old identity context visible while it is unresolved.
                    homeSavedExactProductContext = null
                    if (shellState.route == AppRoute.HOME) renderHome()
                }
                savedPresenter.render(state)
                stapleLaunchPresenter.render(state)
                observedPriceLaunchPresenter.render(state)
                if (::youOverviewExperience.isInitialized && shellState.route == AppRoute.SAVED) {
                    renderYouOverview()
                }
            }
        val savedSnapshotObserver =
            PracticalShoppingSavedValidatedSnapshotObserver { snapshot ->
                homeSavedExactProductContext =
                    PracticalShoppingHomeSavedExactProductContext.fromSnapshot(snapshot)
                if (shellState.route == AppRoute.HOME) renderHome()
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
                PracticalShoppingSavedSurfaceAction.OpenSearch ->
                    dispatch(AppShellIntent.SelectPrimary(AppPrimaryTab.SEARCH))

                is PracticalShoppingSavedSurfaceAction.AddToHome ->
                    addSavedProductToHome(action)

                is PracticalShoppingSavedSurfaceAction.CheckProductPrice ->
                    openGoodPriceForSavedProduct(action.displayName)

                is PracticalShoppingSavedSurfaceAction.TogglePriceWatch ->
                    handleTogglePriceWatch(
                        PriceWatchUiAction.ToggleWatch(
                            productIdentityKey = action.productIdentityKey,
                            displayName = action.displayName,
                            brand = null,
                            packageQuantityLabel = null,
                            watched = action.watched
                        )
                    )

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
            if (argentinaSearchSurfaceState?.mode == ArgentinaLiveSearchSurfaceMode.LOADING) {
                hideKeyboard()
                return@setOnClickListener
            }
            setSearchQuery(query)
            submitArgentinaSearch()
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
        suppressSearchInputCallback = true
        searchInput.setText(query)
        searchInput.setSelection(query.length)
        suppressSearchInputCallback = false
        searchQuery = query
        argentinaSearchHost.queryChanged(query)
    }

    private fun submitArgentinaSearch() {
        val rawQuery = searchInput.text?.toString().orEmpty()
        if (rawQuery.trim().isBlank() ||
            rawQuery.length > ArgentinaBackendContract.MAX_QUERY_CHARS ||
            argentinaSearchSurfaceState?.mode == ArgentinaLiveSearchSurfaceMode.LOADING
        ) {
            hideKeyboard()
            return
        }
        searchQuery = rawQuery
        searchWasSubmitted = true
        hideKeyboard()
        argentinaSearchHost.submit(rawQuery)
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
        privatePriceHistoryForgetDialog?.dismiss()
        privatePriceHistoryForgetDialog = null
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
        privatePriceHistoryForgetDialog?.dismiss()
        privatePriceHistoryForgetDialog = null

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

    /**
     * Opens a bounded exact-observation picker. The picker carries fingerprints rather than list
     * positions, and the selected id is re-checked against the atomic store before deletion.
     */
    private fun forgetPrivatePriceHistoryObservation() {
        if (homePrivateMemoryLoadIssue != null) {
            openComparison()
            return
        }

        val presentation =
            PracticalShoppingPrivatePriceHistoryForgetPresentation.from(homePrivateMemoryState)
        if (presentation == null) {
            AlertDialog.Builder(this)
                .setTitle(R.string.home_private_memory_forget_error_title)
                .setMessage(R.string.home_private_memory_forget_empty)
                .setPositiveButton(android.R.string.ok, null)
                .show()
            return
        }

        privatePriceHistoryDialog?.dismiss()
        privatePriceHistoryDialog = null
        privatePriceHistoryClearDialog?.dismiss()
        privatePriceHistoryClearDialog = null
        privatePriceHistoryExportDialog?.dismiss()
        privatePriceHistoryExportDialog = null

        var selectedObservationId: String? = null
        val choices = presentation.choices.map { choice -> choice.label }.toTypedArray()
        val dialog =
            AlertDialog.Builder(this)
                .setTitle(presentation.title)
                .setMessage(presentation.message)
                .setSingleChoiceItems(choices, -1) { _, which ->
                    selectedObservationId = presentation.choices.getOrNull(which)?.observationId
                    privatePriceHistoryForgetDialog
                        ?.getButton(AlertDialog.BUTTON_POSITIVE)
                        ?.isEnabled = selectedObservationId != null
                }
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.home_private_memory_forget_confirm, null)
                .create()
        dialog.setOnDismissListener {
            if (privatePriceHistoryForgetDialog === dialog) {
                privatePriceHistoryForgetDialog = null
            }
        }
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val observationId = selectedObservationId ?: return@setOnClickListener
                val loaded = homePrivateMemoryStore.load()
                val latestState = loaded.state
                if (!loaded.accepted || latestState == null) {
                    dialog.dismiss()
                    refreshHomePrivateMemory()
                    showPrivatePriceHistoryForgetError(loaded.issue)
                    return@setOnClickListener
                }
                if (latestState.entries.none { it.observationId == observationId }) {
                    dialog.dismiss()
                    refreshHomePrivateMemory()
                    showPrivatePriceHistoryForgetError(
                        CompareHerePrivatePriceMemoryStoreIssue.ENTRY_NOT_FOUND
                    )
                    return@setOnClickListener
                }

                val result = homePrivateMemoryStore.remove(observationId)
                if (!result.accepted) {
                    dialog.dismiss()
                    refreshHomePrivateMemory()
                    showPrivatePriceHistoryForgetError(result.issue)
                    return@setOnClickListener
                }

                dialog.dismiss()
                homePrivateMemoryState = requireNotNull(result.state)
                homePrivateMemoryLoadIssue = null
                renderHome()
            }
        }
        privatePriceHistoryForgetDialog = dialog
        dialog.show()
    }

    private fun showPrivatePriceHistoryForgetError(
        issue: CompareHerePrivatePriceMemoryStoreIssue?
    ) {
        val messageRes =
            when (issue) {
                CompareHerePrivatePriceMemoryStoreIssue.ENTRY_NOT_FOUND ->
                    R.string.home_private_memory_forget_not_found

                CompareHerePrivatePriceMemoryStoreIssue.READ_FAILED,
                CompareHerePrivatePriceMemoryStoreIssue.STORED_DATA_TOO_LARGE,
                CompareHerePrivatePriceMemoryStoreIssue.STORED_DATA_INVALID,
                CompareHerePrivatePriceMemoryStoreIssue.ENCODE_REJECTED,
                CompareHerePrivatePriceMemoryStoreIssue.WRITE_FAILED,
                CompareHerePrivatePriceMemoryStoreIssue.DELETE_FAILED,
                null -> R.string.home_private_memory_forget_failed
            }
        AlertDialog.Builder(this)
            .setTitle(R.string.home_private_memory_forget_error_title)
            .setMessage(messageRes)
            .setPositiveButton(android.R.string.ok, null)
            .show()
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
        privatePriceHistoryForgetDialog?.dismiss()
        privatePriceHistoryForgetDialog = null
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
        val presentation =
            PracticalShoppingDataStatusPresentation.from(
                privateMemory = homePrivateMemoryState,
                privateMemoryAvailable = homePrivateMemoryLoadIssue == null
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
     * Previews and explicitly shares a bounded summary of the current projected Home plan.
     * The pure projector formats existing UI facts only; it never re-ranks, reads private item
     * history, or turns the fictional demo into a live retailer claim.
     */
    private fun shareHomePlan() {
        val state = homeRenderState ?: return
        val result = state.result ?: return
        if (result.primary == null) return
        val card =
            PracticalShoppingHomeShareCardProjector.project(result, state.sampleNotice)
                ?: return

        dismissHomeShareDialog()
        val dialog =
            AlertDialog.Builder(this)
                .setTitle(R.string.home_share_plan_title)
                .setMessage(card.preview)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.home_share_plan_send, null)
                .create()
        dialog.setOnDismissListener {
            if (homeShareDialog === dialog) {
                homeShareDialog = null
                homeShareCard = null
            }
        }
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val currentCard = homeShareCard ?: return@setOnClickListener
                val sendIntent =
                    Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, currentCard.title)
                        putExtra(Intent.EXTRA_TEXT, currentCard.text)
                    }
                try {
                    startActivity(
                        Intent.createChooser(
                            sendIntent,
                            getString(R.string.home_share_plan)
                        )
                    )
                    dialog.dismiss()
                } catch (_: ActivityNotFoundException) {
                    dialog.dismiss()
                    AlertDialog.Builder(this)
                        .setTitle(R.string.home_share_plan_title)
                        .setMessage(R.string.home_share_plan_unavailable)
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                }
            }
        }
        homeShareCard = card
        homeShareDialog = dialog
        dialog.show()
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
        val shouldReopenMap =
            intent == AppShellIntent.NavigateBack &&
                shellState.route == AppRoute.TRIP_PLANNER &&
                next.route == AppRoute.BASKET &&
                reopenProductMapAfterTrip
        shellState = next
        renderShell(next)
        if (shouldReopenMap) {
            reopenProductMapAfterTrip = false
            // The map was entered from Search. Restore that contextual owner before
            // reopening the dialog so closing it returns to the same query/results.
            dispatch(AppShellIntent.SelectPrimary(AppPrimaryTab.SEARCH))
            mainHandler.post(::reopenProductMap)
        }
    }

    private fun renderShell(state: AppShellState) {
        if (state.route != AppRoute.HOME) {
            dismissHomeItemDetailsDialog()
            dismissHomeShareDialog()
        }
        if (
            state.route != AppRoute.BASKET &&
                state.route != AppRoute.CONFIRMED_PURCHASE_HISTORY
        ) {
            confirmedPurchaseDialog?.dismiss()
        }
        if (state.route != AppRoute.PRICE_WATCHES) {
            priceWatchDialog?.dismiss()
            priceWatchDialog = null
            priceObservationDialog?.dismiss()
            priceObservationDialog = null
        }
        if (state.route != AppRoute.TRIP_PLANNER) {
            tripOriginDialog?.dismiss()
            tripOriginDialog = null
            tripAssumptionsDialog?.dismiss()
            tripAssumptionsDialog = null
            tripStorePickerDialog?.dismiss()
            tripStorePickerDialog = null
        }
        val searchVisible =
            state.route == AppRoute.SEARCH && state.selectedPrimaryTab == AppPrimaryTab.SEARCH
        if (!searchVisible && productMapDialog?.isShowing == true) {
            productMapDialog?.dismiss()
            productMapDialog = null
            pendingProductMapLocation = null
        }
        val enteringSearch = searchVisible && !searchRouteActive
        if (!searchVisible && searchRouteActive) {
            searchScrollY = shellScrollView.scrollY.coerceAtLeast(0)
        }
        searchRouteActive = searchVisible
        val basketVisible =
            state.selectedPrimaryTab == AppPrimaryTab.BASKET && state.route == AppRoute.BASKET
        val enteringBasket = basketVisible && !basketRouteActive
        if (!basketVisible && basketRouteActive) {
            basketScrollY = shellScrollView.scrollY.coerceAtLeast(0)
        }
        basketRouteActive = basketVisible
        val personalVisible =
            state.route == AppRoute.SAVED ||
                state.route == AppRoute.CONFIRMED_PURCHASE_HISTORY ||
                state.route == AppRoute.PRICE_WATCHES
        if (personalVisible && personalRouteActive) {
            personalScrollY = shellScrollView.scrollY.coerceAtLeast(0)
        } else if (!personalVisible && personalRouteActive) {
            personalScrollY = shellScrollView.scrollY.coerceAtLeast(0)
        }
        personalRouteActive = personalVisible
        val tripPlannerVisible = state.route == AppRoute.TRIP_PLANNER
        if (tripPlannerVisible && tripPlannerRouteActive) {
            tripPlannerScrollY = shellScrollView.scrollY.coerceAtLeast(0)
        } else if (!tripPlannerVisible && tripPlannerRouteActive) {
            tripPlannerScrollY = shellScrollView.scrollY.coerceAtLeast(0)
        }
        tripPlannerRouteActive = tripPlannerVisible
        youOverviewExperience.visibility = View.GONE
        if (state.route == AppRoute.COMPARE) {
            savedExperience.onRouteVisibilityChanged(false)
            return
        }

        val historyVisible = state.route == AppRoute.CONFIRMED_PURCHASE_HISTORY
        val priceWatchVisible = state.route == AppRoute.PRICE_WATCHES
        val copy =
            if (historyVisible) {
                ScreenCopy(
                    eyebrow = "BOUGHT",
                    title = "Bought",
                    body = "Purchases you explicitly confirmed on this device.",
                    footnote = "Not live pricing or store inventory.",
                    showCompareAction = false
                )
            } else if (priceWatchVisible) {
                ScreenCopy(
                    eyebrow = "WATCHING",
                    title = "Watching",
                    body = "Exact products you want to check again later.",
                    footnote = "No current offer is claimed unless authorized evidence exists.",
                    showCompareAction = false
                )
            } else if (tripPlannerVisible) {
                ScreenCopy(
                    eyebrow = "PRIVATE TRIP PLANNER",
                    title = "Plan this trip",
                    body = "Use the current Basket, a selected store and your own driving assumptions to estimate trip cost.",
                    footnote = "Manual estimate only. No road route, live offer, stock or savings recommendation is connected.",
                    showCompareAction = false
                )
            } else {
                copyFor(state.selectedPrimaryTab)
            }
        screenEyebrow.text = copy.eyebrow
        screenTitle.text = copy.title
        screenBody.text = copy.body
        screenFootnote.text = copy.footnote
        screenBackAction.visibility =
            if (state.canNavigateBack) View.VISIBLE else View.GONE
        screenBackAction.isEnabled = state.canNavigateBack
        screenBackAction.contentDescription =
            getString(R.string.shell_back_description)
        // Home has its own compact command center. Keeping the long route
        // header for secondary destinations avoids repeating the same copy
        // above the primary shopping workspace.
        screenHeaderCard.visibility =
            if (state.route == AppRoute.HOME || state.route == AppRoute.SEARCH || state.route == AppRoute.SAVED) {
                View.GONE
            } else {
                View.VISIBLE
            }
        // The brand promise belongs to the landing surface. Secondary routes use their
        // task title instead of repeating marketing copy above every workflow.
        shellTagline.visibility = if (state.route == AppRoute.HOME) View.VISIBLE else View.GONE
        sourcesLicencesButton.visibility =
            if (state.route == AppRoute.SAVED) View.VISIBLE else View.GONE
        dataStatusButton.visibility =
            if (state.route == AppRoute.SAVED) View.VISIBLE else View.GONE
        primaryAction.visibility = if (copy.showCompareAction) View.VISIBLE else View.GONE

        val homeVisible = state.selectedPrimaryTab == AppPrimaryTab.HOME
        homeExperience.visibility = if (homeVisible) View.VISIBLE else View.GONE
        if (homeVisible) renderHome()

        basketExperience.visibility = if (basketVisible) View.VISIBLE else View.GONE
        if (state.selectedPrimaryTab == AppPrimaryTab.BASKET) {
            renderHome()
            if (enteringBasket) restoreBasketScrollPosition()
        }

        searchExperience.visibility = if (searchVisible) View.VISIBLE else View.GONE

        if (searchVisible) {
            argentinaSearchSurfaceState?.let(::renderSearch)
            if (enteringSearch) restoreSearchScrollPosition()
        }

        val savedVisible = state.route == AppRoute.SAVED
        val observedPriceSelectionVisible =
            state.route == AppRoute.OBSERVED_PRICE_SAVED_SELECTION
        val observedPriceConfirmationDraftVisible =
            state.route == AppRoute.OBSERVED_PRICE_CONFIRMATION_DRAFT
        val stapleSetupVisible = state.route == AppRoute.STAPLE_WATCH_SETUP
        val staplePolicyVisible = state.route == AppRoute.STAPLE_WATCH_POLICY
        youOverviewExperience.visibility = if (savedVisible) View.VISIBLE else View.GONE
        if (savedVisible) renderYouOverview()
        savedExperience.visibility = if (savedVisible) View.VISIBLE else View.GONE
        confirmedPurchaseHistoryButton.visibility = View.GONE
        confirmedPurchaseHistoryExperience.visibility = if (historyVisible) View.VISIBLE else View.GONE
        priceWatchButton.visibility = View.GONE
        priceWatchExperience.visibility = if (priceWatchVisible) View.VISIBLE else View.GONE
        tripPlannerExperience.visibility = if (tripPlannerVisible) View.VISIBLE else View.GONE
        if (historyVisible) {
            confirmedPurchaseHistoryExperience.render(
                ConfirmedPurchaseHistoryProjector.project(
                    state = confirmedPurchaseHistoryState,
                    unavailable = confirmedPurchaseHistoryLoadIssue != null,
                    watchedProductIdentityKeys = watchedProductIdentityKeys()
                )
            )
        }
        if (priceWatchVisible) {
            priceWatchExperience.render(
                PriceWatchProjector.project(
                    state = priceWatchState,
                    purchases = confirmedPurchaseHistoryState.purchases,
                    unavailable = priceWatchLoadIssue != null
                )
            )
        }
        if (personalVisible) {
            restorePersonalScrollPosition()
        }
        if (tripPlannerVisible) {
            renderTripPlanner()
            restoreTripPlannerScrollPosition()
        }
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

    private fun renderArgentinaSearchState(state: ArgentinaLiveSearchSurfaceState) {
        val update = {
            val currentGeneration =
                if (::argentinaSearchHost.isInitialized) argentinaSearchHost.state().generation
                else state.generation
            argentinaSearchRenderGate.renderIfCurrent(state, currentGeneration) {
                argentinaSearchSurfaceState = it
                if (::searchStatus.isInitialized) renderSearch(it)
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) update() else mainHandler.post(update)
    }

    private fun renderSearch(state: ArgentinaLiveSearchSurfaceState) {
        searchAreaLabel.text = getString(R.string.search_area_label, state.areaLabel)
        searchStatus.text = state.statusMessage
        searchStatus.setTextColor(
            when (state.mode) {
                ArgentinaLiveSearchSurfaceMode.RESULTS -> ValuePilotPalette.color("#374151")
                ArgentinaLiveSearchSurfaceMode.AUTHORIZATION_UNAVAILABLE,
                ArgentinaLiveSearchSurfaceMode.TIMEOUT,
                ArgentinaLiveSearchSurfaceMode.TEMPORARILY_UNAVAILABLE -> ValuePilotPalette.color("#B42318")
                ArgentinaLiveSearchSurfaceMode.STALE_RELEASE -> ValuePilotPalette.color("#92400E")
                else -> ValuePilotPalette.color("#6B7280")
            }
        )
        searchProgress.visibility =
            if (state.mode == ArgentinaLiveSearchSurfaceMode.LOADING) View.VISIBLE else View.GONE

        searchButton.setText(
            when {
                state.requiresRevalidation -> R.string.search_again
                state.mode == ArgentinaLiveSearchSurfaceMode.STALE_RELEASE ||
                    state.mode == ArgentinaLiveSearchSurfaceMode.TIMEOUT ||
                    state.mode == ArgentinaLiveSearchSurfaceMode.TEMPORARILY_UNAVAILABLE ||
                    state.mode == ArgentinaLiveSearchSurfaceMode.AUTHORIZATION_UNAVAILABLE ->
                    R.string.search_again
                else -> R.string.search_action
            }
        )

        searchButton.isEnabled =
            state.mode != ArgentinaLiveSearchSurfaceMode.LOADING &&
                searchQuery.trim().isNotBlank() &&
                searchQuery.length <= ArgentinaBackendContract.MAX_QUERY_CHARS
        quickSearchEntries().forEach { (chipId, _) ->
            findViewById<Chip>(chipId).isEnabled =
                state.mode != ArgentinaLiveSearchSurfaceMode.LOADING
        }

        val presentation = state.presentation
        val matches = presentation?.matches
        if (matches != renderedSearchMatches) {
            val preservedScrollY =
                if (::shellScrollView.isInitialized) shellScrollView.scrollY.coerceAtLeast(0) else 0
            searchResultsContainer.removeAllViews()
            matches?.forEach { match ->
                searchResultsContainer.addView(
                    createSearchResultView(
                        match = match,
                        onAddToHome = ::addSearchIdentityToHome,
                        onToggleHome = ::toggleSearchIdentityInHome,
                        onOpenPhotos = ::openGoogleProductPhotos,
                        onOpenMap = ::openProductStoreMap,
                        onOpenHistory = ::openSearchProductHistory,
                        watched = match.identityKey in watchedProductIdentityKeys(),
                        onTogglePriceWatch = ::handleTogglePriceWatch
                    )
                )
            }
            renderedSearchMatches = matches
            if (::shellScrollView.isInitialized) {
                shellScrollView.post {
                    if (searchRouteActive) shellScrollView.scrollTo(0, preservedScrollY)
                }
            }
        }
        searchResultsHeading.visibility =
            if (presentation != null && presentation.matches.isNotEmpty()
            ) View.VISIBLE else View.GONE
        searchResultsCount.text =
            presentation?.matches?.size?.takeIf { it > 0 }?.let { count ->
                resources.getQuantityString(R.plurals.search_results_count, count, count)
            }.orEmpty()
        searchResultsCount.visibility =
            if (presentation != null && presentation.matches.isNotEmpty()) View.VISIBLE else View.GONE

        val canLoadMore =
            state.mode == ArgentinaLiveSearchSurfaceMode.RESULTS &&
                presentation?.hasMore == true && presentation.nextPage != null
        searchLoadMoreButton.visibility = if (canLoadMore) View.VISIBLE else View.GONE
        searchLoadMoreButton.isEnabled = state.mode == ArgentinaLiveSearchSurfaceMode.RESULTS
        searchResultsEnd.visibility =
            if (state.mode == ArgentinaLiveSearchSurfaceMode.RESULTS &&
                presentation != null && presentation.matches.isNotEmpty() && !canLoadMore
            ) View.VISIBLE
            else View.GONE
        searchResultsEnd.text =
            when {
                presentation?.limitReached == true -> getString(R.string.search_results_limit_reached)
                presentation != null && presentation.matches.isNotEmpty() -> getString(R.string.search_results_end)
                else -> ""
            }
    }

    /** Reads only the bounded device-private confirmed-purchase document. */
    private fun refreshConfirmedPurchaseHistory() {
        val loaded = confirmedPurchaseStore.load()
        confirmedPurchaseHistoryState = loaded.state ?: ConfirmedPurchaseHistoryState.empty()
        confirmedPurchaseHistoryLoadIssue = loaded.issue
        if (::youOverviewExperience.isInitialized && shellState.route == AppRoute.SAVED) {
            renderYouOverview()
        }
    }

    private fun refreshPriceWatchState() {
        val loaded = priceWatchStore.load()
        val nextState = loaded.state ?: PriceWatchState.empty()
        if (nextState == priceWatchState && loaded.issue == priceWatchLoadIssue) return
        priceWatchState = nextState
        priceWatchLoadIssue = loaded.issue
        if (::youOverviewExperience.isInitialized && shellState.route == AppRoute.SAVED) {
            renderYouOverview()
        }
        renderedSearchMatches = null
        if (::homeExperience.isInitialized && shellState.route != AppRoute.COMPARE) {
            renderHome()
        }
        if (::priceWatchExperience.isInitialized && shellState.route == AppRoute.PRICE_WATCHES) {
            priceWatchExperience.render(
                PriceWatchProjector.project(
                    state = priceWatchState,
                    purchases = confirmedPurchaseHistoryState.purchases,
                    unavailable = priceWatchLoadIssue != null
                )
            )
        }
    }

    private fun renderYouOverview() {
        youOverviewExperience.render(
            PracticalShoppingYouOverviewState(
                boughtCount = confirmedPurchaseHistoryState.purchases.size,
                savedCount = savedProductCount,
                watchingCount = priceWatchState.watches.size
            )
        )
    }

    private fun refreshTripPlannerState() {
        val loaded = tripPlannerStore.load()
        tripPlannerState = loaded.state ?: TripPlannerState()
        tripPlannerLoadIssue = loaded.issue
        if (::tripPlannerExperience.isInitialized && shellState.route == AppRoute.TRIP_PLANNER) {
            renderTripPlanner()
        }
    }

    private fun currentTripBasketSnapshot(): TripPlannerBasketSnapshot {
        val rendered = homeRenderState
        val plannedItemKeys = rendered?.items?.map { it.key.value }
            ?: homeSessionState.model.ui.items.map { it.key.value }
        val unknownCount = rendered?.unknownItems?.size ?: homeSessionState.model.ui.unknownItems.size
        val privateKeys = homeSessionState.privateIdentities.map { it.identityKey }
        val revisionMaterial =
            (plannedItemKeys + privateKeys + homeSessionState.model.ui.query + unknownCount.toString())
                .joinToString("\u001f")
        return TripPlannerBasketSnapshot(
            plannedItemCount = plannedItemKeys.size,
            privateIdentityCount = privateKeys.size,
            unknownItemCount = unknownCount,
            revision = revisionMaterial.ifBlank { "empty" }
        )
    }

    private fun renderTripPlanner() {
        if (!::tripPlannerExperience.isInitialized) return
        val basket = currentTripBasketSnapshot()
        val decision =
            ShoppingDecisionTripPlannerBridge.evaluate(
                home = homeSessionState,
                trip = tripPlannerState,
                purchases = confirmedPurchaseHistoryState.purchases,
                priceWatchState = priceWatchState
            )
        tripPlannerExperience.render(
            TripPlannerProjector.project(
                state = tripPlannerState,
                basket = basket,
                directoryAvailable = false,
                unavailable = tripPlannerLoadIssue != null,
                decision = decision
            ),
            preferred = tripPlannerState.preferredStoreKey != null &&
                tripPlannerState.preferredStoreKey == tripPlannerState.destination?.storeKey
        )
    }

    private fun persistTripPlannerState(next: TripPlannerState): Boolean {
        val result = tripPlannerStore.replace(next)
        if (!result.accepted) {
            Toast.makeText(this, "Trip data could not be saved; existing data was preserved.", Toast.LENGTH_SHORT).show()
            refreshTripPlannerState()
            return false
        }
        tripPlannerState = requireNotNull(result.state)
        tripPlannerLoadIssue = null
        renderTripPlanner()
        return true
    }

    private fun handleTripPlannerAction(action: TripPlannerUiAction) {
        when (action) {
            TripPlannerUiAction.ChooseDestinationStore -> openTripStorePicker()
            TripPlannerUiAction.SetManualOrigin -> openTripManualOriginDialog()
            TripPlannerUiAction.UseForegroundLocation -> useForegroundLocation()
            TripPlannerUiAction.EditTripAssumptions -> openTripAssumptionsDialog()
            TripPlannerUiAction.Calculate -> renderTripPlanner()
            TripPlannerUiAction.OpenDirections -> openTripExternalDirections()
            TripPlannerUiAction.TogglePreferredStore -> {
                val destination = tripPlannerState.destination ?: return
                val preferred =
                    if (tripPlannerState.preferredStoreKey == destination.storeKey) null
                    else destination.storeKey
                persistTripPlannerState(tripPlannerState.copy(preferredStoreKey = preferred))
            }
            TripPlannerUiAction.Clear -> {
                val result = tripPlannerStore.clear()
                if (!result.accepted) {
                    Toast.makeText(this, "Trip data could not be cleared; existing data was preserved.", Toast.LENGTH_SHORT).show()
                    return
                }
                tripPlannerState = TripPlannerState()
                tripPlannerLoadIssue = null
                renderTripPlanner()
            }
        }
    }

    private fun preferredTripStoreSelection(): TripStoreSelection? {
        val destination = tripPlannerState.destination ?: return null
        return destination.takeIf { tripPlannerState.preferredStoreKey == it.storeKey }
    }

    private fun openTripStorePicker() {
        tripStorePickerDialog?.dismiss()
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(4), dp(4), 0)
        }
        val manualLayout = TextInputLayout(this).apply {
            hint = "Private store name (optional)"
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) }
        }
        val manualInput = TextInputEditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setSingleLine(true)
        }
        manualLayout.addView(manualInput)
        body.addView(TextView(this).apply {
            text = "Enter a private store name. A name does not confirm price, stock or availability."
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(ValuePilotPalette.color("#6B7280"))
        })
        body.addView(manualLayout)
        val dialog = AlertDialog.Builder(this)
            .setTitle("Choose destination store")
            .setView(body)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton("Use private name", null)
            .create()
        tripStorePickerDialog = dialog
        dialog.setOnDismissListener { if (tripStorePickerDialog === dialog) tripStorePickerDialog = null }
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val selection = TripStoreSelectionAdapter.privateEntry(manualInput.text?.toString().orEmpty())
                if (selection == null) {
                    manualLayout.error = "Enter a private store name"
                    return@setOnClickListener
                }
                manualLayout.error = null
                persistTripPlannerState(
                    tripPlannerState.copy(
                        destination = selection,
                        preferredStoreKey = tripPlannerState.preferredStoreKey?.takeIf { it == selection.storeKey }
                    )
                )
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun openTripManualOriginDialog() {
        tripOriginDialog?.dismiss()
        val input = TextInputEditText(this).apply {
            hint = "Starting point or area"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setSingleLine(true)
            setText(tripPlannerState.origin?.takeIf { it.kind == TripOriginKind.MANUAL }?.label.orEmpty())
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("Starting point")
            .setMessage("This label is private context only. ValuePilot does not geocode it or infer a driving route.")
            .setView(input)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton("Save", null)
            .create()
        tripOriginDialog = dialog
        dialog.setOnDismissListener { if (tripOriginDialog === dialog) tripOriginDialog = null }
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val label = TripPlannerOriginParser.manual(input.text?.toString().orEmpty())
                if (label == null) {
                    input.error = "Enter a short starting point"
                    return@setOnClickListener
                }
                persistTripPlannerState(
                    tripPlannerState.copy(
                        origin = TripOriginReference(TripOriginKind.MANUAL, label)
                    )
                )
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun useForegroundLocation() {
        val fineGranted = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!TripPlannerLocationPolicy.hasForegroundPermission(fineGranted, coarseGranted)) {
            requestPermissions(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                FOREGROUND_LOCATION_REQUEST
            )
            return
        }
        captureForegroundLocation()
    }

    private fun captureForegroundLocation() {
        val fineGranted = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!TripPlannerLocationPolicy.hasForegroundPermission(fineGranted, coarseGranted)) {
            Toast.makeText(this, "Location was not granted; manual starting point remains available.", Toast.LENGTH_SHORT).show()
            return
        }
        val manager = getSystemService(LOCATION_SERVICE) as? LocationManager
        val location =
            manager?.let {
                try {
                    it.getProviders(true).asSequence()
                        .mapNotNull { provider -> it.getLastKnownLocation(provider) }
                        .maxByOrNull { candidate -> candidate.time }
                } catch (_: SecurityException) {
                    null
                }
            }
        if (location == null) {
            Toast.makeText(this, "No recent foreground location is available; enter a starting point manually.", Toast.LENGTH_LONG).show()
            return
        }
        persistTripPlannerState(
            tripPlannerState.copy(
                origin = TripOriginReference(
                    kind = TripOriginKind.FOREGROUND_LOCATION,
                    label = "Current foreground location",
                    contextId = "foreground-${UUID.randomUUID()}"
                )
            )
        )
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PRODUCT_MAP_FOREGROUND_LOCATION_REQUEST) {
            val pending = pendingProductMapLocation
            pendingProductMapLocation = null
            if (grantResults.any { it == PackageManager.PERMISSION_GRANTED }) {
                pending?.invoke()
            } else {
                Toast.makeText(this, "Location not used; showing the CABA search area.", Toast.LENGTH_SHORT).show()
            }
        } else if (requestCode == FOREGROUND_LOCATION_REQUEST && grantResults.any { it == PackageManager.PERMISSION_GRANTED }) {
            captureForegroundLocation()
        } else if (requestCode == FOREGROUND_LOCATION_REQUEST) {
            Toast.makeText(this, "Location was not granted; manual starting point remains available.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openTripAssumptionsDialog() {
        tripAssumptionsDialog?.dismiss()
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(4), dp(4), 0)
        }
        val basisGroup = android.widget.RadioGroup(this).apply { orientation = android.widget.RadioGroup.VERTICAL }
        val roundTrip = android.widget.RadioButton(this).apply { id = View.generateViewId(); text = "Round-trip distance (entered one way)" }
        val additional = android.widget.RadioButton(this).apply { id = View.generateViewId(); text = "Additional driving distance (already total)" }
        basisGroup.addView(roundTrip); basisGroup.addView(additional)
        val distance = tripTripInput("Driving distance (km, max 3 decimals)", tripPlannerState.drivingDistanceMetres?.let { BigDecimal.valueOf(it).movePointLeft(3).stripTrailingZeros().toPlainString() }.orEmpty())
        val consumption = tripTripInput("Fuel consumption (ml per 100 km)", tripPlannerState.fuelConsumptionMilliLitresPer100Km?.toString().orEmpty())
        val fuel = tripTripInput("Fuel price per litre", tripPlannerState.fuelPrice?.let { formatTripMoney(it) }.orEmpty())
        // Argentina is the active launch context. This is only the empty-form hint;
        // a persisted or explicitly entered personal currency is preserved unchanged.
        val currency = tripTripInput("Currency code", tripPlannerState.fuelPrice?.currencyCode ?: "ARS")
        val toll = tripTripInput("Tolls (optional)", tripPlannerState.tolls?.let { formatTripMoney(it) }.orEmpty())
        val parking = tripTripInput("Parking (optional)", tripPlannerState.parking?.let { formatTripMoney(it) }.orEmpty())
        body.addView(TextView(this).apply {
            text = "All values are your entered assumptions. ValuePilot does not turn them into a verified road route."
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(ValuePilotPalette.color("#6B7280"))
        })
        body.addView(basisGroup)
        body.addView(distance.first); body.addView(consumption.first); body.addView(fuel.first)
        body.addView(currency.first); body.addView(toll.first); body.addView(parking.first)
        when (tripPlannerState.distanceBasis) {
            TripDistanceBasis.ADDITIONAL_DRIVING -> additional.isChecked = true
            else -> roundTrip.isChecked = true
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("Driving assumptions")
            .setView(body)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton("Save", null)
            .create()
        tripAssumptionsDialog = dialog
        dialog.setOnDismissListener { if (tripAssumptionsDialog === dialog) tripAssumptionsDialog = null }
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val basis = if (additional.isChecked) TripDistanceBasis.ADDITIONAL_DRIVING else TripDistanceBasis.ROUND_TRIP
                val parsed = TripPlannerInputParser.parse(
                    TripPlannerAssumptionInput(
                        distanceKmText = distance.second.text?.toString().orEmpty(),
                        distanceBasis = basis,
                        consumptionText = consumption.second.text?.toString().orEmpty(),
                        fuelPriceText = fuel.second.text?.toString().orEmpty(),
                        currencyText = currency.second.text?.toString().orEmpty(),
                        tollText = toll.second.text?.toString().orEmpty(),
                        parkingText = parking.second.text?.toString().orEmpty()
                    )
                )
                if (!parsed.accepted) {
                    val failure = requireNotNull(parsed.failure)
                    val target = when (failure.field) {
                        TripPlannerAssumptionField.DISTANCE -> distance.first
                        TripPlannerAssumptionField.CONSUMPTION -> consumption.first
                        TripPlannerAssumptionField.FUEL_PRICE -> fuel.first
                        TripPlannerAssumptionField.CURRENCY -> currency.first
                        TripPlannerAssumptionField.TOLLS -> toll.first
                        TripPlannerAssumptionField.PARKING -> parking.first
                    }
                    target.error = failure.message
                    return@setOnClickListener
                }
                val input = requireNotNull(parsed.value)
                persistTripPlannerState(
                    tripPlannerState.copy(
                        drivingDistanceMetres = input.drivingDistanceMetres,
                        distanceBasis = input.distanceBasis,
                        fuelConsumptionMilliLitresPer100Km = input.fuelConsumptionMilliLitresPer100Km,
                        fuelPrice = input.fuelPricePerLitre,
                        tolls = input.tolls,
                        parking = input.parking
                    )
                )
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun tripTripInput(hint: String, value: String): Pair<TextInputLayout, TextInputEditText> {
        val layout = TextInputLayout(this).apply {
            this.hint = hint
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) }
        }
        val input = TextInputEditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setSingleLine(true)
            setText(value)
        }
        layout.addView(input)
        return layout to input
    }

    private fun formatTripMoney(money: com.valuepilot.core.Money): String =
        BigDecimal.valueOf(money.minorUnits).movePointLeft(money.fractionDigits).toPlainString()

    private fun openTripExternalDirections() {
        val destination = tripPlannerState.destination ?: return
        val latitude = destination.latitudeE7 ?: return
        val longitude = destination.longitudeE7 ?: return
        val lat = BigDecimal.valueOf(latitude.toLong()).movePointLeft(7).toPlainString()
        val lon = BigDecimal.valueOf(longitude.toLong()).movePointLeft(7).toPlainString()
        val uri = Uri.parse("geo:$lat,$lon?q=$lat,$lon(${Uri.encode(destination.displayName)})")
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "No directions app is available. Enter a manual distance instead.", Toast.LENGTH_LONG).show()
        }
    }

    private fun watchedProductIdentityKeys(): Set<String> =
        priceWatchState.watches.map { it.productIdentityKey }.toSet()

    private fun restoreSearchScrollPosition() {
        val targetY = searchScrollY.coerceAtLeast(0)
        shellScrollView.post {
            if (searchRouteActive) shellScrollView.scrollTo(0, targetY)
        }
    }

    private fun restoreBasketScrollPosition() {
        val targetY = basketScrollY.coerceAtLeast(0)
        shellScrollView.post {
            if (basketRouteActive) shellScrollView.scrollTo(0, targetY)
        }
    }

    private fun restorePersonalScrollPosition() {
        val targetY = personalScrollY.coerceAtLeast(0)
        shellScrollView.post {
            if (personalRouteActive) shellScrollView.scrollTo(0, targetY)
        }
    }

    private fun restoreTripPlannerScrollPosition() {
        val targetY = tripPlannerScrollY.coerceAtLeast(0)
        shellScrollView.post {
            if (tripPlannerRouteActive) shellScrollView.scrollTo(0, targetY)
        }
    }

    private fun createSearchResultView(
        match: PracticalShoppingSearchIdentityPresentation.Match,
        onAddToHome: (PracticalShoppingSearchIdentityPresentation.Match) -> Unit,
        onToggleHome: (PracticalShoppingSearchIdentityPresentation.Match) -> Unit,
        onOpenPhotos: (PracticalShoppingSearchIdentityPresentation.Match) -> Unit,
        onOpenMap: (PracticalShoppingSearchIdentityPresentation.Match) -> Unit,
        onOpenHistory: (PracticalShoppingSearchIdentityPresentation.Match) -> Unit,
        watched: Boolean,
        onTogglePriceWatch: (PriceWatchUiAction.ToggleWatch) -> Unit
    ): View {
        val mapEligible = ArgentinaProductStoreMapAdapter.canQuery(match)
        val historyAvailable =
            confirmedPurchaseHistoryState.purchases.any { purchase ->
                purchase.productIdentityKey == match.identityKey
            }
        val homeSaved = homeSessionState.privateIdentities.any { identity ->
            identity.identityKey == match.identityKey
        }
        val cardPresentation = SearchResultCardPresentationPolicy.project(
            match = match,
            mapEligible = mapEligible,
            historyAvailable = historyAvailable,
            saved = homeSaved,
            watched = watched
        )
        val privateIdentity = PracticalShoppingPrivateIdentity.fromSearchMatch(match)
        val card = ValuePilotProductCard(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(12) }
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            isClickable = false
            isFocusable = false
            contentDescription =
                listOfNotNull(
                    match.displayName,
                    match.brand,
                    match.packageQuantityLabel?.let {
                        getString(R.string.search_identity_package_size, it)
                    },
                    match.matchLabel,
                    getString(R.string.search_identity_no_current_price),
                    if (cardPresentation.primaryAction == SearchResultPrimaryAction.REMOVE_FROM_BASKET) {
                        getString(R.string.search_identity_remove_home_draft)
                    } else if (cardPresentation.primaryAction == SearchResultPrimaryAction.ADD_TO_BASKET) {
                        getString(R.string.search_identity_add_home_draft)
                    } else {
                        null
                    },
                    "More actions"
                ).joinToString(". ") { it.trim().trimEnd('.', '!', '?') } + "."
        }

        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(15), dp(16), dp(15))
            // Keep the projected text as one card-level announcement. The explicit
            // Home action is added as a sibling below this decorative summary so it
            // remains available to keyboard and assistive-technology users.
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }

        val name = TextView(this).apply {
            text = match.displayName
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTextColor(ValuePilotPalette.color("#111827"))
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            setPadding(0, dp(5), 0, 0)
        }

        val brand = match.brand?.let { brandName ->
            TextView(this).apply {
                text = brandName
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setTextColor(ValuePilotPalette.color("#374151"))
                setPadding(0, dp(7), 0, 0)
            }
        }

        val packageQuantity = match.packageQuantityLabel?.let { quantityLabel ->
            TextView(this).apply {
                text = getString(R.string.search_identity_package_size, quantityLabel)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setTextColor(ValuePilotPalette.color("#374151"))
                setPadding(0, dp(7), 0, 0)
            }
        }

        val matchLabel = TextView(this).apply {
            text = match.matchLabel
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(ValuePilotPalette.color("#6B7280"))
            setPadding(0, dp(8), 0, 0)
        }

        val priceState = TextView(this).apply {
            text = getString(R.string.search_identity_no_current_price)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(ValuePilotPalette.color("#92400E"))
            setPadding(0, dp(8), 0, 0)
        }

        body.addView(name)
        brand?.let(body::addView)
        packageQuantity?.let(body::addView)
        body.addView(matchLabel)
        body.addView(priceState)
        val addHomeButton = MaterialButton(this).apply {
            text = getString(
                if (cardPresentation.primaryAction == SearchResultPrimaryAction.REMOVE_FROM_BASKET) {
                    R.string.search_identity_remove_home_draft
                } else {
                    R.string.search_identity_add_home_draft
                }
            )
            isAllCaps = false
            minHeight = dp(48)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(dp(16), dp(6), dp(16), dp(12))
            }
            contentDescription = getString(
                if (homeSaved) {
                    R.string.search_identity_remove_home_draft_description
                } else {
                    R.string.search_identity_add_home_draft_description
                },
                match.displayName
            )
            visibility =
                if (cardPresentation.primaryAction == SearchResultPrimaryAction.NONE) {
                    View.GONE
                } else {
                    View.VISIBLE
                }
            setOnClickListener {
                if (homeSaved) onToggleHome(match) else onAddToHome(match)
            }
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        }
        val contextActions = ValuePilotProductContextMenu(this).apply {
            setActions(
                buildList {
                    if (cardPresentation.showPhotos) {
                        val identity = requireNotNull(privateIdentity)
                        add(
                            ValuePilotProductContextAction(
                                kind = ValuePilotProductContextActionKind.PHOTO,
                                label = getString(R.string.search_identity_view_photos),
                                contentDescription = getString(
                                    R.string.search_identity_view_photos_description,
                                    identity.displayName
                                )
                            )
                        )
                    }
                    add(
                        ValuePilotProductContextAction(
                            kind = ValuePilotProductContextActionKind.WATCH,
                            label = getString(
                                if (cardPresentation.watching) {
                                    R.string.search_identity_watching
                                } else {
                                    R.string.search_identity_watch
                                }
                            ),
                            contentDescription =
                                if (watched) {
                                    "Stop watching ${match.displayName}; private history is retained"
                                } else {
                                    "Watch exact product ${match.displayName} privately"
                                }
                        )
                    )
                    if (cardPresentation.showNearby) {
                        add(
                            ValuePilotProductContextAction(
                                kind = ValuePilotProductContextActionKind.MAP,
                                label = getString(R.string.search_identity_view_map),
                                contentDescription = getString(
                                    R.string.search_identity_view_map_description,
                                    match.displayName
                                )
                            )
                        )
                    }
                    if (cardPresentation.showHistory) {
                        add(
                            ValuePilotProductContextAction(
                                kind = ValuePilotProductContextActionKind.HISTORY,
                                label = getString(R.string.search_identity_view_history),
                                contentDescription = getString(
                                    R.string.search_identity_view_history_description,
                                    match.displayName
                                )
                            )
                        )
                    }
                }
            )
            onAction = { action ->
                when (action.kind) {
                    ValuePilotProductContextActionKind.PHOTO -> onOpenPhotos(match)
                    ValuePilotProductContextActionKind.WATCH ->
                        onTogglePriceWatch(
                            PriceWatchUiAction.ToggleWatch(
                                productIdentityKey = match.identityKey,
                                displayName = match.displayName,
                                brand = match.brand,
                                packageQuantityLabel = match.packageQuantityLabel,
                                watched = watched
                            )
                        )
                    ValuePilotProductContextActionKind.MAP -> onOpenMap(match)
                    ValuePilotProductContextActionKind.HISTORY -> onOpenHistory(match)
                }
            }
        }
        content.addView(body)
        // Adding/removing is the one obvious product action. Everything else is
        // progressive disclosure inside the shared More control.
        content.addView(addHomeButton)
        content.addView(contextActions)
        card.addView(content)
        return card
    }

    private fun openSearchProductHistory(
        match: PracticalShoppingSearchIdentityPresentation.Match
    ) {
        // History entry is exact-key gated; Search never fuzzy-matches a
        // purchase or creates a new history record as a side effect.
        if (confirmedPurchaseHistoryState.purchases.none { purchase ->
                purchase.productIdentityKey == match.identityKey
            }
        ) {
            return
        }
        dispatch(AppShellIntent.SelectPrimary(AppPrimaryTab.SAVED))
        dispatch(AppShellIntent.OpenConfirmedPurchaseHistory)
    }

    private fun toggleSearchIdentityInHome(
        match: PracticalShoppingSearchIdentityPresentation.Match
    ) {
        val previousState = homeSessionState
        val nextState =
            if (previousState.privateIdentities.any { identity ->
                    identity.identityKey == match.identityKey
                }
            ) {
                PracticalShoppingHomeSession.removePrivateIdentity(
                    previousState,
                    match.identityKey
                )
            } else {
                PracticalShoppingPrivateIdentity.fromSearchMatch(match)?.let { identity ->
                    PracticalShoppingHomeSession.addPrivateIdentity(previousState, identity)
                } ?: previousState
            }
        if (nextState == previousState) {
            searchStatus.text = getString(R.string.search_identity_add_home_failed)
            return
        }
        homeSessionState = nextState
        authorizePrivateListPersistence()
        renderHome()
        renderedSearchMatches = null
        argentinaSearchSurfaceState?.let(::renderSearch)
    }

    private fun openProductStoreMap(
        match: PracticalShoppingSearchIdentityPresentation.Match,
        cachedResponse: ArgentinaBackendContract.ShopResponse? = null,
        origin: ProductStoreMapOrigin = ProductStoreMapOrigin.CABA_FALLBACK,
        originCoordinate: ProductStoreMapCoordinate? = null,
        selectedStoreKey: String? = null
    ) {
        productMapSelectedKey = match.identityKey
        productMapSelectedLabel = match.displayName
        val fallbackCoordinate = ProductStoreMapCoordinate.parse(
            argentinaLaunchArea.latitude,
            argentinaLaunchArea.longitude
        )
        val mapContext =
            ProductMapContext(
                match = match,
                response = cachedResponse,
                origin = origin,
                originCoordinate = originCoordinate ?: fallbackCoordinate,
                selectedStoreKey = selectedStoreKey
            )
        productMapContext = mapContext
        val surface = ProductStoreMapSurfaceView(this)
        surface.showLoading(match.displayName, match.packageQuantityLabel)
        surface.onClose = {
            productMapDialog?.dismiss()
            productMapDialog = null
            pendingProductMapLocation = null
            reopenProductMapAfterTrip = false
        }
        surface.onTripCost = ::selectProductMapStore
        surface.onDirections = ::openProductMapDirections
        surface.onUseForegroundLocation = {
            useProductMapForegroundLocation(surface, match) { mapContext.response }
        }
        productMapDialog?.dismiss()
        productMapDialog = AlertDialog.Builder(this)
            .setView(surface)
            .create()
            .also { dialog ->
                dialog.setOnDismissListener {
                    if (productMapDialog === dialog) productMapDialog = null
                    pendingProductMapLocation = null
                }
                dialog.show()
            }

        if (cachedResponse != null) {
            renderProductMapResponse(surface, mapContext)
            return
        }

        val requestId = Math.addExact(productMapRequestId, 1L)
        productMapRequestId = requestId
        searchExecutor.submit {
            val outcome = runCatching {
                val status =
                    ArgentinaBackendConsumerBridge(
                        ValuePilotArgentinaEdgeTransportFactory.create(),
                        null
                    ).status()
                val statusValue = (status as? ArgentinaBackendBridgeResult.Success)?.value
                    ?: throw IllegalStateException("The qualified Argentina release is unavailable.")
                val release = ArgentinaBackendContract.ExpectedRelease(
                    releaseId = statusValue.releaseId
                        ?: throw IllegalStateException("The qualified Argentina release has no id."),
                    releaseDate = statusValue.releaseDate
                        ?: throw IllegalStateException("The qualified Argentina release has no date.")
                )
                val quantity = ArgentinaProductStoreMapAdapter.explicitPackageQuantity(match.packageQuantityLabel)
                    ?: throw IllegalStateException("This exact identity has no structured package quantity; ValuePilot will not infer one for map lookup.")
                val request = ArgentinaBackendContract.ShopRequest(
                    latitude = argentinaLaunchArea.latitude,
                    longitude = argentinaLaunchArea.longitude,
                    radiusKm = argentinaLaunchArea.radiusKm,
                    items = listOf(
                        ArgentinaBackendContract.RequestItem(
                            lineId = ArgentinaProductStoreMapAdapter.deterministicLineId(match.identityKey),
                            query = match.displayName,
                            amount = quantity.first,
                            unit = quantity.second,
                            productEvidenceKey = match.identityKey
                        )
                    )
                )
                ArgentinaBackendConsumerBridge(
                    ValuePilotArgentinaEdgeTransportFactory.create(),
                    release
                ).shop(request)
            }
            mainHandler.post {
                if (requestId != productMapRequestId || isFinishing || isDestroyed) return@post
                if (productMapDialog == null || !productMapDialog!!.isShowing) return@post
                when (val result = outcome.getOrNull()) {
                    is ArgentinaBackendBridgeResult.Success -> {
                        mapContext.response = result.value
                        renderProductMapResponse(surface, mapContext)
                    }

                    else -> surface.showFailure(match.displayName, match.packageQuantityLabel)
                }
            }
        }
    }

    private fun renderProductMapResponse(
        surface: ProductStoreMapSurfaceView,
        mapContext: ProductMapContext
    ) {
        val response = mapContext.response ?: return
        val mapResult = ArgentinaProductStoreMapAdapter.query(
            response,
            mapContext.match,
            mapContext.originCoordinate
        )
        surface.render(
            ProductStoreMapProjector.project(
                productName = mapContext.match.displayName,
                packageQuantityLabel = mapContext.match.packageQuantityLabel,
                result = mapResult,
                origin = mapContext.origin
            )
        )
        surface.restoreSelectedStore(mapContext.selectedStoreKey)
    }

    private fun reopenProductMap() {
        val mapContext = productMapContext ?: return
        openProductStoreMap(
            match = mapContext.match,
            cachedResponse = mapContext.response,
            origin = mapContext.origin,
            originCoordinate = mapContext.originCoordinate,
            selectedStoreKey = mapContext.selectedStoreKey
        )
    }

    private fun useProductMapForegroundLocation(
        surface: ProductStoreMapSurfaceView,
        match: PracticalShoppingSearchIdentityPresentation.Match,
        responseProvider: () -> ArgentinaBackendContract.ShopResponse?
    ) {
        if (productMapDialog == null || !productMapDialog!!.isShowing || isFinishing || isDestroyed) return
        val fineGranted = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val capture = {
            captureProductMapForegroundLocation(surface, match, responseProvider)
        }
        if (!TripPlannerLocationPolicy.hasForegroundPermission(fineGranted, coarseGranted)) {
            pendingProductMapLocation = capture
            requestPermissions(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                PRODUCT_MAP_FOREGROUND_LOCATION_REQUEST
            )
            return
        }
        capture()
    }

    private fun captureProductMapForegroundLocation(
        surface: ProductStoreMapSurfaceView,
        match: PracticalShoppingSearchIdentityPresentation.Match,
        responseProvider: () -> ArgentinaBackendContract.ShopResponse?
    ) {
        if (productMapDialog == null || !productMapDialog!!.isShowing || isFinishing || isDestroyed) return
        val fineGranted = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!TripPlannerLocationPolicy.hasForegroundPermission(fineGranted, coarseGranted)) {
            Toast.makeText(this, "Location not used; showing the CABA search area.", Toast.LENGTH_SHORT).show()
            return
        }
        val manager = getSystemService(LOCATION_SERVICE) as? LocationManager
        val location = manager?.let {
            try {
                it.getProviders(true).asSequence()
                    .mapNotNull { provider -> it.getLastKnownLocation(provider) }
                    .maxByOrNull { candidate -> candidate.time }
            } catch (_: SecurityException) {
                null
            }
        }
        val response = responseProvider()
        val coordinate = location?.let {
            ProductStoreMapCoordinate.parse(it.latitude.toString(), it.longitude.toString())
        }
        if (response == null || coordinate == null) {
            Toast.makeText(this, "Location not available; showing the CABA search area.", Toast.LENGTH_LONG).show()
            return
        }
        productMapContext?.origin = ProductStoreMapOrigin.ONE_SHOT_LOCATION
        productMapContext?.originCoordinate = coordinate
        val result = ArgentinaProductStoreMapAdapter.query(response, match, coordinate)
        productMapContext?.let { context ->
            context.response = response
            surface.render(
                ProductStoreMapProjector.project(
                    productName = match.displayName,
                    packageQuantityLabel = match.packageQuantityLabel,
                    result = result,
                    origin = ProductStoreMapOrigin.ONE_SHOT_LOCATION
                )
            )
        }
    }

    private fun selectProductMapStore(pin: com.valuepilot.core.ProductStoreMapPin) {
        productMapContext?.selectedStoreKey = pin.storeKey.value
        val selection =
            TripStoreSelection(
                storeKey = pin.storeKey,
                displayName = pin.storeName,
                addressLabel = pin.addressLabel,
                sourceLabel = pin.sourceLabel,
                latitudeE7 = pin.coordinate.latitudeE7,
                longitudeE7 = pin.coordinate.longitudeE7
            )
        if (!persistTripPlannerState(tripPlannerState.copy(destination = selection, preferredStoreKey = null))) {
            Toast.makeText(this, "That store could not be saved; the map remains unchanged.", Toast.LENGTH_SHORT).show()
            return
        }
        reopenProductMapAfterTrip = true
        productMapDialog?.dismiss()
        productMapDialog = null
        pendingProductMapLocation = null
        dispatch(AppShellIntent.SelectPrimary(AppPrimaryTab.BASKET))
        dispatch(AppShellIntent.OpenTripPlanner)
    }

    private fun openProductMapDirections(pin: com.valuepilot.core.ProductStoreMapPin) {
        val latitude = BigDecimal.valueOf(pin.coordinate.latitudeE7.toLong())
            .movePointLeft(7)
            .toPlainString()
        val longitude = BigDecimal.valueOf(pin.coordinate.longitudeE7.toLong())
            .movePointLeft(7)
            .toPlainString()
        val uri = Uri.parse(
            "geo:$latitude,$longitude?q=$latitude,$longitude(${Uri.encode(pin.storeName)})"
        )
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "Directions are unavailable on this device.", Toast.LENGTH_LONG).show()
        }
    }

    private fun openGoogleProductPhotos(
        match: PracticalShoppingSearchIdentityPresentation.Match
    ) {
        PracticalShoppingPrivateIdentity.fromSearchMatch(match)?.let(::openGoogleProductPhotos)
    }

    private fun openGoogleProductPhotos(identity: PracticalShoppingPrivateIdentity) {
        if (googlePhotoViewerLaunchInFlight) return
        val url = GoogleProductPhotoViewer.urlFor(identity) ?: run {
            showGooglePhotoLaunchError()
            return
        }
        googlePhotoViewerLaunchInFlight = true
        val uri = android.net.Uri.parse(url)
        val intent =
            CustomTabsIntent.Builder()
                .setShowTitle(true)
                .setInitialActivityHeightPx(
                    googlePhotoInitialHeightPx(),
                    CustomTabsIntent.ACTIVITY_HEIGHT_ADJUSTABLE
                )
                .build()
        try {
            intent.launchUrl(this, uri)
        } catch (_: ActivityNotFoundException) {
            // AndroidX has no preflight capability bit for partial Custom Tabs. If the provider
            // rejects the partial launch, ask before falling back to a full-screen browser.
            showGooglePhotoBrowserFallback(uri)
        } catch (_: SecurityException) {
            showGooglePhotoBrowserFallback(uri)
        }
    }

    private fun showGooglePhotoBrowserFallback(uri: android.net.Uri) {
        googlePhotoViewerLaunchInFlight = false
        AlertDialog.Builder(this)
            .setTitle(R.string.search_identity_browser_fallback_title)
            .setMessage(R.string.search_identity_browser_fallback_message)
            .setNegativeButton(R.string.search_identity_browser_fallback_cancel, null)
            .setPositiveButton(R.string.search_identity_browser_fallback_open) { _, _ ->
                launchGooglePhotoFullScreen(uri)
            }
            .show()
    }

    private fun launchGooglePhotoFullScreen(uri: android.net.Uri) {
        googlePhotoViewerLaunchInFlight = true
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (_: ActivityNotFoundException) {
            googlePhotoViewerLaunchInFlight = false
            showGooglePhotoLaunchError()
        } catch (_: SecurityException) {
            googlePhotoViewerLaunchInFlight = false
            showGooglePhotoLaunchError()
        }
    }

    private fun showGooglePhotoLaunchError() {
        val message = getString(R.string.search_identity_browser_unavailable)
        when (shellState.selectedPrimaryTab) {
            AppPrimaryTab.HOME -> homeExperience.showPhotoError(message)
            AppPrimaryTab.BASKET -> basketExperience.showPhotoError(message)
            AppPrimaryTab.SEARCH -> searchStatus.text = message
            else -> Unit
        }
    }

    private fun addSearchIdentityToHome(
        match: PracticalShoppingSearchIdentityPresentation.Match
    ) {
        val identity = PracticalShoppingPrivateIdentity.fromSearchMatch(match)
        if (identity == null) {
            searchStatus.text = getString(R.string.search_identity_add_home_failed)
            return
        }
        val previousState = homeSessionState
        val previousCount = homeSessionState.privateIdentities.size
        val nextState = PracticalShoppingHomeSession.addPrivateIdentity(homeSessionState, identity)
        if (nextState.privateIdentities.size == previousCount &&
            nextState.privateIdentities.none { it.identityKey == identity.identityKey }
        ) {
            searchStatus.text = getString(R.string.search_identity_add_home_failed)
            return
        }
        homeSessionState = nextState
        if (nextState != previousState) {
            authorizePrivateListPersistence()
        }
        dispatch(AppShellIntent.SelectPrimary(AppPrimaryTab.HOME))
        renderHome()
    }

    private fun addSavedProductToHome(action: PracticalShoppingSavedSurfaceAction.AddToHome) {
        val previousState = homeSessionState
        val identity =
            PracticalShoppingPrivateIdentity(
                identityKey = action.identityKey,
                displayName = action.displayName
            )
        val nextState = PracticalShoppingHomeSession.addPrivateIdentity(previousState, identity)
        if (nextState == previousState) {
            val message =
                if (previousState.privateIdentities.any { it.identityKey == action.identityKey }) {
                    "That product is already in Home."
                } else {
                    "Home is full; the saved product was not added."
                }
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            return
        }
        homeSessionState = nextState
        authorizePrivateListPersistence()
        dispatch(AppShellIntent.SelectPrimary(AppPrimaryTab.HOME))
        renderHome()
    }

    // private fun copyFor — keep Search card source-boundary inspection scoped to the card.

    private fun handleTogglePriceWatch(action: PriceWatchUiAction.ToggleWatch) {
        val result =
            if (action.watched) {
                priceWatchStore.removeWatch(action.productIdentityKey)
            } else {
                priceWatchStore.upsertWatch(
                    PriceWatch(
                        productIdentityKey = action.productIdentityKey,
                        displayName = action.displayName,
                        brand = action.brand,
                        packageQuantityLabel = action.packageQuantityLabel
                    )
                )
            }
        if (!result.accepted) {
            refreshPriceWatchState()
            if (::searchStatus.isInitialized) {
                searchStatus.text =
                    "Private Price Watch could not be updated; existing data was preserved."
            }
            return
        }
        priceWatchState = requireNotNull(result.state)
        priceWatchLoadIssue = null
        renderedSearchMatches = null
        renderHome()
        if (::searchStatus.isInitialized) {
            argentinaSearchSurfaceState?.let(::renderSearch)
        }
        if (shellState.route == AppRoute.SAVED ||
            shellState.route == AppRoute.PRICE_WATCHES ||
            shellState.route == AppRoute.CONFIRMED_PURCHASE_HISTORY
        ) {
            renderShell(shellState)
        }
    }

    private fun handlePriceWatchAction(action: PriceWatchUiAction) {
        when (action) {
            is PriceWatchUiAction.ToggleWatch -> handleTogglePriceWatch(action)
            is PriceWatchUiAction.AddPersonalObservation ->
                openPriceObservationDialog(action.productIdentityKey)
            is PriceWatchUiAction.EditTarget -> openPriceWatchTargetDialog(action.productIdentityKey)
            is PriceWatchUiAction.EditObservation -> {
                priceWatchState.observations
                    .firstOrNull { it.observationId == action.observationId }
                    ?.let { observation ->
                        openPriceObservationDialog(observation.productIdentityKey, observation)
                    }
            }
            is PriceWatchUiAction.DeleteObservation -> {
                val result = priceWatchStore.removeObservation(action.observationId)
                if (!result.accepted) {
                    refreshPriceWatchState()
                    return
                }
                priceWatchState = requireNotNull(result.state)
                priceWatchLoadIssue = null
                renderedSearchMatches = null
                renderShell(shellState)
            }
        }
    }

    private fun openPriceObservationDialog(
        productIdentityKey: String,
        existing: PriceObservation? = null
    ) {
        val watch = priceWatchState.watches.firstOrNull { it.productIdentityKey == productIdentityKey } ?: return
        priceObservationDialog?.dismiss()
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(4), dp(4), 0)
        }
        val quantityLayout = TextInputLayout(this).apply { hint = "Package quantity (optional)" }
        val quantityInput = TextInputEditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setSingleLine(true)
            setText(existing?.packageQuantity?.let(ConfirmedPurchaseFormParser::displayQuantityText).orEmpty())
        }
        quantityLayout.addView(quantityInput)
        val unitLayout = TextInputLayout(this).apply {
            hint = "Quantity unit (count, g/kg, or ml/l; optional)"
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) }
        }
        val unitInput = TextInputEditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            setSingleLine(true)
            setText(existing?.packageQuantity?.let(ConfirmedPurchaseFormParser::displayQuantityUnitText).orEmpty())
        }
        unitLayout.addView(unitInput)
        val amountLayout = TextInputLayout(this).apply {
            hint = "Price paid (required, exact decimal)"
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) }
        }
        val amountInput = TextInputEditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setSingleLine(true)
            setText(existing?.let { PriceWatchProjector.formatMoney(it.price).substringBeforeLast(" ") }.orEmpty())
        }
        amountLayout.addView(amountInput)
        val currencyLayout = TextInputLayout(this).apply {
            hint = "Currency code (required, e.g. CAD)"
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) }
        }
        val currencyInput = TextInputEditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            setSingleLine(true)
            setText(existing?.price?.currencyCode.orEmpty())
        }
        currencyLayout.addView(currencyInput)
        val dateLayout = TextInputLayout(this).apply {
            hint = "Observed date YYYY-MM-DD (optional)"
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) }
        }
        val dateInput = TextInputEditText(this).apply { setSingleLine(true); setText(existing?.observedDateIso.orEmpty()) }
        dateLayout.addView(dateInput)
        val storeLayout = TextInputLayout(this).apply {
            hint = "Store (optional)"
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) }
        }
        val storeInput = TextInputEditText(this).apply { setSingleLine(true); setText(existing?.store?.displayName.orEmpty()) }
        storeLayout.addView(storeInput)
        val preferredStore = preferredTripStoreSelection()
        val associatePreferredStore = CheckBox(this).apply {
            text = preferredStore?.let { "Associate with preferred store: ${it.displayName}" }
            isChecked = preferredStore != null && existing?.store?.storeKey == preferredStore.storeKey
            visibility = if (preferredStore == null) View.GONE else View.VISIBLE
        }
        body.addView(TextView(this).apply {
            text = "${watch.displayName}. This remains a private observation, not a live retailer offer."
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(ValuePilotPalette.color("#6B7280"))
        })
        body.addView(quantityLayout); body.addView(unitLayout); body.addView(amountLayout)
        body.addView(currencyLayout); body.addView(dateLayout); body.addView(storeLayout)
        body.addView(associatePreferredStore)
        val dialog = AlertDialog.Builder(this)
            .setTitle(if (existing == null) "Add personal price" else "Correct personal price")
            .setView(body)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton("Save", null)
            .create()
        priceObservationDialog = dialog
        dialog.setOnDismissListener { if (priceObservationDialog === dialog) priceObservationDialog = null }
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                listOf(quantityLayout, unitLayout, amountLayout, currencyLayout, dateLayout, storeLayout).forEach { it.error = null }
                val parsed = ConfirmedPurchaseFormParser.parse(
                    ConfirmedPurchaseFormInput(
                        quantityText = quantityInput.text?.toString().orEmpty(),
                        quantityUnitText = unitInput.text?.toString().orEmpty(),
                        amountText = amountInput.text?.toString().orEmpty(),
                        currencyText = currencyInput.text?.toString().orEmpty(),
                        dateText = dateInput.text?.toString().orEmpty(),
                        storeText = storeInput.text?.toString().orEmpty()
                    )
                )
                if (!parsed.accepted || parsed.values == null) {
                    val failure = requireNotNull(parsed.failure)
                    val target = when (failure.field) {
                        ConfirmedPurchaseFormField.QUANTITY -> quantityLayout
                        ConfirmedPurchaseFormField.AMOUNT, ConfirmedPurchaseFormField.CURRENCY -> amountLayout
                        ConfirmedPurchaseFormField.DATE -> dateLayout
                        ConfirmedPurchaseFormField.STORE -> storeLayout
                    }
                    target.error = failure.message
                    return@setOnClickListener
                }
                val values = requireNotNull(parsed.values)
                val amount = values.amountPaid
                if (amount == null || amount.minorUnits <= 0L) {
                    amountLayout.error = "Enter a positive exact price"
                    return@setOnClickListener
                }
                val associatedStore =
                    if (associatePreferredStore.isChecked && preferredStore != null) {
                        ConfirmedPurchaseStore(displayName = preferredStore.displayName, storeKey = preferredStore.storeKey)
                    } else {
                        values.store
                    }
                val observation = PriceObservation.personal(
                    observationId = existing?.observationId ?: "observation-${UUID.randomUUID()}",
                    productIdentityKey = productIdentityKey,
                    packageQuantity = values.quantity,
                    price = amount,
                    observedDateIso = values.purchaseDateIso,
                    store = associatedStore
                )
                val result =
                    if (existing == null) {
                        priceWatchStore.appendObservation(observation)
                    } else {
                        priceWatchStore.replaceObservation(observation)
                    }
                if (!result.accepted) {
                    amountLayout.error = "Could not save; existing private history was preserved"
                    refreshPriceWatchState()
                    return@setOnClickListener
                }
                priceWatchState = requireNotNull(result.state)
                priceWatchLoadIssue = null
                renderedSearchMatches = null
                dialog.dismiss()
                renderShell(shellState)
            }
        }
        dialog.show()
    }

    private fun openPriceWatchTargetDialog(productIdentityKey: String) {
        val watch = priceWatchState.watches.firstOrNull { it.productIdentityKey == productIdentityKey } ?: return
        priceWatchDialog?.dismiss()
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(4), dp(4), dp(4), 0) }
        val amountLayout = TextInputLayout(this).apply { hint = "Target amount (blank clears)" }
        val amountInput = TextInputEditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setSingleLine(true)
            setText(watch.targetPrice?.let { PriceWatchProjector.formatMoney(it).substringBeforeLast(" ") }.orEmpty())
        }
        amountLayout.addView(amountInput)
        val currencyLayout = TextInputLayout(this).apply { hint = "Currency code"; layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) } }
        val currencyInput = TextInputEditText(this).apply { inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS; setSingleLine(true); setText(watch.targetPrice?.currencyCode.orEmpty()) }
        currencyLayout.addView(currencyInput)
        body.addView(TextView(this).apply { text = "Target prices are private reminders only; they never create alerts or change ranking."; setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f); setTextColor(ValuePilotPalette.color("#6B7280")) })
        body.addView(amountLayout); body.addView(currencyLayout)
        val dialog = AlertDialog.Builder(this).setTitle("Target price").setView(body).setNegativeButton(R.string.cancel, null).setPositiveButton("Save", null).create()
        priceWatchDialog = dialog
        dialog.setOnDismissListener { if (priceWatchDialog === dialog) priceWatchDialog = null }
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                amountLayout.error = null; currencyLayout.error = null
                val amountText = amountInput.text?.toString()?.trim().orEmpty()
                val currencyText = currencyInput.text?.toString()?.trim().orEmpty()
                val parsedTarget = PriceWatchTargetParser.parse(amountText, currencyText)
                if (!parsedTarget.accepted) {
                    amountLayout.error = parsedTarget.message
                    return@setOnClickListener
                }
                val target = parsedTarget.target
                val result = priceWatchStore.upsertWatch(watch.copy(targetPrice = target))
                if (!result.accepted) { amountLayout.error = "Could not save; existing private data was preserved"; refreshPriceWatchState(); return@setOnClickListener }
                priceWatchState = requireNotNull(result.state); priceWatchLoadIssue = null; renderedSearchMatches = null
                dialog.dismiss(); renderShell(shellState)
            }
        }
        dialog.show()
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

    private fun googlePhotoInitialHeightPx(): Int =
        maxOf(
            resources.displayMetrics.heightPixels * GOOGLE_PHOTO_INITIAL_HEIGHT_PERCENT / 100,
            dp(GOOGLE_PHOTO_MIN_INITIAL_HEIGHT_DP)
        )

    private data class ScreenCopy(
        val eyebrow: String,
        val title: String,
        val body: String,
        val footnote: String,
        val showCompareAction: Boolean
    )

    private data class ProductMapContext(
        val match: PracticalShoppingSearchIdentityPresentation.Match,
        var response: ArgentinaBackendContract.ShopResponse?,
        var origin: ProductStoreMapOrigin,
        var originCoordinate: ProductStoreMapCoordinate?,
        var selectedStoreKey: String?
    )

    companion object {
        private const val FOREGROUND_LOCATION_REQUEST = 7041
        private const val PRODUCT_MAP_FOREGROUND_LOCATION_REQUEST = 7042

        private const val GOOGLE_PHOTO_INITIAL_HEIGHT_PERCENT = 65
        private const val GOOGLE_PHOTO_MIN_INITIAL_HEIGHT_DP = 320
        private const val STATE_PRIMARY_TAB = "app_shell.primary_tab"
        private const val STATE_ROUTE = "app_shell.route"
        private const val STATE_HOME_QUERY = "app_shell.home_query"
        private const val STATE_HOME_WAS_SUBMITTED = "app_shell.home_was_submitted"
        private const val STATE_HOME_CHICKEN_CHOICE = "app_shell.home_chicken_choice"
        private const val STATE_HOME_EXTRA_STOP_MINIMUM_SAVINGS =
            "app_shell.home_extra_stop_minimum_savings"
        private const val STATE_HOME_REQUEST_DETAILS = "app_shell.home_request_details"
        private const val STATE_HOME_PRIVATE_STATE_PRESENT =
            "app_shell.home_private_state_present"
        private const val STATE_HOME_PRIVATE_IDENTITIES = "app_shell.home_private_identities"
        private const val STATE_HOME_PRIVATE_BASKET_KEYS = "app_shell.home_private_basket_keys"
        private const val STATE_HOME_DETAILS_ITEM_KEY = "app_shell.home_details_item_key"
        private const val STATE_HOME_DETAILS_PACKAGE_COUNT = "app_shell.home_details_package_count"
        private const val STATE_HOME_DETAILS_BRAND = "app_shell.home_details_brand"
        private const val STATE_HOME_DETAILS_EXACT_PRODUCT = "app_shell.home_details_exact_product"
        private const val STATE_SEARCH_QUERY = "app_shell.search_query"
        private const val STATE_SEARCH_WAS_SUBMITTED = "app_shell.search_was_submitted"
        private const val STATE_SEARCH_SCROLL_Y = "app_shell.search_scroll_y"
        private const val STATE_BASKET_SCROLL_Y = "app_shell.basket_scroll_y"
        private const val STATE_PERSONAL_SCROLL_Y = "app_shell.personal_scroll_y"
        private const val STATE_TRIP_PLANNER_SCROLL_Y = "app_shell.trip_planner_scroll_y"
        private const val STATE_PRODUCT_MAP_SELECTED_KEY = "app_shell.product_map_selected_key"
        private const val STATE_PRODUCT_MAP_SELECTED_LABEL = "app_shell.product_map_selected_label"
    }
}
