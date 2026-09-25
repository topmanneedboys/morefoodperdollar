package com.valuepilot.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductionBestValuePresentationTest {

    private val profile = ProductionActivationProfiles.CONSUMER_MOBILE_CATALOG

    private val freshnessPolicy =
        EvidenceFreshnessPolicy(
            freshForMillis = 2_000L,
            staleAfterMillis = 5_000L,
            futureToleranceMillis = 100L
        )

    private val acceptancePolicy = EvidenceAcceptancePolicy(freshnessPolicy = freshnessPolicy)

    @Test
    fun `presentation snapshot flattens exact auditable fields from verified ranking`() {
        val lower = priceFixture("lower-price", priceMinor = 800L, suppliedGtin = GTIN_A)
        val higher = priceFixture("higher-price", priceMinor = 900L, suppliedGtin = GTIN_B)
        val quantities =
            listOf(
                quantityCandidate("q-lower", GTIN_A, QuantityNormalization.count(100)),
                quantityCandidate("q-higher", GTIN_B, QuantityNormalization.count(100))
            )

        val result =
            evaluate(
                priceFixtures = listOf(lower, higher),
                candidates =
                    listOf(
                        ProductionBestValueCandidate("higher", "higher-price"),
                        ProductionBestValueCandidate("lower", "lower-price")
                    ),
                quantities = quantities
            )

        assertEquals(2_000L, result.snapshot.evaluatedAtEpochMillis)
        assertTrue(result.snapshot.blockedItems.isEmpty())
        val group = result.snapshot.groups.single()
        assertTrue(group.meaningfulComparison)
        assertEquals(ProductionBestValueComparisonKey("CAD", RateUnit.ITEM), group.key)
        assertEquals(listOf("lower", "higher"), group.items.map { it.candidateId })

        val row = group.items.first()
        assertEquals("Example lower-price", row.productName)
        assertEquals("provider-lower-price", row.providerDisplayName)
        assertEquals("Source lower-price", row.sourceDisplayName)
        assertEquals("merchant-lower-price", row.merchantKey)
        assertEquals("ONLINE", row.commerceChannelKey)
        assertEquals("CA", row.offerCountryCode)
        assertEquals(Money(800L, "CAD"), row.currentPrice)
        assertEquals(Money(1_000L, "CAD"), row.referencePrice)
        assertEquals(QuantityNormalization.count(100), row.quantity)
        assertEquals(UnitRate("CAD", 80_000L, RateUnit.ITEM), row.unitRate)
        assertEquals(AvailabilityState.IN_STOCK, row.availabilityState)
        assertEquals(1, row.valueRank)
        assertEquals(1, row.deterministicOrder)
        assertTrue(row.bestValue)
        assertEquals("dataset-lower-price", row.evidenceLink.priceDatasetNamespaceId)
        assertEquals("snapshot-lower-price", row.evidenceLink.priceSnapshotId)
        assertEquals("claim-lower-price", row.evidenceLink.priceClaimId)
        assertEquals("quantity-q-lower", row.evidenceLink.quantityDatasetNamespaceId)
        assertEquals("claim-q-lower", row.evidenceLink.quantityClaimId)
        assertEquals("q-lower", row.evidenceLink.quantityEvidenceId)
    }

    @Test
    fun `singleton rankable group never presents a best value badge`() {
        val price = priceFixture("only-price", priceMinor = 800L, suppliedGtin = GTIN_A)
        val result =
            evaluate(
                priceFixtures = listOf(price),
                candidates = listOf(ProductionBestValueCandidate("only", "only-price")),
                quantities = listOf(quantityCandidate("q-only", GTIN_A, QuantityNormalization.count(100)))
            )

        val group = result.snapshot.groups.single()
        assertFalse(group.meaningfulComparison)
        assertEquals(1, group.items.size)
        assertFalse(group.items.single().bestValue)
    }

    @Test
    fun `blocked candidate is projected as explanation data and never as ranked row`() {
        val blocked =
            priceFixture(
                "blocked-price",
                priceMinor = 700L,
                suppliedGtin = GTIN_A,
                availabilityState = AvailabilityState.OUT_OF_STOCK
            )
        val result =
            evaluate(
                priceFixtures = listOf(blocked),
                candidates = listOf(ProductionBestValueCandidate("blocked", "blocked-price")),
                quantities = listOf(quantityCandidate("q-blocked", GTIN_A, QuantityNormalization.count(100)))
            )

        assertTrue(result.snapshot.groups.isEmpty())
        val blockedRow = result.snapshot.blockedItems.single()
        assertEquals("blocked", blockedRow.candidateId)
        assertTrue(ProductionUnitValueEligibilityBlocker.PRICE_STAGE_BLOCKED in blockedRow.unitValueBlockers)
        assertTrue(
            ProductionCurrentPriceEligibilityBlocker.CANDIDATE_NOT_ACCEPTANCE_RANKABLE in
                blockedRow.priceBlockers
        )
    }

    @Test
    fun `presentation evaluator rechecks current lifecycle instead of trusting an older result`() {
        val price = priceFixture("revoked-price", priceMinor = 800L, suppliedGtin = GTIN_A)
        val result =
            evaluate(
                priceFixtures = listOf(price),
                candidates = listOf(ProductionBestValueCandidate("revoked", "revoked-price")),
                quantities = listOf(quantityCandidate("q-revoked", GTIN_A, QuantityNormalization.count(100))),
                lifecycleMutation = { registry ->
                    assertEquals(
                        ProductionDatasetLifecycleWriteResult.UPDATED,
                        registry.write(
                            price.lifecycleRecord.copy(
                                revision = 2L,
                                state = ProductionDatasetLifecycleState.REVOKED,
                                effectiveAtEpochMillis = 1_500L,
                                basisId = "revoked-before-presentation"
                            )
                        )
                    )
                }
            )

        assertTrue(result.snapshot.groups.isEmpty())
        val blockedRow = result.snapshot.blockedItems.single()
        assertTrue(ProductionUnitValueEligibilityBlocker.PRICE_STAGE_BLOCKED in blockedRow.unitValueBlockers)
        assertTrue(
            ProductionCurrentPriceEligibilityBlocker.CANDIDATE_CLAIM_BLOCKED in
                blockedRow.priceBlockers
        )
    }

    private fun evaluate(
        priceFixtures: List<PriceFixture>,
        candidates: List<ProductionBestValueCandidate>,
        quantities: List<ProductPackageQuantityEvidenceCandidate>,
        lifecycleMutation: (ProductionDatasetLifecycleRegistry) -> Unit = {}
    ): ProductionBestValuePresentationResult {
        val lifecycleRegistry = ProductionDatasetLifecycleRegistry()
        val dispositionRegistry = ProductionDatasetDispositionRegistry()

        priceFixtures.forEach { fixture ->
            val lifecycleResult = lifecycleRegistry.write(fixture.lifecycleRecord)
            if (lifecycleResult != ProductionDatasetLifecycleWriteResult.ADDED) {
                assertEquals(ProductionDatasetLifecycleWriteResult.DUPLICATE, lifecycleResult)
            }

            val dispositionResult = dispositionRegistry.write(fixture.dispositionRecord)
            if (dispositionResult != ProductionDatasetDispositionWriteResult.ADDED) {
                assertEquals(ProductionDatasetDispositionWriteResult.DUPLICATE, dispositionResult)
            }
        }

        lifecycleMutation(lifecycleRegistry)

        return ProductionBestValuePresentationEvaluator.evaluate(
            priceRequests = priceFixtures.map { it.request },
            candidates = candidates,
            lifecycleRegistry = lifecycleRegistry,
            dispositionRegistry = dispositionRegistry,
            evaluatedAtEpochMillis = 2_000L,
            acceptancePolicy = acceptancePolicy,
            quantityCandidates = quantities
        )
    }

    private fun priceFixture(
        requestId: String,
        priceMinor: Long,
        suppliedGtin: String,
        availabilityState: AvailabilityState = AvailabilityState.IN_STOCK
    ): PriceFixture {
        val providerId = "provider-$requestId"
        val datasetId = "dataset-$requestId"
        val provider =
            EvidenceProvider(
                id = EvidenceProviderId(providerId),
                displayName = providerId
            )
        val source =
            ShoppingSource(
                id = ShoppingSourceId("source-$requestId"),
                displayName = "Source $requestId"
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
                snapshotId = "snapshot-$requestId"
            )
        val referenceMinor = maxOf(1_000L, priceMinor + 100L)
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
                productName = "Example $requestId",
                sourcePriceFields =
                    listOf(
                        ImportedPriceField(
                            sourceFieldName = "sale_price",
                            rawValue = "price-$priceMinor",
                            parsedAmount = Money(priceMinor, "CAD")
                        ),
                        ImportedPriceField(
                            sourceFieldName = "retail_price",
                            rawValue = "reference-$referenceMinor",
                            parsedAmount = Money(referenceMinor, "CAD")
                        )
                    ),
                availability =
                    AvailabilityEvidence(
                        state = availabilityState,
                        claimKind = EvidenceClaimKind.SOURCE_ASSERTED,
                        observedAtEpochMillis = 1_000L
                    ),
                priceObservedAtEpochMillis = 1_000L
            )
        val roles =
            ProductionPriceFieldRoles(
                currentPriceFieldName = "sale_price",
                referencePriceFieldName = "retail_price",
                relationshipRule = ProductionPriceRelationshipRule.CURRENT_MUST_NOT_EXCEED_REFERENCE
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
                merchantKey = "merchant-$requestId",
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

        return PriceFixture(request, lifecycleRecord, dispositionRecord)
    }

    private fun quantityCandidate(
        evidenceId: String,
        suppliedGtin: String,
        quantity: NormalizedQuantity
    ): ProductPackageQuantityEvidenceCandidate {
        val namespaceId = "quantity-$evidenceId"
        val namespace =
            EvidenceDatasetNamespace(
                id = namespaceId,
                displayName = namespaceId,
                licenseId = "quantity-rights-reviewed",
                storageBoundary = EvidenceStorageBoundary.OPEN_SHARE_ALIKE
            )
        val claim =
            EvidenceClaim(
                claimId = "claim-$evidenceId",
                domain = EvidenceClaimDomain.PACKAGE_QUANTITY,
                valueFingerprint = EvidenceFingerprints.quantity(quantity),
                authority = EvidenceAuthorityClass.SOURCE_ASSERTED_METADATA,
                scope = EvidenceClaimScope(productKey = productKey(suppliedGtin)),
                observedAtEpochMillis = 1_000L
            )

        return ProductPackageQuantityEvidenceCandidate(
            evidenceId = evidenceId,
            namespace = namespace,
            claim = claim,
            quantity = quantity
        )
    }

    private fun productKey(gtin: String): String =
        requireNotNull(
            ProductionProductEvidenceKeyResolver.resolve(
                providerId = EvidenceProviderId("quantity-test-provider"),
                identity = SourceProductIdentity(gtin = gtin)
            )
        ).value

    private data class PriceFixture(
        val request: ProductionCurrentPriceEligibilityRequest,
        val lifecycleRecord: ProductionDatasetLifecycleRecord,
        val dispositionRecord: ProductionDatasetDispositionRecord
    )

    companion object {
        private const val GTIN_A = "036000291452"
        private const val GTIN_B = "4006381333931"
    }
}
