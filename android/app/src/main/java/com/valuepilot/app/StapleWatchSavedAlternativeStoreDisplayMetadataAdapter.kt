package com.valuepilot.app

import com.valuepilot.core.PracticalShoppingProductionPriceStoreScope
import com.valuepilot.core.PracticalShoppingStoreIdentityScope

/**
 * Presentation-only bridge from validated Saved store names to one completed Staple Watch
 * evidence set.
 *
 * Stable store keys are not sufficient authority for this bridge because the same logical key may
 * later be re-confirmed to a different merchant/location/channel scope. A Saved name is therefore
 * carried forward only when the current exact Saved scope still matches the exact production price
 * scope retained by the completed evidence. Missing, unsaved, or re-confirmed stores are omitted.
 *
 * Only alternative-store names are emitted. Staple Watch consumer projection uses metadata for the
 * recommended alternative, and the fact model already permits 64 alternatives, which is the full
 * display-metadata capacity. This adapter does not acquire names, infer identity, evaluate prices,
 * choose policy, rank stores, render UI, persist state, schedule work, or authorize notifications.
 */
internal object StapleWatchSavedAlternativeStoreDisplayMetadataAdapter {

    fun adapt(
        snapshot: PracticalShoppingSavedValidatedSnapshot,
        preconditions: StapleWatchEconomicEvidencePreconditions
    ): StapleWatchStoreDisplayMetadata {
        val entries =
            preconditions.alternativeStorePriceFacts.productionStoreScopes.mapNotNull { scope ->
                val savedPreference = snapshot.exactState.storeFor(scope.storeKey)
                    ?: return@mapNotNull null
                if (!sameExactScope(savedPreference.scope, scope)) return@mapNotNull null

                val displayName = snapshot.displayMetadata.storeDisplayNames[scope.storeKey]
                    ?: return@mapNotNull null

                StapleWatchStoreDisplayMetadataEntry(
                    storeKey = scope.storeKey,
                    displayName = displayName
                )
            }

        return StapleWatchStoreDisplayMetadata(entries = entries)
    }

    private fun sameExactScope(
        savedScope: PracticalShoppingStoreIdentityScope,
        evidenceScope: PracticalShoppingProductionPriceStoreScope
    ): Boolean =
        savedScope.merchantKey == evidenceScope.merchantKey &&
            savedScope.locationKey == evidenceScope.locationKey &&
            savedScope.commerceChannelKey == evidenceScope.commerceChannelKey
}
