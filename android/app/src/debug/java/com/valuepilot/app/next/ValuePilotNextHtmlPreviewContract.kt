package com.valuepilot.app.next

import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

internal data class ValuePilotNextHtmlPhotoRequest(
    val productId: String,
    val query: String
)

internal data class ValuePilotNextHtmlLanguageRequest(
    val language: String
)

internal data class ValuePilotNextHtmlNativeSearchRequest(
    val query: String
)

internal object ValuePilotNextHtmlNativeSavedReadRequest

internal object ValuePilotNextHtmlNativeBasketRequest

internal object ValuePilotNextHtmlNativeBasketPlanRequest

internal object ValuePilotNextHtmlNativeObservedPriceReadRequest

internal data class ValuePilotNextHtmlNativeObservedPriceSelectionRequest(
    val kind: String,
    val key: String? = null
)

internal object ValuePilotNextHtmlNativeObservedPricePrefillRequest

internal data class ValuePilotNextHtmlNativeObservedPriceAmountRequest(
    val amount: String,
    val currency: String
)

internal data class ValuePilotNextHtmlNativeObservedPriceTimeRequest(
    val date: String,
    val time: String,
    val utcOffset: String
)

internal data class ValuePilotNextHtmlNativeObservedPriceProofRequest(
    val artifactId: String,
    val proofType: String
)

internal object ValuePilotNextHtmlNativeObservedPriceProofPickerRequest
internal object ValuePilotNextHtmlNativeObservedPriceSubmitRequest
internal object ValuePilotNextHtmlNativeObservedPriceCancelRequest

internal data class ValuePilotNextHtmlNativeSavedToolsRequest(
    val destination: String
)

internal data class ValuePilotNextHtmlNativeJourneyRequest(
    val destination: String
)

internal data class ValuePilotNextHtmlNativeGoodPriceFromSavedRequest(
    val identityKey: String,
    val displayName: String
)

internal data class ValuePilotNextHtmlNativeProductDetailRequest(
    val identityKey: String,
    val query: String
)

internal data class ValuePilotNextHtmlNativeSaveRequest(
    val identityKey: String,
    val displayName: String,
    val brand: String?,
    val matchLabel: String,
    val packageQuantityLabel: String?,
    val sourceLabel: String?,
    val gtin: String?
)

internal data class ValuePilotNextHtmlNativeRemoveRequest(
    val identityKey: String
)

internal data class ValuePilotNextHtmlNativeTripRequest(
    val oneWayDistance: String,
    val fuelConsumption: String,
    val fuelPrice: String,
    val tolls: String,
    val parking: String
)

internal data class ValuePilotNextHtmlNativePhotoRequest(
    val brand: String?,
    val name: String,
    val quantity: String?
) {
    val query: String
        get() = listOfNotNull(
            brand?.trim()?.takeIf { it.isNotEmpty() },
            name.trim().takeIf { it.isNotEmpty() },
            quantity?.trim()?.takeIf { it.isNotEmpty() },
            "envase",
            "Argentina"
        ).joinToString(" ")
}

internal data class ValuePilotNextHtmlProduct(
    val productId: String,
    val brand: String,
    val name: String,
    val quantity: String
) {
    val query: String
        get() = listOf(brand, name, quantity, "envase", "Argentina").joinToString(" ")
}

/** Native boundary for the supplied HTML prototype's external and Search actions. */
internal object ValuePilotNextHtmlPreviewContract {
    const val GOOGLE_IMAGES_HOST = "www.google.com"
    const val MAX_QUERY_LENGTH = 240
    const val MAX_PRODUCT_ID_LENGTH = 64

