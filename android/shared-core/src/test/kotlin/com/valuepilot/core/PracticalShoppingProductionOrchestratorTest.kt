package com.valuepilot.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticalShoppingProductionOrchestratorTest {

    private val eggs = ShoppingItemKey("eggs")
    private val request = ShoppingRequest(listOf(eggs))
    private val freshnessPolicy =
        EvidenceFreshnessPolicy(
            freshForMillis = 2_000L,
            staleAfterMillis = 5_000L,
            futureToleranceMillis = 100L
        )
    private val acceptancePolicy = EvidenceAcceptancePolicy(freshnessPolicy)
    private val planningPolicy =
        PracticalShoppingPolicy(
            minimumSecondStopSavings = Money.parse("15.00", "CAD"),
            maxAdditionalTravelSeconds = 600L,
            maxAdditionalDistanceMetres = 5_000L
        )

    @Test
    fun `valid orchestration with no price evidence reaches no coverage instead of failing validation`() {
        val orchestration =
            orchestration(
                stores = listOf(store("only-store")),
                priceBindings = emptyList(),
                priceRequests = emptyList()
            )

        val result =
            PracticalShoppingProductionOrchestrator.evaluate(
                request = orchestration,
                lifecycleRegistry = ProductionDatasetLifecycleRegistry(),
                dispositionRegistry = ProductionDatasetDispositionRegistry()
            )

        assertTrue(result.validation.valid)
        val decision = requireNotNull(result.decisionResult).decision
        assertEquals(PrimaryShoppingPlanKind.NO_COVERAGE, decision.primaryKind)
        assertNull(decision.primary)
        assertEquals(SecondStopDecision.NOT_EVALUATED_NO_PRIMARY, decision.secondStopDecision)
    }

    @Test
    fun `broken references are aggregated before production evidence evaluation`() {
        val binding =
            PracticalShoppingProductionPriceBinding(
                itemKey = ShoppingItemKey("not-requested"),
                productKey = productKey("missing-product"),
                storeKey = ShoppingStoreKey("missing-store"),
                currentPriceRequestId = "missing-price-request"
            )
        val pair =
            PracticalShoppingProductionStorePairScope(
                baseStoreKey = ShoppingStoreKey("missing-base"),
                addedStoreKey = ShoppingStoreKey("missing-added"),
                additionalTravel = ShoppingTravel(500L, 60L)
            )

        val validation =
            PracticalShoppingProductionOrchestrator.validate(
                orchestration(
                    stores = listOf(store("declared")),
                    storePairs = listOf(pair),
                    priceBindings = listOf(binding),
                    priceRequests = emptyList()
                )
            )

        assertFalse(validation.valid)
        assertEquals(
            setOf(
                PracticalShoppingProductionOrchestrationIssue.BINDING_ITEM_NOT_REQUESTED,
                PracticalShoppingProductionOrchestrationIssue.BINDING_STORE_NOT_DECLARED,
                PracticalShoppingProductionOrchestrationIssue.BINDING_PRICE_REQUEST_NOT_SUPPLIED,
                PracticalShoppingProductionOrchestrationIssue.PAIR_BASE_STORE_NOT_DECLARED,
                PracticalShoppingProductionOrchestrationIssue.PAIR_ADDED_STORE_NOT_DECLARED
            ),
            validation.issues
        )
    }

    @Test
    fun `duplicate bindings stores pairs and raw request ids are reported deterministically`() {
        val sharedStore = store("shared")
        val secondStore = store("second")
        val pair =
            PracticalShoppingProductionStorePairScope(
                baseStoreKey = sharedStore.storeKey,
                addedStoreKey = secondStore.storeKey,
                additionalTravel = ShoppingTravel(100L, 30L)
            )
        val raw = rawPriceRequest("raw")
        val binding =
            PracticalShoppingProductionPriceBinding(
                itemKey = eggs,
                productKey = productKey("shared-product"),
                storeKey = sharedStore.storeKey,
                currentPriceRequestId = raw.requestId
            )

        val validation =
            PracticalShoppingProductionOrchestrator.validate(
                orchestration(
                    stores = listOf(sharedStore, sharedStore, secondStore),
                    storePairs = listOf(pair, pair),
                    priceBindings = listOf(binding, binding),
                    priceRequests = listOf(raw, raw)
                )
            )

        assertFalse(validation.valid)
        assertTrue(PracticalShoppingProductionOrchestrationIssue.DUPLICATE_STORE_KEY in validation.issues)
        assertTrue(PracticalShoppingProductionOrchestrationIssue.DUPLICATE_STORE_PAIR in validation.issues)
        assertTrue(PracticalShoppingProductionOrchestrationIssue.DUPLICATE_PRICE_REQUEST_ID in validation.issues)
        assertTrue(PracticalShoppingProductionOrchestrationIssue.DUPLICATE_ITEM_STORE_BINDING in validation.issues)
        assertTrue(PracticalShoppingProductionOrchestrationIssue.DUPLICATE_BOUND_PRICE_REQUEST_ID in validation.issues)
        assertTrue(PracticalShoppingProductionOrchestrationIssue.DUPLICATE_STORE_PRODUCT_BINDING in validation.issues)
    }

    @Test
    fun `store bound is reported before lower bridge preconditions can throw`() {
        val stores =
            (0..64).map { index ->
                store("store-$index")
            }

        val validation =
            PracticalShoppingProductionOrchestrator.validate(
                orchestration(stores = stores)
            )

        assertFalse(validation.valid)
        assertEquals(
            setOf(PracticalShoppingProductionOrchestrationIssue.TOO_MANY_STORES),
            validation.issues
        )
    }

    @Test
    fun `unbound raw price request is allowed because it may be conflict evidence`() {
        val raw = rawPriceRequest("conflict-only")

        val validation =
            PracticalShoppingProductionOrchestrator.validate(
                orchestration(
                    stores = listOf(store("declared")),
                    priceBindings = emptyList(),
                    priceRequests = listOf(raw)
                )
            )

        assertTrue(validation.valid)
        assertTrue(validation.issues.isEmpty())
    }

    @Test
    fun `invalid orchestration returns no detached decision`() {
        val badBinding =
            PracticalShoppingProductionPriceBinding(
                itemKey = eggs,
                productKey = productKey("p"),
                storeKey = ShoppingStoreKey("missing"),
                currentPriceRequestId = "missing"
            )

        val result =
            PracticalShoppingProductionOrchestrator.evaluate(
                request = orchestration(priceBindings = listOf(badBinding)),
                lifecycleRegistry = ProductionDatasetLifecycleRegistry(),
                dispositionRegistry = ProductionDatasetDispositionRegistry()
            )

        assertFalse(result.validation.valid)
        assertNull(result.decisionResult)
    }

    private fun orchestration(
        stores: List<PracticalShoppingProductionStoreScope> = emptyList(),
        storePairs: List<PracticalShoppingProductionStorePairScope> = emptyList(),
        priceBindings: List<PracticalShoppingProductionPriceBinding> = emptyList(),
        priceRequests: List<ProductionCurrentPriceEligibilityRequest> = emptyList()
    ): PracticalShoppingProductionOrchestrationRequest =
        PracticalShoppingProductionOrchestrationRequest(
            shoppingRequest = request,
            stores = stores,
            storePairs = storePairs,
            priceBindings = priceBindings,
            priceRequests = priceRequests,
            evaluatedAtEpochMillis = 2_000L,
            acceptancePolicy = acceptancePolicy,
            planningPolicy = planningPolicy
        )

    private fun store(key: String): PracticalShoppingProductionStoreScope =
        PracticalShoppingProductionStoreScope(
            storeKey = ShoppingStoreKey(key),
            merchantKey = "merchant-$key",
            locationKey = "location-$key",
            commerceChannelKey = "PHYSICAL",
            travelFromUser = ShoppingTravel(1_000L, 300L)
        )

    private fun productKey(value: String): ProductionProductEvidenceKey =
        ProductionProductEvidenceKey(
            value = "provider:fixture:$value",
            scope = ProductionProductKeyScope.PROVIDER_ITEM
        )

    /** Structurally valid raw request used only to test orchestration references. */
    private fun rawPriceRequest(requestId: String): ProductionCurrentPriceEligibilityRequest {
        val profile = ProductionActivationProfiles.CONSUMER_MOBILE_CATALOG
        val provider =
            EvidenceProvider(
                id = EvidenceProviderId("provider-$requestId"),
                displayName = "Provider $requestId"
            )
        val source =
            ShoppingSource(
                id = ShoppingSourceId("source-$requestId"),
                displayName = "Source $requestId"
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
                identity =
                    ImportedSourceIdentity(
                        providerItemId = "item-$requestId",
                        sku = "sku-$requestId",
                        suppliedGtin = null
                    ),
                productName = "Example product",
                sourcePriceFields =
                    listOf(
                        ImportedPriceField(
                            sourceFieldName = "price",
                            rawValue = "8.00",
                            parsedAmount = Money.parse("8.00", "CAD")
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

        return ProductionCurrentPriceEligibilityRequest(
            requestId = requestId,
            record = record,
            priceRoles = ProductionPriceFieldRoles(currentPriceFieldName = "price"),
            currentAuthorizationAssessment = authorization,
            activationProfile = profile,
            geography =
                ProviderDatasetOfferGeography(
                    providerId = provider.id,
                    datasetNamespaceId = dataset.id,
                    countryCode = "CA",
                    basis = ImportedOfferCountryBasis.EXPLICIT_DATASET_COUNTRY,
                    basisId = "documented-ca-$requestId"
                ),
            targetCountryCode = "CA",
            snapshot = snapshot,
            descriptor =
                ProductionCurrentPriceClaimDescriptor(
                    claimId = "claim-$requestId",
                    merchantKey = "merchant-$requestId",
                    commerceChannelKey = "PHYSICAL",
                    authority = EvidenceAuthorityClass.SOURCE_ASSERTED_METADATA,
                    authorityBasisId = "authority-$requestId"
                ),
            offerFreshnessPolicy = freshnessPolicy
        )
    }
}
