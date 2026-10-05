package com.valuepilot.app

/**
 * Permanent top-level destinations for the ValuePilot consumer application.
 *
 * These values describe navigation state only. They do not imply that Basket,
 * Saved, Search, or any future commerce capability is already implemented.
 */
enum class AppPrimaryTab {
    HOME,
    SEARCH,
    BASKET,
    SAVED
}

/**
 * Routes that can be rendered by a presentation.
 *
 * COMPARE is a standalone workflow layered above the four permanent primary tabs.
 * OBSERVED_PRICE_SAVED_SELECTION, OBSERVED_PRICE_CONFIRMATION_DRAFT, STAPLE_WATCH_SETUP,
 * STAPLE_WATCH_POLICY, CONFIRMED_PURCHASE_HISTORY, and PRICE_WATCHES are Saved-owned subroutes layered above
 * the Saved primary tab. TRIP_PLANNER is a Basket-owned subroute layered above Basket.
 * None of these workflows is a fifth primary tab.
 */
enum class AppRoute {
    HOME,
    SEARCH,
    BASKET,
    SAVED,
    OBSERVED_PRICE_SAVED_SELECTION,
    OBSERVED_PRICE_CONFIRMATION_DRAFT,
    STAPLE_WATCH_SETUP,
    STAPLE_WATCH_POLICY,
    CONFIRMED_PURCHASE_HISTORY,
    PRICE_WATCHES,
    TRIP_PLANNER,
    COMPARE
}

/**
 * Immutable application-level navigation state.
 *
 * Presentations render this state and emit [AppShellIntent] actions. They do
 * not own navigation rules.
 */
data class AppShellState(
    val selectedPrimaryTab: AppPrimaryTab,
    val route: AppRoute,
    val compareReturnTab: AppPrimaryTab?,
    val canNavigateBack: Boolean
) {
    companion object {
        fun initial(): AppShellState =
            AppShellState(
                selectedPrimaryTab = AppPrimaryTab.HOME,
                route = AppRoute.HOME,
                compareReturnTab = null,
                canNavigateBack = false
            )
    }
}

/**
 * Typed actions emitted by replaceable presentations.
 */
sealed interface AppShellIntent {
    data class SelectPrimary(
        val tab: AppPrimaryTab
    ) : AppShellIntent

    data object OpenObservedPriceSavedSelection : AppShellIntent

    data object OpenObservedPriceConfirmationDraft : AppShellIntent

    data object OpenStapleWatchSetup : AppShellIntent

    data object OpenStapleWatchPolicy : AppShellIntent

    data object OpenConfirmedPurchaseHistory : AppShellIntent

    data object OpenPriceWatches : AppShellIntent

    data object OpenTripPlanner : AppShellIntent

    data object OpenStandaloneCompare : AppShellIntent

    data object NavigateBack : AppShellIntent
}

/**
 * Pure deterministic navigation reducer.
 *
 * There is deliberately no Android dependency, back-stack object, unbounded
 * collection, clock, filesystem, network, or hidden mutable global state.
 */
object AppShellReducer {

    fun reduce(
        previous: AppShellState,
        intent: AppShellIntent
    ): AppShellState =
        when (intent) {
            is AppShellIntent.SelectPrimary ->
                selectPrimary(intent.tab)

            AppShellIntent.OpenObservedPriceSavedSelection ->
                openObservedPriceSavedSelection(previous)

            AppShellIntent.OpenObservedPriceConfirmationDraft ->
                openObservedPriceConfirmationDraft(previous)

            AppShellIntent.OpenStapleWatchSetup ->
                openStapleWatchSetup(previous)

            AppShellIntent.OpenStapleWatchPolicy ->
                openStapleWatchPolicy(previous)

            AppShellIntent.OpenConfirmedPurchaseHistory ->
                openConfirmedPurchaseHistory(previous)

            AppShellIntent.OpenPriceWatches ->
                openPriceWatches(previous)

            AppShellIntent.OpenTripPlanner ->
                openTripPlanner(previous)

            AppShellIntent.OpenStandaloneCompare ->
                openCompare(previous)

            AppShellIntent.NavigateBack ->
                navigateBack(previous)
        }

