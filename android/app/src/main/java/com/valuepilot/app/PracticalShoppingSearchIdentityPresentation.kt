package com.valuepilot.app

/**
 * Immutable presentation for Search's qualified identity rail.
 *
 * The Argentina identity rail contributes names and optional brands only. It
 * may also carry an explicitly validated package
 * quantity, but this presentation carries no price, store, availability,
 * freshness or ranking authority. A later action may hand the selected name
 * to Scan & compare, but that route still requires the shopper to review and
 * supply exact evidence.
 */
internal data class PracticalShoppingSearchIdentityPresentation(
    val query: String,
    val matches: List<Match>,
    val evaluatedCandidateCount: Int,
    val notice: String,
    val page: Int = 0,
    val hasMore: Boolean = false,
    val nextPage: Int? = null,
    val limitReached: Boolean = false
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
        val gtin: String? = null
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
        }
    }

    init {
        require(query.isNotBlank())
        require(matches.size <= 30)
        require(evaluatedCandidateCount >= matches.size)
        require(evaluatedCandidateCount <= ArgentinaBackendContract.MAX_PRODUCT_CANDIDATES)
        require(notice.isNotBlank())
        require(page in 0..ArgentinaBackendContract.MAX_SEARCH_PAGE)
        require(nextPage == null || nextPage == page + 1)
        require(!limitReached || hasMore)
        require(!hasMore || nextPage != null || limitReached)
    }

    /** Dialog-safe summary that keeps the identity-only boundary visible. */
    val summaryMessage: String
        get() = buildString {
            append(notice)
            append("\n\nChecked ")
            append(evaluatedCandidateCount)
            append(" source identity candidates.")
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

}
