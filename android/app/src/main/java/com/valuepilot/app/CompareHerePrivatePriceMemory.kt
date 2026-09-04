package com.valuepilot.app

import com.valuepilot.core.CompareHerePriceSelection
import com.valuepilot.core.CompareHereExactCandidate
import com.valuepilot.core.BaseUnit
import com.valuepilot.core.Money
import com.valuepilot.core.NormalizedQuantity
import com.valuepilot.core.UnitRate
import com.valuepilot.core.RateUnit
import java.security.MessageDigest

internal const val MAX_COMPARE_HERE_PRIVATE_MEMORY_CAPTURE = 32
internal const val MAX_COMPARE_HERE_PRIVATE_MEMORY_ENTRIES = 256
private const val MAX_MEMORY_DISPLAY_NAME_BYTES = 640
private const val MAX_MEMORY_PROMOTION_LABEL_BYTES = 640
private const val MAX_COMPARE_HERE_PRIVATE_MEMORY_ENCODED_BYTES = 262_144
private const val MEMORY_CODEC_MAGIC = "VALUEPILOT_COMPARE_MEMORY"
private const val MEMORY_RECORD = "M"
private const val NULL_FIELD = "~"

/** The only source currently allowed to create an automatic private comparison memory entry. */
internal enum class CompareHerePrivatePriceMemorySource {
    CONFIRMED_COMPARE_HERE
}

/**
 * A local comparison snapshot, not a current offer and not a ranking input.
 *
 * The entry retains exact facts already accepted by Compare Here. It deliberately has no store,
 * provider, availability or public-evidence authority because a manual comparison cannot prove
 * any of those things.
 */
internal data class CompareHerePrivatePriceMemoryEntry(
    val observationId: String,
    val displayName: String,
    val price: Money,
    val quantity: NormalizedQuantity,
    val rate: UnitRate,
    val priceSelection: CompareHerePriceSelection,
    val promotionLabel: String?,
    val promotionReceivedUnits: Long,
    val promotionPaidUnits: Long,
    val observedAtEpochMillis: Long,
    val source: CompareHerePrivatePriceMemorySource =
        CompareHerePrivatePriceMemorySource.CONFIRMED_COMPARE_HERE
) {
    init {
        require(observationId.matches(Regex("[0-9a-f]{64}")))
        requireSafeMemoryLabel(displayName, MAX_MEMORY_DISPLAY_NAME_BYTES)
        if (promotionLabel != null) {
            requireSafeMemoryLabel(promotionLabel, MAX_MEMORY_PROMOTION_LABEL_BYTES)
        }
        require(price.minorUnits > 0L)
        require(rate.currencyMicrosPerUnit > 0L)
        require(rate.currencyCode == price.currencyCode)
        require(rate.unit == quantity.toRateUnit())
        require(promotionReceivedUnits > 0L)
        require(promotionPaidUnits > 0L)
        require(promotionPaidUnits <= promotionReceivedUnits)
        require(observedAtEpochMillis >= 0L)
    }

    companion object {
        fun fromExactCandidate(
            candidate: CompareHereExactCandidate,
            displayName: String,
            priceSelection: CompareHerePriceSelection,
            promotionLabel: String?,
            promotionReceivedUnits: Long,
            promotionPaidUnits: Long,
            observedAtEpochMillis: Long
        ): CompareHerePrivatePriceMemoryEntry {
            val safePromotionLabel = normalizeOptionalLabel(promotionLabel)
            val fingerprint =
                fingerprint(
                    displayName = displayName,
                    price = candidate.selectedPrice,
                    quantity = candidate.quantity,
                    rate = candidate.rate,
                    priceSelection = priceSelection,
                    promotionLabel = safePromotionLabel,
                    promotionReceivedUnits = promotionReceivedUnits,
                    promotionPaidUnits = promotionPaidUnits,
                    observedAtEpochMillis = observedAtEpochMillis
                )
            return CompareHerePrivatePriceMemoryEntry(
                observationId = fingerprint,
                displayName = displayName.trim(),
                price = candidate.selectedPrice,
                quantity = candidate.quantity,
                rate = candidate.rate,
                priceSelection = priceSelection,
                promotionLabel = safePromotionLabel,
                promotionReceivedUnits = promotionReceivedUnits,
                promotionPaidUnits = promotionPaidUnits,
                observedAtEpochMillis = observedAtEpochMillis
            )
        }

        internal fun fingerprintFor(entry: CompareHerePrivatePriceMemoryEntry): String =
            fingerprint(
                displayName = entry.displayName,
                price = entry.price,
                quantity = entry.quantity,
                rate = entry.rate,
                priceSelection = entry.priceSelection,
                promotionLabel = entry.promotionLabel,
                promotionReceivedUnits = entry.promotionReceivedUnits,
                promotionPaidUnits = entry.promotionPaidUnits,
                observedAtEpochMillis = entry.observedAtEpochMillis
            )

        private fun fingerprint(
            displayName: String,
            price: Money,
            quantity: NormalizedQuantity,
            rate: UnitRate,
            priceSelection: CompareHerePriceSelection,
            promotionLabel: String?,
            promotionReceivedUnits: Long,
            promotionPaidUnits: Long,
            observedAtEpochMillis: Long
        ): String {
            val fields =
                listOf(
                    displayName.trim(),
                    price.minorUnits.toString(),
                    price.currencyCode,
                    price.fractionDigits.toString(),
                    quantity.amountMicros.toString(),
                    quantity.unit.name,
                    rate.currencyCode,
                    rate.currencyMicrosPerUnit.toString(),
                    rate.unit.name,
                    priceSelection.name,
                    promotionLabel.orEmpty(),
                    promotionReceivedUnits.toString(),
                    promotionPaidUnits.toString(),
                    observedAtEpochMillis.toString(),
                    CompareHerePrivatePriceMemorySource.CONFIRMED_COMPARE_HERE.name
                )
            val canonical =
                fields.joinToString("") { field ->
                    "${field.toByteArray(Charsets.UTF_8).size}:$field|"
                }
            return MessageDigest
                .getInstance("SHA-256")
                .digest(canonical.toByteArray(Charsets.UTF_8))
                .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
        }
    }
}

