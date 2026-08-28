package com.valuepilot.app

import com.valuepilot.core.AvailabilityEvidence
import com.valuepilot.core.EvidenceChannel
import com.valuepilot.core.EvidenceClaimKind
import com.valuepilot.core.EvidenceEnvironment
import com.valuepilot.core.EvidenceProvider
import com.valuepilot.core.EvidenceProviderId
import com.valuepilot.core.ProductObservation
import com.valuepilot.core.ProductObservationId
import com.valuepilot.core.ShoppingEvidence
import com.valuepilot.core.ShoppingSource
import com.valuepilot.core.ShoppingSourceId
import com.valuepilot.core.SourceProductIdentity
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

/**
 * Network-free representation of one already-decoded Open Prices export row.
 *
 * This is deliberately not an API client and performs no I/O. A future import
 * boundary may decode Parquet/JSONL into this type, but it must preserve the
 * source observation time rather than substituting download/import time.
 */
data class OpenPricesImportedRow(
    val priceId: String,
    val productCode: String,
    val productName: String,
    val quantityText: String,
    val priceText: String,
    val currencyCode: String,
    val countryCode: String,
    val locationId: String,
    val locationName: String,
    val locationKind: OpenPricesLocationKind,
    val observedAtEpochMillis: Long,
    val proofId: String?,
    val proofType: OpenPricesProofType
)

enum class OpenPricesProofType {
    RECEIPT,
    PRICE_TAG,
    OTHER
}

enum class OpenPricesLocationKind {
    PHYSICAL_STORE,
    ONLINE,
    UNKNOWN
}

enum class OpenPricesImportFailure {
    INVALID_PRICE_ID,
    INVALID_GTIN,
    INVALID_PRODUCT_NAME,
    INVALID_QUANTITY,
    INVALID_PRICE,
    NON_CAD_CURRENCY,
    NON_CANADIAN_LOCATION,
    INVALID_LOCATION,
    UNSUPPORTED_LOCATION_KIND,
    INVALID_OBSERVATION_TIME,
    MISSING_PROOF,
    UNSUPPORTED_PROOF
}

data class OpenPricesImportResult(
    val evidence: ShoppingEvidence?,
    val failures: Set<OpenPricesImportFailure>
) {
    init {
        require((evidence != null) == failures.isEmpty())
    }

    val accepted: Boolean
        get() = evidence != null
}

/**
 * Conservative first-pass mapper for the Open Prices 5D validation rail.
 *
 * Only Canadian, CAD, physical-store, proof-backed rows with a checksum-valid
 * GTIN and explicit deterministic quantity are admitted. This intentionally
 * rejects useful-but-weaker rows until their semantics are designed.
 *
 * Admitted evidence still passes through the permanent EvidenceAcceptance
 * boundary. In particular, an old shelf/receipt observation remains old: this
 * mapper never turns import time into observation time and never infers stock.
 */
object OpenPricesImportedEvidenceMapper {

    private val provider =
        EvidenceProvider(
            id = EvidenceProviderId("open-prices"),
            displayName = "Open Prices"
        )

