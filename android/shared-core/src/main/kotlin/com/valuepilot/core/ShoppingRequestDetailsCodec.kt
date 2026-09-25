package com.valuepilot.core

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

private const val SHOPPING_REQUEST_DETAILS_CODEC_MAGIC = "VALUEPILOT_SHOPPING_REQUEST_DETAILS"
private const val SHOPPING_REQUEST_DETAILS_CODEC_SCHEMA = 1
private const val MAX_SHOPPING_REQUEST_DETAILS_ENCODED_BYTES = 524_288
private const val MAX_SHOPPING_REQUEST_DETAILS_ITEMS = 128
private const val MAX_SHOPPING_REQUEST_DETAILS_ITEM_KEY_BYTES = 512
private const val MAX_SHOPPING_REQUEST_DETAILS_FIELD_BYTES = 1_024
private const val ITEM_RECORD = "I"
private const val DETAIL_PRESENT = "1"
private const val DETAIL_ABSENT = "0"
private const val NULL_FIELD = "~"

/** Fail-closed errors owned only by the request-details bytes boundary. */
enum class ShoppingRequestDetailsCodecIssue {
    FIELD_TOO_LARGE,
    OUTPUT_TOO_LARGE,
    INPUT_TOO_LARGE,
    TOO_MANY_ITEMS,
    INVALID_HEADER,
    MALFORMED_INPUT
}

data class ShoppingRequestDetailsEncodeResult(
    val bytes: ByteArray?,
    val issue: ShoppingRequestDetailsCodecIssue?
) {
    init {
        require((bytes != null) == (issue == null))
    }

    val accepted: Boolean
        get() = bytes != null
}

data class ShoppingRequestDetailsDecodeResult(
    val details: ShoppingRequestDetails?,
    val issue: ShoppingRequestDetailsCodecIssue?
) {
    init {
        require((details != null) == (issue == null))
    }

    val accepted: Boolean
        get() = details != null
}

/**
 * Deterministic, bounded transport/persistence codec for explicit shopper intent.
 *
 * This codec does not interpret quantity, infer package arithmetic, resolve products,
 * rank stores, attach price evidence, or authorize network/UI behavior. It only
 * preserves the already-validated [ShoppingRequestDetails] value across an opaque
 * byte boundary.
 *
 * The outer format is ASCII. User-controlled item/brand identifiers are UTF-8 hex,
 * so delimiters cannot become data. Decode rejects oversized input before parsing,
 * rejects excess item records before field allocation, and reconstructs the public
 * domain types so their existing validation remains authoritative.
 */
object ShoppingRequestDetailsCodec {

    val maximumEncodedBytes: Int
        get() = MAX_SHOPPING_REQUEST_DETAILS_ENCODED_BYTES

    fun encode(details: ShoppingRequestDetails): ShoppingRequestDetailsEncodeResult {
        if (details.request.itemKeys.size > MAX_SHOPPING_REQUEST_DETAILS_ITEMS) {
            return failureEncode(ShoppingRequestDetailsCodecIssue.TOO_MANY_ITEMS)
        }

        val lines = ArrayList<String>(details.request.itemKeys.size + 1)
        lines += "$SHOPPING_REQUEST_DETAILS_CODEC_MAGIC|$SHOPPING_REQUEST_DETAILS_CODEC_SCHEMA"

        for (itemKey in details.request.itemKeys) {
            if (!fits(itemKey.value, MAX_SHOPPING_REQUEST_DETAILS_ITEM_KEY_BYTES)) {
                return failureEncode(ShoppingRequestDetailsCodecIssue.FIELD_TOO_LARGE)
            }

            val detail = details.detailFor(itemKey)
            if (detail == null) {
                lines +=
                    listOf(
                        ITEM_RECORD,
                        encodeField(itemKey.value),
                        DETAIL_ABSENT,
                        NULL_FIELD,
                        NULL_FIELD,
                        NULL_FIELD,
                        NULL_FIELD,
                        NULL_FIELD,
                        NULL_FIELD,
                        NULL_FIELD,
                        NULL_FIELD
                    ).joinToString("|")
                continue
            }

            val brandKey = detail.brandPreference.exactBrandKey?.value
            if (!fitsNullable(brandKey, MAX_SHOPPING_REQUEST_DETAILS_FIELD_BYTES)) {
                return failureEncode(ShoppingRequestDetailsCodecIssue.FIELD_TOO_LARGE)
            }

            val quantity = detail.requestedQuantity
            lines +=
                listOf(
                    ITEM_RECORD,
                    encodeField(itemKey.value),
                    DETAIL_PRESENT,
                    detail.productSpecificity.name,
                    encodeLong(quantity?.totalQuantity?.amountMicros),
                    quantity?.totalQuantity?.unit?.name ?: NULL_FIELD,
                    encodeLong(quantity?.packageCount),
                    encodeLong(quantity?.preferredPackageQuantity?.amountMicros),
                    quantity?.preferredPackageQuantity?.unit?.name ?: NULL_FIELD,
                    detail.brandPreference.flexibility.name,
                    encodeFieldNullable(brandKey)
                ).joinToString("|")
        }

        val bytes = lines.joinToString("\n").toByteArray(Charsets.US_ASCII)
        if (bytes.size > MAX_SHOPPING_REQUEST_DETAILS_ENCODED_BYTES) {
            return failureEncode(ShoppingRequestDetailsCodecIssue.OUTPUT_TOO_LARGE)
        }

        return ShoppingRequestDetailsEncodeResult(bytes = bytes, issue = null)
    }