internal data class CompareHerePrivatePriceMemoryCapture(
    val entries: List<CompareHerePrivatePriceMemoryEntry>
) {
    init {
        require(entries.isNotEmpty())
        require(entries.size <= MAX_COMPARE_HERE_PRIVATE_MEMORY_CAPTURE)
        require(entries.map { it.observationId }.distinct().size == entries.size)
    }
}

internal data class CompareHerePrivatePriceMemoryState(
    val entries: List<CompareHerePrivatePriceMemoryEntry> = emptyList()
) {
    init {
        require(entries.size <= MAX_COMPARE_HERE_PRIVATE_MEMORY_ENTRIES)
        require(entries.map { it.observationId }.distinct().size == entries.size)
    }

    companion object {
        fun empty(): CompareHerePrivatePriceMemoryState = CompareHerePrivatePriceMemoryState()
    }
}

internal object CompareHerePrivatePriceMemoryAssembler {
    fun from(
        success: CompareHereManualComparisonResult.Success,
        observedAtEpochMillis: Long
    ): CompareHerePrivatePriceMemoryCapture? {
        if (success.projection.state.status != CompareHereUiStatus.READY) return null
        if (observedAtEpochMillis < 0L) return null

        val candidatesById = success.adaptation.candidates.associateBy { it.candidateId }
        val exactCandidates = success.projection.exactByCandidateId.values.sortedBy { it.candidateId }
        if (exactCandidates.size < 2 || exactCandidates.size != candidatesById.size) return null

        val entries =
            exactCandidates.map { exact ->
                val candidate = candidatesById[exact.candidateId] ?: return null
                val displayName = success.projection.displayNameByCandidateId[exact.candidateId]
                    ?: return null
                val promotion = candidate.offer.promotion
                CompareHerePrivatePriceMemoryEntry.fromExactCandidate(
                    candidate = exact,
                    displayName = displayName,
                    priceSelection = success.projection.priceSelection,
                    promotionLabel = promotion.label,
                    promotionReceivedUnits = promotion.receivedUnits,
                    promotionPaidUnits = promotion.paidUnits,
                    observedAtEpochMillis = observedAtEpochMillis
                )
            }
        return runCatching { CompareHerePrivatePriceMemoryCapture(entries) }.getOrNull()
    }
}

