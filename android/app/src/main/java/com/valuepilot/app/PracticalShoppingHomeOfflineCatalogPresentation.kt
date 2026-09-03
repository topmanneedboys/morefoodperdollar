package com.valuepilot.app

import com.valuepilot.core.OfflineCatalogDiscoveryMatch
import com.valuepilot.core.OfflineCatalogDiscoveryResult
import com.valuepilot.core.OfflineCatalogMatchKind

/**
 * Immutable Home presentation for an offline catalog lookup.
 *
 * The catalog contributes identity suggestions only. This presentation never
 * carries a price, package quantity, store, availability, freshness or rank and
 * therefore cannot turn an identity match into a planner or Best Value result.
 */
data class PracticalShoppingHomeOfflineCatalogPresentation(
    val query: String,
    val matches: List<Match>,
    val notice: String
) {
    data class Match(
        val displayName: String,
        val brand: String?,
        val matchLabel: String
    ) {
        init {
            require(displayName.isNotBlank())
            require(brand == null || brand.isNotBlank())
            require(matchLabel.isNotBlank())
        }
    }

    init {
        require(query.isNotBlank())
        require(matches.size <= 24)
        require(notice.isNotBlank())
    }

    /** Dialog-safe text that keeps the identity-only boundary visible. */
    val message: String
        get() = buildString {
            append(notice)
            if (matches.isEmpty()) {
                append("\n\nNo matching product identity was found in this bundled snapshot.")
            } else {
                append("\n\n")
                matches.forEachIndexed { index, match ->
                    if (index > 0) append("\n")
                    append("• ")
                    append(match.displayName)
                    match.brand?.let { append(" · ").append(it) }
                    append(" (").append(match.matchLabel).append(")")
                }
            }
        }

    companion object {
        private const val NOTICE =
            "Identity suggestions from a Canada-labelled offline snapshot only — no current prices, package quantities, stock, store availability or freshness are included."

        fun from(
            query: String,
            result: OfflineCatalogDiscoveryResult
        ): PracticalShoppingHomeOfflineCatalogPresentation {
            val normalizedQuery = query.trim()
            require(normalizedQuery.isNotBlank())
            return PracticalShoppingHomeOfflineCatalogPresentation(
                query = normalizedQuery,
                matches = result.matches.map(::mapMatch),
                notice = NOTICE
            )
        }

        private fun mapMatch(match: OfflineCatalogDiscoveryMatch): Match =
            Match(
                displayName = match.product.displayName,
                brand = match.product.brand,
                matchLabel = matchLabel(match.kind)
            )

        private fun matchLabel(kind: OfflineCatalogMatchKind): String =
            when (kind) {
                OfflineCatalogMatchKind.EXACT_GTIN -> "exact barcode"
                OfflineCatalogMatchKind.EXACT_NAME -> "exact name"
                OfflineCatalogMatchKind.TOKEN_MATCH -> "name match"
                OfflineCatalogMatchKind.PREFIX_MATCH -> "name prefix"
                OfflineCatalogMatchKind.TYPO_MATCH -> "possible typo match"
            }
    }
}
