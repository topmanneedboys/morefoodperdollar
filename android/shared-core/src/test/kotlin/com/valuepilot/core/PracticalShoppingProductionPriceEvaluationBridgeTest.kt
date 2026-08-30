package com.valuepilot.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PracticalShoppingProductionPriceEvaluationBridgeTest {

    private val profile = ProductionActivationProfiles.CONSUMER_MOBILE_CATALOG
    private val freshnessPolicy =
        EvidenceFreshnessPolicy(
            freshForMillis = 2_000L,
            staleAfterMillis = 5_000L,
            futureToleranceMillis = 100L
        )
    private val acceptancePolicy =
        EvidenceAcceptancePolicy(
            freshnessPolicy = freshnessPolicy,
            rankAgingRealWorld = true
        )
    private val evaluatedAtEpochMillis = 5_000L
    private val store =
        PracticalShoppingProductionPriceStoreScope(
            storeKey = ShoppingStoreKey("store-a"),
            merchantKey = "merchant-a",
            locationKey = "location-a",
            commerceChannelKey = "IN_STORE"
        )

    @Test
    fun travelFreePathReturnsFreshAndAgingExactPrices() {
        val eggs = ShoppingItemKey("eggs")
        val milk = ShoppingItemKey("milk")
        val freshEggs =
            fixture(
                requestId = "eggs-price",
                providerItemId = "eggs-product",
                priceMinor = 400L,
                observedAtEpochMillis = 4_500L
            )
        val agingMilk =
            fixture(
                requestId = "milk-price",
                providerItemId = "milk-product",
                priceMinor = 600L,
                observedAtEpochMillis = 2_000L
            )

        val evaluations =
            evaluatePrices(
                request = ShoppingRequest(listOf(eggs, milk)),
                bindings =
                    listOf(
                        binding(eggs, freshEggs),
                        binding(milk, agingMilk)
                    ),
                fixtures = listOf(freshEggs, agingMilk)
            )

        assertEquals(listOf(eggs, milk), evaluations.map { it.binding.itemKey })
        assertEquals(listOf(Money(400L, "CAD"), Money(600L, "CAD")), evaluations.map { it.selectedPrice })
        assertEquals(listOf(EvidenceFreshness.FRESH, EvidenceFreshness.AGING), evaluations.map { it.freshness })
        assertTrue(evaluations.all { it.usable })
    }

    @Test
    fun travelFreePathPreservesBlockedPriceAsMissing() {
        val milk = ShoppingItemKey("milk")
        val unavailableMilk =
            fixture(
                requestId = "milk-price",
                providerItemId = "milk-product",
                priceMinor = 600L,
                observedAtEpochMillis = 4_500L,
                availability = AvailabilityState.OUT_OF_STOCK
            )

        val evaluation =
            evaluatePrices(
                request = ShoppingRequest(listOf(milk)),
                bindings = listOf(binding(milk, unavailableMilk)),
                fixtures = listOf(unavailableMilk)
            ).single()

        assertNull(evaluation.selectedPrice)
        assertNull(evaluation.freshness)
        assertTrue(
            PracticalShoppingProductionPriceBlocker.CURRENT_PRICE_NOT_ELIGIBLE in
                evaluation.blockers
        )
        assertTrue(
            ProductionCurrentPriceEligibilityBlocker.CANDIDATE_NOT_ACCEPTANCE_RANKABLE in
                evaluation.upstreamBlockers
        )
    }

    private fun evaluatePrices(
        request: ShoppingRequest,
        bindings: List<PracticalShoppingProductionPriceBinding>,
        fixtures: List<Fixture>
    ): List<PracticalShoppingProductionPriceEvaluation> {
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

        return PracticalShoppingProductionCandidateBridge.evaluatePrices(
            request = request,
            stores = listOf(store),
            priceBindings = bindings,
            priceRequests = fixtures.map { it.request },
            lifecycleRegistry = lifecycleRegistry,
            dispositionRegistry = dispositionRegistry,
            evaluatedAtEpochMillis = evaluatedAtEpochMillis,
            acceptancePolicy = acceptancePolicy
        )
    }

    private fun binding(
        itemKey: ShoppingItemKey,
        fixture: Fixture
    ): PracticalShoppingProductionPriceBinding =
        PracticalShoppingProductionPriceBinding(
            itemKey = itemKey,
            productKey = fixture.productKey,
            storeKey = store.storeKey,
            currentPriceRequestId = fixture.request.requestId
        )

    private fun fixture(
        requestId: String,
        providerItemId: String,
        priceMinor: Long,
        observedAtEpochMillis: Long,
        availability: AvailabilityState = AvailabilityState.IN_STOCK
    ): Fixture {
        val provider =
            EvidenceProvider(
                id = EvidenceProviderId("provider-a"),
                displayName = "Provider A"
            )
        val source =
            ShoppingSource(
                id = ShoppingSourceId("source-a"),
                displayName = "Source A"
            )
        val dataset =
            EvidenceDatasetNamespace(
                id = "dataset-$requestId",
                displayName = "Dataset $requestId",
                licenseId = "reviewed-rights",
                storageBoundary = EvidenceStorageBoundary.PROPRIETARY_RESTRICTED
            )
        val snapshot =
            ProductionDatasetSnapshotRef(
                providerId = provider.id,
                datasetNamespaceId = dataset.id,
                snapshotId = "snapshot-$requestId"
            )
        val record =
            ProviderOfferImportRecord(
                provider = provider,
                source = source,
                dataset = dataset,
                environment = EvidenceEnvironment.REAL_WORLD,
                channel = EvidenceChannel.FIRST_PARTY_FEED,
                claimKind = EvidenceClaimKind.SOURCE_ASSERTED,
                identity = ImportedSourceIdentity(providerItemId = providerItemId),
                productName = "Product $providerItemId",
                sourcePriceFields =
                    listOf(
                        ImportedPriceField(
                            sourceFieldName = "current_price",
                            rawValue = "current-$priceMinor",
                            parsedAmount = Money(priceMinor, "CAD")
                        ),
                        ImportedPriceField(
                            sourceFieldName = "reference_price",
                            rawValue = "reference",
                            parsedAmount = Money(Math.addExact(priceMinor, 1_000L), "CAD")
                        )
                    ),
                availability =
                    AvailabilityEvidence(
                        state = availability,
                        claimKind = EvidenceClaimKind.SOURCE_ASSERTED,
                        observedAtEpochMillis = observedAtEpochMillis
                    ),
                priceObservedAtEpochMillis = observedAtEpochMillis
            )
        val roles =
            ProductionPriceFieldRoles(
                currentPriceFieldName = "current_price",
                referencePriceFieldName = "reference_price",
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
                claimId = "claim-$requestId",
                merchantKey = store.merchantKey,
                locationKey = store.locationKey,
                commerceChannelKey = store.commerceChannelKey,
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
                basisId = "lifecycle-$requestId"
            )
        val dispositionRecord =
            ProductionDatasetDispositionRecord(
                namespace = dataset,
                revision = 1L,
                state = ProductionDatasetDispositionState.RETAINED,
                basisId = "retained-$requestId"
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
        val sourceIdentity = requireNotNull(record.identity.validatedSourceProductIdentity())
        val productKey =
            requireNotNull(
                ProductionProductEvidenceKeyResolver.resolve(
                    providerId = provider.id,
                    identity = sourceIdentity
                )
            )

        return Fixture(
            request = request,
            lifecycleRecord = lifecycleRecord,
            dispositionRecord = dispositionRecord,
            productKey = productKey
        )
    }

    private data class Fixture(
        val request: ProductionCurrentPriceEligibilityRequest,
        val lifecycleRecord: ProductionDatasetLifecycleRecord,
        val dispositionRecord: ProductionDatasetDispositionRecord,
        val productKey: ProductionProductEvidenceKey
    )
}
