package com.valuepilot.app

import com.valuepilot.core.ShoppingItemKey

/**
 * Fail-closed lifecycle predicate for the Home item-details editor.
 *
 * The editor may only remain visible while its typed item identity is still
 * present in the latest immutable Home projection. This does not interpret
 * products or request details; it only prevents a stale dialog from offering
 * an action for an item that the shopper has already removed or replaced.
 */
internal fun practicalShoppingHomeItemDetailsDialogShouldDismiss(
    activeItemKey: ShoppingItemKey?,
    visibleItemKeys: Collection<ShoppingItemKey>
): Boolean = activeItemKey == null || activeItemKey !in visibleItemKeys
