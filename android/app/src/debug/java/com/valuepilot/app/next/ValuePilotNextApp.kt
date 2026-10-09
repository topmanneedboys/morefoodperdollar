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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import com.valuepilot.app.AppPrimaryTab
import com.valuepilot.app.AppShellIntent
import com.valuepilot.app.AppShellReducer
import com.valuepilot.app.AppShellState
import com.valuepilot.app.R
import kotlinx.coroutines.launch

private enum class NextDestination(val primaryTab: AppPrimaryTab?) {
    HOME(AppPrimaryTab.HOME),
    SEARCH(AppPrimaryTab.SEARCH),
    BASKET(AppPrimaryTab.BASKET),
    SAVED(AppPrimaryTab.SAVED),
    OBSERVED_PRICE_SELECTION(null),
    PRODUCT_DETAIL(null),
    SHOPPING_DECISION(null),
    TRIP_PLANNER(null),
    MAP_HANDOFF(null)
}

private data class PrimaryDestination(
    val tab: AppPrimaryTab,
    val destination: NextDestination,
    val label: String,
    val iconResource: Int
)

private val primaryDestinations = listOf(
    PrimaryDestination(AppPrimaryTab.HOME, NextDestination.HOME, "Home", R.drawable.ic_vp_home),
    PrimaryDestination(AppPrimaryTab.SEARCH, NextDestination.SEARCH, "Search", R.drawable.ic_vp_search),
    PrimaryDestination(AppPrimaryTab.BASKET, NextDestination.BASKET, "Basket", R.drawable.ic_vp_basket),
    PrimaryDestination(AppPrimaryTab.SAVED, NextDestination.SAVED, "Saved", R.drawable.ic_vp_saved)
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
    reducedMotion: Boolean,
    onExitPreview: () -> Unit,
    onOpenComparison: () -> Unit,
    onOpenStoreMap: (com.valuepilot.app.ArgentinaPrivateBasketStoreProjection) -> Boolean,
    onCheckSavedPrice: (String) -> Unit,
    onOpenSavedTools: () -> Unit,
    onOpenObservedPriceConfirmation:
        (com.valuepilot.app.UserObservedPriceSavedPrefillHandoffAttempt) -> Unit
) {
    val ui = journey.uiState
    var shellState by rememberSaveable(stateSaver = appShellStateSaver) {
        mutableStateOf(AppShellState.initial())
    }
    var routeStack by rememberSaveable(stateSaver = destinationStackSaver) {
        mutableStateOf(listOf(NextDestination.HOME))
    }
    var selectedIdentityKey by rememberSaveable { mutableStateOf<String?>(null) }
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
            currentDestination == NextDestination.OBSERVED_PRICE_SELECTION
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
        val tab = destination.primaryTab ?: when (destination) {
            NextDestination.OBSERVED_PRICE_SELECTION -> AppPrimaryTab.SAVED
            NextDestination.PRODUCT_DETAIL -> AppPrimaryTab.SEARCH
            NextDestination.SHOPPING_DECISION,
            NextDestination.TRIP_PLANNER,
            NextDestination.MAP_HANDOFF -> AppPrimaryTab.BASKET
            else -> AppPrimaryTab.HOME
        }
        if (shellState.selectedPrimaryTab != tab) {
            shellState = AppShellReducer.reduce(shellState, AppShellIntent.SelectPrimary(tab))
        }
    }

    fun selectPrimary(destination: NextDestination) {
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
                        canGoBack = routeStack.size > 1,
                        onBack = ::navigateBack,
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
                            label = "ValuePilot shopping journey"
                        ) { destination ->
                            destinationStateHolder.SaveableStateProvider(destination.name) {
                            when (destination) {
                                NextDestination.HOME -> ValuePilotHomeScreen(
                                    basketCount = ui.privateIdentities.size,
                                    areaLabel = ui.search.areaLabel,
                                    onSearch = { navigate(NextDestination.SEARCH) },
                                    onBasket = { selectPrimary(NextDestination.BASKET) },
                                    onCompare = onOpenComparison
                                )

                                NextDestination.SEARCH -> ValuePilotSearchScreen(
                                    query = query,
                                    state = ui.search,
                                    onQueryChanged = ::onQueryChanged,
                                    onSubmit = ::submitSearch,
                                    onLoadMore = journey::loadMoreSearchResults,
                                    onOpenMatch = { match ->
                                        selectedIdentityKey = match.identityKey
                                        navigate(NextDestination.PRODUCT_DETAIL)
                                    }
                                )

                                NextDestination.PRODUCT_DETAIL -> {
                                    val match = ui.search.presentation?.matches
                                        ?.firstOrNull { it.identityKey == selectedIdentityKey }
                                    ValuePilotProductDetailScreen(
                                        match = match,
                                        isSearchLoading = ui.search.mode ==
                                            com.valuepilot.app.ArgentinaLiveSearchSurfaceMode.LOADING,
                                        onAddToBasket = { selected ->
                                            when (journey.addSearchMatch(selected)) {
                                                AddPrivateIdentityResult.ADDED -> {
                                                    notify("Added to your private basket.")
                                                    selectPrimary(NextDestination.BASKET)
                                                }
                                                AddPrivateIdentityResult.REFRESHED -> {
                                                    notify("Current Search evidence refreshed.")
                                                    selectPrimary(NextDestination.BASKET)
                                                }
                                                AddPrivateIdentityResult.SOURCE_UNAVAILABLE ->
                                                    notify("This result has no qualified source identity to save.")
                                                AddPrivateIdentityResult.BASKET_LIMIT ->
                                                    notify("Your private basket has reached its item limit.")
                                                AddPrivateIdentityResult.PERSISTENCE_UNAVAILABLE ->
                                                    notify("The private basket could not be saved.")
                                            }
                                        },
                                        onSearchAgain = { selectPrimary(NextDestination.SEARCH) },
                                        onOpenMapContext = { offer ->
                                            val context =
                                                com.valuepilot.app.ArgentinaQualifiedStoreMapContextPresentation
                                                    .fromOffer(offer, match?.displayName.orEmpty())
                                            if (context.latitude != null && context.longitude != null) {
                                                selectedMapOfferKey = offer.offerKey
                                                selectedMapProductName = match?.displayName
                                                navigate(NextDestination.MAP_HANDOFF)
                                                notify("Store coordinates are source context, not a route.")
                                            } else {
                                                notify("This offer has no verified map coordinates.")
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
                                            notify("The saved basket could not be updated.")
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
                                            notify("No map app is available for this store.")
                                        }
                                    }
                                )

                                NextDestination.SAVED -> ValuePilotSavedScreen(
                                    state = ui.saved,
                                    onAction = journey::onSavedSurfaceAction,
                                    onCheckPrice = onCheckSavedPrice,
                                    onSearch = { selectPrimary(NextDestination.SEARCH) },
                                    onRecordObservedPrice = {
                                        navigate(NextDestination.OBSERVED_PRICE_SELECTION)
                                    },
                                    onOpenSavedTools = onOpenSavedTools
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
                                                ?.let(onOpenObservedPriceConfirmation)
                                        },
                                        onBackToSaved = ::navigateBack
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

@Composable
private fun JourneyTopBar(
    destination: NextDestination,
    canGoBack: Boolean,
    onBack: () -> Unit,
    onExitPreview: () -> Unit
) {
    val title = when (destination) {
        NextDestination.HOME -> "ValuePilot"
        NextDestination.SEARCH -> "Search"
        NextDestination.BASKET -> "Your basket"
        NextDestination.SAVED -> "Saved"
        NextDestination.OBSERVED_PRICE_SELECTION -> "Observed price"
        NextDestination.PRODUCT_DETAIL -> "Product detail"
        NextDestination.SHOPPING_DECISION -> "Shopping decision"
        NextDestination.TRIP_PLANNER -> "Trip planner"
        NextDestination.MAP_HANDOFF -> "Store context"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ValuePilotSpacing.xLarge, vertical = ValuePilotSpacing.small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ValuePilotSpacing.medium)
    ) {
        if (canGoBack) {
            androidx.compose.material3.TextButton(
                onClick = onBack,
                modifier = Modifier.size(ValuePilotSpacing.minimumTouchTarget)
            ) {
                Text("‹", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
            }
        } else {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Text("V", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.titleLarge)
            }
        }

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
            Text(
                "SHOPPING INTELLIGENCE",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.tertiary,
                fontSize = 10.sp
            )
        }
        androidx.compose.material3.TextButton(
            onClick = onExitPreview,
            modifier = Modifier
                .heightIn(min = ValuePilotSpacing.minimumTouchTarget)
                .widthIn(min = 64.dp)
        ) {
            Text("Current", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ValuePilotNavigationBar(
    selectedTab: AppPrimaryTab,
    onSelectTab: (AppPrimaryTab) -> Unit
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = ValuePilotElevation.flat
    ) {
        primaryDestinations.forEach { destination ->
            NavigationBarItem(
                selected = selectedTab == destination.tab,
                onClick = { onSelectTab(destination.tab) },
                icon = {
                    androidx.compose.material3.Icon(
                        painter = painterResource(destination.iconResource),
                        contentDescription = null
                    )
                },
                label = { Text(destination.label) },
                colors = NavigationBarItemDefaults.colors(
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

@Composable
private fun ValuePilotNavigationRail(
    selectedTab: AppPrimaryTab,
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
                label = { Text(destination.label) },
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
