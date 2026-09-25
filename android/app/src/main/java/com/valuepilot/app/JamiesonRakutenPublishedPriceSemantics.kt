package com.valuepilot.app

import com.valuepilot.core.ImportedDiscountAssessment
import com.valuepilot.core.ImportedDiscountRelationshipEvaluator
import com.valuepilot.core.Money

/**
 * Parse/validation status for one documented Rakuten price field.
 *
 * Sale Price is optional in the published Product Catalog schema. Retail Price is
 * required. Currency is carried by the row itself and is never inferred from the
 * advertiser, target market, or another field.
 */
enum class JamiesonRakutenPublishedPriceFieldStatus {
    PARSED,
    ABSENT_OPTIONAL,
    MISSING_REQUIRED,
    INVALID,
    BLOCKED_BY_INVALID_CURRENCY
}

enum class JamiesonRakutenPublishedPriceSemanticBlocker {
    INVALID_CURRENCY,
    MISSING_RETAIL_PRICE,
    INVALID_RETAIL_PRICE,
    INVALID_SALE_PRICE
}

/**
 * Typed interpretation of only the documented Sale Price / Retail Price roles.
 *
 * This object deliberately exposes no selected/current price. A complete parse only
 * means the published fields were structurally understood. Rights, geography,
 * dataset recency, per-offer freshness, availability, production authorization and
 * current-price selection remain separate gates.
 */
class JamiesonRakutenPublishedPriceSemanticAssessment internal constructor(
    val rawCurrencyFieldValue: String,
    val rawSalePriceFieldValue: String,
    val rawRetailPriceFieldValue: String,
    val parsedCurrencyCode: String?,
    val salePriceStatus: JamiesonRakutenPublishedPriceFieldStatus,
    val retailPriceStatus: JamiesonRakutenPublishedPriceFieldStatus,
    val salePrice: Money?,
    val retailPrice: Money?,
    val relationshipAssessment: ImportedDiscountAssessment?,
    val blockers: Set<JamiesonRakutenPublishedPriceSemanticBlocker>
) {
    val structurallyUsableForStaging: Boolean
        get() = blockers.isEmpty()

    init {
        require(blockers.isNotEmpty() || parsedCurrencyCode != null)
        require((salePriceStatus == JamiesonRakutenPublishedPriceFieldStatus.PARSED) == (salePrice != null))
        require((retailPriceStatus == JamiesonRakutenPublishedPriceFieldStatus.PARSED) == (retailPrice != null))
        require(relationshipAssessment == null || retailPrice != null)
        require(
            relationshipAssessment == null ||
                relationshipAssessment.discountedFieldName == JamiesonRakutenPublishedPriceSemantics.SALE_PRICE_FIELD_NAME
        )
        require(
            relationshipAssessment == null ||
                relationshipAssessment.referenceFieldName == JamiesonRakutenPublishedPriceSemantics.RETAIL_PRICE_FIELD_NAME
        )
    }
}

/**
 * Pure Jamieson/Rakuten adapter for published Product Catalog price-field semantics.
 *
 * The published schema documents Sale Price as an optional discounted field and
 * Retail Price as the required reference field. This adapter parses those two values
 * with exact Money and delegates relative-order checking to the provider-neutral
 * shared-core evaluator.
 *
 * It does not choose a current price. In particular, Sale Price being below Retail
 * Price only supports the source's declared discount relationship; it does not make
 * Sale Price a fresh/current/rankable production fact.
 */
object JamiesonRakutenPublishedPriceSemantics {

    const val SALE_PRICE_FIELD_NAME = "Sale Price"
    const val RETAIL_PRICE_FIELD_NAME = "Retail Price"

