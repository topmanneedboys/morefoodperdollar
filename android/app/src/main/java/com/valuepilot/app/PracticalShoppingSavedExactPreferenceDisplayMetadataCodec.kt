package com.valuepilot.app

import com.valuepilot.core.PracticalShoppingStoreIdentityScope
import com.valuepilot.core.ProductionProductEvidenceKey
import com.valuepilot.core.ProductionProductKeyScope
import com.valuepilot.core.ShoppingItemKey
import com.valuepilot.core.ShoppingStoreKey
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

private const val SAVED_DISPLAY_METADATA_CODEC_MAGIC = "VALUEPILOT_SAVED_DISPLAY"
private const val SAVED_DISPLAY_METADATA_SCHEMA_VERSION = 1
private const val MAX_SAVED_DISPLAY_METADATA_ENCODED_BYTES = 262_144
private const val MAX_SAVED_DISPLAY_METADATA_RECORDS = 192
private const val MAX_SAVED_DISPLAY_METADATA_KEY_BYTES = 512
private const val MAX_SAVED_DISPLAY_METADATA_FIELD_BYTES = 4_096
private const val DISPLAY_PRODUCT_RECORD = "P"
private const val DISPLAY_STORE_RECORD = "S"
private const val DISPLAY_NULL_FIELD = "~"

enum class PracticalShoppingSavedExactPreferenceDisplayMetadataCodecIssue {
    FIELD_TOO_LARGE,
    OUTPUT_TOO_LARGE,
    INPUT_TOO_LARGE,
    TOO_MANY_RECORDS,
    INVALID_HEADER,
    UNSUPPORTED_SCHEMA_VERSION,
    MALFORMED_INPUT
}

data class PracticalShoppingSavedExactPreferenceDisplayMetadataEncodeResult(
    val bytes: ByteArray?,
    val issue: PracticalShoppingSavedExactPreferenceDisplayMetadataCodecIssue?
) {
    init {
        require((bytes != null) == (issue == null))
    }

    val accepted: Boolean
        get() = bytes != null
}

data class PracticalShoppingSavedExactPreferenceDisplayMetadataDecodeResult(
    val snapshot: PracticalShoppingSavedExactPreferenceDisplayMetadataSnapshot?,
    val issue: PracticalShoppingSavedExactPreferenceDisplayMetadataCodecIssue?
) {
    init {
        require((snapshot != null) == (issue == null))
    }

    val accepted: Boolean
        get() = snapshot != null
}

/**
 * Deterministic storage codec for identity-bound Saved presentation metadata.
 *
 * This is intentionally a separate document from the exact-preference persistence schema.
 * Product/store identity authority therefore remains in the exact-preference file. The
 * display file stores only the stable logical key, the exact presentation binding that was
 * already established, the human-facing name and its presentation basis.
 *
 * All text is UTF-8 encoded then hex-wrapped inside an ASCII line format. Input is bounded
 * before parsing and every decoded object is reconstructed through its validated domain
 * constructor. Stale-vs-current binding is deliberately not decided here; callers must run
 * [PracticalShoppingSavedExactPreferenceDisplayMetadataBinder] against current preference
 * state after decoding.
 */
object PracticalShoppingSavedExactPreferenceDisplayMetadataCodec {

    val currentSchemaVersion: Int
        get() = SAVED_DISPLAY_METADATA_SCHEMA_VERSION

    val maximumEncodedBytes: Int
        get() = MAX_SAVED_DISPLAY_METADATA_ENCODED_BYTES

    fun encode(
        snapshot: PracticalShoppingSavedExactPreferenceDisplayMetadataSnapshot
    ): PracticalShoppingSavedExactPreferenceDisplayMetadataEncodeResult {
        if (!fieldsFit(snapshot)) {
            return encodeFailure(
                PracticalShoppingSavedExactPreferenceDisplayMetadataCodecIssue.FIELD_TOO_LARGE
            )
        }

        val lines =
            ArrayList<String>(
                1 + snapshot.productEntries.size + snapshot.storeEntries.size
            )
        lines += "$SAVED_DISPLAY_METADATA_CODEC_MAGIC|$SAVED_DISPLAY_METADATA_SCHEMA_VERSION"

        snapshot.productEntries
            .sortedBy { it.itemKey.value }
            .forEach { entry ->
                lines +=
                    listOf(
                        DISPLAY_PRODUCT_RECORD,
                        encodeField(entry.itemKey.value),
                        encodeField(entry.productKey.value),
                        encodeField(entry.productKey.scope.name),
                        encodeField(entry.displayName),
                        encodeField(entry.basis.name)
                    ).joinToString("|")
            }

        snapshot.storeEntries
            .sortedBy { it.storeKey.value }
            .forEach { entry ->
                lines +=
                    listOf(
                        DISPLAY_STORE_RECORD,
                        encodeField(entry.storeKey.value),
                        encodeField(entry.scope.merchantKey),
                        encodeField(entry.scope.locationKey),
                        encodeField(entry.scope.commerceChannelKey),
                        encodeField(entry.displayName),
                        encodeField(entry.basis.name)
                    ).joinToString("|")
            }

        val bytes = lines.joinToString("\n").toByteArray(Charsets.US_ASCII)
        if (bytes.size > MAX_SAVED_DISPLAY_METADATA_ENCODED_BYTES) {
            return encodeFailure(
                PracticalShoppingSavedExactPreferenceDisplayMetadataCodecIssue.OUTPUT_TOO_LARGE
            )
        }

        return PracticalShoppingSavedExactPreferenceDisplayMetadataEncodeResult(
            bytes = bytes,
            issue = null
        )
    }

