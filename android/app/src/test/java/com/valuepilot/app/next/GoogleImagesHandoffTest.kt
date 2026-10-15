package com.valuepilot.app.next

import com.valuepilot.app.PracticalShoppingSearchIdentityPresentation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleImagesHandoffTest {

    private val match = PracticalShoppingSearchIdentityPresentation.Match(
        displayName = "Yerba Mate, Miércoles",
        brand = "Marca",
        matchLabel = "exact name",
        packageQuantityLabel = "1 kg",
        sourceLabel = "private provider label",
        identityKey = "sepa:private:001"
    )

    @Test
    fun queryUsesProductIdentityAndArgentinaAreaWithoutPrivateMetadata() {
        val query = requireNotNull(GoogleImagesHandoff.queryFor(match, "Buenos Aires / CABA"))

        assertEquals("Yerba Mate, Miércoles Marca 1 kg CABA, Argentina", query)
        assertFalse(query.contains(match.identityKey))
        assertFalse(query.contains(requireNotNull(match.sourceLabel)))
        assertFalse(query.contains("price"))
    }

    @Test
    fun queryIncludesSourceValidatedGtinWhenItFitsWithoutDroppingArgentinaContext() {
        val identifiedMatch = match.copy(gtin = "7791234567898")
        val query = requireNotNull(GoogleImagesHandoff.queryFor(identifiedMatch, "CABA"))

        assertEquals("Yerba Mate, Miércoles Marca 1 kg EAN 7791234567898 CABA, Argentina", query)
        assertTrue(query.contains(identifiedMatch.gtin!!))

        val boundedQuery = requireNotNull(
            GoogleImagesHandoff.queryFor(
                productName = "N".repeat(136),
                brand = "B".repeat(36),
                packageQuantity = "Q".repeat(28),
                packageType = "P".repeat(24),
                argentinaArea = "CABA",
                gtin = "7791234567898"
            )
        )
        assertTrue(boundedQuery.endsWith("CABA, Argentina"))
        assertFalse(boundedQuery.contains("EAN 7791234567898"))
        assertTrue(boundedQuery.length <= GoogleImagesHandoff.MAX_QUERY_LENGTH)
    }

    @Test
    fun queryIncludesThePackageTypeOnlyWhenTheCallerHasAnExplicitValue() {
        val query = requireNotNull(
            GoogleImagesHandoff.queryFor(
                productName = "Leche entera",
                brand = "Marca",
                packageQuantity = "1 L",
                packageType = "sachet",
                argentinaArea = "CABA"
            )
        )

        assertEquals("Leche entera Marca 1 L sachet CABA, Argentina", query)
    }

    @Test
    fun urlOpensTheRealGoogleImagesSearchWithOnlyTheBoundedQuery() {
        val url = requireNotNull(GoogleImagesHandoff.urlFor(match, "CABA"))

        assertEquals(
            "https://www.google.com/search?tbm=isch&q=Yerba+Mate%2C+Mi%C3%A9rcoles+Marca+1+kg+CABA%2C+Argentina",
            url
        )
        assertTrue(url.startsWith("https://${GoogleImagesHandoff.GOOGLE_IMAGES_HOST}/search?"))
        assertTrue(url.contains("tbm=isch"))
        assertFalse(url.contains(match.identityKey))
        assertFalse(url.contains("private+provider"))
    }

    @Test
    fun missingFieldsControlsAndLongQueriesAreHandledSafely() {
        assertEquals(
            "Yerba Mate CABA, Argentina",
            GoogleImagesHandoff.queryFor("Yerba Mate", " ", null, null, " ")
        )
        assertNull(GoogleImagesHandoff.queryFor("\u0000\t", null, null, null, "CABA"))
        val bounded = requireNotNull(
            GoogleImagesHandoff.queryFor(
                productName = "N".repeat(512),
                brand = "B".repeat(160),
                packageQuantity = "Q".repeat(64),
                packageType = "P".repeat(64),
                argentinaArea = "CABA"
            )
        )
        assertTrue(bounded.length <= GoogleImagesHandoff.MAX_QUERY_LENGTH)
        assertNull(GoogleImagesHandoff.urlForQuery(" "))
    }

}
