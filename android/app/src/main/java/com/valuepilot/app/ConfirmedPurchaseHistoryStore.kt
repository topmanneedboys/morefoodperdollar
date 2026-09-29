package com.valuepilot.app

import android.content.Context
import android.util.AtomicFile
import com.valuepilot.core.ConfirmedPurchase
import com.valuepilot.core.ConfirmedPurchaseIdentityOrigin
import com.valuepilot.core.ConfirmedPurchaseStore
import com.valuepilot.core.Money
import com.valuepilot.core.NormalizedQuantity
import com.valuepilot.core.PracticalShoppingStoreIdentityScope
import com.valuepilot.core.BaseUnit
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets

internal const val MAX_CONFIRMED_PURCHASES = 256
private const val MAX_CONFIRMED_PURCHASES_ENCODED_BYTES = 262_144
private const val HISTORY_FILE_NAME = "confirmed-purchase-history.v1"
private const val HISTORY_MAGIC = "VALUEPILOT_CONFIRMED_PURCHASES"
private const val NULL_FIELD = "~"
private const val READ_BUFFER_BYTES = 8_192

internal data class ConfirmedPurchaseHistoryState(
    val purchases: List<ConfirmedPurchase>
) {
    init {
        require(purchases.size <= MAX_CONFIRMED_PURCHASES)
        require(purchases.map { it.purchaseId }.distinct().size == purchases.size)
    }

    companion object {
        fun empty(): ConfirmedPurchaseHistoryState = ConfirmedPurchaseHistoryState(emptyList())
    }
}

internal enum class ConfirmedPurchaseHistoryCodecIssue {
    FIELD_TOO_LARGE,
    OUTPUT_TOO_LARGE,
    INPUT_TOO_LARGE,
    TOO_MANY_RECORDS,
    INVALID_HEADER,
    MALFORMED_INPUT,
    DUPLICATE_PURCHASE_ID
}

internal data class ConfirmedPurchaseHistoryEncodeResult(
    val bytes: ByteArray?,
    val issue: ConfirmedPurchaseHistoryCodecIssue? = null
) {
    init {
        require((bytes != null) == (issue == null))
    }

    val accepted: Boolean
        get() = bytes != null
}

internal data class ConfirmedPurchaseHistoryDecodeResult(
    val state: ConfirmedPurchaseHistoryState?,
    val issue: ConfirmedPurchaseHistoryCodecIssue? = null
) {
    init {
        require((state != null) == (issue == null))
    }

    val accepted: Boolean
        get() = state != null
}

/** Deterministic bounded local format; no provider or current-offer authority is stored. */
internal object ConfirmedPurchaseHistoryCodec {

    val maximumEncodedBytes: Int
        get() = MAX_CONFIRMED_PURCHASES_ENCODED_BYTES

    fun encode(state: ConfirmedPurchaseHistoryState): ConfirmedPurchaseHistoryEncodeResult {
        val ordered =
            state.purchases.sortedWith(
                compareByDescending<ConfirmedPurchase> { it.purchaseDateIso ?: "" }
                    .thenBy { it.purchaseId }
            )
        if (ordered.any { !fieldsFit(it) }) {
            return ConfirmedPurchaseHistoryEncodeResult(
                bytes = null,
                issue = ConfirmedPurchaseHistoryCodecIssue.FIELD_TOO_LARGE
            )
        }

        val lines = ArrayList<String>(ordered.size + 1)
        lines += "$HISTORY_MAGIC|1"
        ordered.forEach { purchase ->
            val quantity = purchase.quantity
            val amountPaid = purchase.amountPaid
            val store = purchase.store
            val scope = store?.identityScope
            lines += listOf(
                "P",
                encodeField(purchase.purchaseId),
                encodeField(purchase.productIdentityKey),
                purchase.identityOrigin.name,
                encodeField(purchase.displayName),
                encodeNullable(purchase.brand),
                encodeNullable(purchase.packageQuantityLabel),
                quantity?.amountMicros?.toString() ?: NULL_FIELD,
                quantity?.unit?.name ?: NULL_FIELD,
                amountPaid?.minorUnits?.toString() ?: NULL_FIELD,
                amountPaid?.currencyCode ?: NULL_FIELD,
                amountPaid?.fractionDigits?.toString() ?: NULL_FIELD,
                encodeNullable(purchase.purchaseDateIso),
                encodeNullable(store?.displayName),
                encodeNullable(scope?.merchantKey),
                encodeNullable(scope?.locationKey),
                encodeNullable(scope?.commerceChannelKey)
            ).joinToString("|")
        }

        val bytes = lines.joinToString("\n").toByteArray(StandardCharsets.US_ASCII)
        if (bytes.size > MAX_CONFIRMED_PURCHASES_ENCODED_BYTES) {
            return ConfirmedPurchaseHistoryEncodeResult(
                bytes = null,
                issue = ConfirmedPurchaseHistoryCodecIssue.OUTPUT_TOO_LARGE
            )
        }
        return ConfirmedPurchaseHistoryEncodeResult(bytes = bytes)
    }

