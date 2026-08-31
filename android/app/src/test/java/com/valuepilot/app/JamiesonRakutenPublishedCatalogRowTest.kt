package com.valuepilot.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class JamiesonRakutenPublishedCatalogRowTest {

    @Test
    fun `published primary schema matches qualifier exact contiguous 28 field layout`() {
        val fields = JamiesonRakutenPublishedCatalogField.entries

        assertEquals(JamiesonRakutenPublishedCatalogRow.PRIMARY_FIELD_COUNT, fields.size)
        assertEquals((0 until 28).toList(), fields.map { it.index })
        assertEquals(
            listOf(
                "product_id",
                "product_name",
                "sku_number",
                "primary_category",
                "secondary_category",
                "product_url",
                "product_image_url",
                "buy_url",
                "short_description",
                "long_description",
                "discount",
                "discount_type",
                "sale_price",
                "retail_price",
                "begin_date",
                "end_date",
                "brand",
                "shipping",
                "keywords",
                "manufacturer_part_number",
                "manufacturer_name",
                "shipping_information",
                "availability",
                "upc",
                "class_id",
                "currency",
                "m1",
                "pixel"
            ),
            fields.map { it.qualifierFieldName }
        )
        assertEquals(
            listOf(
                "Product ID",
                "Product Name",
                "SKU Number",
                "Primary Category",
                "Secondary Category",
                "Product URL",
                "Product Image URL",
                "Buy URL",
                "Short Product Description",
                "Long Product Description",
                "Discount",
                "Discount Type",
                "Sale Price",
                "Retail Price",
                "Begin Date",
                "End Date",
                "Brand",
                "Shipping",
                "Keywords",
                "Manufacturer Part #",
                "Manufacturer Name",
                "Shipping Information",
                "Availability",
                "Universal Product Code",
                "Class ID",
                "Currency",
                "M1",
                "Pixel"
            ),
            fields.map { it.publishedName }
        )
    }

    @Test
    fun `android schema is mechanically aligned with offline qualifier primary fields`() {
        val qualifier = repositoryFile("tools/qualify_rakuten_product_catalog.py").readText()
        val startMarker = "PRIMARY_FIELDS = ["
        val start = qualifier.indexOf(startMarker)
        val end = qualifier.indexOf("]\n\nREQUIRED_PRIMARY_FIELDS", startIndex = start)

        assertTrue("qualifier PRIMARY_FIELDS block missing", start >= 0 && end > start)
        val block = qualifier.substring(start + startMarker.length, end)
        val qualifierFields =
            Regex("\\\"([a-z0-9_]+)\\\"")
                .findAll(block)
                .map { match -> match.groupValues[1] }
                .toList()

        assertEquals(
            JamiesonRakutenPublishedCatalogField.entries.map { it.qualifierFieldName },
            qualifierFields
        )
        assertTrue(qualifier.contains("MIN_PRIMARY_FIELDS = 28"))
        assertTrue(qualifier.contains("DOCUMENTED_FULL_FIELDS = 38"))
    }

    @Test
    fun `documented 38 field row decodes official primary positions and preserves class tail opaquely`() {
        val primary = canonicalPrimaryValues()
        val attributes = List(10) { index -> "opaque-attribute-${index + 1}" }
        val row = JamiesonRakutenPublishedCatalogRow.decode(primary + attributes)

        JamiesonRakutenPublishedCatalogField.entries.forEach { field ->
            assertEquals(primary[field.index], row.value(field))
        }

        assertEquals("product-123", row.productId)
        assertEquals("Jamieson Vitamin D3 1000 IU", row.productName)
        assertEquals("JAM-D3-100", row.skuNumber)
        assertEquals("Health > Vitamins", row.primaryCategory)
        assertEquals("Vitamin D", row.secondaryCategory)
        assertEquals("https://example.invalid/product", row.productUrl)
        assertEquals("https://example.invalid/image.jpg", row.productImageUrl)
        assertEquals("https://example.invalid/buy", row.buyUrl)
        assertEquals("Short description", row.shortProductDescription)
        assertEquals("Long | description stays one token", row.longProductDescription)
        assertEquals("5.00", row.discount)
        assertEquals("amount", row.discountType)
        assertEquals("14.99", row.salePriceFieldValue)
        assertEquals("19.99", row.retailPriceFieldValue)
        assertEquals("08/01/2026", row.beginDate)
        assertEquals("09/30/2026", row.endDate)
        assertEquals("Jamieson", row.brand)
        assertEquals("0", row.shipping)
        assertEquals("vitamin,d3", row.keywords)
        assertEquals("MPN-123", row.manufacturerPartNumber)
        assertEquals("Jamieson Wellness", row.manufacturerName)
        assertEquals("Canada", row.shippingInformation)
        assertEquals("in-stock", row.availabilityFieldValue)
        assertEquals("064642012345", row.universalProductCode)
        assertEquals("101", row.classId)
        assertEquals("CAD", row.currencyFieldValue)
        assertEquals("m1-value", row.m1)
        assertEquals("pixel-value", row.pixel)
        assertEquals(attributes, row.opaquePostPrimaryFieldValues)
        assertEquals(28, row.primaryFieldValues.size)
    }

    @Test
    fun `sale and retail fields use official positions 12 and 13 and optional sale blank is preserved`() {
        val values = canonicalPrimaryValues().toMutableList()
        values[JamiesonRakutenPublishedCatalogField.DISCOUNT.index] = "not-sale-price"
        values[JamiesonRakutenPublishedCatalogField.DISCOUNT_TYPE.index] = "not-retail-price"
        values[JamiesonRakutenPublishedCatalogField.SALE_PRICE.index] = ""
        values[JamiesonRakutenPublishedCatalogField.RETAIL_PRICE.index] = "19.99"

        val row = JamiesonRakutenPublishedCatalogRow.decode(values)

        assertEquals(12, JamiesonRakutenPublishedCatalogField.SALE_PRICE.index)
        assertEquals(13, JamiesonRakutenPublishedCatalogField.RETAIL_PRICE.index)
        assertEquals("", row.salePriceFieldValue)
        assertEquals("19.99", row.retailPriceFieldValue)
        assertFalse(row.salePriceFieldValue == row.retailPriceFieldValue)
    }

    @Test
    fun `post primary fields are preserved opaquely and are never labeled extra prices`() {
        val extras = listOf("attribute-a", "attribute-b", "feed-extra-c")
        val row = JamiesonRakutenPublishedCatalogRow.decode(canonicalPrimaryValues() + extras)

        assertEquals(extras, row.opaquePostPrimaryFieldValues)
        assertEquals(28, row.primaryFieldValues.size)
    }

    @Test
    fun `fewer than 28 already tokenized fields fails closed`() {
        assertThrows<IllegalArgumentException> {
            JamiesonRakutenPublishedCatalogRow.decode(List(27) { "field-$it" })
        }
    }

    @Test
    fun `decoder snapshots caller list so later mutation cannot change decoded evidence`() {
        val source = canonicalPrimaryValues().toMutableList()
        val row = JamiesonRakutenPublishedCatalogRow.decode(source)

        source[JamiesonRakutenPublishedCatalogField.PRODUCT_NAME.index] = "mutated"
        source += "late-extra"

        assertEquals("Jamieson Vitamin D3 1000 IU", row.productName)
        assertTrue(row.opaquePostPrimaryFieldValues.isEmpty())
    }

    @Test
    fun `published availability text is preserved at official index 22 without reinterpretation`() {
        listOf("in-stock", "out-of-stock", "preorder", "backorder", "unexpected", "").forEach { value ->
            val fields = canonicalPrimaryValues().toMutableList()
            fields[JamiesonRakutenPublishedCatalogField.AVAILABILITY.index] = value

            val row = JamiesonRakutenPublishedCatalogRow.decode(fields)

            assertEquals(22, JamiesonRakutenPublishedCatalogField.AVAILABILITY.index)
            assertEquals(value, row.availabilityFieldValue)
        }
    }

    @Test
    fun `decoder does not reinterpret delimiters inside an already tokenized description`() {
        val fields = canonicalPrimaryValues().toMutableList()
        val description = "Vitamin D | 100 tablets | bilingual label"
        fields[JamiesonRakutenPublishedCatalogField.LONG_PRODUCT_DESCRIPTION.index] = description

        val row = JamiesonRakutenPublishedCatalogRow.decode(fields)

        assertEquals(description, row.longProductDescription)
        assertEquals(28, row.primaryFieldValues.size)
    }

    @Test
    fun `source boundary fixes qualifier positions and owns no offer freshness or side effect authority`() {
        val source = source("JamiesonRakutenPublishedCatalogRow.kt").readText()

        listOf(
            "PRIMARY_FIELD_COUNT = 28",
            "PRODUCT_ID(0, \"product_id\", \"Product ID\")",
            "PRODUCT_NAME(1, \"product_name\", \"Product Name\")",
            "SECONDARY_CATEGORY(4, \"secondary_category\", \"Secondary Category\")",
            "SALE_PRICE(12, \"sale_price\", \"Sale Price\")",
            "RETAIL_PRICE(13, \"retail_price\", \"Retail Price\")",
            "AVAILABILITY(22, \"availability\", \"Availability\")",
            "UNIVERSAL_PRODUCT_CODE(23, \"upc\", \"Universal Product Code\")",
            "CLASS_ID(24, \"class_id\", \"Class ID\")",
            "CURRENCY(25, \"currency\", \"Currency\")",
            "tokenizedFields.take(PRIMARY_FIELD_COUNT)",
            "tokenizedFields.drop(PRIMARY_FIELD_COUNT)",
            "opaquePostPrimaryFieldValues"
        ).forEach { required ->
            assertTrue("missing published-schema boundary: $required", source.contains(required))
        }

        listOf(
            "MISC1(",
            "MISC2(",
            "extraPriceFieldValues",
            "ProviderOfferImportRecord(",
            "ProviderPriceFieldSelector",
            "ProviderCurrentPriceCandidateFactory",
            "ImportedOfferPriceRole.ADVERTISER_ASSERTED_CURRENT",
            "EvidenceClaim(",
            "observedAtEpochMillis",
            "datasetGeneratedAtEpochMillis",
            "System.currentTimeMillis",
            "Instant.now",
            "HttpURLConnection",
            "OkHttp",
            "Retrofit",
            "android.permission.INTERNET",
            "android.permission.ACCESS_NETWORK_STATE",
            "SharedPreferences",
            "WorkManager",
            "Notification",
            "MainActivity",
            "ProductionSearchSurfaceHost",
            "StapleWatch",
            "ProductionBestValueRanking"
        ).forEach { forbidden ->
            assertFalse("unexpected schema drift, authority, or side effect: $forbidden", source.contains(forbidden))
        }
    }

    private fun canonicalPrimaryValues(): List<String> =
        listOf(
            "product-123",
            "Jamieson Vitamin D3 1000 IU",
            "JAM-D3-100",
            "Health > Vitamins",
            "Vitamin D",
            "https://example.invalid/product",
            "https://example.invalid/image.jpg",
            "https://example.invalid/buy",
            "Short description",
            "Long | description stays one token",
            "5.00",
            "amount",
            "14.99",
            "19.99",
            "08/01/2026",
            "09/30/2026",
            "Jamieson",
            "0",
            "vitamin,d3",
            "MPN-123",
            "Jamieson Wellness",
            "Canada",
            "in-stock",
            "064642012345",
            "101",
            "CAD",
            "m1-value",
            "pixel-value"
        )

    private inline fun <reified T : Throwable> assertThrows(block: () -> Unit) {
        try {
            block()
        } catch (error: Throwable) {
            assertTrue(
                "expected ${T::class.java.name}, got ${error::class.java.name}",
                error is T
            )
            return
        }
        throw AssertionError("expected ${T::class.java.name}")
    }

    private fun source(fileName: String): File =
        sequenceOf(
            File("src/main/java/com/valuepilot/app/$fileName"),
            File("android/app/src/main/java/com/valuepilot/app/$fileName")
        ).firstOrNull(File::isFile)
            ?: error("Source file not found: $fileName")

    private fun repositoryFile(path: String): File =
        sequenceOf(
            File(path),
            File("../$path"),
            File("../../$path"),
            File("../../../$path")
        ).firstOrNull(File::isFile)
            ?: error("Repository file not found: $path")
}
