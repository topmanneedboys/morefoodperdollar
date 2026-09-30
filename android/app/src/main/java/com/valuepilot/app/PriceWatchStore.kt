package com.valuepilot.app

import android.content.Context
import android.util.AtomicFile
import com.valuepilot.core.BaseUnit
import com.valuepilot.core.ConfirmedPurchaseStore
import com.valuepilot.core.Money
import com.valuepilot.core.NormalizedQuantity
import com.valuepilot.core.PriceObservation
import com.valuepilot.core.PriceObservationFreshness
import com.valuepilot.core.PriceObservationPermission
import com.valuepilot.core.PriceObservationProvenance
import com.valuepilot.core.PriceObservationSourceKind
import com.valuepilot.core.PriceWatch
import com.valuepilot.core.PriceWatchState
import com.valuepilot.core.ShoppingStoreKey
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

private const val PRICE_WATCH_FILE_NAME = "price-watch.v1"
private const val PRICE_WATCH_MAGIC = "VALUEPILOT_PRICE_WATCH"
private const val WATCH_RECORD = "W"
private const val OBSERVATION_RECORD = "O"
private const val NULL_FIELD = "~"
private const val MAX_ENCODED_BYTES = 524_288
private const val READ_BUFFER_BYTES = 8_192

internal enum class PriceWatchCodecIssue {
    INPUT_TOO_LARGE,
    OUTPUT_TOO_LARGE,
    INVALID_HEADER,
    MALFORMED_INPUT,
    TOO_MANY_RECORDS,
    DUPLICATE_WATCH,
    DUPLICATE_OBSERVATION,
    FIELD_TOO_LARGE
}

internal data class PriceWatchEncodeResult(
    val bytes: ByteArray?,
    val issue: PriceWatchCodecIssue? = null
) {
    init {
        require((bytes != null) == (issue == null))
    }

    val accepted: Boolean
        get() = bytes != null
}

internal data class PriceWatchDecodeResult(
    val state: PriceWatchState?,
    val issue: PriceWatchCodecIssue? = null
) {
    init {
        require((state != null) == (issue == null))
    }

    val accepted: Boolean
        get() = state != null
}

/** Deterministic ASCII/hex document shared by the local watch store only. */
internal object PriceWatchCodec {
    val maximumEncodedBytes: Int
        get() = MAX_ENCODED_BYTES

    fun encode(state: PriceWatchState): PriceWatchEncodeResult {
        val watches = state.watches.sortedBy { it.productIdentityKey }
        val observations =
            state.observations.sortedWith(
                compareByDescending<PriceObservation> { it.observedDateIso ?: "" }
                    .thenBy { it.observationId }
            )
        if (watches.any { !watchFits(it) } || observations.any { !observationFits(it) }) {
            return PriceWatchEncodeResult(null, PriceWatchCodecIssue.FIELD_TOO_LARGE)
        }

        val lines = ArrayList<String>(watches.size + observations.size + 1)
        lines += "$PRICE_WATCH_MAGIC|2"
        watches.forEach { watch ->
            val target = watch.targetPrice
            lines +=
                listOf(
                    WATCH_RECORD,
                    encodeField(watch.productIdentityKey),
                    encodeField(watch.displayName),
                    encodeNullable(watch.brand),
                    encodeNullable(watch.packageQuantityLabel),
                    target?.minorUnits?.toString() ?: NULL_FIELD,
                    target?.currencyCode ?: NULL_FIELD,
                    target?.fractionDigits?.toString() ?: NULL_FIELD
                ).joinToString("|")
        }
        observations.forEach { observation ->
            val quantity = observation.packageQuantity
            val store = observation.store
            val scope = store?.identityScope
            val provenance = observation.provenance
            lines +=
                listOf(
                    OBSERVATION_RECORD,
                    encodeField(observation.observationId),
                    encodeField(observation.productIdentityKey),
                    observation.price.minorUnits.toString(),
                    observation.price.currencyCode,
                    observation.price.fractionDigits.toString(),
                    quantity?.amountMicros?.toString() ?: NULL_FIELD,
                    quantity?.unit?.name ?: NULL_FIELD,
                    encodeNullable(observation.observedDateIso),
                    encodeNullable(store?.displayName),
                    encodeNullable(scope?.merchantKey),
                    encodeNullable(scope?.locationKey),
                    encodeNullable(scope?.commerceChannelKey),
                    encodeNullable(store?.storeKey?.value),
                    observation.freshness.name,
                    provenance.sourceKind.name,
                    encodeField(provenance.sourceId),
                    encodeNullable(provenance.rightsRecordId),
                    provenance.permission.name
                ).joinToString("|")
        }

        val bytes = lines.joinToString("\n").toByteArray(StandardCharsets.US_ASCII)
        if (bytes.size > MAX_ENCODED_BYTES) {
            return PriceWatchEncodeResult(null, PriceWatchCodecIssue.OUTPUT_TOO_LARGE)
        }
        return PriceWatchEncodeResult(bytes)
    }