internal object CompareHerePrivatePriceMemoryStateManager {
    fun append(
        state: CompareHerePrivatePriceMemoryState,
        capture: CompareHerePrivatePriceMemoryCapture
    ): CompareHerePrivatePriceMemoryState {
        val merged =
            (state.entries + capture.entries)
                .associateBy { it.observationId }
                .values
                .sortedWith(
                    compareByDescending<CompareHerePrivatePriceMemoryEntry> { it.observedAtEpochMillis }
                        .thenBy { it.observationId }
                )
                .take(MAX_COMPARE_HERE_PRIVATE_MEMORY_ENTRIES)
        return CompareHerePrivatePriceMemoryState(merged)
    }
}

internal enum class CompareHerePrivatePriceMemoryCodecIssue {
    FIELD_TOO_LARGE,
    OUTPUT_TOO_LARGE,
    INPUT_TOO_LARGE,
    TOO_MANY_RECORDS,
    INVALID_HEADER,
    MALFORMED_INPUT
}

internal data class CompareHerePrivatePriceMemoryEncodeResult(
    val bytes: ByteArray?,
    val issue: CompareHerePrivatePriceMemoryCodecIssue? = null
) {
    init {
        require((bytes != null) == (issue == null))
    }

    val accepted: Boolean
        get() = bytes != null
}

internal data class CompareHerePrivatePriceMemoryDecodeResult(
    val state: CompareHerePrivatePriceMemoryState?,
    val issue: CompareHerePrivatePriceMemoryCodecIssue? = null
) {
    init {
        require((state != null) == (issue == null))
    }

    val accepted: Boolean
        get() = state != null
}

/** Deterministic, bounded, ASCII-only local format. It is not a public/current offer artifact. */
internal object CompareHerePrivatePriceMemoryCodec {
    val maximumEncodedBytes: Int
        get() = MAX_COMPARE_HERE_PRIVATE_MEMORY_ENCODED_BYTES

    fun encode(
        state: CompareHerePrivatePriceMemoryState
    ): CompareHerePrivatePriceMemoryEncodeResult {
        val ordered =
            state.entries.sortedWith(
                compareByDescending<CompareHerePrivatePriceMemoryEntry> { it.observedAtEpochMillis }
                    .thenBy { it.observationId }
            )
        if (ordered.any { !fieldsFit(it) }) {
            return CompareHerePrivatePriceMemoryEncodeResult(
                bytes = null,
                issue = CompareHerePrivatePriceMemoryCodecIssue.FIELD_TOO_LARGE
            )
        }

        val lines = ArrayList<String>(1 + ordered.size)
        lines += "$MEMORY_CODEC_MAGIC|1"
        ordered.forEach { entry ->
            lines +=
                listOf(
                    MEMORY_RECORD,
                    encodeField(entry.observationId),
                    encodeField(entry.displayName),
                    entry.price.minorUnits.toString(),
                    entry.price.currencyCode,
                    entry.price.fractionDigits.toString(),
                    entry.quantity.amountMicros.toString(),
                    entry.quantity.unit.name,
                    entry.rate.currencyCode,
                    entry.rate.currencyMicrosPerUnit.toString(),
                    entry.rate.unit.name,
                    entry.priceSelection.name,
                    encodeField(entry.promotionLabel),
                    entry.promotionReceivedUnits.toString(),
                    entry.promotionPaidUnits.toString(),
                    entry.observedAtEpochMillis.toString(),
                    entry.source.name
                ).joinToString("|")
        }
        val bytes = lines.joinToString("\n").toByteArray(Charsets.US_ASCII)
        if (bytes.size > MAX_COMPARE_HERE_PRIVATE_MEMORY_ENCODED_BYTES) {
            return CompareHerePrivatePriceMemoryEncodeResult(
                bytes = null,
                issue = CompareHerePrivatePriceMemoryCodecIssue.OUTPUT_TOO_LARGE
            )
        }
        return CompareHerePrivatePriceMemoryEncodeResult(bytes = bytes)
    }

