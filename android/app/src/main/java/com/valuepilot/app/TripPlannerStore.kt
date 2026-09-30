package com.valuepilot.app

import android.content.Context
import android.util.AtomicFile
import com.valuepilot.core.Money
import com.valuepilot.core.ShoppingStoreKey
import com.valuepilot.core.TripCostInput
import com.valuepilot.core.TripDistanceBasis
import com.valuepilot.core.TripOriginKind
import com.valuepilot.core.TripOriginReference
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

private const val TRIP_FILE_NAME = "trip-planner.v1"
private const val TRIP_MAGIC = "VALUEPILOT_TRIP_PLANNER"
private const val NULL_FIELD = "~"
private const val MAX_TRIP_BYTES = 65_536
private const val MAX_TRIP_TEXT = 240
private const val READ_BUFFER_BYTES = 8_192

internal data class TripStoreSelection(
    val storeKey: ShoppingStoreKey,
    val displayName: String,
    val addressLabel: String? = null,
    val sourceLabel: String,
    val latitudeE7: Int? = null,
    val longitudeE7: Int? = null
) {
    init {
        require(displayName.isNotBlank() && displayName.length <= MAX_TRIP_TEXT)
        require(displayName.none { it == '\n' || it == '\r' })
        require(addressLabel == null || (addressLabel.isNotBlank() && addressLabel.length <= MAX_TRIP_TEXT))
        require(sourceLabel.isNotBlank() && sourceLabel.length <= MAX_TRIP_TEXT)
        require((latitudeE7 == null) == (longitudeE7 == null))
        latitudeE7?.let { require(it in -900_000_000..900_000_000) }
        longitudeE7?.let { require(it in -1_800_000_000..1_800_000_000) }
    }
}

/** Durable trip draft; the basket itself is deliberately not copied here. */
internal data class TripPlannerState(
    val destination: TripStoreSelection? = null,
    val preferredStoreKey: ShoppingStoreKey? = null,
    val origin: TripOriginReference? = null,
    val drivingDistanceMetres: Long? = null,
    val distanceBasis: TripDistanceBasis? = null,
    val fuelConsumptionMilliLitresPer100Km: Long? = null,
    val fuelPrice: Money? = null,
    val tolls: Money? = null,
    val parking: Money? = null
) {
    init {
        require(destination == null || destination.storeKey.value.isNotBlank())
        require(preferredStoreKey == null || preferredStoreKey.value.isNotBlank())
        require(preferredStoreKey == null || destination?.storeKey == preferredStoreKey)
        require((drivingDistanceMetres == null) == (distanceBasis == null))
        require(drivingDistanceMetres == null || drivingDistanceMetres >= 0L)
        require(fuelConsumptionMilliLitresPer100Km == null || fuelConsumptionMilliLitresPer100Km > 0L)
        fuelPrice?.let { require(it.minorUnits >= 0L) }
        tolls?.let { require(it.minorUnits >= 0L) }
        parking?.let { require(it.minorUnits >= 0L) }
        require(tolls == null || fuelPrice != null)
        require(parking == null || fuelPrice != null)
        listOfNotNull(fuelPrice, tolls, parking).drop(1).forEach { money ->
            val first = requireNotNull(fuelPrice)
            require(first.currencyCode == money.currencyCode && first.fractionDigits == money.fractionDigits)
        }
        if (fuelPrice != null) require(fuelConsumptionMilliLitresPer100Km != null)
        if (fuelConsumptionMilliLitresPer100Km != null) require(fuelPrice != null)
    }

    fun costInputOrNull(): TripCostInput? {
        val distance = drivingDistanceMetres ?: return null
        val basis = distanceBasis ?: return null
        val consumption = fuelConsumptionMilliLitresPer100Km ?: return null
        val fuel = fuelPrice ?: return null
        return TripCostInput(
            drivingDistanceMetres = distance,
            distanceBasis = basis,
            fuelConsumptionMilliLitresPer100Km = consumption,
            fuelPricePerLitre = fuel,
            tolls = tolls,
            parking = parking
        )
    }
}

internal enum class TripPlannerCodecIssue {
    INVALID_HEADER,
    MALFORMED_INPUT,
    INPUT_TOO_LARGE,
    OUTPUT_TOO_LARGE
}