    fun assess(
        row: JamiesonRakutenPublishedCatalogRow
    ): JamiesonRakutenPublishedPriceSemanticAssessment {
        val rawCurrency = row.currencyFieldValue
        val rawSale = row.salePriceFieldValue
        val rawRetail = row.retailPriceFieldValue

        val blockers = linkedSetOf<JamiesonRakutenPublishedPriceSemanticBlocker>()
        val currencyCode = rawCurrency.takeIf { it.matches(Regex("[A-Z]{3}")) }

        if (currencyCode == null) {
            blockers += JamiesonRakutenPublishedPriceSemanticBlocker.INVALID_CURRENCY

            return JamiesonRakutenPublishedPriceSemanticAssessment(
                rawCurrencyFieldValue = rawCurrency,
                rawSalePriceFieldValue = rawSale,
                rawRetailPriceFieldValue = rawRetail,
                parsedCurrencyCode = null,
                salePriceStatus =
                    if (rawSale.isBlank()) {
                        JamiesonRakutenPublishedPriceFieldStatus.ABSENT_OPTIONAL
                    } else {
                        JamiesonRakutenPublishedPriceFieldStatus.BLOCKED_BY_INVALID_CURRENCY
                    },
                retailPriceStatus =
                    if (rawRetail.isBlank()) {
                        blockers += JamiesonRakutenPublishedPriceSemanticBlocker.MISSING_RETAIL_PRICE
                        JamiesonRakutenPublishedPriceFieldStatus.MISSING_REQUIRED
                    } else {
                        JamiesonRakutenPublishedPriceFieldStatus.BLOCKED_BY_INVALID_CURRENCY
                    },
                salePrice = null,
                retailPrice = null,
                relationshipAssessment = null,
                blockers = blockers.toSet()
            )
        }

        val retail = when {
            rawRetail.isBlank() -> {
                blockers += JamiesonRakutenPublishedPriceSemanticBlocker.MISSING_RETAIL_PRICE
                null
            }
            else -> parsePositiveMoney(rawRetail, currencyCode).also { parsed ->
                if (parsed == null) {
                    blockers += JamiesonRakutenPublishedPriceSemanticBlocker.INVALID_RETAIL_PRICE
                }
            }
        }
        val retailStatus = when {
            rawRetail.isBlank() -> JamiesonRakutenPublishedPriceFieldStatus.MISSING_REQUIRED
            retail == null -> JamiesonRakutenPublishedPriceFieldStatus.INVALID
            else -> JamiesonRakutenPublishedPriceFieldStatus.PARSED
        }

        val sale = when {
            rawSale.isBlank() -> null
            else -> parsePositiveMoney(rawSale, currencyCode).also { parsed ->
                if (parsed == null) {
                    blockers += JamiesonRakutenPublishedPriceSemanticBlocker.INVALID_SALE_PRICE
                }
            }
        }
        val saleStatus = when {
            rawSale.isBlank() -> JamiesonRakutenPublishedPriceFieldStatus.ABSENT_OPTIONAL
            sale == null -> JamiesonRakutenPublishedPriceFieldStatus.INVALID
            else -> JamiesonRakutenPublishedPriceFieldStatus.PARSED
        }

        val relationship =
            if (retail != null && JamiesonRakutenPublishedPriceSemanticBlocker.INVALID_SALE_PRICE !in blockers) {
                ImportedDiscountRelationshipEvaluator.assess(
                    discountedFieldName = SALE_PRICE_FIELD_NAME,
                    discountedAmount = sale,
                    referenceFieldName = RETAIL_PRICE_FIELD_NAME,
                    referenceAmount = retail
                )
            } else {
                null
            }

        return JamiesonRakutenPublishedPriceSemanticAssessment(
            rawCurrencyFieldValue = rawCurrency,
            rawSalePriceFieldValue = rawSale,
            rawRetailPriceFieldValue = rawRetail,
            parsedCurrencyCode = currencyCode,
            salePriceStatus = saleStatus,
            retailPriceStatus = retailStatus,
            salePrice = sale,
            retailPrice = retail,
            relationshipAssessment = relationship,
            blockers = blockers.toSet()
        )
    }

    private fun parsePositiveMoney(rawValue: String, currencyCode: String): Money? =
        try {
            Money.parse(rawValue, currencyCode).takeIf { it.minorUnits > 0L }
        } catch (_: IllegalArgumentException) {
            null
        } catch (_: ArithmeticException) {
            null
        }
}
