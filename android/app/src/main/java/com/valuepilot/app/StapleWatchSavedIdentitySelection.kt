package com.valuepilot.app

import com.valuepilot.core.ShoppingItemKey
import com.valuepilot.core.ShoppingRequest
import com.valuepilot.core.ShoppingStoreKey

private const val MIN_WATCHED_SAVED_ITEMS_FOR_HANDOFF = 2
private const val MAX_WATCHED_SAVED_ITEMS = 128

/**
 * Explicit user-selected identities for a future Watch My Staples workflow.
 *
 * Saved choices are only eligibility inputs: creating or reconciling this state never turns a
 * Saved product/store into a watched choice automatically. The state carries stable identities
 * only; it owns no price, travel, freshness, ranking, clock, persistence or notification authority.
 */
data class StapleWatchSavedIdentitySelection(
    val watchedItemKeys: List<ShoppingItemKey>,
    val usualStoreKey: ShoppingStoreKey?
) {
    init {
        require(watchedItemKeys.size <= MAX_WATCHED_SAVED_ITEMS)
        require(watchedItemKeys.distinct().size == watchedItemKeys.size)
    }

    companion object {
        fun initial(): StapleWatchSavedIdentitySelection =
            StapleWatchSavedIdentitySelection(
                watchedItemKeys = emptyList(),
                usualStoreKey = null
            )
    }
}

/**
 * Stable identity handoff for later fact resolution.
 *
 * This is deliberately not an economic decision and is not sufficient to notify. A later boundary
 * must still obtain validated basket prices, route burden and evidence freshness before shared-core
 * can evaluate whether a switch is worthwhile.
 */
data class StapleWatchSavedIdentityHandoff(
    val request: ShoppingRequest,
    val usualStoreKey: ShoppingStoreKey
)

sealed interface StapleWatchSavedIdentitySelectionAction {
    data class SetProductWatched(
        val itemKey: ShoppingItemKey,
        val watched: Boolean
    ) : StapleWatchSavedIdentitySelectionAction

    data class SelectUsualStore(
        val storeKey: ShoppingStoreKey
    ) : StapleWatchSavedIdentitySelectionAction

    data object ClearUsualStore : StapleWatchSavedIdentitySelectionAction

    data object ClearSelection : StapleWatchSavedIdentitySelectionAction
}

enum class StapleWatchSavedIdentitySelectionIssue {
    PRODUCT_NOT_SAVED,
    STORE_NOT_SAVED
}

data class StapleWatchSavedIdentitySelectionTransition(
    val state: StapleWatchSavedIdentitySelection,
    val issue: StapleWatchSavedIdentitySelectionIssue? = null
) {
    val accepted: Boolean
        get() = issue == null
}

/**
 * Pure selection/reconciliation boundary between Saved exact identities and Watch My Staples.
 *
 * Trust rules:
 * - Saved does not imply watched. [initial] is always empty.
 * - Product/store actions are accepted only for identities still present in validated Saved state.
 * - Reconciliation only removes choices that are no longer Saved; it never auto-selects additions.
 * - Watched item ordering is deterministic by stable key.
 * - At least two explicitly watched items plus one explicit usual store are required before an
 *   identity handoff can be created.
 * - No persisted Saved schema is changed and no Android/storage/network/background work occurs.
 */
object StapleWatchSavedIdentitySelectionReducer {

    fun initial(): StapleWatchSavedIdentitySelection = StapleWatchSavedIdentitySelection.initial()

    fun reconcile(
        previous: StapleWatchSavedIdentitySelection,
        savedState: PracticalShoppingSavedExactPreferenceState
    ): StapleWatchSavedIdentitySelection {
        val savedProducts = savedState.productPreferences.map { it.itemKey }.toSet()
        val savedStores = savedState.storePreferences.map { it.storeKey }.toSet()

        return StapleWatchSavedIdentitySelection(
            watchedItemKeys =
                previous.watchedItemKeys
                    .filter(savedProducts::contains)
                    .distinct()
                    .sortedBy { it.value },
            usualStoreKey = previous.usualStoreKey?.takeIf(savedStores::contains)
        )
    }

    fun reduce(
        previous: StapleWatchSavedIdentitySelection,
        savedState: PracticalShoppingSavedExactPreferenceState,
        action: StapleWatchSavedIdentitySelectionAction
    ): StapleWatchSavedIdentitySelectionTransition {
        val current = reconcile(previous, savedState)
        val savedProducts = savedState.productPreferences.map { it.itemKey }.toSet()
        val savedStores = savedState.storePreferences.map { it.storeKey }.toSet()

        return when (action) {
            is StapleWatchSavedIdentitySelectionAction.SetProductWatched -> {
                if (action.itemKey !in savedProducts) {
                    StapleWatchSavedIdentitySelectionTransition(
                        state = current,
                        issue = StapleWatchSavedIdentitySelectionIssue.PRODUCT_NOT_SAVED
                    )
                } else {
                    val updated =
                        if (action.watched) {
                            (current.watchedItemKeys + action.itemKey)
                                .distinct()
                                .sortedBy { it.value }
                        } else {
                            current.watchedItemKeys.filterNot { it == action.itemKey }
                        }

                    StapleWatchSavedIdentitySelectionTransition(
                        state = current.copy(watchedItemKeys = updated)
                    )
                }
            }

            is StapleWatchSavedIdentitySelectionAction.SelectUsualStore -> {
                if (action.storeKey !in savedStores) {
                    StapleWatchSavedIdentitySelectionTransition(
                        state = current,
                        issue = StapleWatchSavedIdentitySelectionIssue.STORE_NOT_SAVED
                    )
                } else {
                    StapleWatchSavedIdentitySelectionTransition(
                        state = current.copy(usualStoreKey = action.storeKey)
                    )
                }
            }

            StapleWatchSavedIdentitySelectionAction.ClearUsualStore ->
                StapleWatchSavedIdentitySelectionTransition(
                    state = current.copy(usualStoreKey = null)
                )

            StapleWatchSavedIdentitySelectionAction.ClearSelection ->
                StapleWatchSavedIdentitySelectionTransition(
                    state = StapleWatchSavedIdentitySelection.initial()
                )
        }
    }

    fun identityHandoffOrNull(
        selection: StapleWatchSavedIdentitySelection,
        savedState: PracticalShoppingSavedExactPreferenceState
    ): StapleWatchSavedIdentityHandoff? {
        val reconciled = reconcile(selection, savedState)
        val usualStore = reconciled.usualStoreKey ?: return null
        if (reconciled.watchedItemKeys.size < MIN_WATCHED_SAVED_ITEMS_FOR_HANDOFF) return null

        return StapleWatchSavedIdentityHandoff(
            request = ShoppingRequest(reconciled.watchedItemKeys),
            usualStoreKey = usualStore
        )
    }
}
