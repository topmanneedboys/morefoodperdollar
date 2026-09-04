package com.valuepilot.app

import android.content.Context
import android.util.AtomicFile
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream

private const val MEMORY_FILE_NAME = "compare-here-private-price-memory.v1"
private const val READ_BUFFER_BYTES = 8_192

internal enum class CompareHerePrivatePriceMemoryStoreIssue {
    READ_FAILED,
    STORED_DATA_TOO_LARGE,
    STORED_DATA_INVALID,
    ENCODE_REJECTED,
    WRITE_FAILED,
    DELETE_FAILED
}

internal data class CompareHerePrivatePriceMemoryLoadResult(
    val state: CompareHerePrivatePriceMemoryState?,
    val foundStoredDocument: Boolean,
    val issue: CompareHerePrivatePriceMemoryStoreIssue? = null,
    val codecIssue: CompareHerePrivatePriceMemoryCodecIssue? = null
) {
    init {
        require((state != null) == (issue == null))
        require(codecIssue == null || issue == CompareHerePrivatePriceMemoryStoreIssue.STORED_DATA_INVALID)
    }

    val accepted: Boolean
        get() = state != null
}

internal data class CompareHerePrivatePriceMemoryMutationResult(
    val state: CompareHerePrivatePriceMemoryState?,
    val issue: CompareHerePrivatePriceMemoryStoreIssue? = null,
    val codecIssue: CompareHerePrivatePriceMemoryCodecIssue? = null
) {
    init {
        require((state != null) == (issue == null))
        require(codecIssue == null || issue == CompareHerePrivatePriceMemoryStoreIssue.STORED_DATA_INVALID)
    }

    val accepted: Boolean
        get() = state != null
}

internal interface CompareHerePrivatePriceMemoryStore {
    fun load(): CompareHerePrivatePriceMemoryLoadResult
    fun append(capture: CompareHerePrivatePriceMemoryCapture): CompareHerePrivatePriceMemoryMutationResult
    fun clear(): CompareHerePrivatePriceMemoryMutationResult
}

internal data class RawMemoryReadResult(
    val bytes: ByteArray?,
    val found: Boolean,
    val issue: CompareHerePrivatePriceMemoryStoreIssue? = null
)

internal interface MemoryByteStorage {
    fun read(maxBytes: Int): RawMemoryReadResult
    fun replace(bytes: ByteArray): Boolean
    fun delete(): Boolean
}

private class AndroidAtomicMemoryByteStorage(context: Context) : MemoryByteStorage {
    private val atomicFile =
        AtomicFile(File(context.filesDir, MEMORY_FILE_NAME))

