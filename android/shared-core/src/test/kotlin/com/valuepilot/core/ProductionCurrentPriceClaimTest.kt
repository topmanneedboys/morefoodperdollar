package com.valuepilot.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductionCurrentPriceClaimTest {

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
    private val freshnessPolicy =
        EvidenceFreshnessPolicy(
            freshForMillis = 2_000L,
            staleAfterMillis = 5_000L,
            futureToleranceMillis = 100L
        )

    private fun record(
        identity: ImportedSourceIdentity =
            ImportedSourceIdentity(
                providerItemId = "item-a",
                sku = "sku-a",
                suppliedGtin = "036000291452"
            ),
        observedAt: Long? = 1_000L,
        claimKind: EvidenceClaimKind = EvidenceClaimKind.SOURCE_ASSERTED,
        channel: EvidenceChannel = EvidenceChannel.FIRST_PARTY_FEED
    ) =
        ProviderOfferImportRecord(
            provider = provider,
            source = source,
            dataset = dataset,
            environment = EvidenceEnvironment.REAL_WORLD,
            channel = channel,
            claimKind = claimKind,
            identity = identity,
            productName = "Example product",
            sourcePriceFields =
                listOf(
                    ImportedPriceField("sale_price", "8.00", Money(800L, "CAD")),
                    ImportedPriceField("retail_price", "10.00", Money(1_000L, "CAD"))
                ),
            availability =
                AvailabilityEvidence(
                    state = AvailabilityState.IN_STOCK,
                    claimKind = claimKind,
                    observedAtEpochMillis = observedAt
                ),
            priceObservedAtEpochMillis = observedAt
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
        state: ProductionDatasetLifecycleState = ProductionDatasetLifecycleState.ACTIVE
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

    private fun dispositionRegistry(
        state: ProductionDatasetDispositionState = ProductionDatasetDispositionState.RETAINED
    ) =
        ProductionDatasetDispositionRegistry().also { registry ->
            assertEquals(
                ProductionDatasetDispositionWriteResult.ADDED,
                registry.write(
                    ProductionDatasetDispositionRecord(
                        namespace = dataset,
                        revision = 7L,
                        state = state,
                        basisId = "disposition-${state.name.lowercase()}"
                    )
                )
            )
        }

    private fun descriptor(
        authority: EvidenceAuthorityClass = EvidenceAuthorityClass.SOURCE_ASSERTED_METADATA
    ) =
        ProductionCurrentPriceClaimDescriptor(
            claimId = "claim-current-price-a",
            merchantKey = "merchant-explicit-a",
            commerceChannelKey = "ONLINE_DELIVERY",
            locationKey = "canada-web",
            authority = authority,
            authorityBasisId = "reviewed-provider-price-provenance"
        )

    private fun evaluate(
        record: ProviderOfferImportRecord = record(),
        descriptor: ProductionCurrentPriceClaimDescriptor = descriptor(),
        lifecycleRegistry: ProductionDatasetLifecycleRegistry = lifecycleRegistry(),
        dispositionRegistry: ProductionDatasetDispositionRegistry = dispositionRegistry(),
        evaluatedAt: Long = 2_000L
    ) =
        ProductionCurrentPriceClaimEvaluator.evaluate(
            record = record,
            priceRoles = roles,
            currentAuthorizationAssessment = authorization(),
            activationProfile = profile,
            geography = geography(),
            targetCountryCode = "CA",
            snapshot = snapshot(),
            lifecycleRegistry = lifecycleRegistry,
            dispositionRegistry = dispositionRegistry,
            descriptor = descriptor,
            evaluatedAtEpochMillis = evaluatedAt,
            offerFreshnessPolicy = freshnessPolicy
        )

    @Test
    fun `current price claim preserves exact scope money timestamp and production provenance`() {
        val result = evaluate()

        assertTrue(result.accepted)
        assertTrue(result.blockers.isEmpty())
        val evidence = requireNotNull(result.evidence)
        val claim = evidence.claim

        assertEquals(EvidenceClaimDomain.CURRENT_PRICE, claim.domain)
        assertEquals(EvidenceFingerprints.money(Money(800L, "CAD")), claim.valueFingerprint)
        assertEquals(EvidenceAuthorityClass.SOURCE_ASSERTED_METADATA, claim.authority)
        assertEquals("merchant-explicit-a", claim.scope.merchantKey)
        assertEquals("ONLINE_DELIVERY", claim.scope.commerceChannelKey)
        assertEquals("canada-web", claim.scope.locationKey)
        assertEquals("CAD", claim.scope.currencyCode)
        assertEquals(1_000L, claim.observedAtEpochMillis)
        assertEquals("gtin:0036000291452", claim.scope.productKey)
        assertEquals(ProductionProductKeyScope.CROSS_SOURCE_GTIN, evidence.productKey.scope)
        assertEquals("reviewed-provider-price-provenance", evidence.authorityBasisId)
        assertEquals(EvidenceChannel.FIRST_PARTY_FEED, evidence.acquisitionChannel)
        assertEquals(EvidenceClaimKind.SOURCE_ASSERTED, evidence.sourceClaimKind)
        assertEquals("sale_price", evidence.currentPriceSourceFieldName)
        assertEquals("CA", evidence.offerCountryCode)
        assertEquals(4L, evidence.lifecycleRevision)
        assertEquals(7L, evidence.dispositionRevision)
        assertEquals(2_000L, evidence.productionEvaluatedAtEpochMillis)
    }

    @Test
    fun `merchant and commerce scope are explicit and not copied from provider or source ids`() {
        val claim = requireNotNull(evaluate().evidence).claim

        assertTrue(claim.scope.merchantKey != provider.id.value)
        assertTrue(claim.scope.merchantKey != source.id.value)
        assertEquals("merchant-explicit-a", claim.scope.merchantKey)
        assertEquals("ONLINE_DELIVERY", claim.scope.commerceChannelKey)
    }

    @Test
    fun `weak or unrelated authority cannot become production current price evidence`() {
        listOf(
            EvidenceAuthorityClass.USER_ASSERTED,
            EvidenceAuthorityClass.GOVERNMENT_RECORD,
            EvidenceAuthorityClass.INFERRED,
            EvidenceAuthorityClass.UNKNOWN
        ).forEach { authority ->
            val result = evaluate(descriptor = descriptor(authority))

            assertFalse(result.accepted)
            assertNull(result.evidence)
            assertTrue(
                ProductionCurrentPriceClaimBlocker.UNSUPPORTED_CURRENT_PRICE_AUTHORITY in
                    result.blockers
            )
        }
    }

    @Test
    fun `direct observation authority cannot be attached to source asserted feed row`() {
        val result =
            evaluate(
                descriptor = descriptor(EvidenceAuthorityClass.PROOF_BACKED_DIRECT_OBSERVATION)
            )

        assertFalse(result.accepted)
        assertTrue(
            ProductionCurrentPriceClaimBlocker.AUTHORITY_CLAIM_KIND_MISMATCH in
                result.blockers
        )
    }

    @Test
    fun `proof backed direct observation authority requires direct observation claim kind`() {
        val result =
            evaluate(
                record =
                    record(
                        claimKind = EvidenceClaimKind.DIRECT_OBSERVATION,
                        channel = EvidenceChannel.DEVICE_OBSERVED
                    ),
                descriptor = descriptor(EvidenceAuthorityClass.PROOF_BACKED_DIRECT_OBSERVATION)
            )

        assertTrue(result.accepted)
        val evidence = requireNotNull(result.evidence)
        assertEquals(EvidenceAuthorityClass.PROOF_BACKED_DIRECT_OBSERVATION, evidence.claim.authority)
        assertEquals(EvidenceClaimKind.DIRECT_OBSERVATION, evidence.sourceClaimKind)
        assertEquals(EvidenceChannel.DEVICE_OBSERVED, evidence.acquisitionChannel)
    }

    @Test
    fun `source metadata authority cannot be attached to direct observation claim`() {
        val result =
            evaluate(
                record =
                    record(
                        claimKind = EvidenceClaimKind.DIRECT_OBSERVATION,
                        channel = EvidenceChannel.DEVICE_OBSERVED
                    ),
                descriptor = descriptor(EvidenceAuthorityClass.SOURCE_ASSERTED_METADATA)
            )

        assertFalse(result.accepted)
        assertTrue(
            ProductionCurrentPriceClaimBlocker.AUTHORITY_CLAIM_KIND_MISMATCH in
                result.blockers
        )
    }

    @Test
    fun `revoked lifecycle blocks claim before generic evidence is created`() {
        val result =
            evaluate(
                lifecycleRegistry = lifecycleRegistry(ProductionDatasetLifecycleState.REVOKED)
            )

        assertFalse(result.accepted)
        assertNull(result.evidence)
        assertTrue(ProductionCurrentPriceClaimBlocker.PRODUCTION_VIEW_BLOCKED in result.blockers)
        assertTrue(
            ProductionOfferViewBlocker.SNAPSHOT_LIFECYCLE_BLOCKED in
                result.productionViewDecision.blockers
        )
    }

    @Test
    fun `quarantined namespace blocks claim before generic evidence is created`() {
        val result =
            evaluate(
                dispositionRegistry =
                    dispositionRegistry(ProductionDatasetDispositionState.QUARANTINED)
            )

        assertFalse(result.accepted)
        assertNull(result.evidence)
        assertTrue(ProductionCurrentPriceClaimBlocker.PRODUCTION_VIEW_BLOCKED in result.blockers)
        assertTrue(
            ProductionOfferViewBlocker.NAMESPACE_DISPOSITION_BLOCKED in
                result.productionViewDecision.blockers
        )
    }

    @Test
    fun `stale price blocks claim through production view reevaluation`() {
        val result = evaluate(record = record(observedAt = 1_000L), evaluatedAt = 7_000L)

        assertFalse(result.accepted)
        assertNull(result.evidence)
        assertTrue(ProductionCurrentPriceClaimBlocker.PRODUCTION_VIEW_BLOCKED in result.blockers)
        assertTrue(
            ProductionOfferViewBlocker.STAGING_BLOCKED in result.productionViewDecision.blockers
        )
    }

    @Test
    fun `sku only identity remains provider scoped rather than becoming cross source`() {
        val result =
            evaluate(
                record = record(identity = ImportedSourceIdentity(sku = "sku-only"))
            )

        assertTrue(result.accepted)
        val evidence = requireNotNull(result.evidence)
        assertEquals(ProductionProductKeyScope.PROVIDER_SKU, evidence.productKey.scope)
        assertFalse(evidence.productKey.usesCrossSourceRepresentation)
        assertEquals(evidence.productKey.value, evidence.claim.scope.productKey)
    }

    @Test
    fun `reference price never becomes current price evidence value`() {
        val evidence = requireNotNull(evaluate().evidence)

        assertEquals(EvidenceFingerprints.money(Money(800L, "CAD")), evidence.claim.valueFingerprint)
        assertFalse(
            evidence.claim.valueFingerprint ==
                EvidenceFingerprints.money(Money(1_000L, "CAD"))
        )
    }

    @Test
    fun `generic conflict resolver consumes claim with exact offer scope`() {
        val first = requireNotNull(evaluate().evidence).claim
        val newer =
            first.copy(
                claimId = "claim-current-price-newer",
                valueFingerprint = EvidenceFingerprints.money(Money(750L, "CAD")),
                observedAtEpochMillis = 1_500L
            )

        val decision = EvidenceConflictPolicy.resolve(first, newer)

        assertEquals(EvidenceConflictRelationship.PREFER_RIGHT, decision.relationship)
        assertEquals("claim-current-price-newer", decision.selectedClaimId)
        assertFalse(decision.blocksRanking)
    }
}