    fun decode(bytes: ByteArray): PriceWatchDecodeResult {
        if (bytes.size > MAX_ENCODED_BYTES) {
            return failure(PriceWatchCodecIssue.INPUT_TOO_LARGE)
        }
        if (bytes.any { (it.toInt() and 0xff) > 0x7f }) {
            return failure(PriceWatchCodecIssue.MALFORMED_INPUT)
        }
        val lines = String(bytes, StandardCharsets.US_ASCII).split('\n')
        val header = lines.firstOrNull()?.split('|').orEmpty()
        if (header.size != 2 || header[0] != PRICE_WATCH_MAGIC || header[1] !in setOf("1", "2")) {
            return failure(PriceWatchCodecIssue.INVALID_HEADER)
        }
        val version = header[1].toInt()
        if (lines.size - 1 > PriceWatchState.MAX_PRICE_WATCHES + PriceWatchState.MAX_PRICE_OBSERVATIONS) {
            return failure(PriceWatchCodecIssue.TOO_MANY_RECORDS)
        }

        return try {
            val watches = mutableListOf<PriceWatch>()
            val observations = mutableListOf<PriceObservation>()
            lines.drop(1).forEach { line ->
                if (line.isEmpty()) throw CodecParseFailure()
                when (line.firstOrNull()) {
                    WATCH_RECORD.first() -> watches += decodeWatch(line.split('|'))
                    OBSERVATION_RECORD.first() -> observations += decodeObservation(line.split('|'), version)
                    else -> throw CodecParseFailure()
                }
            }
            if (watches.map { it.productIdentityKey }.distinct().size != watches.size) {
                return failure(PriceWatchCodecIssue.DUPLICATE_WATCH)
            }
            if (observations.map { it.observationId }.distinct().size != observations.size) {
                return failure(PriceWatchCodecIssue.DUPLICATE_OBSERVATION)
            }
            PriceWatchDecodeResult(PriceWatchState(watches, observations))
        } catch (_: CodecParseFailure) {
            failure(PriceWatchCodecIssue.MALFORMED_INPUT)
        } catch (_: IllegalArgumentException) {
            failure(PriceWatchCodecIssue.MALFORMED_INPUT)
        } catch (_: ArithmeticException) {
            failure(PriceWatchCodecIssue.MALFORMED_INPUT)
        }
    }

