package com.valuepilot.app.next

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ValuePilotNextHtmlPreviewContractTest {

    @Test
    fun `accepts only the supplied product identity and canonical query`() {
        val product = requireNotNull(
            ValuePilotNextHtmlPreviewContract.productFor("milk-serenisima-1l")
        )
        val request = requireNotNull(
            ValuePilotNextHtmlPreviewContract.parseOpenGoogleImagesMessage(
                "{\"type\":\"OPEN_GOOGLE_IMAGES\",\"productId\":\"${product.productId}\",\"query\":\"${product.query}\"}"
            )
        )

        assertEquals(product.productId, request.productId)
        assertEquals(product.query, request.query)
        assertEquals(
            "https://www.google.com/search?tbm=isch&q=La%20Seren%C3%ADsima%20Leche%20Entera%20Cl%C3%A1sica%203%25%201.000%20ml%20envase%20Argentina",
            ValuePilotNextHtmlPreviewContract.urlFor(request)
        )
    }

    @Test
    fun `rejects arbitrary url data malformed messages and mismatched query`() {
        val valid = "{\"type\":\"OPEN_GOOGLE_IMAGES\",\"productId\":\"milk-serenisima-1l\",\"query\":\"La Serenísima Leche Entera Clásica 3% 1.000 ml envase Argentina\"}"
        assertNull(ValuePilotNextHtmlPreviewContract.parseOpenGoogleImagesMessage(null))
        assertNull(ValuePilotNextHtmlPreviewContract.parseOpenGoogleImagesMessage("not json"))
        assertNull(ValuePilotNextHtmlPreviewContract.parseOpenGoogleImagesMessage(valid.replace("OPEN_GOOGLE_IMAGES", "OPEN_URL")))
        assertNull(ValuePilotNextHtmlPreviewContract.parseOpenGoogleImagesMessage(valid.replace("envase Argentina", "https://evil.example")))
        assertNull(ValuePilotNextHtmlPreviewContract.parseOpenGoogleImagesMessage(valid.replace("milk-serenisima-1l", "../../evil")))
    }

    @Test
    fun `rejects controls and oversized messages`() {
        val product = requireNotNull(ValuePilotNextHtmlPreviewContract.productFor("rice-arroz-1kg"))
        val valid = "{\"type\":\"OPEN_GOOGLE_IMAGES\",\"productId\":\"${product.productId}\",\"query\":\"${product.query}\"}"
        assertNull(
            ValuePilotNextHtmlPreviewContract.parseOpenGoogleImagesMessage(
                valid.replace("Argentina", "Argentina\u0001")
            )
        )
        assertNull(
            ValuePilotNextHtmlPreviewContract.parseOpenGoogleImagesMessage(
                valid + " ".repeat(1_024)
            )
        )
        assertTrue(
            ValuePilotNextHtmlPreviewContract.urlFor(
                requireNotNull(ValuePilotNextHtmlPreviewContract.parseOpenGoogleImagesMessage(valid))
            ).startsWith("https://www.google.com/")
        )
    }
    @Test
    fun acceptsSupportedPreviewLanguages() {
        assertEquals(
            "en",
            ValuePilotNextHtmlPreviewContract
                .parseSetLanguageMessage("{\"type\":\"SET_LANGUAGE\",\"language\":\"en\"}")
                ?.language
        )
        assertEquals(
            "es-AR",
            ValuePilotNextHtmlPreviewContract
                .parseSetLanguageMessage("{\"type\":\"SET_LANGUAGE\",\"language\":\"es-AR\"}")
                ?.language
        )
    }

    @Test
    fun rejectsUnknownLanguageMessagesAndOversizedPayloads() {
        assertNull(ValuePilotNextHtmlPreviewContract.parseSetLanguageMessage(null))
        assertNull(
            ValuePilotNextHtmlPreviewContract.parseSetLanguageMessage(
                "{\"type\":\"SET_LANGUAGE\",\"language\":\"fr\"}"
            )
        )
        assertNull(
            ValuePilotNextHtmlPreviewContract.parseSetLanguageMessage(
                "{\"type\":\"SET_LANGUAGE\",\"language\":\"en\"}" + " ".repeat(256)
            )
        )
        assertNull(
            ValuePilotNextHtmlPreviewContract.parseSetLanguageMessage(
                "{\"type\":\"OPEN_GOOGLE_IMAGES\",\"language\":\"en\"}"
            )
        )
    }

    @Test
    fun acceptsOnlyBoundedNativeSearchQueries() {
        assertEquals(
            "leche entera",
            ValuePilotNextHtmlPreviewContract
                .parseNativeSearchMessage(
                    "{\"type\":\"SEARCH_VALUEPILOT\",\"query\":\" leche entera \"}"
                )
                ?.query
        )
        assertNull(
            ValuePilotNextHtmlPreviewContract.parseNativeSearchMessage(
                "{\"type\":\"SEARCH_VALUEPILOT\",\"query\":\"\u0001\"}"
            )
        )
        assertNull(
            ValuePilotNextHtmlPreviewContract.parseNativeSearchMessage(
                "{\"type\":\"SEARCH_VALUEPILOT\",\"query\":\"${"x".repeat(97)}\"}"
            )
        )
    }

    @Test
    fun buildsNativeGoogleImagesQueriesFromIdentityFields() {
        val request = requireNotNull(
            ValuePilotNextHtmlPreviewContract.parseNativeGoogleImagesMessage(
                "{\"type\":\"OPEN_GOOGLE_IMAGES_NATIVE\",\"brand\":\"Armonía\",\"name\":\"Leche Entera\",\"quantity\":\"1.000 ml\"}"
            )
        )
        assertEquals(
            "Armonía Leche Entera 1.000 ml envase Argentina",
            request.query
        )
        assertTrue(
            ValuePilotNextHtmlPreviewContract.urlFor(request)
                .contains("Armon%C3%ADa%20Leche%20Entera")
        )
        assertNull(
            ValuePilotNextHtmlPreviewContract.parseNativeGoogleImagesMessage(
                "{\"type\":\"OPEN_GOOGLE_IMAGES_NATIVE\",\"name\":\"\"}"
            )
        )
    }

    @Test
    fun acceptsNativeSavedReadAndStrictIdentityActions() {
        assertTrue(
            ValuePilotNextHtmlPreviewContract
                .parseNativeSavedReadMessage("{\"type\":\"READ_NATIVE_SAVED\"}") != null
        )
        val save = requireNotNull(
            ValuePilotNextHtmlPreviewContract.parseNativeSaveMessage(
                "{" +
                    "\"type\":\"SAVE_VALUEPILOT_IDENTITY\"," +
                    "\"identityKey\":\"gtin-123\"," +
                    "\"displayName\":\"Leche Entera\"," +
                    "\"brand\":\"Armonía\"," +
                    "\"matchLabel\":\"exact name\"," +
                    "\"packageQuantityLabel\":\"1.000 ml\"," +
                    "\"sourceLabel\":\"qualified Argentina source\"," +
                    "\"gtin\":\"12345678\"" +
                    "}"
            )
        )
        assertEquals("gtin-123", save.identityKey)
        assertEquals("qualified Argentina source", save.sourceLabel)
        assertEquals(
            "gtin-123",
            requireNotNull(
                ValuePilotNextHtmlPreviewContract.parseNativeRemoveMessage(
                    "{\"type\":\"REMOVE_VALUEPILOT_IDENTITY\",\"identityKey\":\"gtin-123\"}"
                )
            ).identityKey
        )
    }

    @Test
    fun rejectsNativeSavedIdentitySpoofingAndControls() {
        assertTrue(
            ValuePilotNextHtmlPreviewContract
                .parseNativeSavedReadMessage("{\"type\":\"READ_NATIVE_SAVED\",\"extra\":true}") != null
        )
        assertNull(
            ValuePilotNextHtmlPreviewContract.parseNativeSaveMessage(
                "{" +
                    "\"type\":\"SAVE_VALUEPILOT_IDENTITY\"," +
                    "\"identityKey\":\"x\"," +
                    "\"displayName\":\"Leche\"," +
                    "\"matchLabel\":\"exact name\"," +
                    "\"sourceLabel\":\"\"" +
                    "}"
            )
        )
        assertNull(
            ValuePilotNextHtmlPreviewContract.parseNativeRemoveMessage(
                "{\"type\":\"REMOVE_VALUEPILOT_IDENTITY\",\"identityKey\":\"x\\u0001\"}"
            )
        )
        assertNull(
            ValuePilotNextHtmlPreviewContract.parseNativeSavedReadMessage(
                "{\"type\":\"READ_NATIVE_SEARCH\"}"
            )
        )
    }

    @Test
    fun acceptsBoundedNativeTripInputs() {
        val request = requireNotNull(
            ValuePilotNextHtmlPreviewContract.parseNativeTripMessage(
                "{" +
                    "\"type\":\"CALCULATE_NATIVE_TRIP\"," +
                    "\"oneWayDistance\":\"8\"," +
                    "\"fuelConsumption\":\"7.5\"," +
                    "\"fuelPrice\":\"1200\"," +
                    "\"tolls\":\"150\"," +
                    "\"parking\":\"300\"" +
                    "}"
            )
        )
        assertEquals("8", request.oneWayDistance)
        assertEquals("300", request.parking)
        assertNull(
            ValuePilotNextHtmlPreviewContract.parseNativeTripMessage(
                "{\"type\":\"CALCULATE_NATIVE_TRIP\",\"oneWayDistance\":\"8e3\",\"fuelConsumption\":\"7.5\",\"fuelPrice\":\"1200\",\"tolls\":\"0\",\"parking\":\"0\"}"
            )
        )
    }
}
