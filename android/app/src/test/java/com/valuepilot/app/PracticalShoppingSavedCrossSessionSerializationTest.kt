package com.valuepilot.app

import com.valuepilot.core.EvidenceProviderId
import com.valuepilot.core.ShoppingItemKey
import com.valuepilot.core.SourceProductIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticalShoppingSavedCrossSessionSerializationTest {

    @Test
    fun `shared worker orders old session mutation before new session load after recreation`() {
        val worker = SharedQueueScheduler()
        val oldOwner = QueueDispatcher()
        val newOwner = QueueDispatcher()
        val events = mutableListOf<String>()
        val oldGateway =
            RecordingGateway(
                name = "old",
                events = events,
                loadResult = acceptedUnresolvedLoad(),
                actionResult = acceptedEmptyAction()
            )
        val newGateway =
            RecordingGateway(
                name = "new",
                events = events,
                loadResult = acceptedEmptyLoad(),
                actionResult = acceptedEmptyAction()
            )
        val oldHost =
            PracticalShoppingSavedLifecycleHost(
                gateway = oldGateway,
                worker = worker,
                completionDispatcher = oldOwner,
                renderer = PracticalShoppingSavedLifecycleRenderer { }
            )

        // Establish an actionable old-session projection.
        oldHost.refresh()
        worker.runNext()
        oldOwner.runNext()
        assertEquals(PracticalShoppingSavedLifecycleStatus.READY, oldHost.currentState().status)
        assertEquals(
            PracticalShoppingSavedExactPreferenceUiAction.ClearAll,
            oldHost.currentState().projection?.state?.clearAllAction
        )
        events.clear()

        // Queue a real mutation, then close the old UI owner before the worker runs it.
        oldHost.selectAction(PracticalShoppingSavedExactPreferenceUiAction.ClearAll)
        assertEquals(1, worker.pendingCount)
        oldHost.close()

        // A recreated UI session shares the same serial worker and queues its first load behind it.
        val newHost =
            PracticalShoppingSavedLifecycleHost(
                gateway = newGateway,
                worker = worker,
                completionDispatcher = newOwner,
                renderer = PracticalShoppingSavedLifecycleRenderer { }
            )
        newHost.refresh()

        assertEquals(2, worker.pendingCount)
        assertEquals(PracticalShoppingSavedLifecycleStatus.LOADING, newHost.currentState().status)

        worker.runNext()
        assertEquals(listOf("old:action:CLEAR_ALL"), events)
        assertEquals(1, oldOwner.pendingCount)

        worker.runNext()
        assertEquals(listOf("old:action:CLEAR_ALL", "new:load"), events)
        assertEquals(1, newOwner.pendingCount)

        // The old completion is ignored after close; it cannot enqueue its post-mutation reload.
        oldOwner.runNext()
        assertTrue(oldHost.isClosed())
        assertEquals(0, worker.pendingCount)

        // The recreated session receives state produced after the older mutation executed.
        newOwner.runNext()
        assertEquals(PracticalShoppingSavedLifecycleStatus.READY, newHost.currentState().status)
        assertTrue(requireNotNull(newHost.currentState().projection).state.productRows.isEmpty())
    }

    private class RecordingGateway(
        private val name: String,
        private val events: MutableList<String>,
        private val loadResult: PracticalShoppingSavedExperienceLoadResult,
        private val actionResult: PracticalShoppingSavedExperienceActionResult
    ) : PracticalShoppingSavedExperienceGateway {
        override fun load(): PracticalShoppingSavedExperienceLoadResult {
            events += "$name:load"
            return loadResult
        }

        override fun handleAction(
            action: PracticalShoppingSavedExactPreferenceUiAction
        ): PracticalShoppingSavedExperienceActionResult {
            val actionName =
                when (action) {
                    is PracticalShoppingSavedExactPreferenceUiAction.DeleteProduct -> "DELETE_PRODUCT"
                    is PracticalShoppingSavedExactPreferenceUiAction.DeleteStore -> "DELETE_STORE"
                    PracticalShoppingSavedExactPreferenceUiAction.ClearAll -> "CLEAR_ALL"
                }
            events += "$name:action:$actionName"
            return actionResult
        }
    }

    private class SharedQueueScheduler : PracticalShoppingSavedWorkScheduler {
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
                                        itemKey = ShoppingItemKey("eggs"),
                                        providerId = EvidenceProviderId("open-food-facts"),
                                        sourceIdentity = SourceProductIdentity(gtin = "036000291452"),
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
