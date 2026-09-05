package com.valuepilot.app

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class UserProvidedPriceProofArtifactLocalStoreTest {

    @Test
    fun `retain stores exact bytes under digest and verifies them on read`() {
        val bytes = "receipt-image-bytes".toByteArray()
        val artifact = artifact("artifact-001", bytes)
        val storage = FakeProofStorage()
        val store = UserProvidedPriceProofArtifactLocalStore(storage)

        val retained = store.retain(artifact, bytes)
        val verified = store.verify(artifact)

        assertTrue(retained.accepted)
        assertFalse(retained.alreadyRetained)
        assertSame(artifact, retained.artifact)
        assertEquals(1, storage.replaceCount)
        assertEquals(artifact.sha256, storage.lastStorageKey)
        assertFalse(requireNotNull(storage.lastStorageKey).contains(artifact.artifactId))
        assertArrayEquals(bytes, storage.entries[artifact.sha256])

        assertTrue(verified.verified)
        assertTrue(verified.foundStoredArtifact)
        assertSame(artifact, verified.artifact)
        assertNull(verified.issue)
    }

    @Test
    fun `same content is deduplicated and does not rewrite an already verified digest`() {
        val bytes = "receipt-image-bytes".toByteArray()
        val firstArtifact = artifact("artifact-001", bytes)
        val secondArtifact = artifact("artifact-002", bytes)
        val storage = FakeProofStorage()
        val store = UserProvidedPriceProofArtifactLocalStore(storage)

        val first = store.retain(firstArtifact, bytes)
        val second = store.retain(secondArtifact, bytes)

        assertTrue(first.accepted)
        assertFalse(first.alreadyRetained)
        assertTrue(second.accepted)
        assertTrue(second.alreadyRetained)
        assertSame(secondArtifact, second.artifact)
        assertEquals(firstArtifact.sha256, secondArtifact.sha256)
        assertEquals(1, storage.replaceCount)
        assertEquals(1, storage.entries.size)
    }

    @Test
    fun `caller bytes must match the exact fingerprint before storage is consulted`() {
        val original = "receipt-image-bytes".toByteArray()
        val artifact = artifact("artifact-001", original)
        val storage = FakeProofStorage()
        val store = UserProvidedPriceProofArtifactLocalStore(storage)

        val result = store.retain(artifact, "different-proof-bytes".toByteArray())

        assertFalse(result.accepted)
        assertEquals(
            UserProvidedPriceProofArtifactStorageIssue.PROOF_BYTES_MISMATCH,
            result.issue
        )
        assertEquals(0, storage.readCount)
        assertEquals(0, storage.inventoryCount)
        assertEquals(0, storage.replaceCount)
        assertTrue(storage.entries.isEmpty())
    }

    @Test
    fun `corrupt existing digest fails closed and is never silently overwritten`() {
        val bytes = "receipt-image-bytes".toByteArray()
        val artifact = artifact("artifact-001", bytes)
        val storage = FakeProofStorage()
        storage.entries[artifact.sha256] = ByteArray(bytes.size) { 7 }
        val store = UserProvidedPriceProofArtifactLocalStore(storage)

        val verified = store.verify(artifact)
        val retained = store.retain(artifact, bytes)

        assertFalse(verified.verified)
        assertTrue(verified.foundStoredArtifact)
        assertEquals(
            UserProvidedPriceProofArtifactStorageIssue.STORED_ARTIFACT_INVALID,
            verified.issue
        )
        assertFalse(retained.accepted)
        assertEquals(
            UserProvidedPriceProofArtifactStorageIssue.STORED_ARTIFACT_INVALID,
            retained.issue
        )
        assertEquals(0, storage.replaceCount)
        assertArrayEquals(ByteArray(bytes.size) { 7 }, storage.entries[artifact.sha256])
    }

    @Test
    fun `oversized and unreadable retained files remain distinct fail closed states`() {
        val artifact = artifact("artifact-001", "receipt-image-bytes".toByteArray())
        val storage = FakeProofStorage()
        val store = UserProvidedPriceProofArtifactLocalStore(storage)

        storage.forcedReadIssue = UserProvidedPriceProofRawReadIssue.INPUT_TOO_LARGE
        storage.forcedReadFound = true
        val oversized = store.verify(artifact)

        storage.forcedReadIssue = UserProvidedPriceProofRawReadIssue.IO_FAILURE
        val unreadable = store.verify(artifact)

        assertFalse(oversized.verified)
        assertTrue(oversized.foundStoredArtifact)
        assertEquals(
            UserProvidedPriceProofArtifactStorageIssue.STORED_ARTIFACT_TOO_LARGE,
            oversized.issue
        )
        assertFalse(unreadable.verified)
        assertTrue(unreadable.foundStoredArtifact)
        assertEquals(UserProvidedPriceProofArtifactStorageIssue.READ_FAILED, unreadable.issue)
    }

    @Test
    fun `retention enforces artifact count and total byte hard bounds`() {
        val bytes = "receipt-image-bytes".toByteArray()
        val artifact = artifact("artifact-001", bytes)
        val storage = FakeProofStorage()
        val store = UserProvidedPriceProofArtifactLocalStore(storage)

        storage.forcedInventory =
            UserProvidedPriceProofInventoryResult(
                artifactCount = UserProvidedPriceProofArtifactLocalStore.MAX_RETAINED_ARTIFACTS,
                totalBytes = 1L
            )
        val countBlocked = store.retain(artifact, bytes)

        storage.forcedInventory =
            UserProvidedPriceProofInventoryResult(
                artifactCount = 1,
                totalBytes =
                    UserProvidedPriceProofArtifactLocalStore.MAX_RETAINED_BYTES -
                        artifact.byteLength.toLong() +
                        1L
            )
        val bytesBlocked = store.retain(artifact, bytes)

        assertFalse(countBlocked.accepted)
        assertEquals(
            UserProvidedPriceProofArtifactStorageIssue.ARTIFACT_COUNT_LIMIT_REACHED,
            countBlocked.issue
        )
        assertFalse(bytesBlocked.accepted)
        assertEquals(
            UserProvidedPriceProofArtifactStorageIssue.TOTAL_BYTES_LIMIT_REACHED,
            bytesBlocked.issue
        )
        assertEquals(0, storage.replaceCount)
    }

    @Test
    fun `invalid inventory and write failures cannot produce accepted retention`() {
        val bytes = "receipt-image-bytes".toByteArray()
        val artifact = artifact("artifact-001", bytes)
        val storage = FakeProofStorage()
        val store = UserProvidedPriceProofArtifactLocalStore(storage)

        storage.forcedInventory =
            UserProvidedPriceProofInventoryResult(
                artifactCount = null,
                totalBytes = null,
                issue = UserProvidedPriceProofInventoryIssue.INVALID_COMMITTED_ARTIFACT
            )
        val invalidInventory = store.retain(artifact, bytes)

        storage.forcedInventory = null
        storage.failReplace = true
        val failedWrite = store.retain(artifact, bytes)

        assertFalse(invalidInventory.accepted)
        assertEquals(
            UserProvidedPriceProofArtifactStorageIssue.STORED_ARTIFACT_INVALID,
            invalidInventory.issue
        )
        assertFalse(failedWrite.accepted)
        assertEquals(UserProvidedPriceProofArtifactStorageIssue.WRITE_FAILED, failedWrite.issue)
    }

    @Test
    fun `successful write is read back and corruption during write is rejected`() {
        val bytes = "receipt-image-bytes".toByteArray()
        val artifact = artifact("artifact-001", bytes)
        val storage = FakeProofStorage()
        storage.corruptSuccessfulReplace = true
        val store = UserProvidedPriceProofArtifactLocalStore(storage)

        val result = store.retain(artifact, bytes)

        assertFalse(result.accepted)
        assertEquals(
            UserProvidedPriceProofArtifactStorageIssue.STORED_ARTIFACT_INVALID,
            result.issue
        )
        assertEquals(1, storage.replaceCount)
        assertEquals(2, storage.readCount)
    }

    @Test
    fun `delete and clear are explicit recovery paths and surface failures`() {
        val firstBytes = "receipt-one".toByteArray()
        val secondBytes = "receipt-two".toByteArray()
        val first = artifact("artifact-001", firstBytes)
        val second = artifact("artifact-002", secondBytes)
        val storage = FakeProofStorage()
        val store = UserProvidedPriceProofArtifactLocalStore(storage)
        assertTrue(store.retain(first, firstBytes).accepted)
        assertTrue(store.retain(second, secondBytes).accepted)

        val deleted = store.delete(first)
        val firstAfterDelete = store.verify(first)
        val cleared = store.clearAll()
        val secondAfterClear = store.verify(second)

        assertTrue(deleted.accepted)
        assertFalse(firstAfterDelete.verified)
        assertFalse(firstAfterDelete.foundStoredArtifact)
        assertTrue(cleared.accepted)
        assertFalse(secondAfterClear.verified)
        assertFalse(secondAfterClear.foundStoredArtifact)
        assertTrue(storage.entries.isEmpty())

        storage.failDelete = true
        assertEquals(
            UserProvidedPriceProofArtifactStorageIssue.DELETE_FAILED,
            store.delete(first).issue
        )
        storage.failDelete = false
        storage.failClear = true
        assertEquals(
            UserProvidedPriceProofArtifactStorageIssue.DELETE_FAILED,
            store.clearAll().issue
        )
    }

    @Test
    fun `artifact retention is private bounded storage only with no factual authority`() {
        val source = source("UserProvidedPriceProofArtifactLocalStore.kt").readSourceText()

        listOf(
            "context.noBackupFilesDir",
            "AtomicFile",
            "MAX_RETAINED_ARTIFACTS",
            "MAX_RETAINED_BYTES",
            "artifact.sha256",
            "UserProvidedPriceProofArtifact\n                .fingerprint",
            "USER_PRICE_PROOF_STORAGE_KEY"
        ).forEach { required ->
            assertTrue("Expected retention boundary $required", source.contains(required))
        }

        listOf(
            "ShoppingEvidence(",
            "EvidenceClaim(",
            "EvidenceAuthorityClass.",
            "EvidenceClaimDomain.",
            "ProviderOfferImportRecord",
            "StapleWatch",
            "System.currentTimeMillis",
            "SharedPreferences",
            "WorkManager",
            "NotificationManager",
            "OcrScanner.scan",
            "java.net",
            "android.permission",
            "context.filesDir"
        ).forEach { forbidden ->
            assertFalse("Retention boundary must not own $forbidden", source.contains(forbidden))
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
        var forcedReadIssue: UserProvidedPriceProofRawReadIssue? = null
        var forcedReadFound: Boolean = false
        var forcedInventory: UserProvidedPriceProofInventoryResult? = null
        var failReplace: Boolean = false
        var corruptSuccessfulReplace: Boolean = false
        var failDelete: Boolean = false
        var failClear: Boolean = false
        var readCount: Int = 0
        var inventoryCount: Int = 0
        var replaceCount: Int = 0
        var lastStorageKey: String? = null

        override fun read(
            storageKey: String,
            maxBytes: Int
        ): UserProvidedPriceProofRawReadResult {
            readCount += 1
            forcedReadIssue?.let { issue ->
                return UserProvidedPriceProofRawReadResult(
                    bytes = null,
                    found = forcedReadFound,
                    issue = issue
                )
            }

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
            replaceCount += 1
            lastStorageKey = storageKey
            if (failReplace) return false

            entries[storageKey] =
                if (corruptSuccessfulReplace) {
                    bytes.copyOf().also { copy -> copy[0] = (copy[0].toInt() xor 0x01).toByte() }
                } else {
                    bytes.copyOf()
                }
            return true
        }

        override fun delete(storageKey: String): Boolean {
            if (failDelete) return false
            entries.remove(storageKey)
            return true
        }

        override fun clearAll(): Boolean {
            if (failClear) return false
            entries.clear()
            return true
        }

        override fun inventory(
            maxArtifactBytes: Int
        ): UserProvidedPriceProofInventoryResult {
            inventoryCount += 1
            forcedInventory?.let { return it }

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