    private fun decodeWatch(parts: List<String>): PriceWatch {
        if (parts.size != 8 || parts[0] != WATCH_RECORD) throw CodecParseFailure()
        val key = decodeRequired(parts[1], 256)
        val name = decodeRequired(parts[2], 512)
        val brand = decodeNullable(parts[3], 160)
        val packageLabel = decodeNullable(parts[4], 96)
        val targetMinor = parseNullableLong(parts[5], positive = false)
        val targetCurrency = parseNullableCurrency(parts[6])
        val targetFraction = parseNullableLong(parts[7], positive = false)
        if ((targetMinor == null) != (targetCurrency == null) ||
            (targetMinor == null) != (targetFraction == null)
        ) {
            throw CodecParseFailure()
        }
        val target =
            if (targetMinor == null) null
            else Money(targetMinor, requireNotNull(targetCurrency), requireNotNull(targetFraction).toInt())
        return PriceWatch(key, name, brand, packageLabel, target)
    }

    private fun decodeObservation(parts: List<String>, version: Int): PriceObservation {
        val expectedSize = if (version == 1) 18 else 19
        if (parts.size != expectedSize || parts[0] != OBSERVATION_RECORD) throw CodecParseFailure()
        val id = decodeRequired(parts[1], 160)
        val key = decodeRequired(parts[2], 256)
        val price =
            Money(
                parseLong(parts[3], positive = true),
                parseCurrency(parts[4]),
                parseLong(parts[5], positive = false).toInt()
            )
        val quantityAmount = parseNullableLong(parts[6], positive = true)
        val quantityUnit = parseNullableEnum<BaseUnit>(parts[7])
        if ((quantityAmount == null) != (quantityUnit == null)) throw CodecParseFailure()
        val quantity =
            if (quantityAmount == null) null
            else NormalizedQuantity(quantityAmount, requireNotNull(quantityUnit))
        val date = decodeNullable(parts[8], 10)
        if (date != null && !com.valuepilot.core.ConfirmedPurchase.isValidIsoDate(date)) {
            throw CodecParseFailure()
        }
        val storeDisplay = decodeNullable(parts[9], 240)
        val merchant = decodeNullable(parts[10], 240)
        val location = decodeNullable(parts[11], 240)
        val channel = decodeNullable(parts[12], 160)
        if ((merchant == null) != (location == null) || (merchant == null) != (channel == null)) {
            throw CodecParseFailure()
        }
        val storeKey =
            if (version == 2) {
                decodeNullable(parts[13], 256)?.let(::ShoppingStoreKey)
            } else {
                null
            }
        val scope =
            if (merchant == null) null
            else com.valuepilot.core.PracticalShoppingStoreIdentityScope(
                merchantKey = merchant,
                locationKey = location,
                commerceChannelKey = requireNotNull(channel)
            )
        val store =
            if (storeDisplay == null && scope == null && storeKey == null) null
            else ConfirmedPurchaseStore(displayName = storeDisplay, identityScope = scope, storeKey = storeKey)
        val freshnessIndex = if (version == 2) 14 else 13
        val freshness = parseEnum<PriceObservationFreshness>(parts[freshnessIndex])
        val sourceKind = parseEnum<PriceObservationSourceKind>(parts[freshnessIndex + 1])
        val sourceId = decodeRequired(parts[freshnessIndex + 2], 160)
        val rights = decodeNullable(parts[freshnessIndex + 3], 240)
        val permission = parseEnum<PriceObservationPermission>(parts[freshnessIndex + 4])
        return PriceObservation(
            observationId = id,
            productIdentityKey = key,
            packageQuantity = quantity,
            price = price,
            observedDateIso = date,
            store = store,
            freshness = freshness,
            provenance =
                PriceObservationProvenance(
                    sourceId = sourceId,
                    sourceKind = sourceKind,
                    rightsRecordId = rights,
                    permission = permission
                )
        )
    }

    private fun watchFits(watch: PriceWatch): Boolean =
        watch.productIdentityKey.toByteArray(StandardCharsets.UTF_8).size <= 1024 &&
            watch.displayName.toByteArray(StandardCharsets.UTF_8).size <= 2048

