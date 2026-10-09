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

internal data class ValuePilotNextHtmlProduct(
    val productId: String,
    val brand: String,
    val name: String,
    val quantity: String
) {
    val query: String
        get() = listOf(brand, name, quantity, "envase", "Argentina").joinToString(" ")
}

/** Native boundary for the supplied HTML prototype's single external action. */
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

    fun urlFor(request: ValuePilotNextHtmlPhotoRequest): String =
        "https://$GOOGLE_IMAGES_HOST/search?tbm=isch&q=${encodeQuery(request.query)}"

    fun productFor(productId: String): ValuePilotNextHtmlProduct? = productsById[productId]

    private fun containsControlCharacter(value: String): Boolean = value.any { it.code < 0x20 || it.code == 0x7f }

    private fun encodeQuery(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")
}
