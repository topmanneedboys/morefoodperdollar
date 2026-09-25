package com.valuepilot.app

/**
 * Explicit action boundary that binds the current editable observed-price draft to the current
 * transient proof-content snapshot and one freshly captured confirmation lifecycle metadata pair.
 *
 * The action is allowed only while the route currently exposes every required draft value except
 * confirmation ID/time, and the separate proof-content owner currently exposes selected bytes.
 * Confirmation metadata is captured only after those preconditions pass. The metadata is overlaid
 * into a one-shot immutable submission snapshot and is never written back into the editable draft.
 *
 * This coordinator owns no View, navigation, price/proof parsing, proof reading, byte retention,
 * fingerprinting, persistence, semantic confirmation validation, evidence, ranking, networking, or
 * current-price authority. Downstream submission remains responsible for all semantic validation
 * and durable execution behavior.
 */
internal class UserObservedPriceConfirmationActionCoordinator(
    private val routeCoordinator: UserObservedPriceSavedConfirmationDraftRouteCoordinator,
    private val proofContentCoordinator:
        UserObservedPriceConfirmationDraftProofContentSelectionCoordinator,
    private val target: UserObservedPriceConfirmationDraftSubmissionTarget,
    private val metadataSource: UserObservedPriceConfirmationLifecycleMetadataSource =
        LocalUserObservedPriceConfirmationLifecycleMetadataSource
) {

    fun submit(): Boolean {
        val finalization = routeCoordinator.currentFinalizationOrNull() ?: return false
        if (finalization.missingFields != CONFIRMATION_ONLY_MISSING_FIELDS) return false

        val artifactBytes =
            proofContentCoordinator.selectedContentSnapshotOrNull()
                ?: return false

        val metadata = metadataSource.capture()
        val submission =
            routeCoordinator.currentSubmissionWithConfirmationOrNull(metadata)
                ?: return false

        return target.submit(
            submission = submission,
            artifactBytes = artifactBytes
        )
    }

    private companion object {
        val CONFIRMATION_ONLY_MISSING_FIELDS =
            setOf(
                UserObservedPriceConfirmationDraftMissingField.CONFIRMATION_ID,
                UserObservedPriceConfirmationDraftMissingField.CONFIRMED_AT
            )
    }
}
