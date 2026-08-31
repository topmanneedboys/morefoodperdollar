package com.valuepilot.app

/**
 * Route-local memory owner for one explicit Staple Watch policy draft.
 *
 * The session starts from the verified baseline money specification with every economic choice
 * unanswered. It owns only temporary draft state, route visibility, typed consumer edits and
 * re-presentation. The immutable draft/finalizer remain the sole validation and policy-construction
 * authority.
 *
 * Invalid typed edits fail closed: the draft contract is allowed to reject them, this route catches
 * only that contract's [IllegalArgumentException], retains the previous draft and re-presents the
 * safe state. Hidden or closed routes ignore edits. A later composition owner may read a completed
 * finalization only while this route is visible; that read does not itself authorize evaluation,
 * persistence, background work or notification delivery.
 */
internal class StapleWatchPolicyDraftRouteSession(
    moneySpec: StapleWatchPolicyBaselineMoneySpec,
    private val presenter: StapleWatchPolicyDraftSurfacePresenter
) : AutoCloseable {

    private var draft: StapleWatchPolicyDraft? = StapleWatchPolicyDraft.start(moneySpec)
    private var routeVisible = false
    private var closed = false

    fun onRouteVisibilityChanged(visible: Boolean) {
        if (closed || visible == routeVisible) return

        routeVisible = visible
        if (visible) {
            renderCurrent()
        }
    }

    fun onSurfaceAction(action: StapleWatchPolicyDraftUiAction) {
        if (closed || !routeVisible) return

        val current = draft ?: return
        val updated =
            try {
                when (action) {
                    is StapleWatchPolicyDraftUiAction.SetMinimumSwitchSavingsMinorUnits ->
                        current.withMinimumSwitchSavingsMinorUnits(action.minorUnits)
                    is StapleWatchPolicyDraftUiAction.SetMaxAdditionalTravelSeconds ->
                        current.withMaxAdditionalTravelSeconds(action.seconds)
                    StapleWatchPolicyDraftUiAction.SetDistanceUnlimited ->
                        current.withDistanceLimit(StapleWatchPolicyDistanceLimitDraft.Unlimited)
                    is StapleWatchPolicyDraftUiAction.SetMaxAdditionalDistanceMetres ->
                        current.withDistanceLimit(
                            StapleWatchPolicyDistanceLimitDraft.AtMostMetres(action.metres)
                        )
                    is StapleWatchPolicyDraftUiAction.SetMinimumStapleItemCount ->
                        current.withMinimumStapleItemCount(action.count)
                }
            } catch (_: IllegalArgumentException) {
                current
            }

        draft = updated
        renderCurrent()
    }

    fun currentFinalizationOrNull(): StapleWatchPolicyDraftFinalization? {
        if (closed || !routeVisible) return null

        val current = draft ?: return null
        return StapleWatchPolicyDraftFinalizer.finalize(current).takeIf { it.finalized }
    }

    override fun close() {
        if (closed) return

        closed = true
        routeVisible = false
        draft = null
    }

    private fun renderCurrent() {
        val current = draft ?: return
        presenter.render(StapleWatchPolicyDraftFinalizer.finalize(current))
    }
}
