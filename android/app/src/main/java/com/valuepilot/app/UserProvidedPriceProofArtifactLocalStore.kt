package com.valuepilot.app

import android.content.Context
import android.util.AtomicFile
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream

private const val USER_PRICE_PROOF_DIRECTORY_NAME = "user-price-proof-artifacts.v1"
private const val USER_PRICE_PROOF_READ_BUFFER_BYTES = 8_192
private val USER_PRICE_PROOF_STORAGE_KEY = Regex("[0-9a-f]{64}")
private val USER_PRICE_PROOF_COMMITTED_FILE = Regex("[0-9a-f]{64}\\.bin")

internal enum class UserProvidedPriceProofRawReadIssue {
    IO_FAILURE,
    INPUT_TOO_LARGE
}

internal data class UserProvidedPriceProofRawReadResult(
    val bytes: ByteArray?,
    val found: Boolean,
    val issue: UserProvidedPriceProofRawReadIssue? = null
) {
    init {
        require(bytes == null || found)
        require(issue == null || bytes == null)
        require(issue != null || found == (bytes != null))
    }
}

internal enum class UserProvidedPriceProofInventoryIssue {
    IO_FAILURE,
    INVALID_COMMITTED_ARTIFACT
}

internal data class UserProvidedPriceProofInventoryResult(
    val artifactCount: Int?,
    val totalBytes: Long?,
    val issue: UserProvidedPriceProofInventoryIssue? = null
) {
    init {
        require((artifactCount != null) == (totalBytes != null))
        require((artifactCount != null) == (issue == null))
        require(artifactCount == null || artifactCount >= 0)
        require(totalBytes == null || totalBytes >= 0L)
    }

    val accepted: Boolean
        get() = issue == null
}

/**
 * Injectable byte-storage boundary so retention policy and corruption handling remain JVM-testable.
 * Storage keys are content digests produced by [UserProvidedPriceProofArtifact], never caller paths.
 */
internal interface UserProvidedPriceProofArtifactByteStorage {
    fun read(
        storageKey: String,
        maxBytes: Int
    ): UserProvidedPriceProofRawReadResult

    fun replace(
        storageKey: String,
        bytes: ByteArray
    ): Boolean

    fun delete(storageKey: String): Boolean

    fun clearAll(): Boolean

    fun inventory(maxArtifactBytes: Int): UserProvidedPriceProofInventoryResult
}

/**
 * Crash-safe, app-private storage for raw receipt/price-tag proof bytes.
 *
 * Raw proofs intentionally live under noBackupFilesDir even though ValuePilot currently disables
 * Android backup globally. Each committed digest has its own AtomicFile so retaining one receipt
 * never rewrites every other proof. AtomicFile temporary/recovery files are ignored by inventory;
 * only exact <sha256>.bin base files count as committed artifacts.
 */