    private fun observationFits(observation: PriceObservation): Boolean =
        observation.observationId.toByteArray(StandardCharsets.UTF_8).size <= 640 &&
            observation.productIdentityKey.toByteArray(StandardCharsets.UTF_8).size <= 1024 &&
            observation.provenance.sourceId.toByteArray(StandardCharsets.UTF_8).size <= 640

    private fun encodeNullable(value: String?): String = value?.let(::encodeField) ?: NULL_FIELD

    private fun encodeField(value: String): String =
        value.toByteArray(StandardCharsets.UTF_8).joinToString(separator = "") { byte ->
            "%02x".format(byte.toInt() and 0xff)
        }

    private fun decodeRequired(value: String, maxLength: Int): String =
        decodeNullable(value, maxLength) ?: throw CodecParseFailure()

    private fun decodeNullable(value: String, maxLength: Int): String? {
        if (value == NULL_FIELD) return null
        if (value.isEmpty() || value.length % 2 != 0 || value.length > maxLength * 8) {
            throw CodecParseFailure()
        }
        val bytes = ByteArray(value.length / 2)
        (value.indices step 2).forEach { index ->
            val high = HEX_DIGITS.indexOf(value[index])
            val low = HEX_DIGITS.indexOf(value[index + 1])
            if (high < 0 || low < 0) throw CodecParseFailure()
            bytes[index / 2] = ((high shl 4) or low).toByte()
        }
        val decoded =
            try {
                StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString()
            } catch (_: CharacterCodingException) {
                throw CodecParseFailure()
            }
        if (decoded.length > maxLength || decoded.isBlank()) throw CodecParseFailure()
        return decoded
    }

    private fun parseNullableLong(value: String, positive: Boolean): Long? =
        if (value == NULL_FIELD) null else parseLong(value, positive)

    private fun parseLong(value: String, positive: Boolean): Long {
        val pattern = if (positive) "[1-9]\\d*" else "0|[1-9]\\d*"
        if (!value.matches(Regex(pattern))) throw CodecParseFailure()
        return value.toLongOrNull() ?: throw CodecParseFailure()
    }

    private fun parseNullableCurrency(value: String): String? =
        if (value == NULL_FIELD) null else parseCurrency(value)

    private fun parseCurrency(value: String): String {
        if (!value.matches(Regex("[A-Z]{3}"))) throw CodecParseFailure()
        return value
    }

    private inline fun <reified T : Enum<T>> parseNullableEnum(value: String): T? =
        if (value == NULL_FIELD) null else parseEnum(value)

    private inline fun <reified T : Enum<T>> parseEnum(value: String): T =
        try {
            enumValueOf<T>(value)
        } catch (_: IllegalArgumentException) {
            throw CodecParseFailure()
        }

    private fun failure(issue: PriceWatchCodecIssue) = PriceWatchDecodeResult(null, issue)

    private class CodecParseFailure : RuntimeException()
    private const val HEX_DIGITS = "0123456789abcdef"
}

internal enum class PriceWatchStoreIssue {
    READ_FAILED,
    STORED_DATA_TOO_LARGE,
    STORED_DATA_INVALID,
    ENCODE_REJECTED,
    WRITE_FAILED,
    DUPLICATE_WATCH,
    WATCH_NOT_FOUND,
    DUPLICATE_OBSERVATION,
    OBSERVATION_NOT_FOUND,
    WATCH_LIMIT,
    OBSERVATION_LIMIT
}

internal data class PriceWatchStoreLoadResult(
    val state: PriceWatchState?,
    val foundStoredDocument: Boolean,
    val issue: PriceWatchStoreIssue? = null,
    val codecIssue: PriceWatchCodecIssue? = null
) {
    init {
        require((state != null) == (issue == null))
        require(codecIssue == null || issue == PriceWatchStoreIssue.STORED_DATA_INVALID)
    }

    val accepted: Boolean
        get() = state != null
}