    fun decode(bytes: ByteArray): ConfirmedPurchaseHistoryDecodeResult {
        if (bytes.size > MAX_CONFIRMED_PURCHASES_ENCODED_BYTES) {
            return failure(ConfirmedPurchaseHistoryCodecIssue.INPUT_TOO_LARGE)
        }
        if (bytes.any { (it.toInt() and 0xff) > 0x7f }) {
            return failure(ConfirmedPurchaseHistoryCodecIssue.MALFORMED_INPUT)
        }
        val lines = String(bytes, StandardCharsets.US_ASCII).split('\n')
        val header = lines.firstOrNull()?.split('|').orEmpty()
        if (header.size != 2 || header[0] != HISTORY_MAGIC || header[1] != "1") {
            return failure(ConfirmedPurchaseHistoryCodecIssue.INVALID_HEADER)
        }
        if (lines.size - 1 > MAX_CONFIRMED_PURCHASES) {
            return failure(ConfirmedPurchaseHistoryCodecIssue.TOO_MANY_RECORDS)
        }

        return try {
            val purchases = lines.drop(1).map { line -> decodePurchase(line.split('|')) }
            if (purchases.map { it.purchaseId }.distinct().size != purchases.size) {
                return failure(ConfirmedPurchaseHistoryCodecIssue.DUPLICATE_PURCHASE_ID)
            }
            ConfirmedPurchaseHistoryDecodeResult(ConfirmedPurchaseHistoryState(purchases))
        } catch (_: CodecParseFailure) {
            failure(ConfirmedPurchaseHistoryCodecIssue.MALFORMED_INPUT)
        } catch (_: IllegalArgumentException) {
            failure(ConfirmedPurchaseHistoryCodecIssue.MALFORMED_INPUT)
        } catch (_: ArithmeticException) {
            failure(ConfirmedPurchaseHistoryCodecIssue.MALFORMED_INPUT)
        }
    }

