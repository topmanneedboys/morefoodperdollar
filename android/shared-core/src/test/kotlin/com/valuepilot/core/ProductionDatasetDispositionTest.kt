package com.valuepilot.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductionDatasetDispositionTest {

    private fun namespace(
        id: String,
        licenseId: String = "reviewed-rights"
    ) =
        EvidenceDatasetNamespace(
            id = id,
            displayName = "Dataset $id",
            licenseId = licenseId,
            storageBoundary = EvidenceStorageBoundary.PROPRIETARY_RESTRICTED
        )

    private fun record(
        namespace: EvidenceDatasetNamespace,
        revision: Long = 1L,
        state: ProductionDatasetDispositionState =
            ProductionDatasetDispositionState.RETAINED,
        basisId: String = "test-disposition"
    ) =
        ProductionDatasetDispositionRecord(
            namespace = namespace,
            revision = revision,
            state = state,
            basisId = basisId
        )

    private fun registryWith(
        record: ProductionDatasetDispositionRecord
    ): ProductionDatasetDispositionRegistry =
        ProductionDatasetDispositionRegistry().also { registry ->
            assertEquals(
                ProductionDatasetDispositionWriteResult.ADDED,
                registry.write(record)
            )
        }

    private fun claim(id: String, productKey: String) =
        EvidenceClaim(
            claimId = id,
            domain = EvidenceClaimDomain.PRODUCT_IDENTITY,
            valueFingerprint = "identity:$productKey",
            authority = EvidenceAuthorityClass.SOURCE_ASSERTED_METADATA,
            scope = EvidenceClaimScope(productKey = productKey)
        )

    @Test
    fun `missing namespace disposition fails closed for production use`() {
        val decision =
            ProductionDatasetUseDispositionEvaluator.evaluate(
                expectedNamespace = namespace("dataset-a"),
                registry = ProductionDatasetDispositionRegistry()
            )

        assertFalse(decision.usableFromNamespacePolicy)
        assertNull(decision.disposition)
        assertTrue(
            ProductionDatasetUseBlocker.DISPOSITION_MISSING in decision.blockers
        )
    }

    @Test
    fun `retained is only namespace-policy permission while blocked states fail closed`() {
        val namespace = namespace("dataset-a")

        val retainedRecord = record(namespace)
        val retained =
            ProductionDatasetUseDispositionEvaluator.evaluate(
                expectedNamespace = namespace,
                registry = registryWith(retainedRecord)
            )
        assertTrue(retained.usableFromNamespacePolicy)
        assertTrue(retained.blockers.isEmpty())
        assertEquals(retainedRecord, retained.disposition)

        val expected =
            listOf(
                ProductionDatasetDispositionState.QUARANTINED to
                    ProductionDatasetUseBlocker.DATASET_QUARANTINED,
                ProductionDatasetDispositionState.WITHDRAWAL_REQUIRED to
                    ProductionDatasetUseBlocker.DATASET_WITHDRAWAL_REQUIRED,
                ProductionDatasetDispositionState.DELETED to
                    ProductionDatasetUseBlocker.DATASET_DELETED
            )

        expected.forEach { (state, blocker) ->
            val registry = ProductionDatasetDispositionRegistry()
            val initial = record(namespace)
            assertEquals(
                ProductionDatasetDispositionWriteResult.ADDED,
                registry.write(initial)
            )

            val stateRecord =
                if (state == ProductionDatasetDispositionState.DELETED) {
                    val withdrawal =
                        initial.copy(
                            revision = 2L,
                            state = ProductionDatasetDispositionState.WITHDRAWAL_REQUIRED,
                            basisId = "withdrawal"
                        )
                    assertEquals(
                        ProductionDatasetDispositionWriteResult.UPDATED,
                        registry.write(withdrawal)
                    )
                    withdrawal.copy(
                        revision = 3L,
                        state = ProductionDatasetDispositionState.DELETED,
                        basisId = "deleted"
                    )
                } else {
                    initial.copy(
                        revision = 2L,
                        state = state,
                        basisId = "state-${state.name.lowercase()}"
                    )
                }

            assertEquals(
                ProductionDatasetDispositionWriteResult.UPDATED,
                registry.write(stateRecord)
            )

            val decision =
                ProductionDatasetUseDispositionEvaluator.evaluate(
                    expectedNamespace = namespace,
                    registry = registry
                )

            assertFalse(decision.usableFromNamespacePolicy)
            assertTrue(blocker in decision.blockers)
        }
    }

    @Test
    fun `namespace metadata mismatch blocks use even when ids match`() {
        val expected = namespace("dataset-a", licenseId = "license-a")
        val differentMetadata = namespace("dataset-a", licenseId = "license-b")
        val registry = registryWith(record(differentMetadata))

        val decision =
            ProductionDatasetUseDispositionEvaluator.evaluate(
                expectedNamespace = expected,
                registry = registry
            )

        assertFalse(decision.usableFromNamespacePolicy)
        assertTrue(
            ProductionDatasetUseBlocker.NAMESPACE_SCOPE_MISMATCH in decision.blockers
        )
    }

    @Test
    fun `registry allows quarantine recovery but withdrawal is one-way toward deletion`() {
        val namespace = namespace("dataset-a")
        val registry = ProductionDatasetDispositionRegistry()

        val retained = record(namespace, revision = 1L)
        assertEquals(
            ProductionDatasetDispositionWriteResult.ADDED,
            registry.write(retained)
        )

        val quarantined =
            retained.copy(
                revision = 2L,
                state = ProductionDatasetDispositionState.QUARANTINED,
                basisId = "review-open"
            )
        assertEquals(
            ProductionDatasetDispositionWriteResult.UPDATED,
            registry.write(quarantined)
        )

        val restored =
            quarantined.copy(
                revision = 3L,
                state = ProductionDatasetDispositionState.RETAINED,
                basisId = "review-cleared"
            )
        assertEquals(
            ProductionDatasetDispositionWriteResult.UPDATED,
            registry.write(restored)
        )

        val withdrawal =
            restored.copy(
                revision = 4L,
                state = ProductionDatasetDispositionState.WITHDRAWAL_REQUIRED,
                basisId = "namespace-rights-withdrawn"
            )
        assertEquals(
            ProductionDatasetDispositionWriteResult.UPDATED,
            registry.write(withdrawal)
        )

        assertEquals(
            ProductionDatasetDispositionWriteResult.REJECTED_INVALID_TRANSITION,
            registry.write(
                withdrawal.copy(
                    revision = 5L,
                    state = ProductionDatasetDispositionState.QUARANTINED,
                    basisId = "cannot-return-from-withdrawal"
                )
            )
        )

        val deleted =
            withdrawal.copy(
                revision = 5L,
                state = ProductionDatasetDispositionState.DELETED,
                basisId = "storage-removal-confirmed"
            )
        assertEquals(
            ProductionDatasetDispositionWriteResult.UPDATED,
            registry.write(deleted)
        )
        assertEquals(
            ProductionDatasetDispositionWriteResult.REJECTED_TERMINAL_DELETED,
            registry.write(
                deleted.copy(
                    revision = 6L,
                    state = ProductionDatasetDispositionState.RETAINED,
                    basisId = "attempted-resurrection"
                )
            )
        )
        assertEquals(
            ProductionDatasetDispositionState.DELETED,
            registry.currentRecord(namespace.id)?.state
        )
    }

    @Test
    fun `direct deletion is rejected before explicit withdrawal required state`() {
        val namespace = namespace("dataset-a")
        val registry = ProductionDatasetDispositionRegistry()

        assertEquals(
            ProductionDatasetDispositionWriteResult.REJECTED_INVALID_TRANSITION,
            registry.write(
                record(
                    namespace = namespace,
                    state = ProductionDatasetDispositionState.DELETED
                )
            )
        )
        assertNull(registry.currentRecord(namespace.id))

        val retained = record(namespace, revision = 1L)
        assertEquals(
            ProductionDatasetDispositionWriteResult.ADDED,
            registry.write(retained)
        )
        assertEquals(
            ProductionDatasetDispositionWriteResult.REJECTED_INVALID_TRANSITION,
            registry.write(
                retained.copy(
                    revision = 2L,
                    state = ProductionDatasetDispositionState.DELETED,
                    basisId = "skip-withdrawal-attempt"
                )
            )
        )
        assertEquals(
            ProductionDatasetDispositionState.RETAINED,
            registry.currentRecord(namespace.id)?.state
        )
    }

    @Test
    fun `registry rejects stale revisions collisions and namespace metadata mutation`() {
        val namespace = namespace("dataset-a")
        val registry = ProductionDatasetDispositionRegistry()
        val retained = record(namespace, revision = 2L)

        assertEquals(
            ProductionDatasetDispositionWriteResult.ADDED,
            registry.write(retained)
        )
        assertEquals(
            ProductionDatasetDispositionWriteResult.DUPLICATE,
            registry.write(retained)
        )
        assertEquals(
            ProductionDatasetDispositionWriteResult.REJECTED_STALE_REVISION,
            registry.write(retained.copy(revision = 1L))
        )
        assertEquals(
            ProductionDatasetDispositionWriteResult.REJECTED_REVISION_COLLISION,
            registry.write(
                retained.copy(
                    state = ProductionDatasetDispositionState.QUARANTINED,
                    basisId = "same-revision-mutation"
                )
            )
        )
        assertEquals(
            ProductionDatasetDispositionWriteResult.REJECTED_NAMESPACE_METADATA_CHANGE,
            registry.write(
                retained.copy(
                    namespace = namespace.copy(licenseId = "different-license"),
                    revision = 3L,
                    basisId = "metadata-mutation"
                )
            )
        )
    }

    @Test
    fun `missing retained quarantined and deleted registry states cannot physically remove namespace`() {
        val target = namespace("dataset-a")

        fun freshIndex() = SourceIsolatedEvidenceIndex().also {
            it.insert(target, claim("claim-a", "product-a"))
        }

        val missingResult =
            ProductionDatasetWithdrawalExecutor.execute(
                index = freshIndex(),
                registry = ProductionDatasetDispositionRegistry(),
                namespace = target
            )
        assertEquals(
            ProductionDatasetWithdrawalExecutionStatus.BLOCKED_DISPOSITION_MISSING,
            missingResult.status
        )
        assertNull(missingResult.dispositionRevision)

        listOf(
            ProductionDatasetDispositionState.RETAINED,
            ProductionDatasetDispositionState.QUARANTINED,
            ProductionDatasetDispositionState.DELETED
        ).forEach { state ->
            val registry = ProductionDatasetDispositionRegistry()
            val initial = record(target)
            assertEquals(
                ProductionDatasetDispositionWriteResult.ADDED,
                registry.write(initial)
            )

            if (state == ProductionDatasetDispositionState.QUARANTINED) {
                assertEquals(
                    ProductionDatasetDispositionWriteResult.UPDATED,
                    registry.write(
                        initial.copy(
                            revision = 2L,
                            state = state,
                            basisId = "quarantined"
                        )
                    )
                )
            } else if (state == ProductionDatasetDispositionState.DELETED) {
                val withdrawal =
                    initial.copy(
                        revision = 2L,
                        state = ProductionDatasetDispositionState.WITHDRAWAL_REQUIRED,
                        basisId = "withdrawal"
                    )
                assertEquals(
                    ProductionDatasetDispositionWriteResult.UPDATED,
                    registry.write(withdrawal)
                )
                assertEquals(
                    ProductionDatasetDispositionWriteResult.UPDATED,
                    registry.write(
                        withdrawal.copy(
                            revision = 3L,
                            state = ProductionDatasetDispositionState.DELETED,
                            basisId = "deleted"
                        )
                    )
                )
            }

            val index = freshIndex()
            val result =
                ProductionDatasetWithdrawalExecutor.execute(
                    index = index,
                    registry = registry,
                    namespace = target
                )

            assertEquals(
                ProductionDatasetWithdrawalExecutionStatus
                    .BLOCKED_NOT_WITHDRAWAL_REQUIRED,
                result.status
            )
            assertEquals(0, result.removedClaimCount)
            assertEquals(1, index.claimsInNamespace(target.id).size)
        }
    }

    @Test
    fun `withdrawal required removes only exact target namespace`() {
        val target = namespace("dataset-a")
        val other = namespace("dataset-b")
        val index = SourceIsolatedEvidenceIndex()
        index.insert(target, claim("claim-a", "product-a"))
        index.insert(other, claim("claim-b", "product-b"))
        val withdrawal =
            record(
                namespace = target,
                state = ProductionDatasetDispositionState.WITHDRAWAL_REQUIRED
            )
        val registry = registryWith(withdrawal)

        val result =
            ProductionDatasetWithdrawalExecutor.execute(
                index = index,
                registry = registry,
                namespace = target
            )

        assertEquals(
            ProductionDatasetWithdrawalExecutionStatus.REMOVED,
            result.status
        )
        assertTrue(result.namespaceRemoved)
        assertEquals(1, result.removedClaimCount)
        assertEquals(withdrawal.revision, result.dispositionRevision)
        assertTrue(index.claimsInNamespace(target.id).isEmpty())
        assertEquals(1, index.claimsInNamespace(other.id).size)
        assertEquals(
            listOf(other.id),
            index.registeredNamespaces().map { it.id }
        )
    }

    @Test
    fun `withdrawal verifies exact namespace metadata before deleting`() {
        val registered = namespace("dataset-a", licenseId = "license-a")
        val wrongMetadata = namespace("dataset-a", licenseId = "license-b")
        val index = SourceIsolatedEvidenceIndex()
        index.insert(registered, claim("claim-a", "product-a"))
        val registry =
            registryWith(
                record(
                    namespace = wrongMetadata,
                    state = ProductionDatasetDispositionState.WITHDRAWAL_REQUIRED
                )
            )

        val result =
            ProductionDatasetWithdrawalExecutor.execute(
                index = index,
                registry = registry,
                namespace = registered
            )

        assertEquals(
            ProductionDatasetWithdrawalExecutionStatus
                .BLOCKED_NAMESPACE_METADATA_MISMATCH,
            result.status
        )
        assertEquals(0, result.removedClaimCount)
        assertEquals(1, index.claimsInNamespace(registered.id).size)
    }

    @Test
    fun `empty registered namespace can still be physically removed`() {
        val target = namespace("dataset-empty")
        val index = SourceIsolatedEvidenceIndex()
        index.register(target)
        val registry =
            registryWith(
                record(
                    namespace = target,
                    state = ProductionDatasetDispositionState.WITHDRAWAL_REQUIRED
                )
            )

        val result =
            ProductionDatasetWithdrawalExecutor.execute(
                index = index,
                registry = registry,
                namespace = target
            )

        assertEquals(
            ProductionDatasetWithdrawalExecutionStatus.REMOVED,
            result.status
        )
        assertTrue(result.namespaceRemoved)
        assertEquals(0, result.removedClaimCount)
        assertTrue(index.registeredNamespaces().isEmpty())
    }

    @Test
    fun `physical removal does not silently mark disposition deleted`() {
        val target = namespace("dataset-a")
        val index = SourceIsolatedEvidenceIndex()
        index.insert(target, claim("claim-a", "product-a"))
        val registry = ProductionDatasetDispositionRegistry()
        val withdrawal =
            record(
                namespace = target,
                revision = 1L,
                state = ProductionDatasetDispositionState.WITHDRAWAL_REQUIRED
            )
        assertEquals(
            ProductionDatasetDispositionWriteResult.ADDED,
            registry.write(withdrawal)
        )

        val execution =
            ProductionDatasetWithdrawalExecutor.execute(
                index = index,
                registry = registry,
                namespace = target
            )

        assertEquals(
            ProductionDatasetWithdrawalExecutionStatus.REMOVED,
            execution.status
        )
        assertEquals(
            ProductionDatasetDispositionState.WITHDRAWAL_REQUIRED,
            registry.currentRecord(target.id)?.state
        )

        val deleted =
            withdrawal.copy(
                revision = 2L,
                state = ProductionDatasetDispositionState.DELETED,
                basisId = "all-storage-removal-confirmed"
            )
        assertEquals(
            ProductionDatasetDispositionWriteResult.UPDATED,
            registry.write(deleted)
        )
    }

    @Test
    fun `namespace absent is not treated as confirmed deletion by this index`() {
        val target = namespace("dataset-a")
        val index = SourceIsolatedEvidenceIndex()
        val withdrawal =
            record(
                namespace = target,
                state = ProductionDatasetDispositionState.WITHDRAWAL_REQUIRED
            )
        val registry = registryWith(withdrawal)

        val result =
            ProductionDatasetWithdrawalExecutor.execute(
                index = index,
                registry = registry,
                namespace = target
            )

        assertEquals(
            ProductionDatasetWithdrawalExecutionStatus.NAMESPACE_NOT_PRESENT,
            result.status
        )
        assertFalse(result.namespaceRemoved)
        assertEquals(0, result.removedClaimCount)
        assertEquals(withdrawal.revision, result.dispositionRevision)
    }
}
