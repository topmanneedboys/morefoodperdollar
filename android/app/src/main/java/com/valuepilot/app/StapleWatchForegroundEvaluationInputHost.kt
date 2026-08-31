package com.valuepilot.app

import com.valuepilot.core.StapleWatchPolicy

/**
 * Foreground-only owner for the exact inputs that may unlock one Staple Watch evaluation.
 *
 * Completed evidence starts a fresh immutable input session. Policy and display metadata must be
 * supplied explicitly for that same session; neither is carried across later evidence. Output
 * lifecycle changes are emitted through [outputObserver] without giving this host presentation,
 * persistence, background-work, or notification authority.
 */
internal class StapleWatchForegroundEvaluationInputHost(
    private val outputObserver: StapleWatchForegroundEvaluationOutputObserver =
        StapleWatchForegroundEvaluationOutputObserver { }
) :
    StapleWatchEconomicEvidencePreconditionsObserver,
    StapleWatchPolicyObserver,
    StapleWatchStoreDisplayMetadataObserver,
    AutoCloseable {

    private var currentSession: StapleWatchForegroundEvaluationInputSession? = null
    private var closed = false

    override fun onPreconditions(preconditions: StapleWatchEconomicEvidencePreconditions) {
        if (closed) return
        currentSession = StapleWatchForegroundEvaluationInputSession.start(preconditions)
        outputObserver.onOutputChanged(StapleWatchForegroundEvaluationOutput.Cleared)
    }

    override fun onPolicy(policy: StapleWatchPolicy) {
        accept(policy)
    }

    override fun onDisplayMetadata(metadata: StapleWatchStoreDisplayMetadata) {
        accept(metadata)
    }

    fun accept(policy: StapleWatchPolicy) {
        if (closed) return
        val session = currentSession ?: return
        val updated = session.withPolicy(policy)
        currentSession = updated
        publishEvaluation(updated)
    }

    fun accept(displayMetadata: StapleWatchStoreDisplayMetadata) {
        if (closed) return
        val session = currentSession ?: return
        val updated = session.withDisplayMetadata(displayMetadata)
        currentSession = updated
        publishEvaluation(updated)
    }

    fun currentSessionOrNull(): StapleWatchForegroundEvaluationInputSession? = currentSession

    fun isClosed(): Boolean = closed

    override fun close() {
        if (closed) return
        closed = true
        currentSession = null
    }

    private fun publishEvaluation(session: StapleWatchForegroundEvaluationInputSession) {
        session.evaluation?.let { evaluation ->
            outputObserver.onOutputChanged(
                StapleWatchForegroundEvaluationOutput.Evaluated(evaluation)
            )
        }
    }
}
