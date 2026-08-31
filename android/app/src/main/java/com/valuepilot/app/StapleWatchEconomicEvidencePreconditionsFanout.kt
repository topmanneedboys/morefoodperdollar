package com.valuepilot.app

/**
 * Transparent foreground fanout for one already-authoritative Staple Watch evidence object.
 *
 * The exact [StapleWatchEconomicEvidencePreconditions] instance is forwarded first to the
 * foreground evaluation-input boundary and then to explicit policy setup. This fanout deliberately
 * owns no deduplication or retained state; upstream lifecycle owners decide whether a callback is
 * new. It performs no copying, evidence evaluation, money resolution, policy construction,
 * rendering, Android lifecycle work, persistence, background work, evaluation, or delivery.
 */
internal class StapleWatchEconomicEvidencePreconditionsFanout(
    private val foregroundInputObserver: StapleWatchEconomicEvidencePreconditionsObserver,
    private val policySetupObserver: StapleWatchEconomicEvidencePreconditionsObserver
) : StapleWatchEconomicEvidencePreconditionsObserver {

    override fun onPreconditions(preconditions: StapleWatchEconomicEvidencePreconditions) {
        foregroundInputObserver.onPreconditions(preconditions)
        policySetupObserver.onPreconditions(preconditions)
    }
}
