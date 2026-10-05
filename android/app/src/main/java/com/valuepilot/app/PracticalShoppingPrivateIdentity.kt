package com.valuepilot.app

/**
 * A shopper-selected identity from Search, kept separate from planner items.
 *
 * The durable identity remains separate from price authority. A Search result
 * may attach a bounded in-memory offer preview for the current session; the
 * persistence codec intentionally omits that preview so a restart cannot turn
 * old evidence into a current claim.
 */
data class PracticalShoppingPrivateIdentity(
    val identityKey: String,
    val displayName: String,
    val brand: String? = null,
    val sourceLabel: String? = null,
    val packageQuantityLabel: String? = null,
    val qualifiedOffers: List<ArgentinaBackendContract.SearchOffer> = emptyList()
) {
    init {
        require(identityKey.isNotBlank())
        require(identityKey.length <= MAX_IDENTITY_KEY_LENGTH)
        require(displayName.isNotBlank())
        require(displayName.length <= MAX_DISPLAY_NAME_LENGTH)
        require(brand == null || brand.isNotBlank())
        require(brand == null || brand.length <= MAX_BRAND_LENGTH)
        require(sourceLabel == null || sourceLabel.isNotBlank())
        require(sourceLabel == null || sourceLabel.length <= MAX_SOURCE_LABEL_LENGTH)
        require(packageQuantityLabel == null || packageQuantityLabel.isNotBlank())
        require(packageQuantityLabel == null || packageQuantityLabel.length <= MAX_PACKAGE_QUANTITY_LENGTH)
        require(qualifiedOffers.size <= ArgentinaBackendContract.MAX_SEARCH_OFFERS_PER_IDENTITY)
        require(qualifiedOffers.all { it.productEvidenceKey == identityKey })
    }

    companion object {
        const val MAX_IDENTITY_KEY_LENGTH = 256
        const val MAX_DISPLAY_NAME_LENGTH = 512
        const val MAX_BRAND_LENGTH = 160
        const val MAX_SOURCE_LABEL_LENGTH = 160
        const val MAX_PACKAGE_QUANTITY_LENGTH = 64
        const val MAX_COUNT = 100

        /** Builds a private item only when the Search source label is explicit. */
        internal fun fromSearchMatch(
            match: PracticalShoppingSearchIdentityPresentation.Match
        ): PracticalShoppingPrivateIdentity? {
            if (match.sourceLabel.isNullOrBlank()) return null
            return PracticalShoppingPrivateIdentity(
                identityKey = match.identityKey,
                displayName = match.displayName,
                brand = match.brand,
                sourceLabel = match.sourceLabel,
                packageQuantityLabel = match.packageQuantityLabel,
                qualifiedOffers = match.qualifiedOffers
            )
        }
    }
}
