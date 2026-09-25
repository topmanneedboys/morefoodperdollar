package com.valuepilot.app

import com.valuepilot.core.ShoppingItemKey
import com.valuepilot.core.ShoppingStoreKey

enum class PracticalShoppingSavedDisplayMetadataTransactionIssue {
    PRODUCT_NOT_CURRENT_EXACT_CHOICE,
    STORE_NOT_CURRENT_EXACT_CHOICE,
    STORAGE_FAILURE
}

data class PracticalShoppingSavedDisplayMetadataTransactionResult(
    val snapshot: PracticalShoppingSavedExactPreferenceDisplayMetadataSnapshot?,
    val issue: PracticalShoppingSavedDisplayMetadataTransactionIssue? = null,
    val storageIssue: PracticalShoppingSavedDisplayMetadataStorageIssue? = null,
    val codecIssue: PracticalShoppingSavedExactPreferenceDisplayMetadataCodecIssue? = null,
    val prunedStaleProductKeys: List<ShoppingItemKey> = emptyList(),
    val prunedStaleStoreKeys: List<ShoppingStoreKey> = emptyList()
) {
    init {
        require((snapshot != null) == (issue == null))
        require(
            issue == PracticalShoppingSavedDisplayMetadataTransactionIssue.STORAGE_FAILURE ||
                storageIssue == null
        )
        require(storageIssue != null || codecIssue == null)
        require(prunedStaleProductKeys.distinct().size == prunedStaleProductKeys.size)
        require(prunedStaleStoreKeys.distinct().size == prunedStaleStoreKeys.size)
    }

    val accepted: Boolean
        get() = snapshot != null
}

/**
 * Transactional upsert boundary for non-authoritative Saved display metadata.
 *
 * A new display entry is admitted only when the verified binder says its exact binding is
 * current in the supplied saved-exact state. Before a product upsert, stale/orphan product
 * metadata is pruned; before a store upsert, stale/orphan store metadata is pruned. Each
 * operation deliberately leaves the other category untouched, so a product-label write can
 * never delete store presentation metadata merely because a caller supplied partial state.
 *
 * The full `load -> category prune -> upsert -> AtomicFile replace` sequence synchronizes on
 * the same display-store instance, closing the lost-update window for callers sharing that
 * store. There is intentionally no cross-file transaction with exact-preference storage: if
 * exact state changes concurrently, the persisted label simply becomes stale and the binder
 * will withhold it on the next read.
 */
object PracticalShoppingSavedDisplayMetadataTransactions {

    fun saveProductEntry(
        store: PracticalShoppingSavedDisplayMetadataLocalStore,
        exactState: PracticalShoppingSavedExactPreferenceState,
        entry: PracticalShoppingSavedProductDisplayMetadataEntry
    ): PracticalShoppingSavedDisplayMetadataTransactionResult {
        if (!productEntryIsCurrent(exactState, entry)) {
            return PracticalShoppingSavedDisplayMetadataTransactionResult(
                snapshot = null,
                issue =
                    PracticalShoppingSavedDisplayMetadataTransactionIssue
                        .PRODUCT_NOT_CURRENT_EXACT_CHOICE
            )
        }

        return synchronized(store) {
            val loaded = store.load()
            if (!loaded.accepted) return@synchronized loaded.toTransactionFailure()

            val current = requireNotNull(loaded.snapshot)
            val pruned = pruneProductsAgainstExactState(exactState, current)
            val updated =
                pruned.snapshot.copy(
                    productEntries =
                        (pruned.snapshot.productEntries.filterNot { it.itemKey == entry.itemKey } + entry)
                            .sortedBy { it.itemKey.value }
                )

            if (updated == current) {
                return@synchronized PracticalShoppingSavedDisplayMetadataTransactionResult(
                    snapshot = current,
                    prunedStaleProductKeys = pruned.staleProductKeys
                )
            }

            store.replace(updated).toTransactionResult(pruned)
        }
    }

    fun saveStoreEntry(
        store: PracticalShoppingSavedDisplayMetadataLocalStore,
        exactState: PracticalShoppingSavedExactPreferenceState,
        entry: PracticalShoppingSavedStoreDisplayMetadataEntry
    ): PracticalShoppingSavedDisplayMetadataTransactionResult {
        if (!storeEntryIsCurrent(exactState, entry)) {
            return PracticalShoppingSavedDisplayMetadataTransactionResult(
                snapshot = null,
                issue =
                    PracticalShoppingSavedDisplayMetadataTransactionIssue
                        .STORE_NOT_CURRENT_EXACT_CHOICE
            )
        }

        return synchronized(store) {
            val loaded = store.load()
            if (!loaded.accepted) return@synchronized loaded.toTransactionFailure()

            val current = requireNotNull(loaded.snapshot)
            val pruned = pruneStoresAgainstExactState(exactState, current)
            val updated =
                pruned.snapshot.copy(
                    storeEntries =
                        (pruned.snapshot.storeEntries.filterNot { it.storeKey == entry.storeKey } + entry)
                            .sortedBy { it.storeKey.value }
                )

            if (updated == current) {
                return@synchronized PracticalShoppingSavedDisplayMetadataTransactionResult(
                    snapshot = current,
                    prunedStaleStoreKeys = pruned.staleStoreKeys
                )
            }

            store.replace(updated).toTransactionResult(pruned)
        }
    }