internal data class TripPlannerCodecResult(
    val state: TripPlannerState?,
    val issue: TripPlannerCodecIssue? = null
) {
    init {
        require((state != null) == (issue == null))
    }

    val accepted: Boolean
        get() = state != null
}

internal object TripPlannerCodec {
    fun encode(state: TripPlannerState): ByteArray? {
        val destination = state.destination
        val origin = state.origin
        val fuel = state.fuelPrice
        val encodedFields = listOf(
            encodeNullable(destination?.storeKey?.value),
            encodeNullable(destination?.displayName),
            encodeNullable(destination?.addressLabel),
            encodeNullable(destination?.sourceLabel),
            destination?.latitudeE7?.toString() ?: NULL_FIELD,
            destination?.longitudeE7?.toString() ?: NULL_FIELD,
            encodeNullable(state.preferredStoreKey?.value),
            origin?.kind?.name ?: NULL_FIELD,
            encodeNullable(origin?.label),
            encodeNullable(origin?.contextId),
            state.drivingDistanceMetres?.toString() ?: NULL_FIELD,
            state.distanceBasis?.name ?: NULL_FIELD,
            state.fuelConsumptionMilliLitresPer100Km?.toString() ?: NULL_FIELD,
            fuel?.minorUnits?.toString() ?: NULL_FIELD,
            fuel?.currencyCode ?: NULL_FIELD,
            fuel?.fractionDigits?.toString() ?: NULL_FIELD,
            state.tolls?.minorUnits?.toString() ?: NULL_FIELD,
            state.tolls?.currencyCode ?: NULL_FIELD,
            state.tolls?.fractionDigits?.toString() ?: NULL_FIELD,
            state.parking?.minorUnits?.toString() ?: NULL_FIELD,
            state.parking?.currencyCode ?: NULL_FIELD,
            state.parking?.fractionDigits?.toString() ?: NULL_FIELD
        )
        val bytes = (listOf(TRIP_MAGIC, "1") + encodedFields).joinToString("|").toByteArray(StandardCharsets.US_ASCII)
        return bytes.takeIf { it.size <= MAX_TRIP_BYTES }
    }

    fun decode(bytes: ByteArray): TripPlannerCodecResult {
        if (bytes.size > MAX_TRIP_BYTES) return failure(TripPlannerCodecIssue.INPUT_TOO_LARGE)
        if (bytes.any { (it.toInt() and 0xff) > 0x7f }) return failure(TripPlannerCodecIssue.MALFORMED_INPUT)
        return try {
            val parts = String(bytes, StandardCharsets.US_ASCII).split('|')
            if (parts.size != 24 || parts[0] != TRIP_MAGIC || parts[1] != "1") {
                return failure(TripPlannerCodecIssue.INVALID_HEADER)
            }
            val destinationKey = decodeNullable(parts[2], 256)
            val destination =
                if (destinationKey == null) {
                    if (listOf(parts[3], parts[4], parts[5], parts[6], parts[7]).any { it != NULL_FIELD }) {
                        throw CodecParseFailure()
                    }
                    null
                } else {
                    TripStoreSelection(
                        storeKey = ShoppingStoreKey(destinationKey),
                        displayName = decodeRequired(parts[3], MAX_TRIP_TEXT),
                        addressLabel = decodeNullable(parts[4], MAX_TRIP_TEXT),
                        sourceLabel = decodeRequired(parts[5], MAX_TRIP_TEXT),
                        latitudeE7 = parseNullableInt(parts[6]),
                        longitudeE7 = parseNullableInt(parts[7])
                    )
                }
            val preferred = decodeNullable(parts[8], 256)?.let(::ShoppingStoreKey)
            val originKind = parseNullableEnum<TripOriginKind>(parts[9])
            val origin =
                originKind?.let {
                    TripOriginReference(
                        kind = it,
                        label = decodeRequired(parts[10], MAX_TRIP_TEXT),
                        contextId = decodeNullable(parts[11], 240)
                    )
                }
            if (originKind == null && (parts[10] != NULL_FIELD || parts[11] != NULL_FIELD)) {
                throw CodecParseFailure()
            }
            val distance = parseNullableLong(parts[12], positive = false)
            val basis = parseNullableEnum<TripDistanceBasis>(parts[13])
            val consumption = parseNullableLong(parts[14], positive = true)
            val fuel = decodeMoney(parts, 15)
            val tolls = decodeMoney(parts, 18)
            val parking = decodeMoney(parts, 21)
            TripPlannerCodecResult(
                TripPlannerState(
                    destination = destination,
                    preferredStoreKey = preferred,
                    origin = origin,
                    drivingDistanceMetres = distance,
                    distanceBasis = basis,
                    fuelConsumptionMilliLitresPer100Km = consumption,
                    fuelPrice = fuel,
                    tolls = tolls,
                    parking = parking
                )
            )
        } catch (_: IllegalArgumentException) {
            failure(TripPlannerCodecIssue.MALFORMED_INPUT)
        } catch (_: ArithmeticException) {
            failure(TripPlannerCodecIssue.MALFORMED_INPUT)
        } catch (_: CodecParseFailure) {
            failure(TripPlannerCodecIssue.MALFORMED_INPUT)
        }
    }

