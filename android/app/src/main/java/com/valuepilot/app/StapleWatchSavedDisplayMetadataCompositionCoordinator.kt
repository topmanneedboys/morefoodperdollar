package com.valuepilot.app

/** Receives consumer-safe Staple Watch store display metadata only. */
internal fun interface StapleWatchStoreDisplayMetadataObserver {
    fun onDisplayMetadata(metadata: StapleWatchStoreDisplayMetadata)
}

/**
 * Foreground-only fan-in between validated Saved presentation metadata and completed Watch facts.
 *
 * Saved snapshots and fact completion are independent streams, so callback order must not decide
 * which inputs belong together. This coordinator retains only the latest already-validated Saved
 * snapshot and latest completed evidence preconditions. A new evidence object is always forwarded
 * downstream first, then any metadata derived for that exact evidence is emitted. A later Saved
 * snapshot recomputes metadata against the retained evidence so a store re-confirmed to a different
 * exact scope loses its old label through the verified adapter rather than relabeling old evidence.
 *
 * Exact duplicate object callbacks are idempotent. This boundary owns no Saved persistence, fact
 * acquisition, policy, economics, provider/network work, clock, UI rendering, background work,
 * delivery, or notifications. Closing clears its in-memory pairing state but does not close either
 * downstream observer.
 */
internal class StapleWatchSavedDisplayMetadataCompositionCoordinator(
    private val preconditionsObserver: StapleWatchEconomicEvidencePreconditionsObserver =
        StapleWatchEconomicEvidencePreconditionsObserver { },
    private val displayMetadataObserver: StapleWatchStoreDisplayMetadataObserver =
        StapleWatchStoreDisplayMetadataObserver { }
) : PracticalShoppingSavedValidatedSnapshotObserver,
    StapleWatchEconomicEvidencePreconditionsObserver,
    AutoCloseable {

    private var latestSnapshot: PracticalShoppingSavedValidatedSnapshot? = null
    private var latestPreconditions: StapleWatchEconomicEvidencePreconditions? = null
    private var closed = false

    override fun onSnapshot(snapshot: PracticalShoppingSavedValidatedSnapshot) {
        if (closed || snapshot === latestSnapshot) return

        latestSnapshot = snapshot
        latestPreconditions?.let { preconditions ->
            emitMetadata(snapshot, preconditions)
        }
    }

    override fun onPreconditions(preconditions: StapleWatchEconomicEvidencePreconditions) {
        if (closed || preconditions === latestPreconditions) return

        latestPreconditions = preconditions
        preconditionsObserver.onPreconditions(preconditions)
        latestSnapshot?.let { snapshot ->
            emitMetadata(snapshot, preconditions)
        }
    }

    fun isClosed(): Boolean = closed

    override fun close() {
        if (closed) return

        closed = true
        latestSnapshot = null
        latestPreconditions = null
    }

    private fun emitMetadata(
        snapshot: PracticalShoppingSavedValidatedSnapshot,
        preconditions: StapleWatchEconomicEvidencePreconditions
    ) {
        displayMetadataObserver.onDisplayMetadata(
            StapleWatchSavedAlternativeStoreDisplayMetadataAdapter.adapt(
                snapshot = snapshot,
                preconditions = preconditions
            )
        )
    }
}