    fun decode(bytes: ByteArray): CompareHerePrivatePriceMemoryDecodeResult {
        if (bytes.size > MAX_COMPARE_HERE_PRIVATE_MEMORY_ENCODED_BYTES) {
            return failure(CompareHerePrivatePriceMemoryCodecIssue.INPUT_TOO_LARGE)
        }
        if (bytes.any { (it.toInt() and 0xff) > 0x7f }) {
            return failure(CompareHerePrivatePriceMemoryCodecIssue.MALFORMED_INPUT)
        }

        val lines = String(bytes, Charsets.US_ASCII).split('\n')
        if (lines.isEmpty()) return failure(CompareHerePrivatePriceMemoryCodecIssue.INVALID_HEADER)
        val header = lines.first().split('|')
        if (header.size != 2 || header[0] != MEMORY_CODEC_MAGIC || header[1] != "1") {
            return failure(CompareHerePrivatePriceMemoryCodecIssue.INVALID_HEADER)
        }
        if (lines.size - 1 > MAX_COMPARE_HERE_PRIVATE_MEMORY_ENTRIES) {
            return failure(CompareHerePrivatePriceMemoryCodecIssue.TOO_MANY_RECORDS)
        }

        return try {
            val entries =
                lines.drop(1).map { line ->
                    if (line.isEmpty()) throw CodecParseFailure()
                    decodeEntry(line.split('|'))
                }
            val state = CompareHerePrivatePriceMemoryState(entries)
            if (entries.any { CompareHerePrivatePriceMemoryEntry.fingerprintFor(it) != it.observationId }) {
                throw CodecParseFailure()
            }
            CompareHerePrivatePriceMemoryDecodeResult(state = state)
        } catch (_: CodecParseFailure) {
            failure(CompareHerePrivatePriceMemoryCodecIssue.MALFORMED_INPUT)
        } catch (_: IllegalArgumentException) {
            failure(CompareHerePrivatePriceMemoryCodecIssue.MALFORMED_INPUT)
        } catch (_: ArithmeticException) {
            failure(CompareHerePrivatePriceMemoryCodecIssue.MALFORMED_INPUT)
        }
    }

    private fun decodeEntry(parts: List<String>): CompareHerePrivatePriceMemoryEntry {
        if (parts.size != 17 || parts[0] != MEMORY_RECORD) throw CodecParseFailure()
        val observationId = decodeRequired(parts[1], 64)
        val displayName = decodeRequired(parts[2], MAX_MEMORY_DISPLAY_NAME_BYTES)
        val priceMinor = parseLong(parts[3], positive = true)
        val currency = parseCurrency(parts[4])
        val fractionDigits = parseLong(parts[5], positive = false).toInt()
        if (fractionDigits !in 0..6) throw CodecParseFailure()
        val quantityAmount = parseLong(parts[6], positive = true)
        val quantityUnit = parseEnum<BaseUnit>(parts[7])
        val rateCurrency = parseCurrency(parts[8])
        val rateMicros = parseLong(parts[9], positive = true)
        val rateUnit = parseEnum<RateUnit>(parts[10])
        val selection = parseEnum<CompareHerePriceSelection>(parts[11])
        val promotionLabel = decodeNullable(parts[12], MAX_MEMORY_PROMOTION_LABEL_BYTES)
        val receivedUnits = parseLong(parts[13], positive = true)
        val paidUnits = parseLong(parts[14], positive = true)
        val observedAt = parseLong(parts[15], positive = false)
        val source = parseEnum<CompareHerePrivatePriceMemorySource>(parts[16])
        if (source != CompareHerePrivatePriceMemorySource.CONFIRMED_COMPARE_HERE) {
            throw CodecParseFailure()
        }
        return CompareHerePrivatePriceMemoryEntry(
            observationId = observationId,
            displayName = displayName,
            price = Money(priceMinor, currency, fractionDigits),
            quantity = NormalizedQuantity(quantityAmount, quantityUnit),
            rate = UnitRate(rateCurrency, rateMicros, rateUnit),
            priceSelection = selection,
            promotionLabel = promotionLabel,
            promotionReceivedUnits = receivedUnits,
            promotionPaidUnits = paidUnits,
            observedAtEpochMillis = observedAt,
            source = source
        )
    }

