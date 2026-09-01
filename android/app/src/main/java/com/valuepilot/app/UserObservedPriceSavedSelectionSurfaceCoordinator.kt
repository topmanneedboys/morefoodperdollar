package com.valuepilot.app

/**
 * Android-side wiring adapter between the physical Saved-selection surface and its route session.
 *
 * This coordinator owns no route lifecycle, presentation projection, Saved persistence, prefill
 * execution, observed-price draft mutation, proof capture, ranking, navigation, or current-price
 * authority. The route session remains the typed selection owner and already rerenders through its
 * verified presenter after selection actions. The prefill-check marker is forwarded unchanged to an
 * external owner rather than executed here.
 *
 * Closing only detaches callbacks installed on the physical surface. It does not close the route
 * session because this coordinator does not own that session's lifecycle.
 */
internal class UserObservedPriceSavedSelectionSurfaceCoordinator(
    private val surface: UserObservedPriceSavedSelectionSurfaceView,
    private val routeSession: UserObservedPriceSavedSelectionRouteSession,
    private val onCheckPrefillAction: (UserObservedPriceSavedPrefillCheckUiAction) -> Unit
) : AutoCloseable {

    private var closed = false

    init {
        surface.onSelectionAction = ::forwardSelectionAction
        surface.onCheckPrefillAction = ::forwardCheckPrefillAction
    }

    private fun forwardSelectionAction(action: UserObservedPriceSavedSelectionAction) {
        if (closed) return
        routeSession.onSelectionAction(action)
    }

    private fun forwardCheckPrefillAction(action: UserObservedPriceSavedPrefillCheckUiAction) {
        if (closed) return
        onCheckPrefillAction(action)
    }

    override fun close() {
        if (closed) return

        closed = true
        surface.onSelectionAction = null
        surface.onCheckPrefillAction = null
    }
}
