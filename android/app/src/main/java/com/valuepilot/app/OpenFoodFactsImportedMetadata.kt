package com.valuepilot.app

import com.valuepilot.core.BaseUnit
import com.valuepilot.core.EvidenceAuthorityClass
import com.valuepilot.core.EvidenceClaim
import com.valuepilot.core.EvidenceClaimDomain
import com.valuepilot.core.EvidenceClaimScope
import com.valuepilot.core.GtinValidation
import com.valuepilot.core.NormalizedQuantity
import java.math.BigDecimal
import java.util.Locale

/**
 * Network-free representation of the small Open Food Facts field subset that
 * ValuePilot needs for the first metadata-only validation step.
 *
 * productQuantity/productQuantityUnit correspond to Open Food Facts'
 * normalized whole-product quantity fields. The documented normalized unit is
 * g or ml. rawQuantity is retained only for provenance/cross-checking and is
 * never interpreted as a retailer price or availability claim.
 */
data class OpenFoodFactsImportedProduct(
    val code: String,
    val productName: String? = null,
    val brands: String? = null,
    val rawQuantity: String? = null,
    val productQuantity: String?,
    val productQuantityUnit: String?,
    val lastModifiedEpochSeconds: Long? = null
)

enum class OpenFoodFactsImportFailure {
    INVALID_GTIN,
    INVALID_PRODUCT_NAME,
    INVALID_BRANDS,
    MISSING_STRUCTURED_QUANTITY,
    INVALID_STRUCTURED_QUANTITY,
    UNSUPPORTED_STRUCTURED_UNIT,
    INCONSISTENT_RAW_QUANTITY,
    INVALID_MODIFICATION_TIME
}

data class OpenFoodFactsProductMetadata(
    val providerId: String,
    val gtin: String,
    val productName: String?,
    val brands: String?,
    val rawQuantity: String?,
    val normalizedQuantity: NormalizedQuantity,
    val sourceLastModifiedAtEpochMillis: Long?
)

data class OpenFoodFactsImportResult(
    val metadata: OpenFoodFactsProductMetadata?,
    val quantityClaim: EvidenceClaim?,
    val failures: Set<OpenFoodFactsImportFailure>
) {
    init {
        require((metadata != null) == (quantityClaim != null))
        require((metadata != null) == failures.isEmpty())
    }

    val accepted: Boolean
        get() = metadata != null
}

/**
 * Strict Open Food Facts product-metadata adapter.
 *
 * This deliberately emits only PACKAGE_QUANTITY evidence keyed by validated
 * GTIN. It cannot emit price, stock, promotion, retailer, or market-benchmark
 * claims. That separation prevents community product metadata from becoming a
 * retailer offer by accident.
 *
 * The normalized structured quantity is authoritative only as
 * SOURCE_ASSERTED_METADATA. When raw `quantity` is simple enough to parse
 * deterministically, it is cross-checked and disagreement fails closed.
 */
object OpenFoodFactsImportedMetadataMapper {

    const val PROVIDER_ID = "open-food-facts"

