package com.valuepilot.app.next

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import com.valuepilot.app.AppPrimaryTab
import com.valuepilot.app.AppShellIntent
import com.valuepilot.app.AppShellReducer
import com.valuepilot.app.AppShellState
import com.valuepilot.app.PracticalShoppingSearchIdentityPresentation
import com.valuepilot.app.R
import kotlinx.coroutines.launch

internal enum class NextDestination(val primaryTab: AppPrimaryTab?) {
    HOME(AppPrimaryTab.HOME),
    SEARCH(AppPrimaryTab.SEARCH),
    BASKET(AppPrimaryTab.BASKET),
    SAVED(AppPrimaryTab.SAVED),
    GOOD_PRICE_CHECK(null),
    STAPLE_WATCH_SETUP(null),
    PRIVATE_PRICE_HISTORY(null),
    OBSERVED_PRICE_SELECTION(null),
    OBSERVED_PRICE_CONFIRMATION(null),
    PRODUCT_DETAIL(null),
    SHOPPING_DECISION(null),
    TRIP_PLANNER(null),
    MAP_HANDOFF(null),
    COMPARE(null),
    SETTINGS(null)
}

internal fun usesCompactJourneyTopBar(availableWidth: Dp, fontScale: Float): Boolean =
    availableWidth < 380.dp || fontScale >= 1.2f

/**
 * Opens a fresh observed-price draft without restoring text from a previous use of the same
 * saveable destination. The active attempt still keeps its form state across configuration change.
 */
internal fun prepareAndOpenObservedPriceConfirmation(
    prepare: () -> Boolean,
    resetSavedFormState: () -> Unit,
    navigate: () -> Unit
) {
    if (!prepare()) return
    resetSavedFormState()
    navigate()
}

internal fun primaryTabForNextDestination(
    destination: NextDestination,
    originDestination: NextDestination? = null
): AppPrimaryTab {
    if (destination == NextDestination.COMPARE || destination == NextDestination.SETTINGS) {
        val origin = originDestination?.takeIf {
            it != NextDestination.COMPARE && it != NextDestination.SETTINGS
        }
            ?: NextDestination.HOME
        return primaryTabForNextDestination(origin)
    }
    destination.primaryTab?.let { return it }
    return when (destination) {
        NextDestination.GOOD_PRICE_CHECK,
        NextDestination.STAPLE_WATCH_SETUP,
        NextDestination.PRIVATE_PRICE_HISTORY,
        NextDestination.OBSERVED_PRICE_SELECTION,
        NextDestination.OBSERVED_PRICE_CONFIRMATION -> AppPrimaryTab.SAVED
        NextDestination.PRODUCT_DETAIL -> AppPrimaryTab.SEARCH
        NextDestination.SHOPPING_DECISION,
        NextDestination.TRIP_PLANNER,
        NextDestination.MAP_HANDOFF -> AppPrimaryTab.BASKET
        NextDestination.COMPARE -> AppPrimaryTab.HOME
        NextDestination.SETTINGS -> AppPrimaryTab.HOME
        else -> AppPrimaryTab.HOME
    }
}

internal fun initialShellStateForNextDestination(
    destination: NextDestination
): AppShellState =
    AppShellState.initial().copy(
        selectedPrimaryTab = primaryTabForNextDestination(destination)
    )

private data class PrimaryDestination(
    val tab: AppPrimaryTab,
    val destination: NextDestination,
    val label: String,
    val iconResource: Int
)

private val primaryDestinations = listOf(
    PrimaryDestination(AppPrimaryTab.HOME, NextDestination.HOME, "Inicio", R.drawable.ic_vp_home),
    PrimaryDestination(AppPrimaryTab.SEARCH, NextDestination.SEARCH, "Buscar", R.drawable.ic_vp_search),
    PrimaryDestination(AppPrimaryTab.BASKET, NextDestination.BASKET, "Canasta", R.drawable.ic_vp_basket),
    PrimaryDestination(AppPrimaryTab.SAVED, NextDestination.SAVED, "Guardados", R.drawable.ic_vp_saved)
)

private val destinationStackSaver = listSaver<List<NextDestination>, String>(
    save = { stack -> stack.map(NextDestination::name) },
    restore = { saved ->
        saved.mapNotNull { name -> NextDestination.entries.firstOrNull { it.name == name } }
            .ifEmpty { listOf(NextDestination.HOME) }
    }
)

