package com.valuepilot.core

/**
 * Namespace-wide storage/use disposition.
 *
 * This is deliberately separate from ProductionDatasetLifecycleRecord:
 * lifecycle is snapshot + activation-profile scoped, while this record applies
 * to the entire isolated evidence namespace. A revoked profile or snapshot must
 * never silently become a namespace-wide deletion instruction.
 */
enum class ProductionDatasetDispositionState {
    /** Dataset may remain stored and is not blocked by namespace policy alone. */
    RETAINED,

    /** Keep the namespace for audit/review, but block production use globally. */
    QUARANTINED,

    /** Block use globally and require explicit physical namespace withdrawal. */
    WITHDRAWAL_REQUIRED,

    /** Physical namespace withdrawal has been separately confirmed by the caller. */
    DELETED
}

data class ProductionDatasetDispositionRecord(
    val namespace: EvidenceDatasetNamespace,
    val revision: Long,
    val state: ProductionDatasetDispositionState,
    val basisId: String
) {
    init {
        require(revision > 0L)
        require(basisId.isNotBlank())
        require(basisId.length <= 240)
    }
}

enum class ProductionDatasetDispositionWriteResult {
    ADDED,
    UPDATED,
    DUPLICATE,
    REJECTED_STALE_REVISION,
    REJECTED_REVISION_COLLISION,
    REJECTED_NAMESPACE_METADATA_CHANGE,
    REJECTED_INVALID_TRANSITION,
    REJECTED_TERMINAL_DELETED
}

/**
 * Deterministic in-memory namespace-disposition prototype.
 *
 * Allowed state transitions:
 * - RETAINED <-> QUARANTINED
 * - RETAINED/QUARANTINED -> WITHDRAWAL_REQUIRED
 * - WITHDRAWAL_REQUIRED -> DELETED
 *
 * Direct deletion is rejected so deletion is always preceded by an explicit
 * withdrawal-required decision. DELETED is terminal for the same namespace id.
 */
class ProductionDatasetDispositionRegistry {
    private val records =
        linkedMapOf<String, ProductionDatasetDispositionRecord>()

    fun write(
        record: ProductionDatasetDispositionRecord
    ): ProductionDatasetDispositionWriteResult {
        val existing = records[record.namespace.id]

        if (existing == null) {
            if (record.state == ProductionDatasetDispositionState.DELETED) {
                return ProductionDatasetDispositionWriteResult.REJECTED_INVALID_TRANSITION
            }
            records[record.namespace.id] = record
            return ProductionDatasetDispositionWriteResult.ADDED
        }

        if (existing.namespace != record.namespace) {
            return ProductionDatasetDispositionWriteResult.REJECTED_NAMESPACE_METADATA_CHANGE
        }

        if (record.revision < existing.revision) {
            return ProductionDatasetDispositionWriteResult.REJECTED_STALE_REVISION
        }

        if (record.revision == existing.revision) {
            return if (record == existing) {
                ProductionDatasetDispositionWriteResult.DUPLICATE
            } else {
                ProductionDatasetDispositionWriteResult.REJECTED_REVISION_COLLISION
            }
        }

        if (existing.state == ProductionDatasetDispositionState.DELETED) {
            return ProductionDatasetDispositionWriteResult.REJECTED_TERMINAL_DELETED
        }

        if (!transitionAllowed(existing.state, record.state)) {
            return ProductionDatasetDispositionWriteResult.REJECTED_INVALID_TRANSITION
        }

        records[record.namespace.id] = record
        return ProductionDatasetDispositionWriteResult.UPDATED
    }

    fun currentRecord(
        namespaceId: String
    ): ProductionDatasetDispositionRecord? = records[namespaceId]

    fun records(): List<ProductionDatasetDispositionRecord> =
        records.values.sortedBy { it.namespace.id }

    fun size(): Int = records.size

    private fun transitionAllowed(
        from: ProductionDatasetDispositionState,
        to: ProductionDatasetDispositionState
    ): Boolean =
        when (from) {
            ProductionDatasetDispositionState.RETAINED ->
                to == ProductionDatasetDispositionState.RETAINED ||
                    to == ProductionDatasetDispositionState.QUARANTINED ||
                    to == ProductionDatasetDispositionState.WITHDRAWAL_REQUIRED

            ProductionDatasetDispositionState.QUARANTINED ->
                to == ProductionDatasetDispositionState.RETAINED ||
                    to == ProductionDatasetDispositionState.QUARANTINED ||
                    to == ProductionDatasetDispositionState.WITHDRAWAL_REQUIRED

            ProductionDatasetDispositionState.WITHDRAWAL_REQUIRED ->
                to == ProductionDatasetDispositionState.WITHDRAWAL_REQUIRED ||
                    to == ProductionDatasetDispositionState.DELETED

            ProductionDatasetDispositionState.DELETED -> false
        }
}

