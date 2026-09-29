package com.valuepilot.app

import java.net.URLEncoder

/**
 * Builds the one-way, user-triggered Google Images handoff for a selected
 * product identity. This contains no image retrieval or product authority.
 */
object GoogleProductPhotoViewer {

    const val GOOGLE_IMAGES_HOST = "www.google.com"
    const val MAX_QUERY_LENGTH = 256

    /**
     * Uses only public identity fields that are useful to a shopper. The
     * provider identity key, source metadata, price data and private state are
     * deliberately excluded from the query.
     */
    fun queryFor(identity: PracticalShoppingPrivateIdentity): String? =
        queryFor(
            displayName = identity.displayName,
            brand = identity.brand,
            packageQuantityLabel = identity.packageQuantityLabel
        )

    fun queryFor(
        displayName: String,
        brand: String?,
        packageQuantityLabel: String?
    ): String? {
        val fields =
            listOf(displayName, brand, packageQuantityLabel)
                .mapNotNull(::cleanField)
        if (fields.isEmpty()) return null
        return fields.joinToString(" ").take(MAX_QUERY_LENGTH).takeIf { it.isNotBlank() }
    }

    fun urlFor(identity: PracticalShoppingPrivateIdentity): String? =
        queryFor(identity)?.let(::urlForQuery)

    fun urlForQuery(query: String): String? {
        val safeQuery = query.trim().take(MAX_QUERY_LENGTH).takeIf { it.isNotBlank() } ?: return null
        return "https://$GOOGLE_IMAGES_HOST/search?tbm=isch&q=" +
            URLEncoder.encode(safeQuery, Charsets.UTF_8.name())
    }

    private fun cleanField(value: String?): String? {
        val cleaned =
            value
                ?.filterNot(Char::isISOControl)
                ?.replace(WHITESPACE, " ")
                ?.trim()
                ?.take(MAX_FIELD_LENGTH)
        return cleaned?.takeIf { it.isNotBlank() }
    }

    private const val MAX_FIELD_LENGTH = 160
    private val WHITESPACE = Regex("\\s+")
}
