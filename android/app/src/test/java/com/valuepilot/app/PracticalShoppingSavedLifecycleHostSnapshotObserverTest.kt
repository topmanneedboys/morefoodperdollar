package com.valuepilot.app

import com.valuepilot.core.EvidenceProviderId
import com.valuepilot.core.ShoppingItemKey
import com.valuepilot.core.SourceProductIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticalShoppingSavedLifecycleHostSnapshotObserverTest {

    @Test
    fun `current accepted load notifies validated snapshot only through owner dispatcher`() {
        val worker = QueueScheduler()
        val owner = QueueDispatcher()
        val loaded = acceptedLoad(PracticalShoppingSavedExactPreferenceState.empty())
        val snapshots = mutableListOf<PracticalShoppingSavedValidatedSnapshot>()
        val rendered = mutableListOf<PracticalShoppingSavedLifecycleState>()
        val host =
            PracticalShoppingSavedLifecycleHost(
                gateway = RecordingGateway(loadResult = loaded),
                worker = worker,
                completionDispatcher = owner,
                renderer = PracticalShoppingSavedLifecycleRenderer(rendered::add),
                snapshotObserver = PracticalShoppingSavedValidatedSnapshotObserver(snapshots::add)
            )

        host.refresh()
        assertTrue(snapshots.isEmpty())

        worker.runNext()
        assertEquals(1, owner.pendingCount)
        assertTrue(snapshots.isEmpty())
        assertEquals(PracticalShoppingSavedLifecycleStatus.LOADING, host.currentState().status)

        owner.runNext()

        assertEquals(listOf(requireNotNull(loaded.validatedSnapshot)), snapshots)
        assertEquals(PracticalShoppingSavedLifecycleStatus.READY, host.currentState().status)
        assertEquals(
            listOf(
                PracticalShoppingSavedLifecycleStatus.LOADING,
                PracticalShoppingSavedLifecycleStatus.READY
            ),
            rendered.map { state -> state.status }
        )
    }

    @Test
    fun `failed load never notifies validated snapshot observer`() {
        val worker = QueueScheduler()
        val owner = QueueDispatcher()
        val snapshots = mutableListOf<PracticalShoppingSavedValidatedSnapshot>()
        val host =
            PracticalShoppingSavedLifecycleHost(
                gateway = RecordingGateway(loadResult = failedLoad()),
                worker = worker,
                completionDispatcher = owner,
                renderer = PracticalShoppingSavedLifecycleRenderer { },
                snapshotObserver = PracticalShoppingSavedValidatedSnapshotObserver(snapshots::add)
            )

        host.refresh()
        worker.runNext()
        owner.runNext()

        assertEquals(PracticalShoppingSavedLifecycleStatus.ERROR, host.currentState().status)
        assertTrue(snapshots.isEmpty())
    }

    @Test
    fun `close suppresses queued validated snapshot observation`() {
        val worker = QueueScheduler()
        val owner = QueueDispatcher()
        val snapshots = mutableListOf<PracticalShoppingSavedValidatedSnapshot>()
        val host =
            PracticalShoppingSavedLifecycleHost(
                gateway = RecordingGateway(
                    loadResult = acceptedLoad(PracticalShoppingSavedExactPreferenceState.empty())
                ),
                worker = worker,
                completionDispatcher = owner,
                renderer = PracticalShoppingSavedLifecycleRenderer { },
                snapshotObserver = PracticalShoppingSavedValidatedSnapshotObserver(snapshots::add)
            )

        host.refresh()
        worker.runNext()
        assertEquals(1, owner.pendingCount)

        host.close()
        owner.runNext()

        assertTrue(host.isClosed())
        assertEquals(PracticalShoppingSavedLifecycleStatus.LOADING, host.currentState().status)
        assertTrue(snapshots.isEmpty())
    }

    @Test
    fun `successful mutation emits no snapshot until authoritative reload completes`() {
        val worker = QueueScheduler()
        val owner = QueueDispatcher()
        val initialExact = exactStateWithEggs()
        val gateway =
            RecordingGateway(
                loadResult = acceptedLoad(initialExact),
                actionResult =
                    PracticalShoppingSavedExperienceActionResult(
                        exactState = PracticalShoppingSavedExactPreferenceState.empty()
                    )
            )
        val snapshots = mutableListOf<PracticalShoppingSavedValidatedSnapshot>()
        val host =
            PracticalShoppingSavedLifecycleHost(
                gateway = gateway,
                worker = worker,
                completionDispatcher = owner,
                renderer = PracticalShoppingSavedLifecycleRenderer { },
                snapshotObserver = PracticalShoppingSavedValidatedSnapshotObserver(snapshots::add)
            )

        host.refresh()
        worker.runNext()
        owner.runNext()
        assertEquals(1, snapshots.size)
        assertEquals(
            PracticalShoppingSavedExactPreferenceUiAction.ClearAll,
            host.currentState().projection?.state?.clearAllAction
        )

        host.selectAction(PracticalShoppingSavedExactPreferenceUiAction.ClearAll)
        worker.runNext()
        owner.runNext()

        assertEquals(PracticalShoppingSavedLifecycleStatus.LOADING, host.currentState().status)
        assertEquals(1, snapshots.size)
        assertEquals(1, worker.pendingCount)

        gateway.loadResult = acceptedLoad(PracticalShoppingSavedExactPreferenceState.empty())
        worker.runNext()
        assertEquals(1, snapshots.size)
        owner.runNext()

        assertEquals(PracticalShoppingSavedLifecycleStatus.READY, host.currentState().status)
        assertEquals(2, snapshots.size)
        assertTrue(snapshots.last().exactState.productPreferences.isEmpty())
    }

    @Test
    fun `physical saved renderer contract still receives lifecycle state only`() {
        val renderMethods =
            PracticalShoppingSavedLifecycleRenderer::class.java.declaredMethods
                .filter { method -> method.name == "render" }

        assertEquals(1, renderMethods.size)
        assertEquals(
            listOf(PracticalShoppingSavedLifecycleState::class.java),
            renderMethods.single().parameterTypes.toList()
        )
        assertFalse(
            PracticalShoppingSavedLifecycleState::class.java.declaredFields
                .any { field -> field.name == "validatedSnapshot" }
        )
    }

    private fun acceptedLoad(
        exact: PracticalShoppingSavedExactPreferenceState
    ): PracticalShoppingSavedExperienceLoadResult {
        val metadata = PracticalShoppingSavedExactPreferenceDisplayMetadata()
        return PracticalShoppingSavedExperienceLoadResult(
            projection =
                PracticalShoppingSavedExactPreferenceUiProjector.project(
                    savedState = exact,
                    metadata = metadata
                ),
            exactState = exact,
            validatedSnapshot =
                PracticalShoppingSavedValidatedSnapshot(
                    exactState = exact,
                    displayMetadata = metadata
                )
        )
    }

    private fun failedLoad(): PracticalShoppingSavedExperienceLoadResult =
        PracticalShoppingSavedExperienceLoadResult(
            projection = null,
            exactState = null,
            issue = PracticalShoppingSavedExperienceLoadIssue.EXACT_PREFERENCE_STORAGE_FAILURE,
            exactStorageIssue = PracticalShoppingSavedExactPreferenceStorageIssue.READ_FAILED
        )

    private fun exactStateWithEggs(): PracticalShoppingSavedExactPreferenceState =
        PracticalShoppingSavedExactPreferenceState(
            productPreferences =
                listOf(
                    PracticalShoppingSavedExactProductPreference(
                        itemKey = ShoppingItemKey("eggs"),
                        providerId = EvidenceProviderId("open-food-facts"),
                        sourceIdentity = SourceProductIdentity(gtin = "036000291452"),
                        dataset = OpenFoodFactsPracticalShoppingIdentityAdapter.DATASET_NAMESPACE
                    )
                ),
            storePreferences = emptyList()
        )

    private class RecordingGateway(
        var loadResult: PracticalShoppingSavedExperienceLoadResult,
        private val actionResult: PracticalShoppingSavedExperienceActionResult =
            PracticalShoppingSavedExperienceActionResult(
                exactState = PracticalShoppingSavedExactPreferenceState.empty()
            )
    ) : PracticalShoppingSavedExperienceGateway {
        override fun load(): PracticalShoppingSavedExperienceLoadResult = loadResult

        override fun handleAction(
            action: PracticalShoppingSavedExactPreferenceUiAction
        ): PracticalShoppingSavedExperienceActionResult = actionResult
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
}