private class AndroidAtomicUserProvidedPriceProofArtifactByteStorage(
    context: Context
) : UserProvidedPriceProofArtifactByteStorage {

    private val directory =
        File(context.noBackupFilesDir, USER_PRICE_PROOF_DIRECTORY_NAME)

    override fun read(
        storageKey: String,
        maxBytes: Int
    ): UserProvidedPriceProofRawReadResult {
        require(USER_PRICE_PROOF_STORAGE_KEY.matches(storageKey))
        require(maxBytes > 0)

        val atomicFile = atomicFile(storageKey)
        val input =
            try {
                atomicFile.openRead()
            } catch (_: FileNotFoundException) {
                return if (atomicFile.baseFile.exists()) {
                    UserProvidedPriceProofRawReadResult(
                        bytes = null,
                        found = true,
                        issue = UserProvidedPriceProofRawReadIssue.IO_FAILURE
                    )
                } else {
                    UserProvidedPriceProofRawReadResult(
                        bytes = null,
                        found = false
                    )
                }
            } catch (_: Exception) {
                return UserProvidedPriceProofRawReadResult(
                    bytes = null,
                    found = atomicFile.baseFile.exists(),
                    issue = UserProvidedPriceProofRawReadIssue.IO_FAILURE
                )
            }

        return try {
            input.use { stream ->
                val output =
                    ByteArrayOutputStream(
                        minOf(maxBytes, USER_PRICE_PROOF_READ_BUFFER_BYTES)
                    )
                val buffer = ByteArray(USER_PRICE_PROOF_READ_BUFFER_BYTES)
                var total = 0

                while (true) {
                    val read = stream.read(buffer)
                    if (read < 0) break
                    if (read > maxBytes - total) {
                        return UserProvidedPriceProofRawReadResult(
                            bytes = null,
                            found = true,
                            issue = UserProvidedPriceProofRawReadIssue.INPUT_TOO_LARGE
                        )
                    }
                    total += read
                    output.write(buffer, 0, read)
                }

                UserProvidedPriceProofRawReadResult(
                    bytes = output.toByteArray(),
                    found = true
                )
            }
        } catch (_: Exception) {
            UserProvidedPriceProofRawReadResult(
                bytes = null,
                found = true,
                issue = UserProvidedPriceProofRawReadIssue.IO_FAILURE
            )
        }
    }

    override fun replace(
        storageKey: String,
        bytes: ByteArray
    ): Boolean {
        require(USER_PRICE_PROOF_STORAGE_KEY.matches(storageKey))
        if (!ensureDirectory()) return false

        val atomicFile = atomicFile(storageKey)
        var output: FileOutputStream? = null
        return try {
            output = atomicFile.startWrite()
            output.write(bytes)
            atomicFile.finishWrite(output)
            true
        } catch (_: Exception) {
            output?.let { stream ->
                runCatching { atomicFile.failWrite(stream) }
            }
            false
        }
    }

    override fun delete(storageKey: String): Boolean {
        require(USER_PRICE_PROOF_STORAGE_KEY.matches(storageKey))
        if (!directory.exists()) return true
        return try {
            atomicFile(storageKey).delete()
            true
        } catch (_: Exception) {
            false
        }
    }

    override fun clearAll(): Boolean {
        if (!directory.exists()) return true
        return try {
            directory.deleteRecursively()
        } catch (_: Exception) {
            false
        }
    }

    override fun inventory(
        maxArtifactBytes: Int
    ): UserProvidedPriceProofInventoryResult {
        require(maxArtifactBytes > 0)
        if (!directory.exists()) {
            return UserProvidedPriceProofInventoryResult(
                artifactCount = 0,
                totalBytes = 0L
            )
        }
        if (!directory.isDirectory) {
            return UserProvidedPriceProofInventoryResult(
                artifactCount = null,
                totalBytes = null,
                issue = UserProvidedPriceProofInventoryIssue.IO_FAILURE
            )
        }

        val files =
            try {
                directory.listFiles()
            } catch (_: Exception) {
                null
            }
                ?: return UserProvidedPriceProofInventoryResult(
                    artifactCount = null,
                    totalBytes = null,
                    issue = UserProvidedPriceProofInventoryIssue.IO_FAILURE
                )

        var count = 0
        var total = 0L
        for (file in files) {
            if (!USER_PRICE_PROOF_COMMITTED_FILE.matches(file.name)) continue
            if (!file.isFile) {
                return UserProvidedPriceProofInventoryResult(
                    artifactCount = null,
                    totalBytes = null,
                    issue = UserProvidedPriceProofInventoryIssue.INVALID_COMMITTED_ARTIFACT
                )
            }

            val length = file.length()
            if (length <= 0L || length > maxArtifactBytes.toLong()) {
                return UserProvidedPriceProofInventoryResult(
                    artifactCount = null,
                    totalBytes = null,
                    issue = UserProvidedPriceProofInventoryIssue.INVALID_COMMITTED_ARTIFACT
                )
            }
            if (Long.MAX_VALUE - total < length) {
                return UserProvidedPriceProofInventoryResult(
                    artifactCount = null,
                    totalBytes = null,
                    issue = UserProvidedPriceProofInventoryIssue.IO_FAILURE
                )
            }
            count += 1
            total += length
        }

        return UserProvidedPriceProofInventoryResult(
            artifactCount = count,
            totalBytes = total
        )
    }

    private fun atomicFile(storageKey: String): AtomicFile =
        AtomicFile(File(directory, "$storageKey.bin"))

    private fun ensureDirectory(): Boolean =
        try {
            (directory.isDirectory || directory.mkdirs()) && directory.isDirectory
        } catch (_: Exception) {
            false
        }
}

