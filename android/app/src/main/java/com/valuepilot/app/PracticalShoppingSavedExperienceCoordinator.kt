package com.valuepilot.app

import com.valuepilot.core.ShoppingItemKey
import com.valuepilot.core.ShoppingStoreKey

enum class PracticalShoppingSavedExperienceLoadIssue {
    EXACT_PREFERENCE_STORAGE_FAILURE
}

data class PracticalShoppingSavedExperienceLoadResult(
    val projection: PracticalShoppingSavedExactPreferenceUiProjection?,
    val exactState: PracticalShoppingSavedExactPreferenceState?,
    val issue: PracticalShoppingSavedExperienceLoadIssue? = null,
    val exactStorageIssue: PracticalShoppingSavedExactPreferenceStorageIssue? = null,
    val exactCodecIssue: PracticalShoppingSavedExactPreferenceCodecIssue? = null,
    val exactDocumentIssues: Set<PracticalShoppingSavedExactPreferenceLoadIssue> = emptySet(),
    val displayStorageIssue: PracticalShoppingSavedDisplayMetadataStorageIssue? = null,
    val displayCodecIssue: PracticalShoppingSavedExactPreferenceDisplayMetadataCodecIssue? = null,
    val staleDisplayProductKeys: List<ShoppingItemKey> = emptyList(),
    val staleDisplayStoreKeys: List<ShoppingStoreKey> = emptyList()
) {
    init {
        require((projection != null) == (exactState != null))
        require((projection != null) == (issue == null))
        require(
            issue == PracticalShoppingSavedExperienceLoadIssue.EXACT_PREFERENCE_STORAGE_FAILURE ||
                exactStorageIssue == null
        )
        require(exactStorageIssue != null || (exactCodecIssue == null && exactDocumentIssues.isEmpty()))
        require(displayStorageIssue != null || displayCodecIssue == null)
        require(staleDisplayProductKeys.distinct().size == staleDisplayProductKeys.size)
        require(staleDisplayStoreKeys.distinct().size == staleDisplayStoreKeys.size)
        require(issue == null || (displayStorageIssue == null && displayCodecIssue == null))
    }

    val accepted: Boolean
        get() = projection != null

    val displayMetadataDegraded: Boolean
        get() = displayStorageIssue != null
}

enum class PracticalShoppingSavedExperienceActionIssue {
    EXACT_PREFERENCE_MUTATION_FAILURE
}

data class PracticalShoppingSavedExperienceActionResult(
    val exactState: PracticalShoppingSavedExactPreferenceState?,
    val issue: PracticalShoppingSavedExperienceActionIssue? = null,
    val exactStorageIssue: PracticalShoppingSavedExactPreferenceStorageIssue? = null,
    val exactCodecIssue: PracticalShoppingSavedExactPreferenceCodecIssue? = null,
    val exactDocumentIssues: Set<PracticalShoppingSavedExactPreferenceLoadIssue> = emptySet(),
    val displayCleanupIssue: PracticalShoppingSavedDisplayMetadataStorageIssue? = null,
    val displayCleanupCodecIssue: PracticalShoppingSavedExactPreferenceDisplayMetadataCodecIssue? = null
) {
    init {
        require((exactState != null) == (issue == null))
        require(
            issue == PracticalShoppingSavedExperienceActionIssue.EXACT_PREFERENCE_MUTATION_FAILURE ||
                exactStorageIssue == null
        )
        require(exactStorageIssue != null || (exactCodecIssue == null && exactDocumentIssues.isEmpty()))
        require(displayCleanupIssue != null || displayCleanupCodecIssue == null)
        require(issue == null || (displayCleanupIssue == null && displayCleanupCodecIssue == null))
    }

    val accepted: Boolean
        get() = exactState != null

    val displayCleanupDegraded: Boolean
        get() = displayCleanupIssue != null
}

