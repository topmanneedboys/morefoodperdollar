package com.valuepilot.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticalShoppingSavedLifecycleHostTest {

    @Test
    fun `refresh schedules persistence work and applies completion only through owner dispatcher`() {
        val worker = QueueScheduler()
        val owner = QueueDispatcher()
        val gateway = RecordingGateway(loadResult = acceptedEmptyLoad())
        val rendered = mutableListOf<PracticalShoppingSavedLifecycleState>()
        val host =
            PracticalShoppingSavedLifecycleHost(
                gateway = gateway,
                worker = worker,
                completionDispatcher = owner,
                renderer = PracticalShoppingSavedLifecycleRenderer(rendered::add)
            )

        host.refresh()

        assertEquals(PracticalShoppingSavedLifecycleStatus.LOADING, host.currentState().status)
        assertEquals(1, worker.pendingCount)
        assertEquals(0, owner.pendingCount)
        assertEquals(0, gateway.loadCalls)
        assertEquals(listOf(PracticalShoppingSavedLifecycleStatus.LOADING), rendered.map { it.status })

        worker.runNext()

        assertEquals(1, gateway.loadCalls)
        assertEquals(1, owner.pendingCount)
        assertEquals(PracticalShoppingSavedLifecycleStatus.LOADING, host.currentState().status)

        owner.runNext()

        assertEquals(PracticalShoppingSavedLifecycleStatus.READY, host.currentState().status)
        assertEquals(
            listOf(
                PracticalShoppingSavedLifecycleStatus.LOADING,
                PracticalShoppingSavedLifecycleStatus.READY
            ),
            rendered.map { it.status }
        )
    }

    @Test
    fun `duplicate refresh while load is in flight does not schedule duplicate work or render`() {
        val worker = QueueScheduler()
        val owner = QueueDispatcher()
        val rendered = mutableListOf<PracticalShoppingSavedLifecycleState>()
        val host =
            PracticalShoppingSavedLifecycleHost(
                gateway = RecordingGateway(loadResult = acceptedEmptyLoad()),
                worker = worker,
                completionDispatcher = owner,
                renderer = PracticalShoppingSavedLifecycleRenderer(rendered::add)
            )

        host.refresh()
        host.refresh()

        assertEquals(1, worker.pendingCount)
        assertEquals(1, rendered.size)
        assertEquals(PracticalShoppingSavedLifecycleStatus.LOADING, rendered.single().status)
    }

    @Test
    fun `current typed action mutates on worker then automatically schedules authoritative reload`() {
        val worker = QueueScheduler()
        val owner = QueueDispatcher()
        val action = PracticalShoppingSavedExactPreferenceUiAction.ClearAll
        val gateway =
            RecordingGateway(
                loadResult = acceptedEmptyLoad(),
                actionResult = acceptedEmptyAction()
            )
        val rendered = mutableListOf<PracticalShoppingSavedLifecycleState>()
        val host =
            PracticalShoppingSavedLifecycleHost(
                gateway = gateway,
                worker = worker,
                completionDispatcher = owner,
                renderer = PracticalShoppingSavedLifecycleRenderer(rendered::add)
            )

        // First load a projection whose exact saved state is non-empty enough to expose ClearAll.
        gateway.loadResult = acceptedUnresolvedLoad()
        host.refresh()
        worker.runNext()
        owner.runNext()
        assertEquals(PracticalShoppingSavedLifecycleStatus.READY, host.currentState().status)
        assertEquals(action, host.currentState().projection?.state?.clearAllAction)

        host.selectAction(action)

        assertEquals(PracticalShoppingSavedLifecycleStatus.MUTATING, host.currentState().status)
        assertEquals(1, worker.pendingCount)
        worker.runNext()

        assertEquals(listOf(action), gateway.actions)
        assertEquals(1, owner.pendingCount)
        owner.runNext()

        // Successful mutation completion must not patch old rows; it emits a new load.
        assertEquals(PracticalShoppingSavedLifecycleStatus.LOADING, host.currentState().status)
        assertEquals(1, worker.pendingCount)
        assertEquals(null, host.currentState().projection)

        gateway.loadResult = acceptedEmptyLoad()
        worker.runNext()
        owner.runNext()

        assertEquals(PracticalShoppingSavedLifecycleStatus.READY, host.currentState().status)
        assertTrue(requireNotNull(host.currentState().projection).state.productRows.isEmpty())
        assertEquals(2, gateway.loadCalls)
    }

    @Test
    fun `action not present in current projection cannot reach persistence gateway`() {
        val worker = QueueScheduler()
        val owner = QueueDispatcher()
        val gateway = RecordingGateway(loadResult = acceptedEmptyLoad())
        val rendered = mutableListOf<PracticalShoppingSavedLifecycleState>()
        val host =
            PracticalShoppingSavedLifecycleHost(
                gateway = gateway,
                worker = worker,
                completionDispatcher = owner,
                renderer = PracticalShoppingSavedLifecycleRenderer(rendered::add)
            )

        host.refresh()
        worker.runNext()
        owner.runNext()
        val renderCountBefore = rendered.size

        host.selectAction(PracticalShoppingSavedExactPreferenceUiAction.ClearAll)

        assertEquals(0, worker.pendingCount)
        assertTrue(gateway.actions.isEmpty())
        assertEquals(renderCountBefore, rendered.size)
        assertEquals(PracticalShoppingSavedLifecycleStatus.READY, host.currentState().status)
    }

    @Test
    fun `close prevents queued completion from mutating or rendering lifecycle state`() {
        val worker = QueueScheduler()
        val owner = QueueDispatcher()
        val gateway = RecordingGateway(loadResult = acceptedEmptyLoad())
        val rendered = mutableListOf<PracticalShoppingSavedLifecycleState>()
        val host =
            PracticalShoppingSavedLifecycleHost(
                gateway = gateway,
                worker = worker,
                completionDispatcher = owner,
                renderer = PracticalShoppingSavedLifecycleRenderer(rendered::add)
            )

        host.refresh()
        worker.runNext()
        assertEquals(1, owner.pendingCount)
        val stateAtClose = host.currentState()
        val rendersAtClose = rendered.size

        host.close()
        owner.runNext()

        assertTrue(host.isClosed())
        assertEquals(stateAtClose, host.currentState())
        assertEquals(rendersAtClose, rendered.size)

        host.refresh()
        assertEquals(0, worker.pendingCount)
        assertEquals(rendersAtClose, rendered.size)
    }

    @Test
    fun `display degradation from coordinator is preserved as lifecycle degradation`() {
        val worker = QueueScheduler()
        val owner = QueueDispatcher()
        val degraded =
            acceptedEmptyLoad().copy(
                displayStorageIssue = PracticalShoppingSavedDisplayMetadataStorageIssue.READ_FAILED
            )
        val host =
            PracticalShoppingSavedLifecycleHost(
                gateway = RecordingGateway(loadResult = degraded),
                worker = worker,
                completionDispatcher = owner,
                renderer = PracticalShoppingSavedLifecycleRenderer { }
            )

        host.refresh()
        worker.runNext()
        owner.runNext()

        assertEquals(PracticalShoppingSavedLifecycleStatus.DEGRADED, host.currentState().status)
        assertTrue(host.currentState().displayMetadataDegraded)
        assertFalse(host.currentState().displayCleanupDegraded)
    }

    private class RecordingGateway(
        var loadResult: PracticalShoppingSavedExperienceLoadResult,
        private val actionResult: PracticalShoppingSavedExperienceActionResult = acceptedEmptyAction()
    ) : PracticalShoppingSavedExperienceGateway {
        var loadCalls = 0
        val actions = mutableListOf<PracticalShoppingSavedExactPreferenceUiAction>()

        override fun load(): PracticalShoppingSavedExperienceLoadResult {
            loadCalls += 1
            return loadResult
        }

        override fun handleAction(
            action: PracticalShoppingSavedExactPreferenceUiAction
        ): PracticalShoppingSavedExperienceActionResult {
            actions += action
            return actionResult
        }
    }

    private class QueueScheduler : PracticalShoppingSavedWorkScheduler {
        private val queue = ArrayDeque<() -> Unit>()

        val pendingCount: Int
            get() = queue.size

        override fun schedule(block: () -> Unit) {
            queue.addLast(block)
        }

        fun runNext() {
            queue.removeFirst().invoke()
        }
    }

    private class QueueDispatcher : PracticalShoppingSavedCompletionDispatcher {
        private val queue = ArrayDeque<() -> Unit>()

        val pendingCount: Int
            get() = queue.size

        override fun dispatch(block: () -> Unit) {
            queue.addLast(block)
        }

        fun runNext() {
            queue.removeFirst().invoke()
        }
    }

    companion object {
        private fun acceptedEmptyLoad(): PracticalShoppingSavedExperienceLoadResult {
            val exact = PracticalShoppingSavedExactPreferenceState.empty()
            return PracticalShoppingSavedExperienceLoadResult(
                projection =
                    PracticalShoppingSavedExactPreferenceUiProjector.project(
                        savedState = exact,
                        metadata = PracticalShoppingSavedExactPreferenceDisplayMetadata()
                    ),
                exactState = exact
            )
        }

        private fun acceptedUnresolvedLoad(): PracticalShoppingSavedExperienceLoadResult {
            val exact =
                requireNotNull(
                    PracticalShoppingSavedExactPreferenceStateManager.load(
                        PracticalShoppingSavedExactPreferenceDocument(
                            schemaVersion = PracticalShoppingSavedExactPreferenceStateManager.currentSchemaVersion,
                            productPreferences =
                                listOf(
                                    PracticalShoppingSavedExactProductPreference(
                                        itemKey = com.valuepilot.core.ShoppingItemKey("eggs"),
                                        providerId = com.valuepilot.core.EvidenceProviderId("open-food-facts"),
                                        sourceIdentity = com.valuepilot.core.SourceProductIdentity(gtin = "036000291452"),
                                        dataset = OpenFoodFactsPracticalShoppingIdentityAdapter.DATASET_NAMESPACE
                                    )
                                ),
                            storePreferences = emptyList()
                        )
                    ).state
                )
            return PracticalShoppingSavedExperienceLoadResult(
                projection =
                    PracticalShoppingSavedExactPreferenceUiProjector.project(
                        savedState = exact,
                        metadata = PracticalShoppingSavedExactPreferenceDisplayMetadata()
                    ),
                exactState = exact
            )
        }

        private fun acceptedEmptyAction(): PracticalShoppingSavedExperienceActionResult =
            PracticalShoppingSavedExperienceActionResult(
                exactState = PracticalShoppingSavedExactPreferenceState.empty()
            )
    }
}
