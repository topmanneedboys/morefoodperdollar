package com.valuepilot.app

/** Debug-only handoff from the Compose preview to the established Saved tools. */
internal object DebugNextSavedToolsHandoff {
    const val ACTION_OPEN_SAVED_TOOLS =
        "com.valuepilot.app.action.DEBUG_OPEN_SAVED_TOOLS"
    const val ACTION_OPEN_OBSERVED_PRICE_CONFIRMATION =
        "com.valuepilot.app.action.DEBUG_OPEN_OBSERVED_PRICE_CONFIRMATION"

    const val EXTRA_ITEM_KEY = "com.valuepilot.app.extra.DEBUG_ITEM_KEY"
    const val EXTRA_STORE_KEY = "com.valuepilot.app.extra.DEBUG_STORE_KEY"
    const val EXTRA_RAW_GTIN = "com.valuepilot.app.extra.DEBUG_RAW_GTIN"
    const val EXTRA_PRODUCT_NAME = "com.valuepilot.app.extra.DEBUG_PRODUCT_NAME"
    const val EXTRA_STORE_MERCHANT_KEY = "com.valuepilot.app.extra.DEBUG_STORE_MERCHANT_KEY"
    const val EXTRA_STORE_LOCATION_KEY = "com.valuepilot.app.extra.DEBUG_STORE_LOCATION_KEY"
    const val EXTRA_STORE_CHANNEL_KEY = "com.valuepilot.app.extra.DEBUG_STORE_CHANNEL_KEY"
    const val EXTRA_STORE_DISPLAY_NAME = "com.valuepilot.app.extra.DEBUG_STORE_DISPLAY_NAME"

    fun shouldOpenSavedTools(
        debugBuild: Boolean,
        action: String?
    ): Boolean =
        debugBuild && action == ACTION_OPEN_SAVED_TOOLS

    fun savedToolsPrimaryTabOrNull(
        debugBuild: Boolean,
        action: String?
    ): AppPrimaryTab? =
        if (shouldOpenSavedTools(debugBuild, action)) AppPrimaryTab.SAVED else null

    fun shouldOpenObservedPriceConfirmation(
        debugBuild: Boolean,
        action: String?
    ): Boolean =
        debugBuild && action == ACTION_OPEN_OBSERVED_PRICE_CONFIRMATION
}
