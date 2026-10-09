package com.valuepilot.app

/** Debug-only handoff from the Compose preview to the established Saved tools. */
internal object DebugNextSavedToolsHandoff {
    const val ACTION_OPEN_SAVED_TOOLS =
        "com.valuepilot.app.action.DEBUG_OPEN_SAVED_TOOLS"

    fun shouldOpenSavedTools(
        debugBuild: Boolean,
        action: String?
    ): Boolean =
        debugBuild && action == ACTION_OPEN_SAVED_TOOLS
}
