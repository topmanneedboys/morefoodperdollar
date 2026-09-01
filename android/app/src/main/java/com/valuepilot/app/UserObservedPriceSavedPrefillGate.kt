package com.valuepilot.app

import com.valuepilot.core.GtinValidation
import com.valuepilot.core.PracticalShoppingStoreIdentityScope
import com.valuepilot.core.ShoppingItemKey
import com.valuepilot.core.ShoppingStoreKey

/** Why a Saved exact item/store pair cannot safely prefill observed-price confirmation identity. */
internal enum class UserObservedPriceSavedPrefillIssue {
    PRODUCT_NOT_SAVED,
    STORE_NOT_SAVED,
    PRODUCT_GTIN_UNAVAILABLE,
    PRODUCT_GTIN_INVALID,
    PRODUCT_DISPLAY_NAME_UNAVAILABLE,
    STORE_DISPLAY_NAME_UNAVAILABLE
}

/**
 * Already-validated Saved identity/display context suitable only for prefilling a future explicit
 * observed-price confirmation experience.
 *
 * [rawGtin] is the checksum-valid GTIN already present on the exact Saved product identity.
 * [storeScope] is copied from the exact Saved store preference. Consumer labels come only from the
 * verified Saved projector and must never be used to reconstruct either identity.
 *
 * This value intentionally contains no price, proof reference/bytes, observation or confirmation
 * id, timestamp, evidence claim, quantity, freshness, current-price status, ranking result, route
 * intent, or submission authority.
 */
internal data class UserObservedPriceSavedPrefill(
    val itemKey: ShoppingItemKey,
    val storeKey: ShoppingStoreKey,
    val rawGtin: String,
    val productName: String,
    val storeScope: PracticalShoppingStoreIdentityScope,
    val storeDisplayName: String
) {
    init {
        require(GtinValidation.isValid(rawGtin))
        require(productName.isNotBlank())
        require(storeDisplayName.isNotBlank())
    }
}

internal data class UserObservedPriceSavedPrefillAttempt(
    val prefill: UserObservedPriceSavedPrefill?,
    val issue: UserObservedPriceSavedPrefillIssue?
) {
    init {
        require((prefill != null) != (issue != null))
    }

    val accepted: Boolean
        get() = prefill != null
}

/**
 * Pure fail-closed adapter from an explicitly chosen Saved item/store key pair to identity-only
 * observed-price prefill context.
 *
 * The caller still owns the explicit user action that chooses [itemKey] and [storeKey]. Calling
 * [request] does not open a route. The gate only verifies that those keys still resolve inside the
 * already-validated Saved snapshot, that the product already carries a checksum-valid GTIN, and
 * that both choices remain consumer-displayable through the verified Saved projector.
 *
 * A provider item id or SKU is never converted into a GTIN. Invalid GTIN values are never repaired.
 * No price, proof, time, id generation, storage, evidence, quantity, freshness, ranking, current
 * price, navigation, Android, or network work occurs here.
 */
internal object UserObservedPriceSavedPrefillGate {

    fun request(
        itemKey: ShoppingItemKey,
        storeKey: ShoppingStoreKey,
        snapshot: PracticalShoppingSavedValidatedSnapshot
    ): UserObservedPriceSavedPrefillAttempt {
        val product =
            snapshot.exactState.productFor(itemKey)
                ?: return rejected(UserObservedPriceSavedPrefillIssue.PRODUCT_NOT_SAVED)
        val store =
            snapshot.exactState.storeFor(storeKey)
                ?: return rejected(UserObservedPriceSavedPrefillIssue.STORE_NOT_SAVED)

        val gtin =
            product.sourceIdentity.gtin
                ?: return rejected(UserObservedPriceSavedPrefillIssue.PRODUCT_GTIN_UNAVAILABLE)
        if (!GtinValidation.isValid(gtin)) {
            return rejected(UserObservedPriceSavedPrefillIssue.PRODUCT_GTIN_INVALID)
        }

        val savedProjection =
            PracticalShoppingSavedExactPreferenceUiProjector.project(
                savedState = snapshot.exactState,
                metadata = snapshot.displayMetadata
            )
        val productRow =
            savedProjection.state.productRows
                .firstOrNull { row -> row.action.itemKey == itemKey }
                ?: return rejected(
                    UserObservedPriceSavedPrefillIssue.PRODUCT_DISPLAY_NAME_UNAVAILABLE
                )
        val storeRow =
            savedProjection.state.storeRows
                .firstOrNull { row -> row.action.storeKey == storeKey }
                ?: return rejected(
                    UserObservedPriceSavedPrefillIssue.STORE_DISPLAY_NAME_UNAVAILABLE
                )

        return UserObservedPriceSavedPrefillAttempt(
            prefill =
                UserObservedPriceSavedPrefill(
                    itemKey = itemKey,
                    storeKey = storeKey,
                    rawGtin = gtin,
                    productName = productRow.title,
                    storeScope = store.scope,
                    storeDisplayName = storeRow.title
                ),
            issue = null
        )
    }

    private fun rejected(
        issue: UserObservedPriceSavedPrefillIssue
    ): UserObservedPriceSavedPrefillAttempt =
        UserObservedPriceSavedPrefillAttempt(
            prefill = null,
            issue = issue
        )
}
