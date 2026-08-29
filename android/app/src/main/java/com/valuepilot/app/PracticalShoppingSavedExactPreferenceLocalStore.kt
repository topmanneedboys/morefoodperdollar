package com.valuepilot.app

import android.content.Context
import android.util.AtomicFile
import com.valuepilot.core.ShoppingItemKey
import com.valuepilot.core.ShoppingStoreKey
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream

private const val SAVED_EXACT_PREFERENCE_FILE_NAME = "practical-shopping-saved-exact-preferences.v1"
private const val READ_BUFFER_BYTES = 8_192

internal enum class PracticalShoppingSavedExactPreferenceRawReadIssue {
    IO_FAILURE,
    INPUT_TOO_LARGE
}

internal data class PracticalShoppingSavedExactPreferenceRawReadResult(
    val bytes: ByteArray?,
    val found: Boolean,
    val issue: PracticalShoppingSavedExactPreferenceRawReadIssue? = null
) {
    init {
        require(bytes == null || found)
        require(issue == null || bytes == null)
        require(issue != null || found == (bytes != null))
    }
}

/** Small injectable byte-store boundary used so persistence policy remains JVM-testable. */
internal interface PracticalShoppingSavedExactPreferenceByteStorage {
    fun read(maxBytes: Int): PracticalShoppingSavedExactPreferenceRawReadResult
    fun replace(bytes: ByteArray): Boolean
    fun delete(): Boolean
}

/**
 * Android app-internal storage backed by framework [AtomicFile].
 *
 * AtomicFile is available below ValuePilot's minSdk and commits only a fully written,
 * synced replacement. Reads are streamed with a hard upper bound rather than using
 * AtomicFile.readFully(), so an unexpectedly large/corrupt file cannot force an
 * unbounded allocation before the codec gets a chance to reject it.
 */