    fun decode(bytes: ByteArray): ShoppingRequestDetailsDecodeResult {
        if (bytes.size > MAX_SHOPPING_REQUEST_DETAILS_ENCODED_BYTES) {
            return failureDecode(ShoppingRequestDetailsCodecIssue.INPUT_TOO_LARGE)
        }
        if (bytes.any { (it.toInt() and 0xff) > 0x7f }) {
            return failureDecode(ShoppingRequestDetailsCodecIssue.MALFORMED_INPUT)
        }

        val lines = String(bytes, Charsets.US_ASCII).split('\n')
        if (lines.isEmpty() || lines.first().isEmpty()) {
            return failureDecode(ShoppingRequestDetailsCodecIssue.INVALID_HEADER)
        }
        if (lines.size - 1 > MAX_SHOPPING_REQUEST_DETAILS_ITEMS) {
            return failureDecode(ShoppingRequestDetailsCodecIssue.TOO_MANY_ITEMS)
        }

        val header = lines.first().split('|')
        if (
            header.size != 2 ||
            header[0] != SHOPPING_REQUEST_DETAILS_CODEC_MAGIC ||
            header[1].toIntOrNull() != SHOPPING_REQUEST_DETAILS_CODEC_SCHEMA
        ) {
            return failureDecode(ShoppingRequestDetailsCodecIssue.INVALID_HEADER)
        }

        val itemKeys = ArrayList<ShoppingItemKey>(lines.size - 1)
        val itemDetails = ArrayList<ShoppingItemRequestDetail>(lines.size - 1)

        try {
            for (line in lines.drop(1)) {
                if (line.isEmpty()) throw CodecParseFailure()
                val parts = line.split('|')
                if (parts.size != 11 || parts[0] != ITEM_RECORD) {
                    throw CodecParseFailure()
                }

                val itemKey =
                    ShoppingItemKey(
                        decodeRequired(parts[1], MAX_SHOPPING_REQUEST_DETAILS_ITEM_KEY_BYTES)
                    )
                itemKeys += itemKey

                when (parts[2]) {
                    DETAIL_ABSENT -> {
                        if (parts.subList(3, 11).any { it != NULL_FIELD }) {
                            throw CodecParseFailure()
                        }
                    }

                    DETAIL_PRESENT -> itemDetails += decodeDetail(itemKey, parts)
                    else -> throw CodecParseFailure()
                }
            }

            return ShoppingRequestDetailsDecodeResult(
                details =
                    ShoppingRequestDetails(
                        request = ShoppingRequest(itemKeys),
                        itemDetails = itemDetails
                    ),
                issue = null
            )
        } catch (_: CodecParseFailure) {
            return failureDecode(ShoppingRequestDetailsCodecIssue.MALFORMED_INPUT)
        } catch (_: IllegalArgumentException) {
            return failureDecode(ShoppingRequestDetailsCodecIssue.MALFORMED_INPUT)
        }
    }

