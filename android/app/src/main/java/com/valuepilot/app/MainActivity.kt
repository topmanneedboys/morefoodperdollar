package com.valuepilot.app

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.textfield.TextInputEditText
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private var shellState = AppShellState.initial()
    private var homeModel = LocalSamplePracticalShoppingDemo.initialModel()

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
    private lateinit var homeExperience: PracticalShoppingHomeSurfaceView
    private lateinit var searchExperience: View
    private lateinit var searchInput: TextInputEditText
    private lateinit var searchButton: MaterialButton
    private lateinit var searchStatus: TextView
    private lateinit var searchProgress: ProgressBar
    private lateinit var searchResultsHeading: TextView
    private lateinit var searchResultsContainer: LinearLayout
    private lateinit var savedExperience: PracticalShoppingSavedSurfaceView
    private lateinit var savedStapleLaunchExperience: PracticalShoppingSavedStapleLaunchView
    private lateinit var stapleWatchSetupExperience: StapleWatchSavedSelectionSurfaceView
    private lateinit var stapleWatchPolicyExperience: StapleWatchPolicyDraftSurfaceView
    private lateinit var stapleWatchResultExperience: StapleWatchSurfaceView
    private lateinit var savedRouteCoordinator: PracticalShoppingSavedRouteCoordinator
    private lateinit var stapleWatchForegroundEvaluationInputHost: StapleWatchForegroundEvaluationInputHost
    private lateinit var stapleWatchForegroundResultSurfaceBinding:
        StapleWatchForegroundResultSurfaceBinding
    private lateinit var stapleWatchSavedDisplayMetadataCompositionCoordinator:
        StapleWatchSavedDisplayMetadataCompositionCoordinator
    private lateinit var stapleWatchFactResolutionHost: StapleWatchFactResolutionHost
    private lateinit var stapleWatchSetupCoordinator: StapleWatchSavedSetupCompositionCoordinator
    private lateinit var stapleWatchPolicySetupCoordinator: StapleWatchPolicySetupCompositionCoordinator

    private var comparisonActivityOpen = false
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
        homeExperience = findViewById(R.id.homeExperience)
        searchExperience = findViewById(R.id.searchExperience)
        searchInput = findViewById(R.id.searchInput)
        searchButton = findViewById(R.id.searchButton)
        searchStatus = findViewById(R.id.searchStatus)
        searchProgress = findViewById(R.id.searchProgress)
        searchResultsHeading = findViewById(R.id.searchResultsHeading)
        searchResultsContainer = findViewById(R.id.searchResultsContainer)
        savedExperience = findViewById(R.id.savedExperience)
        savedStapleLaunchExperience = findViewById(R.id.savedStapleLaunchExperience)
        stapleWatchSetupExperience = findViewById(R.id.stapleWatchSetupExperience)
        stapleWatchPolicyExperience = findViewById(R.id.stapleWatchPolicyExperience)
        stapleWatchResultExperience = findViewById(R.id.stapleWatchResultExperience)

        installSystemBarInsets()
        shellState = restoreShellState(savedInstanceState)
        homeModel = restoreHomeState(savedInstanceState)
        searchState = restoreSearchState(savedInstanceState)
        configureHomeUi()
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

        bottomNavigation.selectedItemId = menuIdFor(shellState.selectedPrimaryTab)
        renderShell(shellState)
    }

    override fun onResume() {
        super.onResume()

        if (comparisonActivityOpen && shellState.route == AppRoute.COMPARE) {
            comparisonActivityOpen = false
            dispatch(AppShellIntent.NavigateBack)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_PRIMARY_TAB, shellState.selectedPrimaryTab.name)

        val homeSnapshot = PracticalShoppingHomeSession.snapshot(homeModel)
        outState.putString(STATE_HOME_QUERY, homeSnapshot.query)
        outState.putBoolean(STATE_HOME_WAS_SUBMITTED, homeSnapshot.wasSubmitted)
        outState.putString(STATE_HOME_CHICKEN_CHOICE, homeSnapshot.chickenChoice?.name)

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
        if (::savedRouteCoordinator.isInitialized) {
            savedExperience.onAction = null
            savedStapleLaunchExperience.onAction = null
            stapleWatchSetupExperience.onAction = null
            stapleWatchSetupExperience.onContinueAction = null
            stapleWatchPolicyExperience.onAction = null
            stapleWatchPolicyExperience.onContinueAction = null
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
        val savedTab = savedInstanceState
            ?.getString(STATE_PRIMARY_TAB)
            ?.let { runCatching { AppPrimaryTab.valueOf(it) }.getOrNull() }
            ?: AppPrimaryTab.HOME

        return AppShellReducer.reduce(
            AppShellState.initial(),
            AppShellIntent.SelectPrimary(savedTab)
        )
    }

    private fun restoreHomeState(savedInstanceState: Bundle?): LocalSamplePracticalShoppingDemo.Model {
        val choice =
            savedInstanceState
                ?.getString(STATE_HOME_CHICKEN_CHOICE)
                ?.let { saved ->
                    runCatching {
                        LocalSamplePracticalShoppingDemo.ChickenChoice.valueOf(saved)
                    }.getOrNull()
                }

        return PracticalShoppingHomeSession.restore(
            PracticalShoppingHomeSession.Snapshot(
                query = savedInstanceState?.getString(STATE_HOME_QUERY).orEmpty(),
                wasSubmitted = savedInstanceState?.getBoolean(STATE_HOME_WAS_SUBMITTED, false) ?: false,
                chickenChoice = choice
            )
        )
    }

    private fun configureHomeUi() {
        homeExperience.onQueryChanged = { rawQuery ->
            homeModel =
                LocalSamplePracticalShoppingDemo.reduce(
                    homeModel,
                    LocalSamplePracticalShoppingDemo.Intent.QueryChanged(rawQuery)
                )
            renderHome()
        }
        homeExperience.onSubmit = { rawQuery ->
            homeModel = PracticalShoppingHomeSession.submit(homeModel, rawQuery)
            renderHome()
        }
        homeExperience.onChickenChoice = { choice ->
            homeModel = PracticalShoppingHomeSession.chooseChicken(homeModel, choice)
            renderHome()
        }
        homeExperience.onCompare = { openComparison() }
        renderHome()
    }

    private fun renderHome() {
        homeExperience.render(
            PracticalShoppingHomeRenderer.render(homeModel.ui)
        )
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

        configureQuickSearch(R.id.quickEggs, getString(R.string.search_quick_eggs))
        configureQuickSearch(R.id.quickMilk, getString(R.string.search_quick_milk))
        configureQuickSearch(R.id.quickChicken, getString(R.string.search_quick_chicken))
        configureQuickSearch(R.id.quickRice, getString(R.string.search_quick_rice))
        configureQuickSearch(R.id.quickPizza, getString(R.string.search_quick_pizza))
    }

    private fun configureSavedUi() {
        val savedPresenter = PracticalShoppingSavedSurfacePresenter(savedExperience)
        val stapleLaunchPresenter =
            PracticalShoppingSavedStapleLaunchPresenter(savedStapleLaunchExperience)
        val stapleSetupPresenter =
            StapleWatchSavedSelectionSurfacePresenter(stapleWatchSetupExperience)
        val staplePolicyPresenter =
            StapleWatchPolicyDraftSurfacePresenter(stapleWatchPolicyExperience)

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
                preconditionsObserver = stapleWatchSavedDisplayMetadataCompositionCoordinator
            )
        stapleWatchSetupCoordinator =
            StapleWatchSavedSetupCompositionCoordinator(
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
            }
        val savedSnapshotObserver =
            PracticalShoppingSavedValidatedSnapshotObserver { snapshot ->
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

        savedExperience.onAction = savedRouteCoordinator::onSurfaceAction
        savedStapleLaunchExperience.onAction = { action ->
            when (action) {
                PracticalShoppingSavedStapleLaunchAction.OpenStapleWatchSetup ->
                    dispatch(AppShellIntent.OpenStapleWatchSetup)
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
            setSearchQuery(query)
            submitSearch()
        }
    }

    private fun setSearchQuery(query: String) {
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
        val rawQuery = searchInput.text?.toString().orEmpty()

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
        shellState = AppShellReducer.reduce(
            shellState,
            AppShellIntent.OpenStandaloneCompare
        )
        comparisonActivityOpen = true
        startActivity(Intent(this, ComparisonActivity::class.java))
    }

    private fun dispatch(intent: AppShellIntent) {
        val next = AppShellReducer.reduce(shellState, intent)
        if (next == shellState) return
        shellState = next
        renderShell(next)
    }

    private fun renderShell(state: AppShellState) {
        if (state.route == AppRoute.COMPARE) return

        val copy = copyFor(state.selectedPrimaryTab)
        screenEyebrow.text = copy.eyebrow
        screenTitle.text = copy.title
        screenBody.text = copy.body
        screenFootnote.text = copy.footnote
        primaryAction.visibility = if (copy.showCompareAction) View.VISIBLE else View.GONE

        val homeVisible = state.selectedPrimaryTab == AppPrimaryTab.HOME
        homeExperience.visibility = if (homeVisible) View.VISIBLE else View.GONE
        if (homeVisible) renderHome()

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
        val stapleSetupVisible = state.route == AppRoute.STAPLE_WATCH_SETUP
        val staplePolicyVisible = state.route == AppRoute.STAPLE_WATCH_POLICY
        savedExperience.visibility = if (savedVisible) View.VISIBLE else View.GONE
        savedStapleLaunchExperience.visibility = if (savedVisible) View.VISIBLE else View.GONE
        stapleWatchSetupExperience.visibility =
            if (stapleSetupVisible) View.VISIBLE else View.GONE
        stapleWatchPolicyExperience.visibility =
            if (staplePolicyVisible) View.VISIBLE else View.GONE
        savedRouteCoordinator.onRouteVisibilityChanged(savedVisible)
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

        searchButton.isEnabled =
            state.query.isNotBlank() &&
                state.status != UniversalSearchStatus.LOADING &&
                state.status != UniversalSearchStatus.QUERY_TOO_LONG

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
        }

        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(15), dp(16), dp(15))
        }

        val rank = TextView(this).apply {
            text =
                when {
                    row.best ->
                        getString(
                            R.string.best_value_rank,
                            row.rank
                        )

                    row.rank != null ->
                        getString(
                            R.string.rank_number,
                            row.rank
                        )

                    else ->
                        "REFERENCE ONLY"
                }
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
        private const val STATE_SEARCH_QUERY = "app_shell.search_query"
        private const val STATE_SEARCH_WAS_SUBMITTED = "app_shell.search_was_submitted"
    }
}
