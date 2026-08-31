package com.valuepilot.app

/**
 * Pure foreground composition owner for one explicit Staple Watch policy setup lifecycle.
 *
 * Completed economic evidence is the authority for the draft's baseline money specification. The
 * latest exact evidence object and its fail-closed money-spec resolution are retained in memory,
 * but a draft route session is created only while the policy route is visible. Hide/show preserves
 * the same temporary draft for unchanged evidence.
 *
 * Every new non-identical evidence object closes and discards the previous draft before any new
 * session can be created. Exact duplicate object callbacks are idempotent. Blocked baseline-money
 * resolution creates no draft session.
 *
 * Merely completing a draft never emits policy. [onContinueAction] maps only the already-projected
 * explicit continuation marker to [requestPolicyHandoff]. A handoff is forwarded only when the
 * visible session supplies a completed finalization whose retained baseline assembly belongs to the
 * exact current evidence object. This boundary never chooses economic values, parses text, resolves
 * providers, evaluates savings, persists state, schedules work, or authorizes notifications.
 */
internal class StapleWatchPolicySetupCompositionCoordinator(
    private val policyObserver: StapleWatchPolicyObserver = StapleWatchPolicyObserver { },
    private val sessionFactory:
        (StapleWatchPolicyBaselineMoneySpec) -> StapleWatchPolicyDraftRouteSession
) : StapleWatchEconomicEvidencePreconditionsObserver, AutoCloseable {

    private var latestPreconditions: StapleWatchEconomicEvidencePreconditions? = null
    private var latestMoneySpec: StapleWatchPolicyBaselineMoneySpec? = null
    private var session: StapleWatchPolicyDraftRouteSession? = null
    private var routeVisible = false
    private var closed = false

    override fun onPreconditions(preconditions: StapleWatchEconomicEvidencePreconditions) {
        if (closed || preconditions === latestPreconditions) return

        session?.close()
        session = null
        latestPreconditions = preconditions
        latestMoneySpec = StapleWatchPolicyBaselineMoneySpecResolver.resolve(preconditions).moneySpec

        if (routeVisible) {
            ensureSession()?.onRouteVisibilityChanged(true)
        }
    }

    fun onRouteVisibilityChanged(visible: Boolean) {
        if (closed || visible == routeVisible) return

        routeVisible = visible
        if (visible) {
            ensureSession()?.onRouteVisibilityChanged(true)
        } else {
            session?.onRouteVisibilityChanged(false)
        }
    }

    fun onSurfaceAction(action: StapleWatchPolicyDraftUiAction) {
        if (closed || !routeVisible) return

        session?.onSurfaceAction(action)
    }

    fun onContinueAction(action: StapleWatchPolicyHandoffUiAction) {
        when (action) {
            StapleWatchPolicyHandoffUiAction.Request -> requestPolicyHandoff()
        }
    }

    fun requestPolicyHandoff() {
        if (closed || !routeVisible) return

        val preconditions = latestPreconditions ?: return
        val finalization = session?.currentFinalizationOrNull() ?: return
        if (finalization.draft.moneySpec.baselineAssembly.preconditions !== preconditions) return

        val policy = finalization.policy ?: return
        policyObserver.onPolicy(policy)
    }

    override fun close() {
        if (closed) return

        closed = true
        routeVisible = false
        latestPreconditions = null
        latestMoneySpec = null
        session?.close()
        session = null
    }

    fun isClosed(): Boolean = closed

    private fun ensureSession(): StapleWatchPolicyDraftRouteSession? {
        val moneySpec = latestMoneySpec ?: return null
        return session ?: sessionFactory(moneySpec).also { created -> session = created }
    }
}
