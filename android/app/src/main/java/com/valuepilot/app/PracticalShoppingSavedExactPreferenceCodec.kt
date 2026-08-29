package com.valuepilot.app

import com.valuepilot.core.EvidenceDatasetNamespace
import com.valuepilot.core.EvidenceProviderId
import com.valuepilot.core.EvidenceStorageBoundary
import com.valuepilot.core.PracticalShoppingStoreIdentityScope
import com.valuepilot.core.ShoppingItemKey
import com.valuepilot.core.ShoppingStoreKey
import com.valuepilot.core.SourceProductIdentity
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

private const val SAVED_EXACT_PREFERENCE_CODEC_MAGIC = "VALUEPILOT_SAVED_EXACT"
private const val MAX_SAVED_EXACT_PREFERENCE_ENCODED_BYTES = 524_288
private const val MAX_SAVED_EXACT_PREFERENCE_RECORDS = 192
private const val MAX_SAVED_EXACT_PREFERENCE_KEY_BYTES = 512
private const val MAX_SAVED_EXACT_PREFERENCE_FIELD_BYTES = 4_096
private const val NULL_FIELD = "~"
private const val PRODUCT_RECORD = "P"
private const val STORE_RECORD = "S"

/** Failures owned by the bytes <-> saved-exact-preference codec only. */
enum class PracticalShoppingSavedExactPreferenceCodecIssue {
    FIELD_TOO_LARGE,
    OUTPUT_TOO_LARGE,
    INPUT_TOO_LARGE,
    TOO_MANY_RECORDS,
    INVALID_HEADER,
    MALFORMED_INPUT
}

data class PracticalShoppingSavedExactPreferenceEncodeResult(
    val bytes: ByteArray?,
    val issue: PracticalShoppingSavedExactPreferenceCodecIssue?
) {
    init {
        require((bytes != null) == (issue == null))
    }

    val accepted: Boolean
        get() = bytes != null
}

data class PracticalShoppingSavedExactPreferenceDecodeResult(
    val state: PracticalShoppingSavedExactPreferenceState?,
    val codecIssue: PracticalShoppingSavedExactPreferenceCodecIssue? = null,
    val documentIssues: Set<PracticalShoppingSavedExactPreferenceLoadIssue> = emptySet()
) {
    init {
        val failed = codecIssue != null || documentIssues.isNotEmpty()
        require((state == null) == failed)
        require(codecIssue == null || documentIssues.isEmpty())
    }

    val accepted: Boolean
        get() = state != null
}

/**
 * Deterministic schema-1 codec for locally saved exact preferences.
 *
 * The outer representation is ASCII-only. Every user/provider/source string is UTF-8
 * encoded and then hexadecimal, so separators and newlines can never be confused with
 * field content. Null is the dedicated `~` token and is distinct from every hex field.
 *
 * Decoding is bounded before record parsing and before field allocation. The parsed
 * document is then passed through [PracticalShoppingSavedExactPreferenceStateManager]
 * so schema, duplicate-key, capacity and product-identity validation remain centralized.
 */
object PracticalShoppingSavedExactPreferenceCodec {

    val maximumEncodedBytes: Int
        get() = MAX_SAVED_EXACT_PREFERENCE_ENCODED_BYTES

    fun encode(
        state: PracticalShoppingSavedExactPreferenceState
    ): PracticalShoppingSavedExactPreferenceEncodeResult {
        val document = PracticalShoppingSavedExactPreferenceStateManager.document(state)

        if (!fieldsFit(document)) {
            return PracticalShoppingSavedExactPreferenceEncodeResult(
                bytes = null,
                issue = PracticalShoppingSavedExactPreferenceCodecIssue.FIELD_TOO_LARGE
            )
        }

        val lines = ArrayList<String>(1 + document.productPreferences.size + document.storePreferences.size)
        lines += "$SAVED_EXACT_PREFERENCE_CODEC_MAGIC|${document.schemaVersion}"

        document.productPreferences.forEach { preference ->
            val dataset = preference.dataset
            lines +=
                listOf(
                    PRODUCT_RECORD,
                    encodeField(preference.itemKey.value),
                    encodeField(preference.providerId.value),
                    encodeField(preference.sourceIdentity.providerItemId),
                    encodeField(preference.sourceIdentity.sku),
                    encodeField(preference.sourceIdentity.gtin),
                    encodeField(dataset?.id),
                    encodeField(dataset?.displayName),
                    encodeField(dataset?.licenseId),
                    encodeField(dataset?.storageBoundary?.name)
                ).joinToString("|")
        }

        document.storePreferences.forEach { preference ->
            val dataset = preference.dataset
            lines +=
                listOf(
                    STORE_RECORD,
                    encodeField(preference.storeKey.value),
                    encodeField(preference.scope.merchantKey),
                    encodeField(preference.scope.locationKey),
                    encodeField(preference.scope.commerceChannelKey),
                    encodeField(preference.providerId?.value),
                    encodeField(dataset?.id),
                    encodeField(dataset?.displayName),
                    encodeField(dataset?.licenseId),
                    encodeField(dataset?.storageBoundary?.name)
                ).joinToString("|")
        }

        val bytes = lines.joinToString("\n").toByteArray(Charsets.US_ASCII)
        if (bytes.size > MAX_SAVED_EXACT_PREFERENCE_ENCODED_BYTES) {
            return PracticalShoppingSavedExactPreferenceEncodeResult(
                bytes = null,
                issue = PracticalShoppingSavedExactPreferenceCodecIssue.OUTPUT_TOO_LARGE
            )
        }

        return PracticalShoppingSavedExactPreferenceEncodeResult(
            bytes = bytes,
            issue = null
        )
    }

