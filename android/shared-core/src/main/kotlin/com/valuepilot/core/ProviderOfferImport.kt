package com.valuepilot.core

/**
 * Raw GTIN handling at the provider-import boundary.
 *
 * INVALID values remain preserved as source data but are never promoted into a
 * validated cross-source identity.
 */
enum class ImportedGtinStatus {
    NOT_SUPPLIED,
    VALID,
    INVALID
}

/**
 * Source identity exactly as supplied by a provider row.
 *
 * providerItemId and SKU remain provider-scoped. suppliedGtin is preserved even
 * when malformed so a bad source row can be audited instead of silently fixed.
 */
data class ImportedSourceIdentity(
    val providerItemId: String? = null,
    val sku: String? = null,
    val suppliedGtin: String? = null
) {
    init {
        val supplied = listOfNotNull(providerItemId, sku, suppliedGtin)
        require(supplied.isNotEmpty()) {
            "At least one supplied source identifier is required"
        }
        supplied.forEach {
            require(it.isNotBlank())
            require(it.length <= 160)
        }
    }

    val gtinStatus: ImportedGtinStatus
        get() = when {
            suppliedGtin == null -> ImportedGtinStatus.NOT_SUPPLIED
            GtinValidation.isValid(suppliedGtin) -> ImportedGtinStatus.VALID
            else -> ImportedGtinStatus.INVALID
        }

    val validatedGtin: String?
        get() = suppliedGtin?.takeIf(GtinValidation::isValid)

    /**
     * Promote only identifiers safe for the existing ShoppingEvidence contract.
     * An invalid supplied GTIN is retained on this import record but excluded
     * from the promoted identity.
     */
    fun validatedSourceProductIdentity(): SourceProductIdentity? {
        val validGtin = validatedGtin
        if (providerItemId == null && sku == null && validGtin == null) {
            return null
        }
        return SourceProductIdentity(
            providerItemId = providerItemId,
            sku = sku,
            gtin = validGtin
        )
    }
}

/**
 * One source price field before ValuePilot assigns any commerce semantics.
 *
 * sourceFieldName deliberately preserves provider vocabulary such as
 * "retail_price" or "sale_price". parsedAmount can be null when the supplied
 * field is syntactically unusable; rawValue is retained for audit/reporting.
 */
data class ImportedPriceField(
    val sourceFieldName: String,
    val rawValue: String,
    val parsedAmount: Money?
) {
    init {
        require(sourceFieldName.isNotBlank())
        require(sourceFieldName.length <= 96)
        require(rawValue.length <= 160)
    }
}

enum class ImportedPriceSemantics {
    UNRESOLVED_SOURCE_FIELDS
}

/**
 * Provider-neutral staging record for an offer-like product row.
 *
 * This is intentionally not an Offer and exposes no selected current price.
 * Adapters may preserve provider price fields here, but a later, explicit
 * semantic-resolution step must decide whether any field is a current,
 * reference, member, or promotional price.
 *
 * The type is platform-neutral and performs no filesystem, network, retailer,
 * Android, UI, or clock work.
 */
data class ProviderOfferImportRecord(
    val provider: EvidenceProvider,
    val source: ShoppingSource,
    val dataset: EvidenceDatasetNamespace,
    val environment: EvidenceEnvironment,
    val channel: EvidenceChannel,
    val claimKind: EvidenceClaimKind,
    val identity: ImportedSourceIdentity,
    val productName: String,
    val sourcePriceFields: List<ImportedPriceField>,
    val availability: AvailabilityEvidence = AvailabilityEvidence(),
    val productUrl: String? = null,
    val imageUrl: String? = null,
    /** Dataset/file generation time is provenance only, not product freshness. */
    val datasetGeneratedAtEpochMillis: Long? = null,
    /** Only populate when the provider supplies a trustworthy per-offer time. */
    val priceObservedAtEpochMillis: Long? = null
) {
    init {
        require(productName.isNotBlank())
        require(productName.length <= 500)
        require(sourcePriceFields.isNotEmpty())
        require(sourcePriceFields.size <= 16)

        val normalizedPriceFieldNames =
            sourcePriceFields.map { it.sourceFieldName.lowercase() }
        require(normalizedPriceFieldNames.size == normalizedPriceFieldNames.toSet().size) {
            "Source price field names must be unique"
        }

        listOfNotNull(productUrl, imageUrl).forEach {
            require(it.isNotBlank())
            require(it.length <= 4096)
        }

        require(datasetGeneratedAtEpochMillis == null || datasetGeneratedAtEpochMillis > 0L)
        require(priceObservedAtEpochMillis == null || priceObservedAtEpochMillis > 0L)

        if (environment == EvidenceEnvironment.SAMPLE) {
            require(channel == EvidenceChannel.FIXTURE) {
                "Sample imports must use the fixture channel"
            }
        }
        if (channel == EvidenceChannel.FIXTURE) {
            require(environment == EvidenceEnvironment.SAMPLE) {
                "Fixture imports must be explicitly marked sample"
            }
        }
    }

    val priceSemantics: ImportedPriceSemantics
        get() = ImportedPriceSemantics.UNRESOLVED_SOURCE_FIELDS

    fun priceField(sourceFieldName: String): ImportedPriceField? =
        sourcePriceFields.firstOrNull {
            it.sourceFieldName.equals(sourceFieldName, ignoreCase = true)
        }
}