    private fun selectPrimary(
        tab: AppPrimaryTab
    ): AppShellState =
        AppShellState(
            selectedPrimaryTab = tab,
            route = routeFor(tab),
            compareReturnTab = null,
            canNavigateBack = false
        )

    private fun openObservedPriceSavedSelection(
        previous: AppShellState
    ): AppShellState {
        if (previous.route == AppRoute.OBSERVED_PRICE_SAVED_SELECTION) {
            return previous
        }
        if (
            previous.selectedPrimaryTab != AppPrimaryTab.SAVED ||
            previous.route != AppRoute.SAVED
        ) {
            return previous
        }

        return previous.copy(
            route = AppRoute.OBSERVED_PRICE_SAVED_SELECTION,
            compareReturnTab = null,
            canNavigateBack = true
        )
    }

    private fun openObservedPriceConfirmationDraft(
        previous: AppShellState
    ): AppShellState {
        if (previous.route == AppRoute.OBSERVED_PRICE_CONFIRMATION_DRAFT) {
            return previous
        }
        if (
            previous.selectedPrimaryTab != AppPrimaryTab.SAVED ||
            previous.route != AppRoute.OBSERVED_PRICE_SAVED_SELECTION
        ) {
            return previous
        }

        return previous.copy(
            route = AppRoute.OBSERVED_PRICE_CONFIRMATION_DRAFT,
            compareReturnTab = null,
            canNavigateBack = true
        )
    }

    private fun openStapleWatchSetup(
        previous: AppShellState
    ): AppShellState {
        if (previous.route == AppRoute.STAPLE_WATCH_SETUP) {
            return previous
        }
        if (
            previous.selectedPrimaryTab != AppPrimaryTab.SAVED ||
            previous.route != AppRoute.SAVED
        ) {
            return previous
        }

        return previous.copy(
            route = AppRoute.STAPLE_WATCH_SETUP,
            compareReturnTab = null,
            canNavigateBack = true
        )
    }

    private fun openStapleWatchPolicy(
        previous: AppShellState
    ): AppShellState {
        if (previous.route == AppRoute.STAPLE_WATCH_POLICY) {
            return previous
        }
        if (
            previous.selectedPrimaryTab != AppPrimaryTab.SAVED ||
            previous.route != AppRoute.STAPLE_WATCH_SETUP
        ) {
            return previous
        }

        return previous.copy(
            route = AppRoute.STAPLE_WATCH_POLICY,
            compareReturnTab = null,
            canNavigateBack = true
        )
    }

    private fun openConfirmedPurchaseHistory(
        previous: AppShellState
    ): AppShellState {
        if (previous.route == AppRoute.CONFIRMED_PURCHASE_HISTORY) return previous
        if (
            previous.selectedPrimaryTab != AppPrimaryTab.SAVED ||
            previous.route != AppRoute.SAVED
        ) {
            return previous
        }
        return previous.copy(
            route = AppRoute.CONFIRMED_PURCHASE_HISTORY,
            compareReturnTab = null,
            canNavigateBack = true
        )
    }

    private fun openPriceWatches(
        previous: AppShellState
    ): AppShellState {
        if (previous.route == AppRoute.PRICE_WATCHES) return previous
        if (
            previous.selectedPrimaryTab != AppPrimaryTab.SAVED ||
            previous.route != AppRoute.SAVED
        ) {
            return previous
        }
        return previous.copy(
            route = AppRoute.PRICE_WATCHES,
            compareReturnTab = null,
            canNavigateBack = true
        )
    }

    private fun openCompare(
        previous: AppShellState
    ): AppShellState {
        if (previous.route == AppRoute.COMPARE) {
            return previous
        }

        return previous.copy(
            route = AppRoute.COMPARE,
            compareReturnTab = previous.selectedPrimaryTab,
            canNavigateBack = true
        )
    }

    private fun openTripPlanner(
        previous: AppShellState
    ): AppShellState {
        if (previous.route == AppRoute.TRIP_PLANNER) return previous
        if (
            previous.selectedPrimaryTab != AppPrimaryTab.BASKET ||
            previous.route != AppRoute.BASKET
        ) {
            return previous
        }
        return previous.copy(
            route = AppRoute.TRIP_PLANNER,
            compareReturnTab = null,
            canNavigateBack = true
        )
    }

