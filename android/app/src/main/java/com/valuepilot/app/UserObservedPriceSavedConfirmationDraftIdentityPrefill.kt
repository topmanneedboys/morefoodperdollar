package com.valuepilot.app

import com.valuepilot.core.PracticalShoppingStoreIdentityScope

/**
 * Identity-only context that may initialize a future observed-price confirmation draft.
 *
 * These values are caller supplied and are not a statement that a complete confirmation exists.
 * The context deliberately excludes proof, price, observation/confirmation identifiers, timestamps,
 * evidence, quantity, freshness, ranking, current-price status, route intent, and submission state.
 * Semantic confirmation validation remains downstream and unchanged.
 */
internal data class UserObservedPriceConfirmationDraftIdentityPrefill(
    val rawGtin: String,
    val productName: String,
    val storeScope: PracticalShoppingStoreIdentityScope
)

/**
 * Pure adapter from one already-checked Saved prefill attempt to generic confirmation-draft identity
 * context.
 *
 * Only an accepted attempt is adapted. Rejected selection-readiness and downstream prefill blockers
 * fail closed as null. Accepted GTIN, product name, and exact store scope are copied unchanged; Saved
 * item/store keys and display-only store labels deliberately do not cross this boundary.
 *
 * This adapter does not open or mutate a route or draft, generate missing values, read a clock,
 * retain proof, submit a confirmation, create evidence, rank offers, persist data, or touch Android
 * or network APIs.
 */
internal object UserObservedPriceSavedConfirmationDraftIdentityPrefillAdapter {

    fun adapt(
        attempt: UserObservedPriceSavedPrefillHandoffAttempt
    ): UserObservedPriceConfirmationDraftIdentityPrefill? {
        val prefill = attempt.prefill ?: return null

        return UserObservedPriceConfirmationDraftIdentityPrefill(
            rawGtin = prefill.rawGtin,
            productName = prefill.productName,
            storeScope = prefill.storeScope
        )
    }
}
