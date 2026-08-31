package com.valuepilot.app

import com.valuepilot.core.EvidenceAuthorityClass
import com.valuepilot.core.EvidenceClaimDomain
import com.valuepilot.core.EvidenceConflictPolicy
import com.valuepilot.core.EvidenceConflictRelationship
import com.valuepilot.core.EvidenceFingerprints
import com.valuepilot.core.Money
import com.valuepilot.core.PracticalShoppingStoreIdentityScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class UserProofBackedObservedPriceClaimAdapterTest {

    @Test
    fun `verified retained proof exposes exact observed price claim only`() {
        val bytes = "receipt-image-bytes".toByteArray()
        val artifact = artifact("artifact-001", bytes)
        val confirmation = confirmation(artifact)
        val storage = FakeProofStorage()
        val store = UserProvidedPriceProofArtifactLocalStore(storage)
        assertTrue(store.retain(artifact, bytes).accepted)

        val result = UserProofBackedObservedPriceClaimAdapter(store).read(confirmation)

        assertTrue(result.accepted)
        assertNull(result.failure)
        assertNull(result.storageIssue)
        val claim = requireNotNull(result.claim)
        assertEquals(EvidenceClaimDomain.OBSERVED_PRICE, claim.domain)
        assertEquals(EvidenceAuthorityClass.PROOF_BACKED_DIRECT_OBSERVATION, claim.authority)
        assertEquals(EvidenceFingerprints.money(Money(599L, "CAD")), claim.valueFingerprint)
        assertEquals("gtin:4006381333931", claim.scope.productKey)
        assertEquals("merchant-a", claim.scope.merchantKey)
        assertEquals("location-a", claim.scope.locationKey)
        assertEquals("IN_STORE", claim.scope.commerceChannelKey)
        assertEquals("CAD", claim.scope.currencyCode)
        assertEquals(10_000L, claim.observedAtEpochMillis)
        assertTrue(claim.claimId.matches(Regex("user-proof-observed-price:[0-9a-f]{64}")))
    }

    @Test
    fun `missing retained proof cannot expose observed price authority`() {
        val artifact = artifact("artifact-001", "receipt-image-bytes".toByteArray())
        val storage = FakeProofStorage()
        val store = UserProvidedPriceProofArtifactLocalStore(storage)

        val result =
            UserProofBackedObservedPriceClaimAdapter(store)
                .read(confirmation(artifact))

        assertFalse(result.accepted)
        assertNull(result.claim)
        assertEquals(UserProofBackedObservedPriceClaimFailure.PROOF_NOT_RETAINED, result.failure)
        assertNull(result.storageIssue)
        assertEquals(1, storage.readCount)
    }

    @Test
    fun `corrupt retained proof fails closed with storage issue`() {
        val bytes = "receipt-image-bytes".toByteArray()
        val artifact = artifact("artifact-001", bytes)
        val storage = FakeProofStorage()
        storage.entries[artifact.sha256] = ByteArray(bytes.size) { 7 }
        val store = UserProvidedPriceProofArtifactLocalStore(storage)

        val result =
            UserProofBackedObservedPriceClaimAdapter(store)
                .read(confirmation(artifact))

        assertFalse(result.accepted)
        assertNull(result.claim)
        assertEquals(UserProofBackedObservedPriceClaimFailure.PROOF_VERIFICATION_FAILED, result.failure)
        assertEquals(
            UserProvidedPriceProofArtifactStorageIssue.STORED_ARTIFACT_INVALID,
            result.storageIssue
        )
    }

    @Test
    fun `proof deletion revokes the claim on the next read`() {
        val bytes = "receipt-image-bytes".toByteArray()
        val artifact = artifact("artifact-001", bytes)
        val confirmation = confirmation(artifact)
        val storage = FakeProofStorage()
        val store = UserProvidedPriceProofArtifactLocalStore(storage)
        val adapter = UserProofBackedObservedPriceClaimAdapter(store)
        assertTrue(store.retain(artifact, bytes).accepted)

        val beforeDelete = adapter.read(confirmation)
        assertTrue(beforeDelete.accepted)
        assertTrue(store.delete(artifact).accepted)
        val afterDelete = adapter.read(confirmation)

        assertFalse(afterDelete.accepted)
        assertNull(afterDelete.claim)
        assertEquals(
            UserProofBackedObservedPriceClaimFailure.PROOF_NOT_RETAINED,
            afterDelete.failure
        )
    }

    @Test
    fun `claim id is stable for the same bound facts and changes when exact scope changes`() {
        val bytes = "price-tag-image-bytes".toByteArray()
        val artifact = artifact(
            artifactId = "artifact-tag-001",
            bytes = bytes,
            proofType = UserProvidedPriceProofType.PRICE_TAG
        )
        val storage = FakeProofStorage()
        val store = UserProvidedPriceProofArtifactLocalStore(storage)
        assertTrue(store.retain(artifact, bytes).accepted)
        val adapter = UserProofBackedObservedPriceClaimAdapter(store)

        val first = requireNotNull(adapter.read(confirmation(artifact)).claim)
        val second = requireNotNull(adapter.read(confirmation(artifact)).claim)
        val otherStore =
            requireNotNull(
                adapter
                    .read(
                        confirmation(
                            artifact = artifact,
                            storeScope =
                                PracticalShoppingStoreIdentityScope(
                                    merchantKey = "merchant-a",
                                    locationKey = "location-b",
                                    commerceChannelKey = "IN_STORE"
                                )
                        )
                    )
                    .claim
            )

        assertEquals(first.claimId, second.claimId)
        assertNotEquals(first.claimId, otherStore.claimId)
        assertEquals(first.scope.productKey, otherStore.scope.productKey)
    }

    @Test
    fun `observed user proof coexists with a current price claim instead of replacing it`() {
        val bytes = "receipt-image-bytes".toByteArray()
        val artifact = artifact("artifact-001", bytes)
        val storage = FakeProofStorage()
        val store = UserProvidedPriceProofArtifactLocalStore(storage)
        assertTrue(store.retain(artifact, bytes).accepted)
        val observed =
            requireNotNull(
                UserProofBackedObservedPriceClaimAdapter(store)
                    .read(confirmation(artifact))
                    .claim
            )
        val current =
            observed.copy(
                claimId = "merchant-current-price",
                domain = EvidenceClaimDomain.CURRENT_PRICE,
                authority = EvidenceAuthorityClass.MERCHANT_AUTHORITATIVE
            )

        val decision = EvidenceConflictPolicy.resolve(observed, current)

        assertEquals(EvidenceConflictRelationship.COEXISTS, decision.relationship)
        assertFalse(decision.blocksRanking)
        assertNull(decision.selectedClaimId)
    }

    @Test
    fun `adapter source owns observed claim authority only and no generic offer or runtime path`() {
        val source = source("UserProofBackedObservedPriceClaimAdapter.kt").readText()

        listOf(
            "proofStore.verify(confirmation.artifact)",
            "EvidenceClaimDomain.OBSERVED_PRICE",
            "EvidenceAuthorityClass.PROOF_BACKED_DIRECT_OBSERVATION",
            "confirmation.productKey.value",
            "scope.merchantKey",
            "scope.locationKey",
            "scope.commerceChannelKey",
            "EvidenceFingerprints.money(confirmation.price)"
        ).forEach { required ->
            assertTrue("Expected observed-price boundary $required", source.contains(required))
        }

        listOf(
            "ShoppingEvidence(",
            "EvidenceClaimDomain.CURRENT_PRICE",
            "EvidenceAuthorityClass.MERCHANT_AUTHORITATIVE",
            "EvidenceDisposition.RANKABLE",
            "ProductionCurrentPrice",
            "EvidenceAcceptanceEvaluator",
            "ProviderOfferImport",
            "StapleWatch",
            "Offer(",
            "System.currentTimeMillis",
            "SharedPreferences",
            "WorkManager",
            "NotificationManager",
            "OcrScanner.scan",
            "java.net",
            "android.permission"
        ).forEach { forbidden ->
            assertFalse("Observed-price bridge must not own $forbidden", source.contains(forbidden))
        }
    }

    private fun artifact(
        artifactId: String,
        bytes: ByteArray,
        proofType: UserProvidedPriceProofType = UserProvidedPriceProofType.RECEIPT
    ): UserProvidedPriceProofArtifact =
        requireNotNull(
            UserProvidedPriceProofArtifact
                .fingerprint(
                    artifactId = artifactId,
                    proofType = proofType,
                    artifactBytes = bytes
                )
                .artifact
        )

    private fun confirmation(
        artifact: UserProvidedPriceProofArtifact,
        storeScope: PracticalShoppingStoreIdentityScope =
            PracticalShoppingStoreIdentityScope(
                merchantKey = "merchant-a",
                locationKey = "location-a",
                commerceChannelKey = "IN_STORE"
            )
    ): UserConfirmedObservedPrice =
        requireNotNull(
            UserConfirmedObservedPrice
                .confirm(
                    UserObservedPriceConfirmationInput(
                        artifact = artifact,
                        observationId = "obs-001",
                        rawGtin = "4006381333931",
                        productName = "Test Milk",
                        price = Money(599L, "CAD"),
                        storeScope = storeScope,
                        observedAtEpochMillis = 10_000L,
                        confirmationId = "confirm-001",
                        confirmedAtEpochMillis = 20_000L
                    )
                )
                .confirmation
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

    private class FakeProofStorage : UserProvidedPriceProofArtifactByteStorage {
        val entries = linkedMapOf<String, ByteArray>()
        var readCount: Int = 0

        override fun read(
            storageKey: String,
            maxBytes: Int
        ): UserProvidedPriceProofRawReadResult {
            readCount += 1
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
