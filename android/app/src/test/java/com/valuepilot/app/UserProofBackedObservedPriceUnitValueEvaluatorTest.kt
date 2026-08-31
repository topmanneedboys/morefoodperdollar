package com.valuepilot.app

import com.valuepilot.core.BaseUnit
import com.valuepilot.core.EvidenceAuthorityClass
import com.valuepilot.core.EvidenceBackedUnitValueBlockReason
import com.valuepilot.core.EvidenceClaim
import com.valuepilot.core.EvidenceClaimDomain
import com.valuepilot.core.EvidenceClaimScope
import com.valuepilot.core.EvidenceFingerprints
import com.valuepilot.core.EvidenceFreshness
import com.valuepilot.core.EvidenceFreshnessPolicy
import com.valuepilot.core.Money
import com.valuepilot.core.NormalizedQuantity
import com.valuepilot.core.PracticalShoppingStoreIdentityScope
import com.valuepilot.core.RateUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class UserProofBackedObservedPriceUnitValueEvaluatorTest {

    @Test
    fun `fresh verified price tag combines with strong exact quantity without current price upgrade`() {
        val fixture = fixture(UserProvidedPriceProofType.PRICE_TAG)
        val quantity = kilogramsOne()

        val result =
            fixture.evaluator.evaluate(
                confirmation = fixture.confirmation,
                evaluatedAtEpochMillis = 10_500L,
                freshnessPolicy = freshnessPolicy(),
                quantityClaim = quantityClaim(fixture.confirmation, quantity),
                quantity = quantity
            )

        assertTrue(result.rankable)
        assertEquals(EvidenceFreshness.FRESH, result.priceUse.freshness)
        assertEquals(EvidenceClaimDomain.OBSERVED_PRICE, requireNotNull(result.priceUse.claim).domain)
        val rate = requireNotNull(requireNotNull(result.unitValue).rate)
        assertEquals("CAD", rate.currencyCode)
        assertEquals(RateUnit.KILOGRAM, rate.unit)
        assertEquals(5_990_000L, rate.currencyMicrosPerUnit)
    }

    @Test
    fun `receipt remains display only and shared unit value policy blocks ranking`() {
        val fixture = fixture(UserProvidedPriceProofType.RECEIPT)
        val quantity = kilogramsOne()

        val result =
            fixture.evaluator.evaluate(
                fixture.confirmation,
                10_500L,
                freshnessPolicy(),
                quantityClaim(fixture.confirmation, quantity),
                quantity
            )

        assertFalse(result.rankable)
        assertTrue(result.priceUse.displayable)
        assertEquals(UserObservedPriceUseReason.RECEIPT_HISTORICAL_ONLY, result.priceUse.reason)
        assertTrue(
            EvidenceBackedUnitValueBlockReason.PRICE_NOT_RANKABLE in
                requireNotNull(result.unitValue).blockReasons
        )
    }

    @Test
    fun `aging price tag cannot regain rankability through exact quantity`() {
        val fixture = fixture(UserProvidedPriceProofType.PRICE_TAG)
        val quantity = kilogramsOne()

        val result =
            fixture.evaluator.evaluate(
                fixture.confirmation,
                11_001L,
                freshnessPolicy(),
                quantityClaim(fixture.confirmation, quantity),
                quantity
            )

        assertFalse(result.rankable)
        assertEquals(EvidenceFreshness.AGING, result.priceUse.freshness)
        assertEquals(UserObservedPriceUseReason.PRICE_TAG_AGING, result.priceUse.reason)
        assertTrue(
            EvidenceBackedUnitValueBlockReason.PRICE_NOT_RANKABLE in
                requireNotNull(result.unitValue).blockReasons
        )
    }

    @Test
    fun `proof deletion stops before unit value arithmetic because no price claim remains`() {
        val fixture = fixture(UserProvidedPriceProofType.PRICE_TAG)
        val quantity = kilogramsOne()
        assertTrue(fixture.store.delete(fixture.artifact).accepted)

        val result =
            fixture.evaluator.evaluate(
                fixture.confirmation,
                10_500L,
                freshnessPolicy(),
                quantityClaim(fixture.confirmation, quantity),
                quantity
            )

        assertFalse(result.rankable)
        assertEquals(UserObservedPriceUseReason.PROOF_NOT_RETAINED, result.priceUse.reason)
        assertNull(result.priceUse.claim)
        assertNull(result.unitValue)
    }

    @Test
    fun `different product quantity cannot be joined to fresh observed price`() {
        val fixture = fixture(UserProvidedPriceProofType.PRICE_TAG)
        val quantity = kilogramsOne()
        val wrongProduct =
            quantityClaim(fixture.confirmation, quantity).copy(
                scope = EvidenceClaimScope(productKey = "gtin:036000291452")
            )

        val result =
            fixture.evaluator.evaluate(
                fixture.confirmation,
                10_500L,
                freshnessPolicy(),
                wrongProduct,
                quantity
            )

        assertFalse(result.rankable)
        assertTrue(
            EvidenceBackedUnitValueBlockReason.PRODUCT_IDENTITY_MISMATCH in
                requireNotNull(result.unitValue).blockReasons
        )
    }

    @Test
    fun `weak package quantity authority cannot drive observed price unit value`() {
        val fixture = fixture(UserProvidedPriceProofType.PRICE_TAG)
        val quantity = kilogramsOne()
        val weakQuantity =
            quantityClaim(fixture.confirmation, quantity).copy(
                authority = EvidenceAuthorityClass.USER_ASSERTED
            )

        val result =
            fixture.evaluator.evaluate(
                fixture.confirmation,
                10_500L,
                freshnessPolicy(),
                weakQuantity,
                quantity
            )

        assertFalse(result.rankable)
        assertTrue(
            EvidenceBackedUnitValueBlockReason.WEAK_QUANTITY_AUTHORITY in
                requireNotNull(result.unitValue).blockReasons
        )
    }

    @Test
    fun `quantity claim fingerprint must match exact normalized quantity`() {
        val fixture = fixture(UserProvidedPriceProofType.PRICE_TAG)
        val quantity = kilogramsOne()
        val mismatchedClaim =
            quantityClaim(fixture.confirmation, quantity).copy(
                valueFingerprint =
                    EvidenceFingerprints.quantity(
                        NormalizedQuantity(
                            amountMicros = 900_000_000L,
                            unit = BaseUnit.GRAM
                        )
                    )
            )

        val result =
            fixture.evaluator.evaluate(
                fixture.confirmation,
                10_500L,
                freshnessPolicy(),
                mismatchedClaim,
                quantity
            )

        assertFalse(result.rankable)
        assertTrue(
            EvidenceBackedUnitValueBlockReason.QUANTITY_VALUE_MISMATCH in
                requireNotNull(result.unitValue).blockReasons
        )
    }

    @Test
    fun `source delegates policy math and does not mint merchant current price availability or promotions`() {
        val source = source("UserProofBackedObservedPriceUnitValueEvaluator.kt").readText()

        listOf(
            "priceUsePolicy.evaluate(",
            "EvidenceBackedUnitValuePolicy.evaluate(",
            "Offer(current = confirmation.price)",
            "priceDisposition = priceUse.disposition",
            "useMemberPrice = false"
        ).forEach { required ->
            assertTrue("Expected observed-price unit-value bridge $required", source.contains(required))
        }

        listOf(
            "EvidenceClaimDomain.CURRENT_PRICE",
            "EvidenceAuthorityClass.MERCHANT_AUTHORITATIVE",
            "ProductionCurrentPrice",
            "AvailabilityState",
            "AvailabilityEvidence(",
            "PromotionEvidence(",
            "PromotionTerms(",
            ".promotion =",
            "EvidenceFactResolver",
            "System.currentTimeMillis",
            "SharedPreferences",
            "WorkManager",
            "java.net",
            "android.permission"
        ).forEach { forbidden ->
            assertFalse("Unit-value bridge must not own $forbidden", source.contains(forbidden))
        }
    }

    private fun kilogramsOne(): NormalizedQuantity =
        NormalizedQuantity(
            amountMicros = 1_000_000_000L,
            unit = BaseUnit.GRAM
        )

    private fun quantityClaim(
        confirmation: UserConfirmedObservedPrice,
        quantity: NormalizedQuantity
    ): EvidenceClaim =
        EvidenceClaim(
            claimId = "source-metadata:quantity",
            domain = EvidenceClaimDomain.PACKAGE_QUANTITY,
            valueFingerprint = EvidenceFingerprints.quantity(quantity),
            authority = EvidenceAuthorityClass.SOURCE_ASSERTED_METADATA,
            scope = EvidenceClaimScope(productKey = confirmation.productKey.value),
            observedAtEpochMillis = 9_000L
        )

    private fun freshnessPolicy(): EvidenceFreshnessPolicy =
        EvidenceFreshnessPolicy(
            freshForMillis = 1_000L,
            staleAfterMillis = 2_000L,
            futureToleranceMillis = 100L
        )

    private fun fixture(
        proofType: UserProvidedPriceProofType
    ): Fixture {
        val bytes = "unit-value-proof-${proofType.name}".toByteArray()
        val artifact =
            requireNotNull(
                UserProvidedPriceProofArtifact
                    .fingerprint(
                        artifactId = "unit-value-${proofType.name.lowercase()}",
                        proofType = proofType,
                        artifactBytes = bytes
                    )
                    .artifact
            )
        val confirmation =
            requireNotNull(
                UserConfirmedObservedPrice
                    .confirm(
                        UserObservedPriceConfirmationInput(
                            artifact = artifact,
                            observationId = "unit-value-obs",
                            rawGtin = "4006381333931",
                            productName = "Test Milk",
                            price = Money(599L, "CAD"),
                            storeScope =
                                PracticalShoppingStoreIdentityScope(
                                    merchantKey = "merchant-a",
                                    locationKey = "location-a",
                                    commerceChannelKey = "IN_STORE"
                                ),
                            observedAtEpochMillis = 10_000L,
                            confirmationId = "unit-value-confirm",
                            confirmedAtEpochMillis = 10_100L
                        )
                    )
                    .confirmation
            )
        val storage = FakeProofStorage()
        val store = UserProvidedPriceProofArtifactLocalStore(storage)
        assertTrue(store.retain(artifact, bytes).accepted)
        val claimAdapter = UserProofBackedObservedPriceClaimAdapter(store)
        val usePolicy = UserProofBackedObservedPriceUsePolicy(claimAdapter)

        return Fixture(
            artifact = artifact,
            confirmation = confirmation,
            store = store,
            evaluator = UserProofBackedObservedPriceUnitValueEvaluator(usePolicy)
        )
    }

    private fun source(fileName: String): File {
        var directory = File(System.getProperty("user.dir") ?: error("user.dir unavailable"))
        repeat(8) {
            val candidate = File(directory, "app/src/main/java/com/valuepilot/app/$fileName")
            if (candidate.isFile) return candidate
            directory = directory.parentFile ?: return@repeat
        }
        error("Could not locate $fileName")
    }

    private data class Fixture(
        val artifact: UserProvidedPriceProofArtifact,
        val confirmation: UserConfirmedObservedPrice,
        val store: UserProvidedPriceProofArtifactLocalStore,
        val evaluator: UserProofBackedObservedPriceUnitValueEvaluator
    )

    private class FakeProofStorage : UserProvidedPriceProofArtifactByteStorage {
        private val entries = linkedMapOf<String, ByteArray>()

        override fun read(
            storageKey: String,
            maxBytes: Int
        ): UserProvidedPriceProofRawReadResult {
            val bytes = entries[storageKey]
                ?: return UserProvidedPriceProofRawReadResult(
                    bytes = null,
                    found = false
                )
            if (bytes.size > maxBytes) {
                return UserProvidedPriceProofRawReadResult(
                    bytes = null,
                    found = true,
                    issue = UserProvidedPriceProofRawReadIssue.INPUT_TOO_LARGE
                )
            }
            return UserProvidedPriceProofRawReadResult(
                bytes = bytes.copyOf(),
                found = true
            )
        }

        override fun replace(
            storageKey: String,
            bytes: ByteArray
        ): Boolean {
            entries[storageKey] = bytes.copyOf()
            return true
        }

        override fun delete(storageKey: String): Boolean {
            entries.remove(storageKey)
            return true
        }

        override fun clearAll(): Boolean {
            entries.clear()
            return true
        }

        override fun inventory(
            maxArtifactBytes: Int
        ): UserProvidedPriceProofInventoryResult {
            var total = 0L
            entries.values.forEach { bytes ->
                if (bytes.isEmpty() || bytes.size > maxArtifactBytes) {
                    return UserProvidedPriceProofInventoryResult(
                        artifactCount = null,
                        totalBytes = null,
                        issue = UserProvidedPriceProofInventoryIssue.INVALID_COMMITTED_ARTIFACT
                    )
                }
                total += bytes.size.toLong()
            }
            return UserProvidedPriceProofInventoryResult(
                artifactCount = entries.size,
                totalBytes = total
            )
        }
    }
}
