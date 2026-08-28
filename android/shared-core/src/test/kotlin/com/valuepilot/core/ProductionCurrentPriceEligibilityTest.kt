package com.valuepilot.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductionCurrentPriceEligibilityTest {

    private val profile =
        ProductionActivationProfiles.CONSUMER_MOBILE_CATALOG

    private val offerFreshnessPolicy =
        EvidenceFreshnessPolicy(
            freshForMillis = 2_000L,
            staleAfterMillis = 5_000L,
            futureToleranceMillis = 100L
        )

    private val acceptancePolicy =
        EvidenceAcceptancePolicy(freshnessPolicy = offerFreshnessPolicy)

    @Test
    fun `single fresh accepted price becomes eligible for current price stage`() {
        val candidate = fixture(requestId = "candidate")
        val result = evaluate(listOf(candidate), candidateRequestId = "candidate")

        assertTrue(result.eligibleForCurrentPriceStage)
        assertTrue(result.blockers.isEmpty())
        assertEquals(candidate.priceFingerprint, result.factResolution?.selectedValueFingerprint)
        assertTrue(result.candidateEvaluation?.acceptanceResult?.acceptanceRankable == true)
    }

    @Test
    fun `out of stock candidate is factual evidence but not current price eligible`() {
        val candidate =
            fixture(
                requestId = "candidate",
                availability = AvailabilityState.OUT_OF_STOCK
            )

        val result = evaluate(listOf(candidate), candidateRequestId = "candidate")

        assertFalse(result.eligibleForCurrentPriceStage)
        assertTrue(
            ProductionCurrentPriceEligibilityBlocker.CANDIDATE_NOT_ACCEPTANCE_RANKABLE in
                result.blockers
        )
        assertEquals(
            EvidenceDisposition.DISPLAY_ONLY,
            result.candidateEvaluation?.acceptanceResult?.acceptanceDecision?.disposition
        )
        assertEquals(candidate.priceFingerprint, result.factResolution?.selectedValueFingerprint)
    }

    @Test
    fun `equal authority same scope disagreement remains unresolved and blocks`() {
        val candidate = fixture(requestId = "candidate", priceMinor = 800L)
        val competitor =
            fixture(
                requestId = "competitor",
                providerId = "provider-b",
                datasetId = "dataset-b",
                priceMinor = 900L,
                claimId = "claim-b",
                suppliedGtin = "0036000291452"
            )

        val result = evaluate(listOf(candidate, competitor), candidateRequestId = "candidate")

        assertFalse(result.eligibleForCurrentPriceStage)
        assertEquals(
            EvidenceFactResolutionStatus.UNRESOLVED_CONFLICT,
            result.factResolution?.status
        )
        assertTrue(
            ProductionCurrentPriceEligibilityBlocker.UNRESOLVED_CURRENT_PRICE_CONFLICT in
                result.blockers
        )
    }

    @Test
    fun `stronger display only contradictory claim still participates in conflict resolution`() {
        val candidate =
            fixture(
                requestId = "candidate",
                priceMinor = 800L,
                authority = EvidenceAuthorityClass.SOURCE_ASSERTED_METADATA
            )
        val strongerOutOfStock =
            fixture(
                requestId = "stronger",
                providerId = "provider-b",
                datasetId = "dataset-b",
                priceMinor = 900L,
                claimId = "claim-b",
                suppliedGtin = "0036000291452",
                authority = EvidenceAuthorityClass.MERCHANT_AUTHORITATIVE,
                availability = AvailabilityState.OUT_OF_STOCK
            )

        val result =
            evaluate(
                listOf(candidate, strongerOutOfStock),
                candidateRequestId = "candidate"
            )

        assertFalse(result.eligibleForCurrentPriceStage)
        assertTrue(result.candidateEvaluation?.acceptanceResult?.acceptanceRankable == true)
        val strongerEvaluation = result.evaluations.single { it.requestId == "stronger" }
        assertEquals(
            EvidenceDisposition.DISPLAY_ONLY,
            strongerEvaluation.acceptanceResult.acceptanceDecision?.disposition
        )
        assertEquals(
            strongerOutOfStock.priceFingerprint,
            result.factResolution?.selectedValueFingerprint
        )
        assertTrue(
            ProductionCurrentPriceEligibilityBlocker.RESOLVED_CURRENT_PRICE_DIFFERS in
                result.blockers
        )
    }

    @Test
    fun `currently revoked competitor contributes no production claim and cannot block candidate`() {
        val candidate = fixture(requestId = "candidate", priceMinor = 800L)
        val revokedCompetitor =
            fixture(
                requestId = "revoked",
                providerId = "provider-b",
                datasetId = "dataset-b",
                priceMinor = 900L,
                claimId = "claim-b",
                suppliedGtin = "0036000291452",
                authority = EvidenceAuthorityClass.MERCHANT_AUTHORITATIVE,
                lifecycleState = ProductionDatasetLifecycleState.REVOKED
            )

        val result =
            evaluate(
                listOf(candidate, revokedCompetitor),
                candidateRequestId = "candidate"
            )

        assertTrue(result.eligibleForCurrentPriceStage)
        val revokedEvaluation = result.evaluations.single { it.requestId == "revoked" }
        assertFalse(revokedEvaluation.acceptanceResult.claimDecision.accepted)
        assertNull(revokedEvaluation.acceptanceResult.evidence)
        assertEquals(candidate.priceFingerprint, result.factResolution?.selectedValueFingerprint)
    }

    @Test
    fun `different merchant scope coexists and does not conflict with candidate`() {
        val candidate =
            fixture(
                requestId = "candidate",
                merchantKey = "merchant-a",
                priceMinor = 800L
            )
        val otherMerchant =
            fixture(
                requestId = "other",
                providerId = "provider-b",
                datasetId = "dataset-b",
                merchantKey = "merchant-b",
                priceMinor = 900L,
                claimId = "claim-b",
                suppliedGtin = "0036000291452"
            )

        val result = evaluate(listOf(candidate, otherMerchant), "candidate")

        assertTrue(result.eligibleForCurrentPriceStage)
        assertEquals(candidate.priceFingerprint, result.factResolution?.selectedValueFingerprint)
    }

    @Test
    fun `same namespace claim id collision blocks instead of choosing one mutation`() {
        val first =
            fixture(
                requestId = "candidate",
                priceMinor = 800L,
                claimId = "same-claim"
            )
        val mutated =
            fixture(
                requestId = "mutated",
                providerId = first.providerId,
                datasetId = first.datasetId,
                priceMinor = 900L,
                claimId = "same-claim",
                suppliedGtin = "0036000291452"
            )

        val result = evaluate(listOf(first, mutated), "candidate")

        assertFalse(result.eligibleForCurrentPriceStage)
        assertNull(result.factResolution)
        assertTrue(
            ProductionCurrentPriceEligibilityBlocker.RELEVANT_CLAIM_ID_COLLISION in
                result.blockers
        )
    }

    @Test
    fun `missing candidate request fails closed`() {
        val only = fixture(requestId = "only")

        val result = evaluate(listOf(only), candidateRequestId = "missing")

        assertFalse(result.eligibleForCurrentPriceStage)
        assertTrue(
            ProductionCurrentPriceEligibilityBlocker.CANDIDATE_REQUEST_MISSING in
                result.blockers
        )
        assertNull(result.candidateEvaluation)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `request set is bounded`() {
        val requests =
            (0..128).map { index ->
                fixture(
                    requestId = "request-$index",
                    providerId = "provider-$index",
                    datasetId = "dataset-$index",
                    claimId = "claim-$index"
                )
            }

        evaluate(requests, candidateRequestId = "request-0")
    }

    private fun evaluate(
        fixtures: List<Fixture>,
        candidateRequestId: String
    ): ProductionCurrentPriceEligibilityResult {
        val lifecycleRegistry = ProductionDatasetLifecycleRegistry()
        val dispositionRegistry = ProductionDatasetDispositionRegistry()

        fixtures.forEach { fixture ->
            val lifecycleResult = lifecycleRegistry.write(fixture.lifecycleRecord)
            if (lifecycleResult != ProductionDatasetLifecycleWriteResult.ADDED) {
                assertEquals(
                    ProductionDatasetLifecycleWriteResult.DUPLICATE,
                    lifecycleResult
                )
            }

            val dispositionResult = dispositionRegistry.write(fixture.dispositionRecord)
            if (dispositionResult != ProductionDatasetDispositionWriteResult.ADDED) {
                assertEquals(
                    ProductionDatasetDispositionWriteResult.DUPLICATE,
                    dispositionResult
                )
            }
        }

        return ProductionCurrentPriceEligibilityEvaluator.evaluate(
            requests = fixtures.map { it.request },
            candidateRequestId = candidateRequestId,
            lifecycleRegistry = lifecycleRegistry,
            dispositionRegistry = dispositionRegistry,
            evaluatedAtEpochMillis = 2_000L,
            acceptancePolicy = acceptancePolicy
        )
    }

    private fun fixture(
        requestId: String,
        providerId: String = "provider-a",
        datasetId: String = "dataset-a",
        merchantKey: String = "merchant-a",
        commerceChannelKey: String = "ONLINE",
        priceMinor: Long = 800L,
        claimId: String = "claim-a",
        suppliedGtin: String = "036000291452",
        authority: EvidenceAuthorityClass = EvidenceAuthorityClass.SOURCE_ASSERTED_METADATA,
        availability: AvailabilityState = AvailabilityState.IN_STOCK,
        lifecycleState: ProductionDatasetLifecycleState = ProductionDatasetLifecycleState.ACTIVE
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
                        suppliedGtin = suppliedGtin
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
                        state = availability,
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
                merchantKey = merchantKey,
                commerceChannelKey = commerceChannelKey,
                authority = authority,
                authorityBasisId = "authority-$requestId"
            )
        val lifecycleRecord =
            ProductionDatasetLifecycleRecord(
                snapshot = snapshot,
                activationProfileId = profile.id,
                revision = 1L,
                state = lifecycleState,
                effectiveAtEpochMillis = 500L,
                basisId =
                    "lifecycle-$providerId-$datasetId-${lifecycleState.name.lowercase()}"
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
                offerFreshnessPolicy = offerFreshnessPolicy
            )

        return Fixture(
            request = request,
            lifecycleRecord = lifecycleRecord,
            dispositionRecord = dispositionRecord,
            providerId = providerId,
            datasetId = datasetId,
            priceFingerprint = EvidenceFingerprints.money(Money(priceMinor, "CAD"))
        )
    }

    private data class Fixture(
        val request: ProductionCurrentPriceEligibilityRequest,
        val lifecycleRecord: ProductionDatasetLifecycleRecord,
        val dispositionRecord: ProductionDatasetDispositionRecord,
        val providerId: String,
        val datasetId: String,
        val priceFingerprint: String
    )
}
