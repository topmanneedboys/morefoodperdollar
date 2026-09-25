package com.valuepilot.app

/**
 * Application action boundary for Saved preference mutations.
 *
 * A future renderer emits only [PracticalShoppingSavedExactPreferenceUiAction]. This
 * handler delegates every mutation to the already verified local persistence store;
 * it never edits an in-memory preference document directly and owns no UI or network.
 */
object PracticalShoppingSavedExactPreferenceUiActionHandler {

    fun handle(
        store: PracticalShoppingSavedExactPreferenceLocalStore,
        action: PracticalShoppingSavedExactPreferenceUiAction
    ): PracticalShoppingSavedExactPreferenceStorageMutationResult =
        when (action) {
            is PracticalShoppingSavedExactPreferenceUiAction.DeleteProduct ->
                store.deleteProduct(action.itemKey)

            is PracticalShoppingSavedExactPreferenceUiAction.DeleteStore ->
                store.deleteStore(action.storeKey)

            PracticalShoppingSavedExactPreferenceUiAction.ClearAll ->
                store.clearAll()
        }
}
