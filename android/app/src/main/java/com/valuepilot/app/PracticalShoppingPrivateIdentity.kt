package com.valuepilot.app

/**
 * A shopper-selected identity from Search, kept separate from planner items.
 *
 * This is intentionally identity-only. It carries no price, store, stock,
 * availability, ranking or freshness authority. Nullable metadata stays
 * nullable when the source did not provide it.
 */
data class PracticalShoppingPrivateIdentity(
    val identityKey: String,
    val displayName: String,
    val brand: String? = null,
    val sourceLabel: String? = null,
    val packageQuantityLabel: String? = null
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
                packageQuantityLabel = match.packageQuantityLabel
            )
        }
    }
}
