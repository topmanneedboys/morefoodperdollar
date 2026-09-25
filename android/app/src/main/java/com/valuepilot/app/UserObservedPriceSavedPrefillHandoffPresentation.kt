package com.valuepilot.app

/** Consumer-only result status for an explicit Saved-pair prefill check. */
internal enum class UserObservedPriceSavedPrefillHandoffUiStatus {
    IDENTITY_PREFILL_READY,
    BLOCKED
}

/**
 * Immutable consumer presentation of a Saved-pair prefill handoff result.
 *
 * An accepted state exposes only the already-verified consumer display names. It intentionally
 * carries no stable identity, product code, store scope, route, draft, price, proof, time, evidence,
 * quantity, freshness, ranking, current-price, persistence, or submission authority.
 */
internal data class UserObservedPriceSavedPrefillHandoffUiState(
    val status: UserObservedPriceSavedPrefillHandoffUiStatus,
    val headline: String,
    val message: String,
    val productName: String? = null,
    val storeDisplayName: String? = null
) {
    init {
        require(headline.isNotBlank())
        require(message.isNotBlank())
        require((productName != null) == (storeDisplayName != null))
        require(productName == null || productName.isNotBlank())
        require(storeDisplayName == null || storeDisplayName.isNotBlank())
        require(
            (status == UserObservedPriceSavedPrefillHandoffUiStatus.IDENTITY_PREFILL_READY) ==
                (productName != null)
        )
    }
}

/**
 * Pure projection from the verified typed handoff result to consumer-safe immutable copy.
 *
 * Every typed blocker is mapped explicitly. Technical identity data is never copied into the UI
 * state, and this projector does not execute another gate or perform any downstream work.
 */
internal object UserObservedPriceSavedPrefillHandoffUiProjector {

    fun project(
        attempt: UserObservedPriceSavedPrefillHandoffAttempt
    ): UserObservedPriceSavedPrefillHandoffUiState {
        attempt.prefill?.let { prefill ->
            return UserObservedPriceSavedPrefillHandoffUiState(
                status = UserObservedPriceSavedPrefillHandoffUiStatus.IDENTITY_PREFILL_READY,
                headline = "Saved pair checked",
                message =
                    "The selected saved product and store can prefill identity details for a later observed-price confirmation.",
                productName = prefill.productName,
                storeDisplayName = prefill.storeDisplayName
            )
        }

        attempt.issue?.let { issue ->
            return blocked(wrapperMessage(issue))
        }

        return blocked(prefillMessage(requireNotNull(attempt.prefillIssue)))
    }

    private fun blocked(message: String): UserObservedPriceSavedPrefillHandoffUiState =
        UserObservedPriceSavedPrefillHandoffUiState(
            status = UserObservedPriceSavedPrefillHandoffUiStatus.BLOCKED,
            headline = "Saved pair needs attention",
            message = message
        )

    private fun wrapperMessage(issue: UserObservedPriceSavedPrefillHandoffIssue): String =
        when (issue) {
            UserObservedPriceSavedPrefillHandoffIssue.SELECTION_NOT_READY ->
                "Choose one saved product and one saved store again before checking."
        }

    private fun prefillMessage(issue: UserObservedPriceSavedPrefillIssue): String =
        when (issue) {
            UserObservedPriceSavedPrefillIssue.PRODUCT_NOT_SAVED ->
                "That saved product is no longer available. Choose a saved product again."
            UserObservedPriceSavedPrefillIssue.STORE_NOT_SAVED ->
                "That saved store is no longer available. Choose a saved store again."
            UserObservedPriceSavedPrefillIssue.PRODUCT_GTIN_UNAVAILABLE ->
                "This saved product cannot be safely identified for observed-price confirmation yet."
            UserObservedPriceSavedPrefillIssue.PRODUCT_GTIN_INVALID ->
                "This saved product identifier cannot be verified for observed-price confirmation. Choose another saved product."
            UserObservedPriceSavedPrefillIssue.PRODUCT_DISPLAY_NAME_UNAVAILABLE ->
                "The selected saved product needs a current display name before it can be checked."
            UserObservedPriceSavedPrefillIssue.STORE_DISPLAY_NAME_UNAVAILABLE ->
                "The selected saved store needs a current display name before it can be checked."
        }
}

/** Replaceable renderer receives only immutable consumer result state. */
internal fun interface UserObservedPriceSavedPrefillHandoffSurfaceRenderer {
    fun render(state: UserObservedPriceSavedPrefillHandoffUiState)
}

/** One-way presentation boundary; it does not execute the handoff or any downstream operation. */
internal class UserObservedPriceSavedPrefillHandoffSurfacePresenter(
    private val renderer: UserObservedPriceSavedPrefillHandoffSurfaceRenderer
) {
    fun render(attempt: UserObservedPriceSavedPrefillHandoffAttempt) {
        renderer.render(UserObservedPriceSavedPrefillHandoffUiProjector.project(attempt))
    }
}
