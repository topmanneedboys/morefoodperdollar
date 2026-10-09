package com.valuepilot.app.next

import com.valuepilot.app.ArgentinaLiveSearchSurfaceState
import com.valuepilot.app.ArgentinaBackendContract
import com.valuepilot.app.PracticalShoppingPrivateIdentity
import org.json.JSONArray
import org.json.JSONObject

/**
 * Serializes the existing Kotlin Search surface for the isolated Stitch
 * preview. Only identity fields and explicitly attached price evidence cross
 * the WebView boundary; the HTML layer cannot manufacture a successful state.
 */
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
                state.presentation?.matches.orEmpty().forEach { match ->
                    put(
                        JSONObject()
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
                    )
                }
            })
            .toString()

    fun encodeSaved(
        identities: List<PracticalShoppingPrivateIdentity>,
        action: String? = null
    ): String =
        JSONObject()
            .put("type", "NATIVE_SAVED_STATE")
            .put("mode", "READY")
            .put("readOnly", false)
            .put("basketCount", identities.size)
            .put("action", action ?: JSONObject.NULL)
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
