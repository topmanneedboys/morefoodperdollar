package com.valuepilot.app

internal enum class UserObservedPriceConfirmationDraftUiStatus {
    NEEDS_NON_BYTE_INPUT,
    NON_BYTE_INPUT_COMPLETE
}

/** Consumer-readable label for one exact completeness requirement from the draft finalizer. */
internal data class UserObservedPriceConfirmationDraftMissingRequirementUi(
    val field: UserObservedPriceConfirmationDraftMissingField,
    val label: String
) {
    init {
        require(label.isNotBlank())
    }
}

/**
 * Immutable passive projection of observed-price confirmation draft completeness.
 *
 * This state deliberately contains no submission payload, raw proof bytes, editable values, submit
 * action, picker action, confirmation result, evidence claim, unit value, or ranking result. Draft
 * completeness means only that all non-byte fields are present; it never means those fields have
 * passed semantic confirmation validation or that proof has been retained.
 */
internal data class UserObservedPriceConfirmationDraftUiState(
    val status: UserObservedPriceConfirmationDraftUiStatus,
    val headline: String,
    val statusTitle: String,
    val guidance: String,
    val missingRequirements: List<UserObservedPriceConfirmationDraftMissingRequirementUi>,
    val notice: String
) {
    init {
        require(headline.isNotBlank())
        require(statusTitle.isNotBlank())
        require(guidance.isNotBlank())
        require(notice.isNotBlank())
        require(missingRequirements.map { it.field }.distinct().size == missingRequirements.size)
        require(
            (status == UserObservedPriceConfirmationDraftUiStatus.NON_BYTE_INPUT_COMPLETE) ==
                missingRequirements.isEmpty()
        )
    }
}

/**
 * Pure consumer projection from an already-produced completeness result.
 *
 * The projector reads only [UserObservedPriceConfirmationDraftFinalization.complete] and
 * [UserObservedPriceConfirmationDraftFinalization.missingFields]. It does not rerun finalization,
 * inspect the complete submission payload, validate any field, read or retain proof, generate IDs or
 * time, create evidence, resolve package quantity, calculate unit value, rank offers, or authorize
 * current-price semantics.
 */
internal object UserObservedPriceConfirmationDraftUiProjector {

    fun project(
        finalization: UserObservedPriceConfirmationDraftFinalization
    ): UserObservedPriceConfirmationDraftUiState {
        val missing =
            UserObservedPriceConfirmationDraftMissingField.entries
                .filter { field -> field in finalization.missingFields }
                .map { field ->
                    UserObservedPriceConfirmationDraftMissingRequirementUi(
                        field = field,
                        label = label(field)
                    )
                }
        val complete = finalization.complete

        return UserObservedPriceConfirmationDraftUiState(
            status =
                if (complete) {
                    UserObservedPriceConfirmationDraftUiStatus.NON_BYTE_INPUT_COMPLETE
                } else {
                    UserObservedPriceConfirmationDraftUiStatus.NEEDS_NON_BYTE_INPUT
                },
            headline = "Observed price confirmation",
            statusTitle =
                if (complete) {
                    "Confirmation details complete"
                } else {
                    "Confirmation details needed"
                },
            guidance =
                if (complete) {
                    "All non-byte confirmation details are present. Proof reading, retention, and confirmation validation happen separately."
                } else {
                    "Complete the remaining non-byte details before proof can be submitted for confirmation."
                },
            missingRequirements = missing,
            notice =
                "Draft completeness does not mean the confirmation is accepted. Observed prices are not retailer-confirmed current prices."
        )
    }

    private fun label(
        field: UserObservedPriceConfirmationDraftMissingField
    ): String =
        when (field) {
            UserObservedPriceConfirmationDraftMissingField.ARTIFACT_ID -> "Proof reference"
            UserObservedPriceConfirmationDraftMissingField.PROOF_TYPE -> "Proof type"
            UserObservedPriceConfirmationDraftMissingField.OBSERVATION_ID -> "Observation reference"
            UserObservedPriceConfirmationDraftMissingField.GTIN -> "Product GTIN"
            UserObservedPriceConfirmationDraftMissingField.PRODUCT_NAME -> "Product name"
            UserObservedPriceConfirmationDraftMissingField.PRICE -> "Observed price"
            UserObservedPriceConfirmationDraftMissingField.STORE_SCOPE -> "Exact store scope"
            UserObservedPriceConfirmationDraftMissingField.OBSERVED_AT -> "Observation time"
            UserObservedPriceConfirmationDraftMissingField.CONFIRMATION_ID -> "Confirmation reference"
            UserObservedPriceConfirmationDraftMissingField.CONFIRMED_AT -> "Confirmation time"
        }
}

/** Narrow target for a future replaceable physical observed-price confirmation draft renderer. */
internal fun interface UserObservedPriceConfirmationDraftSurfaceRenderer {
    fun render(state: UserObservedPriceConfirmationDraftUiState)
}

/** Presents completeness-only draft state without exposing the draft submission payload. */
internal class UserObservedPriceConfirmationDraftSurfacePresenter(
    private val renderer: UserObservedPriceConfirmationDraftSurfaceRenderer
) {
    fun render(finalization: UserObservedPriceConfirmationDraftFinalization) {
        renderer.render(UserObservedPriceConfirmationDraftUiProjector.project(finalization))
    }
}
