package com.valuepilot.app

import com.valuepilot.core.PracticalShoppingProductIdentityCandidate
import com.valuepilot.core.PracticalShoppingStoreIdentityCandidate

/** Transaction-only failures above the already-typed local storage result. */
enum class PracticalShoppingSavedExactPreferenceTransactionIssue {
    PRODUCT_NOT_USER_CONFIRMED,
    STORE_NOT_USER_CONFIRMED,
    PRODUCT_IDENTITY_INVALID,
    PRODUCT_CAPACITY_REACHED,
    STORE_CAPACITY_REACHED,
    STORAGE_FAILURE
}

data class PracticalShoppingSavedExactPreferenceTransactionResult(
    val state: PracticalShoppingSavedExactPreferenceState?,
    val issue: PracticalShoppingSavedExactPreferenceTransactionIssue? = null,
    val storageIssue: PracticalShoppingSavedExactPreferenceStorageIssue? = null,
    val codecIssue: PracticalShoppingSavedExactPreferenceCodecIssue? = null,
    val documentIssues: Set<PracticalShoppingSavedExactPreferenceLoadIssue> = emptySet()
) {
    init {
        require((state != null) == (issue == null))
        require(issue == PracticalShoppingSavedExactPreferenceTransactionIssue.STORAGE_FAILURE || storageIssue == null)
        require(storageIssue != null || (codecIssue == null && documentIssues.isEmpty()))
    }

    val accepted: Boolean
        get() = state != null
}

/**
 * Transactional remember-choice boundary for saved exact Practical Shopping preferences.
 *
 * The API accepts the exact product/store candidate that the user action produced, not a
 * freely constructed saved record. It first reuses [PracticalShoppingSavedExactPreferenceAdapter]
 * so only USER_CONFIRMED exact relationships can be persisted. Catalog/OSM suggestions and
 * one-time exact barcode requests therefore cannot skip the confirmation boundary.
 *
 * Once admitted, the transaction synchronizes on the same
 * [PracticalShoppingSavedExactPreferenceLocalStore] object whose load/replace methods are
 * synchronized. JVM monitors are re-entrant, so the full
 * `load -> bounded state-manager upsert -> atomic replace` sequence is one critical section
 * for this store instance rather than an externally composable lost-update window.
 */
object PracticalShoppingSavedExactPreferenceTransactions {

    fun saveConfirmedProduct(
        store: PracticalShoppingSavedExactPreferenceLocalStore,
        confirmedCandidate: PracticalShoppingProductIdentityCandidate
    ): PracticalShoppingSavedExactPreferenceTransactionResult {
        val admitted = PracticalShoppingSavedExactPreferenceAdapter.saveProduct(confirmedCandidate)
        if (!admitted.accepted) {
            return PracticalShoppingSavedExactPreferenceTransactionResult(
                state = null,
                issue = PracticalShoppingSavedExactPreferenceTransactionIssue.PRODUCT_NOT_USER_CONFIRMED
            )
        }
        val preference = requireNotNull(admitted.preference)

        return synchronized(store) {
            val loaded = store.load()
            if (!loaded.accepted) {
                return@synchronized loaded.toTransactionFailure()
            }

            val current = requireNotNull(loaded.state)
            val mutation =
                try {
                    PracticalShoppingSavedExactPreferenceStateManager.upsertProduct(
                        state = current,
                        preference = preference
                    )
                } catch (_: IllegalArgumentException) {
                    return@synchronized PracticalShoppingSavedExactPreferenceTransactionResult(
                        state = null,
                        issue = PracticalShoppingSavedExactPreferenceTransactionIssue.PRODUCT_IDENTITY_INVALID
                    )
                }

            if (mutation.issue != null) {
                return@synchronized PracticalShoppingSavedExactPreferenceTransactionResult(
                    state = null,
                    issue = PracticalShoppingSavedExactPreferenceTransactionIssue.PRODUCT_CAPACITY_REACHED
                )
            }

            val updated = mutation.state
            if (updated == current) {
                return@synchronized PracticalShoppingSavedExactPreferenceTransactionResult(state = current)
            }

            store.replace(updated).toTransactionResult()
        }
    }

    fun saveConfirmedStore(
        store: PracticalShoppingSavedExactPreferenceLocalStore,
        confirmedCandidate: PracticalShoppingStoreIdentityCandidate
    ): PracticalShoppingSavedExactPreferenceTransactionResult {
        val admitted = PracticalShoppingSavedExactPreferenceAdapter.saveStore(confirmedCandidate)
        if (!admitted.accepted) {
            return PracticalShoppingSavedExactPreferenceTransactionResult(
                state = null,
                issue = PracticalShoppingSavedExactPreferenceTransactionIssue.STORE_NOT_USER_CONFIRMED
            )
        }
        val preference = requireNotNull(admitted.preference)

        return synchronized(store) {
            val loaded = store.load()
            if (!loaded.accepted) {
                return@synchronized loaded.toTransactionFailure()
            }

            val current = requireNotNull(loaded.state)
            val mutation =
                PracticalShoppingSavedExactPreferenceStateManager.upsertStore(
                    state = current,
                    preference = preference
                )

            if (mutation.issue != null) {
                return@synchronized PracticalShoppingSavedExactPreferenceTransactionResult(
                    state = null,
                    issue = PracticalShoppingSavedExactPreferenceTransactionIssue.STORE_CAPACITY_REACHED
                )
            }

            val updated = mutation.state
            if (updated == current) {
                return@synchronized PracticalShoppingSavedExactPreferenceTransactionResult(state = current)
            }

            store.replace(updated).toTransactionResult()
        }
    }

    private fun PracticalShoppingSavedExactPreferenceStorageLoadResult.toTransactionFailure():
        PracticalShoppingSavedExactPreferenceTransactionResult =
        PracticalShoppingSavedExactPreferenceTransactionResult(
            state = null,
            issue = PracticalShoppingSavedExactPreferenceTransactionIssue.STORAGE_FAILURE,
            storageIssue = requireNotNull(issue),
            codecIssue = codecIssue,
            documentIssues = documentIssues
        )

    private fun PracticalShoppingSavedExactPreferenceStorageMutationResult.toTransactionResult():
        PracticalShoppingSavedExactPreferenceTransactionResult =
        if (accepted) {
            PracticalShoppingSavedExactPreferenceTransactionResult(
                state = requireNotNull(state)
            )
        } else {
            PracticalShoppingSavedExactPreferenceTransactionResult(
                state = null,
                issue = PracticalShoppingSavedExactPreferenceTransactionIssue.STORAGE_FAILURE,
                storageIssue = requireNotNull(issue),
                codecIssue = codecIssue,
                documentIssues = documentIssues
            )
        }
}
