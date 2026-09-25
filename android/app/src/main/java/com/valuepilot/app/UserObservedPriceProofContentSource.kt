package com.valuepilot.app

import android.content.ContentResolver
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.InputStream

private const val USER_OBSERVED_PRICE_PROOF_READ_BUFFER_BYTES = 8_192

internal enum class UserObservedPriceProofContentReadIssue {
    UNSUPPORTED_URI,
    SOURCE_UNAVAILABLE,
    EMPTY_CONTENT,
    INPUT_TOO_LARGE,
    READ_FAILED
}

internal data class UserObservedPriceProofContentReadResult(
    val bytes: ByteArray?,
    val issue: UserObservedPriceProofContentReadIssue? = null
) {
    init {
        require((bytes != null) == (issue == null))
        require(bytes == null || bytes.isNotEmpty())
    }

    val accepted: Boolean
        get() = bytes != null
}

/**
 * Platform-neutral bounded stream reader used by Android content-URI proof import.
 *
 * The caller still owns artifact identity, proof type, timestamps, and all confirmation fields.
 * This boundary only copies transient user-selected bytes under the same hard limit enforced by
 * [UserProvidedPriceProofArtifact]. It never fingerprints, retains, interprets, or promotes them.
 */
internal object UserObservedPriceProofStreamReader {
    fun read(
        input: InputStream,
        maxBytes: Int = UserProvidedPriceProofArtifact.MAX_ARTIFACT_BYTES
    ): UserObservedPriceProofContentReadResult {
        require(maxBytes > 0)

        return try {
            input.use { stream ->
                val output =
                    ByteArrayOutputStream(
                        minOf(maxBytes, USER_OBSERVED_PRICE_PROOF_READ_BUFFER_BYTES)
                    )
                val buffer = ByteArray(USER_OBSERVED_PRICE_PROOF_READ_BUFFER_BYTES)
                var total = 0

                while (true) {
                    val read = stream.read(buffer)
                    if (read < 0) break
                    if (read == 0) {
                        val single = stream.read()
                        if (single < 0) break
                        if (total >= maxBytes) {
                            return UserObservedPriceProofContentReadResult(
                                bytes = null,
                                issue = UserObservedPriceProofContentReadIssue.INPUT_TOO_LARGE
                            )
                        }
                        output.write(single)
                        total += 1
                        continue
                    }
                    if (read > maxBytes - total) {
                        return UserObservedPriceProofContentReadResult(
                            bytes = null,
                            issue = UserObservedPriceProofContentReadIssue.INPUT_TOO_LARGE
                        )
                    }
                    output.write(buffer, 0, read)
                    total += read
                }

                if (total == 0) {
                    UserObservedPriceProofContentReadResult(
                        bytes = null,
                        issue = UserObservedPriceProofContentReadIssue.EMPTY_CONTENT
                    )
                } else {
                    UserObservedPriceProofContentReadResult(
                        bytes = output.toByteArray()
                    )
                }
            }
        } catch (_: Exception) {
            UserObservedPriceProofContentReadResult(
                bytes = null,
                issue = UserObservedPriceProofContentReadIssue.READ_FAILED
            )
        }
    }
}

/**
 * Android read-only adapter for one user-selected proof document/photo represented by a content URI.
 *
 * It intentionally accepts only content:// URIs and acquires no persistable URI permission. The
 * returned ByteArray is transient caller-owned input for the verified confirmation handoff/session;
 * this adapter stores no bytes and has no artifact, confirmation, evidence, ranking, or UI authority.
 */
internal class AndroidUserObservedPriceProofContentSource(
    private val contentResolver: ContentResolver
) {
    fun read(uri: Uri): UserObservedPriceProofContentReadResult {
        if (uri.scheme != ContentResolver.SCHEME_CONTENT) {
            return UserObservedPriceProofContentReadResult(
                bytes = null,
                issue = UserObservedPriceProofContentReadIssue.UNSUPPORTED_URI
            )
        }

        val input =
            try {
                contentResolver.openInputStream(uri)
            } catch (_: Exception) {
                null
            }
                ?: return UserObservedPriceProofContentReadResult(
                    bytes = null,
                    issue = UserObservedPriceProofContentReadIssue.SOURCE_UNAVAILABLE
                )

        return UserObservedPriceProofStreamReader.read(
            input = input,
            maxBytes = UserProvidedPriceProofArtifact.MAX_ARTIFACT_BYTES
        )
    }
}
