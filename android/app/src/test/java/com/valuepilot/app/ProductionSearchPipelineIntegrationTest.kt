package com.valuepilot.app

import com.valuepilot.core.AvailabilityEvidence
import com.valuepilot.core.AvailabilityState
import com.valuepilot.core.EvidenceAcceptancePolicy
import com.valuepilot.core.EvidenceAuthorityClass
import com.valuepilot.core.EvidenceChannel
import com.valuepilot.core.EvidenceClaim
import com.valuepilot.core.EvidenceClaimDomain
import com.valuepilot.core.EvidenceClaimKind
import com.valuepilot.core.EvidenceClaimScope
import com.valuepilot.core.EvidenceDatasetNamespace
import com.valuepilot.core.EvidenceEnvironment
import com.valuepilot.core.EvidenceFingerprints
import com.valuepilot.core.EvidenceFreshnessPolicy
import com.valuepilot.core.EvidenceProvider
import com.valuepilot.core.EvidenceProviderId
import com.valuepilot.core.EvidenceStorageBoundary
import com.valuepilot.core.ImportedOfferCountryBasis
import com.valuepilot.core.ImportedPriceField
import com.valuepilot.core.ImportedSourceIdentity
import com.valuepilot.core.Money
import com.valuepilot.core.NormalizedQuantity
import com.valuepilot.core.ProductPackageQuantityEvidenceCandidate
import com.valuepilot.core.ProductionActivationProfiles
import com.valuepilot.core.ProductionAuthorizationState
import com.valuepilot.core.ProductionBestValueCandidate
import com.valuepilot.core.ProductionBestValueComparisonKey
import com.valuepilot.core.ProductionBestValuePresentationEvaluator
import com.valuepilot.core.ProductionCurrentPriceClaimDescriptor
import com.valuepilot.core.ProductionCurrentPriceEligibilityBlocker
import com.valuepilot.core.ProductionCurrentPriceEligibilityRequest
import com.valuepilot.core.ProductionDatasetDispositionRecord
import com.valuepilot.core.ProductionDatasetDispositionRegistry
import com.valuepilot.core.ProductionDatasetDispositionState
import com.valuepilot.core.ProductionDatasetDispositionWriteResult
import com.valuepilot.core.ProductionDatasetLifecycleRecord
import com.valuepilot.core.ProductionDatasetLifecycleRegistry
import com.valuepilot.core.ProductionDatasetLifecycleState
import com.valuepilot.core.ProductionDatasetLifecycleWriteResult
import com.valuepilot.core.ProductionDatasetSnapshotRef
import com.valuepilot.core.ProductionGateAssessment
import com.valuepilot.core.ProductionPriceFieldRoles
import com.valuepilot.core.ProductionPriceRelationshipRule
import com.valuepilot.core.ProductionProductEvidenceKeyResolver
import com.valuepilot.core.ProductionUnitValueEligibilityBlocker
import com.valuepilot.core.ProviderDatasetOfferGeography
import com.valuepilot.core.ProviderOfferImportRecord
import com.valuepilot.core.ProviderProductionAuthorizationAssessment
import com.valuepilot.core.QuantityNormalization
import com.valuepilot.core.RateUnit
import com.valuepilot.core.ShoppingSource
import com.valuepilot.core.ShoppingSourceId
import com.valuepilot.core.SourceProductIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cross-module regression for the permanent production path:
 * raw provider evidence -> lifecycle/rights/geography/freshness -> price conflict
 * -> quantity conflict/authority -> exact unit value -> Best Value presentation
 * -> Android/application projection.
 *
 * No real provider data, Android networking, legacy ValueItem parsing, or
 * ValueEngine ranking participates in this test.
 */
class ProductionSearchPipelineIntegrationTest {

    private val profile = ProductionActivationProfiles.CONSUMER_MOBILE_CATALOG

