package com.valuepilot.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class OfflineCatalogDiscoveryTest {

    private val canonicalizer =
        object : TextCanonicalizer {
            override fun identity(value: String?): String = value.orEmpty().trim().lowercase()

            override fun search(value: String?): String =
                value.orEmpty()
                    .trim()
                    .lowercase()
                    .replace(Regex("[^a-z0-9]+"), " ")
                    .replace(Regex("\\s+"), " ")
        }

    @Test
    fun `exact barcode matching is canonical and does not fall back to text`() {
        val product = product("milk-upc", "whole milk", gtin = "036000291452")
        val result = discover("00036000291452", listOf(product))

        assertEquals(OfflineCatalogMatchKind.EXACT_GTIN, result.matches.single().kind)
        assertEquals("milk-upc", result.matches.single().product.recordId)
    }

    @Test
    fun `exact name wins and ties are deterministic independent of input order`() {
        val exact = product("exact", "whole milk")
        val broad = product("broad", "whole milk chocolate")

        val first = discover("Whole Milk", listOf(broad, exact))
        val second = discover("Whole Milk", listOf(exact, broad))

        assertEquals(listOf("exact", "broad"), first.matches.map { it.product.recordId })
        assertEquals(first, second)
        assertEquals(OfflineCatalogMatchKind.EXACT_NAME, first.matches.first().kind)
        assertEquals(OfflineCatalogMatchKind.TOKEN_MATCH, first.matches.last().kind)
    }

    @Test
    fun `prefix and one-edit typo matching help users find products`() {
        val milk = product("milk", "whole milk")
        val yogurt = product("yogurt", "plain yogurt")

        val prefix = discover("mil", listOf(milk))
        val typo = discover("yogrt", listOf(yogurt))

        assertEquals(OfflineCatalogMatchKind.PREFIX_MATCH, prefix.matches.single().kind)
        assertEquals(OfflineCatalogMatchKind.TYPO_MATCH, typo.matches.single().kind)
    }

    @Test
    fun `irrelevant and partially matching products are excluded`() {
        val result =
            discover(
                "almond milk",
                listOf(
                    product("almond", "almond milk"),
                    product("milk", "whole milk"),
                    product("almond-cereal", "almond cereal")
                )
            )

        assertEquals(listOf("almond"), result.matches.map { it.product.recordId })
    }

    @Test
    fun `brand and curated aliases aid discovery without changing product identity`() {
        val product =
            product(
                id = "oat",
                name = "Oat Beverage",
                brand = "North Star",
                aliases = listOf("plant milk")
            )

        val result = discover("North Star", listOf(product))
        val aliasResult = discover("plant milk", listOf(product))

        assertEquals("oat", result.matches.single().product.recordId)
        assertEquals("oat", aliasResult.matches.single().product.recordId)
        assertEquals(PracticalShoppingProductIntentRelationship.CATALOG_SUGGESTION,
            product.identitySuggestion(ShoppingItemKey("milk")).relationship)
    }

    @Test
    fun `suggestions never become automatic exact bindings`() {
        val product = product("milk", "whole milk", gtin = "036000291452")
        val request = ShoppingRequest(itemKeys = listOf(ShoppingItemKey("milk")))
        val candidate = product.identitySuggestion(ShoppingItemKey("milk"))

        val resolution = PracticalShoppingProductIdentityResolver.resolve(request, listOf(candidate))

        assertEquals(PracticalShoppingProductIdentityResolutionStatus.NEEDS_EXPLICIT_SELECTION,
            resolution.itemResolutions.single().status)
        assertTrue(resolution.automaticBindings.isEmpty())
    }

    @Test
    fun `candidate and result bounds fail closed`() {
        try {
            OfflineCatalogDiscoveryRequest(
                rawQuery = "milk",
                candidates = (0..OfflineCatalogDiscoveryRequest.MAX_CANDIDATES).map { index ->
                    product("milk-$index", "milk $index")
                }
            )
            fail("Expected candidate bound rejection")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("candidate"))
        }

        try {
            OfflineCatalogDiscoveryRequest(rawQuery = " ", candidates = emptyList())
            fail("Expected blank query rejection")
        } catch (expected: IllegalArgumentException) {
            assertFalse(expected.message.orEmpty().isEmpty())
        }
    }

    @Test
    fun `catalog record has no price or availability authority`() {
        val names = OfflineCatalogProduct::class.java.declaredFields.map { it.name }.toSet()

        assertFalse(names.any { it.contains("price", ignoreCase = true) })
        assertFalse(names.any { it.contains("availability", ignoreCase = true) })
        assertFalse(names.any { it.contains("stock", ignoreCase = true) })
    }

    private fun discover(query: String, products: List<OfflineCatalogProduct>): OfflineCatalogDiscoveryResult =
        OfflineCatalogDiscoveryEngine.discover(
            request = OfflineCatalogDiscoveryRequest(rawQuery = query, candidates = products),
            canonicalizer = canonicalizer
        )

    private fun product(
        id: String,
        name: String,
        gtin: String? = null,
        brand: String? = null,
        aliases: List<String> = emptyList()
    ): OfflineCatalogProduct {
        val providerId = EvidenceProviderId("catalog-fixture")
        val dataset =
            EvidenceDatasetNamespace(
                id = "catalog-fixture-dataset",
                displayName = "Catalog fixture",
                licenseId = "fixture-reviewed-rights",
                storageBoundary = EvidenceStorageBoundary.OPEN_SHARE_ALIKE
            )
        val sourceIdentity =
            SourceProductIdentity(
                providerItemId = id,
                gtin = gtin
            )
        return OfflineCatalogProduct(
            recordId = id,
            providerId = providerId,
            dataset = dataset,
            sourceIdentity = sourceIdentity,
            displayName = name,
            brand = brand,
            canonicalSearchName = name.lowercase().replace(Regex("[^a-z0-9]+"), " ").trim(),
            canonicalSearchBrand = brand?.lowercase()?.replace(Regex("[^a-z0-9]+"), " ")?.trim(),
            canonicalSearchAliases = aliases
        )
    }
}