    private fun fieldsFit(entry: CompareHerePrivatePriceMemoryEntry): Boolean =
        entry.displayName.toByteArray(Charsets.UTF_8).size <= MAX_MEMORY_DISPLAY_NAME_BYTES &&
            (entry.promotionLabel == null ||
                entry.promotionLabel.toByteArray(Charsets.UTF_8).size <= MAX_MEMORY_PROMOTION_LABEL_BYTES)

    private fun parseLong(token: String, positive: Boolean): Long {
        if (!token.matches(Regex(if (positive) "[1-9]\\d*" else "0|[1-9]\\d*"))) {
            throw CodecParseFailure()
        }
        return token.toLongOrNull() ?: throw CodecParseFailure()
    }

    private fun parseCurrency(token: String): String {
        if (!token.matches(Regex("[A-Z]{3}"))) throw CodecParseFailure()
        return token
    }

    private inline fun <reified T : Enum<T>> parseEnum(token: String): T =
        try {
            enumValueOf<T>(token)
        } catch (_: IllegalArgumentException) {
            throw CodecParseFailure()
        }

    private fun encodeField(value: String?): String {
        if (value == null) return NULL_FIELD
        val bytes = value.toByteArray(Charsets.UTF_8)
        val output = StringBuilder(bytes.size * 2)
        bytes.forEach { byte ->
            val unsigned = byte.toInt() and 0xff
            output.append(HEX[unsigned ushr 4])
            output.append(HEX[unsigned and 0x0f])
        }
        return output.toString()
    }

    private fun decodeRequired(token: String, maxBytes: Int): String {
        if (token == NULL_FIELD) throw CodecParseFailure()
        return decodeHex(token, maxBytes)
    }

    private fun decodeNullable(token: String, maxBytes: Int): String? =
        if (token == NULL_FIELD) null else decodeHex(token, maxBytes)

    private fun decodeHex(token: String, maxBytes: Int): String {
        if (token.length % 2 != 0 || token.length > maxBytes * 2) throw CodecParseFailure()
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
            val decoder =
                Charsets.UTF_8.newDecoder()
                    .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                    .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
            decoder.decode(java.nio.ByteBuffer.wrap(bytes)).toString()
        } catch (_: Exception) {
            throw CodecParseFailure()
        }
    }

    private fun failure(issue: CompareHerePrivatePriceMemoryCodecIssue) =
        CompareHerePrivatePriceMemoryDecodeResult(state = null, issue = issue)

    private class CodecParseFailure : RuntimeException()

    private val HEX = "0123456789abcdef".toCharArray()
}

private fun requireSafeMemoryLabel(value: String, maxBytes: Int) {
    require(value.isNotBlank())
    require(value == value.trim())
    require(value.toByteArray(Charsets.UTF_8).size <= maxBytes)
    require(value.none { Character.isISOControl(it.code) })
}

private fun normalizeOptionalLabel(value: String?): String? {
    val trimmed = value?.trim()?.takeIf { it.isNotBlank() } ?: return null
    return trimmed.takeIf {
        it.toByteArray(Charsets.UTF_8).size <= MAX_MEMORY_PROMOTION_LABEL_BYTES &&
            it.none { character -> Character.isISOControl(character.code) }
    }
}

private fun NormalizedQuantity.toRateUnit(): RateUnit =
    when (unit) {
        BaseUnit.GRAM -> RateUnit.KILOGRAM
        BaseUnit.MILLILITRE -> RateUnit.LITRE
        BaseUnit.COUNT -> RateUnit.ITEM
        BaseUnit.SQUARE_INCH -> RateUnit.SQUARE_INCH
    }
