package com.valuepilot.app

import com.valuepilot.core.EvidenceDatasetNamespace
import com.valuepilot.core.EvidenceProviderId
import com.valuepilot.core.EvidenceStorageBoundary
import com.valuepilot.core.OfflineCatalogDiscoveryMatch
import com.valuepilot.core.OfflineCatalogDiscoveryResult
import com.valuepilot.core.OfflineCatalogMatchKind
import com.valuepilot.core.OfflineCatalogProduct
import com.valuepilot.core.SourceProductIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticalShoppingSearchIdentityPresentationTest {

    @Test
    fun `maps identity matches with deterministic labels and no offer facts`() {
        val presentation =
            PracticalShoppingSearchIdentityPresentation.from(
                query = "  milk  ",
                result =
                    OfflineCatalogDiscoveryResult(
                        normalizedQuery = "milk",
                        evaluatedCandidateCount = 30_000,
                        matches =
                            listOf(
                                OfflineCatalogDiscoveryMatch(
                                    product("a", "Whole milk", "Dairyland"),
                                    OfflineCatalogMatchKind.EXACT_NAME
                                ),
                                OfflineCatalogDiscoveryMatch(
                                    product("b", "Milk chocolate", null),
                                    OfflineCatalogMatchKind.TOKEN_MATCH
                                )
                            )
                    )
            )

        assertEquals("milk", presentation.query)
        assertEquals(
            listOf("Whole milk" to "exact name", "Milk chocolate" to "name match"),
            presentation.matches.map { it.displayName to it.matchLabel }
        )
        assertEquals("Dairyland", presentation.matches.first().brand)
        assertEquals(30_000, presentation.evaluatedCandidateCount)
        assertTrue(presentation.summaryMessage.contains("no current prices"))
        assertTrue(presentation.summaryMessage.contains("Checked 30000 bundled product identities"))
        assertFalse(presentation.message.contains("CAD"))
        assertFalse(presentation.message.contains("Store:"))
        assertFalse(presentation.message.contains("available at"))
    }

    @Test
    fun `empty matches do not imply unavailability`() {
        val presentation =
            PracticalShoppingSearchIdentityPresentation.from(
                query = "dragonfruit",
                result =
                    OfflineCatalogDiscoveryResult(
                        normalizedQuery = "dragonfruit",
                        evaluatedCandidateCount = 30_000,
                        matches = emptyList()
                    )
            )

        assertTrue(presentation.matches.isEmpty())
        assertTrue(presentation.message.contains("No matching product identity"))
        assertTrue(presentation.message.contains("does not mean the product is unavailable"))
    }

    private fun product(id: String, name: String, brand: String?): OfflineCatalogProduct =
        OfflineCatalogProduct(
            recordId = id,
            providerId = EvidenceProviderId("open-food-facts"),
            dataset =
                EvidenceDatasetNamespace(
                    id = "off-ca",
                    displayName = "Open Food Facts",
                    licenseId = "ODbL-1.0",
                    storageBoundary = EvidenceStorageBoundary.OPEN_SHARE_ALIKE
                ),
            sourceIdentity = SourceProductIdentity(providerItemId = "off-$id"),
            displayName = name,
            brand = brand,
            canonicalSearchName = name.lowercase(),
            canonicalSearchBrand = brand?.lowercase()
        )
}
