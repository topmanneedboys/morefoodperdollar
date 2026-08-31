package com.valuepilot.app

import com.valuepilot.core.AvailabilityState
import com.valuepilot.core.EvidenceChannel
import com.valuepilot.core.EvidenceClaimKind
import com.valuepilot.core.EvidenceEnvironment
import com.valuepilot.core.EvidenceProvider
import com.valuepilot.core.ImportedPriceField
import com.valuepilot.core.ImportedPriceSemantics
import com.valuepilot.core.ImportedSourceIdentity
import com.valuepilot.core.ProviderOfferImportRecord
import com.valuepilot.core.ShoppingSource
import com.valuepilot.core.ShoppingSourceId
import java.net.URI
import java.net.URISyntaxException

/** Why a structurally decoded Jamieson/Rakuten row was kept out of staging. */
enum class JamiesonRakutenPublishedCatalogStagingRejectionReason {
    MISSING_PRODUCT_NAME,
    PRODUCT_NAME_TOO_LONG,
    MISSING_SKU,
    MISSING_SOURCE_IDENTITY,
    SOURCE_IDENTITY_TOO_LONG,
    MISSING_PRIMARY_CATEGORY,
    INVALID_PRODUCT_URL,
    INVALID_IMAGE_URL,
    UNUSABLE_PRICE_FIELDS,
    DECLARED_CURRENCY_MISMATCH,
    INVALID_VERIFIED_FEED_GENERATION_TIME
}

/**
 * Result of the provider-specific pre-activation staging boundary.
 *
 * A staged row is still not an Offer, current-price fact, availability fact,
 * freshness decision, ranking candidate, or Watch fact. Quarantined rows retain
 * their immutable source row and typed price assessment for deterministic audit.
 */
sealed interface JamiesonRakutenPublishedCatalogStagingResult {
    data class Staged(
        val value: JamiesonRakutenPublishedCatalogStagedRecord
    ) : JamiesonRakutenPublishedCatalogStagingResult

    data class Quarantined(
        val sourceRow: JamiesonRakutenPublishedCatalogRow,
        val priceAssessment: JamiesonRakutenPublishedPriceSemanticAssessment,
        val reasons: Set<JamiesonRakutenPublishedCatalogStagingRejectionReason>
    ) : JamiesonRakutenPublishedCatalogStagingResult {
        init {
            require(reasons.isNotEmpty())
        }
    }
}

/**
 * Provider-specific audit envelope around the permanent provider-neutral import record.
 *
 * Keeping [sourceRow] intact preserves Jamieson/Rakuten fields that ValuePilot does not
 * yet have authority to interpret: Availability, Begin Date, End Date, Buy URL, class
 * metadata and every opaque post-primary attribute. Nothing in this type upgrades those
 * fields into factual production claims.
 */
class JamiesonRakutenPublishedCatalogStagedRecord private constructor(
    val importRecord: ProviderOfferImportRecord,
    val sourceRow: JamiesonRakutenPublishedCatalogRow,
    val priceAssessment: JamiesonRakutenPublishedPriceSemanticAssessment
) {
    init {
        require(importRecord.provider.id == JamiesonProductCatalogProductionContract.providerId)
        require(importRecord.dataset == JamiesonProductCatalogProductionContract.datasetNamespace)
        require(importRecord.environment == EvidenceEnvironment.REAL_WORLD)
        require(importRecord.channel == EvidenceChannel.FIRST_PARTY_FEED)
        require(importRecord.claimKind == EvidenceClaimKind.SOURCE_ASSERTED)
        require(importRecord.priceSemantics == ImportedPriceSemantics.UNRESOLVED_SOURCE_FIELDS)
        require(importRecord.priceObservedAtEpochMillis == null)
        require(importRecord.availability.state == AvailabilityState.UNKNOWN)
        require(importRecord.availability.claimKind == EvidenceClaimKind.UNKNOWN)
        require(importRecord.availability.observedAtEpochMillis == null)
        require(priceAssessment.structurallyUsableForStaging)
        require(
            JamiesonProductCatalogProductionContract.matchesDeclaredFeedCurrency(
                priceAssessment.parsedCurrencyCode
            )
        )
    }

    companion object {
        internal fun create(
            importRecord: ProviderOfferImportRecord,
            sourceRow: JamiesonRakutenPublishedCatalogRow,
            priceAssessment: JamiesonRakutenPublishedPriceSemanticAssessment
        ): JamiesonRakutenPublishedCatalogStagedRecord =
            JamiesonRakutenPublishedCatalogStagedRecord(
                importRecord = importRecord,
                sourceRow = sourceRow,
                priceAssessment = priceAssessment
            )
    }
}