    private fun productEntryIsCurrent(
        exactState: PracticalShoppingSavedExactPreferenceState,
        entry: PracticalShoppingSavedProductDisplayMetadataEntry
    ): Boolean {
        val binding =
            PracticalShoppingSavedExactPreferenceDisplayMetadataBinder.bind(
                savedState = exactState,
                snapshot =
                    PracticalShoppingSavedExactPreferenceDisplayMetadataSnapshot(
                        productEntries = listOf(entry)
                    )
            )
        return !binding.hasStaleEntries &&
            binding.metadata.productDisplayNames[entry.itemKey] == entry.displayName
    }

    private fun storeEntryIsCurrent(
        exactState: PracticalShoppingSavedExactPreferenceState,
        entry: PracticalShoppingSavedStoreDisplayMetadataEntry
    ): Boolean {
        val binding =
            PracticalShoppingSavedExactPreferenceDisplayMetadataBinder.bind(
                savedState = exactState,
                snapshot =
                    PracticalShoppingSavedExactPreferenceDisplayMetadataSnapshot(
                        storeEntries = listOf(entry)
                    )
            )
        return !binding.hasStaleEntries &&
            binding.metadata.storeDisplayNames[entry.storeKey] == entry.displayName
    }

    private data class PrunedSnapshot(
        val snapshot: PracticalShoppingSavedExactPreferenceDisplayMetadataSnapshot,
        val staleProductKeys: List<ShoppingItemKey> = emptyList(),
        val staleStoreKeys: List<ShoppingStoreKey> = emptyList()
    )

    private fun pruneProductsAgainstExactState(
        exactState: PracticalShoppingSavedExactPreferenceState,
        snapshot: PracticalShoppingSavedExactPreferenceDisplayMetadataSnapshot
    ): PrunedSnapshot {
        val productOnly =
            PracticalShoppingSavedExactPreferenceDisplayMetadataSnapshot(
                productEntries = snapshot.productEntries
            )
        val binding =
            PracticalShoppingSavedExactPreferenceDisplayMetadataBinder.bind(exactState, productOnly)
        val staleProducts = binding.staleProductKeys.toSet()

        return PrunedSnapshot(
            snapshot =
                snapshot.copy(
                    productEntries =
                        snapshot.productEntries
                            .filterNot { it.itemKey in staleProducts }
                            .sortedBy { it.itemKey.value }
                ),
            staleProductKeys = binding.staleProductKeys
        )
    }

    private fun pruneStoresAgainstExactState(
        exactState: PracticalShoppingSavedExactPreferenceState,
        snapshot: PracticalShoppingSavedExactPreferenceDisplayMetadataSnapshot
    ): PrunedSnapshot {
        val storeOnly =
            PracticalShoppingSavedExactPreferenceDisplayMetadataSnapshot(
                storeEntries = snapshot.storeEntries
            )
        val binding =
            PracticalShoppingSavedExactPreferenceDisplayMetadataBinder.bind(exactState, storeOnly)
        val staleStores = binding.staleStoreKeys.toSet()

        return PrunedSnapshot(
            snapshot =
                snapshot.copy(
                    storeEntries =
                        snapshot.storeEntries
                            .filterNot { it.storeKey in staleStores }
                            .sortedBy { it.storeKey.value }
                ),
            staleStoreKeys = binding.staleStoreKeys
        )
    }

    private fun PracticalShoppingSavedDisplayMetadataStorageLoadResult.toTransactionFailure():
        PracticalShoppingSavedDisplayMetadataTransactionResult =
        PracticalShoppingSavedDisplayMetadataTransactionResult(
            snapshot = null,
            issue = PracticalShoppingSavedDisplayMetadataTransactionIssue.STORAGE_FAILURE,
            storageIssue = requireNotNull(issue),
            codecIssue = codecIssue
        )

    private fun PracticalShoppingSavedDisplayMetadataStorageMutationResult.toTransactionResult(
        pruned: PrunedSnapshot
    ): PracticalShoppingSavedDisplayMetadataTransactionResult =
        if (accepted) {
            PracticalShoppingSavedDisplayMetadataTransactionResult(
                snapshot = requireNotNull(snapshot),
                prunedStaleProductKeys = pruned.staleProductKeys,
                prunedStaleStoreKeys = pruned.staleStoreKeys
            )
        } else {
            PracticalShoppingSavedDisplayMetadataTransactionResult(
                snapshot = null,
                issue = PracticalShoppingSavedDisplayMetadataTransactionIssue.STORAGE_FAILURE,
                storageIssue = requireNotNull(issue),
                codecIssue = codecIssue,
                prunedStaleProductKeys = pruned.staleProductKeys,
                prunedStaleStoreKeys = pruned.staleStoreKeys
            )
        }
}