    private fun decodePurchase(parts: List<String>): ConfirmedPurchase {
        if (parts.size != 17 || parts[0] != "P") throw CodecParseFailure()
        val purchaseId = decodeRequired(parts[1], ConfirmedPurchase.MAX_PURCHASE_ID_LENGTH)
        val productIdentityKey = decodeRequired(parts[2], ConfirmedPurchase.MAX_PRODUCT_IDENTITY_KEY_LENGTH)
        val origin = parseEnum<ConfirmedPurchaseIdentityOrigin>(parts[3])
        val displayName = decodeRequired(parts[4], ConfirmedPurchase.MAX_DISPLAY_NAME_LENGTH)
        val brand = decodeNullable(parts[5], ConfirmedPurchase.MAX_BRAND_LENGTH)
        val packageQuantity = decodeNullable(parts[6], ConfirmedPurchase.MAX_PACKAGE_QUANTITY_LENGTH)

        val quantityAmount = parseNullableLong(parts[7], positive = true)
        val quantityUnit = parseNullableEnum<BaseUnit>(parts[8])
        if ((quantityAmount == null) != (quantityUnit == null)) throw CodecParseFailure()
        val quantity =
            if (quantityAmount == null) null else NormalizedQuantity(quantityAmount, requireNotNull(quantityUnit))

        val priceMinor = parseNullableLong(parts[9], positive = false)
        val priceCurrency = parseNullableCurrency(parts[10])
        val priceFraction = parseNullableLong(parts[11], positive = false)
        if ((priceMinor == null) != (priceCurrency == null) || (priceMinor == null) != (priceFraction == null)) {
            throw CodecParseFailure()
        }
        val amountPaid =
            if (priceMinor == null) null else Money(priceMinor, requireNotNull(priceCurrency), requireNotNull(priceFraction).toInt())

        val date = decodeNullable(parts[12], 10)
        if (date != null && !ConfirmedPurchase.isValidIsoDate(date)) throw CodecParseFailure()
        val storeDisplay = decodeNullable(parts[13], 240)
        val merchant = decodeNullable(parts[14], 240)
        val location = decodeNullable(parts[15], 240)
        val channel = decodeNullable(parts[16], 160)
        if ((merchant == null) != (location == null) || (merchant == null) != (channel == null)) {
            throw CodecParseFailure()
        }
        val scope =
            if (merchant == null) null
            else PracticalShoppingStoreIdentityScope(
                merchantKey = merchant,
                locationKey = location,
                commerceChannelKey = requireNotNull(channel)
            )
        val store =
            if (storeDisplay == null && scope == null) null
            else ConfirmedPurchaseStore(displayName = storeDisplay, identityScope = scope)

        return ConfirmedPurchase(
            purchaseId = purchaseId,
            productIdentityKey = productIdentityKey,
            identityOrigin = origin,
            displayName = displayName,
            brand = brand,
            packageQuantityLabel = packageQuantity,
            quantity = quantity,
            amountPaid = amountPaid,
            purchaseDateIso = date,
            store = store
        )
    }

    private fun fieldsFit(purchase: ConfirmedPurchase): Boolean =
        purchase.purchaseId.toByteArray(StandardCharsets.UTF_8).size <= ConfirmedPurchase.MAX_PURCHASE_ID_LENGTH * 4 &&
            purchase.productIdentityKey.toByteArray(StandardCharsets.UTF_8).size <= ConfirmedPurchase.MAX_PRODUCT_IDENTITY_KEY_LENGTH * 4 &&
            purchase.displayName.toByteArray(StandardCharsets.UTF_8).size <= ConfirmedPurchase.MAX_DISPLAY_NAME_LENGTH * 4

    private fun encodeNullable(value: String?): String = value?.let(::encodeField) ?: NULL_FIELD

    private fun encodeField(value: String): String =
        value.toByteArray(StandardCharsets.UTF_8).joinToString(separator = "") { byte ->
            "%02x".format(byte.toInt() and 0xff)
        }

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
        val decoded = String(bytes, StandardCharsets.UTF_8)
        if (decoded.length > maxLength || decoded.isBlank()) throw CodecParseFailure()
        return decoded
    }

    private fun parseNullableLong(value: String, positive: Boolean): Long? {
        if (value == NULL_FIELD) return null
        val pattern = if (positive) "[1-9]\\d*" else "0|[1-9]\\d*"
        if (!value.matches(Regex(pattern))) throw CodecParseFailure()
        return value.toLongOrNull() ?: throw CodecParseFailure()
    }

    private fun parseNullableCurrency(value: String): String? {
        if (value == NULL_FIELD) return null
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

    private fun failure(issue: ConfirmedPurchaseHistoryCodecIssue) =
        ConfirmedPurchaseHistoryDecodeResult(state = null, issue = issue)

    private class CodecParseFailure : RuntimeException()
    private const val HEX_DIGITS = "0123456789abcdef"
}

internal enum class ConfirmedPurchaseHistoryStoreIssue {
    READ_FAILED,
    STORED_DATA_TOO_LARGE,
    STORED_DATA_INVALID,
    ENCODE_REJECTED,
    WRITE_FAILED,
    DELETE_FAILED,
    PURCHASE_NOT_FOUND,
    DUPLICATE_PURCHASE_ID
}

