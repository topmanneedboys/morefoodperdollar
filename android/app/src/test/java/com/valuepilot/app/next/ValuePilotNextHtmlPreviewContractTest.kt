package com.valuepilot.app.next

import com.valuepilot.app.ArgentinaPrivateBasketLineProjection
import com.valuepilot.app.ArgentinaPrivateBasketRenderState
import com.valuepilot.app.PracticalShoppingSearchIdentityPresentation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONObject

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
        val request = requireNotNull(
            ValuePilotNextHtmlPreviewContract.parseNativeSearchMessage(
                "{\"type\":\"SEARCH_VALUEPILOT\",\"query\":\" leche entera \",\"selectionId\":\"egg:whole_egg\"}"
            )
        )
        assertEquals("leche entera", request.query)
        assertEquals("egg:whole_egg", request.selectionId)
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
        assertNull(
            ValuePilotNextHtmlPreviewContract.parseNativeSearchMessage(
                "{\"type\":\"SEARCH_VALUEPILOT\",\"query\":\"eggs\",\"selectionId\":\"rice:grain!\"}"
            )
        )
    }

    @Test
    fun acceptsOnlyTheNativeSearchLoadMoreCommand() {
        assertTrue(
            ValuePilotNextHtmlPreviewContract
                .parseNativeSearchLoadMoreMessage("{\"type\":\"LOAD_MORE_VALUEPILOT\"}") != null
        )
        assertNull(
            ValuePilotNextHtmlPreviewContract
                .parseNativeSearchLoadMoreMessage("{\"type\":\"SEARCH_VALUEPILOT\",\"query\":\"rice\"}")
        )
        assertNull(
            ValuePilotNextHtmlPreviewContract
                .parseNativeSearchLoadMoreMessage("{\"type\":\"LOAD_MORE_VALUEPILOT\"}" + " ".repeat(256))
        )
    }

    @Test
    fun acceptsOnlyTheNativeClarificationFacetLoadMoreCommand() {
        assertTrue(
            ValuePilotNextHtmlPreviewContract
                .parseNativeSearchFacetMoreMessage("{\"type\":\"LOAD_MORE_VALUEPILOT_FACETS\"}") != null
        )
        assertNull(
            ValuePilotNextHtmlPreviewContract
                .parseNativeSearchFacetMoreMessage("{\"type\":\"LOAD_MORE_VALUEPILOT\"}")
        )
        assertNull(
            ValuePilotNextHtmlPreviewContract
                .parseNativeSearchFacetMoreMessage("{\"type\":\"LOAD_MORE_VALUEPILOT_FACETS\"}" + " ".repeat(256))
        )
    }

    @Test
    fun acceptsNativeProductDetailHandoffWithBoundedIdentityAndQuery() {
        val request = requireNotNull(
            ValuePilotNextHtmlPreviewContract.parseNativeProductDetailMessage(
                "{\"type\":\"OPEN_NATIVE_PRODUCT_DETAIL\",\"identityKey\":\"gtin-123\",\"query\":\"leche entera\"}"
            )
        )
        assertEquals("gtin-123", request.identityKey)
        assertEquals("leche entera", request.query)
        assertTrue(
            ValuePilotNextHtmlPreviewContract.productDetailQueryMatches(
                currentQuery = " leche entera ",
                requestedQuery = "leche entera"
            )
        )
        assertTrue(
            !ValuePilotNextHtmlPreviewContract.productDetailQueryMatches(
                currentQuery = "leche entera",
                requestedQuery = "arroz"
            )
        )
        assertNull(
            ValuePilotNextHtmlPreviewContract.parseNativeProductDetailMessage(
                "{\"type\":\"OPEN_NATIVE_PRODUCT_DETAIL\",\"identityKey\":\"gtin-123\",\"query\":\"leche\\u0001\"}"
            )
        )
        assertNull(
            ValuePilotNextHtmlPreviewContract.parseNativeProductDetailMessage(
                "{\"type\":\"OPEN_NATIVE_PRODUCT_DETAIL\",\"identityKey\":\"gtin-123\",\"query\":\"${"x".repeat(97)}\"}"
            )
        )
    }

    @Test
    fun acceptsOnlyBoundedNativeMapContextRequests() {
        val request = requireNotNull(
            ValuePilotNextHtmlPreviewContract.parseNativeMapMessage(
                "{\"type\":\"OPEN_NATIVE_MAP_CONTEXT\",\"identityKey\":\"identity-123\",\"offerKey\":\"offer-123\",\"query\":\"leche entera\"}"
            )
        )
        assertEquals("identity-123", request.identityKey)
        assertEquals("offer-123", request.offerKey)
        assertEquals("leche entera", request.query)
        assertNull(
            ValuePilotNextHtmlPreviewContract.parseNativeMapMessage(
                "{\"type\":\"OPEN_NATIVE_MAP_CONTEXT\",\"identityKey\":\"identity-123\",\"offerKey\":\"\",\"query\":\"leche\"}"
            )
        )
        assertNull(
            ValuePilotNextHtmlPreviewContract.parseNativeMapMessage(
                "{\"type\":\"OPEN_NATIVE_MAP_CONTEXT\",\"identityKey\":\"identity-123\",\"offerKey\":\"offer-123\",\"query\":\"leche\\u0001\"}"
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
        assertEquals(
            "SAVED",
            requireNotNull(
                ValuePilotNextHtmlPreviewContract
                    .parseNativeSavedToolsMessage("{\"type\":\"OPEN_NATIVE_SAVED_TOOLS\"}")
            ).destination
        )
        assertEquals(
            "OBSERVED_PRICE_SELECTION",
            requireNotNull(
                ValuePilotNextHtmlPreviewContract.parseNativeSavedToolsMessage(
                    "{\"type\":\"OPEN_NATIVE_SAVED_TOOLS\",\"destination\":\"OBSERVED_PRICE_SELECTION\"}"
                )
            ).destination
        )
        assertEquals(
            "PRIVATE_PRICE_HISTORY",
            requireNotNull(
                ValuePilotNextHtmlPreviewContract.parseNativeSavedToolsMessage(
                    "{\"type\":\"OPEN_NATIVE_SAVED_TOOLS\",\"destination\":\"PRIVATE_PRICE_HISTORY\"}"
                )
            ).destination
        )
        assertEquals(
            "GOOD_PRICE_CHECK",
            requireNotNull(
                ValuePilotNextHtmlPreviewContract.parseNativeJourneyMessage(
                    "{\"type\":\"OPEN_NATIVE_JOURNEY\",\"destination\":\"GOOD_PRICE_CHECK\"}"
                )
            ).destination
        )
        assertEquals(
            "COMPARE",
            requireNotNull(
                ValuePilotNextHtmlPreviewContract.parseNativeJourneyMessage(
                    "{\"type\":\"OPEN_NATIVE_JOURNEY\",\"destination\":\"COMPARE\"}"
                )
            ).destination
        )
        val goodPriceFromSaved = requireNotNull(
            ValuePilotNextHtmlPreviewContract.parseNativeGoodPriceFromSavedMessage(
                "{\"type\":\"OPEN_NATIVE_GOOD_PRICE_FROM_SAVED\",\"identityKey\":\"gtin-123\",\"displayName\":\"Leche Entera\"}"
            )
        )
        assertEquals("gtin-123", goodPriceFromSaved.identityKey)
        assertEquals("Leche Entera", goodPriceFromSaved.displayName)
        assertTrue(
            ValuePilotNextHtmlPreviewContract
                .parseNativeBasketMessage("{\"type\":\"OPEN_NATIVE_BASKET\"}") != null
        )
        assertTrue(
            ValuePilotNextHtmlPreviewContract
                .parseNativeBasketPlanMessage("{\"type\":\"REQUEST_NATIVE_BASKET_PLAN\"}") != null
        )
        val identityOnlySave = requireNotNull(
            ValuePilotNextHtmlPreviewContract.parseNativeSaveMessage(
                "{" +
                    "\"type\":\"SAVE_VALUEPILOT_IDENTITY\"," +
                    "\"identityKey\":\"identity-only-123\"," +
                    "\"displayName\":\"Leche Entera\"," +
                    "\"matchLabel\":\"name match\"," +
                    "\"packageQuantityLabel\":\"1.000 ml\"," +
                    "\"sourceLabel\":\"\"" +
                    "}"
            )
        )
        assertNull(identityOnlySave.sourceLabel)
    }

    @Test
    fun acceptsOnly_bounded_observed_price_messages() {
        assertTrue(
            ValuePilotNextHtmlPreviewContract
                .parseNativeObservedPriceReadMessage(
                    "{\"type\":\"READ_NATIVE_OBSERVED_PRICE\"}"
                ) != null
        )
        val selection = requireNotNull(
            ValuePilotNextHtmlPreviewContract.parseNativeObservedPriceSelectionMessage(
                "{\"type\":\"SELECT_NATIVE_OBSERVED_PRICE\",\"kind\":\"PRODUCT\",\"key\":\"item-123\"}"
            )
        )
        assertEquals("PRODUCT", selection.kind)
        assertEquals("item-123", selection.key)
        assertEquals(
            "ARS",
            requireNotNull(
                ValuePilotNextHtmlPreviewContract.parseNativeObservedPriceAmountMessage(
                    "{\"type\":\"SET_NATIVE_OBSERVED_PRICE\",\"amount\":\"1250,50\",\"currency\":\"ars\"}"
                )
            ).currency
        )
        assertEquals(
            "-03:00",
            requireNotNull(
                ValuePilotNextHtmlPreviewContract.parseNativeObservedPriceTimeMessage(
                    "{\"type\":\"SET_NATIVE_OBSERVED_TIME\",\"date\":\"2026-10-09\",\"time\":\"10:30\",\"utcOffset\":\"-03:00\"}"
                )
            ).utcOffset
        )
        assertEquals(
            "PRICE_TAG",
            requireNotNull(
                ValuePilotNextHtmlPreviewContract.parseNativeObservedPriceProofMessage(
                    "{\"type\":\"SET_NATIVE_OBSERVED_PROOF\",\"artifactId\":\"proof-1\",\"proofType\":\"PRICE_TAG\"}"
                )
            ).proofType
        )
        assertTrue(
            ValuePilotNextHtmlNativeSearchBridge
                .encodeObservedPrice(null, null, null)
                .let { JSONObject(it).getString("type") == "NATIVE_OBSERVED_PRICE_STATE" }
        )
        assertNull(
            ValuePilotNextHtmlPreviewContract.parseNativeObservedPriceSelectionMessage(
                "{\"type\":\"SELECT_NATIVE_OBSERVED_PRICE\",\"kind\":\"PRODUCT\",\"key\":\"x\\u0001\"}"
            )
        )
        assertNull(
            ValuePilotNextHtmlPreviewContract.parseNativeObservedPriceProofMessage(
                "{\"type\":\"SET_NATIVE_OBSERVED_PROOF\",\"artifactId\":\"proof-1\",\"proofType\":\"PHOTO\"}"
            )
        )
        assertNull(
            ValuePilotNextHtmlPreviewContract.parseNativeObservedPriceAmountMessage(
                "{\"type\":\"SET_NATIVE_OBSERVED_PRICE\",\"amount\":\"1e4\",\"currency\":\"ARS\"}"
            )
        )
    }

    @Test
    fun acceptsOnly_typed_staple_watch_actions() {
        assertTrue(
            ValuePilotNextHtmlPreviewContract
                .parseNativeStapleWatchReadMessage(
                    "{\"type\":\"READ_NATIVE_STAPLE_WATCH\"}"
                ) != null
        )
        val product = requireNotNull(
            ValuePilotNextHtmlPreviewContract.parseNativeStapleWatchActionMessage(
                "{\"type\":\"SET_NATIVE_STAPLE_PRODUCT\",\"key\":\"item-123\",\"watched\":true}"
            )
        )
        assertEquals("PRODUCT", product.kind)
        assertEquals("item-123", product.key)
        assertEquals(true, product.watched)
        assertEquals(
            "STORE",
            requireNotNull(
                ValuePilotNextHtmlPreviewContract.parseNativeStapleWatchActionMessage(
                    "{\"type\":\"SELECT_NATIVE_STAPLE_STORE\",\"key\":\"store-123\"}"
                )
            ).kind
        )
        assertEquals(
            "CLEAR_SELECTION",
            requireNotNull(
                ValuePilotNextHtmlPreviewContract.parseNativeStapleWatchActionMessage(
                    "{\"type\":\"CLEAR_NATIVE_STAPLE_SELECTION\"}"
                )
            ).kind
        )
        assertTrue(
            ValuePilotNextHtmlNativeSearchBridge
                .encodeStapleWatch(null)
                .let { JSONObject(it).getString("type") == "NATIVE_STAPLE_WATCH_STATE" }
        )
        assertTrue(
            ValuePilotNextHtmlPreviewContract
                .parseNativeStapleWatchSubmitMessage(
                    "{\"type\":\"SUBMIT_NATIVE_STAPLE_WATCH\"}"
                ) != null
        )
        assertNull(
            ValuePilotNextHtmlPreviewContract.parseNativeStapleWatchActionMessage(
                "{\"type\":\"SET_NATIVE_STAPLE_PRODUCT\",\"key\":\"item-123\",\"watched\":\"true\"}"
            )
        )
        assertNull(
            ValuePilotNextHtmlPreviewContract.parseNativeStapleWatchActionMessage(
                "{\"type\":\"SELECT_NATIVE_STAPLE_STORE\",\"key\":\"store\\u0001\"}"
            )
        )
    }

    @Test
    fun `saved bridge serializes private history without price or store claims`() {
        val payload = JSONObject(
            ValuePilotNextHtmlNativeSearchBridge.encodeSaved(
                identities = emptyList(),
                privateHistory = ValuePilotNextHtmlPrivateHistorySummary(
                    status = "AVAILABLE",
                    exactHistoryCount = 2,
                    observationCount = 3,
                    latestDisplayName = "Leche Entera"
                )
            )
        )
        val history = payload.getJSONObject("privateHistory")

        assertEquals("AVAILABLE", history.getString("status"))
        assertEquals(2, history.getInt("exactHistoryCount"))
        assertEquals(3, history.getInt("observationCount"))
        assertEquals("Leche Entera", history.getString("latestDisplayName"))
        assertTrue(!history.has("price"))
        assertTrue(!history.has("storeName"))
    }

    @Test
    fun `saved bridge carries bounded private history rows as read only facts`() {
        val payload = JSONObject(
            ValuePilotNextHtmlNativeSearchBridge.encodeSaved(
                identities = emptyList(),
                privateHistory = ValuePilotNextHtmlPrivateHistorySummary(
                    status = "AVAILABLE",
                    exactHistoryCount = 1,
                    observationCount = 2,
                    latestDisplayName = "Leche Entera",
                    rows = listOf(
                        ValuePilotNextHtmlPrivateHistoryRow(
                            displayName = "Leche Entera",
                            observationCount = 2,
                            latestPriceText = "1200 ARS",
                            lowestPriceText = "1100 ARS",
                            latestUnitRateText = "1200 ARS/L",
                            rangeText = "1100–1200 ARS/L",
                            recentMovementText = "Up from previous 1100 ARS/L",
                            packageText = "1 L",
                            priceBasisText = "Current price",
                            latestObservedText = "2026-10-09 10:00 UTC",
                            sourceText = "Scan & compare",
                            promotionText = null
                        )
                    )
                )
            )
        )
        val row = payload.getJSONObject("privateHistory").getJSONArray("rows").getJSONObject(0)

        assertEquals("Leche Entera", row.getString("displayName"))
        assertEquals("1200 ARS", row.getString("latestPriceText"))
        assertEquals("Current price", row.getString("priceBasisText"))
        assertEquals("Scan & compare", row.getString("sourceText"))
        assertTrue(!row.has("storeName"))
        assertTrue(!row.has("availability"))
    }

    @Test
    fun `native product detail payload carries only Kotlin-owned identity and offers`() {
        val match = PracticalShoppingSearchIdentityPresentation.Match(
            displayName = "Leche Entera",
            brand = "Armonía",
            matchLabel = "exact name",
            packageQuantityLabel = "1.000 ml",
            sourceLabel = "qualified Argentina source",
            identityKey = "identity-123"
        )
        val payload = JSONObject(
            ValuePilotNextHtmlNativeSearchBridge.encodeProductDetail("leche", match)
        )

        assertEquals("NATIVE_PRODUCT_DETAIL", payload.getString("type"))
        assertEquals("leche", payload.getString("query"))
        assertEquals("identity-123", payload.getJSONObject("match").getString("identityKey"))
        assertEquals(0, payload.getJSONObject("match").getJSONArray("offers").length())
    }

    @Test
    fun `saved bridge includes Kotlin basket evidence state without inventing totals`() {
        val basket = ArgentinaPrivateBasketRenderState(
            headline = "1 selected product",
            guidance = "Search for current price evidence.",
            evidenceNotice = "Prices remain unknown.",
            actionLabel = "Open Search",
            canPlan = false,
            lines = listOf(
                ArgentinaPrivateBasketLineProjection(
                    identityKey = "identity-123",
                    productName = "Leche Entera",
                    packageQuantityText = "1.000 ml",
                    packageCount = null,
                    lineTotalText = null,
                    storeKey = null,
                    storeName = null,
                    availabilityText = "Price evidence unknown",
                    missing = true
                )
            ),
            plan = null
        )
        val payload = JSONObject(
            ValuePilotNextHtmlNativeSearchBridge.encodeSaved(
                identities = emptyList(),
                basket = basket,
                planLoading = true
            )
        )
        val line = payload.getJSONObject("basket").getJSONArray("lines").getJSONObject(0)

        assertEquals("identity-123", line.getString("identityKey"))
        assertTrue(line.isNull("lineTotalText"))
        assertTrue(payload.getJSONObject("basket").getBoolean("planLoading"))
        assertTrue(payload.getJSONObject("basket").isNull("plan"))
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
                    "\"identityKey\":\"x\u0001\"," +
                    "\"displayName\":\"Leche\"," +
                    "\"matchLabel\":\"exact name\"" +
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
        assertNull(
            ValuePilotNextHtmlPreviewContract.parseNativeSavedToolsMessage(
                "{\"type\":\"OPEN_NATIVE_SAVED\"}"
            )
        )
        assertNull(
            ValuePilotNextHtmlPreviewContract.parseNativeSavedToolsMessage(
                "{\"type\":\"OPEN_NATIVE_SAVED_TOOLS\",\"destination\":\"OBSERVED_PRICE_CONFIRMATION\"}"
            )
        )
        assertNull(
            ValuePilotNextHtmlPreviewContract.parseNativeJourneyMessage(
                "{\"type\":\"OPEN_NATIVE_JOURNEY\",\"destination\":\"OBSERVED_PRICE_CONFIRMATION\"}"
            )
        )
        assertNull(
            ValuePilotNextHtmlPreviewContract.parseNativeJourneyMessage(
                "{\"type\":\"OPEN_NATIVE_SAVED_TOOLS\",\"destination\":\"COMPARE\"}"
            )
        )
        assertNull(
            ValuePilotNextHtmlPreviewContract.parseNativeGoodPriceFromSavedMessage(
                "{\"type\":\"OPEN_NATIVE_GOOD_PRICE_FROM_SAVED\",\"identityKey\":\"gtin-123\",\"displayName\":\"Leche\\u0001\"}"
            )
        )
        assertNull(
            ValuePilotNextHtmlPreviewContract.parseNativeGoodPriceFromSavedMessage(
                "{\"type\":\"OPEN_NATIVE_JOURNEY\",\"identityKey\":\"gtin-123\",\"displayName\":\"Leche\"}"
            )
        )
        assertNull(
            ValuePilotNextHtmlPreviewContract.parseNativeBasketMessage(
                "{\"type\":\"OPEN_NATIVE_SAVED_TOOLS\"}"
            )
        )
        assertNull(
            ValuePilotNextHtmlPreviewContract.parseNativeBasketPlanMessage(
                "{\"type\":\"OPEN_NATIVE_BASKET\"}"
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