    fun decode(
        bytes: ByteArray
    ): PracticalShoppingSavedExactPreferenceDisplayMetadataDecodeResult {
        if (bytes.size > MAX_SAVED_DISPLAY_METADATA_ENCODED_BYTES) {
            return decodeFailure(
                PracticalShoppingSavedExactPreferenceDisplayMetadataCodecIssue.INPUT_TOO_LARGE
            )
        }
        if (bytes.any { byte -> (byte.toInt() and 0xff) > 0x7f }) {
            return decodeFailure(
                PracticalShoppingSavedExactPreferenceDisplayMetadataCodecIssue.MALFORMED_INPUT
            )
        }

        val lines = String(bytes, Charsets.US_ASCII).split('\n')
        if (lines.isEmpty() || lines.first().isEmpty()) {
            return decodeFailure(
                PracticalShoppingSavedExactPreferenceDisplayMetadataCodecIssue.INVALID_HEADER
            )
        }
        if (lines.size - 1 > MAX_SAVED_DISPLAY_METADATA_RECORDS) {
            return decodeFailure(
                PracticalShoppingSavedExactPreferenceDisplayMetadataCodecIssue.TOO_MANY_RECORDS
            )
        }

        val header = lines.first().split('|')
        if (header.size != 2 || header[0] != SAVED_DISPLAY_METADATA_CODEC_MAGIC) {
            return decodeFailure(
                PracticalShoppingSavedExactPreferenceDisplayMetadataCodecIssue.INVALID_HEADER
            )
        }
        val schemaVersion = header[1].toIntOrNull()
            ?: return decodeFailure(
                PracticalShoppingSavedExactPreferenceDisplayMetadataCodecIssue.INVALID_HEADER
            )
        if (schemaVersion != SAVED_DISPLAY_METADATA_SCHEMA_VERSION) {
            return decodeFailure(
                PracticalShoppingSavedExactPreferenceDisplayMetadataCodecIssue.UNSUPPORTED_SCHEMA_VERSION
            )
        }

        val products = mutableListOf<PracticalShoppingSavedProductDisplayMetadataEntry>()
        val stores = mutableListOf<PracticalShoppingSavedStoreDisplayMetadataEntry>()

        try {
            lines.drop(1).forEach { line ->
                if (line.isEmpty()) {
                    throw DisplayCodecParseFailure()
                }
                val parts = line.split('|')
                when (parts.firstOrNull()) {
                    DISPLAY_PRODUCT_RECORD -> {
                        if (parts.size != 6) throw DisplayCodecParseFailure()
                        products += decodeProduct(parts)
                    }
                    DISPLAY_STORE_RECORD -> {
                        if (parts.size != 7) throw DisplayCodecParseFailure()
                        stores += decodeStore(parts)
                    }
                    else -> throw DisplayCodecParseFailure()
                }
            }

            return PracticalShoppingSavedExactPreferenceDisplayMetadataDecodeResult(
                snapshot =
                    PracticalShoppingSavedExactPreferenceDisplayMetadataSnapshot(
                        productEntries = products,
                        storeEntries = stores
                    ),
                issue = null
            )
        } catch (_: DisplayCodecParseFailure) {
            return decodeFailure(
                PracticalShoppingSavedExactPreferenceDisplayMetadataCodecIssue.MALFORMED_INPUT
            )
        } catch (_: IllegalArgumentException) {
            return decodeFailure(
                PracticalShoppingSavedExactPreferenceDisplayMetadataCodecIssue.MALFORMED_INPUT
            )
        }
    }

    private fun decodeProduct(
        parts: List<String>
    ): PracticalShoppingSavedProductDisplayMetadataEntry {
        val itemKey = ShoppingItemKey(decodeRequired(parts[1], MAX_SAVED_DISPLAY_METADATA_KEY_BYTES))
        val productKey =
            ProductionProductEvidenceKey(
                value = decodeRequired(parts[2]),
                scope = enumValueOfRequired(parts[3])
            )
        return PracticalShoppingSavedProductDisplayMetadataEntry(
            itemKey = itemKey,
            productKey = productKey,
            displayName = decodeRequired(parts[4]),
            basis = enumValueOfRequired(parts[5])
        )
    }