    fun decode(
        bytes: ByteArray
    ): PracticalShoppingSavedExactPreferenceDecodeResult {
        if (bytes.size > MAX_SAVED_EXACT_PREFERENCE_ENCODED_BYTES) {
            return codecFailure(PracticalShoppingSavedExactPreferenceCodecIssue.INPUT_TOO_LARGE)
        }
        if (bytes.any { (it.toInt() and 0xff) > 0x7f }) {
            return codecFailure(PracticalShoppingSavedExactPreferenceCodecIssue.MALFORMED_INPUT)
        }

        val encoded = String(bytes, Charsets.US_ASCII)
        val lines = encoded.split('\n')
        if (lines.isEmpty() || lines.first().isEmpty()) {
            return codecFailure(PracticalShoppingSavedExactPreferenceCodecIssue.INVALID_HEADER)
        }
        if (lines.size - 1 > MAX_SAVED_EXACT_PREFERENCE_RECORDS) {
            return codecFailure(PracticalShoppingSavedExactPreferenceCodecIssue.TOO_MANY_RECORDS)
        }

        val header = lines.first().split('|')
        if (header.size != 2 || header[0] != SAVED_EXACT_PREFERENCE_CODEC_MAGIC) {
            return codecFailure(PracticalShoppingSavedExactPreferenceCodecIssue.INVALID_HEADER)
        }
        val schemaVersion = header[1].toIntOrNull()
            ?: return codecFailure(PracticalShoppingSavedExactPreferenceCodecIssue.INVALID_HEADER)

        val products = mutableListOf<PracticalShoppingSavedExactProductPreference>()
        val stores = mutableListOf<PracticalShoppingSavedExactStorePreference>()

        try {
            lines.drop(1).forEach { line ->
                if (line.isEmpty()) {
                    throw CodecParseFailure()
                }
                val parts = line.split('|')
                if (parts.size != 10) {
                    throw CodecParseFailure()
                }
                when (parts[0]) {
                    PRODUCT_RECORD -> products += decodeProduct(parts)
                    STORE_RECORD -> stores += decodeStore(parts)
                    else -> throw CodecParseFailure()
                }
            }
        } catch (_: CodecParseFailure) {
            return codecFailure(PracticalShoppingSavedExactPreferenceCodecIssue.MALFORMED_INPUT)
        } catch (_: IllegalArgumentException) {
            return codecFailure(PracticalShoppingSavedExactPreferenceCodecIssue.MALFORMED_INPUT)
        }

        val loaded =
            PracticalShoppingSavedExactPreferenceStateManager.load(
                PracticalShoppingSavedExactPreferenceDocument(
                    schemaVersion = schemaVersion,
                    productPreferences = products,
                    storePreferences = stores
                )
            )

        return if (loaded.accepted) {
            PracticalShoppingSavedExactPreferenceDecodeResult(
                state = requireNotNull(loaded.state)
            )
        } else {
            PracticalShoppingSavedExactPreferenceDecodeResult(
                state = null,
                documentIssues = loaded.issues
            )
        }
    }

    private fun fieldsFit(
        document: PracticalShoppingSavedExactPreferenceDocument
    ): Boolean {
        fun keyFits(value: String): Boolean =
            value.toByteArray(Charsets.UTF_8).size <= MAX_SAVED_EXACT_PREFERENCE_KEY_BYTES

        fun fieldFits(value: String?): Boolean =
            value == null ||
                value.toByteArray(Charsets.UTF_8).size <= MAX_SAVED_EXACT_PREFERENCE_FIELD_BYTES

        return document.productPreferences.all { preference ->
            keyFits(preference.itemKey.value) &&
                fieldFits(preference.providerId.value) &&
                fieldFits(preference.sourceIdentity.providerItemId) &&
                fieldFits(preference.sourceIdentity.sku) &&
                fieldFits(preference.sourceIdentity.gtin) &&
                fieldFits(preference.dataset?.id) &&
                fieldFits(preference.dataset?.displayName) &&
                fieldFits(preference.dataset?.licenseId) &&
                fieldFits(preference.dataset?.storageBoundary?.name)
        } &&
            document.storePreferences.all { preference ->
                keyFits(preference.storeKey.value) &&
                    fieldFits(preference.scope.merchantKey) &&
                    fieldFits(preference.scope.locationKey) &&
                    fieldFits(preference.scope.commerceChannelKey) &&
                    fieldFits(preference.providerId?.value) &&
                    fieldFits(preference.dataset?.id) &&
                    fieldFits(preference.dataset?.displayName) &&
                    fieldFits(preference.dataset?.licenseId) &&
                    fieldFits(preference.dataset?.storageBoundary?.name)
            }
    }