    private val productIdPattern = Regex("[a-z0-9-]{1,64}")
    private val products = listOf(
        ValuePilotNextHtmlProduct(
            productId = "milk-armonia-1l",
            brand = "Armonía",
            name = "Leche Entera Clásica",
            quantity = "1.000 ml"
        ),
        ValuePilotNextHtmlProduct(
            productId = "milk-serenisima-1l",
            brand = "La Serenísima",
            name = "Leche Entera Clásica 3%",
            quantity = "1.000 ml"
        ),
        ValuePilotNextHtmlProduct(
            productId = "milk-light-1l",
            brand = "La Serenísima",
            name = "Leche Descremada 1%",
            quantity = "1.000 ml"
        ),
        ValuePilotNextHtmlProduct(
            productId = "milk-ilolay-1l",
            brand = "Ilolay",
            name = "Leche Entera Clásica",
            quantity = "1.000 ml"
        ),
        ValuePilotNextHtmlProduct(
            productId = "rice-arroz-1kg",
            brand = "Molinos Ala",
            name = "Arroz Largo Fino 00000",
            quantity = "1 kg"
        ),
        ValuePilotNextHtmlProduct(
            productId = "oil-oliva-500ml",
            brand = "Aceite de Oliva",
            name = "Extra Virgen",
            quantity = "500 ml"
        )
    )
    private val productsById = products.associateBy(ValuePilotNextHtmlProduct::productId)

    fun parseOpenGoogleImagesMessage(rawMessage: String?): ValuePilotNextHtmlPhotoRequest? {
        if (rawMessage.isNullOrBlank() || rawMessage.length > 1_024) return null
        val json = runCatching { JSONObject(rawMessage) }.getOrNull() ?: return null
        if (json.optString("type") != "OPEN_GOOGLE_IMAGES") return null

        val productId = json.optString("productId").trim()
        val query = json.optString("query").trim()
        if (!productIdPattern.matches(productId) || productId.length > MAX_PRODUCT_ID_LENGTH) return null
        if (query.isBlank() || query.length > MAX_QUERY_LENGTH || containsControlCharacter(query)) return null

        val expected = productsById[productId]?.query ?: return null
        if (query != expected) return null
        return ValuePilotNextHtmlPhotoRequest(productId, query)
    }

    fun parseSetLanguageMessage(rawMessage: String?): ValuePilotNextHtmlLanguageRequest? {
        if (rawMessage.isNullOrBlank() || rawMessage.length > 256) return null
        val json = runCatching { JSONObject(rawMessage) }.getOrNull() ?: return null
        if (json.optString("type") != "SET_LANGUAGE") return null
        return when (json.optString("language")) {
            "en" -> ValuePilotNextHtmlLanguageRequest("en")
            "es-AR" -> ValuePilotNextHtmlLanguageRequest("es-AR")
            else -> null
        }
    }

    /**
     * Parses the explicit opt-in from the HTML preview to the existing Kotlin
     * Search surface. The preview never sends arbitrary URLs or backend data;
     * it only supplies the bounded user query and the native surface applies
     * its normal release, App Check and evidence rules.
     */
    fun parseNativeSearchMessage(rawMessage: String?): ValuePilotNextHtmlNativeSearchRequest? {
        if (rawMessage.isNullOrBlank() || rawMessage.length > 1_024) return null
        val json = runCatching { JSONObject(rawMessage) }.getOrNull() ?: return null
        if (json.optString("type") != "SEARCH_VALUEPILOT") return null
        val query = json.optString("query").trim()
        if (
            query.isBlank() ||
            query.length > 96 ||
            containsControlCharacter(query)
        ) return null
        return ValuePilotNextHtmlNativeSearchRequest(query)
    }

    /** Opens native Product Detail only for an identity from the current Search query. */
    fun parseNativeProductDetailMessage(
        rawMessage: String?
    ): ValuePilotNextHtmlNativeProductDetailRequest? {
        if (rawMessage.isNullOrBlank() || rawMessage.length > 1_536) return null
        val json = runCatching { JSONObject(rawMessage) }.getOrNull() ?: return null
        if (json.optString("type") != "OPEN_NATIVE_PRODUCT_DETAIL") return null
        val identityKey = json.optString("identityKey").trim()
        val query = json.optString("query").trim()
        if (
            identityKey.isBlank() || identityKey.length > 256 ||
            query.isBlank() || query.length > 96 ||
            containsControlCharacter(identityKey) || containsControlCharacter(query)
        ) return null
        return ValuePilotNextHtmlNativeProductDetailRequest(identityKey, query)
    }

