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
        argentinaArea = areaLabel,
        gtin = match.gtin
    )

    fun queryFor(
        productName: String,
        brand: String?,
        packageQuantity: String?,
        packageType: String?,
        argentinaArea: String?,
        gtin: String? = null
    ): String? {
        val name = cleanField(productName, NAME_LIMIT) ?: return null
        val area = argentinaContext(argentinaArea)
        val productFields = listOfNotNull(
            name,
            cleanField(brand, BRAND_LIMIT),
            cleanField(packageQuantity, QUANTITY_LIMIT),
            cleanField(packageType, PACKAGE_TYPE_LIMIT)
        )
        val areaField = requireNotNull(cleanField(area, AREA_LIMIT))
        val gtinField = cleanField(gtin, GTIN_LIMIT)
            ?.takeIf(GTIN_PATTERN::matches)
            ?.let { "EAN $it" }
        val baseFields = productFields + areaField
        val exactIdentityFields = productFields + listOfNotNull(gtinField) + areaField
        val fields = if (
            gtinField != null && exactIdentityFields.joinToString(" ").length <= MAX_QUERY_LENGTH
        ) exactIdentityFields else baseFields
        return fields.joinToString(" ").takeIf(String::isNotBlank)
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
    private const val GTIN_LIMIT = 14
    private const val AREA_LIMIT = 28
    private const val AREA_INPUT_LIMIT = 64
    private const val AREA_BASE_LIMIT = 16
    private val GTIN_PATTERN = Regex("[0-9]{8}|[0-9]{12}|[0-9]{13}|[0-9]{14}")
    private val WHITESPACE = Regex("\\s+")
}

/** Isolated identities used only to validate the Search layout while live Search is blocked. */
internal object ValuePilotNextSearchFixture {
    private const val PREFIX = "debug-only:search-fixture:"

    private val milk = listOf(
        fixture("milk-armonia", "Leche Entera Clásica", "Armonía", "1 L"),
        fixture("milk-serenisima", "Leche Entera Clásica 3%", "La Serenísima", "1 L"),
        fixture("milk-light", "Leche Descremada 1%", "La Serenísima", "1 L"),
        fixture("milk-ilolay", "Leche Entera Clásica", "Ilolay", "1 L")
    )
    private val rice = listOf(
        fixture("rice-ala", "Arroz Largo Fino 00000", "Molinos Ala", "1 kg"),
        fixture("rice-carolina", "Arroz Doble Carolina", "Molinos Ala", "1 kg"),
        fixture("rice-basmati", "Arroz Basmati", "Jumbo", "500 g"),
        fixture("rice-vapor", "Arroz Listo al Vapor", "Dia", "250 g")
    )
    private val oil = listOf(
        fixture("oil-oliva", "Aceite de Oliva Extra Virgen", "Cocinero", "500 ml"),
        fixture("oil-girasol", "Aceite de Girasol", "Natura", "900 ml"),
        fixture("oil-maiz", "Aceite de Maíz", "Cada Día", "900 ml")
    )

    fun forQuery(query: String): List<PracticalShoppingSearchIdentityPresentation.Match> {
        val normalized = query.trim().lowercase()
        return when {
            normalized.contains("arroz") || normalized.contains("rice") -> rice
            normalized.contains("aceite") || normalized.contains("oil") -> oil
            else -> milk
        }
    }

    fun isFixture(identityKey: String?): Boolean = identityKey?.startsWith(PREFIX) == true

    fun matchFor(identityKey: String?): PracticalShoppingSearchIdentityPresentation.Match? =
        (milk + rice + oil).firstOrNull { it.identityKey == identityKey }

    private fun fixture(
        key: String,
        displayName: String,
        brand: String,
        quantity: String
    ) = PracticalShoppingSearchIdentityPresentation.Match(
        displayName = displayName,
        brand = brand,
        // Internal fixture marker; Search renders the final-app "COMPARISON" pill instead.
        matchLabel = "MUESTRA DE DISEÑO",
        packageQuantityLabel = quantity,
        sourceLabel = null,
        identityKey = PREFIX + key,
        gtin = null,
        qualifiedOffers = emptyList()
    )
}

/** Backward-compatible first fixture used by the Product Detail handoff. */
internal object ValuePilotNextLayoutFixture {
    const val IDENTITY_KEY = "debug-only:search-fixture:milk-armonia"

    val match: PracticalShoppingSearchIdentityPresentation.Match
        get() = ValuePilotNextSearchFixture.matchFor(IDENTITY_KEY)
            ?: error("Missing isolated Search fixture")

    fun isFixture(identityKey: String?): Boolean =
        ValuePilotNextSearchFixture.isFixture(identityKey)

    fun matchFor(identityKey: String?): PracticalShoppingSearchIdentityPresentation.Match? =
        ValuePilotNextSearchFixture.matchFor(identityKey)
}
