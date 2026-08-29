package com.valuepilot.app

import com.valuepilot.core.PracticalShoppingStoreIdentityCandidate
import com.valuepilot.core.PracticalShoppingStoreIdentityRelationship
import com.valuepilot.core.ShoppingStoreKey

enum class PracticalShoppingExactStoreConfirmationFailure {
    STORE_MISMATCH
}

data class PracticalShoppingExactStoreConfirmationResult(
    val candidate: PracticalShoppingStoreIdentityCandidate?,
    val failures: Set<PracticalShoppingExactStoreConfirmationFailure>
) {
    init {
        require((candidate != null) == failures.isEmpty())
    }

    val accepted: Boolean
        get() = candidate != null
}

/**
 * Network-free boundary for an explicit user choice of one already-proposed store scope.
 *
 * This adapter cannot discover a store and cannot derive merchant/location/channel keys
 * from names, addresses, coordinates, distance, routing, or prices. It only records that
 * the user selected [selectedCandidate] for the same logical [storeKey], preserving the
 * candidate's exact scope and source provenance unchanged.
 *
 * The shared-core store resolver remains the sole layer that turns this exact relationship
 * into an automatic store binding. Price evidence must still independently match the exact
 * merchant/location/channel scope downstream.
 */
object PracticalShoppingExactStoreConfirmationAdapter {

    fun confirmSelection(
        storeKey: ShoppingStoreKey,
        selectedCandidate: PracticalShoppingStoreIdentityCandidate,
        candidateId: String
    ): PracticalShoppingExactStoreConfirmationResult {
        if (selectedCandidate.storeKey != storeKey) {
            return PracticalShoppingExactStoreConfirmationResult(
                candidate = null,
                failures = setOf(PracticalShoppingExactStoreConfirmationFailure.STORE_MISMATCH)
            )
        }

        return PracticalShoppingExactStoreConfirmationResult(
            candidate =
                PracticalShoppingStoreIdentityCandidate(
                    candidateId = candidateId,
                    storeKey = storeKey,
                    scope = selectedCandidate.scope,
                    relationship = PracticalShoppingStoreIdentityRelationship.USER_CONFIRMED_EXACT_STORE,
                    providerId = selectedCandidate.providerId,
                    dataset = selectedCandidate.dataset
                ),
            failures = emptySet()
        )
    }
}
