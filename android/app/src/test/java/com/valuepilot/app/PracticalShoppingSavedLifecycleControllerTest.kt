package com.valuepilot.app

import com.valuepilot.core.EvidenceProviderId
import com.valuepilot.core.ShoppingItemKey
import com.valuepilot.core.SourceProductIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticalShoppingSavedLifecycleControllerTest {

    private val eggs = ShoppingItemKey("eggs")
    private val milk = ShoppingItemKey("milk")
    private val controller = PracticalShoppingSavedLifecycleController()

    @Test
    fun `refresh emits one load request and duplicate in flight refresh is ignored`() {
        val initial = controller.initialState()
        val started = controller.reduce(initial, PracticalShoppingSavedLifecycleIntent.Refresh)

        assertEquals(PracticalShoppingSavedLifecycleStatus.LOADING, started.state.status)
        assertEquals(1L, started.state.activeRequestId)
        assertEquals(2L, started.state.nextRequestId)
        assertEquals(PracticalShoppingSavedLifecycleWork.Load(1L), started.work)

        val duplicate = controller.reduce(started.state, PracticalShoppingSavedLifecycleIntent.Refresh)
        assertSame(started.state, duplicate.state)
        assertNull(duplicate.work)
    }

    @Test
    fun `successful load becomes ready with immutable projection`() {
        val started = controller.reduce(controller.initialState(), PracticalShoppingSavedLifecycleIntent.Refresh)
        val projection = projectionForEggs()

        val completed =
            controller.reduce(
                started.state,
                PracticalShoppingSavedLifecycleIntent.LoadCompleted(1L, acceptedLoad(projection))
            )

        assertEquals(PracticalShoppingSavedLifecycleStatus.READY, completed.state.status)
        assertEquals(projection, completed.state.projection)
        assertNull(completed.state.activeRequestId)
        assertNull(completed.state.failure)
        assertFalse(completed.state.displayMetadataDegraded)
        assertFalse(completed.state.displayCleanupDegraded)
        assertNull(completed.work)
    }

    @Test
    fun `display storage degradation remains usable but marks lifecycle degraded`() {
        val started = controller.reduce(controller.initialState(), PracticalShoppingSavedLifecycleIntent.Refresh)
        val projection = unresolvedProjectionForEggs()

        val completed =
            controller.reduce(
                started.state,
                PracticalShoppingSavedLifecycleIntent.LoadCompleted(
                    requestId = 1L,
                    result =
                        PracticalShoppingSavedExperienceLoadResult(
                            projection = projection,
                            exactState = exactState(),
                            displayStorageIssue = PracticalShoppingSavedDisplayMetadataStorageIssue.READ_FAILED
                        )
                )
            )

        assertEquals(PracticalShoppingSavedLifecycleStatus.DEGRADED, completed.state.status)
        assertEquals(projection, completed.state.projection)
        assertTrue(completed.state.displayMetadataDegraded)
        assertFalse(completed.state.displayCleanupDegraded)
        assertNull(completed.state.failure)
    }

    @Test
    fun `fatal exact load failure clears projection and supports explicit retry`() {
        val ready = readyState()
        val refreshed = controller.reduce(ready, PracticalShoppingSavedLifecycleIntent.Refresh)

        val failed =
            controller.reduce(
                refreshed.state,
                PracticalShoppingSavedLifecycleIntent.LoadCompleted(
                    requestId = requireNotNull(refreshed.state.activeRequestId),
                    result = failedLoad()
                )
            )

        assertEquals(PracticalShoppingSavedLifecycleStatus.ERROR, failed.state.status)
        assertEquals(PracticalShoppingSavedLifecycleFailure.LOAD_FAILED, failed.state.failure)
        assertNull(failed.state.projection)
        assertNull(failed.state.activeRequestId)

        val retry = controller.reduce(failed.state, PracticalShoppingSavedLifecycleIntent.Refresh)
        assertEquals(PracticalShoppingSavedLifecycleStatus.LOADING, retry.state.status)
        assertEquals(PracticalShoppingSavedLifecycleWork.Load(3L), retry.work)
    }

    @Test
    fun `only an action in the current projection can start mutation`() {
        val ready = readyState()
        val currentAction = ready.projection?.state?.productRows?.single()?.action
            ?: error("expected product action")

        val staleAction = PracticalShoppingSavedExactPreferenceUiAction.DeleteProduct(milk)
        val rejected = controller.reduce(ready, PracticalShoppingSavedLifecycleIntent.ActionSelected(staleAction))
        assertSame(ready, rejected.state)
        assertNull(rejected.work)

        val accepted = controller.reduce(ready, PracticalShoppingSavedLifecycleIntent.ActionSelected(currentAction))
        assertEquals(PracticalShoppingSavedLifecycleStatus.MUTATING, accepted.state.status)
        assertEquals(currentAction, accepted.state.pendingAction)
        assertEquals(2L, accepted.state.activeRequestId)
        assertEquals(PracticalShoppingSavedLifecycleWork.Mutate(2L, currentAction), accepted.work)
    }

    @Test
    fun `successful mutation always emits authoritative reload instead of patching old projection`() {
        val mutation = beginCurrentDelete()

        val completed =
            controller.reduce(
                mutation.state,
                PracticalShoppingSavedLifecycleIntent.ActionCompleted(
                    requestId = requireNotNull(mutation.state.activeRequestId),
                    result = acceptedAction()
                )
            )

        assertEquals(PracticalShoppingSavedLifecycleStatus.LOADING, completed.state.status)
        assertNull(completed.state.projection)
        assertNull(completed.state.pendingAction)
        assertEquals(3L, completed.state.activeRequestId)
        assertEquals(4L, completed.state.nextRequestId)
        assertEquals(PracticalShoppingSavedLifecycleWork.Load(3L), completed.work)
    }

    @Test
    fun `display cleanup failure cannot turn successful exact mutation into action failure`() {
        val mutation = beginCurrentDelete()

        val completed =
            controller.reduce(
                mutation.state,
                PracticalShoppingSavedLifecycleIntent.ActionCompleted(
                    requestId = requireNotNull(mutation.state.activeRequestId),
                    result =
                        PracticalShoppingSavedExperienceActionResult(
                            exactState = PracticalShoppingSavedExactPreferenceState.empty(),
                            displayCleanupIssue = PracticalShoppingSavedDisplayMetadataStorageIssue.WRITE_FAILED
                        )
                )
            )

        assertEquals(PracticalShoppingSavedLifecycleStatus.LOADING, completed.state.status)
        assertTrue(completed.state.displayCleanupDegraded)
        assertNull(completed.state.failure)

        val reloadProjection = emptyProjection()
        val reloaded =
            controller.reduce(
                completed.state,
                PracticalShoppingSavedLifecycleIntent.LoadCompleted(
                    requestId = requireNotNull(completed.state.activeRequestId),
                    result =
                        PracticalShoppingSavedExperienceLoadResult(
                            projection = reloadProjection,
                            exactState = PracticalShoppingSavedExactPreferenceState.empty()
                        )
                )
            )

        assertEquals(PracticalShoppingSavedLifecycleStatus.DEGRADED, reloaded.state.status)
        assertEquals(reloadProjection, reloaded.state.projection)
        assertTrue(reloaded.state.displayCleanupDegraded)
        assertFalse(reloaded.state.displayMetadataDegraded)

        val explicitRefresh = controller.reduce(reloaded.state, PracticalShoppingSavedLifecycleIntent.Refresh)
        assertFalse(explicitRefresh.state.displayCleanupDegraded)
    }

    @Test
    fun `exact mutation failure clears possibly stale projection and becomes retryable error`() {
        val mutation = beginCurrentDelete()

        val failed =
            controller.reduce(
                mutation.state,
                PracticalShoppingSavedLifecycleIntent.ActionCompleted(
                    requestId = requireNotNull(mutation.state.activeRequestId),
                    result = failedAction()
                )
            )

        assertEquals(PracticalShoppingSavedLifecycleStatus.ERROR, failed.state.status)
        assertEquals(PracticalShoppingSavedLifecycleFailure.ACTION_FAILED, failed.state.failure)
        assertNull(failed.state.projection)
        assertNull(failed.state.activeRequestId)
        assertNull(failed.state.pendingAction)

        val retry = controller.reduce(failed.state, PracticalShoppingSavedLifecycleIntent.Refresh)
        assertEquals(PracticalShoppingSavedLifecycleStatus.LOADING, retry.state.status)
        assertTrue(retry.work is PracticalShoppingSavedLifecycleWork.Load)
    }

    @Test
    fun `stale load and mutation completions cannot replace newer lifecycle state`() {
        val ready = readyState()
        val refreshed = controller.reduce(ready, PracticalShoppingSavedLifecycleIntent.Refresh)

        val staleLoad =
            controller.reduce(
                refreshed.state,
                PracticalShoppingSavedLifecycleIntent.LoadCompleted(
                    requestId = 1L,
                    result = acceptedLoad(emptyProjection(), PracticalShoppingSavedExactPreferenceState.empty())
                )
            )
        assertSame(refreshed.state, staleLoad.state)
        assertNull(staleLoad.work)

        val currentAction = ready.projection?.state?.productRows?.single()?.action
            ?: error("expected product action")
        val mutation = controller.reduce(ready, PracticalShoppingSavedLifecycleIntent.ActionSelected(currentAction))
        val staleMutation =
            controller.reduce(
                mutation.state,
                PracticalShoppingSavedLifecycleIntent.ActionCompleted(
                    requestId = 1L,
                    result = acceptedAction()
                )
            )
        assertSame(mutation.state, staleMutation.state)
        assertNull(staleMutation.work)
    }

    @Test
    fun `clear all remains available when exact choices are unresolved and no named row is visible`() {
        val unresolvedProjection = unresolvedProjectionForEggs()
        val started = controller.reduce(controller.initialState(), PracticalShoppingSavedLifecycleIntent.Refresh)
        val ready =
            controller.reduce(
                started.state,
                PracticalShoppingSavedLifecycleIntent.LoadCompleted(1L, acceptedLoad(unresolvedProjection))
            ).state

        assertTrue(requireNotNull(ready.projection).state.productRows.isEmpty())
        assertEquals(1, ready.projection?.state?.unresolvedDisplayNameCount)

        val transition =
            controller.reduce(
                ready,
                PracticalShoppingSavedLifecycleIntent.ActionSelected(
                    PracticalShoppingSavedExactPreferenceUiAction.ClearAll
                )
            )

        assertEquals(PracticalShoppingSavedLifecycleStatus.MUTATING, transition.state.status)
        assertEquals(
            PracticalShoppingSavedLifecycleWork.Mutate(
                requestId = 2L,
                action = PracticalShoppingSavedExactPreferenceUiAction.ClearAll
            ),
            transition.work
        )
    }

    private fun readyState(): PracticalShoppingSavedLifecycleState {
        val started = controller.reduce(controller.initialState(), PracticalShoppingSavedLifecycleIntent.Refresh)
        return controller.reduce(
            started.state,
            PracticalShoppingSavedLifecycleIntent.LoadCompleted(1L, acceptedLoad(projectionForEggs()))
        ).state
    }

    private fun beginCurrentDelete(): PracticalShoppingSavedLifecycleTransition {
        val ready = readyState()
        val action = ready.projection?.state?.productRows?.single()?.action
            ?: error("expected product action")
        return controller.reduce(ready, PracticalShoppingSavedLifecycleIntent.ActionSelected(action))
    }

    private fun acceptedLoad(
        projection: PracticalShoppingSavedExactPreferenceUiProjection,
        state: PracticalShoppingSavedExactPreferenceState = exactState()
    ): PracticalShoppingSavedExperienceLoadResult =
        PracticalShoppingSavedExperienceLoadResult(
            projection = projection,
            exactState = state
        )

    private fun failedLoad(): PracticalShoppingSavedExperienceLoadResult =
        PracticalShoppingSavedExperienceLoadResult(
            projection = null,
            exactState = null,
            issue = PracticalShoppingSavedExperienceLoadIssue.EXACT_PREFERENCE_STORAGE_FAILURE,
            exactStorageIssue = PracticalShoppingSavedExactPreferenceStorageIssue.READ_FAILED
        )

    private fun acceptedAction(): PracticalShoppingSavedExperienceActionResult =
        PracticalShoppingSavedExperienceActionResult(
            exactState = PracticalShoppingSavedExactPreferenceState.empty()
        )

    private fun failedAction(): PracticalShoppingSavedExperienceActionResult =
        PracticalShoppingSavedExperienceActionResult(
            exactState = null,
            issue = PracticalShoppingSavedExperienceActionIssue.EXACT_PREFERENCE_MUTATION_FAILURE,
            exactStorageIssue = PracticalShoppingSavedExactPreferenceStorageIssue.READ_FAILED
        )

    private fun projectionForEggs(): PracticalShoppingSavedExactPreferenceUiProjection =
        PracticalShoppingSavedExactPreferenceUiProjector.project(
            savedState = exactState(),
            metadata =
                PracticalShoppingSavedExactPreferenceDisplayMetadata(
                    productDisplayNames = mapOf(eggs to "Example Eggs")
                )
        )

    private fun unresolvedProjectionForEggs(): PracticalShoppingSavedExactPreferenceUiProjection =
        PracticalShoppingSavedExactPreferenceUiProjector.project(
            savedState = exactState(),
            metadata = PracticalShoppingSavedExactPreferenceDisplayMetadata()
        )

    private fun emptyProjection(): PracticalShoppingSavedExactPreferenceUiProjection =
        PracticalShoppingSavedExactPreferenceUiProjector.project(
            savedState = PracticalShoppingSavedExactPreferenceState.empty(),
            metadata = PracticalShoppingSavedExactPreferenceDisplayMetadata()
        )

    private fun exactState(): PracticalShoppingSavedExactPreferenceState =
        requireNotNull(
            PracticalShoppingSavedExactPreferenceStateManager.load(
                PracticalShoppingSavedExactPreferenceDocument(
                    schemaVersion = PracticalShoppingSavedExactPreferenceStateManager.currentSchemaVersion,
                    productPreferences =
                        listOf(
                            PracticalShoppingSavedExactProductPreference(
                                itemKey = eggs,
                                providerId = EvidenceProviderId("open-food-facts"),
                                sourceIdentity = SourceProductIdentity(gtin = "036000291452"),
                                dataset = OpenFoodFactsPracticalShoppingIdentityAdapter.DATASET_NAMESPACE
                            )
                        ),
                    storePreferences = emptyList()
                )
            ).state
        )
}
