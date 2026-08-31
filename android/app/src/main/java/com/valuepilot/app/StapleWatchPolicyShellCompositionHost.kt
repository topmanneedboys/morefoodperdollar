package com.valuepilot.app

/**
 * Pure shell-composition host for the explicit foreground Staple Watch policy route.
 *
 * This host assembles only already-defined typed boundaries:
 *
 * - authoritative economic preconditions fan out unchanged to foreground evaluation input and
 *   policy setup;
 * - policy-route availability is mapped to shell intents through the dedicated route adapter;
 * - policy draft presentation/session ownership stays inside the policy setup coordinator;
 * - an explicitly completed policy is forwarded only to the supplied [policyObserver].
 *
 * The host owns no Android view, raw text parsing, money resolution, policy values, economic
 * evaluation, provider/network access, persistence, background work, or notification authority.
 */
internal class StapleWatchPolicyShellCompositionHost(
    foregroundPreconditionsObserver: StapleWatchEconomicEvidencePreconditionsObserver,
    policyObserver: StapleWatchPolicyObserver,
    policyRenderer: StapleWatchPolicyDraftSurfaceRenderer,
    currentRoute: () -> AppRoute,
    emitShellIntent: (AppShellIntent) -> Unit
) : AutoCloseable {

    private val policyPresenter = StapleWatchPolicyDraftSurfacePresenter(policyRenderer)

    private val routeAvailabilityAdapter =
        StapleWatchPolicyRouteAvailabilityShellAdapter(
            currentRoute = currentRoute,
            emitIntent = emitShellIntent
        )

    private val policySetupCoordinator =
        StapleWatchPolicySetupCompositionCoordinator(
            policyObserver = policyObserver,
            routeAvailabilityObserver = routeAvailabilityAdapter,
            sessionFactory = { moneySpec ->
                StapleWatchPolicyDraftRouteSession(
                    moneySpec = moneySpec,
                    presenter = policyPresenter
                )
            }
        )

    val preconditionsObserver: StapleWatchEconomicEvidencePreconditionsObserver =
        StapleWatchEconomicEvidencePreconditionsFanout(
            foregroundObserver = foregroundPreconditionsObserver,
            policyObserver = policySetupCoordinator
        )

    fun onRouteVisibilityChanged(visible: Boolean) {
        policySetupCoordinator.onRouteVisibilityChanged(visible)
    }

    fun onSurfaceAction(action: StapleWatchPolicyDraftUiAction) {
        policySetupCoordinator.onSurfaceAction(action)
    }

    fun onContinueAction(action: StapleWatchPolicyHandoffUiAction) {
        policySetupCoordinator.onContinueAction(action)
    }

    override fun close() {
        policySetupCoordinator.close()
    }
}