    private fun decodeMoney(parts: List<String>, index: Int): Money? {
        val minor = parseNullableLong(parts[index], positive = false)
        val currency = parseNullableCurrency(parts[index + 1])
        val fraction = parseNullableLong(parts[index + 2], positive = false)
        if ((minor == null) != (currency == null) || (minor == null) != (fraction == null)) {
            throw CodecParseFailure()
        }
        return if (minor == null) null else Money(minor, requireNotNull(currency), requireNotNull(fraction).toInt())
    }

    private fun encodeNullable(value: String?): String =
        value?.let(::encodeField) ?: NULL_FIELD

    private fun encodeField(value: String): String =
        value.toByteArray(StandardCharsets.UTF_8).joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

    private fun decodeRequired(value: String, maxLength: Int): String =
        decodeNullable(value, maxLength) ?: throw CodecParseFailure()

    private fun decodeNullable(value: String, maxLength: Int): String? {
        if (value == NULL_FIELD) return null
        if (value.isEmpty() || value.length % 2 != 0 || value.length > maxLength * 8) throw CodecParseFailure()
        val bytes = ByteArray(value.length / 2)
        (value.indices step 2).forEach { index ->
            val high = HEX_DIGITS.indexOf(value[index])
            val low = HEX_DIGITS.indexOf(value[index + 1])
            if (high < 0 || low < 0) throw CodecParseFailure()
            bytes[index / 2] = ((high shl 4) or low).toByte()
        }
        val decoded =
            try {
                StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString()
            } catch (_: CharacterCodingException) {
                throw CodecParseFailure()
            }
        if (decoded.isBlank() || decoded.length > maxLength) throw CodecParseFailure()
        return decoded
    }

    private fun parseNullableLong(value: String, positive: Boolean): Long? {
        if (value == NULL_FIELD) return null
        val pattern = if (positive) "[1-9]\\d*" else "0|[1-9]\\d*"
        if (!value.matches(Regex(pattern))) throw CodecParseFailure()
        return value.toLongOrNull() ?: throw CodecParseFailure()
    }

    private fun parseNullableInt(value: String): Int? =
        parseNullableSignedLong(value)?.let { valueLong ->
            if (valueLong !in Int.MIN_VALUE..Int.MAX_VALUE) throw CodecParseFailure()
            valueLong.toInt()
        }

    private fun parseNullableSignedLong(value: String): Long? {
        if (value == NULL_FIELD) return null
        if (!value.matches(Regex("-?(?:0|[1-9]\\d*)"))) throw CodecParseFailure()
        return value.toLongOrNull() ?: throw CodecParseFailure()
    }

    private fun parseNullableCurrency(value: String): String? {
        if (value == NULL_FIELD) return null
        if (!value.matches(Regex("[A-Z]{3}"))) throw CodecParseFailure()
        return value
    }

    private inline fun <reified T : Enum<T>> parseNullableEnum(value: String): T? =
        if (value == NULL_FIELD) null else try {
            enumValueOf<T>(value)
        } catch (_: IllegalArgumentException) {
            throw CodecParseFailure()
        }

    private fun failure(issue: TripPlannerCodecIssue) = TripPlannerCodecResult(null, issue)

    private class CodecParseFailure : RuntimeException()
    private const val HEX_DIGITS = "0123456789abcdef"
}

internal enum class TripPlannerStoreIssue {
    READ_FAILED,
    STORED_DATA_TOO_LARGE,
    STORED_DATA_INVALID,
    WRITE_FAILED
}