    override fun read(maxBytes: Int): RawMemoryReadResult {
        require(maxBytes > 0)
        val input =
            try {
                atomicFile.openRead()
            } catch (_: FileNotFoundException) {
                return if (atomicFile.baseFile.exists()) {
                    RawMemoryReadResult(
                        bytes = null,
                        found = true,
                        issue = CompareHerePrivatePriceMemoryStoreIssue.READ_FAILED
                    )
                } else {
                    RawMemoryReadResult(bytes = null, found = false)
                }
            } catch (_: Exception) {
                return RawMemoryReadResult(
                    bytes = null,
                    found = atomicFile.baseFile.exists(),
                    issue = CompareHerePrivatePriceMemoryStoreIssue.READ_FAILED
                )
            }

        return try {
            input.use { stream ->
                val output = ByteArrayOutputStream(minOf(maxBytes, READ_BUFFER_BYTES))
                val buffer = ByteArray(READ_BUFFER_BYTES)
                var total = 0
                while (true) {
                    val read = stream.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > maxBytes) {
                        return RawMemoryReadResult(
                            bytes = null,
                            found = true,
                            issue = CompareHerePrivatePriceMemoryStoreIssue.STORED_DATA_TOO_LARGE
                        )
                    }
                    output.write(buffer, 0, read)
                }
                RawMemoryReadResult(bytes = output.toByteArray(), found = true)
            }
        } catch (_: Exception) {
            RawMemoryReadResult(
                bytes = null,
                found = true,
                issue = CompareHerePrivatePriceMemoryStoreIssue.READ_FAILED
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

/**
 * App-internal, device-only comparison memory. A malformed document blocks mutation rather than
 * selectively accepting records; the user can clear it and recover without affecting Compare Here.
 * This adapter is intentionally the only Android persistence boundary for these snapshots; no
 * Home/planner/evidence surface can silently promote them into live shopping facts.
 */
internal class CompareHerePrivatePriceMemoryAndroidStore internal constructor(
    private val storage: MemoryByteStorage
) : CompareHerePrivatePriceMemoryStore {

    constructor(context: Context) :
        this(AndroidAtomicMemoryByteStorage(context.applicationContext ?: context))

    @Synchronized
    override fun load(): CompareHerePrivatePriceMemoryLoadResult {
        val raw = storage.read(CompareHerePrivatePriceMemoryCodec.maximumEncodedBytes)
        raw.issue?.let { issue ->
            return CompareHerePrivatePriceMemoryLoadResult(
                state = null,
                foundStoredDocument = raw.found,
                issue = issue
            )
        }
        if (!raw.found) {
            return CompareHerePrivatePriceMemoryLoadResult(
                state = CompareHerePrivatePriceMemoryState.empty(),
                foundStoredDocument = false
            )
        }

        val decoded = CompareHerePrivatePriceMemoryCodec.decode(requireNotNull(raw.bytes))
        if (!decoded.accepted) {
            return CompareHerePrivatePriceMemoryLoadResult(
                state = null,
                foundStoredDocument = true,
                issue = CompareHerePrivatePriceMemoryStoreIssue.STORED_DATA_INVALID,
                codecIssue = decoded.issue
            )
        }
        return CompareHerePrivatePriceMemoryLoadResult(
            state = requireNotNull(decoded.state),
            foundStoredDocument = true
        )
    }

    @Synchronized
    override fun append(
        capture: CompareHerePrivatePriceMemoryCapture
    ): CompareHerePrivatePriceMemoryMutationResult {
        val loaded = load()
        if (!loaded.accepted) {
            return CompareHerePrivatePriceMemoryMutationResult(
                state = null,
                issue = requireNotNull(loaded.issue),
                codecIssue = loaded.codecIssue
            )
        }
        val next =
            CompareHerePrivatePriceMemoryStateManager.append(
                state = requireNotNull(loaded.state),
                capture = capture
            )
        return replace(next)
    }

    @Synchronized
    override fun clear(): CompareHerePrivatePriceMemoryMutationResult =
        if (storage.delete()) {
            CompareHerePrivatePriceMemoryMutationResult(
                state = CompareHerePrivatePriceMemoryState.empty()
            )
        } else {
            CompareHerePrivatePriceMemoryMutationResult(
                state = null,
                issue = CompareHerePrivatePriceMemoryStoreIssue.DELETE_FAILED
            )
        }

    private fun replace(
        state: CompareHerePrivatePriceMemoryState
    ): CompareHerePrivatePriceMemoryMutationResult {
        val encoded = CompareHerePrivatePriceMemoryCodec.encode(state)
        if (!encoded.accepted) {
            return CompareHerePrivatePriceMemoryMutationResult(
                state = null,
                issue = CompareHerePrivatePriceMemoryStoreIssue.ENCODE_REJECTED,
                codecIssue = encoded.issue
            )
        }
        if (!storage.replace(requireNotNull(encoded.bytes))) {
            return CompareHerePrivatePriceMemoryMutationResult(
                state = null,
                issue = CompareHerePrivatePriceMemoryStoreIssue.WRITE_FAILED
            )
        }
        return CompareHerePrivatePriceMemoryMutationResult(state = state)
    }
}
