package com.valuepilot.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ProviderProductionAuthorizationTest {

    private val providerId = EvidenceProviderId("provider-test")
    private val datasetId = "provider-test-feed"

    private fun satisfiedAssessment(
        gate: ProductionAuthorizationGate
    ): ProductionGateAssessment =
        ProductionGateAssessment(
            gate = gate,
            state = ProductionAuthorizationState.SATISFIED,
            basisId = "evidence-${gate.name.lowercase()}"
        )

    private fun assessmentFor(
        profile: ProductionActivationProfile,
        overrides: Map<ProductionAuthorizationGate, ProductionAuthorizationState> = emptyMap()
    ): ProviderProductionAuthorizationAssessment =
        ProviderProductionAuthorizationAssessment(
            providerId = providerId,
            datasetNamespaceId = datasetId,
            gates =
                profile.requiredGates
                    .sortedBy { it.ordinal }
                    .map { gate ->
                        val state = overrides[gate] ?: ProductionAuthorizationState.SATISFIED
                        ProductionGateAssessment(
                            gate = gate,
                            state = state,
                            basisId =
                                if (
                                    state == ProductionAuthorizationState.PENDING ||
                                    state == ProductionAuthorizationState.UNKNOWN
                                ) {
                                    "checkpoint-${gate.name.lowercase()}"
                                } else {
                                    "evidence-${gate.name.lowercase()}"
                                }
                        )
                    }
        )

    @Test
    fun allRequiredCatalogGatesMustBeExplicitlySatisfied() {
        val profile = ProductionActivationProfiles.CONSUMER_MOBILE_CATALOG
        val decision =
            ProductionAuthorizationEvaluator.evaluate(
                assessment = assessmentFor(profile),
                profile = profile
            )

        assertTrue(decision.authorized)
        assertEquals(ProductionActivationDecisionStatus.AUTHORIZED, decision.status)
        assertEquals(profile.requiredGates, decision.satisfiedGates)
        assertTrue(decision.blockingGates.isEmpty())
    }

    @Test
    fun priceBearingCatalogProfileIncludesEveryProductDiscoveryRight() {
        val discovery =
            ProductionActivationProfiles.CONSUMER_MOBILE_PRODUCT_DISCOVERY
        val priceBearing =
            ProductionActivationProfiles.CONSUMER_MOBILE_CATALOG

        assertTrue(priceBearing.requiredGates.containsAll(discovery.requiredGates))
        assertTrue(
            ProductionAuthorizationGate.PRICE_SEMANTICS_VALIDATED in
                priceBearing.requiredGates
        )
        assertTrue(
            ProductionAuthorizationGate.OFFER_FRESHNESS_POLICY_DEFINED in
                priceBearing.requiredGates
        )
    }

    @Test
    fun feedAccessAloneCanNeverAuthorizeMobileCatalogProduction() {
        val assessment =
            ProviderProductionAuthorizationAssessment(
                providerId = providerId,
                datasetNamespaceId = datasetId,
                gates =
                    listOf(
                        satisfiedAssessment(
                            ProductionAuthorizationGate.DATA_ACCESS_AUTHORIZED
                        )
                    )
            )

        val decision =
            ProductionAuthorizationEvaluator.evaluate(
                assessment = assessment,
                profile = ProductionActivationProfiles.CONSUMER_MOBILE_CATALOG
            )

        assertFalse(decision.authorized)
        assertTrue(
            ProductionAuthorizationGate.CONSUMER_DISPLAY_AUTHORIZED in
                decision.missingGates
        )
        assertTrue(
            ProductionAuthorizationGate.MOBILE_APP_AUTHORIZED in
                decision.missingGates
        )
        assertTrue(
            ProductionAuthorizationGate.OFFER_GEOGRAPHY_VALIDATED in
                decision.missingGates
        )
    }

    @Test
    fun pendingRequiredGateFailsClosed() {
        val profile = ProductionActivationProfiles.CONSUMER_MOBILE_CATALOG
        val gate = ProductionAuthorizationGate.MOBILE_APP_AUTHORIZED
        val assessment =
            assessmentFor(
                profile,
                overrides = mapOf(gate to ProductionAuthorizationState.PENDING)
            )

        val decision = ProductionAuthorizationEvaluator.evaluate(assessment, profile)

        assertFalse(decision.authorized)
        assertEquals(setOf(gate), decision.pendingGates)
        assertTrue(gate in decision.blockingGates)
    }

    @Test
    fun deniedRequiredGateFailsClosed() {
        val profile = ProductionActivationProfiles.CONSUMER_MOBILE_CATALOG
        val gate = ProductionAuthorizationGate.CONSUMER_DISPLAY_AUTHORIZED
        val assessment =
            assessmentFor(
                profile,
                overrides = mapOf(gate to ProductionAuthorizationState.DENIED)
            )

        val decision = ProductionAuthorizationEvaluator.evaluate(assessment, profile)

        assertFalse(decision.authorized)
        assertEquals(setOf(gate), decision.deniedGates)
    }

    @Test
    fun unknownRequiredGateFailsClosed() {
        val profile = ProductionActivationProfiles.CONSUMER_MOBILE_CATALOG
        val gate = ProductionAuthorizationGate.OFFER_FRESHNESS_POLICY_DEFINED
        val assessment =
            assessmentFor(
                profile,
                overrides = mapOf(gate to ProductionAuthorizationState.UNKNOWN)
            )

        val decision = ProductionAuthorizationEvaluator.evaluate(assessment, profile)

        assertFalse(decision.authorized)
        assertEquals(setOf(gate), decision.unknownGates)
    }

    @Test
    fun requiredGateCannotBeSatisfiedByCallingItNotRequired() {
        val profile = ProductionActivationProfiles.CONSUMER_MOBILE_CATALOG
        val gate = ProductionAuthorizationGate.CACHE_AUTHORIZED
        val assessment =
            assessmentFor(
                profile,
                overrides = mapOf(gate to ProductionAuthorizationState.NOT_REQUIRED)
            )

        val decision = ProductionAuthorizationEvaluator.evaluate(assessment, profile)

        assertFalse(decision.authorized)
        assertEquals(setOf(gate), decision.incorrectlyNotRequiredGates)
    }

    @Test
    fun networkAndDsaGatesAreConditionalOnNetworkLinkProfile() {
        val catalogProfile = ProductionActivationProfiles.CONSUMER_MOBILE_CATALOG
        val catalogAssessment = assessmentFor(catalogProfile)

        val catalogDecision =
            ProductionAuthorizationEvaluator.evaluate(
                assessment = catalogAssessment,
                profile = catalogProfile
            )

        assertTrue(catalogDecision.authorized)

        val networkProfile =
            ProductionActivationProfiles.CONSUMER_MOBILE_CATALOG_WITH_NETWORK_LINKS
        val networkDecision =
            ProductionAuthorizationEvaluator.evaluate(
                assessment = catalogAssessment,
                profile = networkProfile
            )

        assertFalse(networkDecision.authorized)
        assertTrue(
            ProductionAuthorizationGate.AFFILIATE_LINK_USE_AUTHORIZED in
                networkDecision.missingGates
        )
        assertTrue(
            ProductionAuthorizationGate.INSTALLED_SOFTWARE_NETWORK_APPROVED in
                networkDecision.missingGates
        )
        assertTrue(
            ProductionAuthorizationGate.ADVERTISER_DISTRIBUTION_APPROVED in
                networkDecision.missingGates
        )
        assertTrue(
            ProductionAuthorizationGate.TRACKING_PRIVACY_READY in
                networkDecision.missingGates
        )
    }

    @Test
    fun nonRequiredDeniedNetworkGateDoesNotBlockCatalogOnlyProfile() {
        val profile = ProductionActivationProfiles.CONSUMER_MOBILE_CATALOG
        val gates =
            assessmentFor(profile).gates +
                ProductionGateAssessment(
                    gate = ProductionAuthorizationGate.INSTALLED_SOFTWARE_NETWORK_APPROVED,
                    state = ProductionAuthorizationState.DENIED,
                    basisId = "network-review-denied"
                )
        val assessment =
            ProviderProductionAuthorizationAssessment(
                providerId = providerId,
                datasetNamespaceId = datasetId,
                gates = gates
            )

        val decision = ProductionAuthorizationEvaluator.evaluate(assessment, profile)

        assertTrue(decision.authorized)
        assertTrue(decision.deniedGates.isEmpty())
    }

    @Test
    fun duplicateGateAssessmentsAreRejected() {
        try {
            ProviderProductionAuthorizationAssessment(
                providerId = providerId,
                datasetNamespaceId = datasetId,
                gates =
                    listOf(
                        satisfiedAssessment(
                            ProductionAuthorizationGate.DATA_ACCESS_AUTHORIZED
                        ),
                        satisfiedAssessment(
                            ProductionAuthorizationGate.DATA_ACCESS_AUTHORIZED
                        )
                    )
            )
            fail("Expected duplicate gate rejection")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("at most once"))
        }
    }

    @Test
    fun satisfiedDeniedAndNotRequiredStatesNeedAuditableBasis() {
        listOf(
            ProductionAuthorizationState.SATISFIED,
            ProductionAuthorizationState.DENIED,
            ProductionAuthorizationState.NOT_REQUIRED
        ).forEach { state ->
            try {
                ProductionGateAssessment(
                    gate = ProductionAuthorizationGate.DATA_ACCESS_AUTHORIZED,
                    state = state,
                    basisId = null
                )
                fail("Expected basis requirement for $state")
            } catch (expected: IllegalArgumentException) {
                assertTrue(expected.message.orEmpty().contains("auditable basis"))
            }
        }
    }

    @Test
    fun fullySatisfiedNetworkProfileCanAuthorizeWithoutProviderSpecificLogic() {
        val profile =
            ProductionActivationProfiles.CONSUMER_MOBILE_CATALOG_WITH_NETWORK_LINKS
        val assessment = assessmentFor(profile)

        val decision = ProductionAuthorizationEvaluator.evaluate(assessment, profile)

        assertTrue(decision.authorized)
        assertEquals(profile.requiredGates, decision.satisfiedGates)
    }
}
