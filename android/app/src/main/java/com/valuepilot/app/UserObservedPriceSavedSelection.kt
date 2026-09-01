package com.valuepilot.app

import com.valuepilot.core.ShoppingItemKey
import com.valuepilot.core.ShoppingStoreKey

/**
 * Explicit Saved identities selected for a future observed-price confirmation entry point.
 *
 * Saved choices never become selected merely because they exist. This state carries only stable
 * Saved keys and owns no product/store display text, GTIN interpretation, price, proof, time, ids,
 * draft state, persistence, evidence, ranking, current-price status, Android lifecycle or route.
 */
internal data class UserObservedPriceSavedSelection(
    val itemKey: ShoppingItemKey?,
    val storeKey: ShoppingStoreKey?
) {
    companion object {
        fun initial(): UserObservedPriceSavedSelection =
            UserObservedPriceSavedSelection(
                itemKey = null,
                storeKey = null
            )
    }
}

/** Exact stable-key pair only; downstream boundaries still decide whether it can be used. */
internal data class UserObservedPriceSavedSelectionPair(
    val itemKey: ShoppingItemKey,
    val storeKey: ShoppingStoreKey
)

internal sealed interface UserObservedPriceSavedSelectionAction {
    data class SelectProduct(
        val itemKey: ShoppingItemKey
    ) : UserObservedPriceSavedSelectionAction

    data object ClearProduct : UserObservedPriceSavedSelectionAction

    data class SelectStore(
        val storeKey: ShoppingStoreKey
    ) : UserObservedPriceSavedSelectionAction

    data object ClearStore : UserObservedPriceSavedSelectionAction

    data object ClearSelection : UserObservedPriceSavedSelectionAction
}

internal enum class UserObservedPriceSavedSelectionIssue {
    PRODUCT_NOT_SAVED,
    STORE_NOT_SAVED
}

internal data class UserObservedPriceSavedSelectionTransition(
    val state: UserObservedPriceSavedSelection,
    val issue: UserObservedPriceSavedSelectionIssue? = null
) {
    val accepted: Boolean
        get() = issue == null
}

/**
 * Pure explicit-selection boundary between validated Saved exact identities and a future
 * observed-price entry flow.
 *
 * Trust rules:
 * - initial state is always empty; no first/only Saved choice is auto-selected;
 * - selection actions are accepted only for identities still present in exact Saved state;
 * - selecting a new product/store replaces only that side of the pair;
 * - reconciliation may only clear choices that are no longer Saved and never selects additions;
 * - a pair is exposed only when both explicit selections still belong to exact Saved state.
 *
 * Pair readiness is identity-only. It does not imply a GTIN-backed product, display-safe labels,
 * proof availability, a price observation, route availability or confirmation eligibility. Those
 * remain separate downstream responsibilities.
 */
internal object UserObservedPriceSavedSelectionReducer {

    fun initial(): UserObservedPriceSavedSelection = UserObservedPriceSavedSelection.initial()

    fun reconcile(
        previous: UserObservedPriceSavedSelection,
        savedState: PracticalShoppingSavedExactPreferenceState
    ): UserObservedPriceSavedSelection {
        val savedProducts = savedState.productPreferences.map { it.itemKey }.toSet()
        val savedStores = savedState.storePreferences.map { it.storeKey }.toSet()

        return UserObservedPriceSavedSelection(
            itemKey = previous.itemKey?.takeIf(savedProducts::contains),
            storeKey = previous.storeKey?.takeIf(savedStores::contains)
        )
    }

    fun reduce(
        previous: UserObservedPriceSavedSelection,
        savedState: PracticalShoppingSavedExactPreferenceState,
        action: UserObservedPriceSavedSelectionAction
    ): UserObservedPriceSavedSelectionTransition {
        val current = reconcile(previous, savedState)
        val savedProducts = savedState.productPreferences.map { it.itemKey }.toSet()
        val savedStores = savedState.storePreferences.map { it.storeKey }.toSet()

        return when (action) {
            is UserObservedPriceSavedSelectionAction.SelectProduct -> {
                if (action.itemKey !in savedProducts) {
                    UserObservedPriceSavedSelectionTransition(
                        state = current,
                        issue = UserObservedPriceSavedSelectionIssue.PRODUCT_NOT_SAVED
                    )
                } else {
                    UserObservedPriceSavedSelectionTransition(
                        state = current.copy(itemKey = action.itemKey)
                    )
                }
            }

            UserObservedPriceSavedSelectionAction.ClearProduct ->
                UserObservedPriceSavedSelectionTransition(
                    state = current.copy(itemKey = null)
                )

            is UserObservedPriceSavedSelectionAction.SelectStore -> {
                if (action.storeKey !in savedStores) {
                    UserObservedPriceSavedSelectionTransition(
                        state = current,
                        issue = UserObservedPriceSavedSelectionIssue.STORE_NOT_SAVED
                    )
                } else {
                    UserObservedPriceSavedSelectionTransition(
                        state = current.copy(storeKey = action.storeKey)
                    )
                }
            }

            UserObservedPriceSavedSelectionAction.ClearStore ->
                UserObservedPriceSavedSelectionTransition(
                    state = current.copy(storeKey = null)
                )

            UserObservedPriceSavedSelectionAction.ClearSelection ->
                UserObservedPriceSavedSelectionTransition(
                    state = UserObservedPriceSavedSelection.initial()
                )
        }
    }

    fun selectedPairOrNull(
        selection: UserObservedPriceSavedSelection,
        savedState: PracticalShoppingSavedExactPreferenceState
    ): UserObservedPriceSavedSelectionPair? {
        val current = reconcile(selection, savedState)
        return UserObservedPriceSavedSelectionPair(
            itemKey = current.itemKey ?: return null,
            storeKey = current.storeKey ?: return null
        )
    }
}
