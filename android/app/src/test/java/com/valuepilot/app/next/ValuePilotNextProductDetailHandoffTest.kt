package com.valuepilot.app.next

import com.valuepilot.app.ArgentinaBackendContract
import com.valuepilot.app.PracticalShoppingSearchIdentityPresentation
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.json.JSONObject

class ValuePilotNextProductDetailHandoffTest {

    @Test
    fun `round trip preserves the full search presentation and selected offer evidence`() {
        val match = sampleMatch()
        val otherMatch = PracticalShoppingSearchIdentityPresentation.Match(
            displayName = "Leche Descremada",
            brand = "Armonía",
            matchLabel = "name match",
            packageQuantityLabel = "1.000 ml",
            identityKey = "gtin-87654321"
        )
        val presentation = PracticalShoppingSearchIdentityPresentation(
            query = "leche entera",
            matches = listOf(match, otherMatch),
            evaluatedCandidateCount = 4,
            notice = "Qualified identities restored from the current Search result.",
            page = 1,
            hasMore = true,
            nextPage = 2,
            limitReached = false
        )
        val payload = ValuePilotNextProductDetailHandoffCodec.encode(
            query = " leche entera ",
            presentation = presentation,
            selectedIdentityKey = match.identityKey
        )

        val restored = requireNotNull(
            ValuePilotNextProductDetailHandoffCodec.decode(payload)
        )

        assertEquals("leche entera", restored.query)
        assertEquals(match.identityKey, restored.selectedIdentityKey)
        assertEquals(2, restored.presentation.matches.size)
        assertEquals(4, restored.presentation.evaluatedCandidateCount)
        assertEquals(1, restored.presentation.page)
        assertEquals(2, restored.presentation.nextPage)
        assertEquals(match.displayName, restored.selectedMatch.displayName)
        assertEquals(match.gtin, restored.selectedMatch.gtin)
        assertEquals(match.qualifiedOffers, restored.selectedMatch.qualifiedOffers)
        assertEquals("ARS", restored.selectedMatch.qualifiedOffers.single().listPrice.currency)
        assertEquals("1299.50", restored.selectedMatch.qualifiedOffers.single().listPrice.amount)
    }

    @Test
    fun `rejects malformed or cross identity offer payloads`() {
        val payload = JSONObject(
            ValuePilotNextProductDetailHandoffCodec.encode("leche", sampleMatch())
        )
        val match = payload.getJSONArray("matches").getJSONObject(0)
        match.getJSONArray("offers")
            .getJSONObject(0)
            .put("productEvidenceKey", "different-identity")

        assertNull(ValuePilotNextProductDetailHandoffCodec.decode(payload.toString()))
        assertNull(ValuePilotNextProductDetailHandoffCodec.decode("{}"))
        assertNull(
            ValuePilotNextProductDetailHandoffCodec.decode(
                "{\"query\":\"leche\"}" + "x".repeat(65 * 1024)
            )
        )
    }

    private fun sampleMatch(): PracticalShoppingSearchIdentityPresentation.Match {
        val money = ArgentinaBackendContract.Money(
            amount = "1299.50",
            currency = "ARS",
            decimal = BigDecimal("1299.50")
        )
        val quantity = ArgentinaBackendContract.Quantity(
            dimension = "VOLUME",
            amount = "1",
            unit = "L",
            baseAmount = "1000",
            baseUnit = "ML"
        )
        val offer = ArgentinaBackendContract.SearchOffer(
            offerKey = "offer-1",
            productEvidenceKey = "gtin-12345678",
            storeKey = "store-1",
            storeName = "Coto",
            storeAddress = mapOf("street" to "Av. Siempre Viva 123"),
            storeLatitude = "-34.60",
            storeLongitude = "-58.40",
            distanceKm = "1.4",
            listPrice = money,
            referencePrice = null,
            quantity = quantity,
            quantityStatus = "EXACT",
            unitPrice = money,
            unitPriceBasis = "L",
            releaseDate = "2026-10-09",
            freshnessStatus = ArgentinaBackendContract.Freshness.FRESH,
            availability = ArgentinaBackendContract.Availability.UNKNOWN,
            provenance = ArgentinaBackendContract.Provenance(
                provider = "qualified-source",
                releaseDate = "2026-10-09",
                freshnessStatus = ArgentinaBackendContract.Freshness.FRESH,
                source = "catalog"
            )
        )
        return PracticalShoppingSearchIdentityPresentation.Match(
            displayName = "Leche Entera",
            brand = "Armonía",
            matchLabel = "exact barcode",
            packageQuantityLabel = "1.000 ml",
            sourceLabel = "qualified-source",
            identityKey = "gtin-12345678",
            gtin = "12345678",
            qualifiedOffers = listOf(offer)
        )
    }
}
