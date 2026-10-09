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
}
