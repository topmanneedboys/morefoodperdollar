package com.valuepilot.app

import com.valuepilot.core.Money
import com.valuepilot.core.PracticalShoppingStoreIdentityScope
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class UserObservedPriceConfirmationTransactionTest {

    private val storeScope =
        PracticalShoppingStoreIdentityScope(
            merchantKey = "merchant-a",
            locationKey = "location-a",
            commerceChannelKey = "IN_STORE"
        )

    @Test
    fun `valid exact confirmation is exposed only after proof is retained and verified`() {
        val bytes = "price-tag-image-bytes".toByteArray()
        val storage = FakeProofStorage()
        val transaction = transaction(storage)

        val result =
            transaction.confirmAndRetain(
                artifactId = "artifact-001",
                proofType = UserProvidedPriceProofType.PRICE_TAG,
                artifactBytes = bytes,
                fields = validFields()
            )

        assertTrue(result.accepted)
        assertFalse(result.proofAlreadyRetained)
        assertTrue(result.artifactFailures.isEmpty())
        assertTrue(result.confirmationFailures.isEmpty())
        assertNull(result.storageIssue)

        val confirmation = requireNotNull(result.confirmation)
        assertEquals(UserProvidedPriceProofType.PRICE_TAG, confirmation.artifact.proofType)
        assertEquals("4006381333931", confirmation.gtin)
        assertEquals("gtin:4006381333931", confirmation.productKey.value)
        assertEquals(Money(599L, "CAD"), confirmation.price)
        assertSame(storeScope, confirmation.storeScope)
        assertEquals(10_000L, confirmation.observedAtEpochMillis)
        assertEquals(20_000L, confirmation.confirmedAtEpochMillis)

        assertEquals(2, storage.readCount)
        assertEquals(1, storage.inventoryCount)
        assertEquals(1, storage.replaceCount)
        assertArrayEquals(bytes, storage.entries[confirmation.artifact.sha256])
    }

    @Test
    fun `invalid proof is rejected before confirmation or storage can run`() {
        val storage = FakeProofStorage()
        val transaction = transaction(storage)

        val result =
            transaction.confirmAndRetain(
                artifactId = "artifact-001",
                proofType = UserProvidedPriceProofType.RECEIPT,
                artifactBytes = byteArrayOf(),
                fields = validFields()
            )

        assertFalse(result.accepted)
        assertNull(result.confirmation)
        assertEquals(
            setOf(UserProvidedPriceArtifactFailure.EMPTY_ARTIFACT),
            result.artifactFailures
        )
        assertTrue(result.confirmationFailures.isEmpty())
        assertNull(result.storageIssue)
        assertStorageUntouched(storage)
    }

    @Test
    fun `invalid confirmation is rejected before proof storage is touched`() {
        val storage = FakeProofStorage()
        val transaction = transaction(storage)

        val result =
            transaction.confirmAndRetain(
                artifactId = "artifact-001",
                proofType = UserProvidedPriceProofType.RECEIPT,
                artifactBytes = "receipt-image-bytes".toByteArray(),
                fields =
                    validFields().copy(
                        rawGtin = "4006381333932",
                        price = Money(0L, "CAD"),
                        confirmedAtEpochMillis = 9_999L
                    )
            )

        assertFalse(result.accepted)
        assertNull(result.confirmation)
        assertTrue(result.artifactFailures.isEmpty())
        assertEquals(
            setOf(
                UserObservedPriceConfirmationFailure.INVALID_GTIN,
                UserObservedPriceConfirmationFailure.NON_POSITIVE_PRICE,
                UserObservedPriceConfirmationFailure.CONFIRMATION_PRECEDES_OBSERVATION
            ),
            result.confirmationFailures
        )
        assertNull(result.storageIssue)
        assertStorageUntouched(storage)
    }

    @Test
    fun `storage failure cannot expose the otherwise valid confirmation`() {
        val storage = FakeProofStorage().apply { failReplace = true }
        val transaction = transaction(storage)

        val result =
            transaction.confirmAndRetain(
                artifactId = "artifact-001",
                proofType = UserProvidedPriceProofType.RECEIPT,
                artifactBytes = "receipt-image-bytes".toByteArray(),
                fields = validFields()
            )

        assertFalse(result.accepted)
        assertNull(result.confirmation)
        assertTrue(result.artifactFailures.isEmpty())
        assertTrue(result.confirmationFailures.isEmpty())
        assertEquals(UserProvidedPriceProofArtifactStorageIssue.WRITE_FAILED, result.storageIssue)
        assertEquals(1, storage.readCount)
        assertEquals(1, storage.inventoryCount)
        assertEquals(1, storage.replaceCount)
        assertTrue(storage.entries.isEmpty())
    }

    @Test
    fun `same retained proof can bind another exact item without rewriting content`() {
        val bytes = "shared-receipt-image-bytes".toByteArray()
        val storage = FakeProofStorage()
        val transaction = transaction(storage)

        val milk =
            transaction.confirmAndRetain(
                artifactId = "artifact-001",
                proofType = UserProvidedPriceProofType.RECEIPT,
                artifactBytes = bytes,
                fields = validFields()
            )
        val eggs =
            transaction.confirmAndRetain(
                artifactId = "artifact-002",
                proofType = UserProvidedPriceProofType.RECEIPT,
                artifactBytes = bytes,
                fields =
                    validFields().copy(
                        observationId = "obs-002",
                        rawGtin = "036000291452",
                        productName = "Test Eggs",
                        price = Money(399L, "CAD"),
                        confirmationId = "confirm-002"
                    )
            )

        assertTrue(milk.accepted)
        assertFalse(milk.proofAlreadyRetained)
        assertTrue(eggs.accepted)
        assertTrue(eggs.proofAlreadyRetained)
        assertEquals(1, storage.replaceCount)
        assertEquals(1, storage.entries.size)
        assertEquals(
            requireNotNull(milk.confirmation).artifact.sha256,
            requireNotNull(eggs.confirmation).artifact.sha256
        )
    }

    @Test
    fun `corrupt retained digest remains visible and is never silently overwritten`() {
        val bytes = "receipt-image-bytes".toByteArray()
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
        val corrupt = ByteArray(bytes.size) { 7 }
        val storage =
            FakeProofStorage().apply {
                entries[artifact.sha256] = corrupt.copyOf()
            }
        val transaction = transaction(storage)

        val result =
            transaction.confirmAndRetain(
                artifactId = "artifact-001",
                proofType = UserProvidedPriceProofType.RECEIPT,
                artifactBytes = bytes,
                fields = validFields()
            )

        assertFalse(result.accepted)
        assertNull(result.confirmation)
        assertEquals(
            UserProvidedPriceProofArtifactStorageIssue.STORED_ARTIFACT_INVALID,
            result.storageIssue
        )
        assertEquals(0, storage.inventoryCount)
        assertEquals(0, storage.replaceCount)
        assertArrayEquals(corrupt, storage.entries[artifact.sha256])
    }

    @Test
    fun `transaction composes existing boundaries without acquiring factual runtime or UI authority`() {
        val source = source("UserObservedPriceConfirmationTransaction.kt").readText()

        val fingerprint = source.indexOf("UserProvidedPriceProofArtifact.fingerprint(")
        val confirmation = source.indexOf("UserConfirmedObservedPrice.confirm(")
        val retention = source.indexOf("proofStore.retain(")
        assertTrue(fingerprint >= 0)
        assertTrue(confirmation > fingerprint)
        assertTrue(retention > confirmation)

        listOf(
            "System.currentTimeMillis",
            "ShoppingEvidence(",
            "EvidenceClaim(",
            "EvidenceClaimDomain.CURRENT_PRICE",
            "EvidenceBackedUnitValuePolicy",
            "ProductPackageQuantity",
            "UserProofBackedObservedPriceClaimAdapter",
            "UserObservedPriceUnitValueSurface",
            "MainActivity",
            "OcrScanner.",
            "android.content.Context",
            "java.net",
            "android.permission"
        ).forEach { forbidden ->
            assertFalse("Transaction must not own $forbidden", source.contains(forbidden))
        }
    }

    private fun validFields(): UserObservedPriceConfirmationFields =
        UserObservedPriceConfirmationFields(
            observationId = "obs-001",
            rawGtin = "4006381333931",
            productName = "Test Milk",
            price = Money(599L, "CAD"),
            storeScope = storeScope,
            observedAtEpochMillis = 10_000L,
            confirmationId = "confirm-001",
            confirmedAtEpochMillis = 20_000L
        )

    private fun transaction(
        storage: FakeProofStorage
    ): UserObservedPriceConfirmationTransaction =
        UserObservedPriceConfirmationTransaction(
            UserProvidedPriceProofArtifactLocalStore(storage)
        )

    private fun assertStorageUntouched(storage: FakeProofStorage) {
        assertEquals(0, storage.readCount)
        assertEquals(0, storage.inventoryCount)
        assertEquals(0, storage.replaceCount)
        assertTrue(storage.entries.isEmpty())
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

    private class FakeProofStorage : UserProvidedPriceProofArtifactByteStorage {
        val entries = linkedMapOf<String, ByteArray>()
        var failReplace: Boolean = false
        var readCount: Int = 0
        var inventoryCount: Int = 0
        var replaceCount: Int = 0

        override fun read(
            storageKey: String,
            maxBytes: Int
        ): UserProvidedPriceProofRawReadResult {
            readCount += 1
            val bytes =
                entries[storageKey]
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
            replaceCount += 1
            if (failReplace) return false
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
            inventoryCount += 1
            var totalBytes = 0L
            entries.values.forEach { bytes ->
                if (bytes.isEmpty() || bytes.size > maxArtifactBytes) {
                    return UserProvidedPriceProofInventoryResult(
                        artifactCount = null,
                        totalBytes = null,
                        issue = UserProvidedPriceProofInventoryIssue.INVALID_COMMITTED_ARTIFACT
                    )
                }
                totalBytes += bytes.size.toLong()
            }
            return UserProvidedPriceProofInventoryResult(
                artifactCount = entries.size,
                totalBytes = totalBytes
            )
        }
    }
}
