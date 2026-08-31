package com.valuepilot.app

/**
 * Published Rakuten Product Catalog primary-field layout mirrored from the
 * repository's offline Product Catalog qualifier.
 *
 * [qualifierFieldName] is the exact canonical key used by
 * tools/qualify_rakuten_product_catalog.py. [publishedName] is a human-readable
 * provider column label. Keeping both here lets tests mechanically catch schema
 * drift instead of validating an Android-only invented layout.
 *
 * This remains a structural schema only. Field position does not establish a
 * production-authoritative selling price, freshness, availability authority,
 * geography, rights, or offer eligibility.
 */
enum class JamiesonRakutenPublishedCatalogField(
    val index: Int,
    val qualifierFieldName: String,
    val publishedName: String
) {
    PRODUCT_ID(0, "product_id", "Product ID"),
    PRODUCT_NAME(1, "product_name", "Product Name"),
    SKU_NUMBER(2, "sku_number", "SKU Number"),
    PRIMARY_CATEGORY(3, "primary_category", "Primary Category"),
    SECONDARY_CATEGORY(4, "secondary_category", "Secondary Category"),
    PRODUCT_URL(5, "product_url", "Product URL"),
    PRODUCT_IMAGE_URL(6, "product_image_url", "Product Image URL"),
    BUY_URL(7, "buy_url", "Buy URL"),
    SHORT_PRODUCT_DESCRIPTION(8, "short_description", "Short Product Description"),
    LONG_PRODUCT_DESCRIPTION(9, "long_description", "Long Product Description"),
    DISCOUNT(10, "discount", "Discount"),
    DISCOUNT_TYPE(11, "discount_type", "Discount Type"),
    SALE_PRICE(12, "sale_price", "Sale Price"),
    RETAIL_PRICE(13, "retail_price", "Retail Price"),
    BEGIN_DATE(14, "begin_date", "Begin Date"),
    END_DATE(15, "end_date", "End Date"),
    BRAND(16, "brand", "Brand"),
    SHIPPING(17, "shipping", "Shipping"),
    KEYWORDS(18, "keywords", "Keywords"),
    MANUFACTURER_PART_NUMBER(19, "manufacturer_part_number", "Manufacturer Part #"),
    MANUFACTURER_NAME(20, "manufacturer_name", "Manufacturer Name"),
    SHIPPING_INFORMATION(21, "shipping_information", "Shipping Information"),
    AVAILABILITY(22, "availability", "Availability"),
    UNIVERSAL_PRODUCT_CODE(23, "upc", "Universal Product Code"),
    CLASS_ID(24, "class_id", "Class ID"),
    CURRENCY(25, "currency", "Currency"),
    M1(26, "m1", "M1"),
    PIXEL(27, "pixel", "Pixel")
}

/**
 * Immutable decoding of one already-tokenized Rakuten Product Catalog product row.
 *
 * Delimiter detection, quoting, gzip/zip handling, HDR/TRL parsing and whole-feed
 * qualification belong to the existing offline qualifier. This type receives a
 * product row only after tokenization and preserves every supplied field exactly.
 *
 * Rakuten's documented Product Catalog has 28 primary fields followed by up to
 * ten class-dependent attribute positions in the documented full shape. Real
 * feeds may contain the documented full shape or more. Without matching class
 * metadata those post-primary values cannot be named safely, so this boundary
 * retains every value after field 28 opaquely and in order. It never interprets
 * those values as prices, quantities, availability, or any other factual domain.
 */
class JamiesonRakutenPublishedCatalogRow private constructor(
    val primaryFieldValues: List<String>,
    val opaquePostPrimaryFieldValues: List<String>
) {
    init {
        require(primaryFieldValues.size == PRIMARY_FIELD_COUNT) {
            "Published Rakuten catalog rows must contain exactly $PRIMARY_FIELD_COUNT primary fields"
        }
    }

    fun value(field: JamiesonRakutenPublishedCatalogField): String =
        primaryFieldValues[field.index]

    val productId: String
        get() = value(JamiesonRakutenPublishedCatalogField.PRODUCT_ID)

    val productName: String
        get() = value(JamiesonRakutenPublishedCatalogField.PRODUCT_NAME)

    val skuNumber: String
        get() = value(JamiesonRakutenPublishedCatalogField.SKU_NUMBER)

    val primaryCategory: String
        get() = value(JamiesonRakutenPublishedCatalogField.PRIMARY_CATEGORY)

    val secondaryCategory: String
        get() = value(JamiesonRakutenPublishedCatalogField.SECONDARY_CATEGORY)

    val productUrl: String
        get() = value(JamiesonRakutenPublishedCatalogField.PRODUCT_URL)

    val productImageUrl: String
        get() = value(JamiesonRakutenPublishedCatalogField.PRODUCT_IMAGE_URL)

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

    companion object {
        const val PRIMARY_FIELD_COUNT = 28

        fun decode(tokenizedFields: List<String>): JamiesonRakutenPublishedCatalogRow {
            require(tokenizedFields.size >= PRIMARY_FIELD_COUNT) {
                "Rakuten catalog row has ${tokenizedFields.size} fields; expected at least $PRIMARY_FIELD_COUNT"
            }

            return JamiesonRakutenPublishedCatalogRow(
                primaryFieldValues = tokenizedFields.take(PRIMARY_FIELD_COUNT).toList(),
                opaquePostPrimaryFieldValues = tokenizedFields.drop(PRIMARY_FIELD_COUNT).toList()
            )
        }
    }
}
