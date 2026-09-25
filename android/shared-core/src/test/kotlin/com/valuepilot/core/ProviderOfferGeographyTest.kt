package com.valuepilot.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ProviderOfferGeographyTest {

    private val providerId = EvidenceProviderId("provider-test")
    private val datasetId = "provider-test-feed"

    @Test
    fun currencyOnlyCanadaGuessCannotValidateCanadianOfferScope() {
        val geography =
            ProviderDatasetOfferGeography(
                providerId = providerId,
                datasetNamespaceId = datasetId,
                countryCode = "CA",
                basis = ImportedOfferCountryBasis.CURRENCY_ONLY,
                basisId = "currency-cad"
            )

        val assessment =
            ProviderDatasetOfferGeographyEvaluator.evaluate(
                geography = geography,
                targetCountryCode = "CA"
            )

        assertEquals(ProviderDatasetCountryMatchStatus.UNRESOLVED, assessment.status)
        assertFalse(assessment.validated)
        assertEquals(
            ProductionAuthorizationState.UNKNOWN,
            assessment.toProductionGateAssessment().state
        )
    }

    @Test
    fun advertiserContextAloneCannotValidateCanada() {
        val geography =
            ProviderDatasetOfferGeography(
                providerId = providerId,
                datasetNamespaceId = datasetId,
                countryCode = "CA",
                basis = ImportedOfferCountryBasis.ADVERTISER_CONTEXT_ONLY,
                basisId = "advertiser-context"
            )

        val assessment =
            ProviderDatasetOfferGeographyEvaluator.evaluate(geography, "CA")

        assertEquals(ProviderDatasetCountryMatchStatus.UNRESOLVED, assessment.status)
    }

    @Test
    fun explicitDatasetCountryCanValidateCanada() {
        val geography =
            ProviderDatasetOfferGeography(
                providerId = providerId,
                datasetNamespaceId = datasetId,
                countryCode = "CA",
                basis = ImportedOfferCountryBasis.EXPLICIT_DATASET_COUNTRY,
                basisId = "dataset-country-declaration"
            )

        val assessment =
            ProviderDatasetOfferGeographyEvaluator.evaluate(geography, "CA")
        val gate = assessment.toProductionGateAssessment()

        assertTrue(assessment.validated)
        assertEquals(ProviderDatasetCountryMatchStatus.MATCH, assessment.status)
        assertEquals(ProductionAuthorizationState.SATISFIED, gate.state)
        assertEquals(
            ProductionAuthorizationGate.OFFER_GEOGRAPHY_VALIDATED,
            gate.gate
        )
    }

    @Test
    fun documentedDatasetMarketCanValidateCanada() {
        val geography =
            ProviderDatasetOfferGeography(
                providerId = providerId,
                datasetNamespaceId = datasetId,
                countryCode = "CA",
                basis = ImportedOfferCountryBasis.DOCUMENTED_DATASET_MARKET,
                basisId = "provider-market-document"
            )

        val assessment =
            ProviderDatasetOfferGeographyEvaluator.evaluate(geography, "CA")

        assertEquals(ProviderDatasetCountryMatchStatus.MATCH, assessment.status)
    }

    @Test
    fun strongExplicitDifferentCountryFailsClosedAsDenied() {
        val geography =
            ProviderDatasetOfferGeography(
                providerId = providerId,
                datasetNamespaceId = datasetId,
                countryCode = "US",
                basis = ImportedOfferCountryBasis.EXPLICIT_DATASET_COUNTRY,
                basisId = "dataset-country-declaration"
            )

        val assessment =
            ProviderDatasetOfferGeographyEvaluator.evaluate(geography, "CA")
        val gate = assessment.toProductionGateAssessment()

        assertEquals(ProviderDatasetCountryMatchStatus.MISMATCH, assessment.status)
        assertEquals(ProductionAuthorizationState.DENIED, gate.state)
    }

    @Test
    fun unknownCountryRemainsUnresolved() {
        val geography =
            ProviderDatasetOfferGeography(
                providerId = providerId,
                datasetNamespaceId = datasetId
            )

        val assessment =
            ProviderDatasetOfferGeographyEvaluator.evaluate(geography, "CA")

        assertEquals(ProviderDatasetCountryMatchStatus.UNRESOLVED, assessment.status)
        assertEquals(ProductionAuthorizationState.UNKNOWN, assessment.toProductionGateAssessment().state)
    }

    @Test
    fun inferredCountryNeverValidatesOfferScope() {
        val geography =
            ProviderDatasetOfferGeography(
                providerId = providerId,
                datasetNamespaceId = datasetId,
                countryCode = "CA",
                basis = ImportedOfferCountryBasis.INFERRED,
                basisId = "inference-only"
            )

        val assessment =
            ProviderDatasetOfferGeographyEvaluator.evaluate(geography, "CA")

        assertEquals(ProviderDatasetCountryMatchStatus.UNRESOLVED, assessment.status)
    }

    @Test
    fun strongCountryEvidenceRequiresAuditableBasis() {
        try {
            ProviderDatasetOfferGeography(
                providerId = providerId,
                datasetNamespaceId = datasetId,
                countryCode = "CA",
                basis = ImportedOfferCountryBasis.EXPLICIT_DATASET_COUNTRY,
                basisId = null
            )
            fail("Expected strong country basis requirement")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("auditable basis"))
        }
    }

    @Test
    fun strongCountryEvidenceRequiresCountryCode() {
        try {
            ProviderDatasetOfferGeography(
                providerId = providerId,
                datasetNamespaceId = datasetId,
                countryCode = null,
                basis = ImportedOfferCountryBasis.DOCUMENTED_DATASET_MARKET,
                basisId = "provider-market-document"
            )
            fail("Expected strong country code requirement")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("country code"))
        }
    }

    @Test
    fun lowercaseCountryCodeIsRejectedRatherThanNormalizedSilently() {
        try {
            ProviderDatasetOfferGeography(
                providerId = providerId,
                datasetNamespaceId = datasetId,
                countryCode = "ca",
                basis = ImportedOfferCountryBasis.CURRENCY_ONLY,
                basisId = "currency-cad"
            )
            fail("Expected lowercase country-code rejection")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("uppercase"))
        }
    }
}