    /** Requests the identity-only Saved surface from the isolated Kotlin journey. */
    fun parseNativeSavedReadMessage(rawMessage: String?): ValuePilotNextHtmlNativeSavedReadRequest? {
        if (rawMessage.isNullOrBlank() || rawMessage.length > 256) return null
        val json = runCatching { JSONObject(rawMessage) }.getOrNull() ?: return null
        return if (json.optString("type") == "READ_NATIVE_SAVED") {
            ValuePilotNextHtmlNativeSavedReadRequest
        } else {
            null
        }
    }

    fun parseNativeBasketMessage(rawMessage: String?): ValuePilotNextHtmlNativeBasketRequest? {
        if (rawMessage.isNullOrBlank() || rawMessage.length > 256) return null
        val json = runCatching { JSONObject(rawMessage) }.getOrNull() ?: return null
        return if (json.optString("type") == "OPEN_NATIVE_BASKET") {
            ValuePilotNextHtmlNativeBasketRequest
        } else {
            null
        }
    }

    fun parseNativeBasketPlanMessage(rawMessage: String?): ValuePilotNextHtmlNativeBasketPlanRequest? {
        if (rawMessage.isNullOrBlank() || rawMessage.length > 256) return null
        val json = runCatching { JSONObject(rawMessage) }.getOrNull() ?: return null
        return if (json.optString("type") == "REQUEST_NATIVE_BASKET_PLAN") {
            ValuePilotNextHtmlNativeBasketPlanRequest
        } else {
            null
        }
    }

    fun parseNativeObservedPriceReadMessage(
        rawMessage: String?
    ): ValuePilotNextHtmlNativeObservedPriceReadRequest? {
        if (rawMessage.isNullOrBlank() || rawMessage.length > 256) return null
        val json = runCatching { JSONObject(rawMessage) }.getOrNull() ?: return null
        return if (json.optString("type") == "READ_NATIVE_OBSERVED_PRICE") {
            ValuePilotNextHtmlNativeObservedPriceReadRequest
        } else {
            null
        }
    }

    fun parseNativeObservedPriceSelectionMessage(
        rawMessage: String?
    ): ValuePilotNextHtmlNativeObservedPriceSelectionRequest? {
        if (rawMessage.isNullOrBlank() || rawMessage.length > 1_024) return null
        val json = runCatching { JSONObject(rawMessage) }.getOrNull() ?: return null
        if (json.optString("type") != "SELECT_NATIVE_OBSERVED_PRICE") return null
        val kind = json.optString("kind").trim()
        if (kind !in setOf("PRODUCT", "STORE", "CLEAR_PRODUCT", "CLEAR_STORE", "CLEAR_SELECTION")) {
            return null
        }
        val key = json.optString("key").trim().takeIf { it.isNotEmpty() }
        if (key != null && (key.length > 512 || containsControlCharacter(key))) return null
        if (kind == "PRODUCT" || kind == "STORE") {
            if (key == null) return null
        } else if (key != null) {
            return null
        }
        return ValuePilotNextHtmlNativeObservedPriceSelectionRequest(kind, key)
    }

    fun parseNativeObservedPricePrefillMessage(
        rawMessage: String?
    ): ValuePilotNextHtmlNativeObservedPricePrefillRequest? {
        if (rawMessage.isNullOrBlank() || rawMessage.length > 256) return null
        val json = runCatching { JSONObject(rawMessage) }.getOrNull() ?: return null
        return if (json.optString("type") == "REQUEST_NATIVE_OBSERVED_PRICE_PREFILL") {
            ValuePilotNextHtmlNativeObservedPricePrefillRequest
        } else {
            null
        }
    }

    fun parseNativeObservedPriceAmountMessage(
        rawMessage: String?
    ): ValuePilotNextHtmlNativeObservedPriceAmountRequest? {
        if (rawMessage.isNullOrBlank() || rawMessage.length > 512) return null
        val json = runCatching { JSONObject(rawMessage) }.getOrNull() ?: return null
        if (json.optString("type") != "SET_NATIVE_OBSERVED_PRICE") return null
        val amount = json.optString("amount").trim()
        val currency = json.optString("currency").trim()
        if (
            amount.isBlank() || amount.length > 64 || containsControlCharacter(amount) ||
                !amount.matches(Regex("-?\\d+(?:[.,]\\d+)?")) ||
            currency.length !in 3..12 || !currency.matches(Regex("[A-Za-z]{3,12}")) ||
            containsControlCharacter(currency)
        ) return null
        return ValuePilotNextHtmlNativeObservedPriceAmountRequest(
            amount = amount,
            currency = currency.uppercase()
        )
    }