    private fun decodeDetail(
        itemKey: ShoppingItemKey,
        parts: List<String>
    ): ShoppingItemRequestDetail {
        val specificity = enumValue<ShoppingProductSpecificity>(parts[3])
        val totalQuantity = decodeQuantityPair(parts[4], parts[5])
        val packageCount = decodeLongNullable(parts[6])
        val preferredPackageQuantity = decodeQuantityPair(parts[7], parts[8])
        val requestedQuantity =
            if (totalQuantity == null && packageCount == null && preferredPackageQuantity == null) {
                null
            } else {
                ShoppingRequestedQuantity(
                    totalQuantity = totalQuantity,
                    packageCount = packageCount,
                    preferredPackageQuantity = preferredPackageQuantity
                )
            }

        val brandFlexibility = enumValue<ShoppingBrandFlexibility>(parts[9])
        val brandKey =
            decodeNullable(parts[10], MAX_SHOPPING_REQUEST_DETAILS_FIELD_BYTES)
                ?.let(::ShoppingBrandKey)

        return ShoppingItemRequestDetail(
            itemKey = itemKey,
            productSpecificity = specificity,
            requestedQuantity = requestedQuantity,
            brandPreference =
                ShoppingBrandPreference(
                    flexibility = brandFlexibility,
                    exactBrandKey = brandKey
                )
        )
    }

    private fun decodeQuantityPair(
        amountToken: String,
        unitToken: String
    ): NormalizedQuantity? {
        val amount = decodeLongNullable(amountToken)
        val unit =
            if (unitToken == NULL_FIELD) {
                null
            } else {
                enumValue<BaseUnit>(unitToken)
            }

        if ((amount == null) != (unit == null)) {
            throw CodecParseFailure()
        }
        return if (amount == null) null else NormalizedQuantity(amount, requireNotNull(unit))
    }

    private inline fun <reified T : Enum<T>> enumValue(token: String): T {
        if (token == NULL_FIELD) throw CodecParseFailure()
        return enumValues<T>().firstOrNull { it.name == token } ?: throw CodecParseFailure()
    }

    private fun encodeLong(value: Long?): String = value?.toString() ?: NULL_FIELD

    private fun decodeLongNullable(token: String): Long? {
        if (token == NULL_FIELD) return null
        return token.toLongOrNull() ?: throw CodecParseFailure()
    }

    private fun fits(value: String, maxBytes: Int): Boolean =
        value.toByteArray(Charsets.UTF_8).size <= maxBytes

    private fun fitsNullable(value: String?, maxBytes: Int): Boolean =
        value == null || fits(value, maxBytes)

    private fun encodeField(value: String): String {
        val bytes = value.toByteArray(Charsets.UTF_8)
        val output = StringBuilder(bytes.size * 2)
        bytes.forEach { byte ->
            val valueByte = byte.toInt() and 0xff
            output.append(HEX[valueByte ushr 4])
            output.append(HEX[valueByte and 0x0f])
        }
        return output.toString()
    }

    private fun encodeFieldNullable(value: String?): String =
        if (value == null) NULL_FIELD else encodeField(value)

    private fun decodeRequired(token: String, maxBytes: Int): String {
        if (token == NULL_FIELD) throw CodecParseFailure()
        return decodeHex(token, maxBytes)
    }

    private fun decodeNullable(token: String, maxBytes: Int): String? =
        if (token == NULL_FIELD) null else decodeHex(token, maxBytes)

    private fun decodeHex(token: String, maxBytes: Int): String {
        if (token.length % 2 != 0 || token.length > maxBytes * 2) {
            throw CodecParseFailure()
        }

        val bytes = ByteArray(token.length / 2)
        var index = 0
        while (index < token.length) {
            val high = Character.digit(token[index], 16)
            val low = Character.digit(token[index + 1], 16)
            if (high < 0 || low < 0) throw CodecParseFailure()
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

    private fun failureEncode(issue: ShoppingRequestDetailsCodecIssue) =
        ShoppingRequestDetailsEncodeResult(bytes = null, issue = issue)

    private fun failureDecode(issue: ShoppingRequestDetailsCodecIssue) =
        ShoppingRequestDetailsDecodeResult(details = null, issue = issue)

    private class CodecParseFailure : RuntimeException()

    private val HEX = "0123456789abcdef".toCharArray()
}
