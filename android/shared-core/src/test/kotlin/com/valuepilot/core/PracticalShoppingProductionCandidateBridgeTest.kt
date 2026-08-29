package com.valuepilot.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PracticalShoppingProductionCandidateBridgeTest {

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

    @Test
    fun freshAndAgingExactPricesBuildOneExactStoreCandidate() {
        val eggs = ShoppingItemKey("eggs")
        val milk = ShoppingItemKey("milk")
        val request = ShoppingRequest(listOf(eggs, milk))
        val store = store()
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

        val result =
            evaluate(
                request = request,
                stores = listOf(store),
                bindings =
                    listOf(
                        binding(eggs, freshEggs, store),
                        binding(milk, agingMilk, store)
                    ),
                fixtures = listOf(freshEggs, agingMilk)
            )

        val candidate = assertNotNull(result.singleStoreCandidates.singleOrNull())
        assertEquals(store.storeKey, candidate.storeKey)
        assertEquals(setOf(eggs, milk), candidate.coveredItemKeys)
        assertEquals(Money(1_000L, "CAD"), candidate.knownBasketCost)
        assertEquals(store.travelFromUser, candidate.travel)
        assertEquals(1, candidate.evidence.freshItemCount)
        assertEquals(1, candidate.evidence.agingItemCount)
        assertEquals(0, candidate.evidence.staleItemCount)
        assertEquals(0, candidate.evidence.unknownFreshnessItemCount)
        assertTrue(result.priceEvaluations.all { it.usable })
    }

    @Test
    fun blockedCurrentPriceRemainsMissingInsteadOfCompletingBasket() {
        val eggs = ShoppingItemKey("eggs")
        val milk = ShoppingItemKey("milk")
        val request = ShoppingRequest(listOf(eggs, milk))
        val store = store()
        val eggsPrice =
            fixture(
                requestId = "eggs-price",
                providerItemId = "eggs-product",
                priceMinor = 400L,
                observedAtEpochMillis = 4_500L
            )
        val unavailableMilk =
            fixture(
                requestId = "milk-price",
                providerItemId = "milk-product",
                priceMinor = 600L,
                observedAtEpochMillis = 4_500L,
                availability = AvailabilityState.OUT_OF_STOCK
            )

        val result =
            evaluate(
                request = request,
                stores = listOf(store),
                bindings =
                    listOf(
                        binding(eggs, eggsPrice, store),
                        binding(milk, unavailableMilk, store)
                    ),
                fixtures = listOf(eggsPrice, unavailableMilk)
            )

        val candidate = assertNotNull(result.singleStoreCandidates.singleOrNull())
        assertEquals(setOf(eggs), candidate.coveredItemKeys)
        assertEquals(Money(400L, "CAD"), candidate.knownBasketCost)
        assertEquals(1, candidate.evidence.totalItemCount)

        val milkEvaluation = result.priceEvaluations.single { it.binding.itemKey == milk }
        assertNull(milkEvaluation.selectedPrice)
        assertTrue(
            PracticalShoppingProductionPriceBlocker.CURRENT_PRICE_NOT_ELIGIBLE in
                milkEvaluation.blockers
        )
        assertTrue(
            ProductionCurrentPriceEligibilityBlocker.CANDIDATE_NOT_ACCEPTANCE_RANKABLE in
                milkEvaluation.upstreamBlockers
        )
    }

    @Test
    fun exactProductIdentityMismatchCannotBorrowAValidPrice() {
        val eggs = ShoppingItemKey("eggs")
        val request = ShoppingRequest(listOf(eggs))
        val store = store()
        val actual =
            fixture(
                requestId = "actual-price",
                providerItemId = "actual-product",
                priceMinor = 400L,
                observedAtEpochMillis = 4_500L
            )
        val differentProduct =
            fixture(
                requestId = "different-price",
                providerItemId = "different-product",
                priceMinor = 500L,
                observedAtEpochMillis = 4_500L
            )
        val wrongBinding =
            PracticalShoppingProductionPriceBinding(
                itemKey = eggs,
                productKey = differentProduct.productKey,
                storeKey = store.storeKey,
                currentPriceRequestId = actual.request.requestId
            )

        val result =
            evaluate(
                request = request,
                stores = listOf(store),
                bindings = listOf(wrongBinding),
                fixtures = listOf(actual, differentProduct)
            )

        assertTrue(result.singleStoreCandidates.isEmpty())
        val evaluation = result.priceEvaluations.single()
        assertTrue(
            PracticalShoppingProductionPriceBlocker.PRODUCT_SCOPE_MISMATCH in
                evaluation.blockers
        )
    }

    @Test
    fun differentPhysicalLocationCannotBeRelabeledAsDeclaredStore() {
        val eggs = ShoppingItemKey("eggs")
        val request = ShoppingRequest(listOf(eggs))
        val declaredStore = store(locationKey = "location-a")
        val otherLocationPrice =
            fixture(
                requestId = "eggs-price",
                providerItemId = "eggs-product",
                priceMinor = 400L,
                observedAtEpochMillis = 4_500L,
                locationKey = "location-b"
            )

        val result =
            evaluate(
                request = request,
                stores = listOf(declaredStore),
                bindings = listOf(binding(eggs, otherLocationPrice, declaredStore)),
                fixtures = listOf(otherLocationPrice)
            )

        assertTrue(result.singleStoreCandidates.isEmpty())
        assertTrue(
            PracticalShoppingProductionPriceBlocker.LOCATION_SCOPE_MISMATCH in
                result.priceEvaluations.single().blockers
        )
    }

    @Test
    fun mixedMoneySpecsFailStoreBasketClosedInsteadOfChoosingACurrency() {
        val eggs = ShoppingItemKey("eggs")
        val milk = ShoppingItemKey("milk")
        val request = ShoppingRequest(listOf(eggs, milk))
        val store = store()
        val cadEggs =
            fixture(
                requestId = "eggs-price",
                providerItemId = "eggs-product",
                priceMinor = 400L,
                observedAtEpochMillis = 4_500L,
                currencyCode = "CAD"
            )
        val usdMilk =
            fixture(
                requestId = "milk-price",
                providerItemId = "milk-product",
                priceMinor = 600L,
                observedAtEpochMillis = 4_500L,
                currencyCode = "USD"
            )

        val result =
            evaluate(
                request = request,
                stores = listOf(store),
                bindings =
                    listOf(
                        binding(eggs, cadEggs, store),
                        binding(milk, usdMilk, store)
                    ),
                fixtures = listOf(cadEggs, usdMilk)
            )

        assertTrue(result.priceEvaluations.all { it.usable })
        assertTrue(result.singleStoreCandidates.isEmpty())
        val storeEvaluation = result.storeEvaluations.single()
        assertTrue(
            PracticalShoppingProductionStoreBlocker.MIXED_MONEY_SPEC in
                storeEvaluation.blockers
        )
    }

    @Test
    fun oneCurrentPriceRequestCannotBeCountedForTwoShoppingItems() {
        val eggs = ShoppingItemKey("eggs")
        val milk = ShoppingItemKey("milk")
        val request = ShoppingRequest(listOf(eggs, milk))
        val store = store()
        val onePrice =
            fixture(
                requestId = "one-price",
                providerItemId = "one-product",
                priceMinor = 400L,
                observedAtEpochMillis = 4_500L
            )

        assertFailsWith<IllegalArgumentException> {
            evaluate(
                request = request,
                stores = listOf(store),
                bindings =
                    listOf(
                        binding(eggs, onePrice, store),
                        PracticalShoppingProductionPriceBinding(
                            itemKey = milk,
                            productKey = onePrice.productKey,
                            storeKey = store.storeKey,
                            currentPriceRequestId = onePrice.request.requestId
                        )
                    ),
                fixtures = listOf(onePrice)
            )
        }
    }

    private fun evaluate(
        request: ShoppingRequest,
        stores: List<PracticalShoppingProductionStoreScope>,
        bindings: List<PracticalShoppingProductionPriceBinding>,
        fixtures: List<Fixture>
    ): PracticalShoppingProductionCandidateBridgeResult {
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

        return PracticalShoppingProductionCandidateBridge.evaluate(
            request = request,
            stores = stores,
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
        fixture: Fixture,
        store: PracticalShoppingProductionStoreScope
    ): PracticalShoppingProductionPriceBinding =
        PracticalShoppingProductionPriceBinding(
            itemKey = itemKey,
            productKey = fixture.productKey,
            storeKey = store.storeKey,
            currentPriceRequestId = fixture.request.requestId
        )

    private fun store(
        merchantKey: String = "merchant-a",
        locationKey: String? = "location-a",
        commerceChannelKey: String = "IN_STORE"
    ): PracticalShoppingProductionStoreScope =
        PracticalShoppingProductionStoreScope(
            storeKey = ShoppingStoreKey("store-a"),
            merchantKey = merchantKey,
            locationKey = locationKey,
            commerceChannelKey = commerceChannelKey,
            travelFromUser = ShoppingTravel(2_000L, 300L)
        )

    private fun fixture(
        requestId: String,
        providerItemId: String,
        priceMinor: Long,
        observedAtEpochMillis: Long,
        merchantKey: String = "merchant-a",
        locationKey: String? = "location-a",
        commerceChannelKey: String = "IN_STORE",
        currencyCode: String = "CAD",
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
                            parsedAmount = Money(priceMinor, currencyCode)
                        ),
                        ImportedPriceField(
                            sourceFieldName = "reference_price",
                            rawValue = "reference",
                            parsedAmount = Money(Math.addExact(priceMinor, 1_000L), currencyCode)
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
                merchantKey = merchantKey,
                locationKey = locationKey,
                commerceChannelKey = commerceChannelKey,
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