    private fun decodeStore(
        parts: List<String>
    ): PracticalShoppingSavedStoreDisplayMetadataEntry {
        val storeKey = ShoppingStoreKey(decodeRequired(parts[1], MAX_SAVED_DISPLAY_METADATA_KEY_BYTES))
        val scope =
            PracticalShoppingStoreIdentityScope(
                merchantKey = decodeRequired(parts[2]),
                locationKey = decodeNullable(parts[3]),
                commerceChannelKey = decodeRequired(parts[4])
            )
        return PracticalShoppingSavedStoreDisplayMetadataEntry(
            storeKey = storeKey,
            scope = scope,
            displayName = decodeRequired(parts[5]),
            basis = enumValueOfRequired(parts[6])
        )
    }

    private inline fun <reified T : Enum<T>> enumValueOfRequired(token: String): T {
        val value = decodeRequired(token)
        return try {
            enumValueOf<T>(value)
        } catch (_: IllegalArgumentException) {
            throw DisplayCodecParseFailure()
        }
    }

    private fun fieldsFit(
        snapshot: PracticalShoppingSavedExactPreferenceDisplayMetadataSnapshot
    ): Boolean {
        fun keyFits(value: String): Boolean =
            value.toByteArray(Charsets.UTF_8).size <= MAX_SAVED_DISPLAY_METADATA_KEY_BYTES

        fun fieldFits(value: String?): Boolean =
            value == null ||
                value.toByteArray(Charsets.UTF_8).size <= MAX_SAVED_DISPLAY_METADATA_FIELD_BYTES

        return snapshot.productEntries.all { entry ->
            keyFits(entry.itemKey.value) &&
                fieldFits(entry.productKey.value) &&
                fieldFits(entry.productKey.scope.name) &&
                fieldFits(entry.displayName) &&
                fieldFits(entry.basis.name)
        } && snapshot.storeEntries.all { entry ->
            keyFits(entry.storeKey.value) &&
                fieldFits(entry.scope.merchantKey) &&
                fieldFits(entry.scope.locationKey) &&
                fieldFits(entry.scope.commerceChannelKey) &&
                fieldFits(entry.displayName) &&
                fieldFits(entry.basis.name)
        }
    }

    private fun encodeField(value: String?): String {
        if (value == null) return DISPLAY_NULL_FIELD
        val bytes = value.toByteArray(Charsets.UTF_8)
        val output = StringBuilder(bytes.size * 2)
        bytes.forEach { byte ->
            val unsigned = byte.toInt() and 0xff
            output.append(HEX[unsigned ushr 4])
            output.append(HEX[unsigned and 0x0f])
        }
        return output.toString()
    }

    private fun decodeRequired(
        token: String,
        maxBytes: Int = MAX_SAVED_DISPLAY_METADATA_FIELD_BYTES
    ): String {
        if (token == DISPLAY_NULL_FIELD) throw DisplayCodecParseFailure()
        return decodeHex(token, maxBytes)
    }

    private fun decodeNullable(
        token: String,
        maxBytes: Int = MAX_SAVED_DISPLAY_METADATA_FIELD_BYTES
    ): String? =
        if (token == DISPLAY_NULL_FIELD) null else decodeHex(token, maxBytes)

    private fun decodeHex(token: String, maxBytes: Int): String {
        if (token.length % 2 != 0 || token.length > maxBytes * 2) {
            throw DisplayCodecParseFailure()
        }
        val bytes = ByteArray(token.length / 2)
        var index = 0
        while (index < token.length) {
            val high = Character.digit(token[index], 16)
            val low = Character.digit(token[index + 1], 16)
            if (high < 0 || low < 0) throw DisplayCodecParseFailure()
            bytes[index / 2] = ((high shl 4) or low).toByte()
            index += 2
        }
        return try {
            Charsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (_: Exception) {
            throw DisplayCodecParseFailure()
        }
    }

    private fun encodeFailure(
        issue: PracticalShoppingSavedExactPreferenceDisplayMetadataCodecIssue
    ): PracticalShoppingSavedExactPreferenceDisplayMetadataEncodeResult =
        PracticalShoppingSavedExactPreferenceDisplayMetadataEncodeResult(
            bytes = null,
            issue = issue
        )

    private fun decodeFailure(
        issue: PracticalShoppingSavedExactPreferenceDisplayMetadataCodecIssue
    ): PracticalShoppingSavedExactPreferenceDisplayMetadataDecodeResult =
        PracticalShoppingSavedExactPreferenceDisplayMetadataDecodeResult(
            snapshot = null,
            issue = issue
        )

    private class DisplayCodecParseFailure : RuntimeException()

    private val HEX = "0123456789abcdef".toCharArray()
}
