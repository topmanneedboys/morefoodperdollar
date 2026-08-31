package com.valuepilot.app

import com.valuepilot.core.ImportedDiscountRelationship
import com.valuepilot.core.ProductionPriceFieldRoles
import com.valuepilot.core.ProductionPriceRelationshipRule

/** Why documented Jamieson/Rakuten price fields cannot be assigned production roles for one row. */
enum class JamiesonRakutenPublishedPriceRoleBlocker {
    DISCOUNT_FIELDS_REQUIRE_SEPARATE_RESOLUTION,
    DISCOUNTED_PRICE_ABOVE_RETAIL_REFERENCE,
    UNEXPECTED_PRICE_RELATIONSHIP
}

/**
 * Row-level resolution of the documented Product Catalog Sale Price / Retail Price roles.
 *
 * Resolved roles are only field semantics. They are not freshness, availability, production
 * authorization, an Offer, or ranking authority. A later provider-neutral production gate still
 * requires an independently trustworthy per-offer timestamp and freshness policy.
 */
sealed interface JamiesonRakutenPublishedPriceRoleResolution {
    data class Resolved(
        val roles: ProductionPriceFieldRoles
    ) : JamiesonRakutenPublishedPriceRoleResolution

    data class Blocked(
        val blocker: JamiesonRakutenPublishedPriceRoleBlocker
    ) : JamiesonRakutenPublishedPriceRoleResolution
}

/**
 * Pure provider-specific role resolver backed by Rakuten Product Catalog Appendix A semantics.
 *
 * Rakuten documents Sale Price as the optional price that reflects discounts and Retail Price as
 * the required price that does not reflect discounts. Therefore:
 * - when a valid Sale Price is present, it is the discounted/current source field and Retail Price
 *   is its non-discounted reference;
 * - when Sale Price is absent and both separate Discount fields are blank, Retail Price is the only
 *   documented price adjustment and becomes the current source field with no separate reference;
 * - when Sale Price is absent but Discount or Discount Type is populated, this resolver fails
 *   closed rather than ignoring a possible source-declared adjustment whose arithmetic belongs to
 *   a separate reviewed slice;
 * - a supplied Sale Price above Retail Price conflicts with the documented roles and fails closed.
 *
 * This resolver deliberately consumes an already-staged Jamieson row so malformed money/currency
 * cannot bypass the staging boundary. It performs no I/O and grants no recency or freshness.
 */
object JamiesonRakutenPublishedPriceFieldRoleResolver {

    fun resolve(
        stagedRecord: JamiesonRakutenPublishedCatalogStagedRecord
    ): JamiesonRakutenPublishedPriceRoleResolution {
        val assessment = stagedRecord.priceAssessment
        require(assessment.structurallyUsableForStaging)
        require(
            JamiesonProductCatalogProductionContract.matchesDeclaredFeedCurrency(
                assessment.parsedCurrencyCode
            )
        )
        require(assessment.retailPrice != null)

        if (assessment.salePrice == null) {
            val sourceRow = stagedRecord.sourceRow
            if (sourceRow.discount.isNotBlank() || sourceRow.discountType.isNotBlank()) {
                return JamiesonRakutenPublishedPriceRoleResolution.Blocked(
                    JamiesonRakutenPublishedPriceRoleBlocker
                        .DISCOUNT_FIELDS_REQUIRE_SEPARATE_RESOLUTION
                )
            }

            return JamiesonRakutenPublishedPriceRoleResolution.Resolved(
                roles =
                    ProductionPriceFieldRoles(
                        currentPriceFieldName =
                            JamiesonRakutenPublishedPriceSemantics.RETAIL_PRICE_FIELD_NAME
                    )
            )
        }

        return when (assessment.relationshipAssessment?.relationship) {
            ImportedDiscountRelationship.DISCOUNTED_BELOW_REFERENCE,
            ImportedDiscountRelationship.EQUAL ->
                JamiesonRakutenPublishedPriceRoleResolution.Resolved(
                    roles =
                        ProductionPriceFieldRoles(
                            currentPriceFieldName =
                                JamiesonRakutenPublishedPriceSemantics.SALE_PRICE_FIELD_NAME,
                            referencePriceFieldName =
                                JamiesonRakutenPublishedPriceSemantics.RETAIL_PRICE_FIELD_NAME,
                            relationshipRule =
                                ProductionPriceRelationshipRule.CURRENT_MUST_NOT_EXCEED_REFERENCE
                        )
                )
            ImportedDiscountRelationship.DISCOUNTED_ABOVE_REFERENCE_CONFLICT ->
                JamiesonRakutenPublishedPriceRoleResolution.Blocked(
                    JamiesonRakutenPublishedPriceRoleBlocker
                        .DISCOUNTED_PRICE_ABOVE_RETAIL_REFERENCE
                )
            ImportedDiscountRelationship.UNAVAILABLE,
            ImportedDiscountRelationship.INCOMPARABLE_MONEY,
            null ->
                JamiesonRakutenPublishedPriceRoleResolution.Blocked(
                    JamiesonRakutenPublishedPriceRoleBlocker.UNEXPECTED_PRICE_RELATIONSHIP
                )
        }
    }
}
