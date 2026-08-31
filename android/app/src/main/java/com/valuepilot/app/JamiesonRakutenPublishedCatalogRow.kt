package com.valuepilot.app

/**
 * Published Rakuten Product Catalog primary-field layout used by the offline
 * qualification tooling.
 *
 * This is deliberately a structural schema only. Field names describe the
 * provider's published columns; they do not establish which price is a
 * production-authoritative selling price, whether an offer is fresh, or whether
 * a real Jamieson feed has passed data-quality qualification.
 */
enum class JamiesonRakutenPublishedCatalogField(
    val index: Int,
    val publishedName: String
) {
    PRODUCT_NAME(0, "Product Name"),
    SKU_NUMBER(1, "SKU Number"),
    PRIMARY_CATEGORY(2, "Primary Category"),
    PRODUCT_URL(3, "Product URL"),
    IMAGE_URL(4, "Image URL"),
    BUY_URL(5, "Buy URL"),
    SHORT_PRODUCT_DESCRIPTION(6, "Short Product Description"),
    LONG_PRODUCT_DESCRIPTION(7, "Long Product Description"),
    DISCOUNT(8, "Discount"),
    DISCOUNT_TYPE(9, "Discount Type"),
    SALE_PRICE(10, "Sale Price"),
    RETAIL_PRICE(11, "Retail Price"),
    BEGIN_DATE(12, "Begin Date"),
    END_DATE(13, "End Date"),
    BRAND(14, "Brand"),
    SHIPPING(15, "Shipping"),
    KEYWORDS(16, "Keywords"),
    MANUFACTURER_PART_NUMBER(17, "Manufacturer Part #"),
    MANUFACTURER_NAME(18, "Manufacturer Name"),
    SHIPPING_INFORMATION(19, "Shipping Information"),
    AVAILABILITY(20, "Availability"),
    UNIVERSAL_PRODUCT_CODE(21, "Universal Product Code"),
    CLASS_ID(22, "Class ID"),
    CURRENCY(23, "Currency"),
    M1(24, "M1"),
    PIXEL(25, "Pixel"),
    MISC1(26, "Misc1"),
    MISC2(27, "Misc2")
}

/**
 * Immutable decoding of one already-tokenized Rakuten Product Catalog product row.
 *
 * Delimiter detection, quoting, gzip/zip handling, HDR parsing and whole-feed
 * qualification belong to the existing offline qualifier. This type receives a
 * row only after tokenization and preserves every supplied field exactly.
 *
 * Extra fields after the 28 published primary fields are retained as opaque
 * extra-price fields because the published format permits advertiser-specific
 * additions there. No semantic meaning is assigned to those extras.
 */
data class JamiesonRakutenPublishedCatalogRow private constructor(
    val primaryFieldValues: List<String>,
    val extraPriceFieldValues: List<String>
) {
    init {
        require(primaryFieldValues.size == PRIMARY_FIELD_COUNT) {
            "Published Rakuten catalog rows must contain exactly $PRIMARY_FIELD_COUNT primary fields"
        }
    }

    fun value(field: JamiesonRakutenPublishedCatalogField): String =
        primaryFieldValues[field.index]

    val productName: String
        get() = value(JamiesonRakutenPublishedCatalogField.PRODUCT_NAME)

    val skuNumber: String
        get() = value(JamiesonRakutenPublishedCatalogField.SKU_NUMBER)

    val primaryCategory: String
        get() = value(JamiesonRakutenPublishedCatalogField.PRIMARY_CATEGORY)

    val productUrl: String
        get() = value(JamiesonRakutenPublishedCatalogField.PRODUCT_URL)

    val imageUrl: String
        get() = value(JamiesonRakutenPublishedCatalogField.IMAGE_URL)

    val buyUrl: String
        get() = value(JamiesonRakutenPublishedCatalogField.BUY_URL)

    val shortProductDescription: String
        get() = value(JamiesonRakutenPublishedCatalogField.SHORT_PRODUCT_DESCRIPTION)

    val longProductDescription: String
        get() = value(JamiesonRakutenPublishedCatalogField.LONG_PRODUCT_DESCRIPTION)

    val discount: String
        get() = value(JamiesonRakutenPublishedCatalogField.DISCOUNT)

    val discountType: String
        get() = value(JamiesonRakutenPublishedCatalogField.DISCOUNT_TYPE)

    /** Provider-published Sale Price column, preserved without price selection. */
    val salePriceFieldValue: String
        get() = value(JamiesonRakutenPublishedCatalogField.SALE_PRICE)

    /** Provider-published Retail Price column, preserved independently of Sale Price. */
    val retailPriceFieldValue: String
        get() = value(JamiesonRakutenPublishedCatalogField.RETAIL_PRICE)

    val beginDate: String
        get() = value(JamiesonRakutenPublishedCatalogField.BEGIN_DATE)

    val endDate: String
        get() = value(JamiesonRakutenPublishedCatalogField.END_DATE)

    val brand: String
        get() = value(JamiesonRakutenPublishedCatalogField.BRAND)

    val shipping: String
        get() = value(JamiesonRakutenPublishedCatalogField.SHIPPING)

    val keywords: String
        get() = value(JamiesonRakutenPublishedCatalogField.KEYWORDS)

    val manufacturerPartNumber: String
        get() = value(JamiesonRakutenPublishedCatalogField.MANUFACTURER_PART_NUMBER)

    val manufacturerName: String
        get() = value(JamiesonRakutenPublishedCatalogField.MANUFACTURER_NAME)

    val shippingInformation: String
        get() = value(JamiesonRakutenPublishedCatalogField.SHIPPING_INFORMATION)

    val availabilityFieldValue: String
        get() = value(JamiesonRakutenPublishedCatalogField.AVAILABILITY)

    val universalProductCode: String
        get() = value(JamiesonRakutenPublishedCatalogField.UNIVERSAL_PRODUCT_CODE)

    val classId: String
        get() = value(JamiesonRakutenPublishedCatalogField.CLASS_ID)

    val currencyFieldValue: String
        get() = value(JamiesonRakutenPublishedCatalogField.CURRENCY)

    val m1: String
        get() = value(JamiesonRakutenPublishedCatalogField.M1)

    val pixel: String
        get() = value(JamiesonRakutenPublishedCatalogField.PIXEL)

    val misc1: String
        get() = value(JamiesonRakutenPublishedCatalogField.MISC1)

    val misc2: String
        get() = value(JamiesonRakutenPublishedCatalogField.MISC2)

    companion object {
        const val PRIMARY_FIELD_COUNT = 28

        fun decode(tokenizedFields: List<String>): JamiesonRakutenPublishedCatalogRow {
            require(tokenizedFields.size >= PRIMARY_FIELD_COUNT) {
                "Rakuten catalog row has ${tokenizedFields.size} fields; expected at least $PRIMARY_FIELD_COUNT"
            }

            return JamiesonRakutenPublishedCatalogRow(
                primaryFieldValues = tokenizedFields.take(PRIMARY_FIELD_COUNT).toList(),
                extraPriceFieldValues = tokenizedFields.drop(PRIMARY_FIELD_COUNT).toList()
            )
        }
    }
}
