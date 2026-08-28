package com.valuepilot.app

import com.valuepilot.core.AvailabilityState
import com.valuepilot.core.EvidenceAcceptanceEvaluator
import com.valuepilot.core.EvidenceAcceptancePolicy
import com.valuepilot.core.EvidenceChannel
import com.valuepilot.core.EvidenceClaimKind
import com.valuepilot.core.EvidenceDisposition
import com.valuepilot.core.EvidenceEnvironment
import com.valuepilot.core.EvidenceFreshness
import com.valuepilot.core.EvidenceFreshnessPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenPricesImportedEvidenceTest {

    @Test
    fun proofBackedCanadianCadRowMapsToTypedRealWorldEvidence() {
        val result =
            OpenPricesImportedEvidenceMapper.map(
                validRow()
            )

        assertTrue(result.accepted)
        assertTrue(result.failures.isEmpty())
        assertNotNull(result.evidence)

        val evidence = requireNotNull(result.evidence)

        assertEquals(
            "open-prices",
            evidence.provider.id.value
        )
        assertEquals(
            "Open Prices",
            evidence.provider.displayName
        )
        assertEquals(
            EvidenceEnvironment.REAL_WORLD,
            evidence.environment
        )
        assertEquals(
            EvidenceChannel.IMPORTED,
            evidence.channel
        )
        assertEquals(
            EvidenceClaimKind.DIRECT_OBSERVATION,
            evidence.observationClaimKind
        )
        assertEquals(
            "open-prices-location-321",
            evidence.source.id.value
        )
        assertEquals(
            "Example Market — Toronto",
            evidence.source.displayName
        )
        assertEquals(
            "036000291452",
            evidence.sourceProductIdentity?.gtin
        )
        assertEquals(
            "open-prices-price-987",
            evidence.sourceProductIdentity?.providerItemId
        )
        assertEquals(
            "Rolled Oats\n4.99 CAD",
            evidence.observation.rawText
        )
        assertEquals(
            OBSERVED_AT,
            evidence.observation.observedAtEpochMillis
        )
        assertEquals(
            AvailabilityState.UNKNOWN,
            evidence.availability.state
        )
        assertFalse(evidence.isSample)
        assertTrue(evidence.isRealWorld)
    }

    @Test
    fun mapperRejectsRowsOutsideInitialCanadaCadPhysicalProofGate() {
        assertFailure(
            validRow().copy(countryCode = "US"),
            OpenPricesImportFailure.NON_CANADIAN_LOCATION
        )
        assertFailure(
            validRow().copy(currencyCode = "USD"),
            OpenPricesImportFailure.NON_CAD_CURRENCY
        )
        assertFailure(
            validRow().copy(
                locationKind = OpenPricesLocationKind.ONLINE
            ),
            OpenPricesImportFailure.UNSUPPORTED_LOCATION_KIND
        )
        assertFailure(
            validRow().copy(proofId = null),
            OpenPricesImportFailure.MISSING_PROOF
        )
        assertFailure(
            validRow().copy(
                proofType = OpenPricesProofType.OTHER
            ),
            OpenPricesImportFailure.UNSUPPORTED_PROOF
        )
    }

    @Test
    fun mapperRejectsInvalidIdentityPriceAndObservationTime() {
        assertFailure(
            validRow().copy(productCode = "036000291453"),
            OpenPricesImportFailure.INVALID_GTIN
        )
        assertFailure(
            validRow().copy(priceText = "4.999"),
            OpenPricesImportFailure.INVALID_PRICE
        )
        assertFailure(
            validRow().copy(priceText = "0"),
            OpenPricesImportFailure.INVALID_PRICE
        )
        assertFailure(
            validRow().copy(observedAtEpochMillis = 0L),
            OpenPricesImportFailure.INVALID_OBSERVATION_TIME
        )
    }

    @Test
    fun gtinValidationChecksBothShapeAndGs1CheckDigit() {
        assertTrue(
            OpenPricesImportedEvidenceMapper
                .isValidGtin("036000291452")
        )
        assertFalse(
            OpenPricesImportedEvidenceMapper
                .isValidGtin("036000291453")
        )
        assertFalse(
            OpenPricesImportedEvidenceMapper
                .isValidGtin("12345")
        )
        assertFalse(
            OpenPricesImportedEvidenceMapper
                .isValidGtin("03600029145X")
        )
    }

    @Test
    fun sourceObservationTimeDrivesFreshnessAndIsNeverReplacedByImportTime() {
        val evaluatedAt = 1_800_000_000_000L
        val threeHours = 3L * 60L * 60L * 1000L

        val evidence =
            OpenPricesImportedEvidenceMapper
                .map(
                    validRow().copy(
                        observedAtEpochMillis =
                            evaluatedAt - threeHours
                    )
                )
                .evidence

        assertNotNull(evidence)

        val decision =
            EvidenceAcceptanceEvaluator.evaluate(
                evidence = requireNotNull(evidence),
                evaluatedAtEpochMillis = evaluatedAt,
                policy =
                    EvidenceAcceptancePolicy(
                        freshnessPolicy =
                            EvidenceFreshnessPolicy(
                                freshForMillis =
                                    15L * 60L * 1000L,
                                staleAfterMillis =
                                    2L * 60L * 60L * 1000L,
                                futureToleranceMillis =
                                    5L * 60L * 1000L
                            )
                    )
            )

        assertEquals(
            EvidenceFreshness.STALE,
            decision.freshness
        )
        assertEquals(
            EvidenceDisposition.DISPLAY_ONLY,
            decision.disposition
        )
    }

    @Test
    fun priceObservationParsesWithoutInventingPackageQuantity() {
        val evidence =
            requireNotNull(
                OpenPricesImportedEvidenceMapper
                    .map(validRow())
                    .evidence
            )

        val item =
            DeterministicProductParser.parse(
                rawText = evidence.observation.rawText,
                sourceId = evidence.source.id.value
            )

        assertNotNull(item)
        assertEquals("Rolled Oats", item?.name)
        assertEquals("CAD", item?.currency)
        assertEquals(4.99, item?.price ?: 0.0, 0.000001)
        assertNull(item?.quantity)
    }

    private fun assertFailure(
        row: OpenPricesImportedRow,
        expected: OpenPricesImportFailure
    ) {
        val result =
            OpenPricesImportedEvidenceMapper.map(row)

        assertFalse(result.accepted)
        assertNull(result.evidence)
        assertTrue(
            "Expected $expected in ${result.failures}",
            expected in result.failures
        )
    }

    private fun validRow() =
        OpenPricesImportedRow(
            priceId = "987",
            productCode = "036000291452",
            productName = "Rolled Oats",
            priceText = "4.99",
            currencyCode = "CAD",
            countryCode = "CA",
            locationId = "321",
            locationName = "Example Market — Toronto",
            locationKind =
                OpenPricesLocationKind.PHYSICAL_STORE,
            observedAtEpochMillis = OBSERVED_AT,
            proofId = "555",
            proofType = OpenPricesProofType.RECEIPT
        )

    companion object {
        private const val OBSERVED_AT = 1_790_000_000_000L
    }
}
