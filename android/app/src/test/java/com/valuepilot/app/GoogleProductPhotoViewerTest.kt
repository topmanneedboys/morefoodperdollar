package com.valuepilot.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleProductPhotoViewerTest {

    private val identity =
        PracticalShoppingPrivateIdentity(
            identityKey = "sepa:private:001",
            displayName = "Yerba Mate, Miércoles",
            brand = "Marca",
            sourceLabel = "Argentina SEPA / Precios Claros",
            packageQuantityLabel = "1 kg"
        )

    @Test
    fun queryUsesOnlyPublicIdentityFieldsAndOmitsPrivateProviderMetadata() {
        val query = requireNotNull(GoogleProductPhotoViewer.queryFor(identity))

        assertEquals("Yerba Mate, Miércoles Marca 1 kg", query)
        assertFalse(query.contains(identity.identityKey))
        assertFalse(query.contains(requireNotNull(identity.sourceLabel)))
    }

    @Test
    fun uriIsHttpsGoogleImagesAndCarriesOnlyBoundedQueryParameters() {
        val url = requireNotNull(GoogleProductPhotoViewer.urlFor(identity))

        assertTrue(url.startsWith("https://${GoogleProductPhotoViewer.GOOGLE_IMAGES_HOST}/search?"))
        assertTrue(url.contains("tbm=isch"))
        assertTrue(url.contains("q=Yerba+Mate%2C+Mi%C3%A9rcoles+Marca+1+kg"))
        assertEquals(
            "https://www.google.com/search?tbm=isch&q=Yerba+Mate%2C+Mi%C3%A9rcoles+Marca+1+kg",
            url
        )
        assertFalse(url.contains(identity.identityKey))
        assertFalse(url.contains("sourceLabel"))
    }

    @Test
    fun missingOptionalFieldsAreOmittedAndBlankNamesFailClosed() {
        assertEquals(
            "Yerba Mate",
            GoogleProductPhotoViewer.queryFor("Yerba Mate", " ", null)
        )
        assertNull(GoogleProductPhotoViewer.queryFor("\u0000\t", null, null))
        assertNull(GoogleProductPhotoViewer.urlForQuery(" "))
    }

    @Test
    fun queryAndUriAreBoundedAndControlCharactersDoNotEscapeTheSearch() {
        val query =
            requireNotNull(
                GoogleProductPhotoViewer.queryFor(
                    displayName = "Yerba\nMate",
                    brand = "Marca\u0000",
                    packageQuantityLabel = "1 kg"
                )
            )
        assertFalse(query.contains('\n'))
        assertFalse(query.contains('\u0000'))
        assertTrue(query.length <= GoogleProductPhotoViewer.MAX_QUERY_LENGTH)

        val url = requireNotNull(GoogleProductPhotoViewer.urlForQuery("x".repeat(500)))
        assertTrue(url.length < 400)
    }
}
