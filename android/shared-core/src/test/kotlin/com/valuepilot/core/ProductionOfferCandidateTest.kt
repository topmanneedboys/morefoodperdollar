package com.valuepilot.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductionOfferCandidateTest {

    private val provider =
        EvidenceProvider(
            id = EvidenceProviderId("provider-test"),
            displayName = "Provider Test"
        )

    private val source =
        ShoppingSource(
            id = ShoppingSourceId("merchant-test"),
            displayName = "Merchant Test"
        )

    private val dataset =
        EvidenceDatasetNamespace(
            id = "provider-test-feed",
            displayName = "Provider Test Feed",
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
            freshForMillis = 1_000L,
            staleAfterMillis = 5_000L,
            futureToleranceMillis = 100L
        )

    private fun record(
        identity: ImportedSourceIdentity =
            ImportedSourceIdentity(
                providerItemId = "product-1",
                sku = "sku-1",
                suppliedGtin = "036000291452"
            ),
        prices: List<ImportedPriceField> =
            listOf(
                ImportedPriceField("sale_price", "8.00", Money(800L, "CAD")),
                ImportedPriceField("retail_price", "10.00", Money(1_000L, "CAD"))
            ),
        environment: EvidenceEnvironment = EvidenceEnvironment.REAL_WORLD,
        channel: EvidenceChannel = EvidenceChannel.FIRST_PARTY_FEED,
        claimKind: EvidenceClaimKind = EvidenceClaimKind.SOURCE_ASSERTED,
        observedAt: Long? = 1_000L
    ): ProviderOfferImportRecord =
        ProviderOfferImportRecord(
            provider = provider,
            source = source,
            dataset = dataset,
            environment = environment,
            channel = channel,
            claimKind = claimKind,
            identity = identity,
            productName = "Example Product",
            sourcePriceFields = prices,
            availability =
                AvailabilityEvidence(
                    state = AvailabilityState.IN_STOCK,
                    claimKind = EvidenceClaimKind.SOURCE_ASSERTED
                ),
            datasetGeneratedAtEpochMillis = 900L,
            priceObservedAtEpochMillis = observedAt
        )

    private fun geography(
        countryCode: String? = "CA",
        basis: ImportedOfferCountryBasis =
            ImportedOfferCountryBasis.EXPLICIT_DATASET_COUNTRY,
        providerId: EvidenceProviderId = provider.id,
        datasetId: String = dataset.id
    ): ProviderDatasetOfferGeography =
        ProviderDatasetOfferGeography(
            providerId = providerId,
            datasetNamespaceId = datasetId,
            countryCode = countryCode,
            basis = basis,
            basisId =
                if (basis == ImportedOfferCountryBasis.UNKNOWN) {
                    null
                } else {
                    "geography-basis"
                }
        )

    private fun authorization(
        activationProfile: ProductionActivationProfile = profile,
        providerId: EvidenceProviderId = provider.id,
        datasetId: String = dataset.id,
        overrides: Map<ProductionAuthorizationGate, ProductionAuthorizationState> =
            emptyMap()
    ): ProviderProductionAuthorizationAssessment =
        ProviderProductionAuthorizationAssessment(
            providerId = providerId,
            datasetNamespaceId = datasetId,
            gates =
                activationProfile.requiredGates
                    .sortedBy { it.ordinal }
                    .map { gate ->
                        val state =
                            overrides[gate] ?: ProductionAuthorizationState.SATISFIED
                        ProductionGateAssessment(
                            gate = gate,
                            state = state,
                            basisId = "basis-${gate.name.lowercase()}"
                        )
                    }
        )

    private fun evaluate(
        record: ProviderOfferImportRecord = record(),
        priceRoles: ProductionPriceFieldRoles = roles,
        authorization: ProviderProductionAuthorizationAssessment = authorization(),
        activationProfile: ProductionActivationProfile = profile,
        geography: ProviderDatasetOfferGeography = geography(),
        targetCountryCode: String = "CA",
        evaluatedAt: Long = 1_500L
    ): ProductionOfferCandidateResult =
        ProductionOfferCandidateEvaluator.evaluate(
            record = record,
            priceRoles = priceRoles,
            authorizationAssessment = authorization,
            activationProfile = activationProfile,
            geography = geography,
            targetCountryCode = targetCountryCode,
            evaluatedAtEpochMillis = evaluatedAt,
            offerFreshnessPolicy = freshnessPolicy
        )

    @Test
    fun fullyValidatedFreshRowBecomesStagedCandidateButNotOffer() {
        val result = evaluate()

        assertTrue(result.accepted)
        assertTrue(result.blockers.isEmpty())
        assertTrue(result.authorizationDecision?.authorized == true)
        assertEquals(ProviderDatasetCountryMatchStatus.MATCH, result.countryAssessment?.status)
        assertEquals(EvidenceFreshness.FRESH, result.freshness)

        val candidate = result.candidate
        assertNotNull(candidate)
        assertEquals(800L, candidate?.currentPrice?.minorUnits)
        assertEquals(1_000L, candidate?.referencePrice?.minorUnits)
        assertEquals("CAD", candidate?.currentPrice?.currencyCode)
        assertEquals("CA", candidate?.offerCountryCode)
        assertEquals("sale_price", candidate?.currentPriceSourceFieldName)
        assertEquals("retail_price", candidate?.referencePriceSourceFieldName)
        assertEquals("0036000291452", candidate?.sourceProductIdentity?.gtin)
    }

    @Test
    fun feedAccessAloneStillCannotProduceCandidate() {
        val accessOnly =
            ProviderProductionAuthorizationAssessment(
                providerId = provider.id,
                datasetNamespaceId = dataset.id,
                gates =
                    listOf(
                        ProductionGateAssessment(
                            gate = ProductionAuthorizationGate.DATA_ACCESS_AUTHORIZED,
                            state = ProductionAuthorizationState.SATISFIED,
                            basisId = "feed-access"
                        )
                    )
            )

        val result = evaluate(authorization = accessOnly)

        assertFalse(result.accepted)
        assertNull(result.candidate)
        assertTrue(
            ProductionOfferCandidateBlocker.PRODUCTION_AUTHORIZATION_BLOCKED in
                result.blockers
        )
    }

    @Test
    fun pendingMobileAuthorizationBlocksCandidate() {
        val pending =
            authorization(
                overrides =
                    mapOf(
                        ProductionAuthorizationGate.MOBILE_APP_AUTHORIZED to
                            ProductionAuthorizationState.PENDING
                    )
            )

        val result = evaluate(authorization = pending)

        assertFalse(result.accepted)
        assertTrue(
            ProductionOfferCandidateBlocker.PRODUCTION_AUTHORIZATION_BLOCKED in
                result.blockers
        )
    }

    @Test
    fun currencyOnlyCanadaHintOverridesFalseSatisfiedGeographyGateAndBlocks() {
        val result =
            evaluate(
                geography =
                    geography(
                        countryCode = "CA",
                        basis = ImportedOfferCountryBasis.CURRENCY_ONLY
                    )
            )

        assertFalse(result.accepted)
        assertEquals(
            ProviderDatasetCountryMatchStatus.UNRESOLVED,
            result.countryAssessment?.status
        )
        assertTrue(
            ProductionOfferCandidateBlocker.OFFER_GEOGRAPHY_UNRESOLVED in
                result.blockers
        )
        assertTrue(result.authorizationDecision?.authorized == false)
        assertTrue(
            ProductionAuthorizationGate.OFFER_GEOGRAPHY_VALIDATED in
                result.authorizationDecision.orEmptyUnknownGates()
        )
    }

    @Test
    fun strongUsScopeAgainstCanadaBlocksAsMismatch() {
        val result =
            evaluate(
                geography =
                    geography(
                        countryCode = "US",
                        basis = ImportedOfferCountryBasis.EXPLICIT_DATASET_COUNTRY
                    )
            )

        assertFalse(result.accepted)
        assertEquals(
            ProviderDatasetCountryMatchStatus.MISMATCH,
            result.countryAssessment?.status
        )
        assertTrue(
            ProductionOfferCandidateBlocker.OFFER_GEOGRAPHY_MISMATCH in
                result.blockers
        )
    }

    @Test
    fun authorizationCannotBeReusedAcrossProvider() {
        val otherProvider = EvidenceProviderId("other-provider")
        val result =
            evaluate(
                authorization = authorization(providerId = otherProvider)
            )

        assertFalse(result.accepted)
        assertTrue(
            ProductionOfferCandidateBlocker.AUTHORIZATION_SCOPE_MISMATCH in
                result.blockers
        )
    }

    @Test
    fun geographyCannotBeReusedAcrossDataset() {
        val result =
            evaluate(
                geography = geography(datasetId = "other-feed")
            )

        assertFalse(result.accepted)
        assertTrue(
            ProductionOfferCandidateBlocker.GEOGRAPHY_SCOPE_MISMATCH in
                result.blockers
        )
    }

    @Test
    fun missingCurrentPriceFieldFailsClosed() {
        val missingRoles =
            ProductionPriceFieldRoles(
                currentPriceFieldName = "missing_current"
            )

        val result = evaluate(priceRoles = missingRoles)

        assertFalse(result.accepted)
        assertTrue(
            ProductionOfferCandidateBlocker.CURRENT_PRICE_UNAVAILABLE in
                result.blockers
        )
    }

    @Test
    fun invertedDiscountReferencePairFailsClosed() {
        val inverted =
            record(
                prices =
                    listOf(
                        ImportedPriceField("sale_price", "12.00", Money(1_200L, "CAD")),
                        ImportedPriceField("retail_price", "10.00", Money(1_000L, "CAD"))
                    )
            )

        val result = evaluate(record = inverted)

        assertFalse(result.accepted)
        assertTrue(
            ProductionOfferCandidateBlocker.PRICE_SEMANTIC_CONFLICT in
                result.blockers
        )
    }

    @Test
    fun mismatchedPriceCurrenciesFailClosed() {
        val incompatible =
            record(
                prices =
                    listOf(
                        ImportedPriceField("sale_price", "8.00", Money(800L, "CAD")),
                        ImportedPriceField("retail_price", "10.00", Money(1_000L, "USD"))
                    )
            )

        val result = evaluate(record = incompatible)

        assertFalse(result.accepted)
        assertTrue(
            ProductionOfferCandidateBlocker.PRICE_MONEY_INCOMPARABLE in
                result.blockers
        )
    }

    @Test
    fun missingPerOfferTimestampCannotUseDatasetTimeAsSubstitute() {
        val result = evaluate(record = record(observedAt = null))

        assertFalse(result.accepted)
        assertTrue(
            ProductionOfferCandidateBlocker.OFFER_TIMESTAMP_MISSING in
                result.blockers
        )
        assertNull(result.freshness)
    }

    @Test
    fun stalePerOfferPriceIsNotProductionCandidate() {
        val result =
            evaluate(
                record = record(observedAt = 1_000L),
                evaluatedAt = 7_000L
            )

        assertFalse(result.accepted)
        assertEquals(EvidenceFreshness.STALE, result.freshness)
        assertTrue(
            ProductionOfferCandidateBlocker.OFFER_STALE in
                result.blockers
        )
    }

    @Test
    fun agingPerOfferPriceCanRemainStagedWithoutBecomingRankable() {
        val result =
            evaluate(
                record = record(observedAt = 1_000L),
                evaluatedAt = 3_000L
            )

        assertTrue(result.accepted)
        assertEquals(EvidenceFreshness.AGING, result.candidate?.freshness)
    }

    @Test
    fun weakInferredProviderClaimCannotProduceCandidate() {
        val result =
            evaluate(
                record = record(claimKind = EvidenceClaimKind.INFERRED)
            )

        assertFalse(result.accepted)
        assertTrue(
            ProductionOfferCandidateBlocker.WEAK_SOURCE_CLAIM in result.blockers
        )
    }

    @Test
    fun invalidGtinOnlyIdentityCannotProduceCandidate() {
        val result =
            evaluate(
                record =
                    record(
                        identity =
                            ImportedSourceIdentity(
                                suppliedGtin = "036000291453"
                            )
                    )
            )

        assertFalse(result.accepted)
        assertTrue(
            ProductionOfferCandidateBlocker.NO_VALIDATED_SOURCE_IDENTITY in
                result.blockers
        )
    }

    @Test
    fun weakerCustomActivationProfileCannotBypassMobileCatalogRequirements() {
        val weakProfile =
            ProductionActivationProfile(
                id = "weak-profile",
                requiredGates =
                    setOf(ProductionAuthorizationGate.DATA_ACCESS_AUTHORIZED)
            )
        val weakAuthorization = authorization(activationProfile = weakProfile)

        val result =
            evaluate(
                authorization = weakAuthorization,
                activationProfile = weakProfile
            )

        assertFalse(result.accepted)
        assertTrue(
            ProductionOfferCandidateBlocker.INSUFFICIENT_ACTIVATION_PROFILE in
                result.blockers
        )
    }

    @Test
    fun networkProfileStillRequiresItsAdditionalApprovalGates() {
        val networkProfile =
            ProductionActivationProfiles.CONSUMER_MOBILE_CATALOG_WITH_NETWORK_LINKS
        val catalogOnlyAuthorization = authorization(activationProfile = profile)

        val result =
            evaluate(
                authorization = catalogOnlyAuthorization,
                activationProfile = networkProfile
            )

        assertFalse(result.accepted)
        assertTrue(
            ProductionOfferCandidateBlocker.PRODUCTION_AUTHORIZATION_BLOCKED in
                result.blockers
        )
    }

    private fun ProductionActivationDecision?.orEmptyUnknownGates(): Set<ProductionAuthorizationGate> =
        this?.unknownGates ?: emptySet()
}
