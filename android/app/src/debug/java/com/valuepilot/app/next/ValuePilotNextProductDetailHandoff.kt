package com.valuepilot.app.next

import com.valuepilot.app.ArgentinaBackendContract
import com.valuepilot.app.PracticalShoppingSearchIdentityPresentation
import com.valuepilot.core.OfflineCatalogSnapshotManifest
import java.math.BigDecimal
import org.json.JSONArray
import org.json.JSONObject

/**
 * Bounded, in-process handoff for the selected Search presentation.
 *
 * The HTML preview may already have a source-authorized identity and offer
 * evidence when it opens Compose Product Detail. Keeping that snapshot in the
 * explicit Activity extra avoids a second network request and preserves the
 * complete Search result context when the user goes back.
 */
internal data class ValuePilotNextProductDetailHandoff(
    val query: String,
    val presentation: PracticalShoppingSearchIdentityPresentation,
    val selectedIdentityKey: String
) {
    init {
        require(query.isNotBlank())
        require(presentation.query == query)
        require(presentation.matches.any { it.identityKey == selectedIdentityKey })
    }

    val selectedMatch: PracticalShoppingSearchIdentityPresentation.Match
        get() = presentation.matches.first { it.identityKey == selectedIdentityKey }
}

internal object ValuePilotNextProductDetailHandoffCodec {
    private const val MAX_PAYLOAD_CHARS = 64 * 1024
    private const val MAX_QUERY_CHARS = 96
    private const val MAX_MATCHES = 30
    private const val MAX_DISPLAY_NAME_CHARS = 512
    private const val MAX_IDENTITY_KEY_CHARS = 256
    private const val MAX_MATCH_LABEL_CHARS = 96
    private const val MAX_BRAND_CHARS = 160
    private const val MAX_SOURCE_LABEL_CHARS = 160
    private const val MAX_QUANTITY_LABEL_CHARS = 64
    private const val MAX_NOTICE_CHARS = 1_024
    private const val MAX_OFFER_STRING_CHARS = 512
    private const val MAX_ADDRESS_ENTRIES = 32
    private const val MAX_ADDRESS_KEY_CHARS = 96
    private const val MAX_ADDRESS_VALUE_CHARS = 512

    /** Compatibility helper for identity-only test fixtures. */
    fun encode(
        query: String,
        match: PracticalShoppingSearchIdentityPresentation.Match
    ): String {
        val normalizedQuery = query.trim()
        return encode(
            query = normalizedQuery,
            presentation = PracticalShoppingSearchIdentityPresentation(
                query = normalizedQuery,
                matches = listOf(match),
                evaluatedCandidateCount = 1,
                notice = DEFAULT_NOTICE
            ),
            selectedIdentityKey = match.identityKey
        )
    }

    fun encode(
        query: String,
        presentation: PracticalShoppingSearchIdentityPresentation,
        selectedIdentityKey: String
    ): String {
        val normalizedQuery = query.trim()
        require(normalizedQuery.isSafe(MAX_QUERY_CHARS))
        require(presentation.query.trim() == normalizedQuery)
        val normalizedPresentation =
            if (presentation.query == normalizedQuery) presentation
            else presentation.copy(query = normalizedQuery)
        val handoff = ValuePilotNextProductDetailHandoff(
            query = normalizedQuery,
            presentation = normalizedPresentation,
            selectedIdentityKey = selectedIdentityKey
        )
        require(handoff.presentation.matches.size <= MAX_MATCHES)
        require(handoff.presentation.notice.isSafe(MAX_NOTICE_CHARS))
        require(handoff.presentation.evaluatedCandidateCount >= handoff.presentation.matches.size)
        require(
            handoff.presentation.evaluatedCandidateCount <=
                OfflineCatalogSnapshotManifest.MAX_TOTAL_RECORDS
        )
        require(selectedIdentityKey.isSafe(MAX_IDENTITY_KEY_CHARS))
        val payload = JSONObject()
            .put("query", handoff.query)
            .put("selectedIdentityKey", handoff.selectedIdentityKey)
            .put("evaluatedCandidateCount", handoff.presentation.evaluatedCandidateCount)
            .put("notice", handoff.presentation.notice)
            .put("page", handoff.presentation.page)
            .put("hasMore", handoff.presentation.hasMore)
            .put("nextPage", handoff.presentation.nextPage ?: JSONObject.NULL)
            .put("limitReached", handoff.presentation.limitReached)
            .put("matches", JSONArray().apply {
                handoff.presentation.matches.forEach { match -> put(encodeMatch(match)) }
            })
            .toString()
        require(payload.length <= MAX_PAYLOAD_CHARS)
        return payload
    }