    private fun decodeProduct(
        parts: List<String>
    ): PracticalShoppingSavedExactProductPreference {
        val itemKey = ShoppingItemKey(decodeRequired(parts[1], MAX_SAVED_EXACT_PREFERENCE_KEY_BYTES))
        val providerId = EvidenceProviderId(decodeRequired(parts[2]))
        val sourceIdentity =
            SourceProductIdentity(
                providerItemId = decodeNullable(parts[3]),
                sku = decodeNullable(parts[4]),
                gtin = decodeNullable(parts[5])
            )
        val dataset = decodeDataset(parts, 6)

        return PracticalShoppingSavedExactProductPreference(
            itemKey = itemKey,
            providerId = providerId,
            sourceIdentity = sourceIdentity,
            dataset = dataset
        )
    }

    private fun decodeStore(
        parts: List<String>
    ): PracticalShoppingSavedExactStorePreference {
        val storeKey = ShoppingStoreKey(decodeRequired(parts[1], MAX_SAVED_EXACT_PREFERENCE_KEY_BYTES))
        val scope =
            PracticalShoppingStoreIdentityScope(
                merchantKey = decodeRequired(parts[2]),
                locationKey = decodeNullable(parts[3]),
                commerceChannelKey = decodeRequired(parts[4])
            )
        val providerId = decodeNullable(parts[5])?.let(::EvidenceProviderId)
        val dataset = decodeDataset(parts, 6)

        return PracticalShoppingSavedExactStorePreference(
            storeKey = storeKey,
            scope = scope,
            providerId = providerId,
            dataset = dataset
        )
    }

    private fun decodeDataset(
        parts: List<String>,
        offset: Int
    ): EvidenceDatasetNamespace? {
        val id = decodeNullable(parts[offset])
        val displayName = decodeNullable(parts[offset + 1])
        val licenseId = decodeNullable(parts[offset + 2])
        val storageBoundaryName = decodeNullable(parts[offset + 3])
        val values = listOf(id, displayName, licenseId, storageBoundaryName)

        if (values.all { it == null }) {
            return null
        }
        if (values.any { it == null }) {
            throw CodecParseFailure()
        }

        val storageBoundary =
            try {
                EvidenceStorageBoundary.valueOf(requireNotNull(storageBoundaryName))
            } catch (_: IllegalArgumentException) {
                throw CodecParseFailure()
            }

        return EvidenceDatasetNamespace(
            id = requireNotNull(id),
            displayName = requireNotNull(displayName),
            licenseId = requireNotNull(licenseId),
            storageBoundary = storageBoundary
        )
    }

    private fun encodeField(value: String?): String {
        if (value == null) {
            return NULL_FIELD
        }
        val bytes = value.toByteArray(Charsets.UTF_8)
        val output = StringBuilder(bytes.size * 2)
        bytes.forEach { byte ->
            val valueByte = byte.toInt() and 0xff
            output.append(HEX[valueByte ushr 4])
            output.append(HEX[valueByte and 0x0f])
        }
        return output.toString()
    }

    private fun decodeRequired(
        token: String,
        maxBytes: Int = MAX_SAVED_EXACT_PREFERENCE_FIELD_BYTES
    ): String {
        if (token == NULL_FIELD) {
            throw CodecParseFailure()
        }
        return decodeHex(token, maxBytes)
    }

    private fun decodeNullable(
        token: String,
        maxBytes: Int = MAX_SAVED_EXACT_PREFERENCE_FIELD_BYTES
    ): String? =
        if (token == NULL_FIELD) {
            null
        } else {
            decodeHex(token, maxBytes)
        }

    private fun decodeHex(
        token: String,
        maxBytes: Int
    ): String {
        if (token.length % 2 != 0 || token.length > maxBytes * 2) {
            throw CodecParseFailure()
        }

        val bytes = ByteArray(token.length / 2)
        var index = 0
        while (index < token.length) {
            val high = Character.digit(token[index], 16)
            val low = Character.digit(token[index + 1], 16)
            if (high < 0 || low < 0) {
                throw CodecParseFailure()
            }
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
            throw CodecParseFailure()
        }
    }

    private fun codecFailure(
        issue: PracticalShoppingSavedExactPreferenceCodecIssue
    ): PracticalShoppingSavedExactPreferenceDecodeResult =
        PracticalShoppingSavedExactPreferenceDecodeResult(
            state = null,
            codecIssue = issue
        )

    private class CodecParseFailure : RuntimeException()

    private val HEX = "0123456789abcdef".toCharArray()
}