/**
 * Composite persistence/presentation coordinator for the Saved experience.
 *
 * These methods may perform local file I/O and must be called off the Android main thread by
 * the eventual lifecycle controller. This object itself owns no thread, UI, clock or network.
 *
 * Exact saved preferences are authoritative for whether a Saved choice exists. Therefore an
 * exact-store load/mutation failure is fatal for the operation. Display metadata is strictly
 * secondary: load failure degrades to an empty metadata snapshot, and cleanup failure after a
 * successful exact deletion is reported but never rolls back or resurrects the exact choice.
 *
 * The binder is always run before projection, so stale/orphan display metadata remains unable
 * to relabel a changed exact product/store or manufacture a Saved row.
 */
object PracticalShoppingSavedExperienceCoordinator {

    fun load(
        exactStore: PracticalShoppingSavedExactPreferenceLocalStore,
        displayStore: PracticalShoppingSavedDisplayMetadataLocalStore
    ): PracticalShoppingSavedExperienceLoadResult {
        val exact = exactStore.load()
        if (!exact.accepted) {
            return PracticalShoppingSavedExperienceLoadResult(
                projection = null,
                exactState = null,
                issue = PracticalShoppingSavedExperienceLoadIssue.EXACT_PREFERENCE_STORAGE_FAILURE,
                exactStorageIssue = requireNotNull(exact.issue),
                exactCodecIssue = exact.codecIssue,
                exactDocumentIssues = exact.documentIssues
            )
        }

        val exactState = requireNotNull(exact.state)
        val display = displayStore.load()
        val displaySnapshot =
            if (display.accepted) {
                requireNotNull(display.snapshot)
            } else {
                PracticalShoppingSavedExactPreferenceDisplayMetadataSnapshot()
            }

        val binding =
            PracticalShoppingSavedExactPreferenceDisplayMetadataBinder.bind(
                savedState = exactState,
                snapshot = displaySnapshot
            )
        val projection =
            PracticalShoppingSavedExactPreferenceUiProjector.project(
                savedState = exactState,
                metadata = binding.metadata
            )

        return PracticalShoppingSavedExperienceLoadResult(
            projection = projection,
            exactState = exactState,
            displayStorageIssue = display.issue,
            displayCodecIssue = display.codecIssue,
            staleDisplayProductKeys = binding.staleProductKeys,
            staleDisplayStoreKeys = binding.staleStoreKeys
        )
    }

    fun handleAction(
        exactStore: PracticalShoppingSavedExactPreferenceLocalStore,
        displayStore: PracticalShoppingSavedDisplayMetadataLocalStore,
        action: PracticalShoppingSavedExactPreferenceUiAction
    ): PracticalShoppingSavedExperienceActionResult {
        val exactMutation =
            PracticalShoppingSavedExactPreferenceUiActionHandler.handle(exactStore, action)
        if (!exactMutation.accepted) {
            return PracticalShoppingSavedExperienceActionResult(
                exactState = null,
                issue = PracticalShoppingSavedExperienceActionIssue.EXACT_PREFERENCE_MUTATION_FAILURE,
                exactStorageIssue = requireNotNull(exactMutation.issue),
                exactCodecIssue = exactMutation.codecIssue,
                exactDocumentIssues = exactMutation.documentIssues
            )
        }

        val displayCleanup =
            when (action) {
                is PracticalShoppingSavedExactPreferenceUiAction.DeleteProduct ->
                    displayStore.deleteProduct(action.itemKey)

                is PracticalShoppingSavedExactPreferenceUiAction.DeleteStore ->
                    displayStore.deleteStore(action.storeKey)

                PracticalShoppingSavedExactPreferenceUiAction.ClearAll ->
                    displayStore.clearAll()
            }

        return if (displayCleanup.accepted) {
            PracticalShoppingSavedExperienceActionResult(
                exactState = requireNotNull(exactMutation.state)
            )
        } else {
            PracticalShoppingSavedExperienceActionResult(
                exactState = requireNotNull(exactMutation.state),
                displayCleanupIssue = requireNotNull(displayCleanup.issue),
                displayCleanupCodecIssue = displayCleanup.codecIssue
            )
        }
    }
}
