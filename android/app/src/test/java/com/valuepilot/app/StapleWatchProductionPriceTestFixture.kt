package com.valuepilot.app

import com.valuepilot.core.AvailabilityEvidence
import com.valuepilot.core.AvailabilityState
import com.valuepilot.core.EvidenceAcceptancePolicy
import com.valuepilot.core.EvidenceAuthorityClass
import com.valuepilot.core.EvidenceChannel
import com.valuepilot.core.EvidenceClaimKind
import com.valuepilot.core.EvidenceDatasetNamespace
import com.valuepilot.core.EvidenceEnvironment
import com.valuepilot.core.EvidenceFreshnessPolicy
import com.valuepilot.core.EvidenceProvider
import com.valuepilot.core.EvidenceProviderId
import com.valuepilot.core.EvidenceStorageBoundary
import com.valuepilot.core.ImportedOfferCountryBasis
import com.valuepilot.core.ImportedPriceField
import com.valuepilot.core.ImportedSourceIdentity
import com.valuepilot.core.Money
import com.valuepilot.core.ProductionActivationProfiles
import com.valuepilot.core.ProductionAuthorizationState
import com.valuepilot.core.ProductionCurrentPriceClaimDescriptor
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
import com.valuepilot.core.ProductionProductEvidenceKey
import com.valuepilot.core.ProductionProductEvidenceKeyResolver
import com.valuepilot.core.ProviderDatasetOfferGeography
import com.valuepilot.core.ProviderOfferImportRecord
import com.valuepilot.core.ProviderProductionAuthorizationAssessment
import com.valuepilot.core.ShoppingSource
import com.valuepilot.core.ShoppingSourceId
import org.junit.Assert.assertEquals

internal class StapleWatchProductionPriceTestFixture {

    private val profile = ProductionActivationProfiles.CONSUMER_MOBILE_CATALOG
    val evaluatedAtEpochMillis: Long = 5_000L
    private val freshnessPolicy =
        EvidenceFreshnessPolicy(
            freshForMillis = 2_000L,
            staleAfterMillis = 5_000L,
            futureToleranceMillis = 100L
        )
    val acceptancePolicy =
        EvidenceAcceptancePolicy(
            freshnessPolicy = freshnessPolicy,
            rankAgingRealWorld = true
        )

    fun case(
        requestId: String,
        providerItemId: String,
        merchantKey: String = "merchant-a",
        locationKey: String? = "location-a",
        commerceChannelKey: String = "IN_STORE",
        priceMinor: Long = 500L,
        observedAtEpochMillis: Long = 4_500L,
        availability: AvailabilityState = AvailabilityState.IN_STOCK
    ): PriceCase {
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
                            rawValue = "reference-$priceMinor",
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
                priceRoles =
                    ProductionPriceFieldRoles(
                        currentPriceFieldName = "current_price",
                        referencePriceFieldName = "reference_price",
                        relationshipRule =
                            ProductionPriceRelationshipRule.CURRENT_MUST_NOT_EXCEED_REFERENCE
                    ),
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

        return PriceCase(
            request = request,
            lifecycleRecord = lifecycleRecord,
            dispositionRecord = dispositionRecord,
            productKey = productKey
        )
    }

    fun registries(cases: Collection<PriceCase>): Registries {
        val lifecycleRegistry = ProductionDatasetLifecycleRegistry()
        val dispositionRegistry = ProductionDatasetDispositionRegistry()

        cases.forEach { priceCase ->
            assertEquals(
                ProductionDatasetLifecycleWriteResult.ADDED,
                lifecycleRegistry.write(priceCase.lifecycleRecord)
            )
            assertEquals(
                ProductionDatasetDispositionWriteResult.ADDED,
                dispositionRegistry.write(priceCase.dispositionRecord)
            )
        }

        return Registries(
            lifecycle = lifecycleRegistry,
            disposition = dispositionRegistry
        )
    }

    internal data class PriceCase(
        val request: ProductionCurrentPriceEligibilityRequest,
        val lifecycleRecord: ProductionDatasetLifecycleRecord,
        val dispositionRecord: ProductionDatasetDispositionRecord,
        val productKey: ProductionProductEvidenceKey
    )

    internal data class Registries(
        val lifecycle: ProductionDatasetLifecycleRegistry,
        val disposition: ProductionDatasetDispositionRegistry
    )
}