    private val freshnessPolicy =
        EvidenceFreshnessPolicy(
            freshForMillis = 2_000L,
            staleAfterMillis = 5_000L,
            futureToleranceMillis = 100L
        )

    private val acceptancePolicy = EvidenceAcceptancePolicy(freshnessPolicy)

    @Test
    fun `raw production evidence reaches exact grouped android projection without legacy ranking`() {
        val lower = priceFixture("lower", 800L, GTIN_A)
        val higher = priceFixture("higher", 900L, GTIN_B)
        val mass = priceFixture("mass", 500L, GTIN_C)
        val blocked =
            priceFixture(
                requestId = "blocked",
                priceMinor = 700L,
                suppliedGtin = GTIN_D,
                availabilityState = AvailabilityState.OUT_OF_STOCK
            )

        val priceFixtures = listOf(lower, higher, mass, blocked)
        val lifecycleRegistry = ProductionDatasetLifecycleRegistry()
        val dispositionRegistry = ProductionDatasetDispositionRegistry()

        priceFixtures.forEach { fixture ->
            assertEquals(
                ProductionDatasetLifecycleWriteResult.ADDED,
                lifecycleRegistry.write(fixture.lifecycleRecord)
            )
            assertEquals(
                ProductionDatasetDispositionWriteResult.ADDED,
                dispositionRegistry.write(fixture.dispositionRecord)
            )
        }

        val quantities =
            listOf(
                quantityCandidate("q-lower", GTIN_A, QuantityNormalization.count(100)),
                quantityCandidate("q-higher", GTIN_B, QuantityNormalization.count(100)),
                quantityCandidate("q-mass", GTIN_C, QuantityNormalization.grams(500)),
                quantityCandidate("q-blocked", GTIN_D, QuantityNormalization.count(100))
            )

        val presentation =
            ProductionBestValuePresentationEvaluator.evaluate(
                priceRequests = priceFixtures.map { it.request },
                candidates =
                    listOf(
                        ProductionBestValueCandidate("higher", "higher"),
                        ProductionBestValueCandidate("mass", "mass"),
                        ProductionBestValueCandidate("blocked", "blocked"),
                        ProductionBestValueCandidate("lower", "lower")
                    ),
                lifecycleRegistry = lifecycleRegistry,
                dispositionRegistry = dispositionRegistry,
                evaluatedAtEpochMillis = 2_000L,
                acceptancePolicy = acceptancePolicy,
                quantityCandidates = quantities
            )

        val projected = ProductionSearchUiProjector.project(presentation.snapshot)

        assertEquals(2_000L, projected.state.evaluatedAtEpochMillis)
        assertEquals(2, projected.state.groups.size)

        val itemGroup =
            projected.state.groups.single {
                it.key == ProductionBestValueComparisonKey("CAD", RateUnit.ITEM)
            }
        assertTrue(itemGroup.meaningfulComparison)
        assertEquals(listOf("lower", "higher"), itemGroup.rows.map { it.candidateId })
        assertEquals(listOf(1, 2), itemGroup.rows.map { it.valueRank })
        assertTrue(itemGroup.rows[0].bestValue)
        assertFalse(itemGroup.rows[1].bestValue)
        assertEquals("8.00 CAD", itemGroup.rows[0].priceText)
        assertEquals("100 items", itemGroup.rows[0].quantityText)
        assertEquals("0.08 CAD/item", itemGroup.rows[0].unitRateText)
        assertEquals("9.00 CAD", itemGroup.rows[1].priceText)
        assertEquals("0.09 CAD/item", itemGroup.rows[1].unitRateText)

        val massGroup =
            projected.state.groups.single {
                it.key == ProductionBestValueComparisonKey("CAD", RateUnit.KILOGRAM)
            }
        assertFalse(massGroup.meaningfulComparison)
        assertEquals("mass", massGroup.rows.single().candidateId)
        assertFalse(massGroup.rows.single().bestValue)
        assertEquals("500 g", massGroup.rows.single().quantityText)
        assertEquals("10 CAD/kg", massGroup.rows.single().unitRateText)

        val blockedRow = projected.state.blocked.single()
        assertEquals("blocked", blockedRow.candidateId)
        assertEquals("Reference only — not eligible for Best Value", blockedRow.notice)
        assertTrue("unit:PRICE_STAGE_BLOCKED" in blockedRow.reasonCodes)
        assertTrue("price:CANDIDATE_NOT_ACCEPTANCE_RANKABLE" in blockedRow.reasonCodes)

        val lowerPresentation =
            presentation.snapshot.groups
                .flatMap { it.items }
                .single { it.candidateId == "lower" }
        assertSame(lowerPresentation, projected.rankedByCandidateId.getValue("lower"))
        assertEquals("claim-lower", lowerPresentation.evidenceLink.priceClaimId)
        assertEquals("claim-q-lower", lowerPresentation.evidenceLink.quantityClaimId)
        assertEquals(Money(800L, "CAD"), lowerPresentation.currentPrice)

        val blockedPresentation = presentation.snapshot.blockedItems.single()
        assertSame(blockedPresentation, projected.blockedByCandidateId.getValue("blocked"))
        assertTrue(
            ProductionUnitValueEligibilityBlocker.PRICE_STAGE_BLOCKED in
                blockedPresentation.unitValueBlockers
        )
        assertTrue(
            ProductionCurrentPriceEligibilityBlocker.CANDIDATE_NOT_ACCEPTANCE_RANKABLE in
                blockedPresentation.priceBlockers
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
                displayName = "Provider $requestId"
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
                licenseId = "synthetic-reviewed-rights",
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
                productName = "Synthetic $requestId",
                sourcePriceFields =
                    listOf(
                        ImportedPriceField(
                            sourceFieldName = "sale_price",
                            rawValue = "synthetic-price-$priceMinor",
                            parsedAmount = Money(priceMinor, "CAD")
                        ),
                        ImportedPriceField(
                            sourceFieldName = "retail_price",
                            rawValue = "synthetic-reference-$referenceMinor",
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
        val priceRoles =
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
                                basisId = "synthetic-basis-${gate.name.lowercase()}-$requestId"
                            )
                        }
            )
        val geography =
            ProviderDatasetOfferGeography(
                providerId = provider.id,
                datasetNamespaceId = dataset.id,
                countryCode = "CA",
                basis = ImportedOfferCountryBasis.EXPLICIT_DATASET_COUNTRY,
                basisId = "synthetic-ca-$requestId"
            )
        val descriptor =
            ProductionCurrentPriceClaimDescriptor(
                claimId = "claim-$requestId",
                merchantKey = "merchant-$requestId",
                commerceChannelKey = "ONLINE",
                authority = EvidenceAuthorityClass.SOURCE_ASSERTED_METADATA,
                authorityBasisId = "synthetic-authority-$requestId"
            )
        val lifecycleRecord =
            ProductionDatasetLifecycleRecord(
                snapshot = snapshot,
                activationProfileId = profile.id,
                revision = 1L,
                state = ProductionDatasetLifecycleState.ACTIVE,
                effectiveAtEpochMillis = 500L,
                basisId = "synthetic-lifecycle-$requestId"
            )
        val dispositionRecord =
            ProductionDatasetDispositionRecord(
                namespace = dataset,
                revision = 1L,
                state = ProductionDatasetDispositionState.RETAINED,
                basisId = "synthetic-retained-$requestId"
            )
        val request =
            ProductionCurrentPriceEligibilityRequest(
                requestId = requestId,
                record = record,
                priceRoles = priceRoles,
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
                licenseId = "synthetic-quantity-rights",
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
                providerId = EvidenceProviderId("synthetic-quantity-provider"),
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
        private const val GTIN_C = "012345678905"
        private const val GTIN_D = "1234567890128"
    }
}