internal data class ConfirmedPurchaseHistoryLoadResult(
    val state: ConfirmedPurchaseHistoryState?,
    val foundStoredDocument: Boolean,
    val issue: ConfirmedPurchaseHistoryStoreIssue? = null,
    val codecIssue: ConfirmedPurchaseHistoryCodecIssue? = null
) {
    init {
        require((state != null) == (issue == null))
    }

    val accepted: Boolean
        get() = state != null
}

internal data class ConfirmedPurchaseHistoryMutationResult(
    val state: ConfirmedPurchaseHistoryState?,
    val issue: ConfirmedPurchaseHistoryStoreIssue? = null,
    val codecIssue: ConfirmedPurchaseHistoryCodecIssue? = null
) {
    init {
        require((state != null) == (issue == null))
    }

    val accepted: Boolean
        get() = state != null
}

internal interface ConfirmedPurchaseHistoryStore {
    fun load(): ConfirmedPurchaseHistoryLoadResult
    fun append(purchase: ConfirmedPurchase): ConfirmedPurchaseHistoryMutationResult
    fun replace(purchase: ConfirmedPurchase): ConfirmedPurchaseHistoryMutationResult
    fun remove(purchaseId: String): ConfirmedPurchaseHistoryMutationResult
    fun clear(): ConfirmedPurchaseHistoryMutationResult
}

internal data class ConfirmedPurchaseRawReadResult(
    val bytes: ByteArray?,
    val found: Boolean,
    val issue: ConfirmedPurchaseHistoryStoreIssue? = null
)

internal interface ConfirmedPurchaseByteStorage {
    fun read(maxBytes: Int): ConfirmedPurchaseRawReadResult
    fun replace(bytes: ByteArray): Boolean
    fun delete(): Boolean
}

private class AndroidConfirmedPurchaseByteStorage(context: Context) : ConfirmedPurchaseByteStorage {
    private val atomicFile = AtomicFile(File(context.filesDir, HISTORY_FILE_NAME))

    override fun read(maxBytes: Int): ConfirmedPurchaseRawReadResult {
        val input =
            try {
                atomicFile.openRead()
            } catch (_: FileNotFoundException) {
                return ConfirmedPurchaseRawReadResult(null, atomicFile.baseFile.exists(), if (atomicFile.baseFile.exists()) ConfirmedPurchaseHistoryStoreIssue.READ_FAILED else null)
            } catch (_: Exception) {
                return ConfirmedPurchaseRawReadResult(null, atomicFile.baseFile.exists(), ConfirmedPurchaseHistoryStoreIssue.READ_FAILED)
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
                        return ConfirmedPurchaseRawReadResult(null, true, ConfirmedPurchaseHistoryStoreIssue.STORED_DATA_TOO_LARGE)
                    }
                    output.write(buffer, 0, read)
                }
                ConfirmedPurchaseRawReadResult(output.toByteArray(), true)
            }
        } catch (_: Exception) {
            ConfirmedPurchaseRawReadResult(null, true, ConfirmedPurchaseHistoryStoreIssue.READ_FAILED)
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

    override fun delete(): Boolean = runCatching {
        atomicFile.delete()
        true
    }.getOrDefault(false)
}