enum class ProductionDatasetUseBlocker {
    DISPOSITION_MISSING,
    NAMESPACE_SCOPE_MISMATCH,
    DATASET_QUARANTINED,
    DATASET_WITHDRAWAL_REQUIRED,
    DATASET_DELETED
}

data class ProductionDatasetUseDecision(
    val usableFromNamespacePolicy: Boolean,
    val blockers: Set<ProductionDatasetUseBlocker>
) {
    init {
        require(usableFromNamespacePolicy == blockers.isEmpty())
    }
}

/**
 * Namespace-wide use guard.
 *
 * RETAINED means only that namespace disposition does not block use. It does
 * NOT prove current production authorization, active snapshot lifecycle,
 * freshness, geography, rankability, or any other downstream gate.
 */
object ProductionDatasetUseDispositionEvaluator {

    fun evaluate(
        expectedNamespace: EvidenceDatasetNamespace,
        disposition: ProductionDatasetDispositionRecord?
    ): ProductionDatasetUseDecision {
        val blockers = linkedSetOf<ProductionDatasetUseBlocker>()

        if (disposition == null) {
            blockers += ProductionDatasetUseBlocker.DISPOSITION_MISSING
        } else {
            if (disposition.namespace != expectedNamespace) {
                blockers += ProductionDatasetUseBlocker.NAMESPACE_SCOPE_MISMATCH
            }

            when (disposition.state) {
                ProductionDatasetDispositionState.RETAINED -> Unit
                ProductionDatasetDispositionState.QUARANTINED ->
                    blockers += ProductionDatasetUseBlocker.DATASET_QUARANTINED
                ProductionDatasetDispositionState.WITHDRAWAL_REQUIRED ->
                    blockers += ProductionDatasetUseBlocker.DATASET_WITHDRAWAL_REQUIRED
                ProductionDatasetDispositionState.DELETED ->
                    blockers += ProductionDatasetUseBlocker.DATASET_DELETED
            }
        }

        return ProductionDatasetUseDecision(
            usableFromNamespacePolicy = blockers.isEmpty(),
            blockers = blockers
        )
    }
}

enum class ProductionDatasetWithdrawalExecutionStatus {
    REMOVED,
    BLOCKED_NOT_WITHDRAWAL_REQUIRED,
    BLOCKED_NAMESPACE_METADATA_MISMATCH,
    NAMESPACE_NOT_PRESENT
}

data class ProductionDatasetWithdrawalExecutionResult(
    val status: ProductionDatasetWithdrawalExecutionStatus,
    val removedClaimCount: Int
) {
    init {
        require(removedClaimCount >= 0)
    }

    val namespaceRemoved: Boolean
        get() = status == ProductionDatasetWithdrawalExecutionStatus.REMOVED
}

/**
 * Explicit physical-removal boundary for the in-memory source-isolated index.
 *
 * This executor never derives withdrawal from snapshot/profile lifecycle. The
 * caller must supply a namespace-wide WITHDRAWAL_REQUIRED disposition. It also
 * verifies exact namespace metadata before removing anything.
 *
 * A successful removal may report zero removed claims when an empty registered
 * namespace itself was removed.
 *
 * A successful removal does NOT mutate the disposition to DELETED. The caller
 * records DELETED only after its full storage/persistence layer confirms the
 * required removal, because this in-memory index may not represent every copy.
 */
object ProductionDatasetWithdrawalExecutor {

    fun execute(
        index: SourceIsolatedEvidenceIndex,
        disposition: ProductionDatasetDispositionRecord
    ): ProductionDatasetWithdrawalExecutionResult {
        if (
            disposition.state !=
            ProductionDatasetDispositionState.WITHDRAWAL_REQUIRED
        ) {
            return ProductionDatasetWithdrawalExecutionResult(
                status =
                    ProductionDatasetWithdrawalExecutionStatus
                        .BLOCKED_NOT_WITHDRAWAL_REQUIRED,
                removedClaimCount = 0
            )
        }

        val registered =
            index.registeredNamespaces()
                .firstOrNull { it.id == disposition.namespace.id }
                ?: return ProductionDatasetWithdrawalExecutionResult(
                    status = ProductionDatasetWithdrawalExecutionStatus.NAMESPACE_NOT_PRESENT,
                    removedClaimCount = 0
                )

        if (registered != disposition.namespace) {
            return ProductionDatasetWithdrawalExecutionResult(
                status =
                    ProductionDatasetWithdrawalExecutionStatus
                        .BLOCKED_NAMESPACE_METADATA_MISMATCH,
                removedClaimCount = 0
            )
        }

        val removed = index.removeNamespace(disposition.namespace.id)

        return ProductionDatasetWithdrawalExecutionResult(
            status = ProductionDatasetWithdrawalExecutionStatus.REMOVED,
            removedClaimCount = removed
        )
    }
}
