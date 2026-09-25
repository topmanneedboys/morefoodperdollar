package com.valuepilot.app

/** Executes Saved work away from the lifecycle owner thread. */
fun interface PracticalShoppingSavedWorkScheduler {
    fun schedule(block: () -> Unit)
}

/** Returns completed Saved work to the lifecycle owner thread. */
fun interface PracticalShoppingSavedCompletionDispatcher {
    fun dispatch(block: () -> Unit)
}

/** Receives immutable lifecycle state only. */
fun interface PracticalShoppingSavedLifecycleRenderer {
    fun render(state: PracticalShoppingSavedLifecycleState)
}

/**
 * Receives only a validated Saved snapshot emitted by a current accepted lifecycle load.
 *
 * This is a composition boundary, not a physical Saved-renderer contract. The snapshot may
 * contain exact Saved identity, so it must not be forwarded into consumer rendering state.
 */
fun interface PracticalShoppingSavedValidatedSnapshotObserver {
    fun onSnapshot(snapshot: PracticalShoppingSavedValidatedSnapshot)
}

/**
 * Testable gateway around the verified Saved persistence coordinator.
 *
 * Implementations must return the coordinator's typed results rather than inventing
 * presentation or exact-preference policy in the host.
 */
interface PracticalShoppingSavedExperienceGateway {
    fun load(): PracticalShoppingSavedExperienceLoadResult

    fun handleAction(
        action: PracticalShoppingSavedExactPreferenceUiAction
    ): PracticalShoppingSavedExperienceActionResult
}

class PracticalShoppingSavedLocalExperienceGateway(
    private val exactStore: PracticalShoppingSavedExactPreferenceLocalStore,
    private val displayStore: PracticalShoppingSavedDisplayMetadataLocalStore
) : PracticalShoppingSavedExperienceGateway {
    override fun load(): PracticalShoppingSavedExperienceLoadResult =
        PracticalShoppingSavedExperienceCoordinator.load(
            exactStore = exactStore,
            displayStore = displayStore
        )

    override fun handleAction(
        action: PracticalShoppingSavedExactPreferenceUiAction
    ): PracticalShoppingSavedExperienceActionResult =
        PracticalShoppingSavedExperienceCoordinator.handleAction(
            exactStore = exactStore,
            displayStore = displayStore,
            action = action
        )
}

/**
 * Execution boundary for the Saved lifecycle.
 *
 * The host owns sequencing only. It contains no Android View, filesystem, clock, network,
 * provider, product/store matching, or ranking logic. [worker] must execute its blocks away
 * from the Android main thread; [completionDispatcher] must return them to the lifecycle
 * owner thread before reducer state, rendering, or validated-snapshot observation is touched.
 *
 * Work is always derived from [PracticalShoppingSavedLifecycleController]. The host never
 * manufactures a persistence action and never patches a successful mutation into UI state;
 * the controller's follow-up authoritative load is scheduled like any other emitted work.
 *
 * [snapshotObserver] receives only the reducer's transient validated snapshot output. It does
 * not receive lifecycle state and is intentionally separate from the physical Saved renderer.
 *
 * [close] does not attempt to cancel an already-running atomic persistence operation. It
 * prevents subsequent events, renders, snapshot observations, and late completions from being
 * applied. A later Android owner may additionally shut down its executor when its lifecycle ends.
 */
class PracticalShoppingSavedLifecycleHost(
    private val gateway: PracticalShoppingSavedExperienceGateway,
    private val worker: PracticalShoppingSavedWorkScheduler,
    private val completionDispatcher: PracticalShoppingSavedCompletionDispatcher,
    private val renderer: PracticalShoppingSavedLifecycleRenderer,
    private val controller: PracticalShoppingSavedLifecycleController =
        PracticalShoppingSavedLifecycleController(),
    private val snapshotObserver: PracticalShoppingSavedValidatedSnapshotObserver =
        PracticalShoppingSavedValidatedSnapshotObserver { }
) {
    private var lifecycleState = controller.initialState()
    private var closed = false

    @Synchronized
    fun currentState(): PracticalShoppingSavedLifecycleState = lifecycleState

    @Synchronized
    fun refresh() {
        accept(PracticalShoppingSavedLifecycleIntent.Refresh)
    }

    @Synchronized
    fun selectAction(action: PracticalShoppingSavedExactPreferenceUiAction) {
        accept(PracticalShoppingSavedLifecycleIntent.ActionSelected(action))
    }

    @Synchronized
    fun close() {
        closed = true
    }

    @Synchronized
    fun isClosed(): Boolean = closed

    private fun accept(intent: PracticalShoppingSavedLifecycleIntent) {
        if (closed) return

        val previous = lifecycleState
        val transition = controller.reduce(previous, intent)
        lifecycleState = transition.state

        if (transition.state != previous) {
            renderer.render(transition.state)
        }

        transition.validatedSnapshot?.let(snapshotObserver::onSnapshot)
        transition.work?.let(::schedule)
    }

    private fun schedule(work: PracticalShoppingSavedLifecycleWork) {
        worker.schedule {
            val completion =
                when (work) {
                    is PracticalShoppingSavedLifecycleWork.Load ->
                        PracticalShoppingSavedLifecycleIntent.LoadCompleted(
                            requestId = work.requestId,
                            result = gateway.load()
                        )

                    is PracticalShoppingSavedLifecycleWork.Mutate ->
                        PracticalShoppingSavedLifecycleIntent.ActionCompleted(
                            requestId = work.requestId,
                            result = gateway.handleAction(work.action)
                        )
                }

            completionDispatcher.dispatch {
                synchronized(this@PracticalShoppingSavedLifecycleHost) {
                    accept(completion)
                }
            }
        }
    }
}
