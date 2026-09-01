package com.valuepilot.app

import com.valuepilot.core.Money
import com.valuepilot.core.PracticalShoppingStoreIdentityScope

/** Receives immutable completeness state for one visible observed-price confirmation draft. */
internal fun interface UserObservedPriceConfirmationDraftObserver {
    fun onDraft(finalization: UserObservedPriceConfirmationDraftFinalization)
}

/**
 * Route-local owner for one explicit observed-price confirmation draft.
 *
 * This session owns only temporary non-byte draft state, route visibility, typed caller edits and
 * completeness re-presentation. [UserObservedPriceConfirmationDraft] and its finalizer remain the
 * sole draft-shaping/completeness boundary; semantic confirmation validation remains downstream in
 * [UserConfirmedObservedPrice].
 *
 * Hidden or closed routes ignore edits. Hiding preserves the temporary draft for the same open
 * route session; closing clears it permanently. A complete submission may be read only while the
 * route is visible, and reading it does not authorize proof retention or any factual promotion.
 *
 * Raw proof bytes never enter this object. The session does not submit work, construct Android
 * runtime owners, fingerprint or persist proof, read a clock, generate identifiers, validate GTIN
 * or price semantics, create evidence, resolve quantity, rank offers, or render Android Views.
 */
internal class UserObservedPriceConfirmationDraftRouteSession(
    private val observer: UserObservedPriceConfirmationDraftObserver =
        UserObservedPriceConfirmationDraftObserver { }
) : AutoCloseable {

    private var draft: UserObservedPriceConfirmationDraft? =
        UserObservedPriceConfirmationDraft.start()
    private var routeVisible = false
    private var closed = false

    fun onRouteVisibilityChanged(visible: Boolean) {
        if (closed || visible == routeVisible) return

        routeVisible = visible
        if (visible) {
            publishCurrent()
        }
    }

    fun onArtifactReferenceChanged(
        artifactId: String,
        proofType: UserProvidedPriceProofType
    ) {
        update { current ->
            current.withArtifactReference(
                artifactId = artifactId,
                proofType = proofType
            )
        }
    }

    fun onObservationReferenceChanged(observationId: String) {
        update { current -> current.withObservationReference(observationId) }
    }

    fun onIdentityPrefill(
        prefill: UserObservedPriceConfirmationDraftIdentityPrefill
    ) {
        update { current -> current.withIdentityPrefill(prefill) }
    }

    fun onProductChanged(
        observationId: String,
        rawGtin: String,
        productName: String
    ) {
        update { current ->
            current.withProduct(
                observationId = observationId,
                rawGtin = rawGtin,
                productName = productName
            )
        }
    }

    fun onPriceChanged(price: Money) {
        update { current -> current.withPrice(price) }
    }

    fun onStoreScopeChanged(storeScope: PracticalShoppingStoreIdentityScope) {
        update { current -> current.withStoreScope(storeScope) }
    }

    fun onObservedAtChanged(observedAtEpochMillis: Long) {
        update { current -> current.withObservedAtEpochMillis(observedAtEpochMillis) }
    }

    fun onConfirmationChanged(
        confirmationId: String,
        confirmedAtEpochMillis: Long
    ) {
        update { current ->
            current.withConfirmation(
                confirmationId = confirmationId,
                confirmedAtEpochMillis = confirmedAtEpochMillis
            )
        }
    }

    fun currentFinalizationOrNull(): UserObservedPriceConfirmationDraftFinalization? {
        if (closed || !routeVisible) return null
        val current = draft ?: return null
        return UserObservedPriceConfirmationDraftFinalizer.finalize(current)
    }

    fun currentSubmissionOrNull(): UserObservedPriceConfirmationDraftSubmission? =
        currentFinalizationOrNull()?.submission

    fun isVisible(): Boolean = !closed && routeVisible

    fun isClosed(): Boolean = closed

    override fun close() {
        if (closed) return

        closed = true
        routeVisible = false
        draft = null
    }

    private inline fun update(
        transform: (UserObservedPriceConfirmationDraft) -> UserObservedPriceConfirmationDraft
    ) {
        if (closed || !routeVisible) return

        val current = draft ?: return
        draft = transform(current)
        publishCurrent()
    }

    private fun publishCurrent() {
        val current = draft ?: return
        observer.onDraft(UserObservedPriceConfirmationDraftFinalizer.finalize(current))
    }
}