    fun map(row: OpenFoodFactsImportedProduct): OpenFoodFactsImportResult {
        val failures = linkedSetOf<OpenFoodFactsImportFailure>()

        val gtin = row.code.trim()
        if (!GtinValidation.isValid(gtin)) {
            failures += OpenFoodFactsImportFailure.INVALID_GTIN
        }

        val productName = sanitizeOptional(row.productName, 180)
        if (!row.productName.isNullOrBlank() && productName == null) {
            failures += OpenFoodFactsImportFailure.INVALID_PRODUCT_NAME
        }

        val brands = sanitizeOptional(row.brands, 240)
        if (!row.brands.isNullOrBlank() && brands == null) {
            failures += OpenFoodFactsImportFailure.INVALID_BRANDS
        }

        val quantityValue = row.productQuantity?.trim()
        val quantityUnit = row.productQuantityUnit?.trim()?.lowercase(Locale.ROOT)

        if (quantityValue.isNullOrBlank() || quantityUnit.isNullOrBlank()) {
            failures += OpenFoodFactsImportFailure.MISSING_STRUCTURED_QUANTITY
        }

        if (
            !quantityUnit.isNullOrBlank() &&
            quantityUnit != "g" &&
            quantityUnit != "ml"
        ) {
            failures += OpenFoodFactsImportFailure.UNSUPPORTED_STRUCTURED_UNIT
        }

        val normalizedQuantity =
            if (
                !quantityValue.isNullOrBlank() &&
                (quantityUnit == "g" || quantityUnit == "ml")
            ) {
                normalizedQuantity(quantityValue, quantityUnit)
            } else {
                null
            }

        if (
            !quantityValue.isNullOrBlank() &&
            (quantityUnit == "g" || quantityUnit == "ml") &&
            normalizedQuantity == null
        ) {
            failures += OpenFoodFactsImportFailure.INVALID_STRUCTURED_QUANTITY
        }

        val rawQuantity = sanitizeOptional(row.rawQuantity, 120)
        if (!row.rawQuantity.isNullOrBlank() && rawQuantity == null) {
            failures += OpenFoodFactsImportFailure.INCONSISTENT_RAW_QUANTITY
        }

        if (normalizedQuantity != null && rawQuantity != null) {
            val parsedRaw = parseSimpleDisplayedQuantity(rawQuantity)
            if (parsedRaw != null && parsedRaw != normalizedQuantity) {
                failures += OpenFoodFactsImportFailure.INCONSISTENT_RAW_QUANTITY
            }
        }

        val modifiedMillis =
            row.lastModifiedEpochSeconds?.let { seconds ->
                if (seconds <= 0L || seconds > Long.MAX_VALUE / 1_000L) {
                    failures += OpenFoodFactsImportFailure.INVALID_MODIFICATION_TIME
                    null
                } else {
                    seconds * 1_000L
                }
            }

        if (failures.isNotEmpty()) {
            return OpenFoodFactsImportResult(
                metadata = null,
                quantityClaim = null,
                failures = failures
            )
        }

        val safeQuantity = requireNotNull(normalizedQuantity)
        val productKey = "gtin:$gtin"
        val valueFingerprint =
            "${safeQuantity.unit.name}:${safeQuantity.amountMicros}"

        val metadata =
            OpenFoodFactsProductMetadata(
                providerId = PROVIDER_ID,
                gtin = gtin,
                productName = productName,
                brands = brands,
                rawQuantity = rawQuantity,
                normalizedQuantity = safeQuantity,
                sourceLastModifiedAtEpochMillis = modifiedMillis
            )

        val claim =
            EvidenceClaim(
                claimId = "$PROVIDER_ID:$gtin:package-quantity",
                domain = EvidenceClaimDomain.PACKAGE_QUANTITY,
                valueFingerprint = valueFingerprint,
                authority = EvidenceAuthorityClass.SOURCE_ASSERTED_METADATA,
                scope = EvidenceClaimScope(productKey = productKey),
                observedAtEpochMillis = modifiedMillis ?: 0L
            )

        return OpenFoodFactsImportResult(
            metadata = metadata,
            quantityClaim = claim,
            failures = emptySet()
        )
    }

    private fun normalizedQuantity(
        value: String,
        unit: String
    ): NormalizedQuantity? {
        val amount = parsePositiveDecimal(value) ?: return null
        if (amount > MAX_BASE_QUANTITY) return null

        val amountMicros =
            runCatching {
                amount
                    .movePointRight(6)
                    .longValueExact()
            }.getOrNull() ?: return null

        if (amountMicros <= 0L) return null

        val baseUnit =
            when (unit) {
                "g" -> BaseUnit.GRAM
                "ml" -> BaseUnit.MILLILITRE
                else -> return null
            }

        return NormalizedQuantity(
            amountMicros = amountMicros,
            unit = baseUnit
        )
    }