    /**
     * Returns null when the bounded Activity extra cannot carry the complete
     * presentation. The caller can then use the legacy query-based restore
     * path instead of crashing while opening Product Detail.
     */
    fun encodeOrNull(
        query: String,
        presentation: PracticalShoppingSearchIdentityPresentation,
        selectedIdentityKey: String
    ): String? = runCatching {
        encode(query, presentation, selectedIdentityKey)
    }.getOrNull()

    fun decode(rawPayload: String?): ValuePilotNextProductDetailHandoff? {
        if (rawPayload.isNullOrBlank() || rawPayload.length > MAX_PAYLOAD_CHARS) return null
        return runCatching {
            val root = JSONObject(rawPayload)
            val query = root.requiredText("query", MAX_QUERY_CHARS) ?: return null
            val selectedIdentityKey =
                root.requiredText("selectedIdentityKey", MAX_IDENTITY_KEY_CHARS) ?: return null
            val matchesJson = root.optJSONArray("matches") ?: return null
            if (matchesJson.length() !in 1..MAX_MATCHES) return null
            val matches = buildList {
                repeat(matchesJson.length()) { index ->
                    add(decodeMatch(matchesJson.optJSONObject(index)) ?: return null)
                }
            }
            val evaluatedCandidateCount = root.requiredInt(
                key = "evaluatedCandidateCount",
                minimum = matches.size,
                maximum = OfflineCatalogSnapshotManifest.MAX_TOTAL_RECORDS
            ) ?: return null
            val notice = root.requiredText("notice", MAX_NOTICE_CHARS) ?: return null
            val page = root.requiredInt("page", 0, ArgentinaBackendContract.MAX_SEARCH_PAGE)
                ?: return null
            val hasMore = root.requiredBoolean("hasMore") ?: return null
            val nextPage = root.optionalInt("nextPage", 0, ArgentinaBackendContract.MAX_SEARCH_PAGE)
            val limitReached = root.requiredBoolean("limitReached") ?: return null
            val presentation = PracticalShoppingSearchIdentityPresentation(
                query = query,
                matches = matches,
                evaluatedCandidateCount = evaluatedCandidateCount,
                notice = notice,
                page = page,
                hasMore = hasMore,
                nextPage = nextPage,
                limitReached = limitReached
            )
            ValuePilotNextProductDetailHandoff(
                query = query,
                presentation = presentation,
                selectedIdentityKey = selectedIdentityKey
            )
        }.getOrNull()
    }

