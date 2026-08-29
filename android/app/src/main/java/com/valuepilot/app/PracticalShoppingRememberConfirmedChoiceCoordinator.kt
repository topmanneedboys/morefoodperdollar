package com.valuepilot.app

import com.valuepilot.core.PracticalShoppingProductIdentityCandidate
import com.valuepilot.core.PracticalShoppingStoreIdentityCandidate

/**
 * Outcome of remembering one already-confirmed exact choice plus its detached display metadata.
 *
 * Exact-choice persistence is the primary result. Display metadata is deliberately secondary:
 * a label-admission or display-storage failure must not roll back an exact user confirmation.
 * The Saved binder/projector will therefore surface the exact preference as unresolved until a
 * safe matching label is available instead of falling back to a technical identifier.
 */
data class PracticalShoppingRememberConfirmedChoiceResult(
    val exactResult: PracticalShoppingSavedExactPreferenceTransactionResult,
    val displayFailures: Set<PracticalShoppingSavedDisplayMetadataFailure> = emptySet(),
    val displayResult: PracticalShoppingSavedDisplayMetadataTransactionResult? = null
) {
    init {
        if (!exactResult.accepted) {
            require(displayFailures.isEmpty())
            require(displayResult == null)
        } else {
            require(displayFailures.isNotEmpty() xor (displayResult != null))
        }
        require(displayFailures.isEmpty() || displayResult == null)
    }

    val exactSaved: Boolean
        get() = exactResult.accepted

    val displaySaved: Boolean
        get() = displayResult?.accepted == true

    val fullyLabeled: Boolean
        get() = exactSaved && displaySaved
}

/**
 * Application boundary for turning an already USER_CONFIRMED exact choice into Saved state.
 *
 * This coordinator does not confirm identity itself. Callers must first use the existing exact
 * product/store confirmation boundaries. The exact transaction re-checks that relationship, so
 * catalog suggestions, OSM suggestions, and one-time barcode requests cannot bypass explicit
 * confirmation even if they are passed here accidentally.
 *
 * The exact preference is persisted first. Only after that succeeds is a human label admitted
 * through the existing confirmation/source-bound display adapter and written through the
 * identity-bound display transaction. The two files intentionally remain non-atomic: if label
 * creation or storage fails, exact identity stays saved and the existing Saved projection safely
 * reports an unresolved display name. If exact identity later changes concurrently, the binder
 * withholds stale detached metadata on read.
 *
 * No name, source row, or display metadata grants product/store identity, price, availability,
 * currentness, rights, geography, travel, or ranking authority here.
 */
object PracticalShoppingRememberConfirmedChoiceCoordinator {

    fun rememberProductWithUserLabel(
        exactStore: PracticalShoppingSavedExactPreferenceLocalStore,
        displayStore: PracticalShoppingSavedDisplayMetadataLocalStore,
        confirmedCandidate: PracticalShoppingProductIdentityCandidate,
        displayName: String
    ): PracticalShoppingRememberConfirmedChoiceResult =
        rememberProduct(
            exactStore = exactStore,
            displayStore = displayStore,
            confirmedCandidate = confirmedCandidate,
            displayMetadata = {
                PracticalShoppingSavedExactPreferenceDisplayMetadataAdapter.userProductLabel(
                    confirmedCandidate = confirmedCandidate,
                    displayName = displayName
                )
            }
        )

    fun rememberOpenFoodFactsProduct(
        exactStore: PracticalShoppingSavedExactPreferenceLocalStore,
        displayStore: PracticalShoppingSavedDisplayMetadataLocalStore,
        confirmedCandidate: PracticalShoppingProductIdentityCandidate,
        row: OpenFoodFactsImportedProduct
    ): PracticalShoppingRememberConfirmedChoiceResult =
        rememberProduct(
            exactStore = exactStore,
            displayStore = displayStore,
            confirmedCandidate = confirmedCandidate,
            displayMetadata = {
                PracticalShoppingSavedExactPreferenceDisplayMetadataAdapter.openFoodFactsProductName(
                    confirmedCandidate = confirmedCandidate,
                    row = row
                )
            }
        )

