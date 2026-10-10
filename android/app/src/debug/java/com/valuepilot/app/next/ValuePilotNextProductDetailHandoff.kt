package com.valuepilot.app.next

import com.valuepilot.app.ArgentinaBackendContract
import com.valuepilot.app.PracticalShoppingSearchIdentityPresentation
import java.math.BigDecimal
import org.json.JSONArray
import org.json.JSONObject

/**
 * Bounded, in-process handoff for the selected Search match.
 *
 * The HTML preview may already have a source-authorized identity and offer
 * evidence when it opens Compose Product Detail. Keeping that snapshot in the
 * explicit Activity extra avoids a second network request and preserves the
 * evidence that the user selected. The receiving Activity still validates the
 * complete payload before using it.
 */
internal data class ValuePilotNextProductDetailHandoff(
    val query: String,
    val match: PracticalShoppingSearchIdentityPresentation.Match
)

internal object ValuePilotNextProductDetailHandoffCodec {
    private const val MAX_PAYLOAD_CHARS = 64 * 1024
    private const val MAX_DISPLAY_NAME_CHARS = 512
    private const val MAX_IDENTITY_KEY_CHARS = 256
    private const val MAX_MATCH_LABEL_CHARS = 96
    private const val MAX_BRAND_CHARS = 160
    private const val MAX_SOURCE_LABEL_CHARS = 160
    private const val MAX_QUANTITY_LABEL_CHARS = 64
    private const val MAX_OFFER_STRING_CHARS = 512
    private const val MAX_ADDRESS_ENTRIES = 32
    private const val MAX_ADDRESS_KEY_CHARS = 96
    private const val MAX_ADDRESS_VALUE_CHARS = 512

    fun encode(
        query: String,
        match: PracticalShoppingSearchIdentityPresentation.Match
    ): String {
        require(query.isSafe(MAX_QUANTITY_LABEL_CHARS + 32))
        require(match.displayName.isSafe(MAX_DISPLAY_NAME_CHARS))
        require(match.identityKey.isSafe(MAX_IDENTITY_KEY_CHARS))
        require(match.matchLabel.isSafe(MAX_MATCH_LABEL_CHARS))
        require(match.brand == null || match.brand.isSafe(MAX_BRAND_CHARS))
        require(match.packageQuantityLabel == null || match.packageQuantityLabel.isSafe(MAX_QUANTITY_LABEL_CHARS))
        require(match.sourceLabel == null || match.sourceLabel.isSafe(MAX_SOURCE_LABEL_CHARS))
        require(match.qualifiedOffers.size <= ArgentinaBackendContract.MAX_SEARCH_OFFERS_PER_IDENTITY)
        return JSONObject()
            .put("query", query.trim())
            .put(
                "match",
                JSONObject()
                    .put("displayName", match.displayName)
                    .put("brand", match.brand ?: JSONObject.NULL)
                    .put("matchLabel", match.matchLabel)
                    .put("packageQuantityLabel", match.packageQuantityLabel ?: JSONObject.NULL)
                    .put("sourceLabel", match.sourceLabel ?: JSONObject.NULL)
                    .put("identityKey", match.identityKey)
                    .put("gtin", match.gtin ?: JSONObject.NULL)
                    .put("offers", JSONArray().apply {
                        match.qualifiedOffers.forEach { offer -> putOffer(offer) }
                    })
            )
            .toString()
            .also { payload -> require(payload.length <= MAX_PAYLOAD_CHARS) }
    }

    fun decode(rawPayload: String?): ValuePilotNextProductDetailHandoff? {
        if (rawPayload.isNullOrBlank() || rawPayload.length > MAX_PAYLOAD_CHARS) return null
        return runCatching {
            val root = JSONObject(rawPayload)
            val query = root.requiredText("query", 96) ?: return null
            val matchJson = root.optJSONObject("match") ?: return null
            val displayName = matchJson.requiredText("displayName", MAX_DISPLAY_NAME_CHARS)
                ?: return null
            val brand = matchJson.optionalText("brand", MAX_BRAND_CHARS)
            val matchLabel = matchJson.requiredText("matchLabel", MAX_MATCH_LABEL_CHARS)
                ?: return null
            val packageQuantityLabel =
                matchJson.optionalText("packageQuantityLabel", MAX_QUANTITY_LABEL_CHARS)
            val sourceLabel = matchJson.optionalText("sourceLabel", MAX_SOURCE_LABEL_CHARS)
            val identityKey = matchJson.requiredText("identityKey", MAX_IDENTITY_KEY_CHARS)
                ?: return null
            val gtin = matchJson.optionalText("gtin", 14)
            if (gtin != null && !gtin.matches(Regex("[0-9]{8}|[0-9]{12}|[0-9]{13}|[0-9]{14}"))) {
                return null
            }
            val offersJson = matchJson.optJSONArray("offers") ?: JSONArray()
            if (offersJson.length() > ArgentinaBackendContract.MAX_SEARCH_OFFERS_PER_IDENTITY) {
                return null
            }
            val offers = buildList {
                repeat(offersJson.length()) { index ->
                    add(decodeOffer(offersJson.optJSONObject(index)) ?: return null)
                }
            }
            if (offers.any { it.productEvidenceKey != identityKey }) return null
            ValuePilotNextProductDetailHandoff(
                query = query,
                match = PracticalShoppingSearchIdentityPresentation.Match(
                    displayName = displayName,
                    brand = brand,
                    matchLabel = matchLabel,
                    packageQuantityLabel = packageQuantityLabel,
                    sourceLabel = sourceLabel,
                    identityKey = identityKey,
                    gtin = gtin,
                    qualifiedOffers = offers
                )
            )
        }.getOrNull()
    }

