package com.valuepilot.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticalShoppingProductionAssemblerTest {

    private val eggs = ShoppingItemKey("eggs")
    private val milk = ShoppingItemKey("milk")
    private val north = ShoppingStoreKey("north")
    private val west = ShoppingStoreKey("west")
    private val context = PracticalShoppingTravelContext("origin-a", "DRIVING")
    private val travelFreshness =
        EvidenceFreshnessPolicy(
            freshForMillis = 5_000L,
            staleAfterMillis = 20_000L,
            futureToleranceMillis = 100L
        )
    private val acceptancePolicy = EvidenceAcceptancePolicy(travelFreshness)
    private val planningPolicy =
        PracticalShoppingPolicy(
            minimumSecondStopSavings = Money.parse("15.00", "CAD"),
            maxAdditionalTravelSeconds = 600L,
            maxAdditionalDistanceMetres = 5_000L
        )

    @Test
    fun `independently resolved product store and travel facts assemble exact production inputs`() {
        val raw = rawPriceRequest("north-eggs")
        val assembly =
            assemblyRequest(
                shoppingRequest = ShoppingRequest(listOf(eggs)),
                stores = listOf(north),
                links = listOf(PracticalShoppingProductionPriceLink(eggs, north, raw.requestId)),
                products = listOf(exactProduct("eggs-product", eggs, "036000291452")),
                storeIdentities = listOf(exactStore("north-store", north, "merchant-north", "location-north")),
                travel = listOf(userTravel("north-route", north, ShoppingTravel(2_000L, 360L))),
                priceRequests = listOf(raw)
            )

        val result = PracticalShoppingProductionAssembler.assemble(assembly)
        val orchestration = result.orchestrationRequest

        assertTrue(result.readiness.complete)
        assertEquals(1, orchestration.stores.size)
        assertEquals("merchant-north", orchestration.stores.single().merchantKey)
        assertEquals("location-north", orchestration.stores.single().locationKey)
        assertEquals(ShoppingTravel(2_000L, 360L), orchestration.stores.single().travelFromUser)
        assertEquals(1, orchestration.priceBindings.size)
        assertEquals("gtin:0036000291452", orchestration.priceBindings.single().productKey.value)
        assertEquals(raw.requestId, orchestration.priceBindings.single().currentPriceRequestId)
        assertEquals(listOf(raw), orchestration.priceRequests)
        assertTrue(PracticalShoppingProductionOrchestrator.validate(orchestration).valid)
    }

    @Test
    fun `catalog product suggestion remains unresolved while raw conflict evidence is retained`() {
        val raw = rawPriceRequest("north-eggs")
        val assembly =
            assemblyRequest(
                shoppingRequest = ShoppingRequest(listOf(eggs)),
                stores = listOf(north),
                links = listOf(PracticalShoppingProductionPriceLink(eggs, north, raw.requestId)),
                products =
                    listOf(
                        PracticalShoppingProductIdentityCandidate(
                            candidateId = "catalog",
                            itemKey = eggs,
                            providerId = EvidenceProviderId("catalog"),
                            sourceIdentity = SourceProductIdentity(gtin = "036000291452"),
                            relationship = PracticalShoppingProductIntentRelationship.CATALOG_SUGGESTION
                        )
                    ),
                storeIdentities = listOf(exactStore("north-store", north, "merchant-north", "location-north")),
                travel = listOf(userTravel("north-route", north, ShoppingTravel(2_000L, 360L))),
                priceRequests = listOf(raw)
            )

        val result = PracticalShoppingProductionAssembler.assemble(assembly)

        assertTrue(eggs in result.readiness.unresolvedProductItems)
        assertTrue(result.orchestrationRequest.priceBindings.isEmpty())
        assertEquals(listOf(raw), result.orchestrationRequest.priceRequests)
        val skipped = result.readiness.skippedPriceLinks.single()
        assertEquals(
            setOf(PracticalShoppingProductionPriceLinkGap.PRODUCT_IDENTITY_UNRESOLVED),
            skipped.gaps
        )
        assertTrue(PracticalShoppingProductionOrchestrator.validate(result.orchestrationRequest).valid)
    }

    @Test
    fun `geocoder store suggestion cannot create store scope or attached price binding`() {
        val raw = rawPriceRequest("north-eggs")
        val assembly =
            assemblyRequest(
                shoppingRequest = ShoppingRequest(listOf(eggs)),
                stores = listOf(north),
                links = listOf(PracticalShoppingProductionPriceLink(eggs, north, raw.requestId)),
                products = listOf(exactProduct("eggs-product", eggs, "036000291452")),
                storeIdentities =
                    listOf(
                        PracticalShoppingStoreIdentityCandidate(
                            candidateId = "geo",
                            storeKey = north,
                            scope = PracticalShoppingStoreIdentityScope("merchant-guess", "osm-node", "PHYSICAL_STORE"),
                            relationship = PracticalShoppingStoreIdentityRelationship.NAME_OR_GEO_SUGGESTION,
                            providerId = EvidenceProviderId("openstreetmap"),
                            dataset =
                                EvidenceDatasetNamespace(
                                    id = "openstreetmap",
                                    displayName = "OpenStreetMap",
                                    licenseId = "odbl-1.0",
                                    storageBoundary = EvidenceStorageBoundary.OPEN_SHARE_ALIKE
                                )
                        )
                    ),
                travel = listOf(userTravel("north-route", north, ShoppingTravel(2_000L, 360L))),
                priceRequests = listOf(raw)
            )

        val result = PracticalShoppingProductionAssembler.assemble(assembly)

        assertTrue(north in result.readiness.unresolvedStoreIdentities)
        assertTrue(result.orchestrationRequest.stores.isEmpty())
        assertTrue(result.orchestrationRequest.priceBindings.isEmpty())
        assertEquals(
            setOf(PracticalShoppingProductionPriceLinkGap.STORE_UNAVAILABLE),
            result.readiness.skippedPriceLinks.single().gaps
        )
    }

    @Test
    fun `stale route omits store instead of inventing travel`() {
        val raw = rawPriceRequest("north-eggs")
        val staleRoute =
            PracticalShoppingTravelCandidate(
                candidateId = "stale-route",
                leg = PracticalShoppingTravelLeg(null, north),
                context = context,
                travel = ShoppingTravel(2_000L, 360L),
                relationship = PracticalShoppingTravelRelationship.USER_CONFIRMED_ESTIMATE,
                observedAtEpochMillis = 1_000L,
                basisId = "old-user-route"
            )
        val assembly =
            assemblyRequest(
                shoppingRequest = ShoppingRequest(listOf(eggs)),
                stores = listOf(north),
                links = listOf(PracticalShoppingProductionPriceLink(eggs, north, raw.requestId)),
                products = listOf(exactProduct("eggs-product", eggs, "036000291452")),
                storeIdentities = listOf(exactStore("north-store", north, "merchant-north", "location-north")),
                travel = listOf(staleRoute),
                priceRequests = listOf(raw),
                evaluatedAt = 30_000L
            )

        val result = PracticalShoppingProductionAssembler.assemble(assembly)

        assertTrue(north in result.readiness.unresolvedStoreTravel)
        assertTrue(result.orchestrationRequest.stores.isEmpty())
        assertTrue(result.orchestrationRequest.priceBindings.isEmpty())
    }

    @Test
    fun `ordered pair assembles only when both stores and additional route are independently resolved`() {
        val pair = PracticalShoppingRequestedStorePair(north, west)
        val pairTravel = ShoppingTravel(1_500L, 240L)
        val assembly =
            assemblyRequest(
                shoppingRequest = ShoppingRequest(listOf(eggs)),
                stores = listOf(north, west),
                pairs = listOf(pair),
                products = listOf(exactProduct("eggs-product", eggs, "036000291452")),
                storeIdentities =
                    listOf(
                        exactStore("north-store", north, "merchant-north", "location-north"),
                        exactStore("west-store", west, "merchant-west", "location-west")
                    ),
                travel =
                    listOf(
                        userTravel("north-route", north, ShoppingTravel(2_000L, 360L)),
                        userTravel("west-route", west, ShoppingTravel(3_000L, 480L)),
                        pairTravel("north-west", north, west, pairTravel)
                    )
            )

        val result = PracticalShoppingProductionAssembler.assemble(assembly)

        assertEquals(2, result.orchestrationRequest.stores.size)
        assertEquals(1, result.orchestrationRequest.storePairs.size)
        assertEquals(pairTravel, result.orchestrationRequest.storePairs.single().additionalTravel)
        assertTrue(result.readiness.unavailableStorePairs.isEmpty())
        assertTrue(result.readiness.unresolvedPairTravel.isEmpty())
    }

    @Test
    fun `missing pair route preserves both single stores and reports unavailable pair`() {
        val pair = PracticalShoppingRequestedStorePair(north, west)
        val assembly =
            assemblyRequest(
                shoppingRequest = ShoppingRequest(listOf(eggs)),
                stores = listOf(north, west),
                pairs = listOf(pair),
                products = listOf(exactProduct("eggs-product", eggs, "036000291452")),
                storeIdentities =
                    listOf(
                        exactStore("north-store", north, "merchant-north", "location-north"),
                        exactStore("west-store", west, "merchant-west", "location-west")
                    ),
                travel =
                    listOf(
                        userTravel("north-route", north, ShoppingTravel(2_000L, 360L)),
                        userTravel("west-route", west, ShoppingTravel(3_000L, 480L))
                    )
            )

        val result = PracticalShoppingProductionAssembler.assemble(assembly)

        assertEquals(2, result.orchestrationRequest.stores.size)
        assertTrue(result.orchestrationRequest.storePairs.isEmpty())
        assertEquals(setOf(pair), result.readiness.unresolvedPairTravel)
        assertEquals(setOf(pair), result.readiness.unavailableStorePairs)
    }

    @Test
    fun `price link must reference supplied raw request`() {
        assertThrows(IllegalArgumentException::class.java) {
            assemblyRequest(
                shoppingRequest = ShoppingRequest(listOf(eggs)),
                stores = listOf(north),
                links = listOf(PracticalShoppingProductionPriceLink(eggs, north, "missing")),
                products = listOf(exactProduct("eggs-product", eggs, "036000291452")),
                storeIdentities = listOf(exactStore("north-store", north, "merchant-north", "location-north")),
                travel = listOf(userTravel("north-route", north, ShoppingTravel(2_000L, 360L))),
                priceRequests = emptyList()
            )
        }
    }

    @Test
    fun `two shopping intents resolving to same exact product in one store fail closed instead of double counting`() {
        val firstRaw = rawPriceRequest("north-eggs")
        val secondRaw = rawPriceRequest("north-milk")
        val assembly =
            assemblyRequest(
                shoppingRequest = ShoppingRequest(listOf(eggs, milk)),
                stores = listOf(north),
                links =
                    listOf(
                        PracticalShoppingProductionPriceLink(eggs, north, firstRaw.requestId),
                        PracticalShoppingProductionPriceLink(milk, north, secondRaw.requestId)
                    ),
                products =
                    listOf(
                        exactProduct("eggs-product", eggs, "036000291452"),
                        exactProduct("milk-same-product", milk, "036000291452")
                    ),
                storeIdentities = listOf(exactStore("north-store", north, "merchant-north", "location-north")),
                travel = listOf(userTravel("north-route", north, ShoppingTravel(2_000L, 360L))),
                priceRequests = listOf(firstRaw, secondRaw)
            )

        assertThrows(IllegalArgumentException::class.java) {
            PracticalShoppingProductionAssembler.assemble(assembly)
        }
    }

    private fun assemblyRequest(
        shoppingRequest: ShoppingRequest,
        stores: List<ShoppingStoreKey>,
        pairs: List<PracticalShoppingRequestedStorePair> = emptyList(),
        links: List<PracticalShoppingProductionPriceLink> = emptyList(),
        products: List<PracticalShoppingProductIdentityCandidate> = emptyList(),
        storeIdentities: List<PracticalShoppingStoreIdentityCandidate> = emptyList(),
        travel: List<PracticalShoppingTravelCandidate> = emptyList(),
        priceRequests: List<ProductionCurrentPriceEligibilityRequest> = emptyList(),
        evaluatedAt: Long = 10_000L
    ): PracticalShoppingProductionAssemblyRequest =
        PracticalShoppingProductionAssemblyRequest(
            shoppingRequest = shoppingRequest,
            storeKeys = stores,
            requestedStorePairs = pairs,
            priceLinks = links,
            productIdentityCandidates = products,
            storeIdentityCandidates = storeIdentities,
            travelContext = context,
            travelCandidates = travel,
            travelFreshnessPolicy = travelFreshness,
            priceRequests = priceRequests,
            evaluatedAtEpochMillis = evaluatedAt,
            acceptancePolicy = acceptancePolicy,
            planningPolicy = planningPolicy
        )

    private fun exactProduct(
        id: String,
        item: ShoppingItemKey,
        gtin: String
    ): PracticalShoppingProductIdentityCandidate =
        PracticalShoppingProductIdentityCandidate(
            candidateId = id,
            itemKey = item,
            providerId = EvidenceProviderId("user-exact"),
            sourceIdentity = SourceProductIdentity(gtin = gtin),
            relationship = PracticalShoppingProductIntentRelationship.USER_CONFIRMED_EXACT_PRODUCT
        )

    private fun exactStore(
        id: String,
        store: ShoppingStoreKey,
        merchant: String,
        location: String
    ): PracticalShoppingStoreIdentityCandidate =
        PracticalShoppingStoreIdentityCandidate(
            candidateId = id,
            storeKey = store,
            scope =
                PracticalShoppingStoreIdentityScope(
                    merchantKey = merchant,
                    locationKey = location,
                    commerceChannelKey = "PHYSICAL_STORE"
                ),
            relationship = PracticalShoppingStoreIdentityRelationship.USER_CONFIRMED_EXACT_STORE
        )

    private fun userTravel(
        id: String,
        store: ShoppingStoreKey,
        travel: ShoppingTravel
    ): PracticalShoppingTravelCandidate =
        PracticalShoppingTravelCandidate(
            candidateId = id,
            leg = PracticalShoppingTravelLeg(null, store),
            context = context,
            travel = travel,
            relationship = PracticalShoppingTravelRelationship.USER_CONFIRMED_ESTIMATE,
            observedAtEpochMillis = 9_000L,
            basisId = "user-$id"
        )

    private fun pairTravel(
        id: String,
        base: ShoppingStoreKey,
        added: ShoppingStoreKey,
        travel: ShoppingTravel
    ): PracticalShoppingTravelCandidate =
        PracticalShoppingTravelCandidate(
            candidateId = id,
            leg = PracticalShoppingTravelLeg(base, added),
            context = context,
            travel = travel,
            relationship = PracticalShoppingTravelRelationship.USER_CONFIRMED_ESTIMATE,
            observedAtEpochMillis = 9_000L,
            basisId = "user-$id"
        )

    /** Structurally valid raw request; production eligibility is rechecked downstream. */
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
                identity = ImportedSourceIdentity(providerItemId = "item-$requestId"),
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
                        observedAtEpochMillis = 9_000L
                    ),
                priceObservedAtEpochMillis = 9_000L
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
                    basisId = "ca-$requestId"
                ),
            targetCountryCode = "CA",
            snapshot = snapshot,
            descriptor =
                ProductionCurrentPriceClaimDescriptor(
                    claimId = "claim-$requestId",
                    merchantKey = "merchant-$requestId",
                    commerceChannelKey = "PHYSICAL_STORE",
                    authority = EvidenceAuthorityClass.SOURCE_ASSERTED_METADATA,
                    authorityBasisId = "authority-$requestId"
                ),
            offerFreshnessPolicy = travelFreshness
        )
    }
}
