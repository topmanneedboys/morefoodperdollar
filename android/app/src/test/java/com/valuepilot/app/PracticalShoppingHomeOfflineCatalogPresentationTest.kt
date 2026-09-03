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

class PracticalShoppingHomeOfflineCatalogPresentationTest {

    @Test
    fun `identity suggestions are deterministic and explicitly not offers`() {
        val presentation =
            PracticalShoppingHomeOfflineCatalogPresentation.from(
                query = " milk ",
                result =
                    OfflineCatalogDiscoveryResult(
                        normalizedQuery = "milk",
                        evaluatedCandidateCount = 2,
                        matches =
                            listOf(
                                OfflineCatalogDiscoveryMatch(
                                    product("whole-milk", "Whole Milk", "Dairy Best"),
                                    OfflineCatalogMatchKind.EXACT_NAME
                                ),
                                OfflineCatalogDiscoveryMatch(
                                    product("oat-milk", "Oat Milk"),
                                    OfflineCatalogMatchKind.TOKEN_MATCH
                                )
                            )
                    )
            )

        assertEquals("milk", presentation.query)
        assertEquals(listOf("Whole Milk", "Oat Milk"), presentation.matches.map { it.displayName })
        assertEquals(listOf("exact name", "name match"), presentation.matches.map { it.matchLabel })
        assertEquals(2, presentation.evaluatedCandidateCount)
        assertTrue(presentation.message.contains("Identity suggestions"))
        assertTrue(presentation.message.contains("Open Food Facts"))
        assertTrue(presentation.message.contains("ODbL-1.0"))
        assertTrue(presentation.message.contains("GTA and Metro Vancouver"))
        assertTrue(presentation.message.contains("Checked 2 bundled product identities"))
        assertTrue(presentation.message.contains("no current prices"))
        assertFalse(presentation.message.contains("CAD"))
        assertFalse(presentation.message.contains("stocked"))
    }

    @Test
    fun `empty identity result remains useful without pretending a miss is unavailable`() {
        val presentation =
            PracticalShoppingHomeOfflineCatalogPresentation.from(
                query = "dragonfruit",
                result =
                    OfflineCatalogDiscoveryResult(
                        normalizedQuery = "dragonfruit",
                        evaluatedCandidateCount = 3_000,
                        matches = emptyList()
                    )
            )

        assertTrue(presentation.matches.isEmpty())
        assertEquals(3_000, presentation.evaluatedCandidateCount)
        assertTrue(presentation.message.contains("Checked 3000 bundled product identities"))
        assertTrue(presentation.message.contains("No matching product identity"))
        assertTrue(presentation.message.contains("no current prices"))
    }

    private fun product(id: String, name: String, brand: String? = null): OfflineCatalogProduct =
        OfflineCatalogProduct(
            recordId = id,
            providerId = EvidenceProviderId("open-food-facts"),
            dataset =
                EvidenceDatasetNamespace(
                    id = "off-ca",
                    displayName = "Open Food Facts Canada",
                    licenseId = "odbl-1.0",
                    storageBoundary = EvidenceStorageBoundary.OPEN_SHARE_ALIKE
                ),
            sourceIdentity = SourceProductIdentity(providerItemId = id),
            displayName = name,
            brand = brand,
            canonicalSearchName = name.lowercase(),
            canonicalSearchBrand = brand?.lowercase()
        )
}