    fun parseNativeObservedPriceTimeMessage(
        rawMessage: String?
    ): ValuePilotNextHtmlNativeObservedPriceTimeRequest? {
        if (rawMessage.isNullOrBlank() || rawMessage.length > 512) return null
        val json = runCatching { JSONObject(rawMessage) }.getOrNull() ?: return null
        if (json.optString("type") != "SET_NATIVE_OBSERVED_TIME") return null
        val date = json.optString("date").trim()
        val time = json.optString("time").trim()
        val utcOffset = json.optString("utcOffset").trim()
        if (
            date.isBlank() || date.length > 32 || containsControlCharacter(date) ||
            time.isBlank() || time.length > 32 || containsControlCharacter(time) ||
            utcOffset.isBlank() || utcOffset.length > 16 || containsControlCharacter(utcOffset)
        ) return null
        return ValuePilotNextHtmlNativeObservedPriceTimeRequest(date, time, utcOffset)
    }

    fun parseNativeObservedPriceProofMessage(
        rawMessage: String?
    ): ValuePilotNextHtmlNativeObservedPriceProofRequest? {
        if (rawMessage.isNullOrBlank() || rawMessage.length > 1_024) return null
        val json = runCatching { JSONObject(rawMessage) }.getOrNull() ?: return null
        if (json.optString("type") != "SET_NATIVE_OBSERVED_PROOF") return null
        val artifactId = json.optString("artifactId").trim()
        val proofType = json.optString("proofType").trim()
        if (
            artifactId.isBlank() || artifactId.length > 160 || containsControlCharacter(artifactId) ||
            proofType !in setOf("RECEIPT", "PRICE_TAG")
        ) return null
        return ValuePilotNextHtmlNativeObservedPriceProofRequest(artifactId, proofType)
    }

    fun parseNativeObservedPriceProofPickerMessage(
        rawMessage: String?
    ): ValuePilotNextHtmlNativeObservedPriceProofPickerRequest? {
        if (rawMessage.isNullOrBlank() || rawMessage.length > 256) return null
        val json = runCatching { JSONObject(rawMessage) }.getOrNull() ?: return null
        return if (json.optString("type") == "SELECT_NATIVE_OBSERVED_PROOF") {
            ValuePilotNextHtmlNativeObservedPriceProofPickerRequest
        } else {
            null
        }
    }

    fun parseNativeObservedPriceSubmitMessage(
        rawMessage: String?
    ): ValuePilotNextHtmlNativeObservedPriceSubmitRequest? {
        if (rawMessage.isNullOrBlank() || rawMessage.length > 256) return null
        val json = runCatching { JSONObject(rawMessage) }.getOrNull() ?: return null
        return if (json.optString("type") == "SUBMIT_NATIVE_OBSERVED_PRICE") {
            ValuePilotNextHtmlNativeObservedPriceSubmitRequest
        } else {
            null
        }
    }

    fun parseNativeObservedPriceCancelMessage(
        rawMessage: String?
    ): ValuePilotNextHtmlNativeObservedPriceCancelRequest? {
        if (rawMessage.isNullOrBlank() || rawMessage.length > 256) return null
        val json = runCatching { JSONObject(rawMessage) }.getOrNull() ?: return null
        return if (json.optString("type") == "CANCEL_NATIVE_OBSERVED_PRICE") {
            ValuePilotNextHtmlNativeObservedPriceCancelRequest
        } else {
            null
        }
    }

    fun parseNativeSavedToolsMessage(rawMessage: String?): ValuePilotNextHtmlNativeSavedToolsRequest? {
        if (rawMessage.isNullOrBlank() || rawMessage.length > 256) return null
        val json = runCatching { JSONObject(rawMessage) }.getOrNull() ?: return null
        if (json.optString("type") != "OPEN_NATIVE_SAVED_TOOLS") return null
        val destination = json.optString("destination").trim().ifEmpty { "SAVED" }
        if (destination !in SUPPORTED_NATIVE_SAVED_DESTINATIONS) return null
        return ValuePilotNextHtmlNativeSavedToolsRequest(destination)
    }

