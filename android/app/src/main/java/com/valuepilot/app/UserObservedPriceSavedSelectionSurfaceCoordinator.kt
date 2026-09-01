package com.valuepilot.app

/**
 * Android-side wiring adapter between the physical Saved-selection surface and its already-verified
 * composition coordinator.
 *
 * This adapter forwards only the typed actions already emitted by the physical surface. Selection
 * actions remain owned by [UserObservedPriceSavedSelectionCompositionCoordinator], which in turn
 * owns the route/session boundary. The prefill-check marker is forwarded unchanged to that same
 * composition boundary; this adapter never executes a prefill gate itself.
 *
 * It owns no route lifecycle, presentation projection, Saved persistence, identity reconstruction,
 * observed-price draft mutation, proof capture, ranking, navigation, or current-price authority.
 * Closing only detaches callbacks installed on the physical surface and does not close the
 * composition coordinator.
 */
internal class UserObservedPriceSavedSelectionSurfaceCoordinator(
    private val surface: UserObservedPriceSavedSelectionSurfaceView,
    private val compositionCoordinator: UserObservedPriceSavedSelectionCompositionCoordinator
) : AutoCloseable {

    private var closed = false

    init {
        surface.onSelectionAction = compositionCoordinator::onSurfaceAction
        surface.onCheckPrefillAction = compositionCoordinator::onCheckPrefillAction
    }

    override fun close() {
        if (closed) return

        closed = true
        surface.onSelectionAction = null
        surface.onCheckPrefillAction = null
    }
}