    private fun JSONArray.putOffer(offer: ArgentinaBackendContract.SearchOffer) {
        put(
            JSONObject()
                .put("offerKey", offer.offerKey)
                .put("productEvidenceKey", offer.productEvidenceKey)
                .put("storeKey", offer.storeKey)
                .put("storeName", offer.storeName)
                .put("storeAddress", JSONObject().apply {
                    offer.storeAddress.forEach { (key, value) -> put(key, value) }
                })
                .put("storeLatitude", offer.storeLatitude)
                .put("storeLongitude", offer.storeLongitude)
                .put("distanceKm", offer.distanceKm)
                .put("listPrice", encodeMoney(offer.listPrice))
                .put("referencePrice", offer.referencePrice?.let(::encodeMoney) ?: JSONObject.NULL)
                .put("quantity", offer.quantity?.let(::encodeQuantity) ?: JSONObject.NULL)
                .put("quantityStatus", offer.quantityStatus ?: JSONObject.NULL)
                .put("unitPrice", offer.unitPrice?.let(::encodeMoney) ?: JSONObject.NULL)
                .put("unitPriceBasis", offer.unitPriceBasis ?: JSONObject.NULL)
                .put("releaseDate", offer.releaseDate)
                .put("freshnessStatus", offer.freshnessStatus.name)
                .put("availability", offer.availability.name)
                .put(
                    "provenance",
                    JSONObject()
                        .put("provider", offer.provenance.provider)
                        .put("releaseDate", offer.provenance.releaseDate)
                        .put("freshnessStatus", offer.provenance.freshnessStatus.name)
                        .put("source", offer.provenance.source ?: JSONObject.NULL)
                )
        )
    }

    private fun encodeMoney(money: ArgentinaBackendContract.Money): JSONObject =
        JSONObject()
            .put("amount", money.amount)
            .put("currency", money.currency)

    private fun encodeQuantity(quantity: ArgentinaBackendContract.Quantity): JSONObject =
        JSONObject()
            .put("dimension", quantity.dimension)
            .put("amount", quantity.amount)
            .put("unit", quantity.unit)
            .put("baseAmount", quantity.baseAmount)
            .put("baseUnit", quantity.baseUnit)
            .put("allowZero", quantity.allowZero)

    private fun decodeOffer(json: JSONObject?): ArgentinaBackendContract.SearchOffer? {
        if (json == null) return null
        val offerKey = json.requiredText("offerKey", MAX_IDENTITY_KEY_CHARS) ?: return null
        val productEvidenceKey = json.requiredText("productEvidenceKey", MAX_IDENTITY_KEY_CHARS)
            ?: return null
        val storeKey = json.requiredText("storeKey", MAX_IDENTITY_KEY_CHARS) ?: return null
        val storeName = json.requiredText("storeName", MAX_OFFER_STRING_CHARS) ?: return null
        val storeAddress = decodeAddress(json.optJSONObject("storeAddress")) ?: return null
        val storeLatitude = json.requiredText("storeLatitude", MAX_OFFER_STRING_CHARS) ?: return null
        val storeLongitude = json.requiredText("storeLongitude", MAX_OFFER_STRING_CHARS) ?: return null
        val distanceKm = json.requiredText("distanceKm", MAX_OFFER_STRING_CHARS) ?: return null
        val listPrice = decodeMoney(json.optJSONObject("listPrice")) ?: return null
        val referencePriceResult = decodeNullableMoney(json, "referencePrice")
        if (!referencePriceResult.first) return null
        val referencePrice = referencePriceResult.second
        val quantityResult = decodeNullableQuantity(json, "quantity")
        if (!quantityResult.first) return null
        val quantity = quantityResult.second
        val quantityStatus = json.optionalText("quantityStatus", MAX_OFFER_STRING_CHARS)
        val unitPriceResult = decodeNullableMoney(json, "unitPrice")
        if (!unitPriceResult.first) return null
        val unitPrice = unitPriceResult.second
        val unitPriceBasis = json.optionalText("unitPriceBasis", MAX_OFFER_STRING_CHARS)
        val releaseDate = json.requiredText("releaseDate", MAX_OFFER_STRING_CHARS) ?: return null
        val freshnessStatus = json.enumValue<ArgentinaBackendContract.Freshness>("freshnessStatus")
            ?: return null
        val availability = json.enumValue<ArgentinaBackendContract.Availability>("availability")
            ?: return null
        val provenanceJson = json.optJSONObject("provenance") ?: return null
        val provenance = ArgentinaBackendContract.Provenance(
            provider = provenanceJson.requiredText("provider", MAX_OFFER_STRING_CHARS) ?: return null,
            releaseDate = provenanceJson.requiredText("releaseDate", MAX_OFFER_STRING_CHARS) ?: return null,
            freshnessStatus = provenanceJson.enumValue<ArgentinaBackendContract.Freshness>("freshnessStatus")
                ?: return null,
            source = provenanceJson.optionalText("source", MAX_OFFER_STRING_CHARS)
        )
        return ArgentinaBackendContract.SearchOffer(
            offerKey = offerKey,
            productEvidenceKey = productEvidenceKey,
            storeKey = storeKey,
            storeName = storeName,
            storeAddress = storeAddress,
            storeLatitude = storeLatitude,
            storeLongitude = storeLongitude,
            distanceKm = distanceKm,
            listPrice = listPrice,
            referencePrice = referencePrice,
            quantity = quantity,
            quantityStatus = quantityStatus,
            unitPrice = unitPrice,
            unitPriceBasis = unitPriceBasis,
            releaseDate = releaseDate,
            freshnessStatus = freshnessStatus,
            availability = availability,
            provenance = provenance
        )
    }