    fun map(row: OpenPricesImportedRow): OpenPricesImportResult {
        val failures = linkedSetOf<OpenPricesImportFailure>()

        val priceId = row.priceId.trim()
        if (!PRICE_ID.matches(priceId)) {
            failures += OpenPricesImportFailure.INVALID_PRICE_ID
        }

        val gtin = row.productCode.trim()
        if (!isValidGtin(gtin)) {
            failures += OpenPricesImportFailure.INVALID_GTIN
        }

        val productName = singleLine(row.productName, 160)
        if (productName == null || productName.length < 2) {
            failures += OpenPricesImportFailure.INVALID_PRODUCT_NAME
        }

        val quantityText = singleLine(row.quantityText, 80)
        val parsedQuantity =
            quantityText?.let(ValueEngine::quantity)
        if (
            quantityText == null ||
            parsedQuantity == null ||
            parsedQuantity.confidence < MIN_EXPLICIT_QUANTITY_CONFIDENCE
        ) {
            failures += OpenPricesImportFailure.INVALID_QUANTITY
        }

        val canonicalPrice = canonicalCadPrice(row.priceText)
        if (canonicalPrice == null) {
            failures += OpenPricesImportFailure.INVALID_PRICE
        }

        if (row.currencyCode.trim().uppercase(Locale.ROOT) != "CAD") {
            failures += OpenPricesImportFailure.NON_CAD_CURRENCY
        }

        if (row.countryCode.trim().uppercase(Locale.ROOT) != "CA") {
            failures += OpenPricesImportFailure.NON_CANADIAN_LOCATION
        }

        val locationId = row.locationId.trim()
        val locationName = singleLine(row.locationName, 160)
        if (
            !LOCATION_ID.matches(locationId) ||
            locationName == null ||
            locationName.length < 2
        ) {
            failures += OpenPricesImportFailure.INVALID_LOCATION
        }

        if (row.locationKind != OpenPricesLocationKind.PHYSICAL_STORE) {
            failures += OpenPricesImportFailure.UNSUPPORTED_LOCATION_KIND
        }

        if (row.observedAtEpochMillis <= 0L) {
            failures += OpenPricesImportFailure.INVALID_OBSERVATION_TIME
        }

        val proofId = row.proofId?.trim()
        if (proofId.isNullOrBlank() || proofId.length > 96) {
            failures += OpenPricesImportFailure.MISSING_PROOF
        }

        if (
            row.proofType != OpenPricesProofType.RECEIPT &&
            row.proofType != OpenPricesProofType.PRICE_TAG
        ) {
            failures += OpenPricesImportFailure.UNSUPPORTED_PROOF
        }

        if (failures.isNotEmpty()) {
            return OpenPricesImportResult(
                evidence = null,
                failures = failures
            )
        }

        val safeName = requireNotNull(productName)
        val safeQuantity = requireNotNull(quantityText)
        val safePrice = requireNotNull(canonicalPrice)

        val sourceId = "open-prices-location-$locationId"
        val providerItemId = "open-prices-price-$priceId"

        val evidence =
            ShoppingEvidence(
                observation =
                    ProductObservation(
                        id = ProductObservationId(providerItemId),
                        sourceId = sourceId,
                        rawText =
                            "$safeName\n$safeQuantity\n$safePrice CAD",
                        observedAtEpochMillis =
                            row.observedAtEpochMillis
                    ),
                provider = provider,
                source =
                    ShoppingSource(
                        id = ShoppingSourceId(sourceId),
                        displayName = requireNotNull(locationName)
                    ),
                environment = EvidenceEnvironment.REAL_WORLD,
                channel = EvidenceChannel.IMPORTED,
                observationClaimKind =
                    EvidenceClaimKind.DIRECT_OBSERVATION,
                sourceProductIdentity =
                    SourceProductIdentity(
                        providerItemId = providerItemId,
                        gtin = gtin
                    ),
                availability = AvailabilityEvidence()
            )

        return OpenPricesImportResult(
            evidence = evidence,
            failures = emptySet()
        )
    }

    /** GS1 modulo-10 check digit validation for GTIN-8/12/13/14. */
    internal fun isValidGtin(value: String): Boolean {
        if (
            value.length !in setOf(8, 12, 13, 14) ||
            !value.all(Char::isDigit)
        ) {
            return false
        }

        val body = value.dropLast(1)
        val suppliedCheckDigit = value.last().digitToInt()
        var sum = 0
        var weight = 3

        for (index in body.indices.reversed()) {
            sum += body[index].digitToInt() * weight
            weight = if (weight == 3) 1 else 3
        }

        val expectedCheckDigit = (10 - (sum % 10)) % 10
        return suppliedCheckDigit == expectedCheckDigit
    }

    private fun singleLine(value: String, maxLength: Int): String? {
        val trimmed = value.trim()
        if (
            trimmed.isBlank() ||
            trimmed.length > maxLength ||
            trimmed.any { it == '\n' || it == '\r' }
        ) {
            return null
        }
        return trimmed
    }

    private fun canonicalCadPrice(value: String): String? {
        val trimmed = value.trim()
        if (!PRICE.matches(trimmed)) {
            return null
        }

        val amount =
            runCatching {
                BigDecimal(trimmed)
            }.getOrNull() ?: return null

        if (amount <= BigDecimal.ZERO) {
            return null
        }

        val stripped = amount.stripTrailingZeros()
        if (stripped.scale() > 2) {
            return null
        }

        return runCatching {
            amount
                .setScale(2, RoundingMode.UNNECESSARY)
                .toPlainString()
        }.getOrNull()
    }

    private val PRICE_ID = Regex("\\d{1,32}")
    private val LOCATION_ID = Regex("[A-Za-z0-9._:-]{1,96}")
    private val PRICE = Regex("\\d{1,6}(?:\\.\\d{1,6})?")

    private const val MIN_EXPLICIT_QUANTITY_CONFIDENCE = 0.90
}
