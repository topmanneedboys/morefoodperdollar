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
 * COMPARE is a workflow layered above the four permanent primary tabs. It is
 * intentionally not a fifth primary tab.
 */
enum class AppRoute {
    HOME,
    SEARCH,
    BASKET,
    SAVED,
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
    ): AppShellState {
        if (previous.route != AppRoute.COMPARE) {
            return previous
        }

        val returnTab =
            previous.compareReturnTab
                ?: previous.selectedPrimaryTab

        return AppShellState(
            selectedPrimaryTab = returnTab,
            route = routeFor(returnTab),
            compareReturnTab = null,
            canNavigateBack = false
        )
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
