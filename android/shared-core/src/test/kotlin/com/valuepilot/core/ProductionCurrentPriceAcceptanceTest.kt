package com.valuepilot.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductionCurrentPriceAcceptanceTest {

    private val provider =
        EvidenceProvider(EvidenceProviderId("provider-a"), "Provider A")
    private val source =
        ShoppingSource(ShoppingSourceId("source-a"), "Source A")
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
            relationshipRule = ProductionPriceRelationshipRule.CURRENT_MUST_NOT_EXCEED_REFERENCE
        )
    private val offerFreshnessPolicy =
        EvidenceFreshnessPolicy(
            freshForMillis = 2_000L,
            staleAfterMillis = 5_000L,
            futureToleranceMillis = 100L
        )
    private val acceptancePolicy =
        EvidenceAcceptancePolicy(
            freshnessPolicy = offerFreshnessPolicy
        )

    @Test
    fun `fresh in stock current price passes shared acceptance policy`() {
        val result =
            evaluate(
                record = record(observedAt = 1_000L, availability = AvailabilityState.IN_STOCK),
                evaluatedAt = 2_000L
            )

        assertTrue(result.claimDecision.accepted)
        assertTrue(result.acceptanceEvaluated)
        assertTrue(result.acceptanceRankable)
        assertTrue(result.displayableByAcceptancePolicy)
        assertEquals(EvidenceDisposition.RANKABLE, result.acceptanceDecision?.disposition)
        assertEquals(EvidenceFreshness.FRESH, result.acceptanceDecision?.freshness)
        assertTrue(result.acceptanceDecision?.warnings?.isEmpty() == true)
    }

    @Test
    fun `out of stock production price remains evidence but cannot rank`() {
        val result =
            evaluate(
                record = record(observedAt = 1_000L, availability = AvailabilityState.OUT_OF_STOCK),
                evaluatedAt = 2_000L
            )

        assertTrue(result.claimDecision.accepted)
        assertFalse(result.acceptanceRankable)
        assertTrue(result.displayableByAcceptancePolicy)
        assertEquals(EvidenceDisposition.DISPLAY_ONLY, result.acceptanceDecision?.disposition)
        assertTrue(
            EvidenceWarning.NOT_CURRENTLY_AVAILABLE in
                requireNotNull(result.acceptanceDecision).warnings
        )
    }

    @Test
    fun `aging production price obeys same caller supplied acceptance policy`() {
        val result =
            evaluate(
                record = record(observedAt = 1_000L, availability = AvailabilityState.IN_STOCK),
                evaluatedAt = 3_500L,
                acceptancePolicy = acceptancePolicy.copy(rankAgingRealWorld = false)
            )

        assertTrue(result.claimDecision.accepted)
        assertEquals(EvidenceFreshness.AGING, result.acceptanceDecision?.freshness)
        assertEquals(EvidenceDisposition.DISPLAY_ONLY, result.acceptanceDecision?.disposition)
        assertFalse(result.acceptanceRankable)
    }

    @Test
    fun `acceptance freshness can be stricter than production-view freshness`() {
        val strictAcceptance =
            EvidenceAcceptancePolicy(
                freshnessPolicy =
                    EvidenceFreshnessPolicy(
                        freshForMillis = 500L,
                        staleAfterMillis = 1_000L,
                        futureToleranceMillis = 100L
                    )
            )

        val result =
            evaluate(
                record = record(observedAt = 1_000L, availability = AvailabilityState.IN_STOCK),
                evaluatedAt = 3_500L,
                acceptancePolicy = strictAcceptance
            )

        assertTrue(result.claimDecision.accepted)
        assertEquals(EvidenceFreshness.STALE, result.acceptanceDecision?.freshness)
        assertEquals(EvidenceDisposition.DISPLAY_ONLY, result.acceptanceDecision?.disposition)
        assertFalse(result.acceptanceRankable)
    }

    @Test
    fun `revoked lifecycle blocks claim before acceptance can run`() {
        val result =
            evaluate(
                record = record(observedAt = 1_000L, availability = AvailabilityState.IN_STOCK),
                evaluatedAt = 2_000L,
                lifecycleState = ProductionDatasetLifecycleState.REVOKED
            )

        assertFalse(result.claimDecision.accepted)
        assertFalse(result.acceptanceEvaluated)
        assertFalse(result.acceptanceRankable)
        assertNull(result.evidence)
        assertNull(result.acceptanceDecision)
        assertTrue(
            ProductionCurrentPriceClaimBlocker.PRODUCTION_VIEW_BLOCKED in
                result.claimDecision.blockers
        )
    }

    private fun evaluate(
        record: ProviderOfferImportRecord,
        evaluatedAt: Long,
        lifecycleState: ProductionDatasetLifecycleState = ProductionDatasetLifecycleState.ACTIVE,
        acceptancePolicy: EvidenceAcceptancePolicy = this.acceptancePolicy
    ): ProductionCurrentPriceAcceptanceResult =
        ProductionCurrentPriceAcceptanceEvaluator.evaluate(
            record = record,
            priceRoles = roles,
            currentAuthorizationAssessment = authorization(),
            activationProfile = profile,
            geography = geography(),
            targetCountryCode = "CA",
            snapshot = snapshot(),
            lifecycleRegistry = lifecycleRegistry(lifecycleState),
            dispositionRegistry = dispositionRegistry(),
            descriptor = descriptor(),
            evaluatedAtEpochMillis = evaluatedAt,
            offerFreshnessPolicy = offerFreshnessPolicy,
            acceptancePolicy = acceptancePolicy
        )

    private fun record(
        observedAt: Long,
        availability: AvailabilityState
    ) =
        ProviderOfferImportRecord(
            provider = provider,
            source = source,
            dataset = dataset,
            environment = EvidenceEnvironment.REAL_WORLD,
            channel = EvidenceChannel.FIRST_PARTY_FEED,
            claimKind = EvidenceClaimKind.SOURCE_ASSERTED,
            identity =
                ImportedSourceIdentity(
                    providerItemId = "item-a",
                    sku = "sku-a",
                    suppliedGtin = "036000291452"
                ),
            productName = "Example product",
            sourcePriceFields =
                listOf(
                    ImportedPriceField("sale_price", "8.00", Money(800L, "CAD")),
                    ImportedPriceField("retail_price", "10.00", Money(1_000L, "CAD"))
                ),
            availability =
                AvailabilityEvidence(
                    state = availability,
                    claimKind = EvidenceClaimKind.SOURCE_ASSERTED,
                    observedAtEpochMillis = observedAt
                ),
            priceObservedAtEpochMillis = observedAt
        )

    private fun descriptor() =
        ProductionCurrentPriceClaimDescriptor(
            claimId = "claim-current-price-a",
            merchantKey = "merchant-a",
            commerceChannelKey = "ONLINE",
            authority = EvidenceAuthorityClass.SOURCE_ASSERTED_METADATA,
            authorityBasisId = "provider-current-price-field-reviewed"
        )

    private fun geography() =
        ProviderDatasetOfferGeography(
            providerId = provider.id,
            datasetNamespaceId = dataset.id,
            countryCode = "CA",
            basis = ImportedOfferCountryBasis.EXPLICIT_DATASET_COUNTRY,
            basisId = "documented-ca-market"
        )

    private fun authorization() =
        ProviderProductionAuthorizationAssessment(
            providerId = provider.id,
            datasetNamespaceId = dataset.id,
            gates =
                profile.requiredGates
                    .sortedBy { it.ordinal }
                    .map { gate ->
                        ProductionGateAssessment(
                            gate = gate,
                            state = ProductionAuthorizationState.SATISFIED,
                            basisId = "basis-${gate.name.lowercase()}"
                        )
                    }
        )

    private fun snapshot() =
        ProductionDatasetSnapshotRef(
            providerId = provider.id,
            datasetNamespaceId = dataset.id,
            snapshotId = "snapshot-a"
        )

    private fun lifecycleRegistry(
        state: ProductionDatasetLifecycleState
    ) =
        ProductionDatasetLifecycleRegistry().also { registry ->
            assertEquals(
                ProductionDatasetLifecycleWriteResult.ADDED,
                registry.write(
                    ProductionDatasetLifecycleRecord(
                        snapshot = snapshot(),
                        activationProfileId = profile.id,
                        revision = 4L,
                        state = state,
                        effectiveAtEpochMillis = 500L,
                        basisId = "lifecycle-${state.name.lowercase()}"
                    )
                )
            )
        }

    private fun dispositionRegistry() =
        ProductionDatasetDispositionRegistry().also { registry ->
            assertEquals(
                ProductionDatasetDispositionWriteResult.ADDED,
                registry.write(
                    ProductionDatasetDispositionRecord(
                        namespace = dataset,
                        revision = 7L,
                        state = ProductionDatasetDispositionState.RETAINED,
                        basisId = "retained-for-approved-use"
                    )
                )
            )
        }
}
