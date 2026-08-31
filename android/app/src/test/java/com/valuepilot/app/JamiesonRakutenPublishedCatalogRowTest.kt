package com.valuepilot.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class JamiesonRakutenPublishedCatalogRowTest {

    @Test
    fun `published primary schema remains exact contiguous 28 field layout`() {
        val fields = JamiesonRakutenPublishedCatalogField.entries

        assertEquals(JamiesonRakutenPublishedCatalogRow.PRIMARY_FIELD_COUNT, fields.size)
        assertEquals((0 until 28).toList(), fields.map { it.index })
        assertEquals(
            listOf(
                "Product Name",
                "SKU Number",
                "Primary Category",
                "Product URL",
                "Image URL",
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
                "Pixel",
                "Misc1",
                "Misc2"
            ),
            fields.map { it.publishedName }
        )
    }

    @Test
    fun `exact 28 field row decodes every published position without normalization`() {
        val values = canonicalValues()
        val row = JamiesonRakutenPublishedCatalogRow.decode(values)

        JamiesonRakutenPublishedCatalogField.entries.forEach { field ->
            assertEquals(values[field.index], row.value(field))
        }

        assertEquals("Jamieson Vitamin D3 1000 IU", row.productName)
        assertEquals("JAM-D3-100", row.skuNumber)
        assertEquals("Health > Vitamins", row.primaryCategory)
        assertEquals("https://example.invalid/product", row.productUrl)
        assertEquals("https://example.invalid/image.jpg", row.imageUrl)
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
        assertEquals("misc-one", row.misc1)
        assertEquals("misc-two", row.misc2)
        assertTrue(row.extraPriceFieldValues.isEmpty())
    }

    @Test
    fun `sale and retail fields remain distinct and optional sale blank is preserved`() {
        val values = canonicalValues().toMutableList()
        values[JamiesonRakutenPublishedCatalogField.SALE_PRICE.index] = ""
        values[JamiesonRakutenPublishedCatalogField.RETAIL_PRICE.index] = "19.99"

        val row = JamiesonRakutenPublishedCatalogRow.decode(values)

        assertEquals("", row.salePriceFieldValue)
        assertEquals("19.99", row.retailPriceFieldValue)
        assertFalse(row.salePriceFieldValue == row.retailPriceFieldValue)
    }

    @Test
    fun `advertiser specific fields after primary schema are preserved opaquely and in order`() {
        val row =
            JamiesonRakutenPublishedCatalogRow.decode(
                canonicalValues() + listOf("extra-price-a", "extra-price-b", "extra-price-c")
            )

        assertEquals(
            listOf("extra-price-a", "extra-price-b", "extra-price-c"),
            row.extraPriceFieldValues
        )
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
        val source = canonicalValues().toMutableList()
        val row = JamiesonRakutenPublishedCatalogRow.decode(source)

        source[JamiesonRakutenPublishedCatalogField.PRODUCT_NAME.index] = "mutated"
        source += "late-extra"

        assertEquals("Jamieson Vitamin D3 1000 IU", row.productName)
        assertTrue(row.extraPriceFieldValues.isEmpty())
    }

    @Test
    fun `published availability text is preserved without collapsing preorder or backorder`() {
        listOf("in-stock", "out-of-stock", "preorder", "backorder", "unexpected", "").forEach { value ->
            val fields = canonicalValues().toMutableList()
            fields[JamiesonRakutenPublishedCatalogField.AVAILABILITY.index] = value

            val row = JamiesonRakutenPublishedCatalogRow.decode(fields)

            assertEquals(value, row.availabilityFieldValue)
        }
    }

    @Test
    fun `decoder does not reinterpret delimiters inside an already tokenized description`() {
        val fields = canonicalValues().toMutableList()
        val description = "Vitamin D | 100 tablets | bilingual label"
        fields[JamiesonRakutenPublishedCatalogField.LONG_PRODUCT_DESCRIPTION.index] = description

        val row = JamiesonRakutenPublishedCatalogRow.decode(fields)

        assertEquals(description, row.longProductDescription)
        assertEquals(28, row.primaryFieldValues.size)
    }

    @Test
    fun `source boundary owns no current price freshness production offer network persistence or UI authority`() {
        val source = source("JamiesonRakutenPublishedCatalogRow.kt").readText()

        listOf(
            "PRIMARY_FIELD_COUNT = 28",
            "SALE_PRICE(10, \"Sale Price\")",
            "RETAIL_PRICE(11, \"Retail Price\")",
            "AVAILABILITY(20, \"Availability\")",
            "CURRENCY(23, \"Currency\")",
            "tokenizedFields.take(PRIMARY_FIELD_COUNT)",
            "tokenizedFields.drop(PRIMARY_FIELD_COUNT)"
        ).forEach { required ->
            assertTrue("missing published-schema boundary: $required", source.contains(required))
        }

        listOf(
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
            assertFalse("unexpected authority or side effect: $forbidden", source.contains(forbidden))
        }
    }

    private fun canonicalValues(): List<String> =
        listOf(
            "Jamieson Vitamin D3 1000 IU",
            "JAM-D3-100",
            "Health > Vitamins",
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
            "pixel-value",
            "misc-one",
            "misc-two"
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
}
