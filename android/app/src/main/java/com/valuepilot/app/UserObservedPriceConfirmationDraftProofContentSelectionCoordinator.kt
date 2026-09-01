package com.valuepilot.app

/**
 * Immutable presentation state for the separate proof-content selection experience.
 *
 * Raw proof bytes are deliberately absent. The physical surface may show only route-local selection
 * status and byte length; it never receives the selected content itself.
 */
internal sealed interface UserObservedPriceConfirmationDraftProofContentSelectionPresentation {
    data object Inactive : UserObservedPriceConfirmationDraftProofContentSelectionPresentation

    data object AwaitingSelection : UserObservedPriceConfirmationDraftProofContentSelectionPresentation

    data class Ready(
        val byteLength: Int
    ) : UserObservedPriceConfirmationDraftProofContentSelectionPresentation {
        init {
            require(byteLength > 0)
        }
    }

    data class Rejected(
        val issue: UserObservedPriceProofContentReadIssue
    ) : UserObservedPriceConfirmationDraftProofContentSelectionPresentation
}

internal fun interface UserObservedPriceConfirmationDraftProofContentSelectionObserver {
    fun onPresentation(
        presentation: UserObservedPriceConfirmationDraftProofContentSelectionPresentation
    )
}

/**
 * Route-local owner for one explicitly selected proof document/photo's already-bounded bytes.
 *
 * The Android picker and [AndroidUserObservedPriceProofContentSource] remain separate adapters. This
 * coordinator accepts only their typed bounded read result, keeps successful bytes transiently in
 * memory while the exact confirmation-draft route is visible, and exposes defensive copies only to
 * a later explicit composition boundary. It never writes the bytes into the confirmation draft.
 *
 * Leaving the route or closing this owner clears the retained array before releasing it. Replacing
 * a selection also clears the prior array. The coordinator owns no URI, Android lifecycle, draft
 * fields, fingerprinting, storage, identifiers, clock, submission, evidence, ranking, or current-
 * price authority.
 */
internal class UserObservedPriceConfirmationDraftProofContentSelectionCoordinator(
    private val requestForegroundSelection: () -> Unit,
    private val observer: UserObservedPriceConfirmationDraftProofContentSelectionObserver
) : AutoCloseable {

    private var routeVisible = false
    private var closed = false
    private var selectedBytes: ByteArray? = null

    fun onRouteVisibilityChanged(visible: Boolean) {
        if (closed || visible == routeVisible) return

        routeVisible = visible
        if (!visible) {
            clearSelectedBytes()
            observer.onPresentation(
                UserObservedPriceConfirmationDraftProofContentSelectionPresentation.Inactive
            )
            return
        }

        observer.onPresentation(
            UserObservedPriceConfirmationDraftProofContentSelectionPresentation.AwaitingSelection
        )
    }

    fun onSelectRequested() {
        if (closed || !routeVisible) return
        requestForegroundSelection()
    }

    fun onContentReadResult(result: UserObservedPriceProofContentReadResult) {
        if (closed || !routeVisible) return

        clearSelectedBytes()
        val bytes = result.bytes
        if (bytes == null) {
            observer.onPresentation(
                UserObservedPriceConfirmationDraftProofContentSelectionPresentation.Rejected(
                    issue = requireNotNull(result.issue)
                )
            )
            return
        }

        selectedBytes = bytes.copyOf()
        observer.onPresentation(
            UserObservedPriceConfirmationDraftProofContentSelectionPresentation.Ready(
                byteLength = bytes.size
            )
        )
    }

    /** Defensive snapshot for a later explicit proof-submission composition slice. */
    fun selectedContentSnapshotOrNull(): ByteArray? =
        if (!closed && routeVisible) selectedBytes?.copyOf() else null

    fun isVisible(): Boolean = !closed && routeVisible

    fun isClosed(): Boolean = closed

    override fun close() {
        if (closed) return

        closed = true
        routeVisible = false
        clearSelectedBytes()
        observer.onPresentation(
            UserObservedPriceConfirmationDraftProofContentSelectionPresentation.Inactive
        )
    }

    private fun clearSelectedBytes() {
        selectedBytes?.fill(0)
        selectedBytes = null
    }
}
