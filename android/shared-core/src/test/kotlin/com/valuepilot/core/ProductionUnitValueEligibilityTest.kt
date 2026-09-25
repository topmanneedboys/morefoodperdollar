package com.valuepilot.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductionUnitValueEligibilityTest {

    private val profile =
        ProductionActivationProfiles.CONSUMER_MOBILE_CATALOG

    private val freshnessPolicy =
        EvidenceFreshnessPolicy(
            freshForMillis = 2_000L,
            staleAfterMillis = 5_000L,
            futureToleranceMillis = 100L
        )

    private val acceptancePolicy =
        EvidenceAcceptancePolicy(freshnessPolicy = freshnessPolicy)

    @Test
    fun `conflict resolved production price and strong package quantity produce unit value`() {
        val price = priceFixture(requestId = "candidate")
        val quantity =
            quantityCandidate(
                evidenceId = "quantity-a",
                quantity = QuantityNormalization.count(100)
            )

        val result =
            evaluate(
                priceFixtures = listOf(price),
                candidatePriceRequestId = "candidate",
                quantities = listOf(quantity)
            )

        assertTrue(result.rankable)
        assertTrue(result.blockers.isEmpty())
        assertEquals("quantity-a", result.selectedQuantityEvidence?.evidenceId)
        assertEquals(RateUnit.ITEM, result.unitValueResult?.rate?.unit)
        assertEquals("CAD", result.unitValueResult?.rate?.currencyCode)
        assertEquals(80_000L, result.unitValueResult?.rate?.currencyMicrosPerUnit)
    }

    @Test
    fun `blocked current price stage prevents unit value evaluation`() {
        val candidate = priceFixture(requestId = "candidate", priceMinor = 800L)
        val conflicting =
            priceFixture(
                requestId = "conflicting",
                providerId = "provider-b",
                datasetId = "dataset-b",
                claimId = "price-b",
                priceMinor = 900L,
                suppliedGtin = "0036000291452"
            )
        val quantity = quantityCandidate("quantity-a", QuantityNormalization.count(100))

        val result =
            evaluate(
                priceFixtures = listOf(candidate, conflicting),
                candidatePriceRequestId = "candidate",
                quantities = listOf(quantity)
            )

        assertFalse(result.rankable)
        assertTrue(
            ProductionUnitValueEligibilityBlocker.PRICE_STAGE_BLOCKED in
                result.blockers
        )
        assertTrue(result.policyAttempts.isEmpty())
        assertNull(result.unitValueResult)
    }

    @Test
    fun `equal authority package quantity disagreement remains unresolved and blocks`() {
        val price = priceFixture(requestId = "candidate")
        val left =
            quantityCandidate(
                evidenceId = "quantity-left",
                namespaceId = "quantity-left-source",
                claimId = "quantity-left-claim",
                quantity = QuantityNormalization.count(100)
            )
        val right =
            quantityCandidate(
                evidenceId = "quantity-right",
                namespaceId = "quantity-right-source",
                claimId = "quantity-right-claim",
                quantity = QuantityNormalization.count(90)
            )

        val result =
            evaluate(
                priceFixtures = listOf(price),
                candidatePriceRequestId = "candidate",
                quantities = listOf(left, right)
            )

        assertFalse(result.rankable)
        assertEquals(
            EvidenceFactResolutionStatus.UNRESOLVED_CONFLICT,
            result.quantityResolution?.status
        )
        assertTrue(
            ProductionUnitValueEligibilityBlocker.UNRESOLVED_PACKAGE_QUANTITY_CONFLICT in
                result.blockers
        )
        assertTrue(result.policyAttempts.isEmpty())
    }

    @Test
    fun `stronger package quantity fact defeats weaker contradictory metadata`() {
        val price = priceFixture(requestId = "candidate")
        val weaker =
            quantityCandidate(
                evidenceId = "weaker",
                namespaceId = "weaker-source",
                claimId = "weaker-claim",
                quantity = QuantityNormalization.count(90),
                authority = EvidenceAuthorityClass.SOURCE_ASSERTED_METADATA
            )
        val stronger =
            quantityCandidate(
                evidenceId = "stronger",
                namespaceId = "stronger-source",
                claimId = "stronger-claim",
                quantity = QuantityNormalization.count(100),
                authority = EvidenceAuthorityClass.MERCHANT_AUTHORITATIVE
            )

        val result =
            evaluate(
                priceFixtures = listOf(price),
                candidatePriceRequestId = "candidate",
                quantities = listOf(weaker, stronger)
            )

        assertTrue(result.rankable)
        assertEquals(
            stronger.claim.valueFingerprint,
            result.quantityResolution?.selectedValueFingerprint
        )
        assertEquals("stronger", result.selectedQuantityEvidence?.evidenceId)
    }

    @Test
    fun `weak supporter cannot hide strong supporter of same resolved quantity`() {
        val price = priceFixture(requestId = "candidate")
        val quantity = QuantityNormalization.count(100)
        val weak =
            quantityCandidate(
                evidenceId = "a-weak",
                namespaceId = "a-weak-source",
                claimId = "weak-claim",
                quantity = quantity,
                authority = EvidenceAuthorityClass.USER_ASSERTED
            )
        val strong =
            quantityCandidate(
                evidenceId = "z-strong",
                namespaceId = "z-strong-source",
                claimId = "strong-claim",
                quantity = quantity,
                authority = EvidenceAuthorityClass.SOURCE_ASSERTED_METADATA
            )

        val result =
            evaluate(
                priceFixtures = listOf(price),
                candidatePriceRequestId = "candidate",
                quantities = listOf(weak, strong)
            )

        assertTrue(result.rankable)
        assertEquals(2, result.policyAttempts.size)
        assertEquals("a-weak", result.policyAttempts[0].quantityEvidenceId)
        assertTrue(
            EvidenceBackedUnitValueBlockReason.WEAK_QUANTITY_AUTHORITY in
                result.policyAttempts[0].result.blockReasons
        )
        assertEquals("z-strong", result.policyAttempts[1].quantityEvidenceId)
        assertTrue(result.policyAttempts[1].result.rankable)
        assertEquals("z-strong", result.selectedQuantityEvidence?.evidenceId)
    }

    @Test
    fun `quantity for another product cannot be joined to selected price`() {
        val price = priceFixture(requestId = "candidate")
        val wrongProduct =
            quantityCandidate(
                evidenceId = "wrong-product",
                quantity = QuantityNormalization.count(100),
                productKey = "gtin:4006381333931"
            )

        val result =
            evaluate(
                priceFixtures = listOf(price),
                candidatePriceRequestId = "candidate",
                quantities = listOf(wrongProduct)
            )

        assertFalse(result.rankable)
        assertTrue(
            ProductionUnitValueEligibilityBlocker.NO_RELEVANT_PACKAGE_QUANTITY in
                result.blockers
        )
        assertTrue(result.policyAttempts.isEmpty())
    }

    @Test
    fun `weak package quantity authority remains blocked by existing unit value policy`() {
        val price = priceFixture(requestId = "candidate")
        val weak =
            quantityCandidate(
                evidenceId = "weak",
                quantity = QuantityNormalization.count(100),
                authority = EvidenceAuthorityClass.USER_ASSERTED
            )

        val result =
            evaluate(
                priceFixtures = listOf(price),
                candidatePriceRequestId = "candidate",
                quantities = listOf(weak)
            )

        assertFalse(result.rankable)
        assertTrue(
            ProductionUnitValueEligibilityBlocker.UNIT_VALUE_POLICY_BLOCKED in
                result.blockers
        )
        assertEquals(1, result.policyAttempts.size)
        assertTrue(
            EvidenceBackedUnitValueBlockReason.WEAK_QUANTITY_AUTHORITY in
                result.policyAttempts.single().result.blockReasons
        )
    }

    @Test
    fun `materialized quantity must match resolved claim fingerprint`() {
        val price = priceFixture(requestId = "candidate")
        val claimed = QuantityNormalization.count(90)
        val materialized = QuantityNormalization.count(100)
        val mismatched =
            quantityCandidate(
                evidenceId = "mismatched",
                quantity = materialized,
                fingerprintQuantity = claimed
            )

        val result =
            evaluate(
                priceFixtures = listOf(price),
                candidatePriceRequestId = "candidate",
                quantities = listOf(mismatched)
            )

        assertFalse(result.rankable)
        assertTrue(
            ProductionUnitValueEligibilityBlocker.UNIT_VALUE_POLICY_BLOCKED in
                result.blockers
        )
        assertTrue(
            EvidenceBackedUnitValueBlockReason.QUANTITY_VALUE_MISMATCH in
                result.policyAttempts.single().result.blockReasons
        )
    }

    @Test
    fun `same namespace quantity claim id mutation fails closed`() {
        val price = priceFixture(requestId = "candidate")
        val first =
            quantityCandidate(
                evidenceId = "first",
                namespaceId = "same-namespace",
                claimId = "same-claim",
                quantity = QuantityNormalization.count(100)
            )
        val mutated =
            quantityCandidate(
                evidenceId = "mutated",
                namespaceId = "same-namespace",
                claimId = "same-claim",
                quantity = QuantityNormalization.count(90)
            )

        val result =
            evaluate(
                priceFixtures = listOf(price),
                candidatePriceRequestId = "candidate",
                quantities = listOf(first, mutated)
            )

        assertFalse(result.rankable)
        assertTrue(
            ProductionUnitValueEligibilityBlocker.PACKAGE_QUANTITY_CLAIM_ID_COLLISION in
                result.blockers
        )
        assertNull(result.quantityResolution)
        assertTrue(result.policyAttempts.isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `package quantity input is bounded`() {
        val price = priceFixture(requestId = "candidate")
        val quantities =
            (0..128).map { index ->
                quantityCandidate(
                    evidenceId = "quantity-$index",
                    namespaceId = "quantity-source-$index",
                    claimId = "quantity-claim-$index",
                    quantity = QuantityNormalization.count(100)
                )
            }

        evaluate(
            priceFixtures = listOf(price),
            candidatePriceRequestId = "candidate",
            quantities = quantities
        )
    }

    private fun evaluate(
        priceFixtures: List<PriceFixture>,
        candidatePriceRequestId: String,
        quantities: List<ProductPackageQuantityEvidenceCandidate>
    ): ProductionUnitValueEligibilityResult {
        val lifecycleRegistry = ProductionDatasetLifecycleRegistry()
        val dispositionRegistry = ProductionDatasetDispositionRegistry()

        priceFixtures.forEach { fixture ->
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

        return ProductionUnitValueEligibilityEvaluator.evaluate(
            priceRequests = priceFixtures.map { it.request },
            candidatePriceRequestId = candidatePriceRequestId,
            lifecycleRegistry = lifecycleRegistry,
            dispositionRegistry = dispositionRegistry,
            evaluatedAtEpochMillis = 2_000L,
            acceptancePolicy = acceptancePolicy,
            quantityCandidates = quantities
        )
    }

    private fun priceFixture(
        requestId: String,
        providerId: String = "provider-a",
        datasetId: String = "dataset-a",
        merchantKey: String = "merchant-a",
        priceMinor: Long = 800L,
        claimId: String = "price-a",
        suppliedGtin: String = SOURCE_GTIN
    ): PriceFixture {
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
                merchantKey = merchantKey,
                commerceChannelKey = "ONLINE",
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
                basisId = "lifecycle-$providerId-$datasetId"
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

        return PriceFixture(
            request = request,
            lifecycleRecord = lifecycleRecord,
            dispositionRecord = dispositionRecord
        )
    }

    private fun quantityCandidate(
        evidenceId: String,
        quantity: NormalizedQuantity,
        namespaceId: String = "quantity-source",
        claimId: String = "quantity-claim",
        productKey: String = PRODUCT_KEY,
        authority: EvidenceAuthorityClass = EvidenceAuthorityClass.SOURCE_ASSERTED_METADATA,
        fingerprintQuantity: NormalizedQuantity = quantity
    ): ProductPackageQuantityEvidenceCandidate {
        val namespace =
            EvidenceDatasetNamespace(
                id = namespaceId,
                displayName = namespaceId,
                licenseId = "quantity-rights-reviewed",
                storageBoundary = EvidenceStorageBoundary.OPEN_SHARE_ALIKE
            )
        val claim =
            EvidenceClaim(
                claimId = claimId,
                domain = EvidenceClaimDomain.PACKAGE_QUANTITY,
                valueFingerprint = EvidenceFingerprints.quantity(fingerprintQuantity),
                authority = authority,
                scope = EvidenceClaimScope(productKey = productKey),
                observedAtEpochMillis = 1_000L
            )

        return ProductPackageQuantityEvidenceCandidate(
            evidenceId = evidenceId,
            namespace = namespace,
            claim = claim,
            quantity = quantity
        )
    }

    private data class PriceFixture(
        val request: ProductionCurrentPriceEligibilityRequest,
        val lifecycleRecord: ProductionDatasetLifecycleRecord,
        val dispositionRecord: ProductionDatasetDispositionRecord
    )

    companion object {
        private const val SOURCE_GTIN = "036000291452"

        private val PRODUCT_KEY =
            requireNotNull(
                ProductionProductEvidenceKeyResolver.resolve(
                    providerId = EvidenceProviderId("quantity-test-provider"),
                    identity = SourceProductIdentity(gtin = SOURCE_GTIN)
                )
            ).value
    }
}
