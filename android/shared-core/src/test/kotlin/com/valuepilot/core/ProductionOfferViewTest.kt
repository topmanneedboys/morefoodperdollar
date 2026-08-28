package com.valuepilot.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductionOfferViewTest {

    private val provider =
        EvidenceProvider(
            id = EvidenceProviderId("provider-a"),
            displayName = "Provider A"
        )

    private val source =
        ShoppingSource(
            id = ShoppingSourceId("merchant-a"),
            displayName = "Merchant A"
        )

    private val dataset =
        EvidenceDatasetNamespace(
            id = "dataset-a",
            displayName = "Dataset A",
            licenseId = "reviewed-rights",
            storageBoundary = EvidenceStorageBoundary.PROPRIETARY_RESTRICTED
        )

    private val profile =
        ProductionActivationProfiles.CONSUMER_MOBILE_CATALOG

    private val roles =
        ProductionPriceFieldRoles(
            currentPriceFieldName = "sale_price",
            referencePriceFieldName = "retail_price",
            relationshipRule =
                ProductionPriceRelationshipRule.CURRENT_MUST_NOT_EXCEED_REFERENCE
        )

    private val freshnessPolicy =
        EvidenceFreshnessPolicy(
            freshForMillis = 2_000L,
            staleAfterMillis = 5_000L,
            futureToleranceMillis = 100L
        )

    private fun record(
        observedAt: Long? = 1_000L,
        currentPrice: Money = Money(800L, "CAD"),
        referencePrice: Money = Money(1_000L, "CAD")
    ): ProviderOfferImportRecord =
        ProviderOfferImportRecord(
            provider = provider,
            source = source,
            dataset = dataset,
            environment = EvidenceEnvironment.REAL_WORLD,
            channel = EvidenceChannel.FIRST_PARTY_FEED,
            claimKind = EvidenceClaimKind.SOURCE_ASSERTED,
            identity =
                ImportedSourceIdentity(
                    providerItemId = "product-a",
                    sku = "sku-a",
                    suppliedGtin = "036000291452"
                ),
            productName = "Example product",
            sourcePriceFields =
                listOf(
                    ImportedPriceField("sale_price", "8.00", currentPrice),
                    ImportedPriceField("retail_price", "10.00", referencePrice)
                ),
            availability =
                AvailabilityEvidence(
                    state = AvailabilityState.IN_STOCK,
                    claimKind = EvidenceClaimKind.SOURCE_ASSERTED,
                    observedAtEpochMillis = observedAt
                ),
            priceObservedAtEpochMillis = observedAt
        )

    private fun geography(
        providerId: EvidenceProviderId = provider.id,
        datasetId: String = dataset.id
    ) =
        ProviderDatasetOfferGeography(
            providerId = providerId,
            datasetNamespaceId = datasetId,
            countryCode = "CA",
            basis = ImportedOfferCountryBasis.EXPLICIT_DATASET_COUNTRY,
            basisId = "documented-ca-market"
        )

    private fun authorization(
        deniedGate: ProductionAuthorizationGate? = null
    ) =
        ProviderProductionAuthorizationAssessment(
            providerId = provider.id,
            datasetNamespaceId = dataset.id,
            gates =
                profile.requiredGates
                    .sortedBy { it.ordinal }
                    .map { gate ->
                        ProductionGateAssessment(
                            gate = gate,
                            state =
                                if (gate == deniedGate) {
                                    ProductionAuthorizationState.DENIED
                                } else {
                                    ProductionAuthorizationState.SATISFIED
                                },
                            basisId = "basis-${gate.name.lowercase()}"
                        )
                    }
        )

    private fun snapshot(
        providerId: EvidenceProviderId = provider.id,
        datasetId: String = dataset.id
    ) =
        ProductionDatasetSnapshotRef(
            providerId = providerId,
            datasetNamespaceId = datasetId,
            snapshotId = "snapshot-a"
        )

    private fun lifecycleRegistry(
        snapshot: ProductionDatasetSnapshotRef = snapshot(),
        state: ProductionDatasetLifecycleState = ProductionDatasetLifecycleState.ACTIVE,
        revision: Long = 4L
    ): ProductionDatasetLifecycleRegistry =
        ProductionDatasetLifecycleRegistry().also { registry ->
            assertEquals(
                ProductionDatasetLifecycleWriteResult.ADDED,
                registry.write(
                    ProductionDatasetLifecycleRecord(
                        snapshot = snapshot,
                        activationProfileId = profile.id,
                        revision = revision,
                        state = state,
                        effectiveAtEpochMillis = 500L,
                        basisId = "lifecycle-${state.name.lowercase()}"
                    )
                )
            )
        }

    private fun dispositionRegistry(
        state: ProductionDatasetDispositionState = ProductionDatasetDispositionState.RETAINED,
        revision: Long = 7L
    ): ProductionDatasetDispositionRegistry =
        ProductionDatasetDispositionRegistry().also { registry ->
            assertEquals(
                ProductionDatasetDispositionWriteResult.ADDED,
                registry.write(
                    ProductionDatasetDispositionRecord(
                        namespace = dataset,
                        revision = revision,
                        state = state,
                        basisId = "disposition-${state.name.lowercase()}"
                    )
                )
            )
        }

    private fun evaluate(
        record: ProviderOfferImportRecord = record(),
        authorization: ProviderProductionAuthorizationAssessment = authorization(),
        geography: ProviderDatasetOfferGeography = geography(),
        snapshot: ProductionDatasetSnapshotRef = snapshot(),
        lifecycleRegistry: ProductionDatasetLifecycleRegistry = lifecycleRegistry(snapshot),
        dispositionRegistry: ProductionDatasetDispositionRegistry = dispositionRegistry(),
        evaluatedAt: Long = 2_000L
    ): ProductionOfferViewResult =
        ProductionOfferViewEvaluator.evaluate(
            record = record,
            priceRoles = roles,
            currentAuthorizationAssessment = authorization,
            activationProfile = profile,
            geography = geography,
            targetCountryCode = "CA",
            snapshot = snapshot,
            lifecycleRegistry = lifecycleRegistry,
            dispositionRegistry = dispositionRegistry,
            evaluatedAtEpochMillis = evaluatedAt,
            offerFreshnessPolicy = freshnessPolicy
        )

    @Test
    fun `validated raw record plus current registries creates point in time production view`() {
        val result = evaluate()

        assertTrue(result.available)
        assertTrue(result.blockers.isEmpty())
        assertTrue(result.stagingDecision.accepted)
        assertTrue(result.bindingDecision?.bound == true)
        assertTrue(result.lifecycleDecision?.active == true)
        assertTrue(result.namespaceDecision.usableFromNamespacePolicy)

        val view = requireNotNull(result.view)
        assertEquals(snapshot(), view.snapshot)
        assertEquals(provider, view.provider)
        assertEquals(dataset, view.dataset)
        assertEquals(Money(800L, "CAD"), view.currentPrice)
        assertEquals(Money(1_000L, "CAD"), view.referencePrice)
        assertEquals(2_000L, view.evaluatedAtEpochMillis)
        assertEquals(EvidenceFreshness.FRESH, view.currentFreshness)
        assertEquals(4L, view.lifecycleRevision)
        assertEquals(7L, view.dispositionRevision)
    }

    @Test
    fun `reference price remains separate from historical previous price`() {
        val result =
            evaluate(
                record =
                    record(
                        currentPrice = Money(800L, "CAD"),
                        referencePrice = Money(1_200L, "CAD")
                    )
            )

        val view = requireNotNull(result.view)
        assertEquals(Money(1_200L, "CAD"), view.referencePrice)

        val arithmeticOffer = view.arithmeticOffer()
        assertEquals(Money(800L, "CAD"), arithmeticOffer.current)
        assertNull(arithmeticOffer.previous)
        assertNull(arithmeticOffer.member)
        assertEquals(PromotionTerms(), arithmeticOffer.promotion)
    }

    @Test
    fun `missing lifecycle registry record blocks view after staging and binding`() {
        val result =
            evaluate(
                lifecycleRegistry = ProductionDatasetLifecycleRegistry()
            )

        assertFalse(result.available)
        assertTrue(result.stagingDecision.accepted)
        assertTrue(result.bindingDecision?.bound == true)
        assertFalse(result.lifecycleDecision?.active == true)
        assertTrue(
            ProductionOfferViewBlocker.SNAPSHOT_LIFECYCLE_BLOCKED in result.blockers
        )
        assertTrue(
            ProductionDatasetActivationBlocker.LIFECYCLE_RECORD_MISSING in
                requireNotNull(result.lifecycleDecision).blockers
        )
    }

    @Test
    fun `revoked current lifecycle registry record blocks retained namespace`() {
        val currentSnapshot = snapshot()
        val result =
            evaluate(
                snapshot = currentSnapshot,
                lifecycleRegistry =
                    lifecycleRegistry(
                        snapshot = currentSnapshot,
                        state = ProductionDatasetLifecycleState.REVOKED
                    )
            )

        assertFalse(result.available)
        assertTrue(result.namespaceDecision.usableFromNamespacePolicy)
        assertTrue(
            ProductionDatasetActivationBlocker.DATASET_REVOKED in
                requireNotNull(result.lifecycleDecision).blockers
        )
    }

    @Test
    fun `quarantined namespace blocks view while snapshot lifecycle stays active`() {
        val result =
            evaluate(
                dispositionRegistry =
                    dispositionRegistry(ProductionDatasetDispositionState.QUARANTINED)
            )

        assertFalse(result.available)
        assertTrue(result.lifecycleDecision?.active == true)
        assertTrue(
            ProductionOfferViewBlocker.NAMESPACE_DISPOSITION_BLOCKED in result.blockers
        )
        assertTrue(
            ProductionDatasetUseBlocker.DATASET_QUARANTINED in
                result.namespaceDecision.blockers
        )
    }

    @Test
    fun `missing namespace disposition blocks view even with active lifecycle`() {
        val result =
            evaluate(
                dispositionRegistry = ProductionDatasetDispositionRegistry()
            )

        assertFalse(result.available)
        assertTrue(result.lifecycleDecision?.active == true)
        assertTrue(
            ProductionDatasetUseBlocker.DISPOSITION_MISSING in
                result.namespaceDecision.blockers
        )
    }

    @Test
    fun `current authorization denial blocks during raw staging`() {
        val result =
            evaluate(
                authorization =
                    authorization(
                        deniedGate = ProductionAuthorizationGate.CONSUMER_DISPLAY_AUTHORIZED
                    )
            )

        assertFalse(result.available)
        assertFalse(result.stagingDecision.accepted)
        assertNull(result.bindingDecision)
        assertNull(result.lifecycleDecision)
        assertTrue(ProductionOfferViewBlocker.STAGING_BLOCKED in result.blockers)
        assertTrue(
            ProductionOfferCandidateBlocker.PRODUCTION_AUTHORIZATION_BLOCKED in
                result.stagingDecision.blockers
        )
    }

    @Test
    fun `wrong snapshot scope blocks after staging without consulting lifecycle`() {
        val wrongSnapshot =
            snapshot(providerId = EvidenceProviderId("provider-b"))

        val result =
            evaluate(
                snapshot = wrongSnapshot,
                lifecycleRegistry = ProductionDatasetLifecycleRegistry()
            )

        assertFalse(result.available)
        assertTrue(result.stagingDecision.accepted)
        assertFalse(result.bindingDecision?.bound == true)
        assertNull(result.lifecycleDecision)
        assertTrue(
            ProductionOfferViewBlocker.SNAPSHOT_BINDING_BLOCKED in result.blockers
        )
        assertTrue(
            ProductionDatasetSnapshotBindingBlocker.PROVIDER_SCOPE_MISMATCH in
                requireNotNull(result.bindingDecision).blockers
        )
    }

    @Test
    fun `stale raw price observation blocks before any production view is created`() {
        val result =
            evaluate(
                record = record(observedAt = 1_000L),
                evaluatedAt = 7_000L
            )

        assertFalse(result.available)
        assertFalse(result.stagingDecision.accepted)
        assertEquals(EvidenceFreshness.STALE, result.stagingDecision.freshness)
        assertNull(result.bindingDecision)
        assertNull(result.lifecycleDecision)
        assertTrue(ProductionOfferViewBlocker.STAGING_BLOCKED in result.blockers)
        assertTrue(
            ProductionOfferCandidateBlocker.OFFER_STALE in
                result.stagingDecision.blockers
        )
    }
}
