package com.valuepilot.app

import android.content.Context
import android.util.AtomicFile
import com.valuepilot.core.ShoppingItemKey
import com.valuepilot.core.ShoppingStoreKey
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream

private const val SAVED_DISPLAY_METADATA_FILE_NAME = "practical-shopping-saved-display-metadata.v1"
private const val SAVED_DISPLAY_METADATA_READ_BUFFER_BYTES = 8_192

internal enum class PracticalShoppingSavedDisplayMetadataRawReadIssue {
    IO_FAILURE,
    INPUT_TOO_LARGE
}

internal data class PracticalShoppingSavedDisplayMetadataRawReadResult(
    val bytes: ByteArray?,
    val found: Boolean,
    val issue: PracticalShoppingSavedDisplayMetadataRawReadIssue? = null
) {
    init {
        require(bytes == null || found)
        require(issue == null || bytes == null)
        require(issue != null || found == (bytes != null))
    }
}

/** Injectable byte-storage boundary so display-metadata persistence stays JVM-testable. */
internal interface PracticalShoppingSavedDisplayMetadataByteStorage {
    fun read(maxBytes: Int): PracticalShoppingSavedDisplayMetadataRawReadResult
    fun replace(bytes: ByteArray): Boolean
    fun delete(): Boolean
}

/**
 * App-internal crash-safe byte storage for Saved presentation metadata.
 *
 * This is deliberately a different AtomicFile from saved exact preferences. Display metadata
 * is non-authoritative and may be stale/orphaned after an interrupted multi-file workflow;
 * callers must always pass decoded metadata through
 * [PracticalShoppingSavedExactPreferenceDisplayMetadataBinder] before presentation.
 */
private class AndroidAtomicSavedDisplayMetadataByteStorage(
    context: Context
) : PracticalShoppingSavedDisplayMetadataByteStorage {

    private val atomicFile =
        AtomicFile(File(context.filesDir, SAVED_DISPLAY_METADATA_FILE_NAME))

    override fun read(maxBytes: Int): PracticalShoppingSavedDisplayMetadataRawReadResult {
        require(maxBytes > 0)

        val input =
            try {
                atomicFile.openRead()
            } catch (_: FileNotFoundException) {
                return if (atomicFile.baseFile.exists()) {
                    PracticalShoppingSavedDisplayMetadataRawReadResult(
                        bytes = null,
                        found = true,
                        issue = PracticalShoppingSavedDisplayMetadataRawReadIssue.IO_FAILURE
                    )
                } else {
                    PracticalShoppingSavedDisplayMetadataRawReadResult(
                        bytes = null,
                        found = false
                    )
                }
            } catch (_: Exception) {
                return PracticalShoppingSavedDisplayMetadataRawReadResult(
                    bytes = null,
                    found = atomicFile.baseFile.exists(),
                    issue = PracticalShoppingSavedDisplayMetadataRawReadIssue.IO_FAILURE
                )
            }

        return try {
            input.use { stream ->
                val output =
                    ByteArrayOutputStream(
                        minOf(maxBytes, SAVED_DISPLAY_METADATA_READ_BUFFER_BYTES)
                    )
                val buffer = ByteArray(SAVED_DISPLAY_METADATA_READ_BUFFER_BYTES)
                var total = 0

                while (true) {
                    val read = stream.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > maxBytes) {
                        return PracticalShoppingSavedDisplayMetadataRawReadResult(
                            bytes = null,
                            found = true,
                            issue = PracticalShoppingSavedDisplayMetadataRawReadIssue.INPUT_TOO_LARGE
                        )
                    }
                    output.write(buffer, 0, read)
                }

                PracticalShoppingSavedDisplayMetadataRawReadResult(
                    bytes = output.toByteArray(),
                    found = true
                )
            }
        } catch (_: Exception) {
            PracticalShoppingSavedDisplayMetadataRawReadResult(
                bytes = null,
                found = true,
                issue = PracticalShoppingSavedDisplayMetadataRawReadIssue.IO_FAILURE
            )
        }
    }

    override fun replace(bytes: ByteArray): Boolean {
        var output: FileOutputStream? = null
        return try {
            output = atomicFile.startWrite()
            output.write(bytes)
            atomicFile.finishWrite(output)
            true
        } catch (_: Exception) {
            output?.let { stream -> runCatching { atomicFile.failWrite(stream) } }
            false
        }
    }

    override fun delete(): Boolean =
        try {
            atomicFile.delete()
            true
        } catch (_: Exception) {
            false
        }
}