internal data class TripPlannerLoadResult(
    val state: TripPlannerState?,
    val issue: TripPlannerStoreIssue? = null,
    val codecIssue: TripPlannerCodecIssue? = null
) {
    init {
        require((state != null) == (issue == null))
    }

    val accepted: Boolean
        get() = state != null
}

internal data class TripPlannerMutationResult(
    val state: TripPlannerState?,
    val issue: TripPlannerStoreIssue? = null,
    val codecIssue: TripPlannerCodecIssue? = null
) {
    init {
        require((state != null) == (issue == null))
    }

    val accepted: Boolean
        get() = state != null
}

internal interface TripPlannerStore {
    fun load(): TripPlannerLoadResult
    fun replace(state: TripPlannerState): TripPlannerMutationResult
    fun clear(): TripPlannerMutationResult
}

internal data class TripPlannerRawReadResult(
    val bytes: ByteArray?,
    val found: Boolean,
    val issue: TripPlannerStoreIssue? = null
)

internal interface TripPlannerByteStorage {
    fun read(maxBytes: Int): TripPlannerRawReadResult
    fun replace(bytes: ByteArray): Boolean
    fun delete(): Boolean
}

private class AndroidTripPlannerByteStorage(context: Context) : TripPlannerByteStorage {
    private val atomicFile = AtomicFile(File(context.filesDir, TRIP_FILE_NAME))

    override fun read(maxBytes: Int): TripPlannerRawReadResult {
        val input =
            try {
                atomicFile.openRead()
            } catch (_: FileNotFoundException) {
                return TripPlannerRawReadResult(null, atomicFile.baseFile.exists(), if (atomicFile.baseFile.exists()) TripPlannerStoreIssue.READ_FAILED else null)
            } catch (_: Exception) {
                return TripPlannerRawReadResult(null, atomicFile.baseFile.exists(), TripPlannerStoreIssue.READ_FAILED)
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
                    if (total > maxBytes) return TripPlannerRawReadResult(null, true, TripPlannerStoreIssue.STORED_DATA_TOO_LARGE)
                    output.write(buffer, 0, read)
                }
                TripPlannerRawReadResult(output.toByteArray(), true)
            }
        } catch (_: Exception) {
            TripPlannerRawReadResult(null, true, TripPlannerStoreIssue.READ_FAILED)
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

    override fun delete(): Boolean = runCatching { atomicFile.delete(); true }.getOrDefault(false)
}

/** Device-private, atomic, fail-closed trip scenario storage. */
internal class AndroidTripPlannerStore internal constructor(
    private val storage: TripPlannerByteStorage
) : TripPlannerStore {
    constructor(context: Context) : this(AndroidTripPlannerByteStorage(context.applicationContext ?: context))

    @Synchronized
    override fun load(): TripPlannerLoadResult {
        val raw = storage.read(MAX_TRIP_BYTES)
        raw.issue?.let { return TripPlannerLoadResult(null, it) }
        if (!raw.found) return TripPlannerLoadResult(TripPlannerState())
        val decoded = TripPlannerCodec.decode(requireNotNull(raw.bytes))
        return if (decoded.accepted) {
            TripPlannerLoadResult(requireNotNull(decoded.state))
        } else {
            TripPlannerLoadResult(null, TripPlannerStoreIssue.STORED_DATA_INVALID, decoded.issue)
        }
    }

    @Synchronized
    override fun replace(state: TripPlannerState): TripPlannerMutationResult {
        val loaded = load()
        if (!loaded.accepted) {
            return TripPlannerMutationResult(null, loaded.issue, loaded.codecIssue)
        }
        val encoded = TripPlannerCodec.encode(state)
            ?: return TripPlannerMutationResult(null, TripPlannerStoreIssue.STORED_DATA_INVALID, TripPlannerCodecIssue.OUTPUT_TOO_LARGE)
        if (!storage.replace(encoded)) return TripPlannerMutationResult(null, TripPlannerStoreIssue.WRITE_FAILED)
        return TripPlannerMutationResult(state)
    }

    @Synchronized
    override fun clear(): TripPlannerMutationResult {
        if (!storage.delete()) return TripPlannerMutationResult(null, TripPlannerStoreIssue.WRITE_FAILED)
        return TripPlannerMutationResult(TripPlannerState())
    }
}
