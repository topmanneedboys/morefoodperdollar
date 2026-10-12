package com.valuepilot.app

import com.valuepilot.core.OfflineCatalogDiscoveryMatch
import com.valuepilot.core.OfflineCatalogDiscoveryResult
import com.valuepilot.core.OfflineCatalogMatchKind
import com.valuepilot.core.OfflineCatalogSnapshotManifest

/**
 * Immutable presentation for Search's exact identity rail.
 *
 * The bundled catalog contributes names and optional brands only. The
 * The offline identity rail contributes names and optional brands only. The
 * qualified Argentina rail may attach a bounded, source-labelled current
 * offer preview. The preview is evidence only: it carries no stock,
 * availability or ranking authority.
 */
internal data class PracticalShoppingSearchIdentityPresentation(
    val query: String,
    val matches: List<Match>,
    val evaluatedCandidateCount: Int,
    val notice: String,
    val page: Int = 0,
    val hasMore: Boolean = false,
    val nextPage: Int? = null,
    val limitReached: Boolean = false,
    val clarification: ArgentinaBackendContract.SearchClarification? = null
) {
    data class Match(
        val displayName: String,
        val brand: String?,
        val matchLabel: String,
        /** Exact structured package quantity when the source supplied one. */
        val packageQuantityLabel: String? = null,
        /** Source attribution supplied by the identity rail, when available. */
        val sourceLabel: String? = null,
        val identityKey: String = displayName,
        /** The source-validated GTIN, when the exact identity supplied one. */
        val gtin: String? = null,
        /** Bounded exact current-price evidence, when the backend supplied it. */
        val qualifiedOffers: List<ArgentinaBackendContract.SearchOffer> = emptyList()
    ) {
        init {
            require(displayName.isNotBlank())
            require(brand == null || brand.isNotBlank())
            require(matchLabel.isNotBlank())
            require(packageQuantityLabel == null || packageQuantityLabel.isNotBlank())
            require(packageQuantityLabel == null || packageQuantityLabel.length <= 64)
            require(sourceLabel == null || sourceLabel.isNotBlank())
            require(sourceLabel == null || sourceLabel.length <= 160)
            require(identityKey.isNotBlank())
            require(gtin == null || gtin.matches(Regex("[0-9]{8}|[0-9]{12}|[0-9]{13}|[0-9]{14}")))
            require(qualifiedOffers.size <= ArgentinaBackendContract.MAX_SEARCH_OFFERS_PER_IDENTITY)
            require(qualifiedOffers.all { it.productEvidenceKey == identityKey })
        }
    }

    init {
        require(query.isNotBlank())
        require(matches.size <= 30)
        require(evaluatedCandidateCount >= matches.size)
        require(evaluatedCandidateCount <= OfflineCatalogSnapshotManifest.MAX_TOTAL_RECORDS)
        require(notice.isNotBlank())
        require(page in 0..ArgentinaBackendContract.MAX_SEARCH_PAGE)
        require(nextPage == null || nextPage == page + 1)
        require(!limitReached || hasMore)
        require(!hasMore || nextPage != null || limitReached)
    }

    /** Dialog-safe summary that keeps the evidence boundary visible. */
    val summaryMessage: String
        get() = buildString {
            append(notice)
            append("\n\nChecked ")
            append(evaluatedCandidateCount)
            append(" bundled product identities.")
        }

    /** Dialog-safe result text for empty and non-empty matches. */
    val message: String
        get() = buildString {
            append(summaryMessage)
            if (matches.isEmpty()) {
                append("\nNo matching product identity was returned by the qualified source.")
                append(" This does not mean the product is unavailable; it only means this request had no match.")
            } else {
                append("\n\n")
                matches.forEachIndexed { index, match ->
                    if (index > 0) append("\n")
                    append("• ")
                    append(match.displayName)
                    match.brand?.let { append(" · ").append(it) }
                    match.packageQuantityLabel?.let { append(" · package ").append(it) }
                    append(" (").append(match.matchLabel).append(")")
                }
            }
        }

    companion object {
        private const val NOTICE =
            "Signed offline product-identity suggestions from Canada-labelled Open Food Facts snapshots for the GTA and Metro Vancouver (ODbL-1.0) only — no current prices, stock, store availability or freshness are included; package size appears only when explicitly supplied by the source."

        fun from(
            query: String,
            result: OfflineCatalogDiscoveryResult
        ): PracticalShoppingSearchIdentityPresentation {
            val normalizedQuery = query.trim()
            require(normalizedQuery.isNotBlank())
            return PracticalShoppingSearchIdentityPresentation(
                query = normalizedQuery,
                matches = result.matches.map(::mapMatch),
                evaluatedCandidateCount = result.evaluatedCandidateCount,
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