/**
 * Pure pre-activation adapter for already-tokenized, real-world Jamieson Product Catalog rows.
 *
 * The adapter intentionally stops at [ProviderOfferImportRecord]. It preserves source price
 * fields and source identity but selects no current price. It does not interpret source
 * Availability, Begin Date, End Date or post-primary attributes. It owns no network, clock,
 * filesystem, UI, ranking or background-work capability.
 *
 * [verifiedFeedHeaderGeneratedAtEpochMillis] may only be the positively parsed timestamp from
 * the Rakuten HDR record (file/dataset generation provenance). A download/import time must not
 * be passed here, and this timestamp is never copied into per-offer freshness.
 */
object JamiesonRakutenPublishedCatalogStagingAdapter {
    const val PROVIDER_DISPLAY_NAME = "Rakuten Advertising"
    const val SOURCE_ID = JamiesonProductCatalogProductionContract.DATASET_NAMESPACE_ID
    const val SOURCE_DISPLAY_NAME = "Jamieson Product Catalog via Rakuten"

    private const val MAX_PRODUCT_NAME_LENGTH = 500
    private const val MAX_SOURCE_IDENTITY_LENGTH = 160
    private const val MAX_URL_LENGTH = 4096

    fun stage(
        row: JamiesonRakutenPublishedCatalogRow,
        verifiedFeedHeaderGeneratedAtEpochMillis: Long? = null
    ): JamiesonRakutenPublishedCatalogStagingResult {
        val priceAssessment = JamiesonRakutenPublishedPriceSemantics.assess(row)
        val reasons = linkedSetOf<JamiesonRakutenPublishedCatalogStagingRejectionReason>()

        if (row.productName.isBlank()) {
            reasons += JamiesonRakutenPublishedCatalogStagingRejectionReason.MISSING_PRODUCT_NAME
        } else if (row.productName.length > MAX_PRODUCT_NAME_LENGTH) {
            reasons += JamiesonRakutenPublishedCatalogStagingRejectionReason.PRODUCT_NAME_TOO_LONG
        }

        if (row.skuNumber.isBlank()) {
            reasons += JamiesonRakutenPublishedCatalogStagingRejectionReason.MISSING_SKU
        }

        if (row.primaryCategory.isBlank()) {
            reasons += JamiesonRakutenPublishedCatalogStagingRejectionReason.MISSING_PRIMARY_CATEGORY
        }

        if (!isValidHttpUrl(row.productUrl)) {
            reasons += JamiesonRakutenPublishedCatalogStagingRejectionReason.INVALID_PRODUCT_URL
        }
        if (!isValidHttpUrl(row.productImageUrl)) {
            reasons += JamiesonRakutenPublishedCatalogStagingRejectionReason.INVALID_IMAGE_URL
        }

        val suppliedIdentityValues =
            listOf(row.productId, row.skuNumber, row.universalProductCode)
                .filter { it.isNotBlank() }
        if (suppliedIdentityValues.isEmpty()) {
            reasons += JamiesonRakutenPublishedCatalogStagingRejectionReason.MISSING_SOURCE_IDENTITY
        }
        if (suppliedIdentityValues.any { it.length > MAX_SOURCE_IDENTITY_LENGTH }) {
            reasons += JamiesonRakutenPublishedCatalogStagingRejectionReason.SOURCE_IDENTITY_TOO_LONG
        }

        if (!priceAssessment.structurallyUsableForStaging) {
            reasons += JamiesonRakutenPublishedCatalogStagingRejectionReason.UNUSABLE_PRICE_FIELDS
        }
        if (
            !JamiesonProductCatalogProductionContract.matchesDeclaredFeedCurrency(
                priceAssessment.parsedCurrencyCode
            )
        ) {
            reasons += JamiesonRakutenPublishedCatalogStagingRejectionReason.DECLARED_CURRENCY_MISMATCH
        }

        if (
            verifiedFeedHeaderGeneratedAtEpochMillis != null &&
                verifiedFeedHeaderGeneratedAtEpochMillis <= 0L
        ) {
            reasons +=
                JamiesonRakutenPublishedCatalogStagingRejectionReason
                    .INVALID_VERIFIED_FEED_GENERATION_TIME
        }

        if (reasons.isNotEmpty()) {
            return JamiesonRakutenPublishedCatalogStagingResult.Quarantined(
                sourceRow = row,
                priceAssessment = priceAssessment,
                reasons = reasons.toSet()
            )
        }

        val identity =
            ImportedSourceIdentity(
                providerItemId = row.productId.takeIf { it.isNotBlank() },
                sku = row.skuNumber.takeIf { it.isNotBlank() },
                suppliedGtin = row.universalProductCode.takeIf { it.isNotBlank() }
            )

        val importRecord =
            ProviderOfferImportRecord(
                provider =
                    EvidenceProvider(
                        id = JamiesonProductCatalogProductionContract.providerId,
                        displayName = PROVIDER_DISPLAY_NAME
                    ),
                source =
                    ShoppingSource(
                        id = ShoppingSourceId(SOURCE_ID),
                        displayName = SOURCE_DISPLAY_NAME
                    ),
                dataset = JamiesonProductCatalogProductionContract.datasetNamespace,
                environment = EvidenceEnvironment.REAL_WORLD,
                channel = EvidenceChannel.FIRST_PARTY_FEED,
                claimKind = EvidenceClaimKind.SOURCE_ASSERTED,
                identity = identity,
                productName = row.productName,
                sourcePriceFields =
                    listOf(
                        ImportedPriceField(
                            sourceFieldName = JamiesonRakutenPublishedPriceSemantics.SALE_PRICE_FIELD_NAME,
                            rawValue = row.salePriceFieldValue,
                            parsedAmount = priceAssessment.salePrice
                        ),
                        ImportedPriceField(
                            sourceFieldName = JamiesonRakutenPublishedPriceSemantics.RETAIL_PRICE_FIELD_NAME,
                            rawValue = row.retailPriceFieldValue,
                            parsedAmount = priceAssessment.retailPrice
                        )
                    ),
                productUrl = row.productUrl,
                imageUrl = row.productImageUrl,
                datasetGeneratedAtEpochMillis = verifiedFeedHeaderGeneratedAtEpochMillis,
                priceObservedAtEpochMillis = null
            )

        return JamiesonRakutenPublishedCatalogStagingResult.Staged(
            JamiesonRakutenPublishedCatalogStagedRecord.create(
                importRecord = importRecord,
                sourceRow = row,
                priceAssessment = priceAssessment
            )
        )
    }

    private fun isValidHttpUrl(value: String): Boolean {
        if (value.isBlank() || value.length > MAX_URL_LENGTH) {
            return false
        }

        return try {
            val parsed = URI(value)
            parsed.scheme in setOf("http", "https") && !parsed.rawAuthority.isNullOrBlank()
        } catch (_: URISyntaxException) {
            false
        }
    }
}
