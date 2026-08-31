package com.valuepilot.app

import com.valuepilot.core.StapleWatchPolicy

/**
 * Foreground-only owner for the exact inputs that may unlock one Staple Watch evaluation.
 *
 * Completed evidence starts a fresh immutable input session. Policy and display metadata must be
 * supplied explicitly for that same session; neither is carried across later evidence.
 */
internal class StapleWatchForegroundEvaluationInputHost :
    StapleWatchEconomicEvidencePreconditionsObserver,
    StapleWatchStoreDisplayMetadataObserver,
    AutoCloseable {

    private var currentSession: StapleWatchForegroundEvaluationInputSession? = null
    private var closed = false

    override fun onPreconditions(preconditions: StapleWatchEconomicEvidencePreconditions) {
        if (closed) return
        currentSession = StapleWatchForegroundEvaluationInputSession.start(preconditions)
    }

    override fun onDisplayMetadata(metadata: StapleWatchStoreDisplayMetadata) {
        accept(metadata)
    }

    fun accept(policy: StapleWatchPolicy) {
        if (closed) return
        val session = currentSession ?: return
        currentSession = session.withPolicy(policy)
    }

    fun accept(displayMetadata: StapleWatchStoreDisplayMetadata) {
        if (closed) return
        val session = currentSession ?: return
        currentSession = session.withDisplayMetadata(displayMetadata)
    }

    fun currentSessionOrNull(): StapleWatchForegroundEvaluationInputSession? = currentSession

    fun isClosed(): Boolean = closed

    override fun close() {
        if (closed) return
        closed = true
        currentSession = null
    }
}