enum class UserProvidedPriceProofArtifactStorageIssue {
    PROOF_BYTES_MISMATCH,
    READ_FAILED,
    STORED_ARTIFACT_TOO_LARGE,
    STORED_ARTIFACT_INVALID,
    INVENTORY_FAILED,
    ARTIFACT_COUNT_LIMIT_REACHED,
    TOTAL_BYTES_LIMIT_REACHED,
    WRITE_FAILED,
    DELETE_FAILED
}

data class UserProvidedPriceProofArtifactVerificationResult(
    val artifact: UserProvidedPriceProofArtifact?,
    val foundStoredArtifact: Boolean,
    val issue: UserProvidedPriceProofArtifactStorageIssue? = null
) {
    init {
        require(artifact == null || foundStoredArtifact)
        require(issue == null || artifact == null)
    }

    val verified: Boolean
        get() = artifact != null
}

data class UserProvidedPriceProofArtifactRetentionResult(
    val artifact: UserProvidedPriceProofArtifact?,
    val alreadyRetained: Boolean,
    val issue: UserProvidedPriceProofArtifactStorageIssue? = null
) {
    init {
        require((artifact != null) == (issue == null))
        require(!alreadyRetained || artifact != null)
    }

    val accepted: Boolean
        get() = artifact != null
}

data class UserProvidedPriceProofArtifactMutationResult(
    val issue: UserProvidedPriceProofArtifactStorageIssue? = null
) {
    val accepted: Boolean
        get() = issue == null
}

/**
 * Bounded durable-retention boundary for user-controlled raw observed-price proof artifacts.
 *
 * The store is content-addressed by the SHA-256 that was already computed from actual bytes by
 * [UserProvidedPriceProofArtifact]. The logical artifactId is never a filesystem path. Retention
 * re-checks the caller bytes, reads back every successful write, and re-fingerprints every read.
 * A corrupt existing digest is never silently replaced: corruption must remain visible/fail-closed
 * until the user explicitly deletes/clears the retained proof.
 *
 * This is storage only. Successful retention does not create ShoppingEvidence, EvidenceClaim,
 * current-price authority, Watch facts, or any other factual promotion. A later boundary must
 * explicitly combine an exact user confirmation with a freshly verified retained proof.
 */
