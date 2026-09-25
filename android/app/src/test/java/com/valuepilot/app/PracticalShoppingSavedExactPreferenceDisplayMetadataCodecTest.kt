package com.valuepilot.app

import com.valuepilot.core.PracticalShoppingStoreIdentityScope
import com.valuepilot.core.ProductionProductEvidenceKey
import com.valuepilot.core.ProductionProductKeyScope
import com.valuepilot.core.ShoppingItemKey
import com.valuepilot.core.ShoppingStoreKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticalShoppingSavedExactPreferenceDisplayMetadataCodecTest {

    private val eggs = ShoppingItemKey("eggs")
    private val milk = ShoppingItemKey("milk")
    private val north = ShoppingStoreKey("north")

    @Test
    fun `schema one round trip is deterministic and preserves unicode labels and exact bindings`() {
        val eggsEntry = productEntry(eggs, "gtin:0036000291452", "Free-range Eggs 🥚")
        val milkEntry = productEntry(milk, "gtin:0012345678905", "Milk 4 L")
        val storeEntry = storeEntry(north, "North Market – Downtown")

        val first =
            PracticalShoppingSavedExactPreferenceDisplayMetadataSnapshot(
                productEntries = listOf(milkEntry, eggsEntry),
                storeEntries = listOf(storeEntry)
            )
        val second =
            PracticalShoppingSavedExactPreferenceDisplayMetadataSnapshot(
                productEntries = listOf(eggsEntry, milkEntry),
                storeEntries = listOf(storeEntry)
            )

        val firstEncoded =
            requireNotNull(
                PracticalShoppingSavedExactPreferenceDisplayMetadataCodec.encode(first).bytes
            )
        val secondEncoded =
            requireNotNull(
                PracticalShoppingSavedExactPreferenceDisplayMetadataCodec.encode(second).bytes
            )

        assertTrue(firstEncoded.contentEquals(secondEncoded))
        assertTrue(firstEncoded.all { byte -> (byte.toInt() and 0xff) <= 0x7f })

        val decoded =
            PracticalShoppingSavedExactPreferenceDisplayMetadataCodec.decode(firstEncoded)

        assertTrue(decoded.accepted)
        val snapshot = requireNotNull(decoded.snapshot)
        assertEquals(listOf(eggs, milk), snapshot.productEntries.map { it.itemKey })
        assertEquals("Free-range Eggs 🥚", snapshot.productEntries.first().displayName)
        assertEquals("gtin:0036000291452", snapshot.productEntries.first().productKey.value)
        assertEquals(ProductionProductKeyScope.CROSS_SOURCE_GTIN, snapshot.productEntries.first().productKey.scope)
        assertEquals("North Market – Downtown", snapshot.storeEntries.single().displayName)
        assertEquals("wikidata:Q483551", snapshot.storeEntries.single().scope.merchantKey)
        assertEquals("osm:node:12345", snapshot.storeEntries.single().scope.locationKey)
    }

    @Test
    fun `empty snapshot round trips`() {
        val encoded =
            requireNotNull(
                PracticalShoppingSavedExactPreferenceDisplayMetadataCodec
                    .encode(PracticalShoppingSavedExactPreferenceDisplayMetadataSnapshot())
                    .bytes
            )

        val decoded = PracticalShoppingSavedExactPreferenceDisplayMetadataCodec.decode(encoded)

        assertTrue(decoded.accepted)
        assertTrue(requireNotNull(decoded.snapshot).productEntries.isEmpty())
        assertTrue(requireNotNull(decoded.snapshot).storeEntries.isEmpty())
    }

    @Test
    fun `unsupported schema fails closed`() {
        val encoded =
            requireNotNull(
                PracticalShoppingSavedExactPreferenceDisplayMetadataCodec
                    .encode(PracticalShoppingSavedExactPreferenceDisplayMetadataSnapshot())
                    .bytes
            )
        val changed = String(encoded, Charsets.US_ASCII).replaceFirst("|1", "|2")

        val decoded =
            PracticalShoppingSavedExactPreferenceDisplayMetadataCodec.decode(
                changed.toByteArray(Charsets.US_ASCII)
            )

        assertFalse(decoded.accepted)
        assertEquals(
            PracticalShoppingSavedExactPreferenceDisplayMetadataCodecIssue.UNSUPPORTED_SCHEMA_VERSION,
            decoded.issue
        )
    }

    @Test
    fun `truncated field fails closed without partial snapshot`() {
        val snapshot =
            PracticalShoppingSavedExactPreferenceDisplayMetadataSnapshot(
                productEntries = listOf(productEntry(eggs, "gtin:0036000291452", "Example Eggs"))
            )
        val encoded =
            requireNotNull(
                PracticalShoppingSavedExactPreferenceDisplayMetadataCodec.encode(snapshot).bytes
            )
        val truncated = encoded.copyOf(encoded.size - 1)

        val decoded = PracticalShoppingSavedExactPreferenceDisplayMetadataCodec.decode(truncated)

        assertFalse(decoded.accepted)
        assertEquals(
            PracticalShoppingSavedExactPreferenceDisplayMetadataCodecIssue.MALFORMED_INPUT,
            decoded.issue
        )
    }

    @Test
    fun `invalid product key scope and presentation basis fail closed`() {
        val badScope =
            listOf(
                "VALUEPILOT_SAVED_DISPLAY|1",
                listOf(
                    "P",
                    hex("eggs"),
                    hex("gtin:0036000291452"),
                    hex("NOT_A_SCOPE"),
                    hex("Example Eggs"),
                    hex("USER_PROVIDED")
                ).joinToString("|")
            ).joinToString("\n").toByteArray(Charsets.US_ASCII)
        val badBasis =
            listOf(
                "VALUEPILOT_SAVED_DISPLAY|1",
                listOf(
                    "S",
                    hex("north"),
                    hex("wikidata:Q483551"),
                    hex("osm:node:12345"),
                    hex("PHYSICAL_STORE"),
                    hex("North Market"),
                    hex("NOT_A_BASIS")
                ).joinToString("|")
            ).joinToString("\n").toByteArray(Charsets.US_ASCII)

        val first = PracticalShoppingSavedExactPreferenceDisplayMetadataCodec.decode(badScope)
        val second = PracticalShoppingSavedExactPreferenceDisplayMetadataCodec.decode(badBasis)

        assertEquals(PracticalShoppingSavedExactPreferenceDisplayMetadataCodecIssue.MALFORMED_INPUT, first.issue)
        assertEquals(PracticalShoppingSavedExactPreferenceDisplayMetadataCodecIssue.MALFORMED_INPUT, second.issue)
    }

    @Test
    fun `duplicate stable keys fail closed on decode`() {
        val line =
            listOf(
                "P",
                hex("eggs"),
                hex("gtin:0036000291452"),
                hex("CROSS_SOURCE_GTIN"),
                hex("Example Eggs"),
                hex("USER_PROVIDED")
            ).joinToString("|")
        val bytes =
            listOf("VALUEPILOT_SAVED_DISPLAY|1", line, line)
                .joinToString("\n")
                .toByteArray(Charsets.US_ASCII)

        val decoded = PracticalShoppingSavedExactPreferenceDisplayMetadataCodec.decode(bytes)

        assertFalse(decoded.accepted)
        assertEquals(
            PracticalShoppingSavedExactPreferenceDisplayMetadataCodecIssue.MALFORMED_INPUT,
            decoded.issue
        )
    }

    @Test
    fun `oversized input is rejected before parsing`() {
        val bytes = ByteArray(
            PracticalShoppingSavedExactPreferenceDisplayMetadataCodec.maximumEncodedBytes + 1
        ) { 'A'.code.toByte() }

        val decoded = PracticalShoppingSavedExactPreferenceDisplayMetadataCodec.decode(bytes)

        assertFalse(decoded.accepted)
        assertEquals(
            PracticalShoppingSavedExactPreferenceDisplayMetadataCodecIssue.INPUT_TOO_LARGE,
            decoded.issue
        )
    }

    private fun productEntry(
        itemKey: ShoppingItemKey,
        productKey: String,
        name: String
    ): PracticalShoppingSavedProductDisplayMetadataEntry =
        PracticalShoppingSavedProductDisplayMetadataEntry(
            itemKey = itemKey,
            productKey =
                ProductionProductEvidenceKey(
                    value = productKey,
                    scope = ProductionProductKeyScope.CROSS_SOURCE_GTIN
                ),
            displayName = name,
            basis = PracticalShoppingSavedDisplayMetadataBasis.USER_PROVIDED
        )

    private fun storeEntry(
        storeKey: ShoppingStoreKey,
        name: String
    ): PracticalShoppingSavedStoreDisplayMetadataEntry =
        PracticalShoppingSavedStoreDisplayMetadataEntry(
            storeKey = storeKey,
            scope =
                PracticalShoppingStoreIdentityScope(
                    merchantKey = "wikidata:Q483551",
                    locationKey = "osm:node:12345",
                    commerceChannelKey = "PHYSICAL_STORE"
                ),
            displayName = name,
            basis = PracticalShoppingSavedDisplayMetadataBasis.OPENSTREETMAP_PLACE_NAME
        )

    private fun hex(value: String): String =
        value.toByteArray(Charsets.UTF_8).joinToString(separator = "") { byte ->
            "%02x".format(byte.toInt() and 0xff)
        }
}
