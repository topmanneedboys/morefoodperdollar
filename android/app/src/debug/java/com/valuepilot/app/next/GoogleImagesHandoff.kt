package com.valuepilot.app.next

import com.valuepilot.app.PracticalShoppingSearchIdentityPresentation
import java.net.URLEncoder

/** User-triggered, one-way handoff to the real Google Images webpage. */
internal object GoogleImagesHandoff {
    const val GOOGLE_IMAGES_HOST = "www.google.com"
    const val MAX_QUERY_LENGTH = 256

    /**
     * Uses only public product identity. Provider keys, source labels, prices,
     * observations, and private saved state never enter the external query.
     */
    fun queryFor(
        match: PracticalShoppingSearchIdentityPresentation.Match,
        areaLabel: String,
        packageTypeLabel: String? = null
    ): String? = queryFor(
        productName = match.displayName,
        brand = match.brand,
        packageQuantity = match.packageQuantityLabel,
        packageType = packageTypeLabel,
        argentinaArea = areaLabel
    )

    fun queryFor(
        productName: String,
        brand: String?,
        packageQuantity: String?,
        packageType: String?,
        argentinaArea: String?
    ): String? {
        val name = cleanField(productName, NAME_LIMIT) ?: return null
        val area = argentinaContext(argentinaArea)
        val fields = listOfNotNull(
            name,
            cleanField(brand, BRAND_LIMIT),
            cleanField(packageQuantity, QUANTITY_LIMIT),
            cleanField(packageType, PACKAGE_TYPE_LIMIT),
            cleanField(area, AREA_LIMIT)
        )
        return fields.joinToString(" ").take(MAX_QUERY_LENGTH).takeIf(String::isNotBlank)
    }

    fun urlFor(
        match: PracticalShoppingSearchIdentityPresentation.Match,
        areaLabel: String,
        packageTypeLabel: String? = null
    ): String? = queryFor(match, areaLabel, packageTypeLabel)?.let(::urlForQuery)

    fun urlForQuery(query: String): String? {
        val safeQuery = query.trim().take(MAX_QUERY_LENGTH).takeIf(String::isNotBlank) ?: return null
        val encoded = URLEncoder.encode(safeQuery, Charsets.UTF_8.name())
        return "https://$GOOGLE_IMAGES_HOST/search?tbm=isch&q=$encoded"
    }

    private fun argentinaContext(areaLabel: String?): String {
        val area = cleanField(areaLabel, AREA_INPUT_LIMIT)
        return when {
            area.isNullOrBlank() -> "CABA, Argentina"
            area.contains("CABA", ignoreCase = true) -> "CABA, Argentina"
            area.contains("Argentina", ignoreCase = true) -> "Argentina"
            else -> "${area.take(AREA_BASE_LIMIT)}, Argentina"
        }
    }

    private fun cleanField(value: String?, limit: Int): String? = value
        ?.filterNot(Char::isISOControl)
        ?.replace(WHITESPACE, " ")
        ?.trim()
        ?.take(limit)
        ?.takeIf(String::isNotBlank)

    private const val NAME_LIMIT = 136
    private const val BRAND_LIMIT = 36
    private const val QUANTITY_LIMIT = 28
    private const val PACKAGE_TYPE_LIMIT = 24
    private const val AREA_LIMIT = 28
    private const val AREA_INPUT_LIMIT = 64
    private const val AREA_BASE_LIMIT = 16
    private val WHITESPACE = Regex("\\s+")
}

/** Explicitly isolated sample identity for layout validation while live Search is blocked. */
internal object ValuePilotNextLayoutFixture {
    const val IDENTITY_KEY = "debug-only:layout-fixture:milk-1l"

    val match = PracticalShoppingSearchIdentityPresentation.Match(
        displayName = "Leche entera",
        brand = null,
        matchLabel = "MUESTRA DE DISEÑO",
        packageQuantityLabel = "1 L",
        sourceLabel = null,
        identityKey = IDENTITY_KEY,
        gtin = null,
        qualifiedOffers = emptyList()
    )
}
