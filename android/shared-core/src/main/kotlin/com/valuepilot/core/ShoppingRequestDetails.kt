package com.valuepilot.core

private const val MAX_SHOPPING_BRAND_KEY_LENGTH = 160
private const val MAX_REQUESTED_PACKAGE_COUNT = 1_000_000L

/** Opaque stable brand identity selected by the shopper or a trusted local preference. */
@JvmInline
value class ShoppingBrandKey(val value: String) {
    init {
        require(value.isNotBlank())
        require(value.length <= MAX_SHOPPING_BRAND_KEY_LENGTH)
    }
}

/**
 * How narrowly the shopper intends one list item.
 *
 * EXACT_PRODUCT_REQUIRED is only a shopper constraint. It does not establish a
 * production product key, authorize a catalog suggestion, or permit fuzzy matching.
 * Exact product identity still has to be established by the existing production
 * product-identity boundary.
 */
enum class ShoppingProductSpecificity {
    BROAD_INTENT,
    EXACT_PRODUCT_REQUIRED
}

enum class ShoppingBrandFlexibility {
    FLEXIBLE,
    EXACT_REQUIRED
}

/** Shopper brand constraint, separate from product/provider evidence. */
data class ShoppingBrandPreference(
    val flexibility: ShoppingBrandFlexibility,
    val exactBrandKey: ShoppingBrandKey? = null
) {
    init {
        require(
            (flexibility == ShoppingBrandFlexibility.EXACT_REQUIRED) ==
                (exactBrandKey != null)
        ) {
            "An exact brand key is present if and only if exact brand is required"
        }
    }

    companion object {
        fun flexible(): ShoppingBrandPreference =
            ShoppingBrandPreference(ShoppingBrandFlexibility.FLEXIBLE)

        fun exact(brandKey: ShoppingBrandKey): ShoppingBrandPreference =
            ShoppingBrandPreference(
                flexibility = ShoppingBrandFlexibility.EXACT_REQUIRED,
                exactBrandKey = brandKey
            )
    }
}

/**
 * Explicit shopper-requested amount/package preference.
 *
 * This is NOT package-quantity evidence about a product. [totalQuantity] is how
 * much product the shopper wants, [packageCount] is an explicit number of packages
 * when the shopper cares about package count, and [preferredPackageQuantity] is the
 * desired content of each package. No field is inferred from another and this type
 * performs no basket-price arithmetic.
 *
 * When total content and preferred package content are both supplied they must use
 * the same normalized base unit. A shopper wanting "2 bottles of 1 L" can represent
 * that as packageCount=2 plus preferredPackageQuantity=1 L, optionally with a 2 L
 * total. Unknown/unspecified values remain null.
 */
data class ShoppingRequestedQuantity(
    val totalQuantity: NormalizedQuantity? = null,
    val packageCount: Long? = null,
    val preferredPackageQuantity: NormalizedQuantity? = null
) {
    init {
        require(
            totalQuantity != null ||
                packageCount != null ||
                preferredPackageQuantity != null
        ) {
            "Requested quantity must contain at least one explicit shopper preference"
        }
        require(packageCount == null || packageCount in 1..MAX_REQUESTED_PACKAGE_COUNT) {
            "Requested package count is outside the bounded supported range"
        }
        if (totalQuantity != null && preferredPackageQuantity != null) {
            require(totalQuantity.unit == preferredPackageQuantity.unit) {
                "Requested total and preferred package content must use the same base unit"
            }
        }
    }
}

/**
 * Optional typed detail for one stable [ShoppingItemKey].
 *
 * Defaults are intentionally conservative: a normal list item is broad, brand
 * flexible, and has no invented quantity. This object owns no product identity,
 * price, evidence, substitution, ranking, provider, clock, network, or UI authority.
 */
data class ShoppingItemRequestDetail(
    val itemKey: ShoppingItemKey,
    val productSpecificity: ShoppingProductSpecificity = ShoppingProductSpecificity.BROAD_INTENT,
    val requestedQuantity: ShoppingRequestedQuantity? = null,
    val brandPreference: ShoppingBrandPreference = ShoppingBrandPreference.flexible()
)

/**
 * Companion typed-detail layer for the existing bounded [ShoppingRequest].
 *
 * This intentionally does not replace or extend planner basket arithmetic yet.
 * Current planners continue consuming [request] exactly as before. Upstream intent
 * capture may preserve explicit quantity/package/brand/product-specificity choices
 * here until a later, separately verified boundary defines how those choices map to
 * exact products and package-aware basket costs.
 */
data class ShoppingRequestDetails(
    val request: ShoppingRequest,
    val itemDetails: List<ShoppingItemRequestDetail> = emptyList()
) {
    init {
        require(itemDetails.size <= request.itemKeys.size) {
            "Shopping request details cannot outnumber requested items"
        }
        val detailKeys = itemDetails.map { it.itemKey }
        require(detailKeys.distinct().size == detailKeys.size) {
            "Shopping request details must be unique per item"
        }
        require(request.itemKeySet.containsAll(detailKeys)) {
            "Shopping request detail contains an item outside the shopping request"
        }
    }

    private val detailsByItem: Map<ShoppingItemKey, ShoppingItemRequestDetail> =
        itemDetails.associateBy { it.itemKey }

    fun detailFor(itemKey: ShoppingItemKey): ShoppingItemRequestDetail? = detailsByItem[itemKey]
}
