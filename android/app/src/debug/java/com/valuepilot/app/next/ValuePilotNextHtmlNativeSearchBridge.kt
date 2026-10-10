package com.valuepilot.app.next

import com.valuepilot.app.ArgentinaLiveSearchSurfaceState
import com.valuepilot.app.ArgentinaBackendContract
import com.valuepilot.app.ArgentinaPrivateBasketRenderState
import com.valuepilot.app.ArgentinaPrivateTripCostResult
import com.valuepilot.app.PracticalShoppingSearchIdentityPresentation
import com.valuepilot.app.PracticalShoppingPrivateIdentity
import org.json.JSONArray
import org.json.JSONObject

/**
 * Serializes the existing Kotlin Search surface for the isolated Stitch
 * preview. Only identity fields and explicitly attached price evidence cross
 * the WebView boundary; the HTML layer cannot manufacture a successful state.
 */
internal data class ValuePilotNextHtmlPrivateHistorySummary(
    val status: String,
    val exactHistoryCount: Int,
    val observationCount: Int,
    val latestDisplayName: String? = null,
    val rows: List<ValuePilotNextHtmlPrivateHistoryRow> = emptyList()
) {
    init {
        require(status in setOf("AVAILABLE", "EMPTY", "UNAVAILABLE"))
        require(exactHistoryCount >= 0)
        require(observationCount >= 0)
        require(status == "AVAILABLE" || exactHistoryCount == 0)
        require(status == "AVAILABLE" || observationCount == 0)
        require(status == "AVAILABLE" || latestDisplayName == null)
        require(latestDisplayName == null || latestDisplayName.isNotBlank())
        require(rows.size <= 32)
        require(status == "AVAILABLE" || rows.isEmpty())
        require(rows.size <= exactHistoryCount)
    }
}

/**
 * Read-only private-history facts crossing into the HTML presentation layer.
 *
 * These values are projections of the device-only Kotlin memory. They carry no retailer,
 * availability, ranking, or current-offer authority. Mutations remain on the existing Kotlin
 * private-history route.
 */
internal data class ValuePilotNextHtmlPrivateHistoryRow(
    val displayName: String,
    val observationCount: Int,
    val latestPriceText: String,
    val lowestPriceText: String,
    val latestUnitRateText: String,
    val rangeText: String,
    val recentMovementText: String?,
    val packageText: String,
    val priceBasisText: String,
    val latestObservedText: String,
    val sourceText: String,
    val promotionText: String?
) {
    init {
        require(displayName.isNotBlank())
        require(observationCount > 0)
        listOf(
            latestPriceText,
            lowestPriceText,
            latestUnitRateText,
            rangeText,
            packageText,
            priceBasisText,
            latestObservedText,
            sourceText
        ).forEach { require(it.isNotBlank()) }
        require(recentMovementText == null || recentMovementText.isNotBlank())
        require(promotionText == null || promotionText.isNotBlank())
    }
}

internal object ValuePilotNextHtmlNativeSearchBridge {
    fun encode(state: ArgentinaLiveSearchSurfaceState): String =
        JSONObject()
            .put("areaLabel", state.areaLabel)
            .put("mode", state.mode.name)
            .put("generation", state.generation)
            .put("query", state.query)
            .put("statusMessage", state.statusMessage)
            .put("caveat", state.caveat)
            .put("matches", JSONArray().apply {
                state.presentation?.matches.orEmpty().forEach { match -> put(encodeMatch(match)) }
            })
            .toString()

    fun encodeProductDetail(
        query: String,
        match: PracticalShoppingSearchIdentityPresentation.Match
    ): String = JSONObject()
        .put("type", "NATIVE_PRODUCT_DETAIL")
        .put("mode", "READY")
        .put("query", query)
        .put("match", encodeMatch(match))
        .toString()

    fun encodeSaved(
        identities: List<PracticalShoppingPrivateIdentity>,
        action: String? = null,
        privateHistory: ValuePilotNextHtmlPrivateHistorySummary? = null,
        basket: ArgentinaPrivateBasketRenderState? = null,
        planLoading: Boolean = false
    ): String =
        JSONObject()
            .put("type", "NATIVE_SAVED_STATE")
            .put("mode", "READY")
            .put("readOnly", false)
            .put("basketCount", identities.size)
            .put("action", action ?: JSONObject.NULL)
            .put(
                "privateHistory",
                privateHistory?.let { summary ->
                    JSONObject()
                        .put("status", summary.status)
                        .put("exactHistoryCount", summary.exactHistoryCount)
                        .put("observationCount", summary.observationCount)
                        .put("latestDisplayName", summary.latestDisplayName ?: JSONObject.NULL)
                        .put("rows", JSONArray().apply {
                            summary.rows.forEach { row ->
                                put(
                                    JSONObject()
                                        .put("displayName", row.displayName)
                                        .put("observationCount", row.observationCount)
                                        .put("latestPriceText", row.latestPriceText)
                                        .put("lowestPriceText", row.lowestPriceText)
                                        .put("latestUnitRateText", row.latestUnitRateText)
                                        .put("rangeText", row.rangeText)
                                        .put("recentMovementText", row.recentMovementText ?: JSONObject.NULL)
                                        .put("packageText", row.packageText)
                                        .put("priceBasisText", row.priceBasisText)
                                        .put("latestObservedText", row.latestObservedText)
                                        .put("sourceText", row.sourceText)
                                        .put("promotionText", row.promotionText ?: JSONObject.NULL)
                                )
                            }
                        })
                } ?: JSONObject.NULL
            )
            .put(
                "basket",
                basket?.let { encodeBasket(it, planLoading) } ?: JSONObject.NULL
            )
            .put("items", JSONArray().apply {
                identities.forEach { identity ->
                    put(
                        JSONObject()
                            .put("identityKey", identity.identityKey)
                            .put("displayName", identity.displayName)
                            .put("brand", identity.brand ?: JSONObject.NULL)
                            .put("sourceLabel", identity.sourceLabel ?: JSONObject.NULL)
                            .put("packageQuantityLabel", identity.packageQuantityLabel ?: JSONObject.NULL)
                    )
                }
            })
            .toString()