    /**
     * Cross-check only quantity strings with unambiguous mass/volume syntax.
     * Unsupported display syntax returns null and does not cause us to invent
     * an interpretation. Structured OFF fields remain the source assertion.
     */
    private fun parseSimpleDisplayedQuantity(
        value: String
    ): NormalizedQuantity? {
        val normalized = value
            .replace('\u00A0', ' ')
            .replace("℮", "")
            .trim()
            .lowercase(Locale.ROOT)
            .replace(Regex("\\s+"), " ")

        MULTIPACK.matchEntire(normalized)?.let { match ->
            val count = match.groupValues[1].toLongOrNull() ?: return null
            if (count !in 1L..1_000L) return null
            val each = parsePositiveDecimal(match.groupValues[2]) ?: return null
            val converted = convertDisplayed(each, match.groupValues[3]) ?: return null
            val totalMicros =
                runCatching {
                    Math.multiplyExact(converted.amountMicros, count)
                }.getOrNull() ?: return null
            return runCatching {
                NormalizedQuantity(totalMicros, converted.unit)
            }.getOrNull()
        }

        SIMPLE_QUANTITY.matchEntire(normalized)?.let { match ->
            val amount = parsePositiveDecimal(match.groupValues[1]) ?: return null
            return convertDisplayed(amount, match.groupValues[2])
        }

        return null
    }

    private fun convertDisplayed(
        amount: BigDecimal,
        unit: String
    ): NormalizedQuantity? {
        val (multiplier, baseUnit) =
            when (unit.lowercase(Locale.ROOT)) {
                "g" -> BigDecimal.ONE to BaseUnit.GRAM
                "kg" -> BigDecimal("1000") to BaseUnit.GRAM
                "ml" -> BigDecimal.ONE to BaseUnit.MILLILITRE
                "cl" -> BigDecimal("10") to BaseUnit.MILLILITRE
                "l" -> BigDecimal("1000") to BaseUnit.MILLILITRE
                else -> return null
            }

        val baseAmount = amount.multiply(multiplier)
        if (baseAmount <= BigDecimal.ZERO || baseAmount > MAX_BASE_QUANTITY) {
            return null
        }

        val micros =
            runCatching {
                baseAmount
                    .movePointRight(6)
                    .longValueExact()
            }.getOrNull() ?: return null

        return runCatching {
            NormalizedQuantity(micros, baseUnit)
        }.getOrNull()
    }

    private fun parsePositiveDecimal(value: String): BigDecimal? {
        val normalized = value.trim().replace(',', '.')
        if (!DECIMAL.matches(normalized)) return null

        val amount = runCatching { BigDecimal(normalized) }.getOrNull() ?: return null
        if (amount <= BigDecimal.ZERO) return null
        if (amount.stripTrailingZeros().scale() > 6) return null
        return amount
    }

    private fun sanitizeOptional(
        value: String?,
        maxLength: Int
    ): String? {
        val trimmed = value?.trim().orEmpty()
        if (trimmed.isBlank()) return null
        if (
            trimmed.length > maxLength ||
            trimmed.any { it == '\n' || it == '\r' || it == '\u0000' }
        ) {
            return null
        }
        return trimmed
    }

    private val DECIMAL = Regex("\\d{1,12}(?:[.,]\\d{1,6})?")
    private val SIMPLE_QUANTITY =
        Regex("(\\d{1,12}(?:[.,]\\d{1,6})?)\\s*(g|kg|ml|cl|l)")
    private val MULTIPACK =
        Regex("(\\d{1,4})\\s*[x×]\\s*(\\d{1,12}(?:[.,]\\d{1,6})?)\\s*(g|kg|ml|cl|l)")

    /** Deliberately broad corruption guard: one consumer package <= 1 tonne / 1000 L. */
    private val MAX_BASE_QUANTITY = BigDecimal("1000000")
}