enum class PracticalShoppingSavedDisplayMetadataStorageIssue {
    READ_FAILED,
    STORED_DATA_TOO_LARGE,
    STORED_DATA_INVALID,
    ENCODE_REJECTED,
    WRITE_FAILED,
    DELETE_FAILED
}

data class PracticalShoppingSavedDisplayMetadataStorageLoadResult(
    val snapshot: PracticalShoppingSavedExactPreferenceDisplayMetadataSnapshot?,
    val foundStoredDocument: Boolean,
    val issue: PracticalShoppingSavedDisplayMetadataStorageIssue? = null,
    val codecIssue: PracticalShoppingSavedExactPreferenceDisplayMetadataCodecIssue? = null
) {
    init {
        require((snapshot != null) == (issue == null))
        require(
            issue == PracticalShoppingSavedDisplayMetadataStorageIssue.STORED_DATA_INVALID ||
                codecIssue == null
        )
    }

    val accepted: Boolean
        get() = snapshot != null
}

data class PracticalShoppingSavedDisplayMetadataStorageMutationResult(
    val snapshot: PracticalShoppingSavedExactPreferenceDisplayMetadataSnapshot?,
    val issue: PracticalShoppingSavedDisplayMetadataStorageIssue? = null,
    val codecIssue: PracticalShoppingSavedExactPreferenceDisplayMetadataCodecIssue? = null
) {
    init {
        require((snapshot != null) == (issue == null))
        require(
            issue == PracticalShoppingSavedDisplayMetadataStorageIssue.STORED_DATA_INVALID ||
                issue == PracticalShoppingSavedDisplayMetadataStorageIssue.ENCODE_REJECTED ||
                codecIssue == null
        )
    }

    val accepted: Boolean
        get() = snapshot != null
}

/**
 * Local persistence boundary for identity-bound Saved display metadata.
 *
 * This store owns no exact product/store authority and never binds labels by itself. The
 * decoded snapshot is detached presentation metadata until the verified binder checks it
 * against the current saved-exact-preference state. All operations are synchronized on one
 * store instance because AtomicFile provides replacement atomicity, not concurrency locking.
 *
 * Corrupt data blocks selective deletion rather than being partially repaired. clearAll()
 * remains a recovery path because deleting non-authoritative presentation metadata cannot
 * delete or alter the separately persisted exact saved preferences.
 */