    private fun encodeMatch(
        match: PracticalShoppingSearchIdentityPresentation.Match
    ): JSONObject = JSONObject()
        .put("identityKey", match.identityKey)
        .put("displayName", match.displayName)
        .put("brand", match.brand ?: JSONObject.NULL)
        .put("matchLabel", match.matchLabel)
        .put("packageQuantityLabel", match.packageQuantityLabel ?: JSONObject.NULL)
        .put("sourceLabel", match.sourceLabel ?: JSONObject.NULL)
        .put("gtin", match.gtin ?: JSONObject.NULL)
        .put("offers", JSONArray().apply {
            match.qualifiedOffers.forEach { offer -> putOffer(offer) }
        })

    private fun encodeBasket(
        state: ArgentinaPrivateBasketRenderState,
        planLoading: Boolean
    ): JSONObject =
        JSONObject()
            .put("headline", state.headline)
            .put("guidance", state.guidance)
            .put("evidenceNotice", state.evidenceNotice)
            .put("actionLabel", state.actionLabel)
            .put("canPlan", state.canPlan)
            .put("planLoading", planLoading)
            .put("tripSummaryText", state.tripSummaryText ?: JSONObject.NULL)
            .put("errorNotice", state.errorNotice ?: JSONObject.NULL)
            .put("lines", JSONArray().apply {
                state.lines.forEach { line ->
                    put(
                        JSONObject()
                            .put("identityKey", line.identityKey)
                            .put("productName", line.productName)
                            .put("packageQuantityText", line.packageQuantityText)
                            .put("packageCount", line.packageCount ?: JSONObject.NULL)
                            .put("lineTotalText", line.lineTotalText ?: JSONObject.NULL)
                            .put("storeKey", line.storeKey ?: JSONObject.NULL)
                            .put("storeName", line.storeName ?: JSONObject.NULL)
                            .put("availabilityText", line.availabilityText)
                            .put("missing", line.missing)
                    )
                }
            })
            .put(
                "plan",
                state.plan?.let { plan ->
                    JSONObject()
                        .put("status", plan.status.name)
                        .put("headline", plan.headline)
                        .put("totalText", plan.totalText ?: JSONObject.NULL)
                        .put("coverageText", plan.coverageText)
                        .put("notice", plan.notice)
                        .put("releaseDate", plan.releaseDate)
                } ?: JSONObject.NULL
            )

    fun encodeTrip(
        result: ArgentinaPrivateTripCostResult?,
        error: String?
    ): String =
        JSONObject()
            .put("type", "NATIVE_TRIP_RESULT")
            .put("status", if (result != null) "SUCCESS" else "ERROR")
            .put("error", error ?: JSONObject.NULL)
            .put(
                "result",
                result?.let {
                    JSONObject()
                        .put("roundTripKm", it.roundTripKm.toPlainString())
                        .put("fuelCost", it.fuelCost.toPlainString())
                        .put("tolls", it.tolls.toPlainString())
                        .put("parking", it.parking.toPlainString())
                        .put("total", it.total.toPlainString())
                } ?: JSONObject.NULL
            )
            .toString()

    private fun JSONArray.putOffer(offer: ArgentinaBackendContract.SearchOffer) {
        put(
            JSONObject()
                .put("storeName", offer.storeName)
                .put("priceAmount", offer.listPrice.amount)
                .put("currency", offer.listPrice.currency)
                .put("unitPriceAmount", offer.unitPrice?.amount ?: JSONObject.NULL)
                .put("unitPriceCurrency", offer.unitPrice?.currency ?: JSONObject.NULL)
                .put("unitPriceBasis", offer.unitPriceBasis ?: JSONObject.NULL)
                .put("quantity", offer.quantity?.amount ?: JSONObject.NULL)
                .put("quantityUnit", offer.quantity?.unit ?: JSONObject.NULL)
                .put("freshness", offer.freshnessStatus.name)
        )
    }
}
