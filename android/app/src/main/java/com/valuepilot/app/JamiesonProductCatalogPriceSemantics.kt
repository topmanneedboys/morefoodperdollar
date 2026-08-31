package com.valuepilot.app

import com.valuepilot.core.ImportedDiscountAssessment
import com.valuepilot.core.ImportedDiscountRelationship
import com.valuepilot.core.ProviderOfferImportRecord
import com.valuepilot.core.ProductionPriceFieldRoles
import com.valuepilot.core.ProductionPriceRelationshipRule

/**
 * Why one Jamieson/Rakuten Product Catalog row could not receive documented price-field roles.
 *
 * These are semantic/structural blockers only. Even a successful resolution grants no
 * production current-price claim: authorization, lifecycle, dataset disposition, geography,
 * per-offer timestamp, freshness, acceptance and conflict gates remain downstream.
 */
enum class JamiesonProductCatalogPriceSemanticsBlocker {
    PROVIDER_OR_DATASET_SCOPE_MISMATCH,
    RETAIL_PRICE_UNAVAILABLE,
    SALE_PRICE_INVALID,
    NON_CAD_PRICE,
    INCOMPARABLE_PRICE_MONEY,
    SALE_PRICE_ABOVE_RETAIL_PRICE
}

data class JamiesonProductCatalogPriceSemanticsResult(
    val priceRoles: ProductionPriceFieldRoles?,
    val discountAssessment: ImportedDiscountAssessment?,
    val blockers: Set<JamiesonProductCatalogPriceSemanticsBlocker>
) {
    init {
        require((priceRoles != null) == blockers.isEmpty()) {
            "Jamieson price roles exist if and only if semantic checks pass"
        }
    }

    val resolved: Boolean
        get() = priceRoles != null
}

/**
 * Source-specific interpretation of Rakuten Product Catalog price fields for Jamieson.
 *
 * Rakuten's reviewed Product Catalog documentation defines Sale Price as reflecting
 * discounts and Retail Price as not reflecting discounts. Sale Price is optional while
 * Retail Price is required. ValuePilot therefore applies these deterministic row rules:
 *
 * - a valid nonblank sale_price is the current field role and retail_price is its reference;
 * - an absent/blank optional sale_price leaves retail_price as the regular current field role;
 * - a supplied but malformed/non-positive sale_price is not silently ignored;
 * - sale_price may not exceed retail_price when both are usable;
 * - Jamieson's written feed scope requires all accepted price money to be CAD.
 *
 * This resolver performs no I/O, reads no clock, and never assigns dataset or offer freshness.
 * It creates no Offer, EvidenceClaim, ranking state, promotion arithmetic or Watch fact.
 */
object JamiesonProductCatalogPriceSemantics {
    const val SALE_PRICE_FIELD_NAME = "sale_price"
    const val RETAIL_PRICE_FIELD_NAME = "retail_price"

    fun resolve(
        record: ProviderOfferImportRecord
    ): JamiesonProductCatalogPriceSemanticsResult {
        if (
            record.provider.id != JamiesonProductCatalogProductionContract.providerId ||
            record.dataset.id !=
                JamiesonProductCatalogProductionContract.DATASET_NAMESPACE_ID
        ) {
            return blocked(
                JamiesonProductCatalogPriceSemanticsBlocker
                    .PROVIDER_OR_DATASET_SCOPE_MISMATCH
            )
        }

        val blockers = linkedSetOf<JamiesonProductCatalogPriceSemanticsBlocker>()
        val retailField = record.priceField(RETAIL_PRICE_FIELD_NAME)
        val retailPrice = retailField?.parsedAmount

        val retailUsable =
            retailField != null &&
                retailField.rawValue.isNotBlank() &&
                retailPrice != null &&
                retailPrice.minorUnits > 0L

        if (!retailUsable) {
            blockers +=
                JamiesonProductCatalogPriceSemanticsBlocker.RETAIL_PRICE_UNAVAILABLE
        } else if (
            !JamiesonProductCatalogProductionContract
                .matchesDeclaredFeedCurrency(retailPrice!!.currencyCode)
        ) {
            blockers += JamiesonProductCatalogPriceSemanticsBlocker.NON_CAD_PRICE
        }

        val saleField = record.priceField(SALE_PRICE_FIELD_NAME)
        val saleWasSupplied = saleField != null && saleField.rawValue.isNotBlank()
        val salePrice = if (saleWasSupplied) saleField?.parsedAmount else null
        val saleUsable = salePrice != null && salePrice.minorUnits > 0L

        if (saleWasSupplied && !saleUsable) {
            blockers += JamiesonProductCatalogPriceSemanticsBlocker.SALE_PRICE_INVALID
        } else if (
            saleUsable &&
            !JamiesonProductCatalogProductionContract
                .matchesDeclaredFeedCurrency(salePrice!!.currencyCode)
        ) {
            blockers += JamiesonProductCatalogPriceSemanticsBlocker.NON_CAD_PRICE
        }

        val discountAssessment =
            if (retailUsable && saleUsable) {
                record.assessDiscountRelationship(
                    discountedFieldName = SALE_PRICE_FIELD_NAME,
                    referenceFieldName = RETAIL_PRICE_FIELD_NAME
                )
            } else {
                null
            }

        when (discountAssessment?.relationship) {
            ImportedDiscountRelationship.INCOMPARABLE_MONEY ->
                blockers +=
                    JamiesonProductCatalogPriceSemanticsBlocker
                        .INCOMPARABLE_PRICE_MONEY

            ImportedDiscountRelationship.DISCOUNTED_ABOVE_REFERENCE_CONFLICT ->
                blockers +=
                    JamiesonProductCatalogPriceSemanticsBlocker
                        .SALE_PRICE_ABOVE_RETAIL_PRICE

            ImportedDiscountRelationship.DISCOUNTED_BELOW_REFERENCE,
            ImportedDiscountRelationship.EQUAL,
            ImportedDiscountRelationship.UNAVAILABLE,
            null -> Unit
        }

        if (blockers.isNotEmpty()) {
            return JamiesonProductCatalogPriceSemanticsResult(
                priceRoles = null,
                discountAssessment = discountAssessment,
                blockers = blockers
            )
        }

        val roles =
            if (saleUsable) {
                ProductionPriceFieldRoles(
                    currentPriceFieldName = SALE_PRICE_FIELD_NAME,
                    referencePriceFieldName = RETAIL_PRICE_FIELD_NAME,
                    relationshipRule =
                        ProductionPriceRelationshipRule
                            .CURRENT_MUST_NOT_EXCEED_REFERENCE
                )
            } else {
                ProductionPriceFieldRoles(
                    currentPriceFieldName = RETAIL_PRICE_FIELD_NAME
                )
            }

        return JamiesonProductCatalogPriceSemanticsResult(
            priceRoles = roles,
            discountAssessment = discountAssessment,
            blockers = emptySet()
        )
    }

    private fun blocked(
        blocker: JamiesonProductCatalogPriceSemanticsBlocker
    ): JamiesonProductCatalogPriceSemanticsResult =
        JamiesonProductCatalogPriceSemanticsResult(
            priceRoles = null,
            discountAssessment = null,
            blockers = setOf(blocker)
        )
}