class PracticalShoppingSavedDisplayMetadataLocalStore internal constructor(
    private val storage: PracticalShoppingSavedDisplayMetadataByteStorage
) {

    constructor(context: Context) :
        this(AndroidAtomicSavedDisplayMetadataByteStorage(context.applicationContext ?: context))

    @Synchronized
    fun load(): PracticalShoppingSavedDisplayMetadataStorageLoadResult = loadInternal()

    @Synchronized
    fun replace(
        snapshot: PracticalShoppingSavedExactPreferenceDisplayMetadataSnapshot
    ): PracticalShoppingSavedDisplayMetadataStorageMutationResult = replaceInternal(snapshot)

    @Synchronized
    fun deleteProduct(
        itemKey: ShoppingItemKey
    ): PracticalShoppingSavedDisplayMetadataStorageMutationResult {
        val loaded = loadInternal()
        if (!loaded.accepted) return loaded.toMutationFailure()

        val current = requireNotNull(loaded.snapshot)
        val updatedEntries = current.productEntries.filterNot { it.itemKey == itemKey }
        if (updatedEntries.size == current.productEntries.size) {
            return PracticalShoppingSavedDisplayMetadataStorageMutationResult(snapshot = current)
        }
        return replaceInternal(current.copy(productEntries = updatedEntries))
    }

    @Synchronized
    fun deleteStore(
        storeKey: ShoppingStoreKey
    ): PracticalShoppingSavedDisplayMetadataStorageMutationResult {
        val loaded = loadInternal()
        if (!loaded.accepted) return loaded.toMutationFailure()

        val current = requireNotNull(loaded.snapshot)
        val updatedEntries = current.storeEntries.filterNot { it.storeKey == storeKey }
        if (updatedEntries.size == current.storeEntries.size) {
            return PracticalShoppingSavedDisplayMetadataStorageMutationResult(snapshot = current)
        }
        return replaceInternal(current.copy(storeEntries = updatedEntries))
    }

    @Synchronized
    fun clearAll(): PracticalShoppingSavedDisplayMetadataStorageMutationResult =
        if (storage.delete()) {
            PracticalShoppingSavedDisplayMetadataStorageMutationResult(
                snapshot = PracticalShoppingSavedExactPreferenceDisplayMetadataSnapshot()
            )
        } else {
            PracticalShoppingSavedDisplayMetadataStorageMutationResult(
                snapshot = null,
                issue = PracticalShoppingSavedDisplayMetadataStorageIssue.DELETE_FAILED
            )
        }

    private fun loadInternal(): PracticalShoppingSavedDisplayMetadataStorageLoadResult {
        val raw =
            storage.read(
                PracticalShoppingSavedExactPreferenceDisplayMetadataCodec.maximumEncodedBytes
            )

        when (raw.issue) {
            PracticalShoppingSavedDisplayMetadataRawReadIssue.IO_FAILURE ->
                return PracticalShoppingSavedDisplayMetadataStorageLoadResult(
                    snapshot = null,
                    foundStoredDocument = raw.found,
                    issue = PracticalShoppingSavedDisplayMetadataStorageIssue.READ_FAILED
                )

            PracticalShoppingSavedDisplayMetadataRawReadIssue.INPUT_TOO_LARGE ->
                return PracticalShoppingSavedDisplayMetadataStorageLoadResult(
                    snapshot = null,
                    foundStoredDocument = true,
                    issue = PracticalShoppingSavedDisplayMetadataStorageIssue.STORED_DATA_TOO_LARGE
                )

            null -> Unit
        }

        if (!raw.found) {
            return PracticalShoppingSavedDisplayMetadataStorageLoadResult(
                snapshot = PracticalShoppingSavedExactPreferenceDisplayMetadataSnapshot(),
                foundStoredDocument = false
            )
        }

        val decoded =
            PracticalShoppingSavedExactPreferenceDisplayMetadataCodec.decode(
                requireNotNull(raw.bytes)
            )
        if (!decoded.accepted) {
            return PracticalShoppingSavedDisplayMetadataStorageLoadResult(
                snapshot = null,
                foundStoredDocument = true,
                issue = PracticalShoppingSavedDisplayMetadataStorageIssue.STORED_DATA_INVALID,
                codecIssue = decoded.issue
            )
        }

        return PracticalShoppingSavedDisplayMetadataStorageLoadResult(
            snapshot = requireNotNull(decoded.snapshot),
            foundStoredDocument = true
        )
    }

    private fun replaceInternal(
        snapshot: PracticalShoppingSavedExactPreferenceDisplayMetadataSnapshot
    ): PracticalShoppingSavedDisplayMetadataStorageMutationResult {
        val encoded = PracticalShoppingSavedExactPreferenceDisplayMetadataCodec.encode(snapshot)
        if (!encoded.accepted) {
            return PracticalShoppingSavedDisplayMetadataStorageMutationResult(
                snapshot = null,
                issue = PracticalShoppingSavedDisplayMetadataStorageIssue.ENCODE_REJECTED,
                codecIssue = encoded.issue
            )
        }

        if (!storage.replace(requireNotNull(encoded.bytes))) {
            return PracticalShoppingSavedDisplayMetadataStorageMutationResult(
                snapshot = null,
                issue = PracticalShoppingSavedDisplayMetadataStorageIssue.WRITE_FAILED
            )
        }

        return PracticalShoppingSavedDisplayMetadataStorageMutationResult(snapshot = snapshot)
    }

    private fun PracticalShoppingSavedDisplayMetadataStorageLoadResult.toMutationFailure():
        PracticalShoppingSavedDisplayMetadataStorageMutationResult =
        PracticalShoppingSavedDisplayMetadataStorageMutationResult(
            snapshot = null,
            issue = requireNotNull(issue),
            codecIssue = codecIssue
        )
}
