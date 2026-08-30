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
 * STAPLE_WATCH_SETUP is a Saved-owned subroute layered above the Saved primary tab.
 * Neither workflow is a fifth primary tab.
 */
enum class AppRoute {
    HOME,
    SEARCH,
    BASKET,
    SAVED,
    STAPLE_WATCH_SETUP,
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

    data object OpenStapleWatchSetup : AppShellIntent

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

            AppShellIntent.OpenStapleWatchSetup ->
                openStapleWatchSetup(previous)

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