/** Atomic device-only storage. Corrupt data blocks mutation instead of being silently replaced. */
internal class AndroidConfirmedPurchaseHistoryStore internal constructor(
    private val storage: ConfirmedPurchaseByteStorage
) : ConfirmedPurchaseHistoryStore {
    constructor(context: Context) : this(AndroidConfirmedPurchaseByteStorage(context.applicationContext ?: context))

    @Synchronized
    override fun load(): ConfirmedPurchaseHistoryLoadResult {
        val raw = storage.read(ConfirmedPurchaseHistoryCodec.maximumEncodedBytes)
        raw.issue?.let { issue ->
            return ConfirmedPurchaseHistoryLoadResult(null, raw.found, issue)
        }
        if (!raw.found) return ConfirmedPurchaseHistoryLoadResult(ConfirmedPurchaseHistoryState.empty(), false)
        val decoded = ConfirmedPurchaseHistoryCodec.decode(requireNotNull(raw.bytes))
        if (!decoded.accepted) {
            return ConfirmedPurchaseHistoryLoadResult(
                state = null,
                foundStoredDocument = true,
                issue = ConfirmedPurchaseHistoryStoreIssue.STORED_DATA_INVALID,
                codecIssue = decoded.issue
            )
        }
        return ConfirmedPurchaseHistoryLoadResult(requireNotNull(decoded.state), true)
    }

    @Synchronized
    override fun append(purchase: ConfirmedPurchase): ConfirmedPurchaseHistoryMutationResult {
        val loaded = load()
        if (!loaded.accepted) return failure(loaded.issue, loaded.codecIssue)
        val current = requireNotNull(loaded.state)
        if (current.purchases.any { it.purchaseId == purchase.purchaseId }) {
            return ConfirmedPurchaseHistoryMutationResult(null, ConfirmedPurchaseHistoryStoreIssue.DUPLICATE_PURCHASE_ID)
        }
        val bounded =
            (current.purchases + purchase)
                .sortedWith(
                    compareByDescending<ConfirmedPurchase> { it.purchaseDateIso ?: "" }
                        .thenBy { it.purchaseId }
                )
                .take(MAX_CONFIRMED_PURCHASES)
        return replaceState(ConfirmedPurchaseHistoryState(bounded))
    }

    @Synchronized
    override fun replace(purchase: ConfirmedPurchase): ConfirmedPurchaseHistoryMutationResult {
        val loaded = load()
        if (!loaded.accepted) return failure(loaded.issue, loaded.codecIssue)
        val current = requireNotNull(loaded.state)
        if (current.purchases.none { it.purchaseId == purchase.purchaseId }) {
            return ConfirmedPurchaseHistoryMutationResult(null, ConfirmedPurchaseHistoryStoreIssue.PURCHASE_NOT_FOUND)
        }
        return replaceState(ConfirmedPurchaseHistoryState(current.purchases.map { if (it.purchaseId == purchase.purchaseId) purchase else it }))
    }

    @Synchronized
    override fun remove(purchaseId: String): ConfirmedPurchaseHistoryMutationResult {
        val loaded = load()
        if (!loaded.accepted) return failure(loaded.issue, loaded.codecIssue)
        val current = requireNotNull(loaded.state)
        if (current.purchases.none { it.purchaseId == purchaseId }) {
            return ConfirmedPurchaseHistoryMutationResult(null, ConfirmedPurchaseHistoryStoreIssue.PURCHASE_NOT_FOUND)
        }
        return replaceState(ConfirmedPurchaseHistoryState(current.purchases.filterNot { it.purchaseId == purchaseId }))
    }

    @Synchronized
    override fun clear(): ConfirmedPurchaseHistoryMutationResult =
        if (storage.delete()) ConfirmedPurchaseHistoryMutationResult(ConfirmedPurchaseHistoryState.empty())
        else ConfirmedPurchaseHistoryMutationResult(null, ConfirmedPurchaseHistoryStoreIssue.DELETE_FAILED)

    private fun replaceState(state: ConfirmedPurchaseHistoryState): ConfirmedPurchaseHistoryMutationResult {
        val encoded = ConfirmedPurchaseHistoryCodec.encode(state)
        if (!encoded.accepted) return ConfirmedPurchaseHistoryMutationResult(null, ConfirmedPurchaseHistoryStoreIssue.ENCODE_REJECTED, encoded.issue)
        if (!storage.replace(requireNotNull(encoded.bytes))) return ConfirmedPurchaseHistoryMutationResult(null, ConfirmedPurchaseHistoryStoreIssue.WRITE_FAILED)
        return ConfirmedPurchaseHistoryMutationResult(state)
    }

    private fun failure(issue: ConfirmedPurchaseHistoryStoreIssue?, codecIssue: ConfirmedPurchaseHistoryCodecIssue?) =
        ConfirmedPurchaseHistoryMutationResult(null, issue ?: ConfirmedPurchaseHistoryStoreIssue.STORED_DATA_INVALID, codecIssue)
}
