package com.valuepilot.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductionCurrentPriceEligibilityBatchTest {

    private val profile = ProductionActivationProfiles.CONSUMER_MOBILE_CATALOG
    private val freshnessPolicy =
        EvidenceFreshnessPolicy(
            freshForMillis = 2_000L,
            staleAfterMillis = 5_000L,
            futureToleranceMillis = 100L
        )
    private val acceptancePolicy = EvidenceAcceptancePolicy(freshnessPolicy = freshnessPolicy)

    @Test
    fun `same-instant batch preserves original candidate conflict semantics`() {
        val first = fixture("first", "provider-a", "dataset-a", "claim-a", 800L)
        val second = fixture("second", "provider-b", "dataset-b", "claim-b", 900L)
        val fixtures = listOf(first, second)
        val lifecycleRegistry = ProductionDatasetLifecycleRegistry()
        val dispositionRegistry = ProductionDatasetDispositionRegistry()

        fixtures.forEach { fixture ->
            assertEquals(
                ProductionDatasetLifecycleWriteResult.ADDED,
                lifecycleRegistry.write(fixture.lifecycleRecord)
            )
            assertEquals(
                ProductionDatasetDispositionWriteResult.ADDED,
                dispositionRegistry.write(fixture.dispositionRecord)
            )
        }

        val requests = fixtures.map { it.request }
        val batch =
            ProductionCurrentPriceEligibilityEvaluator.evaluateAll(
                requests = requests,
                lifecycleRegistry = lifecycleRegistry,
                dispositionRegistry = dispositionRegistry,
                evaluatedAtEpochMillis = 2_000L,
                acceptancePolicy = acceptancePolicy
            )

        assertEquals(setOf("first", "second"), batch.keys)

        for (requestId in batch.keys) {
            val repeatedSingle =
                ProductionCurrentPriceEligibilityEvaluator.evaluate(
                    requests = requests,
                    candidateRequestId = requestId,
                    lifecycleRegistry = lifecycleRegistry,
                    dispositionRegistry = dispositionRegistry,
                    evaluatedAtEpochMillis = 2_000L,
                    acceptancePolicy = acceptancePolicy
                )
            val batched = requireNotNull(batch[requestId])

            assertEquals(repeatedSingle.blockers, batched.blockers)
            assertEquals(repeatedSingle.factResolution, batched.factResolution)
            assertEquals(
                repeatedSingle.candidateEvaluation?.acceptanceResult?.acceptanceDecision,
                batched.candidateEvaluation?.acceptanceResult?.acceptanceDecision
            )
            assertEquals(
                repeatedSingle.candidateEvaluation?.acceptanceResult?.evidence?.claim,
                batched.candidateEvaluation?.acceptanceResult?.evidence?.claim
            )
            assertEquals(
                repeatedSingle.eligibleForCurrentPriceStage,
                batched.eligibleForCurrentPriceStage
            )
        }

        assertFalse(requireNotNull(batch["first"]).eligibleForCurrentPriceStage)
        assertFalse(requireNotNull(batch["second"]).eligibleForCurrentPriceStage)
        assertTrue(
            ProductionCurrentPriceEligibilityBlocker.UNRESOLVED_CURRENT_PRICE_CONFLICT in
                requireNotNull(batch["first"]).blockers
        )
    }

    private fun fixture(
        requestId: String,
        providerId: String,
        datasetId: String,
        claimId: String,
        priceMinor: Long
    ): Fixture {
        val provider =
            EvidenceProvider(
                id = EvidenceProviderId(providerId),
                displayName = providerId
            )
        val source =
            ShoppingSource(
                id = ShoppingSourceId("source-$providerId"),
                displayName = "Source $providerId"
            )
        val dataset =
            EvidenceDatasetNamespace(
                id = datasetId,
                displayName = datasetId,
                licenseId = "reviewed-rights",
                storageBoundary = EvidenceStorageBoundary.PROPRIETARY_RESTRICTED
            )
        val snapshot =
            ProductionDatasetSnapshotRef(
                providerId = provider.id,
                datasetNamespaceId = dataset.id,
                snapshotId = "snapshot-$providerId"
            )
        val record =
            ProviderOfferImportRecord(
                provider = provider,
                source = source,
                dataset = dataset,
                environment = EvidenceEnvironment.REAL_WORLD,
                channel = EvidenceChannel.FIRST_PARTY_FEED,
                claimKind = EvidenceClaimKind.SOURCE_ASSERTED,
                identity =
                    ImportedSourceIdentity(
                        providerItemId = "item-$requestId",
                        sku = "sku-$requestId",
                        suppliedGtin = "036000291452"
                    ),
                productName = "Example product",
                sourcePriceFields =
                    listOf(
                        ImportedPriceField(
                            sourceFieldName = "sale_price",
                            rawValue = "price-$priceMinor",
                            parsedAmount = Money(priceMinor, "CAD")
                        ),
                        ImportedPriceField(
                            sourceFieldName = "retail_price",
                            rawValue = "reference",
                            parsedAmount = Money(1_000L, "CAD")
                        )
                    ),
                availability =
                    AvailabilityEvidence(
                        state = AvailabilityState.IN_STOCK,
                        claimKind = EvidenceClaimKind.SOURCE_ASSERTED,
                        observedAtEpochMillis = 1_000L
                    ),
                priceObservedAtEpochMillis = 1_000L
            )
        val roles =
            ProductionPriceFieldRoles(
                currentPriceFieldName = "sale_price",
                referencePriceFieldName = "retail_price",
                relationshipRule =
                    ProductionPriceRelationshipRule.CURRENT_MUST_NOT_EXCEED_REFERENCE
            )
        val authorization =
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
                                basisId = "basis-${gate.name.lowercase()}-$requestId"
                            )
                        }
            )
        val geography =
            ProviderDatasetOfferGeography(
                providerId = provider.id,
                datasetNamespaceId = dataset.id,
                countryCode = "CA",
                basis = ImportedOfferCountryBasis.EXPLICIT_DATASET_COUNTRY,
                basisId = "documented-ca-$requestId"
            )
        val descriptor =
            ProductionCurrentPriceClaimDescriptor(
                claimId = claimId,
                merchantKey = "merchant-same",
                commerceChannelKey = "ONLINE",
                locationKey = null,
                authority = EvidenceAuthorityClass.SOURCE_ASSERTED_METADATA,
                authorityBasisId = "authority-$requestId"
            )
        val lifecycleRecord =
            ProductionDatasetLifecycleRecord(
                snapshot = snapshot,
                activationProfileId = profile.id,
                revision = 1L,
                state = ProductionDatasetLifecycleState.ACTIVE,
                effectiveAtEpochMillis = 500L,
                basisId = "lifecycle-$providerId"
            )
        val dispositionRecord =
            ProductionDatasetDispositionRecord(
                namespace = dataset,
                revision = 1L,
                state = ProductionDatasetDispositionState.RETAINED,
                basisId = "retained-$datasetId"
            )
        val request =
            ProductionCurrentPriceEligibilityRequest(
                requestId = requestId,
                record = record,
                priceRoles = roles,
                currentAuthorizationAssessment = authorization,
                activationProfile = profile,
                geography = geography,
                targetCountryCode = "CA",
                snapshot = snapshot,
                descriptor = descriptor,
                offerFreshnessPolicy = freshnessPolicy
            )

        return Fixture(
            request = request,
            lifecycleRecord = lifecycleRecord,
            dispositionRecord = dispositionRecord
        )
    }

    private data class Fixture(
        val request: ProductionCurrentPriceEligibilityRequest,
        val lifecycleRecord: ProductionDatasetLifecycleRecord,
        val dispositionRecord: ProductionDatasetDispositionRecord
    )
}