    /** Opens a supported Compose journey without granting the WebView arbitrary routing. */
    fun parseNativeJourneyMessage(rawMessage: String?): ValuePilotNextHtmlNativeJourneyRequest? {
        if (rawMessage.isNullOrBlank() || rawMessage.length > 256) return null
        val json = runCatching { JSONObject(rawMessage) }.getOrNull() ?: return null
        if (json.optString("type") != "OPEN_NATIVE_JOURNEY") return null
        val destination = json.optString("destination").trim()
        if (destination !in SUPPORTED_NATIVE_JOURNEY_DESTINATIONS) return null
        return ValuePilotNextHtmlNativeJourneyRequest(destination)
    }

    /** Starts Good Price from one exact identity already returned by native Saved. */
    fun parseNativeGoodPriceFromSavedMessage(
        rawMessage: String?
    ): ValuePilotNextHtmlNativeGoodPriceFromSavedRequest? {
        if (rawMessage.isNullOrBlank() || rawMessage.length > 2_048) return null
        val json = runCatching { JSONObject(rawMessage) }.getOrNull() ?: return null
        if (json.optString("type") != "OPEN_NATIVE_GOOD_PRICE_FROM_SAVED") return null
        val identityKey = json.optString("identityKey").trim()
        val displayName = json.optString("displayName").trim()
        if (
            identityKey.isBlank() || identityKey.length > 256 ||
            displayName.isBlank() || displayName.length > 512 ||
            containsControlCharacter(identityKey) || containsControlCharacter(displayName)
        ) return null
        return ValuePilotNextHtmlNativeGoodPriceFromSavedRequest(identityKey, displayName)
    }

    /**
     * Parses a save request carrying the exact identity fields rendered by
     * native Search. The Activity still matches the identity against the
     * current Kotlin presentation before persisting it.
     */
    fun parseNativeSaveMessage(rawMessage: String?): ValuePilotNextHtmlNativeSaveRequest? {
        if (rawMessage.isNullOrBlank() || rawMessage.length > 4_096) return null
        val json = runCatching { JSONObject(rawMessage) }.getOrNull() ?: return null
        if (json.optString("type") != "SAVE_VALUEPILOT_IDENTITY") return null
        val identityKey = json.optString("identityKey").trim()
        val displayName = json.optString("displayName").trim()
        val brand = json.optString("brand").trim().takeIf { it.isNotEmpty() }
        val matchLabel = json.optString("matchLabel").trim()
        val packageQuantityLabel = json.optString("packageQuantityLabel")
            .trim()
            .takeIf { it.isNotEmpty() }
        val sourceLabel = json.optString("sourceLabel").trim().takeIf { it.isNotEmpty() }
        val gtin = json.optString("gtin").trim().takeIf { it.isNotEmpty() }
        if (
            identityKey.isBlank() || identityKey.length > 256 ||
            displayName.isBlank() || displayName.length > 512 ||
            brand != null && brand.length > 160 ||
            matchLabel.isBlank() || matchLabel.length > 96 ||
            packageQuantityLabel != null && packageQuantityLabel.length > 64 ||
            sourceLabel != null && sourceLabel.length > 160 ||
            gtin != null && !gtin.matches(Regex("[0-9]{8}|[0-9]{12}|[0-9]{13}|[0-9]{14}"))
        ) return null
        val values = listOfNotNull(
            identityKey,
            displayName,
            brand,
            matchLabel,
            packageQuantityLabel,
            sourceLabel,
            gtin
        )
        if (values.any(::containsControlCharacter)) return null
        return ValuePilotNextHtmlNativeSaveRequest(
            identityKey = identityKey,
            displayName = displayName,
            brand = brand,
            matchLabel = matchLabel,
            packageQuantityLabel = packageQuantityLabel,
            sourceLabel = sourceLabel,
            gtin = gtin
        )
    }

