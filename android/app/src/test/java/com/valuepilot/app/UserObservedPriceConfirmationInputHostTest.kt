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

class UserObservedPriceConfirmationInputHostTest {

    private val storeScope =
        PracticalShoppingStoreIdentityScope(
            merchantKey = "merchant-a",
            locationKey = "location-a",
            commerceChannelKey = "IN_STORE"
        )

    @Test
    fun `open host forwards one exact caller submission and emits transaction result unchanged`() {
        val bytes = "price-tag-image-bytes".toByteArray()
        val storage = FakeProofStorage()
        val emitted = mutableListOf<UserObservedPriceConfirmationTransactionResult>()
        val host = host(storage, emitted)
        val fields = validFields()

        host.submit(
            artifactId = "artifact-001",
            proofType = UserProvidedPriceProofType.PRICE_TAG,
            artifactBytes = bytes,
            fields = fields
        )

        assertFalse(host.isClosed())
        assertEquals(1, emitted.size)
        val result = emitted.single()
        assertTrue(result.accepted)
        val confirmation = requireNotNull(result.confirmation)
        assertEquals("artifact-001", confirmation.artifact.artifactId)
        assertEquals(UserProvidedPriceProofType.PRICE_TAG, confirmation.artifact.proofType)
        assertEquals(fields.observationId, confirmation.observationId)
        assertEquals("4006381333931", confirmation.gtin)
        assertEquals(fields.productName, confirmation.productName)
        assertEquals(fields.price, confirmation.price)
        assertSame(storeScope, confirmation.storeScope)
        assertEquals(fields.observedAtEpochMillis, confirmation.observedAtEpochMillis)
        assertEquals(fields.confirmationId, confirmation.confirmationId)
        assertEquals(fields.confirmedAtEpochMillis, confirmation.confirmedAtEpochMillis)
        assertArrayEquals(bytes, storage.entries[confirmation.artifact.sha256])
    }

    @Test
    fun `transaction rejection is forwarded without host reinterpretation`() {
        val storage = FakeProofStorage()
        val emitted = mutableListOf<UserObservedPriceConfirmationTransactionResult>()
        val host = host(storage, emitted)

        host.submit(
            artifactId = "artifact-001",
            proofType = UserProvidedPriceProofType.RECEIPT,
            artifactBytes = byteArrayOf(),
            fields = validFields()
        )

        assertEquals(1, emitted.size)
        val result = emitted.single()
        assertFalse(result.accepted)
        assertNull(result.confirmation)
        assertEquals(
            setOf(UserProvidedPriceArtifactFailure.EMPTY_ARTIFACT),
            result.artifactFailures
        )
        assertTrue(result.confirmationFailures.isEmpty())
        assertNull(result.storageIssue)
        assertEquals(0, storage.readCount)
        assertEquals(0, storage.replaceCount)
    }

    @Test
    fun `close is idempotent and suppresses later submissions storage and callbacks`() {
        val storage = FakeProofStorage()
        val emitted = mutableListOf<UserObservedPriceConfirmationTransactionResult>()
        val host = host(storage, emitted)

        host.close()
        host.close()
        host.submit(
            artifactId = "artifact-after-close",
            proofType = UserProvidedPriceProofType.PRICE_TAG,
            artifactBytes = "should-never-be-stored".toByteArray(),
            fields = validFields()
        )

        assertTrue(host.isClosed())
        assertTrue(emitted.isEmpty())
        assertTrue(storage.entries.isEmpty())
        assertEquals(0, storage.readCount)
        assertEquals(0, storage.inventoryCount)
        assertEquals(0, storage.replaceCount)
    }

    @Test
    fun `later submission cannot inherit proof or confirmation state from prior submission`() {
        val storage = FakeProofStorage()
        val emitted = mutableListOf<UserObservedPriceConfirmationTransactionResult>()
        val host = host(storage, emitted)

        host.submit(
            artifactId = "artifact-valid",
            proofType = UserProvidedPriceProofType.PRICE_TAG,
            artifactBytes = "valid-price-tag".toByteArray(),
            fields = validFields()
        )
        host.submit(
            artifactId = "artifact-invalid",
            proofType = UserProvidedPriceProofType.RECEIPT,
            artifactBytes = byteArrayOf(),
            fields =
                validFields().copy(
                    observationId = "obs-002",
                    confirmationId = "confirm-002"
                )
        )

        assertEquals(2, emitted.size)
        assertTrue(emitted[0].accepted)
        assertFalse(emitted[1].accepted)
        assertNull(emitted[1].confirmation)
        assertEquals(
            setOf(UserProvidedPriceArtifactFailure.EMPTY_ARTIFACT),
            emitted[1].artifactFailures
        )
        assertEquals(1, storage.entries.size)
    }

    @Test
    fun `host keeps no raw proof byte state and owns no runtime evidence policy or UI authority`() {
        val byteArrayFields =
            UserObservedPriceConfirmationInputHost::class.java.declaredFields
                .filter { field -> field.type == ByteArray::class.java }
        assertTrue(byteArrayFields.isEmpty())

        val source = source("UserObservedPriceConfirmationInputHost.kt").readText()
        assertTrue(source.contains("transaction.confirmAndRetain("))
        assertTrue(source.contains("resultObserver.onResult(result)"))
        assertTrue(source.contains("if (closed) return"))

        listOf(
            "System.currentTimeMillis",
            "UUID",
            "ShoppingEvidence(",
            "EvidenceClaim(",
            "CURRENT_PRICE",
            "UserProofBackedObservedPrice",
            "ProductPackageQuantity",
            "UserObservedPriceUnitValue",
            "MainActivity",
            "OcrScanner",
            "android.content.Context",
            "android.os.Bundle",
            "Parcelable",
            "Serializable",
            "java.net"
        ).forEach { forbidden ->
            assertFalse("Input host must not own $forbidden", source.contains(forbidden))
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

    private fun host(
        storage: FakeProofStorage,
        emitted: MutableList<UserObservedPriceConfirmationTransactionResult>
    ): UserObservedPriceConfirmationInputHost =
        UserObservedPriceConfirmationInputHost(
            transaction =
                UserObservedPriceConfirmationTransaction(
                    UserProvidedPriceProofArtifactLocalStore(storage)
                ),
            resultObserver =
                UserObservedPriceConfirmationResultObserver { result ->
                    emitted += result
                }
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
