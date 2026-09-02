package com.valuepilot.app

import com.valuepilot.core.ShoppingItemRequestDetail
import com.valuepilot.core.ShoppingProductSpecificity

/**
 * Small presentation-only summary for explicit shopper intent attached to one Home item.
 *
 * This deliberately describes what the shopper saved; it does not infer a product, package
 * evidence, price, substitution or ranking consequence from the detail.
 */
object PracticalShoppingHomeItemDetailsPresentation {

    fun summary(detail: ShoppingItemRequestDetail?): String {
        if (detail == null) return "No extra preferences"

        val parts = mutableListOf<String>()
        detail.requestedQuantity?.let { quantity ->
            quantity.packageCount?.let { count ->
                parts += if (count == 1L) "1 package" else "$count packages"
            }
            if (quantity.totalQuantity != null || quantity.preferredPackageQuantity != null) {
                parts += "Specific amount"
            }
        }
        detail.brandPreference.exactBrandKey?.let { brandKey ->
            parts += "Brand: ${brandKey.value}"
        }
        if (detail.productSpecificity == ShoppingProductSpecificity.EXACT_PRODUCT_REQUIRED) {
            parts += "Exact product"
        }

        return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
            ?: "No extra preferences"
    }

    fun actionLabel(detail: ShoppingItemRequestDetail?): String =
        if (detail == null) "Add details" else "Edit details"
}
