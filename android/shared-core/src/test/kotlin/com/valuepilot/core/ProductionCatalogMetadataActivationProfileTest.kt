package com.valuepilot.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductionCatalogMetadataActivationProfileTest {

    private val providerId = EvidenceProviderId("metadata-profile-test")
    private val datasetId = "metadata-profile-test-feed"

    @Test
    fun `metadata profile requires catalog rights geography and dataset recency but no price or network gates`() {
        val profile = ProductionActivationProfiles.CONSUMER_MOBILE_CATALOG_METADATA

        assertEquals("consumer-mobile-catalog-metadata", profile.id)
        assertEquals(
            setOf(
                ProductionAuthorizationGate.DATA_ACCESS_AUTHORIZED,
                ProductionAuthorizationGate.CONSUMER_DISPLAY_AUTHORIZED,
                ProductionAuthorizationGate.CACHE_AUTHORIZED,
                ProductionAuthorizationGate.INDEX_AUTHORIZED,
                ProductionAuthorizationGate.MOBILE_APP_AUTHORIZED,
                ProductionAuthorizationGate.RETENTION_DELETION_POLICY_DEFINED,
                ProductionAuthorizationGate.OFFER_GEOGRAPHY_VALIDATED,
                ProductionAuthorizationGate.DATASET_RECENCY_POLICY_DEFINED
            ),
            profile.requiredGates
        )

        assertFalse(ProductionAuthorizationGate.PRICE_SEMANTICS_VALIDATED in profile.requiredGates)
        assertFalse(ProductionAuthorizationGate.OFFER_FRESHNESS_POLICY_DEFINED in profile.requiredGates)
        assertFalse(ProductionAuthorizationGate.AFFILIATE_LINK_USE_AUTHORIZED in profile.requiredGates)
        assertFalse(ProductionAuthorizationGate.INSTALLED_SOFTWARE_NETWORK_APPROVED in profile.requiredGates)
        assertFalse(ProductionAuthorizationGate.ADVERTISER_DISTRIBUTION_APPROVED in profile.requiredGates)
        assertFalse(ProductionAuthorizationGate.TRACKING_PRIVACY_READY in profile.requiredGates)
    }

    @Test
    fun `price bearing catalog is exactly metadata profile plus price semantics and offer freshness`() {
        val metadata = ProductionActivationProfiles.CONSUMER_MOBILE_CATALOG_METADATA
        val priced = ProductionActivationProfiles.CONSUMER_MOBILE_CATALOG

        assertEquals(
            metadata.requiredGates +
                setOf(
                    ProductionAuthorizationGate.PRICE_SEMANTICS_VALIDATED,
                    ProductionAuthorizationGate.OFFER_FRESHNESS_POLICY_DEFINED
                ),
            priced.requiredGates
        )
        assertTrue(ProductionAuthorizationGate.DATASET_RECENCY_POLICY_DEFINED in priced.requiredGates)
    }

    @Test
    fun `unknown price semantics and offer freshness do not block metadata only activation`() {
        val metadata = ProductionActivationProfiles.CONSUMER_MOBILE_CATALOG_METADATA
        val assessment =
            ProviderProductionAuthorizationAssessment(
                providerId = providerId,
                datasetNamespaceId = datasetId,
                gates =
                    metadata.requiredGates.map(::satisfied) +
                        listOf(
                            unknown(ProductionAuthorizationGate.PRICE_SEMANTICS_VALIDATED),
                            unknown(ProductionAuthorizationGate.OFFER_FRESHNESS_POLICY_DEFINED)
                        )
            )

        val decision = ProductionAuthorizationEvaluator.evaluate(assessment, metadata)

        assertTrue(decision.authorized)
        assertEquals(metadata.requiredGates, decision.satisfiedGates)
        assertTrue(decision.blockingGates.isEmpty())
    }

    @Test
    fun `same assessment remains blocked for price bearing catalog`() {
        val metadata = ProductionActivationProfiles.CONSUMER_MOBILE_CATALOG_METADATA
        val priced = ProductionActivationProfiles.CONSUMER_MOBILE_CATALOG
        val assessment =
            ProviderProductionAuthorizationAssessment(
                providerId = providerId,
                datasetNamespaceId = datasetId,
                gates =
                    metadata.requiredGates.map(::satisfied) +
                        listOf(
                            unknown(ProductionAuthorizationGate.PRICE_SEMANTICS_VALIDATED),
                            unknown(ProductionAuthorizationGate.OFFER_FRESHNESS_POLICY_DEFINED)
                        )
            )

        val decision = ProductionAuthorizationEvaluator.evaluate(assessment, priced)

        assertFalse(decision.authorized)
        assertEquals(
            setOf(
                ProductionAuthorizationGate.PRICE_SEMANTICS_VALIDATED,
                ProductionAuthorizationGate.OFFER_FRESHNESS_POLICY_DEFINED
            ),
            decision.unknownGates
        )
    }

    @Test
    fun `dataset recency remains mandatory even when no price facts are shown`() {
        val profile = ProductionActivationProfiles.CONSUMER_MOBILE_CATALOG_METADATA
        val assessment =
            ProviderProductionAuthorizationAssessment(
                providerId = providerId,
                datasetNamespaceId = datasetId,
                gates =
                    profile.requiredGates
                        .filterNot { it == ProductionAuthorizationGate.DATASET_RECENCY_POLICY_DEFINED }
                        .map(::satisfied) +
                        unknown(ProductionAuthorizationGate.DATASET_RECENCY_POLICY_DEFINED)
            )

        val decision = ProductionAuthorizationEvaluator.evaluate(assessment, profile)

        assertFalse(decision.authorized)
        assertEquals(
            setOf(ProductionAuthorizationGate.DATASET_RECENCY_POLICY_DEFINED),
            decision.unknownGates
        )
    }

    @Test
    fun `network link profile still inherits every price bearing catalog gate`() {
        val priced = ProductionActivationProfiles.CONSUMER_MOBILE_CATALOG
        val network = ProductionActivationProfiles.CONSUMER_MOBILE_CATALOG_WITH_NETWORK_LINKS

        assertTrue(network.requiredGates.containsAll(priced.requiredGates))
        assertEquals(
            priced.requiredGates +
                setOf(
                    ProductionAuthorizationGate.AFFILIATE_LINK_USE_AUTHORIZED,
                    ProductionAuthorizationGate.INSTALLED_SOFTWARE_NETWORK_APPROVED,
                    ProductionAuthorizationGate.ADVERTISER_DISTRIBUTION_APPROVED,
                    ProductionAuthorizationGate.TRACKING_PRIVACY_READY
                ),
            network.requiredGates
        )
    }

    private fun satisfied(gate: ProductionAuthorizationGate): ProductionGateAssessment =
        ProductionGateAssessment(
            gate = gate,
            state = ProductionAuthorizationState.SATISFIED,
            basisId = "evidence-${gate.name.lowercase()}"
        )

    private fun unknown(gate: ProductionAuthorizationGate): ProductionGateAssessment =
        ProductionGateAssessment(
            gate = gate,
            state = ProductionAuthorizationState.UNKNOWN,
            basisId = "unknown-${gate.name.lowercase()}"
        )
}
