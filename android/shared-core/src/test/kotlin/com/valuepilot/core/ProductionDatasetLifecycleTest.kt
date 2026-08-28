package com.valuepilot.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductionDatasetLifecycleTest {

    private val profile =
        ProductionActivationProfiles.CONSUMER_MOBILE_CATALOG

    private val freshnessPolicy =
        EvidenceFreshnessPolicy(
            freshForMillis = 2_000L,
            staleAfterMillis = 5_000L,
            futureToleranceMillis = 100L
        )

    private fun provider(id: String) =
        EvidenceProvider(
            id = EvidenceProviderId(id),
            displayName = "Provider $id"
        )

    private fun source(id: String) =
        ShoppingSource(
            id = ShoppingSourceId(id),
            displayName = "Source $id"
        )

    private fun dataset(id: String) =
        EvidenceDatasetNamespace(
            id = id,
            displayName = "Dataset $id",
            licenseId = "reviewed-rights",
            storageBoundary = EvidenceStorageBoundary.PROPRIETARY_RESTRICTED
        )

    private fun candidate(
        providerId: String = "provider-a",
        datasetId: String = "dataset-a",
        observedAt: Long = 1_000L
    ): StagedProductionOfferCandidate {
        val provider = provider(providerId)
        val dataset = dataset(datasetId)

        return StagedProductionOfferCandidate(
            provider = provider,
            source = source("merchant-$datasetId"),
            dataset = dataset,
            sourceProductIdentity =
                SourceProductIdentity(
                    providerItemId = "product-$datasetId",
                    sku = "sku-$datasetId"
                ),
            productName = "Example product $datasetId",
            currentPrice = Money(800L, "CAD"),
            currentPriceSourceFieldName = "sale_price",
            referencePrice = Money(1_000L, "CAD"),
            referencePriceSourceFieldName = "retail_price",
            priceObservedAtEpochMillis = observedAt,
            freshness = EvidenceFreshness.FRESH,
            offerCountryCode = "CA",
            activationProfileId = profile.id,
            availability =
                AvailabilityEvidence(
                    state = AvailabilityState.IN_STOCK,
                    claimKind = EvidenceClaimKind.SOURCE_ASSERTED,
                    observedAtEpochMillis = observedAt
                )
        )
    }

    private fun snapshot(
        candidate: StagedProductionOfferCandidate,
        snapshotId: String = "snapshot-1"
    ) =
        ProductionDatasetSnapshotRef(
            providerId = candidate.provider.id,
            datasetNamespaceId = candidate.dataset.id,
            snapshotId = snapshotId
        )

    private fun bound(
        candidate: StagedProductionOfferCandidate,
        snapshotId: String = "snapshot-1"
    ): SnapshotBoundProductionOfferCandidate =
        requireNotNull(
            ProductionDatasetSnapshotBinder.bind(
                candidate = candidate,
                snapshot = snapshot(candidate, snapshotId)
            ).boundCandidate
        )

    private fun authorization(
        candidate: StagedProductionOfferCandidate,
        deniedGate: ProductionAuthorizationGate? = null
    ): ProviderProductionAuthorizationAssessment =
        ProviderProductionAuthorizationAssessment(
            providerId = candidate.provider.id,
            datasetNamespaceId = candidate.dataset.id,
            gates =
                profile.requiredGates.map { gate ->
                    if (gate == deniedGate) {
                        ProductionGateAssessment(
                            gate = gate,
                            state = ProductionAuthorizationState.DENIED,
                            basisId = "test-denied-${gate.name.lowercase()}"
                        )
                    } else {
                        ProductionGateAssessment(
                            gate = gate,
                            state = ProductionAuthorizationState.SATISFIED,
                            basisId = "test-satisfied-${gate.name.lowercase()}"
                        )
                    }
                }
        )

    private fun lifecycle(
        bound: SnapshotBoundProductionOfferCandidate,
        revision: Long = 1L,
        state: ProductionDatasetLifecycleState =
            ProductionDatasetLifecycleState.ACTIVE,
        effectiveAt: Long = 500L,
        validUntil: Long? = 5_000L,
        basisId: String = "test-lifecycle"
    ) =
        ProductionDatasetLifecycleRecord(
            snapshot = bound.snapshot,
            activationProfileId = bound.candidate.activationProfileId,
            revision = revision,
            state = state,
            effectiveAtEpochMillis = effectiveAt,
            validUntilEpochMillis = validUntil,
            basisId = basisId
        )

    @Test
    fun `snapshot binding succeeds only for exact provider and dataset scope`() {
        val candidate = candidate()
        val correct =
            ProductionDatasetSnapshotBinder.bind(
                candidate = candidate,
                snapshot = snapshot(candidate)
            )

        assertTrue(correct.bound)
        assertTrue(correct.blockers.isEmpty())
        assertNotNull(correct.boundCandidate)

        val wrongProvider =
            ProductionDatasetSnapshotBinder.bind(
                candidate = candidate,
                snapshot =
                    ProductionDatasetSnapshotRef(
                        providerId = EvidenceProviderId("provider-b"),
                        datasetNamespaceId = candidate.dataset.id,
                        snapshotId = "snapshot-1"
                    )
            )

        assertFalse(wrongProvider.bound)
        assertTrue(
            ProductionDatasetSnapshotBindingBlocker.PROVIDER_SCOPE_MISMATCH in
                wrongProvider.blockers
        )

        val wrongDataset =
            ProductionDatasetSnapshotBinder.bind(
                candidate = candidate,
                snapshot =
                    ProductionDatasetSnapshotRef(
                        providerId = candidate.provider.id,
                        datasetNamespaceId = "dataset-b",
                        snapshotId = "snapshot-1"
                    )
            )

        assertFalse(wrongDataset.bound)
        assertTrue(
            ProductionDatasetSnapshotBindingBlocker.DATASET_SCOPE_MISMATCH in
                wrongDataset.blockers
        )
    }

    @Test
    fun `staged candidate is not active without explicit lifecycle record`() {
        val candidate = candidate()
        val bound = bound(candidate)

        val result =
            ProductionDatasetActivationEvaluator.evaluate(
                boundCandidate = bound,
                lifecycleRecord = null,
                currentAuthorizationAssessment = authorization(candidate),
                activationProfile = profile,
                evaluatedAtEpochMillis = 2_000L,
                offerFreshnessPolicy = freshnessPolicy
            )

        assertFalse(result.active)
        assertNull(result.activeCandidate)
        assertTrue(
            ProductionDatasetActivationBlocker.LIFECYCLE_RECORD_MISSING in
                result.blockers
        )
    }

    @Test
    fun `active exact snapshot with current authorization and fresh price activates`() {
        val candidate = candidate()
        val bound = bound(candidate)

        val result =
            ProductionDatasetActivationEvaluator.evaluate(
                boundCandidate = bound,
                lifecycleRecord = lifecycle(bound),
                currentAuthorizationAssessment = authorization(candidate),
                activationProfile = profile,
                evaluatedAtEpochMillis = 2_000L,
                offerFreshnessPolicy = freshnessPolicy
            )

        assertTrue(result.active)
        assertNotNull(result.activeCandidate)
        assertTrue(result.blockers.isEmpty())
        assertTrue(result.authorizationDecision?.authorized == true)
        assertEquals(EvidenceFreshness.FRESH, result.currentFreshness)
    }

    @Test
    fun `suspended revoked and retired snapshots all fail closed`() {
        val candidate = candidate()
        val bound = bound(candidate)

        val expected =
            listOf(
                ProductionDatasetLifecycleState.SUSPENDED to
                    ProductionDatasetActivationBlocker.DATASET_SUSPENDED,
                ProductionDatasetLifecycleState.REVOKED to
                    ProductionDatasetActivationBlocker.DATASET_REVOKED,
                ProductionDatasetLifecycleState.RETIRED to
                    ProductionDatasetActivationBlocker.DATASET_RETIRED
            )

        expected.forEach { (state, blocker) ->
            val result =
                ProductionDatasetActivationEvaluator.evaluate(
                    boundCandidate = bound,
                    lifecycleRecord = lifecycle(bound, state = state),
                    currentAuthorizationAssessment = authorization(candidate),
                    activationProfile = profile,
                    evaluatedAtEpochMillis = 2_000L,
                    offerFreshnessPolicy = freshnessPolicy
                )

            assertFalse(result.active)
            assertTrue(blocker in result.blockers)
        }
    }

    @Test
    fun `activation window fails closed before effective time and after expiry`() {
        val candidate = candidate()
        val bound = bound(candidate)

        val before =
            ProductionDatasetActivationEvaluator.evaluate(
                boundCandidate = bound,
                lifecycleRecord =
                    lifecycle(
                        bound = bound,
                        effectiveAt = 2_500L,
                        validUntil = 4_000L
                    ),
                currentAuthorizationAssessment = authorization(candidate),
                activationProfile = profile,
                evaluatedAtEpochMillis = 2_000L,
                offerFreshnessPolicy = freshnessPolicy
            )

        assertFalse(before.active)
        assertTrue(
            ProductionDatasetActivationBlocker.ACTIVATION_NOT_YET_EFFECTIVE in
                before.blockers
        )

        val expired =
            ProductionDatasetActivationEvaluator.evaluate(
                boundCandidate = bound,
                lifecycleRecord =
                    lifecycle(
                        bound = bound,
                        effectiveAt = 500L,
                        validUntil = 1_500L
                    ),
                currentAuthorizationAssessment = authorization(candidate),
                activationProfile = profile,
                evaluatedAtEpochMillis = 2_000L,
                offerFreshnessPolicy = freshnessPolicy
            )

        assertFalse(expired.active)
        assertTrue(
            ProductionDatasetActivationBlocker.ACTIVATION_EXPIRED in
                expired.blockers
        )
    }

    @Test
    fun `current authorization denial disables a previously staged candidate`() {
        val candidate = candidate()
        val bound = bound(candidate)

        val result =
            ProductionDatasetActivationEvaluator.evaluate(
                boundCandidate = bound,
                lifecycleRecord = lifecycle(bound),
                currentAuthorizationAssessment =
                    authorization(
                        candidate = candidate,
                        deniedGate =
                            ProductionAuthorizationGate.CONSUMER_DISPLAY_AUTHORIZED
                    ),
                activationProfile = profile,
                evaluatedAtEpochMillis = 2_000L,
                offerFreshnessPolicy = freshnessPolicy
            )

        assertFalse(result.active)
        assertTrue(
            ProductionDatasetActivationBlocker.PRODUCTION_AUTHORIZATION_BLOCKED in
                result.blockers
        )
        assertTrue(
            ProductionAuthorizationGate.CONSUMER_DISPLAY_AUTHORIZED in
                requireNotNull(result.authorizationDecision).deniedGates
        )
    }

    @Test
    fun `activation rechecks price freshness instead of trusting staged freshness`() {
        val candidate = candidate(observedAt = 1_000L)
        val bound = bound(candidate)

        val result =
            ProductionDatasetActivationEvaluator.evaluate(
                boundCandidate = bound,
                lifecycleRecord =
                    lifecycle(
                        bound = bound,
                        validUntil = null
                    ),
                currentAuthorizationAssessment = authorization(candidate),
                activationProfile = profile,
                evaluatedAtEpochMillis = 7_000L,
                offerFreshnessPolicy = freshnessPolicy
            )

        assertEquals(EvidenceFreshness.STALE, result.currentFreshness)
        assertFalse(result.active)
        assertTrue(
            ProductionDatasetActivationBlocker.OFFER_STALE in result.blockers
        )
    }

    @Test
    fun `lifecycle registry rejects stale revisions collisions and terminal reactivation`() {
        val candidate = candidate()
        val bound = bound(candidate)
        val registry = ProductionDatasetLifecycleRegistry()
        val active = lifecycle(bound, revision = 2L)

        assertEquals(
            ProductionDatasetLifecycleWriteResult.ADDED,
            registry.write(active)
        )
        assertEquals(
            ProductionDatasetLifecycleWriteResult.DUPLICATE,
            registry.write(active)
        )
        assertEquals(
            ProductionDatasetLifecycleWriteResult.REJECTED_STALE_REVISION,
            registry.write(active.copy(revision = 1L))
        )
        assertEquals(
            ProductionDatasetLifecycleWriteResult.REJECTED_REVISION_COLLISION,
            registry.write(
                active.copy(
                    state = ProductionDatasetLifecycleState.SUSPENDED,
                    basisId = "same-revision-different-value"
                )
            )
        )

        val revoked =
            active.copy(
                revision = 3L,
                state = ProductionDatasetLifecycleState.REVOKED,
                basisId = "rights-revoked"
            )

        assertEquals(
            ProductionDatasetLifecycleWriteResult.UPDATED,
            registry.write(revoked)
        )
        assertEquals(
            ProductionDatasetLifecycleWriteResult.REJECTED_TERMINAL_SNAPSHOT,
            registry.write(
                revoked.copy(
                    revision = 4L,
                    state = ProductionDatasetLifecycleState.ACTIVE,
                    basisId = "attempted-reactivation"
                )
            )
        )
        assertEquals(1, registry.size())
        assertEquals(
            ProductionDatasetLifecycleState.REVOKED,
            registry.currentRecord(bound.snapshot, profile.id)?.state
        )
    }

    @Test
    fun `revoking one dataset does not disable another provider dataset`() {
        val candidateA = candidate(providerId = "provider-a", datasetId = "dataset-a")
        val candidateB = candidate(providerId = "provider-b", datasetId = "dataset-b")
        val boundA = bound(candidateA, snapshotId = "snapshot-a")
        val boundB = bound(candidateB, snapshotId = "snapshot-b")
        val registry = ProductionDatasetLifecycleRegistry()

        val activeA = lifecycle(boundA, revision = 1L, basisId = "activate-a")
        val activeB = lifecycle(boundB, revision = 1L, basisId = "activate-b")

        assertEquals(ProductionDatasetLifecycleWriteResult.ADDED, registry.write(activeA))
        assertEquals(ProductionDatasetLifecycleWriteResult.ADDED, registry.write(activeB))

        assertEquals(
            ProductionDatasetLifecycleWriteResult.UPDATED,
            registry.write(
                activeA.copy(
                    revision = 2L,
                    state = ProductionDatasetLifecycleState.REVOKED,
                    basisId = "revoke-a"
                )
            )
        )

        val resultA =
            ProductionDatasetActivationEvaluator.evaluate(
                boundCandidate = boundA,
                lifecycleRecord = registry.currentRecord(boundA.snapshot, profile.id),
                currentAuthorizationAssessment = authorization(candidateA),
                activationProfile = profile,
                evaluatedAtEpochMillis = 2_000L,
                offerFreshnessPolicy = freshnessPolicy
            )
        val resultB =
            ProductionDatasetActivationEvaluator.evaluate(
                boundCandidate = boundB,
                lifecycleRecord = registry.currentRecord(boundB.snapshot, profile.id),
                currentAuthorizationAssessment = authorization(candidateB),
                activationProfile = profile,
                evaluatedAtEpochMillis = 2_000L,
                offerFreshnessPolicy = freshnessPolicy
            )

        assertFalse(resultA.active)
        assertTrue(
            ProductionDatasetActivationBlocker.DATASET_REVOKED in resultA.blockers
        )
        assertTrue(resultB.active)
        assertEquals(2, registry.size())
    }

    @Test
    fun `authorization from another dataset cannot activate snapshot`() {
        val candidateA = candidate(providerId = "provider-a", datasetId = "dataset-a")
        val candidateB = candidate(providerId = "provider-a", datasetId = "dataset-b")
        val boundA = bound(candidateA)

        val result =
            ProductionDatasetActivationEvaluator.evaluate(
                boundCandidate = boundA,
                lifecycleRecord = lifecycle(boundA),
                currentAuthorizationAssessment = authorization(candidateB),
                activationProfile = profile,
                evaluatedAtEpochMillis = 2_000L,
                offerFreshnessPolicy = freshnessPolicy
            )

        assertFalse(result.active)
        assertTrue(
            ProductionDatasetActivationBlocker.AUTHORIZATION_SCOPE_MISMATCH in
                result.blockers
        )
        assertTrue(
            ProductionDatasetActivationBlocker.PRODUCTION_AUTHORIZATION_BLOCKED in
                result.blockers
        )
    }
}
