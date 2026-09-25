package com.valuepilot.app

import com.valuepilot.core.EvidenceDatasetNamespace
import com.valuepilot.core.EvidenceProviderId
import com.valuepilot.core.EvidenceStorageBoundary
import com.valuepilot.core.PracticalShoppingStoreIdentityScope
import com.valuepilot.core.ShoppingItemKey
import com.valuepilot.core.ShoppingStoreKey
import com.valuepilot.core.SourceProductIdentity
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticalShoppingSavedExactPreferenceCodecTest {

    @Test
    fun `schema one round trip is deterministic and preserves exact provenance`() {
        val first =
            state(
                products = listOf(product("milk", "042100005264"), product("eggs", "036000291452")),
                stores = listOf(store("south", 22L), store("north", 11L))
            )
        val second =
            state(
                products = listOf(product("eggs", "036000291452"), product("milk", "042100005264")),
                stores = listOf(store("north", 11L), store("south", 22L))
            )

        val firstEncoded = PracticalShoppingSavedExactPreferenceCodec.encode(first)
        val secondEncoded = PracticalShoppingSavedExactPreferenceCodec.encode(second)

        assertTrue(firstEncoded.accepted)
        assertTrue(secondEncoded.accepted)
        assertArrayEquals(requireNotNull(firstEncoded.bytes), requireNotNull(secondEncoded.bytes))

        val decoded =
            PracticalShoppingSavedExactPreferenceCodec.decode(requireNotNull(firstEncoded.bytes))

        assertTrue(decoded.accepted)
        assertEquals(first, decoded.state)
        assertEquals(productDataset, decoded.state?.productFor(ShoppingItemKey("eggs"))?.dataset)
        assertEquals(storeDataset, decoded.state?.storeFor(ShoppingStoreKey("north"))?.dataset)
    }

    @Test
    fun `unknown schema decodes structurally but fails closed in the state manager`() {
        val encoded = requireNotNull(PracticalShoppingSavedExactPreferenceCodec.encode(state()).bytes)
        val text = String(encoded, Charsets.US_ASCII)
        val unknownVersion = text.replaceFirst("VALUEPILOT_SAVED_EXACT|1", "VALUEPILOT_SAVED_EXACT|2")

        val decoded =
            PracticalShoppingSavedExactPreferenceCodec.decode(
                unknownVersion.toByteArray(Charsets.US_ASCII)
            )

        assertFalse(decoded.accepted)
        assertNull(decoded.state)
        assertNull(decoded.codecIssue)
        assertEquals(
            setOf(PracticalShoppingSavedExactPreferenceLoadIssue.UNSUPPORTED_SCHEMA_VERSION),
            decoded.documentIssues
        )
    }

    @Test
    fun `truncated hex field is rejected instead of partially recovered`() {
        val encoded = requireNotNull(PracticalShoppingSavedExactPreferenceCodec.encode(state()).bytes)
        val truncated = encoded.copyOf(encoded.size - 1)

        val decoded = PracticalShoppingSavedExactPreferenceCodec.decode(truncated)

        assertFalse(decoded.accepted)
        assertEquals(
            PracticalShoppingSavedExactPreferenceCodecIssue.MALFORMED_INPUT,
            decoded.codecIssue
        )
        assertTrue(decoded.documentIssues.isEmpty())
    }

    @Test
    fun `non ascii outer document is rejected`() {
        val encoded = requireNotNull(PracticalShoppingSavedExactPreferenceCodec.encode(state()).bytes)
        val corrupt = encoded.copyOf()
        corrupt[0] = 0xff.toByte()

        val decoded = PracticalShoppingSavedExactPreferenceCodec.decode(corrupt)

        assertFalse(decoded.accepted)
        assertEquals(
            PracticalShoppingSavedExactPreferenceCodecIssue.MALFORMED_INPUT,
            decoded.codecIssue
        )
    }

    @Test
    fun `input larger than the codec limit is rejected before parsing`() {
        val oversized = ByteArray(PracticalShoppingSavedExactPreferenceCodec.maximumEncodedBytes + 1)

        val decoded = PracticalShoppingSavedExactPreferenceCodec.decode(oversized)

        assertFalse(decoded.accepted)
        assertEquals(
            PracticalShoppingSavedExactPreferenceCodecIssue.INPUT_TOO_LARGE,
            decoded.codecIssue
        )
    }

    @Test
    fun `corrupted gtin that still looks structurally valid is revalidated and rejected`() {
        val encoded = requireNotNull(PracticalShoppingSavedExactPreferenceCodec.encode(state()).bytes)
        val text = String(encoded, Charsets.US_ASCII)
        val validGtinHex = hex("036000291452")
        val invalidGtinHex = hex("036000291453")
        assertTrue(text.contains(validGtinHex))

        val corrupt = text.replace(validGtinHex, invalidGtinHex).toByteArray(Charsets.US_ASCII)
        val decoded = PracticalShoppingSavedExactPreferenceCodec.decode(corrupt)

        assertFalse(decoded.accepted)
        assertNull(decoded.codecIssue)
        assertEquals(
            setOf(PracticalShoppingSavedExactPreferenceLoadIssue.PRODUCT_IDENTITY_UNAVAILABLE),
            decoded.documentIssues
        )
    }

    @Test
    fun `oversized stable key is rejected before persistence encoding`() {
        val base = product("eggs", "036000291452")
        val oversized =
            PracticalShoppingSavedExactPreferenceState(
                productPreferences =
                    listOf(
                        base.copy(
                            itemKey = ShoppingItemKey("x".repeat(513))
                        )
                    ),
                storePreferences = emptyList()
            )

        val encoded = PracticalShoppingSavedExactPreferenceCodec.encode(oversized)

        assertFalse(encoded.accepted)
        assertNull(encoded.bytes)
        assertEquals(
            PracticalShoppingSavedExactPreferenceCodecIssue.FIELD_TOO_LARGE,
            encoded.issue
        )
    }

    @Test
    fun `partial dataset provenance is malformed rather than guessed`() {
        val encoded = requireNotNull(PracticalShoppingSavedExactPreferenceCodec.encode(state()).bytes)
        val text = String(encoded, Charsets.US_ASCII)
        val datasetNameHex = hex(productDataset.displayName)
        assertTrue(text.contains(datasetNameHex))

        val partial = text.replace(datasetNameHex, "~").toByteArray(Charsets.US_ASCII)
        val decoded = PracticalShoppingSavedExactPreferenceCodec.decode(partial)

        assertFalse(decoded.accepted)
        assertEquals(
            PracticalShoppingSavedExactPreferenceCodecIssue.MALFORMED_INPUT,
            decoded.codecIssue
        )
    }

    private fun state(
        products: List<PracticalShoppingSavedExactProductPreference> =
            listOf(product("eggs", "036000291452")),
        stores: List<PracticalShoppingSavedExactStorePreference> =
            listOf(store("north", 11L))
    ): PracticalShoppingSavedExactPreferenceState =
        requireNotNull(
            PracticalShoppingSavedExactPreferenceStateManager.load(
                PracticalShoppingSavedExactPreferenceDocument(
                    schemaVersion = PracticalShoppingSavedExactPreferenceStateManager.currentSchemaVersion,
                    productPreferences = products,
                    storePreferences = stores
                )
            ).state
        )

    private fun product(
        key: String,
        gtin: String
    ): PracticalShoppingSavedExactProductPreference =
        PracticalShoppingSavedExactProductPreference(
            itemKey = ShoppingItemKey(key),
            providerId = EvidenceProviderId("open-food-facts"),
            sourceIdentity = SourceProductIdentity(gtin = gtin),
            dataset = productDataset
        )

    private fun store(
        key: String,
        osmNodeId: Long
    ): PracticalShoppingSavedExactStorePreference =
        PracticalShoppingSavedExactStorePreference(
            storeKey = ShoppingStoreKey(key),
            scope =
                PracticalShoppingStoreIdentityScope(
                    merchantKey = "wikidata:Q483551",
                    locationKey = "osm:node:$osmNodeId",
                    commerceChannelKey = "physical-store"
                ),
            providerId = EvidenceProviderId("openstreetmap"),
            dataset = storeDataset
        )

    private fun hex(value: String): String {
        val chars = "0123456789abcdef"
        return buildString {
            value.toByteArray(Charsets.UTF_8).forEach { byte ->
                val unsigned = byte.toInt() and 0xff
                append(chars[unsigned ushr 4])
                append(chars[unsigned and 0x0f])
            }
        }
    }

    private val productDataset =
        EvidenceDatasetNamespace(
            id = "openfoodfacts-products",
            displayName = "Open Food Facts products",
            licenseId = "ODbL-1.0",
            storageBoundary = EvidenceStorageBoundary.OPEN_SHARE_ALIKE
        )

    private val storeDataset =
        EvidenceDatasetNamespace(
            id = "openstreetmap-places",
            displayName = "OpenStreetMap places",
            licenseId = "ODbL-1.0",
            storageBoundary = EvidenceStorageBoundary.OPEN_SHARE_ALIKE
        )
}