    fun parseNativeRemoveMessage(rawMessage: String?): ValuePilotNextHtmlNativeRemoveRequest? {
        if (rawMessage.isNullOrBlank() || rawMessage.length > 1_024) return null
        val json = runCatching { JSONObject(rawMessage) }.getOrNull() ?: return null
        if (json.optString("type") != "REMOVE_VALUEPILOT_IDENTITY") return null
        val identityKey = json.optString("identityKey").trim()
        if (identityKey.isBlank() || identityKey.length > 256 || containsControlCharacter(identityKey)) {
            return null
        }
        return ValuePilotNextHtmlNativeRemoveRequest(identityKey)
    }

    /** Keeps the preview's trip form on the existing deterministic Kotlin calculator. */
    fun parseNativeTripMessage(rawMessage: String?): ValuePilotNextHtmlNativeTripRequest? {
        if (rawMessage.isNullOrBlank() || rawMessage.length > 2_048) return null
        val json = runCatching { JSONObject(rawMessage) }.getOrNull() ?: return null
        if (json.optString("type") != "CALCULATE_NATIVE_TRIP") return null
        val values = listOf(
            json.optString("oneWayDistance").trim(),
            json.optString("fuelConsumption").trim(),
            json.optString("fuelPrice").trim(),
            json.optString("tolls").trim(),
            json.optString("parking").trim()
        )
        if (
            values.any { it.isBlank() || it.length > 48 || containsControlCharacter(it) } ||
            values.any { !it.matches(Regex("[0-9]+(?:\\.[0-9]+)?")) }
        ) return null
        return ValuePilotNextHtmlNativeTripRequest(
            oneWayDistance = values[0],
            fuelConsumption = values[1],
            fuelPrice = values[2],
            tolls = values[3],
            parking = values[4]
        )
    }

    /**
     * Native Search identities can be outside the six visual fixtures. Keep
     * the same strict query construction while allowing the real identity
     * fields returned by the Kotlin shopping engine.
     */
    fun parseNativeGoogleImagesMessage(rawMessage: String?): ValuePilotNextHtmlNativePhotoRequest? {
        if (rawMessage.isNullOrBlank() || rawMessage.length > 1_024) return null
        val json = runCatching { JSONObject(rawMessage) }.getOrNull() ?: return null
        if (json.optString("type") != "OPEN_GOOGLE_IMAGES_NATIVE") return null
        val brand = json.optString("brand").trim().takeIf { it.isNotEmpty() }
        val name = json.optString("name").trim()
        val quantity = json.optString("quantity").trim().takeIf { it.isNotEmpty() }
        if (name.isBlank() || name.length > 160) return null
        if (brand != null && brand.length > 120) return null
        if (quantity != null && quantity.length > 64) return null
        val request = ValuePilotNextHtmlNativePhotoRequest(brand, name, quantity)
        if (request.query.length > MAX_QUERY_LENGTH || containsControlCharacter(request.query)) return null
        return request
    }

    fun urlFor(request: ValuePilotNextHtmlPhotoRequest): String =
        "https://$GOOGLE_IMAGES_HOST/search?tbm=isch&q=${encodeQuery(request.query)}"

    fun urlFor(request: ValuePilotNextHtmlNativePhotoRequest): String =
        "https://$GOOGLE_IMAGES_HOST/search?tbm=isch&q=${encodeQuery(request.query)}"

    fun productFor(productId: String): ValuePilotNextHtmlProduct? = productsById[productId]

    /** The WebView may retain harmless outer whitespace around the submitted Search query. */
    fun productDetailQueryMatches(currentQuery: String, requestedQuery: String): Boolean =
        currentQuery.trim() == requestedQuery.trim()

    private fun containsControlCharacter(value: String): Boolean = value.any { it.code < 0x20 || it.code == 0x7f }

    private val SUPPORTED_NATIVE_SAVED_DESTINATIONS = setOf(
        "SAVED",
        "OBSERVED_PRICE_SELECTION",
        "STAPLE_WATCH_SETUP",
        "PRIVATE_PRICE_HISTORY"
    )

    private val SUPPORTED_NATIVE_JOURNEY_DESTINATIONS = setOf(
        "GOOD_PRICE_CHECK",
        "COMPARE"
    )

    private fun encodeQuery(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")
}