private class AndroidAtomicSavedExactPreferenceByteStorage(
    context: Context
) : PracticalShoppingSavedExactPreferenceByteStorage {

    private val atomicFile =
        AtomicFile(File(context.filesDir, SAVED_EXACT_PREFERENCE_FILE_NAME))

    override fun read(
        maxBytes: Int
    ): PracticalShoppingSavedExactPreferenceRawReadResult {
        require(maxBytes > 0)

        val input =
            try {
                atomicFile.openRead()
            } catch (_: FileNotFoundException) {
                return if (atomicFile.baseFile.exists()) {
                    PracticalShoppingSavedExactPreferenceRawReadResult(
                        bytes = null,
                        found = true,
                        issue = PracticalShoppingSavedExactPreferenceRawReadIssue.IO_FAILURE
                    )
                } else {
                    PracticalShoppingSavedExactPreferenceRawReadResult(
                        bytes = null,
                        found = false
                    )
                }
            } catch (_: Exception) {
                return PracticalShoppingSavedExactPreferenceRawReadResult(
                    bytes = null,
                    found = atomicFile.baseFile.exists(),
                    issue = PracticalShoppingSavedExactPreferenceRawReadIssue.IO_FAILURE
                )
            }

        return try {
            input.use { stream ->
                val output = ByteArrayOutputStream(minOf(maxBytes, READ_BUFFER_BYTES))
                val buffer = ByteArray(READ_BUFFER_BYTES)
                var total = 0

                while (true) {
                    val read = stream.read(buffer)
                    if (read < 0) {
                        break
                    }
                    total += read
                    if (total > maxBytes) {
                        return PracticalShoppingSavedExactPreferenceRawReadResult(
                            bytes = null,
                            found = true,
                            issue = PracticalShoppingSavedExactPreferenceRawReadIssue.INPUT_TOO_LARGE
                        )
                    }
                    output.write(buffer, 0, read)
                }

                PracticalShoppingSavedExactPreferenceRawReadResult(
                    bytes = output.toByteArray(),
                    found = true
                )
            }
        } catch (_: Exception) {
            PracticalShoppingSavedExactPreferenceRawReadResult(
                bytes = null,
                found = true,
                issue = PracticalShoppingSavedExactPreferenceRawReadIssue.IO_FAILURE
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
            output?.let { stream ->
                runCatching { atomicFile.failWrite(stream) }
            }
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

enum class PracticalShoppingSavedExactPreferenceStorageIssue {
    READ_FAILED,
    STORED_DATA_TOO_LARGE,
    STORED_DATA_INVALID,
    ENCODE_REJECTED,
    WRITE_FAILED,
    DELETE_FAILED
}

data class PracticalShoppingSavedExactPreferenceStorageLoadResult(
    val state: PracticalShoppingSavedExactPreferenceState?,
    val foundStoredDocument: Boolean,
    val issue: PracticalShoppingSavedExactPreferenceStorageIssue? = null,
    val codecIssue: PracticalShoppingSavedExactPreferenceCodecIssue? = null,
    val documentIssues: Set<PracticalShoppingSavedExactPreferenceLoadIssue> = emptySet()
) {
    init {
        require((state != null) == (issue == null))
        require(codecIssue == null || documentIssues.isEmpty())
        require(
            issue == PracticalShoppingSavedExactPreferenceStorageIssue.STORED_DATA_INVALID ||
                (codecIssue == null && documentIssues.isEmpty())
        )
    }

    val accepted: Boolean
        get() = state != null
}

data class PracticalShoppingSavedExactPreferenceStorageMutationResult(
    val state: PracticalShoppingSavedExactPreferenceState?,
    val issue: PracticalShoppingSavedExactPreferenceStorageIssue? = null,
    val codecIssue: PracticalShoppingSavedExactPreferenceCodecIssue? = null,
    val documentIssues: Set<PracticalShoppingSavedExactPreferenceLoadIssue> = emptySet()
) {
    init {
        require((state != null) == (issue == null))
        require(codecIssue == null || documentIssues.isEmpty())
        require(
            issue == PracticalShoppingSavedExactPreferenceStorageIssue.STORED_DATA_INVALID ||
                issue == PracticalShoppingSavedExactPreferenceStorageIssue.ENCODE_REJECTED ||
                (codecIssue == null && documentIssues.isEmpty())
        )
    }

    val accepted: Boolean
        get() = state != null
}

/**
 * First on-device persistence boundary for saved exact Practical Shopping preferences.
 *
 * This class stores one versioned codec document in app-internal AtomicFile storage.
 * It owns no UI, product/store matching, price/travel authority, network, account or
 * clock. All public operations are synchronized on the store instance because AtomicFile
 * deliberately provides atomic replacement but not concurrency locking.
 *
 * A corrupt/unsupported document blocks selective mutation rather than being partially
 * repaired. clearAll() is intentionally able to delete corrupt state so the user can
 * always recover. Delete-one operations are idempotent when the requested key is absent.
 */
class PracticalShoppingSavedExactPreferenceLocalStore internal constructor(
    private val storage: PracticalShoppingSavedExactPreferenceByteStorage
) {

    constructor(context: Context) :
        this(AndroidAtomicSavedExactPreferenceByteStorage(context.applicationContext ?: context))

    @Synchronized
    fun load(): PracticalShoppingSavedExactPreferenceStorageLoadResult =
        loadInternal()

    @Synchronized
    fun replace(
        state: PracticalShoppingSavedExactPreferenceState
    ): PracticalShoppingSavedExactPreferenceStorageMutationResult =
        replaceInternal(state)

    @Synchronized
    fun deleteProduct(
        itemKey: ShoppingItemKey
    ): PracticalShoppingSavedExactPreferenceStorageMutationResult {
        val loaded = loadInternal()
        if (!loaded.accepted) {
            return loaded.toMutationFailure()
        }

        val current = requireNotNull(loaded.state)
        val updated = PracticalShoppingSavedExactPreferenceStateManager.removeProduct(current, itemKey)
        if (updated == current) {
            return PracticalShoppingSavedExactPreferenceStorageMutationResult(state = current)
        }
        return replaceInternal(updated)
    }

    @Synchronized
    fun deleteStore(
        storeKey: ShoppingStoreKey
    ): PracticalShoppingSavedExactPreferenceStorageMutationResult {
        val loaded = loadInternal()
        if (!loaded.accepted) {
            return loaded.toMutationFailure()
        }

        val current = requireNotNull(loaded.state)
        val updated = PracticalShoppingSavedExactPreferenceStateManager.removeStore(current, storeKey)
        if (updated == current) {
            return PracticalShoppingSavedExactPreferenceStorageMutationResult(state = current)
        }
        return replaceInternal(updated)
    }

    @Synchronized
    fun clearAll(): PracticalShoppingSavedExactPreferenceStorageMutationResult =
        if (storage.delete()) {
            PracticalShoppingSavedExactPreferenceStorageMutationResult(
                state = PracticalShoppingSavedExactPreferenceState.empty()
            )
        } else {
            PracticalShoppingSavedExactPreferenceStorageMutationResult(
                state = null,
                issue = PracticalShoppingSavedExactPreferenceStorageIssue.DELETE_FAILED
            )
        }

    private fun loadInternal(): PracticalShoppingSavedExactPreferenceStorageLoadResult {
        val raw = storage.read(PracticalShoppingSavedExactPreferenceCodec.maximumEncodedBytes)
        when (raw.issue) {
            PracticalShoppingSavedExactPreferenceRawReadIssue.IO_FAILURE ->
                return PracticalShoppingSavedExactPreferenceStorageLoadResult(
                    state = null,
                    foundStoredDocument = raw.found,
                    issue = PracticalShoppingSavedExactPreferenceStorageIssue.READ_FAILED
                )

            PracticalShoppingSavedExactPreferenceRawReadIssue.INPUT_TOO_LARGE ->
                return PracticalShoppingSavedExactPreferenceStorageLoadResult(
                    state = null,
                    foundStoredDocument = true,
                    issue = PracticalShoppingSavedExactPreferenceStorageIssue.STORED_DATA_TOO_LARGE
                )

            null -> Unit
        }

        if (!raw.found) {
            return PracticalShoppingSavedExactPreferenceStorageLoadResult(
                state = PracticalShoppingSavedExactPreferenceState.empty(),
                foundStoredDocument = false
            )
        }

        val decoded = PracticalShoppingSavedExactPreferenceCodec.decode(requireNotNull(raw.bytes))
        if (!decoded.accepted) {
            return PracticalShoppingSavedExactPreferenceStorageLoadResult(
                state = null,
                foundStoredDocument = true,
                issue = PracticalShoppingSavedExactPreferenceStorageIssue.STORED_DATA_INVALID,
                codecIssue = decoded.codecIssue,
                documentIssues = decoded.documentIssues
            )
        }

        return PracticalShoppingSavedExactPreferenceStorageLoadResult(
            state = requireNotNull(decoded.state),
            foundStoredDocument = true
        )
    }

    private fun replaceInternal(
        state: PracticalShoppingSavedExactPreferenceState
    ): PracticalShoppingSavedExactPreferenceStorageMutationResult {
        val encoded = PracticalShoppingSavedExactPreferenceCodec.encode(state)
        if (!encoded.accepted) {
            return PracticalShoppingSavedExactPreferenceStorageMutationResult(
                state = null,
                issue = PracticalShoppingSavedExactPreferenceStorageIssue.ENCODE_REJECTED,
                codecIssue = encoded.issue
            )
        }

        if (!storage.replace(requireNotNull(encoded.bytes))) {
            return PracticalShoppingSavedExactPreferenceStorageMutationResult(
                state = null,
                issue = PracticalShoppingSavedExactPreferenceStorageIssue.WRITE_FAILED
            )
        }

        return PracticalShoppingSavedExactPreferenceStorageMutationResult(state = state)
    }

    private fun PracticalShoppingSavedExactPreferenceStorageLoadResult.toMutationFailure():
        PracticalShoppingSavedExactPreferenceStorageMutationResult =
        PracticalShoppingSavedExactPreferenceStorageMutationResult(
            state = null,
            issue = requireNotNull(issue),
            codecIssue = codecIssue,
            documentIssues = documentIssues
        )
}