private val appShellStateSaver = listSaver<AppShellState, String>(
    save = { state ->
        listOf(
            state.selectedPrimaryTab.name,
            state.route.name,
            state.compareReturnTab?.name.orEmpty(),
            state.canNavigateBack.toString()
        )
    },
    restore = { saved ->
        val selectedTab =
            AppPrimaryTab.entries.firstOrNull { it.name == saved.getOrNull(0) }
                ?: AppPrimaryTab.HOME
        val route =
            com.valuepilot.app.AppRoute.entries.firstOrNull { it.name == saved.getOrNull(1) }
                ?: AppShellReducer.routeFor(selectedTab)
        val returnTab =
            AppPrimaryTab.entries.firstOrNull { it.name == saved.getOrNull(2) }
        AppShellState(
            selectedPrimaryTab = selectedTab,
            route = route,
            compareReturnTab = returnTab,
            canNavigateBack = saved.getOrNull(3)?.toBooleanStrictOrNull() ?: false
        )
    }
)

@Composable
internal fun ValuePilotNextApp(
    journey: ValuePilotNextJourneyViewModel,
    startDestination: NextDestination = NextDestination.HOME,
    reducedMotion: Boolean,
    presentationPreferences: ValuePilotNextUiPreferences,
    language: ValuePilotNextLanguage,
    onPresentationPreferencesChanged: (ValuePilotNextUiPreferences) -> Unit,
    onExitPreview: () -> Unit,
    onOpenLegacyComparisonTools: () -> Unit,
    onOpenGoogleImages: (PracticalShoppingSearchIdentityPresentation.Match, String) -> Unit,
    onShareCompareHereResult: (com.valuepilot.app.CompareHereShareCard) -> Boolean,
    onOpenStoreMap: (com.valuepilot.app.ArgentinaPrivateBasketStoreProjection) -> Boolean,
    onStartGoodPriceBarcodeScan: () -> Unit,
    onOpenLegacyStapleWatch: () -> Unit,
    onSharePrivatePriceHistory: (String) -> Boolean,
    onShareGoodPriceResult: (com.valuepilot.app.GoodPriceShareCard) -> Boolean
) {
    val ui = journey.uiState
    var shellState by rememberSaveable(stateSaver = appShellStateSaver) {
        mutableStateOf(initialShellStateForNextDestination(startDestination))
    }
    var routeStack by rememberSaveable(stateSaver = destinationStackSaver) {
        mutableStateOf(listOf(startDestination))
    }
    var selectedIdentityKey by rememberSaveable { mutableStateOf<String?>(null) }
    var showDesignFixture by rememberSaveable { mutableStateOf(false) }
    var selectedMapOfferKey by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedMapProductName by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf(ui.query) }
    var tripDistance by rememberSaveable { mutableStateOf("") }
    var tripConsumption by rememberSaveable { mutableStateOf("") }
    var tripFuelPrice by rememberSaveable { mutableStateOf("") }
    var tripTolls by rememberSaveable { mutableStateOf("") }
    var tripParking by rememberSaveable { mutableStateOf("") }
    var restoreSubmittedSearch by rememberSaveable { mutableStateOf(false) }
    var transitionDirection by remember { mutableIntStateOf(1) }
    val destinationStateHolder = rememberSaveableStateHolder()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val currentDestination = routeStack.lastOrNull() ?: NextDestination.HOME
    val savedDestinationVisible =
            currentDestination == NextDestination.SAVED ||
            currentDestination == NextDestination.OBSERVED_PRICE_SELECTION ||
            currentDestination == NextDestination.STAPLE_WATCH_SETUP ||
            currentDestination == NextDestination.PRIVATE_PRICE_HISTORY
    DisposableEffect(savedDestinationVisible) {
        if (savedDestinationVisible) journey.onSavedRouteVisibilityChanged(true)
        onDispose {
            if (savedDestinationVisible) journey.onSavedRouteVisibilityChanged(false)
        }
    }
    val observedPriceSelectionVisible =
        currentDestination == NextDestination.OBSERVED_PRICE_SELECTION
    DisposableEffect(observedPriceSelectionVisible) {
        if (observedPriceSelectionVisible) {
            journey.onObservedPriceSelectionRouteVisibilityChanged(true)
        }
        onDispose {
            if (observedPriceSelectionVisible) {
                journey.onObservedPriceSelectionRouteVisibilityChanged(false)
            }
        }
    }
    val observedPriceConfirmationVisible =
        currentDestination == NextDestination.OBSERVED_PRICE_CONFIRMATION
    DisposableEffect(observedPriceConfirmationVisible) {
        if (observedPriceConfirmationVisible) {
            journey.onObservedPriceConfirmationRouteVisibilityChanged(true)
        }
        onDispose {
            if (observedPriceConfirmationVisible) {
                journey.onObservedPriceConfirmationRouteVisibilityChanged(false)
            }
        }
    }
    val stapleWatchSetupVisible = currentDestination == NextDestination.STAPLE_WATCH_SETUP
    DisposableEffect(stapleWatchSetupVisible) {
        if (stapleWatchSetupVisible) {
            journey.onStapleWatchSetupRouteVisibilityChanged(true)
        }
        onDispose {
            if (stapleWatchSetupVisible) {
                journey.onStapleWatchSetupRouteVisibilityChanged(false)
            }
        }
    }
    val privatePriceHistoryVisible = currentDestination == NextDestination.PRIVATE_PRICE_HISTORY
    DisposableEffect(privatePriceHistoryVisible) {
        if (privatePriceHistoryVisible) {
            journey.onPrivatePriceHistoryRouteVisibilityChanged(true)
        }
        onDispose {
            if (privatePriceHistoryVisible) {
                journey.onPrivatePriceHistoryRouteVisibilityChanged(false)
            }
        }
    }
    val selectedMapOffer = remember(ui.search.presentation, selectedMapOfferKey) {
        ui.search.presentation?.matches
            ?.asSequence()
            ?.flatMap { it.qualifiedOffers.asSequence() }
            ?.firstOrNull { it.offerKey == selectedMapOfferKey }
    }

    fun notify(message: String) {
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    fun syncSelectedTab(destination: NextDestination) {
        val origin = currentDestination.takeIf {
            destination == NextDestination.COMPARE || destination == NextDestination.SETTINGS
        }
        val tab = primaryTabForNextDestination(destination, origin)
        if (shellState.selectedPrimaryTab != tab) {
            shellState = AppShellReducer.reduce(shellState, AppShellIntent.SelectPrimary(tab))
        }
    }

    fun selectPrimary(destination: NextDestination) {
        if (currentDestination == NextDestination.OBSERVED_PRICE_CONFIRMATION &&
            destination != NextDestination.OBSERVED_PRICE_CONFIRMATION
        ) {
            journey.cancelObservedPriceConfirmation()
        }
        transitionDirection = 1
        syncSelectedTab(destination)
        routeStack = listOf(destination)
        if (destination != NextDestination.PRODUCT_DETAIL) selectedIdentityKey = null
    }

    fun navigate(destination: NextDestination) {
        transitionDirection = 1
        syncSelectedTab(destination)
        if (routeStack.lastOrNull() != destination) {
            routeStack = routeStack + destination
        }
    }

    fun navigateBack() {
        transitionDirection = -1
        if (currentDestination == NextDestination.OBSERVED_PRICE_CONFIRMATION) {
            journey.cancelObservedPriceConfirmation()
        }
        if (routeStack.size > 1) {
            routeStack = routeStack.dropLast(1)
            syncSelectedTab(routeStack.last())
        } else if (currentDestination != NextDestination.HOME) {
            selectPrimary(NextDestination.HOME)
        } else {
            onExitPreview()
        }
    }

    fun onQueryChanged(value: String) {
        query = value
        restoreSubmittedSearch = false
        journey.queryChanged(value)
    }

    fun submitSearch() {
        focusManager.clearFocus()
        keyboard?.hide()
        restoreSubmittedSearch = query.isNotBlank()
        journey.submitSearch(query)
    }

    androidx.compose.runtime.LaunchedEffect(query, restoreSubmittedSearch, ui.query) {
        if (restoreSubmittedSearch && query.isNotBlank() && ui.query != query) {
            journey.queryChanged(query)
            journey.submitSearch(query)
        }
    }

    BackHandler(enabled = currentDestination != NextDestination.HOME || routeStack.size > 1) {
        navigateBack()
    }

    CompositionLocalProvider(LocalValuePilotNextLanguage provides language) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val expanded = maxWidth >= 760.dp
            val safeInsets =
                if (expanded) WindowInsets.safeDrawing
                else WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(safeInsets)
            ) {
                if (expanded) {
                    ValuePilotNavigationRail(
                        selectedTab = shellState.selectedPrimaryTab,
                        language = language,
                        onSelectTab = { tab ->
                            primaryDestinations.firstOrNull { it.tab == tab }?.let {
                                selectPrimary(it.destination)
                            }
                        }
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    JourneyTopBar(
                        destination = currentDestination,
                        areaLabel = ui.search.areaLabel,
                        language = language,
                        canGoBack = routeStack.size > 1,
                        onBack = ::navigateBack,
                        onOpenSettings = { navigate(NextDestination.SETTINGS) },
                        onExitPreview = onExitPreview
                    )

                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        val enterDuration = ValuePilotMotion.durationMillis(
                            ValuePilotMotion.standardMillis,
                            reducedMotion
                        )
                        val exitDuration = ValuePilotMotion.durationMillis(
                            ValuePilotMotion.quickMillis,
                            reducedMotion
                        )
                        AnimatedContent(
                            targetState = currentDestination,
                            modifier = Modifier.fillMaxSize(),
                            transitionSpec = {
                                (
                                    fadeIn(tween(enterDuration)) +
                                        slideInHorizontally(
                                            initialOffsetX = { width ->
                                                if (transitionDirection > 0) width / 20 else -width / 20
                                            },
                                            animationSpec = tween(enterDuration)
                                        )
                                    ) togetherWith (
                                    fadeOut(tween(exitDuration)) +
                                        slideOutHorizontally(
                                            targetOffsetX = { width ->
                                                if (transitionDirection > 0) -width / 20 else width / 20
                                            },
                                            animationSpec = tween(exitDuration)
                                        )
                                    )
                            },
                            label = valuePilotNextText(language, "ValuePilot shopping journey", "Recorrido de compras ValuePilot")
                        ) { destination ->
                            destinationStateHolder.SaveableStateProvider(destination.name) {
                            when (destination) {
                                NextDestination.HOME -> ValuePilotHomeScreen(
                                    basketCount = ui.privateIdentities.size,
                                    areaLabel = ui.search.areaLabel,
                                    onSearch = { navigate(NextDestination.SEARCH) },
                                    onBasket = { selectPrimary(NextDestination.BASKET) },
                                    onGoodPriceCheck = {
                                        journey.openGoodPriceForNewCheck()
                                        navigate(NextDestination.GOOD_PRICE_CHECK)
                                    },
                                    onTripPlanner = { navigate(NextDestination.TRIP_PLANNER) },
                                    onCompare = { navigate(NextDestination.COMPARE) }
                                )

                                NextDestination.SEARCH -> ValuePilotSearchScreen(
                                    query = query,
                                    state = ui.search,
                                    showDesignFixture = showDesignFixture,
                                    onQueryChanged = { updated ->
                                        showDesignFixture = false
                                        onQueryChanged(updated)
                                    },
                                    onSubmit = {
                                        showDesignFixture = false
                                        submitSearch()
                                    },
                                    onLoadMore = journey::loadMoreSearchResults,
                                    onAddToBasket = { match ->
                                        when (journey.addSearchMatch(match)) {
                                            AddPrivateIdentityResult.ADDED ->
                                                notify(valuePilotNextText(language, "Added to your private basket.", "Producto agregado a tu canasta privada."))
                                            AddPrivateIdentityResult.REFRESHED ->
                                                notify(valuePilotNextText(language, "Current Search evidence refreshed for this product.", "Actualizamos la evidencia de Search para este producto."))
                                            AddPrivateIdentityResult.SOURCE_UNAVAILABLE ->
                                                notify(valuePilotNextText(language, "This result has no qualified source identity to save.", "Este resultado no tiene una identidad de fuente calificada para guardar."))
                                            AddPrivateIdentityResult.BASKET_LIMIT ->
                                                notify(valuePilotNextText(language, "Your private basket has reached its item limit.", "Tu canasta privada alcanzó el límite de productos."))
                                            AddPrivateIdentityResult.PERSISTENCE_UNAVAILABLE ->
                                                notify(valuePilotNextText(language, "The private basket could not be saved.", "No se pudo guardar la identidad en la canasta."))
                                        }
                                    },
                                    onOpenMatch = { match ->
                                        selectedIdentityKey = match.identityKey
                                        navigate(NextDestination.PRODUCT_DETAIL)
                                    },
                                    onOpenGoogleImages = onOpenGoogleImages,
                                    onShowDesignFixture = { showDesignFixture = true }
                                )

                                NextDestination.PRODUCT_DETAIL -> {
                                    val isDesignFixture =
                                        selectedIdentityKey == ValuePilotNextLayoutFixture.IDENTITY_KEY
                                    val match = if (isDesignFixture) {
                                        ValuePilotNextLayoutFixture.match
                                    } else {
                                        ui.search.presentation?.matches
                                            ?.firstOrNull { it.identityKey == selectedIdentityKey }
                                    }
                                    ValuePilotProductDetailScreen(
                                        match = match,
                                        isSearchLoading = ui.search.mode ==
                                            com.valuepilot.app.ArgentinaLiveSearchSurfaceMode.LOADING,
                                        isDesignFixture = isDesignFixture,
                                        areaLabel = ui.search.areaLabel,
                                        onAddToBasket = { selected ->
                                            when (journey.addSearchMatch(selected)) {
                                                AddPrivateIdentityResult.ADDED -> {
                                                    notify(valuePilotNextText(language, "Added to your private basket.", "Producto agregado a tu canasta privada."))
                                                    selectPrimary(NextDestination.BASKET)
                                                }
                                                AddPrivateIdentityResult.REFRESHED -> {
                                                    notify(valuePilotNextText(language, "Current Search evidence refreshed.", "Actualizamos la evidencia de Search."))
                                                    selectPrimary(NextDestination.BASKET)
                                                }
                                                AddPrivateIdentityResult.SOURCE_UNAVAILABLE ->
                                                    notify(valuePilotNextText(language, "This result has no qualified source identity to save.", "Este resultado no tiene una identidad de fuente calificada para guardar."))
                                                AddPrivateIdentityResult.BASKET_LIMIT ->
                                                    notify(valuePilotNextText(language, "Your private basket has reached its item limit.", "Tu canasta privada alcanzó el límite de productos."))
                                                AddPrivateIdentityResult.PERSISTENCE_UNAVAILABLE ->
                                                    notify(valuePilotNextText(language, "The private basket could not be saved.", "No se pudo guardar la identidad en la canasta."))
                                            }
                                        },
                                        onSearchAgain = { selectPrimary(NextDestination.SEARCH) },
                                        onOpenGoogleImages = onOpenGoogleImages,
                                        onOpenMapContext = { offer ->
                                            val context =
                                                com.valuepilot.app.ArgentinaQualifiedStoreMapContextPresentation
                                                    .fromOffer(offer, match?.displayName.orEmpty())
                                            if (context.latitude != null && context.longitude != null) {
                                                selectedMapOfferKey = offer.offerKey
                                                selectedMapProductName = match?.displayName
                                                navigate(NextDestination.MAP_HANDOFF)
                                                notify(valuePilotNextText(language, "Store coordinates are source context, not a route.", "Las coordenadas de tienda son contexto de fuente, no una ruta."))
                                            } else {
                                                notify(valuePilotNextText(language, "This offer has no verified map coordinates.", "Esta oferta no tiene coordenadas de mapa verificadas."))
                                            }
                                        }
                                    )
                                }

                                NextDestination.BASKET -> ValuePilotBasketScreen(
                                    basket = ui.basket,
                                    planLoading = ui.planLoading,
                                    onSearch = { navigate(NextDestination.SEARCH) },
                                    onRemove = { identityKey ->
                                        if (!journey.removePrivateIdentity(identityKey)) {
                                            notify(valuePilotNextText(language, "The saved basket could not be updated.", "No se pudo actualizar la canasta guardada."))
                                        }
                                    },
                                    onPlan = {
                                        journey.requestBasketPlan()
                                        navigate(NextDestination.SHOPPING_DECISION)
                                    }
                                )

                                NextDestination.SHOPPING_DECISION -> ValuePilotShoppingDecisionScreen(
                                    basket = ui.basket,
                                    plan = ui.plan,
                                    planLoading = ui.planLoading,
                                    planError = ui.planError,
                                    tripResult = ui.tripResult,
                                    onEditBasket = { selectPrimary(NextDestination.BASKET) },
                                    onTripPlanner = { navigate(NextDestination.TRIP_PLANNER) },
                                    onMap = {
                                        selectedMapOfferKey = null
                                        selectedMapProductName = null
                                        navigate(NextDestination.MAP_HANDOFF)
                                    }
                                )

                                NextDestination.TRIP_PLANNER -> ValuePilotTripPlannerScreen(
                                    plan = ui.plan,
                                    tripResult = ui.tripResult,
                                    tripError = ui.tripError,
                                    distance = tripDistance,
                                    consumption = tripConsumption,
                                    fuelPrice = tripFuelPrice,
                                    tolls = tripTolls,
                                    parking = tripParking,
                                    onInputChanged = { distance, consumption, fuelPrice, tolls, parking ->
                                        tripDistance = distance
                                        tripConsumption = consumption
                                        tripFuelPrice = fuelPrice
                                        tripTolls = tolls
                                        tripParking = parking
                                        journey.clearTripCalculation()
                                    },
                                    onCalculate = journey::calculateTrip,
                                    onMap = {
                                        selectedMapOfferKey = null
                                        selectedMapProductName = null
                                        navigate(NextDestination.MAP_HANDOFF)
                                    }
                                )

                                NextDestination.MAP_HANDOFF -> ValuePilotMapHandoffScreen(
                                    plan = ui.plan,
                                    selectedOffer = selectedMapOffer,
                                    selectedProductName = selectedMapProductName,
                                    onOpenMap = { store ->
                                        if (!onOpenStoreMap(store)) {
                                            notify(valuePilotNextText(language, "No map app is available for this store.", "No hay una app de mapas disponible para esta tienda."))
                                        }
                                    }
                                )

                                NextDestination.SAVED -> ValuePilotSavedScreen(
                                    state = ui.saved,
                                    language = language,
                                    stapleLaunch = ui.stapleWatchLaunch,
                                    onAction = journey::onSavedSurfaceAction,
                                    onCheckPrice = { displayName ->
                                        journey.openGoodPriceFromSaved(displayName)
                                        navigate(NextDestination.GOOD_PRICE_CHECK)
                                    },
                                    onSearch = { selectPrimary(NextDestination.SEARCH) },
                                    onOpenStapleWatch = {
                                        navigate(NextDestination.STAPLE_WATCH_SETUP)
                                    },
                                    onOpenPrivatePriceHistory = {
                                        navigate(NextDestination.PRIVATE_PRICE_HISTORY)
                                    },
                                    onRecordObservedPrice = {
                                        navigate(NextDestination.OBSERVED_PRICE_SELECTION)
                                    }
                                )

                                NextDestination.GOOD_PRICE_CHECK ->
                                    ValuePilotNextGoodPriceScreen(
                                        state = ui.goodPrice,
                                        onDraftChanged = journey::onGoodPriceDraftChanged,
                                        onPriceSelectionChanged = journey::onGoodPricePriceSelectionChanged,
                                        onCheck = journey::checkGoodPrice,
                                        onClearDraft = journey::clearGoodPriceDraft,
                                        onClearPrivateMemory = journey::clearGoodPricePrivateMemory,
                                        onScanBarcode = onStartGoodPriceBarcodeScan,
                                        onChooseBarcodeOption = journey::chooseGoodPriceBarcodeOption,
                                        onDismissBarcodeChoice = journey::dismissGoodPriceBarcodeChoice,
                                        onShare = { card ->
                                            if (!onShareGoodPriceResult(card)) {
                                                notify(valuePilotNextText(language, "No sharing app is available.", "No hay una app para compartir disponible."))
                                            }
                                        },
                                        onBack = ::navigateBack
                                    )

                                NextDestination.STAPLE_WATCH_SETUP ->
                                    ValuePilotNextStapleWatchSetupScreen(
                                        state = ui.stapleWatchSetup,
                                        onAction = journey::onStapleWatchSetupAction,
                                        onContinue = journey::onStapleWatchSetupContinue,
                                        onOpenLegacyStapleWatch = onOpenLegacyStapleWatch,
                                        onBack = ::navigateBack
                                    )

                                NextDestination.PRIVATE_PRICE_HISTORY ->
                                    ValuePilotNextPrivatePriceHistoryScreen(
                                        state = ui.privatePriceHistory,
                                        onRefresh = journey::refreshPrivatePriceHistory,
                                        onForget = journey::forgetPrivatePriceObservation,
                                        onClearAll = journey::clearPrivatePriceHistory,
                                        onExport = { preview ->
                                            val text = journey.exportPrivatePriceHistoryForShare(preview)
                                            if (text != null && !onSharePrivatePriceHistory(text)) {
                                                journey.reportPrivatePriceHistoryShareUnavailable()
                                            }
                                        },
                                        onOpenComparison = { navigate(NextDestination.COMPARE) },
                                        onBack = ::navigateBack
                                    )

                                NextDestination.COMPARE -> ValuePilotNextCompareHereScreen(
                                    state = ui.compareHere,
                                    onProductChanged = journey::onCompareHereProductChanged,
                                    onAddProduct = journey::onCompareHereProductAdded,
                                    onRemoveProduct = journey::onCompareHereProductRemoved,
                                    onPriceSelectionChanged = journey::onCompareHerePriceSelectionChanged,
                                    onLikeForLikeChanged = journey::onCompareHereLikeForLikeChanged,
                                    onCompare = journey::compareHere,
                                    onClear = journey::clearCompareHereDraft,
                                    onShare = { card ->
                                        if (!onShareCompareHereResult(card)) {
                                            notify(valuePilotNextText(language, "No sharing app is available.", "No hay una app para compartir disponible."))
                                        }
                                    },
                                    onOpenCaptureTools = onOpenLegacyComparisonTools,
                                    onBack = ::navigateBack
                                )

                                NextDestination.SETTINGS -> ValuePilotNextSettingsScreen(
                                    preferences = presentationPreferences,
                                    onAppearanceSelected = { appearance ->
                                        onPresentationPreferencesChanged(
                                            presentationPreferences.copy(appearance = appearance)
                                        )
                                    },
                                    onMotionSelected = { motion ->
                                        onPresentationPreferencesChanged(
                                            presentationPreferences.copy(motion = motion)
                                        )
                                    },
                                    onDensitySelected = { density ->
                                        onPresentationPreferencesChanged(
                                            presentationPreferences.copy(density = density)
                                        )
                                    },
                                    onLanguageSelected = { selectedLanguage ->
                                        onPresentationPreferencesChanged(
                                            presentationPreferences.copy(language = selectedLanguage)
                                        )
                                    },
                                    onResetPreferences = {
                                        onPresentationPreferencesChanged(ValuePilotNextUiPreferences())
                                    },
                                    onBack = ::navigateBack
                                )

                                NextDestination.OBSERVED_PRICE_SELECTION ->
                                    ValuePilotObservedPriceSelectionScreen(
                                        savedState = ui.saved,
                                        selection = ui.observedPriceSelection,
                                        prefillResult = ui.observedPricePrefillResult,
                                        onSelectionAction = journey::onObservedPriceSelectionAction,
                                        onCheckPrefill = journey::onCheckObservedPricePrefill,
                                        onSavedAction = journey::onSavedSurfaceAction,
                                        onContinueToConfirmation = {
                                            journey.currentObservedPricePrefillAttemptOrNull()
                                                ?.let { attempt ->
                                                    prepareAndOpenObservedPriceConfirmation(
                                                        prepare = {
                                                            journey.prepareObservedPriceConfirmation(attempt)
                                                        },
                                                        resetSavedFormState = {
                                                            destinationStateHolder.removeState(
                                                                NextDestination.OBSERVED_PRICE_CONFIRMATION.name
                                                            )
                                                        },
                                                        navigate = {
                                                            navigate(NextDestination.OBSERVED_PRICE_CONFIRMATION)
                                                        }
                                                    )
                                                }
                                        },
                                        onBackToSaved = ::navigateBack
                                    )

                                NextDestination.OBSERVED_PRICE_CONFIRMATION ->
                                    ValuePilotNextObservedPriceConfirmationScreen(
                                        state = ui.observedPriceConfirmation,
                                        onSetPrice = journey::onObservedPriceConfirmationPriceInput,
                                        onSetObservedAt = journey::onObservedPriceConfirmationTimeInput,
                                        onSetProofReference = journey::onObservedPriceConfirmationProofReferenceInput,
                                        onSelectProof = journey::onObservedPriceConfirmationProofSelectionRequested,
                                        onSubmit = journey::onObservedPriceConfirmationSubmitRequested,
                                        onBack = ::navigateBack
                                    )
                            }
                            }
                        }

                        SnackbarHost(
                            hostState = snackbarHostState,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(horizontal = 16.dp, vertical = if (expanded) 16.dp else 8.dp)
                        )
                    }

                    if (!expanded) {
                        ValuePilotNavigationBar(
                            selectedTab = shellState.selectedPrimaryTab,
                            language = language,
                            onSelectTab = { tab ->
                                primaryDestinations.firstOrNull { it.tab == tab }?.let {
                                    selectPrimary(it.destination)
                                }
                            }
                        )
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun JourneyTopBar(
    destination: NextDestination,
    areaLabel: String,
    language: ValuePilotNextLanguage,
    canGoBack: Boolean,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onExitPreview: () -> Unit
) {
    val primaryDestination = destination in setOf(
        NextDestination.HOME,
        NextDestination.SEARCH,
        NextDestination.BASKET,
        NextDestination.SAVED
    )
    val title = when {
        primaryDestination -> "ValuePilot"
        destination == NextDestination.PRODUCT_DETAIL -> valuePilotNextText(language, "Product detail", "Detalle del producto")
        else -> when (destination) {
            NextDestination.GOOD_PRICE_CHECK -> valuePilotNextText(language, "Good price", "Buen precio")
            NextDestination.STAPLE_WATCH_SETUP -> valuePilotNextText(language, "Watch my staples", "Mis esenciales")
            NextDestination.PRIVATE_PRICE_HISTORY -> valuePilotNextText(language, "Price history", "Historial de precios")
            NextDestination.COMPARE -> valuePilotNextText(language, "Compare products", "Comparar productos")
            NextDestination.SETTINGS -> valuePilotNextText(language, "Settings", "Ajustes")
            NextDestination.OBSERVED_PRICE_SELECTION -> valuePilotNextText(language, "Observed price", "Precio observado")
            NextDestination.OBSERVED_PRICE_CONFIRMATION -> valuePilotNextText(language, "Confirm price", "Confirmar precio")
            NextDestination.SHOPPING_DECISION -> valuePilotNextText(language, "Shopping decision", "Decisión de compra")
            NextDestination.TRIP_PLANNER -> valuePilotNextText(language, "Trip planner", "Plan de viaje")
            NextDestination.MAP_HANDOFF -> valuePilotNextText(language, "Store context", "Contexto de tienda")
            else -> "ValuePilot"
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 46.dp)
            .padding(horizontal = 14.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        if (canGoBack) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(42.dp).semantics {
                    contentDescription = valuePilotNextText(language, "Back", "Volver")
                }
            ) {
                Text("‹", style = MaterialTheme.typography.headlineMedium.copy(fontSize = 28.sp), color = MaterialTheme.colorScheme.onSurface)
            }
        } else {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Text("V", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp))
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(0.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp, lineHeight = 19.sp),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1
            )
            Text(
                areaLabel.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, lineHeight = 11.sp),
                color = MaterialTheme.colorScheme.tertiary,
                maxLines = 1
            )
        }
        if (destination != NextDestination.SETTINGS) {
            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier.size(40.dp).semantics {
                    contentDescription = valuePilotNextText(language, "Open settings", "Abrir ajustes")
                }
            ) {
                Text("⚙", style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        androidx.compose.material3.TextButton(
            onClick = onExitPreview,
            modifier = Modifier
                .heightIn(min = 40.dp)
                .widthIn(min = 42.dp)
                .semantics {
                    contentDescription = valuePilotNextText(
                        language,
                        "Exit the preview and return to the original app",
                        "Salir de la vista previa y volver a la aplicación original"
                    )
                }
        ) {
            Text(
                valuePilotNextText(language, "Exit", "Salir"),
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ValuePilotNavigationBar(
    selectedTab: AppPrimaryTab,
    language: ValuePilotNextLanguage,
    onSelectTab: (AppPrimaryTab) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.82f),
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            primaryDestinations.forEach { destination ->
                val selected = selectedTab == destination.tab
                Surface(
                    onClick = { onSelectTab(destination.tab) },
                    modifier = Modifier.weight(1f).heightIn(min = 50.dp),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                    color = if (selected) MaterialTheme.colorScheme.surface else Color.Transparent,
                    contentColor = if (selected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        androidx.compose.material3.Icon(
                            painter = painterResource(destination.iconResource),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            navigationLabel(destination.tab, language),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, lineHeight = 11.sp),
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ValuePilotNavigationRail(
    selectedTab: AppPrimaryTab,
    language: ValuePilotNextLanguage,
    onSelectTab: (AppPrimaryTab) -> Unit
) {
    NavigationRail(
        modifier = Modifier.fillMaxHeight(),
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        primaryDestinations.forEach { destination ->
            NavigationRailItem(
                selected = selectedTab == destination.tab,
                onClick = { onSelectTab(destination.tab) },
                icon = {
                    androidx.compose.material3.Icon(
                        painter = painterResource(destination.iconResource),
                        contentDescription = null
                    )
                },
                label = { Text(navigationLabel(destination.tab, language)) },
                alwaysShowLabel = true,
                colors = NavigationRailItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}

private fun navigationLabel(
    tab: AppPrimaryTab,
    language: ValuePilotNextLanguage
): String = when (tab) {
    AppPrimaryTab.HOME -> valuePilotNextText(language, "Home", "Inicio")
    AppPrimaryTab.SEARCH -> valuePilotNextText(language, "Search", "Buscar")
    AppPrimaryTab.BASKET -> valuePilotNextText(language, "Basket", "Canasta")
    AppPrimaryTab.SAVED -> valuePilotNextText(language, "Saved", "Guardados")
}