    fun rememberStoreWithUserLabel(
        exactStore: PracticalShoppingSavedExactPreferenceLocalStore,
        displayStore: PracticalShoppingSavedDisplayMetadataLocalStore,
        confirmedCandidate: PracticalShoppingStoreIdentityCandidate,
        displayName: String
    ): PracticalShoppingRememberConfirmedChoiceResult =
        rememberStore(
            exactStore = exactStore,
            displayStore = displayStore,
            confirmedCandidate = confirmedCandidate,
            displayMetadata = {
                PracticalShoppingSavedExactPreferenceDisplayMetadataAdapter.userStoreLabel(
                    confirmedCandidate = confirmedCandidate,
                    displayName = displayName
                )
            }
        )

    fun rememberOpenStreetMapStore(
        exactStore: PracticalShoppingSavedExactPreferenceLocalStore,
        displayStore: PracticalShoppingSavedDisplayMetadataLocalStore,
        confirmedCandidate: PracticalShoppingStoreIdentityCandidate,
        row: OpenStreetMapPracticalShoppingStoreDisplayRecord
    ): PracticalShoppingRememberConfirmedChoiceResult =
        rememberStore(
            exactStore = exactStore,
            displayStore = displayStore,
            confirmedCandidate = confirmedCandidate,
            displayMetadata = {
                PracticalShoppingSavedExactPreferenceDisplayMetadataAdapter.openStreetMapStoreName(
                    confirmedCandidate = confirmedCandidate,
                    row = row
                )
            }
        )

    private fun rememberProduct(
        exactStore: PracticalShoppingSavedExactPreferenceLocalStore,
        displayStore: PracticalShoppingSavedDisplayMetadataLocalStore,
        confirmedCandidate: PracticalShoppingProductIdentityCandidate,
        displayMetadata: () -> PracticalShoppingSavedProductDisplayMetadataResult
    ): PracticalShoppingRememberConfirmedChoiceResult {
        val exact =
            PracticalShoppingSavedExactPreferenceTransactions.saveConfirmedProduct(
                store = exactStore,
                confirmedCandidate = confirmedCandidate
            )
        if (!exact.accepted) {
            return PracticalShoppingRememberConfirmedChoiceResult(exactResult = exact)
        }

        val metadata = displayMetadata()
        if (!metadata.accepted) {
            return PracticalShoppingRememberConfirmedChoiceResult(
                exactResult = exact,
                displayFailures = metadata.failures
            )
        }

        val display =
            PracticalShoppingSavedDisplayMetadataTransactions.saveProductEntry(
                store = displayStore,
                exactState = requireNotNull(exact.state),
                entry = requireNotNull(metadata.entry)
            )

        return PracticalShoppingRememberConfirmedChoiceResult(
            exactResult = exact,
            displayResult = display
        )
    }

    private fun rememberStore(
        exactStore: PracticalShoppingSavedExactPreferenceLocalStore,
        displayStore: PracticalShoppingSavedDisplayMetadataLocalStore,
        confirmedCandidate: PracticalShoppingStoreIdentityCandidate,
        displayMetadata: () -> PracticalShoppingSavedStoreDisplayMetadataResult
    ): PracticalShoppingRememberConfirmedChoiceResult {
        val exact =
            PracticalShoppingSavedExactPreferenceTransactions.saveConfirmedStore(
                store = exactStore,
                confirmedCandidate = confirmedCandidate
            )
        if (!exact.accepted) {
            return PracticalShoppingRememberConfirmedChoiceResult(exactResult = exact)
        }

        val metadata = displayMetadata()
        if (!metadata.accepted) {
            return PracticalShoppingRememberConfirmedChoiceResult(
                exactResult = exact,
                displayFailures = metadata.failures
            )
        }

        val display =
            PracticalShoppingSavedDisplayMetadataTransactions.saveStoreEntry(
                store = displayStore,
                exactState = requireNotNull(exact.state),
                entry = requireNotNull(metadata.entry)
            )

        return PracticalShoppingRememberConfirmedChoiceResult(
            exactResult = exact,
            displayResult = display
        )
    }
}
