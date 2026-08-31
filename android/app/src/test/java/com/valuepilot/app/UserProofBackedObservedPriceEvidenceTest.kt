package com.valuepilot.app

import com.valuepilot.core.EvidenceAuthorityClass
import com.valuepilot.core.EvidenceChannel
import com.valuepilot.core.EvidenceClaim
import com.valuepilot.core.EvidenceClaimDomain
import com.valuepilot.core.EvidenceClaimKind
import com.valuepilot.core.EvidenceClaimScope
import com.valuepilot.core.EvidenceConflictPolicy
import com.valuepilot.core.EvidenceConflictRelationship
import com.valuepilot.core.EvidenceEnvironment
import com.valuepilot.core.EvidenceFingerprints
import com.valuepilot.core.Money
import com.valuepilot.core.PracticalShoppingStoreIdentityScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class UserProofBackedObservedPriceEvidenceTest {

    @Test
    fun `freshly retained exact proof promotes only the exact confirmed observed price`() {
        val fixture = fixture(retainProof = true)

        val result =
            UserProofBackedObservedPriceEvidence.promote(
                confirmation = fixture.confirmation,
                proofStore = fixture.store
            )

        assertTrue(result.accepted)
        assertNull(result.failure)
        val promoted = requireNotNull(result.promoted)
        assertSame(fixture.confirmation, promoted.confirmation)

        val evidence = promoted.evidence
        assertEquals("local-user-price-proof", evidence.provider.id.value)
        assertEquals("User-provided price proof", evidence.provider.displayName)
        assertEquals("local-user-price-proof", evidence.source.id.value)
        assertEquals("User-provided proof", evidence.source.displayName)
        assertEquals(EvidenceEnvironment.REAL_WORLD, evidence.environment)
        assertEquals(EvidenceChannel.USER_PROVIDED, evidence.channel)
        assertEquals(EvidenceClaimKind.DIRECT_OBSERVATION, evidence.observationClaimKind)
        assertEquals("012345678905", evidence.sourceProductIdentity?.gtin)
        assertNull(evidence.sourceProductIdentity?.providerItemId)
        assertNull(evidence.sourceProductIdentity?.sku)
        assertEquals("local-user-price:observation-001", evidence.observation.id.value)
        assertEquals("local-user-price-proof", evidence.observation.sourceId)
        assertEquals(1_000_000L, evidence.observation.observedAtEpochMillis)
        assertEquals(
            "Test Milk\nmoney:CAD:2:499",
            evidence.observation.rawText
        )
        assertNull(evidence.promotion)

        val claim = promoted.priceClaim
        assertEquals(EvidenceClaimDomain.OBSERVED_PRICE, claim.domain)
        assertEquals(
            EvidenceAuthorityClass.PROOF_BACKED_DIRECT_OBSERVATION,
            claim.authority
        )
        assertEquals(EvidenceFingerprints.money(Money.parse("4.99", "CAD")), claim.valueFingerprint)
        assertEquals(fixture.confirmation.productKey.value, claim.scope.productKey)
        assertEquals("merchant:test-grocer", claim.scope.merchantKey)
        assertEquals("location:store-17", claim.scope.locationKey)
        assertEquals("PHYSICAL_STORE", claim.scope.commerceChannelKey)
        assertEquals("CAD", claim.scope.currencyCode)
        assertEquals(1_000_000L, claim.observedAtEpochMillis)
        assertTrue(claim.claimId.contains("confirmation-001"))
        assertTrue(claim.claimId.contains(fixture.artifact.sha256))
    }

    @Test
    fun `proof must already be durably retained at promotion time`() {
        val fixture = fixture(retainProof = false)

        val result =
            UserProofBackedObservedPriceEvidence.promote(
                confirmation = fixture.confirmation,
                proofStore = fixture.store
            )

        assertFalse(result.accepted)
        assertNull(result.promoted)
        assertEquals(
            UserProofBackedObservedPricePromotionFailure.PROOF_NOT_RETAINED,
            result.failure
        )
    }

    @Test
    fun `later promotion rechecks storage so deleting proof blocks future promotion`() {
        val fixture = fixture(retainProof = true)
        val first =
            UserProofBackedObservedPriceEvidence.promote(
                confirmation = fixture.confirmation,
                proofStore = fixture.store
            )
        assertTrue(first.accepted)
        assertTrue(fixture.store.delete(fixture.artifact).accepted)

        val later =
            UserProofBackedObservedPriceEvidence.promote(
                confirmation = fixture.confirmation,
                proofStore = fixture.store
            )

        assertFalse(later.accepted)
        assertEquals(
            UserProofBackedObservedPricePromotionFailure.PROOF_NOT_RETAINED,
            later.failure
        )
    }

    @Test
    fun `later promotion rechecks digest so corrupt retained proof fails closed`() {
        val fixture = fixture(retainProof = true)
        val stored = requireNotNull(fixture.storage.entries[fixture.artifact.sha256])
        fixture.storage.entries[fixture.artifact.sha256] =
            stored.copyOf().also { bytes -> bytes[0] = (bytes[0].toInt() xor 0x01).toByte() }

        val result =
            UserProofBackedObservedPriceEvidence.promote(
                confirmation = fixture.confirmation,
                proofStore = fixture.store
            )

        assertFalse(result.accepted)
        assertEquals(UserProofBackedObservedPricePromotionFailure.PROOF_INVALID, result.failure)
    }

    @Test
    fun `proof read failure is distinct and cannot promote authority`() {
        val fixture = fixture(retainProof = true)
        fixture.storage.forcedReadIssue = UserProvidedPriceProofRawReadIssue.IO_FAILURE
        fixture.storage.forcedReadFound = true

        val result =
            UserProofBackedObservedPriceEvidence.promote(
                confirmation = fixture.confirmation,
                proofStore = fixture.store
            )

        assertFalse(result.accepted)
        assertEquals(
            UserProofBackedObservedPricePromotionFailure.PROOF_READ_FAILED,
            result.failure
        )
    }

    @Test
    fun `confirmation time never replaces the real observation time`() {
        val fixture =
            fixture(
                retainProof = true,
                observedAtEpochMillis = 2_000_000L,
                confirmedAtEpochMillis = 9_000_000L
            )

        val promoted =
            requireNotNull(
                UserProofBackedObservedPriceEvidence
                    .promote(fixture.confirmation, fixture.store)
                    .promoted
            )

        assertEquals(2_000_000L, promoted.evidence.observation.observedAtEpochMillis)
        assertEquals(2_000_000L, promoted.priceClaim.observedAtEpochMillis)
        assertEquals(9_000_000L, promoted.confirmation.confirmedAtEpochMillis)
    }

    @Test
    fun `observed price coexists with a current price claim instead of overwriting it`() {
        val fixture = fixture(retainProof = true)
        val observed =
            requireNotNull(
                UserProofBackedObservedPriceEvidence
                    .promote(fixture.confirmation, fixture.store)
                    .promoted
            )
                .priceClaim
        val current =
            EvidenceClaim(
                claimId = "merchant-current-price",
                domain = EvidenceClaimDomain.CURRENT_PRICE,
                valueFingerprint = observed.valueFingerprint,
                authority = EvidenceAuthorityClass.MERCHANT_AUTHORITATIVE,
                scope =
                    EvidenceClaimScope(
                        productKey = observed.scope.productKey,
                        merchantKey = observed.scope.merchantKey,
                        locationKey = observed.scope.locationKey,
                        commerceChannelKey = observed.scope.commerceChannelKey,
                        currencyCode = observed.scope.currencyCode
                    ),
                observedAtEpochMillis = observed.observedAtEpochMillis
            )

        val decision = EvidenceConflictPolicy.resolve(observed, current)

        assertEquals(EvidenceConflictRelationship.COEXISTS, decision.relationship)
        assertNull(decision.selectedClaimId)
        assertFalse(decision.blocksRanking)
        assertEquals("different factual claim domains", decision.reason)
    }

    @Test
    fun `exact money product and complete store scope are preserved without provider sku invention`() {
        val scope =
            PracticalShoppingStoreIdentityScope(
                merchantKey = "merchant:exact:selected",
                locationKey = null,
                commerceChannelKey = "ONLINE"
            )
        val fixture =
            fixture(
                retainProof = true,
                price = Money.parse("123.456", "USD", fractionDigits = 3),
                scope = scope
            )

        val promoted =
            requireNotNull(
                UserProofBackedObservedPriceEvidence
                    .promote(fixture.confirmation, fixture.store)
                    .promoted
            )

        assertEquals("money:USD:3:123456", promoted.priceClaim.valueFingerprint)
        assertEquals("merchant:exact:selected", promoted.priceClaim.scope.merchantKey)
        assertNull(promoted.priceClaim.scope.locationKey)
        assertEquals("ONLINE", promoted.priceClaim.scope.commerceChannelKey)
        assertEquals("USD", promoted.priceClaim.scope.currencyCode)
        assertEquals("gtin:012345678905", promoted.priceClaim.scope.productKey)
        assertEquals("012345678905", promoted.evidence.sourceProductIdentity?.gtin)
        assertNull(promoted.evidence.sourceProductIdentity?.providerItemId)
        assertNull(promoted.evidence.sourceProductIdentity?.sku)
    }

    @Test
    fun `promotion boundary owns observed evidence only and no current offer authority`() {
        val source = source("UserProofBackedObservedPriceEvidence.kt").readText()

        listOf(
            "proofStore.verify(confirmation.artifact)",
            "EvidenceEnvironment.REAL_WORLD",
            "EvidenceChannel.USER_PROVIDED",
            "EvidenceClaimKind.DIRECT_OBSERVATION",
            "EvidenceClaimDomain.OBSERVED_PRICE",
            "EvidenceAuthorityClass.PROOF_BACKED_DIRECT_OBSERVATION",
            "merchantKey = confirmation.storeScope.merchantKey",
            "locationKey = confirmation.storeScope.locationKey",
            "commerceChannelKey = confirmation.storeScope.commerceChannelKey",
            "EvidenceFingerprints.money(confirmation.price)",
            "SourceProductIdentity(gtin = confirmation.gtin)"
        ).forEach { required ->
            assertTrue("Expected observed-price boundary $required", source.contains(required))
        }

        listOf(
            "EvidenceClaimDomain.CURRENT_PRICE",
            "ProviderOfferImportRecord",
            "ProductionCurrentPrice",
            "PracticalShoppingProduction",
            "StapleWatch",
            "Offer(",
            "AvailabilityState.IN_STOCK",
            "PromotionEvidence(",
            "System.currentTimeMillis",
            "WorkManager",
            "NotificationManager",
            "OcrScanner.scan",
            "java.net",
            "android.permission"
        ).forEach { forbidden ->
            assertFalse("Observed-price promotion must not own $forbidden", source.contains(forbidden))
        }
    }

    private fun fixture(
        retainProof: Boolean,
        price: Money = Money.parse("4.99", "CAD"),
        scope: PracticalShoppingStoreIdentityScope =
            PracticalShoppingStoreIdentityScope(
                merchantKey = "merchant:test-grocer",
                locationKey = "location:store-17",
                commerceChannelKey = "PHYSICAL_STORE"
            ),
        observedAtEpochMillis: Long = 1_000_000L,
        confirmedAtEpochMillis: Long = 1_000_100L
    ): Fixture {
        val bytes = "receipt-image-bytes-for-test".toByteArray()
        val artifact =
            requireNotNull(
                UserProvidedPriceProofArtifact
                    .fingerprint(
                        artifactId = "artifact-001",
                        proofType = UserProvidedPriceProofType.RECEIPT,
                        artifactBytes = bytes
                    )
                    .artifact
            )
        val storage = FakeProofStorage()
        val store = UserProvidedPriceProofArtifactLocalStore(storage)
        if (retainProof) {
            assertTrue(store.retain(artifact, bytes).accepted)
        }
        val confirmation =
            requireNotNull(
                UserConfirmedObservedPrice
                    .confirm(
                        UserObservedPriceConfirmationInput(
                            artifact = artifact,
                            observationId = "observation-001",
                            rawGtin = "012345678905",
                            productName = "Test Milk",
                            price = price,
                            storeScope = scope,
                            observedAtEpochMillis = observedAtEpochMillis,
                            confirmationId = "confirmation-001",
                            confirmedAtEpochMillis = confirmedAtEpochMillis
                        )
                    )
                    .confirmation
            )

        return Fixture(
            artifact = artifact,
            confirmation = confirmation,
            storage = storage,
            store = store
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
        val storage: FakeProofStorage,
        val store: UserProvidedPriceProofArtifactLocalStore
    )

    private class FakeProofStorage : UserProvidedPriceProofArtifactByteStorage {
        val entries = linkedMapOf<String, ByteArray>()
        var forcedReadIssue: UserProvidedPriceProofRawReadIssue? = null
        var forcedReadFound: Boolean = false

        override fun read(
            storageKey: String,
            maxBytes: Int
        ): UserProvidedPriceProofRawReadResult {
            forcedReadIssue?.let { issue ->
                return UserProvidedPriceProofRawReadResult(
                    bytes = null,
                    found = forcedReadFound,
                    issue = issue
                )
            }
            val bytes = entries[storageKey]
                ?: return UserProvidedPriceProofRawReadResult(bytes = null, found = false)
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