class UserProvidedPriceProofArtifactLocalStore internal constructor(
    private val storage: UserProvidedPriceProofArtifactByteStorage
) {

    constructor(context: Context) :
        this(
            AndroidAtomicUserProvidedPriceProofArtifactByteStorage(
                context.applicationContext ?: context
            )
        )

    @Synchronized
    fun retain(
        artifact: UserProvidedPriceProofArtifact,
        artifactBytes: ByteArray
    ): UserProvidedPriceProofArtifactRetentionResult {
        if (!matchesArtifact(artifact, artifactBytes)) {
            return retentionFailure(
                UserProvidedPriceProofArtifactStorageIssue.PROOF_BYTES_MISMATCH
            )
        }

        val existing = verifyInternal(artifact)
        if (existing.issue != null) {
            return retentionFailure(existing.issue)
        }
        if (existing.verified) {
            return UserProvidedPriceProofArtifactRetentionResult(
                artifact = artifact,
                alreadyRetained = true
            )
        }

        val inventory = storage.inventory(UserProvidedPriceProofArtifact.MAX_ARTIFACT_BYTES)
        when (inventory.issue) {
            UserProvidedPriceProofInventoryIssue.IO_FAILURE ->
                return retentionFailure(
                    UserProvidedPriceProofArtifactStorageIssue.INVENTORY_FAILED
                )

            UserProvidedPriceProofInventoryIssue.INVALID_COMMITTED_ARTIFACT ->
                return retentionFailure(
                    UserProvidedPriceProofArtifactStorageIssue.STORED_ARTIFACT_INVALID
                )

            null -> Unit
        }

        val count = requireNotNull(inventory.artifactCount)
        val retainedBytes = requireNotNull(inventory.totalBytes)
        if (count >= MAX_RETAINED_ARTIFACTS) {
            return retentionFailure(
                UserProvidedPriceProofArtifactStorageIssue.ARTIFACT_COUNT_LIMIT_REACHED
            )
        }
        if (
            retainedBytes >
            MAX_RETAINED_BYTES - artifact.byteLength.toLong()
        ) {
            return retentionFailure(
                UserProvidedPriceProofArtifactStorageIssue.TOTAL_BYTES_LIMIT_REACHED
            )
        }

        if (!storage.replace(artifact.sha256, artifactBytes)) {
            return retentionFailure(
                UserProvidedPriceProofArtifactStorageIssue.WRITE_FAILED
            )
        }

        val verifiedWrite = verifyInternal(artifact)
        if (!verifiedWrite.verified) {
            return retentionFailure(
                verifiedWrite.issue
                    ?: UserProvidedPriceProofArtifactStorageIssue.STORED_ARTIFACT_INVALID
            )
        }

        return UserProvidedPriceProofArtifactRetentionResult(
            artifact = artifact,
            alreadyRetained = false
        )
    }

    @Synchronized
    fun verify(
        artifact: UserProvidedPriceProofArtifact
    ): UserProvidedPriceProofArtifactVerificationResult =
        verifyInternal(artifact)

    @Synchronized
    fun delete(
        artifact: UserProvidedPriceProofArtifact
    ): UserProvidedPriceProofArtifactMutationResult =
        if (storage.delete(artifact.sha256)) {
            UserProvidedPriceProofArtifactMutationResult()
        } else {
            UserProvidedPriceProofArtifactMutationResult(
                issue = UserProvidedPriceProofArtifactStorageIssue.DELETE_FAILED
            )
        }

    @Synchronized
    fun clearAll(): UserProvidedPriceProofArtifactMutationResult =
        if (storage.clearAll()) {
            UserProvidedPriceProofArtifactMutationResult()
        } else {
            UserProvidedPriceProofArtifactMutationResult(
                issue = UserProvidedPriceProofArtifactStorageIssue.DELETE_FAILED
            )
        }

    private fun verifyInternal(
        artifact: UserProvidedPriceProofArtifact
    ): UserProvidedPriceProofArtifactVerificationResult {
        val raw =
            storage.read(
                storageKey = artifact.sha256,
                maxBytes = UserProvidedPriceProofArtifact.MAX_ARTIFACT_BYTES
            )

        when (raw.issue) {
            UserProvidedPriceProofRawReadIssue.IO_FAILURE ->
                return UserProvidedPriceProofArtifactVerificationResult(
                    artifact = null,
                    foundStoredArtifact = raw.found,
                    issue = UserProvidedPriceProofArtifactStorageIssue.READ_FAILED
                )

            UserProvidedPriceProofRawReadIssue.INPUT_TOO_LARGE ->
                return UserProvidedPriceProofArtifactVerificationResult(
                    artifact = null,
                    foundStoredArtifact = true,
                    issue = UserProvidedPriceProofArtifactStorageIssue.STORED_ARTIFACT_TOO_LARGE
                )

            null -> Unit
        }

        if (!raw.found) {
            return UserProvidedPriceProofArtifactVerificationResult(
                artifact = null,
                foundStoredArtifact = false
            )
        }

        val bytes = requireNotNull(raw.bytes)
        if (!matchesArtifact(artifact, bytes)) {
            return UserProvidedPriceProofArtifactVerificationResult(
                artifact = null,
                foundStoredArtifact = true,
                issue = UserProvidedPriceProofArtifactStorageIssue.STORED_ARTIFACT_INVALID
            )
        }

        return UserProvidedPriceProofArtifactVerificationResult(
            artifact = artifact,
            foundStoredArtifact = true
        )
    }

    private fun matchesArtifact(
        artifact: UserProvidedPriceProofArtifact,
        bytes: ByteArray
    ): Boolean {
        if (bytes.size != artifact.byteLength) return false
        val refingerprinted =
            UserProvidedPriceProofArtifact
                .fingerprint(
                    artifactId = artifact.artifactId,
                    proofType = artifact.proofType,
                    artifactBytes = bytes
                )
                .artifact
                ?: return false

        return refingerprinted.sha256 == artifact.sha256 &&
            refingerprinted.byteLength == artifact.byteLength
    }

    private fun retentionFailure(
        issue: UserProvidedPriceProofArtifactStorageIssue
    ): UserProvidedPriceProofArtifactRetentionResult =
        UserProvidedPriceProofArtifactRetentionResult(
            artifact = null,
            alreadyRetained = false,
            issue = issue
        )

    companion object {
        const val MAX_RETAINED_ARTIFACTS: Int = 64
        const val MAX_RETAINED_BYTES: Long = 64L * 1024L * 1024L
    }
}
