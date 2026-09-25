package com.valuepilot.app

import com.valuepilot.core.ShoppingBrandKey
import com.valuepilot.core.ShoppingBrandPreference
import com.valuepilot.core.ShoppingItemKey
import com.valuepilot.core.ShoppingItemRequestDetail
import com.valuepilot.core.ShoppingProductSpecificity
import com.valuepilot.core.ShoppingRequestedQuantity

/**
 * Pure bounded capture boundary for the optional Home item-details editor.
 *
 * It turns explicit text-field choices into validated core values. It never resolves a product,
 * reads evidence, calculates money or changes the existing sample plan.
 */
object PracticalShoppingHomeItemDetailsEditor {

    private const val MAX_PACKAGE_COUNT = 1_000_000L
    private const val MAX_BRAND_LENGTH = 160

    data class Draft(
        val packageCountText: String,
        val brandText: String,
        val exactProduct: Boolean
    )

    enum class Field {
        PACKAGE_COUNT,
        BRAND
    }

    sealed interface Outcome {
        data class Accepted(val detail: ShoppingItemRequestDetail?) : Outcome

        data class Rejected(
            val field: Field,
            val message: String
        ) : Outcome
    }

    fun initialDraft(detail: ShoppingItemRequestDetail?): Draft =
        Draft(
            packageCountText =
                detail?.requestedQuantity?.packageCount?.toString().orEmpty(),
            brandText = detail?.brandPreference?.exactBrandKey?.value.orEmpty(),
            exactProduct =
                detail?.productSpecificity == ShoppingProductSpecificity.EXACT_PRODUCT_REQUIRED
        )

    /**
     * Applies only the fields represented by this editor. Existing explicit total/package-content
     * quantities are retained when the package-count field is left blank, so opening and saving a
     * future detail cannot silently erase information this small editor does not display.
     */
    fun apply(
        itemKey: ShoppingItemKey,
        current: ShoppingItemRequestDetail?,
        draft: Draft
    ): Outcome {
        require(current == null || current.itemKey == itemKey) {
            "Current Home item detail belongs to another item"
        }

        val packageCountText = draft.packageCountText.trim()
        val packageCount =
            if (packageCountText.isEmpty()) {
                null
            } else {
                val parsed = packageCountText.toLongOrNull()
                if (parsed == null || parsed !in 1..MAX_PACKAGE_COUNT) {
                    return Outcome.Rejected(
                        field = Field.PACKAGE_COUNT,
                        message = "Enter a whole number from 1 to $MAX_PACKAGE_COUNT."
                    )
                }
                parsed
            }

        val brandText = draft.brandText.trim()
        if (brandText.length > MAX_BRAND_LENGTH) {
            return Outcome.Rejected(
                field = Field.BRAND,
                message = "Keep the brand to $MAX_BRAND_LENGTH characters or fewer."
            )
        }

        val existingQuantity = current?.requestedQuantity
        val requestedQuantity =
            when {
                packageCount != null ->
                    ShoppingRequestedQuantity(
                        totalQuantity = existingQuantity?.totalQuantity,
                        packageCount = packageCount,
                        preferredPackageQuantity = existingQuantity?.preferredPackageQuantity
                    )

                existingQuantity != null ->
                    runCatching {
                        ShoppingRequestedQuantity(
                            totalQuantity = existingQuantity.totalQuantity,
                            preferredPackageQuantity = existingQuantity.preferredPackageQuantity
                        )
                    }.getOrNull()

                else -> null
            }

        val brandPreference =
            brandText.takeIf(String::isNotEmpty)?.let { ShoppingBrandPreference.exact(ShoppingBrandKey(it)) }
                ?: ShoppingBrandPreference.flexible()
        val productSpecificity =
            if (draft.exactProduct) {
                ShoppingProductSpecificity.EXACT_PRODUCT_REQUIRED
            } else {
                ShoppingProductSpecificity.BROAD_INTENT
            }

        val detail =
            if (requestedQuantity == null &&
                brandPreference == ShoppingBrandPreference.flexible() &&
                productSpecificity == ShoppingProductSpecificity.BROAD_INTENT
            ) {
                null
            } else {
                ShoppingItemRequestDetail(
                    itemKey = itemKey,
                    productSpecificity = productSpecificity,
                    requestedQuantity = requestedQuantity,
                    brandPreference = brandPreference
                )
            }

        return Outcome.Accepted(detail)
    }
}