internal data class PriceWatchStoreMutationResult(
    val state: PriceWatchState?,
    val issue: PriceWatchStoreIssue? = null,
    val codecIssue: PriceWatchCodecIssue? = null
) {
    init {
        require((state != null) == (issue == null))
        require(codecIssue == null || issue == PriceWatchStoreIssue.STORED_DATA_INVALID || issue == PriceWatchStoreIssue.ENCODE_REJECTED)
    }

    val accepted: Boolean
        get() = state != null
}

internal interface PriceWatchStore {
    fun load(): PriceWatchStoreLoadResult
    fun upsertWatch(watch: PriceWatch): PriceWatchStoreMutationResult
    fun removeWatch(productIdentityKey: String): PriceWatchStoreMutationResult
    fun appendObservation(observation: PriceObservation): PriceWatchStoreMutationResult
    fun replaceObservation(observation: PriceObservation): PriceWatchStoreMutationResult
    fun removeObservation(observationId: String): PriceWatchStoreMutationResult
}

internal data class PriceWatchRawReadResult(
    val bytes: ByteArray?,
    val found: Boolean,
    val issue: PriceWatchStoreIssue? = null
)

internal interface PriceWatchByteStorage {
    fun read(maxBytes: Int): PriceWatchRawReadResult
    fun replace(bytes: ByteArray): Boolean
}

private class AndroidPriceWatchByteStorage(context: Context) : PriceWatchByteStorage {
    private val atomicFile = AtomicFile(File(context.filesDir, PRICE_WATCH_FILE_NAME))

    override fun read(maxBytes: Int): PriceWatchRawReadResult {
        val input =
            try {
                atomicFile.openRead()
            } catch (_: FileNotFoundException) {
                return PriceWatchRawReadResult(null, atomicFile.baseFile.exists(), if (atomicFile.baseFile.exists()) PriceWatchStoreIssue.READ_FAILED else null)
            } catch (_: Exception) {
                return PriceWatchRawReadResult(null, atomicFile.baseFile.exists(), PriceWatchStoreIssue.READ_FAILED)
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
                        return PriceWatchRawReadResult(null, true, PriceWatchStoreIssue.STORED_DATA_TOO_LARGE)
                    }
                    output.write(buffer, 0, read)
                }
                PriceWatchRawReadResult(output.toByteArray(), true)
            }
        } catch (_: Exception) {
            PriceWatchRawReadResult(null, true, PriceWatchStoreIssue.READ_FAILED)
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
            output?.let { runCatching { atomicFile.failWrite(it) } }
            false
        }
    }
}

