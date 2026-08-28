package com.valuepilot.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductionBestValueRankingTest {

    private val profile = ProductionActivationProfiles.CONSUMER_MOBILE_CATALOG

    private val freshnessPolicy =
        EvidenceFreshnessPolicy(
            freshForMillis = 2_000L,
            staleAfterMillis = 5_000L,
            futureToleranceMillis = 100L
        )

    private val acceptancePolicy = EvidenceAcceptancePolicy(freshnessPolicy = freshnessPolicy)

    @Test
    fun `same currency and rate unit rank by lower exact unit rate`() {
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

        assertEquals(2, result.rankedCandidateCount)
        assertEquals(0, result.blockedCandidateCount)
        val group = result.groups.single()
        assertEquals(ProductionBestValueComparisonKey("CAD", RateUnit.ITEM), group.key)
        assertTrue(group.hasMeaningfulComparison)
        assertEquals(listOf("lower", "higher"), group.rankedCandidates.map { it.candidateId })
        assertEquals(listOf(1, 2), group.rankedCandidates.map { it.valueRank })
        assertEquals(listOf(80_000L, 90_000L), group.rankedCandidates.map { it.rate.currencyMicrosPerUnit })
        assertEquals(listOf("lower"), group.bestValueCandidateIds)
    }

    @Test
    fun `equal exact rates share value rank and stable id only controls display order`() {
        val zeta = priceFixture("zeta-price", priceMinor = 800L, suppliedGtin = GTIN_A)
        val alpha = priceFixture("alpha-price", priceMinor = 400L, suppliedGtin = GTIN_B)
        val later = priceFixture("later-price", priceMinor = 900L, suppliedGtin = GTIN_C)
        val quantities =
            listOf(
                quantityCandidate("q-zeta", GTIN_A, QuantityNormalization.count(100)),
                quantityCandidate("q-alpha", GTIN_B, QuantityNormalization.count(50)),
                quantityCandidate("q-later", GTIN_C, QuantityNormalization.count(100))
            )

        val result =
            evaluate(
                priceFixtures = listOf(zeta, alpha, later),
                candidates =
                    listOf(
                        ProductionBestValueCandidate("zeta", "zeta-price"),
                        ProductionBestValueCandidate("later", "later-price"),
                        ProductionBestValueCandidate("alpha", "alpha-price")
                    ),
                quantities = quantities
            )

        val group = result.groups.single()
        assertEquals(listOf("alpha", "zeta", "later"), group.rankedCandidates.map { it.candidateId })
        assertEquals(listOf(1, 1, 2), group.rankedCandidates.map { it.valueRank })
        assertEquals(listOf(1, 2, 3), group.rankedCandidates.map { it.deterministicOrder })
        assertEquals(listOf("alpha", "zeta"), group.bestValueCandidateIds)
    }

    @Test
    fun `different rate units form separate groups and singletons do not claim best value`() {
        val countPrice = priceFixture("count-price", priceMinor = 800L, suppliedGtin = GTIN_A)
        val massPrice = priceFixture("mass-price", priceMinor = 500L, suppliedGtin = GTIN_B)
        val quantities =
            listOf(
                quantityCandidate("q-count", GTIN_A, QuantityNormalization.count(100)),
                quantityCandidate("q-mass", GTIN_B, QuantityNormalization.grams(500))
            )

        val result =
            evaluate(
                priceFixtures = listOf(countPrice, massPrice),
                candidates =
                    listOf(
                        ProductionBestValueCandidate("count", "count-price"),
                        ProductionBestValueCandidate("mass", "mass-price")
                    ),
                quantities = quantities
            )

        assertEquals(2, result.groups.size)
        val itemGroup = result.groups.single { it.key.rateUnit == RateUnit.ITEM }
        val kgGroup = result.groups.single { it.key.rateUnit == RateUnit.KILOGRAM }
        assertFalse(itemGroup.hasMeaningfulComparison)
        assertFalse(kgGroup.hasMeaningfulComparison)
        assertTrue(itemGroup.bestValueCandidateIds.isEmpty())
        assertTrue(kgGroup.bestValueCandidateIds.isEmpty())
    }

    @Test
    fun `different currencies form separate groups and are never cross ranked`() {
        val cad = priceFixture("cad-price", priceMinor = 800L, suppliedGtin = GTIN_A, currencyCode = "CAD")
        val usd = priceFixture("usd-price", priceMinor = 700L, suppliedGtin = GTIN_B, currencyCode = "USD")
        val quantities =
            listOf(
                quantityCandidate("q-cad", GTIN_A, QuantityNormalization.count(100)),
                quantityCandidate("q-usd", GTIN_B, QuantityNormalization.count(100))
            )

        val result =
            evaluate(
                priceFixtures = listOf(cad, usd),
                candidates =
                    listOf(
                        ProductionBestValueCandidate("cad", "cad-price"),
                        ProductionBestValueCandidate("usd", "usd-price")
                    ),
                quantities = quantities
            )

        assertEquals(2, result.groups.size)
        assertEquals(setOf("CAD", "USD"), result.groups.map { it.key.currencyCode }.toSet())
        assertTrue(result.groups.all { !it.hasMeaningfulComparison && it.bestValueCandidateIds.isEmpty() })
    }

    @Test
    fun `blocked unit value candidate remains explainable but receives no rank`() {
        val good = priceFixture("good-price", priceMinor = 800L, suppliedGtin = GTIN_A)
        val blocked =
            priceFixture(
                "blocked-price",
                priceMinor = 700L,
                suppliedGtin = GTIN_B,
                availabilityState = AvailabilityState.OUT_OF_STOCK
            )
        val quantities =
            listOf(
                quantityCandidate("q-good", GTIN_A, QuantityNormalization.count(100)),
                quantityCandidate("q-blocked", GTIN_B, QuantityNormalization.count(100))
            )

        val result =
            evaluate(
                priceFixtures = listOf(good, blocked),
                candidates =
                    listOf(
                        ProductionBestValueCandidate("blocked", "blocked-price"),
                        ProductionBestValueCandidate("good", "good-price")
                    ),
                quantities = quantities
            )

        assertEquals(1, result.rankedCandidateCount)
        assertEquals(1, result.blockedCandidateCount)
        assertEquals("blocked", result.blockedCandidates.single().candidateId)
        assertTrue(
            ProductionUnitValueEligibilityBlocker.PRICE_STAGE_BLOCKED in
                result.blockedCandidates.single().unitValueEligibility.blockers
        )
        val group = result.groups.single()
        assertEquals(listOf("good"), group.rankedCandidates.map { it.candidateId })
        assertTrue(group.bestValueCandidateIds.isEmpty())
    }

    @Test
    fun `ranking is deterministic when input order changes`() {
        val first = priceFixture("first-price", priceMinor = 800L, suppliedGtin = GTIN_A)
        val second = priceFixture("second-price", priceMinor = 400L, suppliedGtin = GTIN_B)
        val quantities =
            listOf(
                quantityCandidate("q-first", GTIN_A, QuantityNormalization.count(100)),
                quantityCandidate("q-second", GTIN_B, QuantityNormalization.count(50))
            )
        val candidates =
            listOf(
                ProductionBestValueCandidate("z-id", "first-price"),
                ProductionBestValueCandidate("a-id", "second-price")
            )

        val forward = evaluate(listOf(first, second), candidates, quantities)
        val reversed = evaluate(listOf(second, first), candidates.reversed(), quantities.reversed())

        assertEquals(
            forward.groups.single().rankedCandidates.map { it.candidateId to it.valueRank },
            reversed.groups.single().rankedCandidates.map { it.candidateId to it.valueRank }
        )
        assertEquals(
            forward.groups.single().bestValueCandidateIds,
            reversed.groups.single().bestValueCandidateIds
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `duplicate candidate ids are rejected`() {
        val first = priceFixture("first-price", suppliedGtin = GTIN_A)
        val second = priceFixture("second-price", suppliedGtin = GTIN_B)

        evaluate(
            priceFixtures = listOf(first, second),
            candidates =
                listOf(
                    ProductionBestValueCandidate("duplicate", "first-price"),
                    ProductionBestValueCandidate("duplicate", "second-price")
                ),
            quantities = emptyList()
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `duplicate price request references are rejected`() {
        val first = priceFixture("first-price", suppliedGtin = GTIN_A)

        evaluate(
            priceFixtures = listOf(first),
            candidates =
                listOf(
                    ProductionBestValueCandidate("a", "first-price"),
                    ProductionBestValueCandidate("b", "first-price")
                ),
            quantities = emptyList()
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `ranking candidate input is bounded`() {
        val candidates =
            (0..128).map { index ->
                ProductionBestValueCandidate(
                    candidateId = "candidate-$index",
                    candidatePriceRequestId = "price-$index"
                )
            }

        evaluate(
            priceFixtures = emptyList(),
            candidates = candidates,
            quantities = emptyList()
        )
    }

    private fun evaluate(
        priceFixtures: List<PriceFixture>,
        candidates: List<ProductionBestValueCandidate>,
        quantities: List<ProductPackageQuantityEvidenceCandidate>
    ): ProductionBestValueRankingResult {
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

        return ProductionBestValueRankingEvaluator.evaluate(
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
        priceMinor: Long = 800L,
        suppliedGtin: String,
        currencyCode: String = "CAD",
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
                            parsedAmount = Money(priceMinor, currencyCode)
                        ),
                        ImportedPriceField(
                            sourceFieldName = "retail_price",
                            rawValue = "reference-$referenceMinor",
                            parsedAmount = Money(referenceMinor, currencyCode)
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
        private const val GTIN_C = "5901234123457"
    }
}