    private fun decodeMoney(json: JSONObject?): ArgentinaBackendContract.Money? {
        if (json == null) return null
        val amount = json.requiredText("amount", MAX_OFFER_STRING_CHARS) ?: return null
        val currency = json.requiredText("currency", 16) ?: return null
        val decimal = runCatching { BigDecimal(amount) }.getOrNull() ?: return null
        return ArgentinaBackendContract.Money(amount, currency, decimal)
    }

    private fun decodeNullableMoney(
        parent: JSONObject,
        key: String
    ): Pair<Boolean, ArgentinaBackendContract.Money?> =
        if (!parent.has(key) || parent.isNull(key)) {
            true to null
        } else {
            decodeMoney(parent.optJSONObject(key))?.let { true to it } ?: (false to null)
        }

    private fun decodeQuantity(json: JSONObject?): ArgentinaBackendContract.Quantity? {
        if (json == null) return null
        val dimension = json.requiredText("dimension", MAX_OFFER_STRING_CHARS) ?: return null
        val amount = json.requiredText("amount", MAX_OFFER_STRING_CHARS) ?: return null
        val unit = json.requiredText("unit", MAX_OFFER_STRING_CHARS) ?: return null
        val baseAmount = json.requiredText("baseAmount", MAX_OFFER_STRING_CHARS) ?: return null
        val baseUnit = json.requiredText("baseUnit", MAX_OFFER_STRING_CHARS) ?: return null
        val allowZero = json.opt("allowZero") as? Boolean ?: return null
        return ArgentinaBackendContract.Quantity(dimension, amount, unit, baseAmount, baseUnit, allowZero)
    }

    private fun decodeNullableQuantity(
        parent: JSONObject,
        key: String
    ): Pair<Boolean, ArgentinaBackendContract.Quantity?> =
        if (!parent.has(key) || parent.isNull(key)) {
            true to null
        } else {
            decodeQuantity(parent.optJSONObject(key))?.let { true to it } ?: (false to null)
        }

    private fun decodeAddress(json: JSONObject?): Map<String, String>? {
        if (json == null || json.length() > MAX_ADDRESS_ENTRIES) return null
        val address = linkedMapOf<String, String>()
        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (!key.isSafe(MAX_ADDRESS_KEY_CHARS)) return null
            val value = json.opt(key) as? String ?: return null
            if (!value.isSafe(MAX_ADDRESS_VALUE_CHARS)) return null
            address[key] = value
        }
        return address
    }

    private inline fun <reified T : Enum<T>> JSONObject.enumValue(key: String): T? =
        requiredText(key, 64)?.let { value -> runCatching { enumValueOf<T>(value) }.getOrNull() }

    private fun JSONObject.requiredText(key: String, maxChars: Int): String? =
        optionalText(key, maxChars)?.takeIf { it.isNotBlank() }

    private fun JSONObject.optionalText(key: String, maxChars: Int): String? {
        if (!has(key) || isNull(key)) return null
        val value = opt(key) as? String ?: return null
        return value.trim().takeIf { it.isSafe(maxChars) }
    }

    private fun String.isSafe(maxChars: Int): Boolean =
        length in 1..maxChars && none { it.code < 0x20 || it.code == 0x7f }
}