    private fun navigateBack(
        previous: AppShellState
    ): AppShellState =
        when (previous.route) {
            AppRoute.COMPARE -> {
                val returnTab =
                    previous.compareReturnTab
                        ?: previous.selectedPrimaryTab

                AppShellState(
                    selectedPrimaryTab = returnTab,
                    route = routeFor(returnTab),
                    compareReturnTab = null,
                    canNavigateBack = false
                )
            }

            AppRoute.OBSERVED_PRICE_CONFIRMATION_DRAFT ->
                AppShellState(
                    selectedPrimaryTab = AppPrimaryTab.SAVED,
                    route = AppRoute.OBSERVED_PRICE_SAVED_SELECTION,
                    compareReturnTab = null,
                    canNavigateBack = true
                )

            AppRoute.STAPLE_WATCH_POLICY ->
                AppShellState(
                    selectedPrimaryTab = AppPrimaryTab.SAVED,
                    route = AppRoute.STAPLE_WATCH_SETUP,
                    compareReturnTab = null,
                    canNavigateBack = true
                )

            AppRoute.CONFIRMED_PURCHASE_HISTORY ->
                selectPrimary(AppPrimaryTab.SAVED)

            AppRoute.PRICE_WATCHES ->
                selectPrimary(AppPrimaryTab.SAVED)

            AppRoute.TRIP_PLANNER ->
                selectPrimary(AppPrimaryTab.BASKET)

            AppRoute.OBSERVED_PRICE_SAVED_SELECTION,
            AppRoute.STAPLE_WATCH_SETUP ->
                selectPrimary(AppPrimaryTab.SAVED)

            else ->
                previous
        }

    fun routeFor(
        tab: AppPrimaryTab
    ): AppRoute =
        when (tab) {
            AppPrimaryTab.HOME ->
                AppRoute.HOME

            AppPrimaryTab.SEARCH ->
                AppRoute.SEARCH

            AppPrimaryTab.BASKET ->
                AppRoute.BASKET

            AppPrimaryTab.SAVED ->
                AppRoute.SAVED
        }
}

/**
 * Reconstructs only shell routes whose state is complete enough to survive an Android
 * recreation. Primary tabs are always safe to restore. The two stable You subroutes are
 * restored only when Saved was the selected tab; transient drafts and external workflows
 * intentionally fall back to the Saved landing surface instead of showing an incomplete
 * screen with missing inputs.
 */
object AppShellStateRestoration {

    fun restore(
        primaryTabName: String?,
        routeName: String?
    ): AppShellState {
        val tab =
            primaryTabName
                ?.let { runCatching { AppPrimaryTab.valueOf(it) }.getOrNull() }
                ?: AppPrimaryTab.HOME
        val base =
            AppShellReducer.reduce(
                AppShellState.initial(),
                AppShellIntent.SelectPrimary(tab)
            )
        if (tab != AppPrimaryTab.SAVED) return base

        return when (
            routeName?.let { runCatching { AppRoute.valueOf(it) }.getOrNull() }
        ) {
            AppRoute.CONFIRMED_PURCHASE_HISTORY ->
                AppShellReducer.reduce(base, AppShellIntent.OpenConfirmedPurchaseHistory)

            AppRoute.PRICE_WATCHES ->
                AppShellReducer.reduce(base, AppShellIntent.OpenPriceWatches)

            else ->
                base
        }
    }

    /**
     * Returns the route worth placing in an instance-state bundle. Drafts, dialogs,
     * comparison and external workflows are deliberately represented by their safe primary
     * tab on the next recreation.
     */
    fun restorableRoute(state: AppShellState): AppRoute =
        if (
            state.selectedPrimaryTab == AppPrimaryTab.SAVED &&
            state.route in setOf(
                AppRoute.SAVED,
                AppRoute.CONFIRMED_PURCHASE_HISTORY,
                AppRoute.PRICE_WATCHES
            )
        ) {
            state.route
        } else {
            AppShellReducer.routeFor(state.selectedPrimaryTab)
        }
}