/** Bounded private storage. Malformed/read-failed state blocks mutation and is never replaced. */
internal class AndroidPriceWatchStore internal constructor(
    private val storage: PriceWatchByteStorage
) : PriceWatchStore {
    constructor(context: Context) : this(AndroidPriceWatchByteStorage(context.applicationContext ?: context))

    @Synchronized
    override fun load(): PriceWatchStoreLoadResult {
        val raw = storage.read(PriceWatchCodec.maximumEncodedBytes)
        raw.issue?.let { issue ->
            return PriceWatchStoreLoadResult(null, raw.found, issue)
        }
        if (!raw.found) return PriceWatchStoreLoadResult(PriceWatchState.empty(), false)
        val decoded = PriceWatchCodec.decode(requireNotNull(raw.bytes))
        if (!decoded.accepted) {
            return PriceWatchStoreLoadResult(
                state = null,
                foundStoredDocument = true,
                issue = PriceWatchStoreIssue.STORED_DATA_INVALID,
                codecIssue = decoded.issue
            )
        }
        return PriceWatchStoreLoadResult(requireNotNull(decoded.state), true)
    }

    @Synchronized
    override fun upsertWatch(watch: PriceWatch): PriceWatchStoreMutationResult {
        val loaded = load()
        if (!loaded.accepted) return failure(loaded.issue, loaded.codecIssue)
        val current = requireNotNull(loaded.state)
        val exists = current.watches.any { it.productIdentityKey == watch.productIdentityKey }
        if (!exists && current.watches.size >= PriceWatchState.MAX_PRICE_WATCHES) {
            return PriceWatchStoreMutationResult(null, PriceWatchStoreIssue.WATCH_LIMIT)
        }
        val next =
            PriceWatchState(
                watches =
                    current.watches
                        .filterNot { it.productIdentityKey == watch.productIdentityKey } + watch,
                observations = current.observations
            )
        return replace(next)
    }

    @Synchronized
    override fun removeWatch(productIdentityKey: String): PriceWatchStoreMutationResult {
        val loaded = load()
        if (!loaded.accepted) return failure(loaded.issue, loaded.codecIssue)
        val current = requireNotNull(loaded.state)
        if (current.watches.none { it.productIdentityKey == productIdentityKey }) {
            return PriceWatchStoreMutationResult(null, PriceWatchStoreIssue.WATCH_NOT_FOUND)
        }
        return replace(current.copy(watches = current.watches.filterNot { it.productIdentityKey == productIdentityKey }))
    }

    @Synchronized
    override fun appendObservation(observation: PriceObservation): PriceWatchStoreMutationResult {
        val loaded = load()
        if (!loaded.accepted) return failure(loaded.issue, loaded.codecIssue)
        val current = requireNotNull(loaded.state)
        if (current.observations.any { it.observationId == observation.observationId }) {
            return PriceWatchStoreMutationResult(null, PriceWatchStoreIssue.DUPLICATE_OBSERVATION)
        }
        if (current.observations.size >= PriceWatchState.MAX_PRICE_OBSERVATIONS) {
            return PriceWatchStoreMutationResult(null, PriceWatchStoreIssue.OBSERVATION_LIMIT)
        }
        return replace(current.copy(observations = current.observations + observation))
    }

    @Synchronized
    override fun replaceObservation(observation: PriceObservation): PriceWatchStoreMutationResult {
        val loaded = load()
        if (!loaded.accepted) return failure(loaded.issue, loaded.codecIssue)
        val current = requireNotNull(loaded.state)
        if (current.observations.none { it.observationId == observation.observationId }) {
            return PriceWatchStoreMutationResult(null, PriceWatchStoreIssue.OBSERVATION_NOT_FOUND)
        }
        return replace(
            current.copy(
                observations =
                    current.observations.map {
                        if (it.observationId == observation.observationId) observation else it
                    }
            )
        )
    }

    @Synchronized
    override fun removeObservation(observationId: String): PriceWatchStoreMutationResult {
        val loaded = load()
        if (!loaded.accepted) return failure(loaded.issue, loaded.codecIssue)
        val current = requireNotNull(loaded.state)
        if (current.observations.none { it.observationId == observationId }) {
            return PriceWatchStoreMutationResult(null, PriceWatchStoreIssue.OBSERVATION_NOT_FOUND)
        }
        return replace(current.copy(observations = current.observations.filterNot { it.observationId == observationId }))
    }

    private fun replace(state: PriceWatchState): PriceWatchStoreMutationResult {
        val encoded = PriceWatchCodec.encode(state)
        if (!encoded.accepted) {
            return PriceWatchStoreMutationResult(
                null,
                PriceWatchStoreIssue.ENCODE_REJECTED,
                encoded.issue
            )
        }
        if (!storage.replace(requireNotNull(encoded.bytes))) {
            return PriceWatchStoreMutationResult(null, PriceWatchStoreIssue.WRITE_FAILED)
        }
        return PriceWatchStoreMutationResult(state)
    }

    private fun failure(issue: PriceWatchStoreIssue?, codecIssue: PriceWatchCodecIssue?) =
        PriceWatchStoreMutationResult(
            null,
            issue ?: PriceWatchStoreIssue.STORED_DATA_INVALID,
            codecIssue
        )
}
