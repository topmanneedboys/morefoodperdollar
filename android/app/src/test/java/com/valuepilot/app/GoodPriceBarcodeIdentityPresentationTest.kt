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
import org.junit.Assert.assertTrue
import org.junit.Test

class GoodPriceBarcodeIdentityPresentationTest {

    @Test
    fun `only exact barcode identities become selectable name suggestions`() {
        val exact = product("a", "Whole milk", "Example dairy")
        val fuzzy = product("b", "Whole milk alternative", null)
        val presentation =
            GoodPriceBarcodeIdentityPresentation.from(
                gtin = "036000291452",
                result =
                    OfflineCatalogDiscoveryResult(
                        normalizedQuery = "036000291452",
                        evaluatedCandidateCount = 2,
                        matches =
                            listOf(
                                OfflineCatalogDiscoveryMatch(exact, OfflineCatalogMatchKind.EXACT_GTIN),
                                OfflineCatalogDiscoveryMatch(fuzzy, OfflineCatalogMatchKind.TOKEN_MATCH)
                            )
                    )
            )

        assertEquals(1, presentation.options.size)
        assertEquals("Whole milk · Example dairy", presentation.options.single().label)
        assertEquals(2, presentation.evaluatedCandidateCount)
    }

    @Test
    fun `duplicate exact identity labels collapse without adding factual fields`() {
        val product = product("a", "Free-range eggs", "Example farm")
        val presentation =
            GoodPriceBarcodeIdentityPresentation.from(
                gtin = "4006381333931",
                result =
                    OfflineCatalogDiscoveryResult(
                        normalizedQuery = "4006381333931",
                        evaluatedCandidateCount = 2,
                        matches =
                            listOf(
                                OfflineCatalogDiscoveryMatch(product, OfflineCatalogMatchKind.EXACT_GTIN),
                                OfflineCatalogDiscoveryMatch(product.copy(recordId = "b"), OfflineCatalogMatchKind.EXACT_GTIN)
                            )
                    )
            )

        assertTrue(presentation.options.single().displayName == "Free-range eggs")
        assertEquals("Example farm", presentation.options.single().brand)
    }

    private fun product(recordId: String, name: String, brand: String?): OfflineCatalogProduct =
        OfflineCatalogProduct(
            recordId = recordId,
            providerId = EvidenceProviderId("off"),
            dataset =
                EvidenceDatasetNamespace(
                    id = "off-ca",
                    displayName = "Open Food Facts Canada",
                    licenseId = "ODbL-1.0",
                    storageBoundary = EvidenceStorageBoundary.OPEN_SHARE_ALIKE
                ),
            sourceIdentity = SourceProductIdentity(gtin = "036000291452"),
            displayName = name,
            brand = brand,
            canonicalSearchName = JvmTextCanonicalizer.search(name),
            canonicalSearchBrand = brand?.let(JvmTextCanonicalizer::search)
        )
}