    private fun encodeMatch(
        match: PracticalShoppingSearchIdentityPresentation.Match
    ): JSONObject {
        require(match.displayName.isSafe(MAX_DISPLAY_NAME_CHARS))
        require(match.identityKey.isSafe(MAX_IDENTITY_KEY_CHARS))
        require(match.matchLabel.isSafe(MAX_MATCH_LABEL_CHARS))
        require(match.brand == null || match.brand.isSafe(MAX_BRAND_CHARS))
        require(match.packageQuantityLabel == null || match.packageQuantityLabel.isSafe(MAX_QUANTITY_LABEL_CHARS))
        require(match.sourceLabel == null || match.sourceLabel.isSafe(MAX_SOURCE_LABEL_CHARS))
        require(match.qualifiedOffers.size <= ArgentinaBackendContract.MAX_SEARCH_OFFERS_PER_IDENTITY)
        return JSONObject()
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

    private fun decodeMatch(json: JSONObject?): PracticalShoppingSearchIdentityPresentation.Match? {
        if (json == null) return null
        val displayName = json.requiredText("displayName", MAX_DISPLAY_NAME_CHARS) ?: return null
        val brand = json.optionalText("brand", MAX_BRAND_CHARS)
        val matchLabel = json.requiredText("matchLabel", MAX_MATCH_LABEL_CHARS) ?: return null
        val packageQuantityLabel =
            json.optionalText("packageQuantityLabel", MAX_QUANTITY_LABEL_CHARS)
        val sourceLabel = json.optionalText("sourceLabel", MAX_SOURCE_LABEL_CHARS)
        val identityKey = json.requiredText("identityKey", MAX_IDENTITY_KEY_CHARS) ?: return null
        val gtin = json.optionalText("gtin", 14)
        if (gtin != null && !gtin.matches(Regex("[0-9]{8}|[0-9]{12}|[0-9]{13}|[0-9]{14}"))) {
            return null
        }
        val offersJson = json.optJSONArray("offers") ?: JSONArray()
        if (offersJson.length() > ArgentinaBackendContract.MAX_SEARCH_OFFERS_PER_IDENTITY) {
            return null
        }
        val offers = buildList {
            repeat(offersJson.length()) { index ->
                add(decodeOffer(offersJson.optJSONObject(index)) ?: return null)
            }
        }
        if (offers.any { it.productEvidenceKey != identityKey }) return null
        return PracticalShoppingSearchIdentityPresentation.Match(
            displayName = displayName,
            brand = brand,
            matchLabel = matchLabel,
            packageQuantityLabel = packageQuantityLabel,
            sourceLabel = sourceLabel,
            identityKey = identityKey,
            gtin = gtin,
            qualifiedOffers = offers
        )
    }

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
        val quantityResult = decodeNullableQuantity(json, "quantity")
        if (!quantityResult.first) return null
        val unitPriceResult = decodeNullableMoney(json, "unitPrice")
        if (!unitPriceResult.first) return null
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
            referencePrice = referencePriceResult.second,
            quantity = quantityResult.second,
            quantityStatus = json.optionalText("quantityStatus", MAX_OFFER_STRING_CHARS),
            unitPrice = unitPriceResult.second,
            unitPriceBasis = json.optionalText("unitPriceBasis", MAX_OFFER_STRING_CHARS),
            releaseDate = json.requiredText("releaseDate", MAX_OFFER_STRING_CHARS) ?: return null,
            freshnessStatus = json.enumValue<ArgentinaBackendContract.Freshness>("freshnessStatus")
                ?: return null,
            availability = json.enumValue<ArgentinaBackendContract.Availability>("availability")
                ?: return null,
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

    private fun JSONObject.requiredBoolean(key: String): Boolean? =
        if (!has(key) || isNull(key)) null else opt(key) as? Boolean

    private fun JSONObject.requiredInt(key: String, minimum: Int, maximum: Int): Int? {
        if (!has(key) || isNull(key)) return null
        val number = opt(key) as? Number ?: return null
        val value = number.toLong()
        if (value !in minimum.toLong()..maximum.toLong() || value.toDouble() != number.toDouble()) {
            return null
        }
        return value.toInt()
    }

    private fun JSONObject.optionalInt(key: String, minimum: Int, maximum: Int): Int? {
        if (!has(key) || isNull(key)) return null
        return requiredInt(key, minimum, maximum)
    }

    private fun String.isSafe(maxChars: Int): Boolean =
        length in 1..maxChars && none { it.code < 0x20 || it.code == 0x7f }

    private const val DEFAULT_NOTICE =
        "The selected qualified Search identity was restored for Product Detail."
}
