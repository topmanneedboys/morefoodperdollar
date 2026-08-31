package com.valuepilot.app

import com.valuepilot.core.AvailabilityState
import com.valuepilot.core.EvidenceAcceptanceEvaluator
import com.valuepilot.core.EvidenceAcceptancePolicy
import com.valuepilot.core.EvidenceAuthorityClass
import com.valuepilot.core.EvidenceChannel
import com.valuepilot.core.EvidenceClaimDomain
import com.valuepilot.core.EvidenceClaimKind
import com.valuepilot.core.EvidenceDisposition
import com.valuepilot.core.EvidenceFingerprints
import com.valuepilot.core.EvidenceFreshness
import com.valuepilot.core.EvidenceFreshnessPolicy
import com.valuepilot.core.EvidenceProviderId
import com.valuepilot.core.EvidenceStorageBoundary
import com.valuepilot.core.Money
import com.valuepilot.core.PracticalShoppingStoreIdentityScope
import com.valuepilot.core.ProductionProductEvidenceKeyResolver
import com.valuepilot.core.SourceProductIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class UserProvidedObservedPriceEvidenceAdapterTest {

    private val storeScope =
        PracticalShoppingStoreIdentityScope(
            merchantKey = "merchant-a",
            locationKey = "location-a",
            commerceChannelKey = "IN_STORE"
        )

    @Test
    fun `receipt proof produces exact user controlled observed price evidence`() {
        val input = validInput(proofType = UserProvidedPriceProofType.RECEIPT)

        val result = UserProvidedObservedPriceEvidenceAdapter.adapt(input)

        assertTrue(result.accepted)
        assertTrue(result.failures.isEmpty())
        val accepted = requireNotNull(result.acceptedEvidence)
        val evidence = accepted.evidence
        val claim = accepted.priceClaim

        assertEquals(EvidenceChannel.USER_PROVIDED, evidence.channel)
        assertEquals(EvidenceClaimKind.DIRECT_OBSERVATION, evidence.observationClaimKind)
        assertTrue(evidence.isRealWorld)
        assertEquals("4006381333931", evidence.sourceProductIdentity?.gtin)
        assertEquals(input.observedAtEpochMillis, evidence.observation.observedAtEpochMillis)
        assertEquals(AvailabilityState.UNKNOWN, evidence.availability.state)
        assertNull(evidence.promotion)

        assertEquals(EvidenceStorageBoundary.USER_CONTROLLED, accepted.dataset.storageBoundary)
        assertEquals(input.proof, accepted.proof)
        assertEquals(storeScope, accepted.storeScope)

        assertEquals(EvidenceClaimDomain.OBSERVED_PRICE, claim.domain)
        assertEquals(EvidenceAuthorityClass.PROOF_BACKED_DIRECT_OBSERVATION, claim.authority)
        assertEquals(EvidenceFingerprints.money(input.price), claim.valueFingerprint)
        assertEquals(accepted.productKey.value, claim.scope.productKey)
        assertEquals(storeScope.merchantKey, claim.scope.merchantKey)
        assertEquals(storeScope.locationKey, claim.scope.locationKey)
        assertEquals(storeScope.commerceChannelKey, claim.scope.commerceChannelKey)
        assertEquals("CAD", claim.scope.currencyCode)
        assertEquals(input.observedAtEpochMillis, claim.observedAtEpochMillis)
    }

    @Test
    fun `price tag proof remains observed price rather than merchant current offer`() {
        val result =
            UserProvidedObservedPriceEvidenceAdapter.adapt(
                validInput(proofType = UserProvidedPriceProofType.PRICE_TAG)
            )

        val accepted = requireNotNull(result.acceptedEvidence)
        assertEquals(UserProvidedPriceProofType.PRICE_TAG, accepted.proof.proofType)
        assertEquals(EvidenceClaimDomain.OBSERVED_PRICE, accepted.priceClaim.domain)
        assertFalse(accepted.priceClaim.domain == EvidenceClaimDomain.CURRENT_PRICE)
        assertEquals(EvidenceClaimKind.DIRECT_OBSERVATION, accepted.evidence.observationClaimKind)
    }

    @Test
    fun `gtin is the only cross source product identity used by local proof`() {
        val accepted =
            requireNotNull(
                UserProvidedObservedPriceEvidenceAdapter
                    .adapt(validInput())
                    .acceptedEvidence
            )

        val sameGtinFromUnrelatedProvider =
            requireNotNull(
                ProductionProductEvidenceKeyResolver.resolve(
                    providerId = EvidenceProviderId("unrelated-provider"),
                    identity = SourceProductIdentity(gtin = "4006381333931")
                )
            )

        assertTrue(accepted.productKey.usesCrossSourceRepresentation)
        assertEquals(sameGtinFromUnrelatedProvider, accepted.productKey)
        assertNull(accepted.evidence.sourceProductIdentity?.providerItemId)
        assertNull(accepted.evidence.sourceProductIdentity?.sku)
    }

    @Test
    fun `invalid gtin fails closed without accepted evidence`() {
        val result =
            UserProvidedObservedPriceEvidenceAdapter.adapt(
                validInput().copy(rawGtin = "4006381333932")
            )

        assertFalse(result.accepted)
        assertNull(result.acceptedEvidence)
        assertTrue(UserProvidedObservedPriceFailure.INVALID_GTIN in result.failures)
    }

    @Test
    fun `invalid proof reference fails closed`() {
        val result =
            UserProvidedObservedPriceEvidenceAdapter.adapt(
                validInput().copy(
                    proof =
                        UserProvidedPriceProofReference(
                            proofId = "bad proof id",
                            proofType = UserProvidedPriceProofType.RECEIPT,
                            artifactSha256 = "ABCDEF",
                            confirmationId = "bad confirmation id"
                        )
                )
            )

        assertFalse(result.accepted)
        assertNull(result.acceptedEvidence)
        assertEquals(
            setOf(
                UserProvidedObservedPriceFailure.INVALID_PROOF_ID,
                UserProvidedObservedPriceFailure.INVALID_PROOF_DIGEST,
                UserProvidedObservedPriceFailure.INVALID_CONFIRMATION_ID
            ),
            result.failures
        )
    }

    @Test
    fun `invalid exact price time and display metadata fail closed together`() {
        val result =
            UserProvidedObservedPriceEvidenceAdapter.adapt(
                validInput().copy(
                    productName = "Milk\nInjected store name",
                    price = Money(0L, "CAD"),
                    observedAtEpochMillis = 0L
                )
            )

        assertFalse(result.accepted)
        assertNull(result.acceptedEvidence)
        assertEquals(
            setOf(
                UserProvidedObservedPriceFailure.INVALID_PRODUCT_NAME,
                UserProvidedObservedPriceFailure.NON_POSITIVE_PRICE,
                UserProvidedObservedPriceFailure.INVALID_OBSERVATION_TIME
            ),
            result.failures
        )
    }

    @Test
    fun `recent direct observation can pass generic evidence acceptance without becoming current price`() {
        val accepted =
            requireNotNull(
                UserProvidedObservedPriceEvidenceAdapter
                    .adapt(validInput(observedAtEpochMillis = 10_000L))
                    .acceptedEvidence
            )

        val decision =
            EvidenceAcceptanceEvaluator.evaluate(
                evidence = accepted.evidence,
                evaluatedAtEpochMillis = 12_000L,
                policy =
                    EvidenceAcceptancePolicy(
                        freshnessPolicy =
                            EvidenceFreshnessPolicy(
                                freshForMillis = 5_000L,
                                staleAfterMillis = 20_000L
                            )
                    )
            )

        assertEquals(EvidenceDisposition.RANKABLE, decision.disposition)
        assertEquals(EvidenceFreshness.FRESH, decision.freshness)
        assertEquals(EvidenceClaimDomain.OBSERVED_PRICE, accepted.priceClaim.domain)
        assertFalse(accepted.priceClaim.domain == EvidenceClaimDomain.CURRENT_PRICE)
    }

    @Test
    fun `adapter owns proof backed observation mapping only`() {
        val source = source("UserProvidedObservedPriceEvidenceAdapter.kt").readText()

        listOf(
            "EvidenceClaimDomain.OBSERVED_PRICE",
            "EvidenceChannel.USER_PROVIDED",
            "EvidenceStorageBoundary.USER_CONTROLLED",
            "EvidenceAuthorityClass.PROOF_BACKED_DIRECT_OBSERVATION",
            "EvidenceClaimKind.DIRECT_OBSERVATION",
            "GtinValidation.isValid",
            "artifactSha256",
            "PracticalShoppingStoreIdentityScope"
        ).forEach { required ->
            assertTrue("Expected source boundary $required", source.contains(required))
        }

        listOf(
            "EvidenceClaimDomain.CURRENT_PRICE",
            "ProviderOfferImportRecord",
            "ProductionCurrentPrice",
            "StapleWatch",
            "System.currentTimeMillis",
            "SharedPreferences",
            "WorkManager",
            "NotificationManager",
            "OpenPrices",
            "OpenFoodFacts",
            "OpenStreetMap",
            "UniversalSearchController",
            "ValueItem",
            "java.net",
            "android.permission"
        ).forEach { forbidden ->
            assertFalse("User proof observation adapter must not own $forbidden", source.contains(forbidden))
        }
    }

    private fun validInput(
        proofType: UserProvidedPriceProofType = UserProvidedPriceProofType.RECEIPT,
        observedAtEpochMillis: Long = 10_000L
    ): UserProvidedObservedPriceInput =
        UserProvidedObservedPriceInput(
            observationId = "obs-001",
            rawGtin = "4006381333931",
            productName = "Test Milk",
            price = Money(599L, "CAD"),
            storeScope = storeScope,
            observedAtEpochMillis = observedAtEpochMillis,
            proof =
                UserProvidedPriceProofReference(
                    proofId = "proof-001",
                    proofType = proofType,
                    artifactSha256 = "a".repeat(64),
                    confirmationId = "confirm-001"
                )
        )

    private fun source(fileName: String): File {
        var directory = File(System.getProperty("user.dir") ?: error("user.dir unavailable"))
        repeat(8) {
            val candidate = File(directory, "app/src/main/java/com/valuepilot/app/$fileName")
            if (candidate.isFile) return candidate
            directory = directory.parentFile ?: return@repeat
        }
        error("Could not locate $fileName")
    }
}
