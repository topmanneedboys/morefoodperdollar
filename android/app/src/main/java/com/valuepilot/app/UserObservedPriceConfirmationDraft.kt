package com.valuepilot.app

import com.valuepilot.core.Money
import com.valuepilot.core.PracticalShoppingStoreIdentityScope

enum class UserObservedPriceConfirmationDraftMissingField {
    ARTIFACT_ID,
    PROOF_TYPE,
    OBSERVATION_ID,
    GTIN,
    PRODUCT_NAME,
    PRICE,
    STORE_SCOPE,
    OBSERVED_AT,
    CONFIRMATION_ID,
    CONFIRMED_AT
}

/**
 * Complete non-byte input package for one observed-price confirmation submission.
 *
 * This value deliberately excludes proof bytes. It also carries no statement that the supplied
 * fields are semantically valid; [UserObservedPriceConfirmationTransaction] remains the sole
 * confirmation-validation and retention boundary.
 */
internal data class UserObservedPriceConfirmationDraftSubmission(
    val artifactId: String,
    val proofType: UserProvidedPriceProofType,
    val fields: UserObservedPriceConfirmationFields
)

internal data class UserObservedPriceConfirmationDraftFinalization(
    val submission: UserObservedPriceConfirmationDraftSubmission?,
    val missingFields: Set<UserObservedPriceConfirmationDraftMissingField>
) {
    init {
        require((submission != null) == missingFields.isEmpty())
    }

    val complete: Boolean
        get() = submission != null
}

/**
 * Immutable route-local draft for the non-byte inputs of one explicit observed-price confirmation.
 *
 * Null means unanswered. Non-null values are retained exactly as supplied, including values that
 * may later be rejected as semantically invalid. This draft intentionally performs no trimming,
 * GTIN validation, price validation, timestamp ordering, product-key resolution, proof hashing,
 * storage, evidence construction, freshness/ranking evaluation, or ID/time generation.
 *
 * Raw proof bytes never enter this object. A future foreground owner may keep this small draft while
 * the proof itself remains transient and pass both only at the explicit submit boundary.
 */
internal class UserObservedPriceConfirmationDraft private constructor(
    val artifactId: String?,
    val proofType: UserProvidedPriceProofType?,
    val observationId: String?,
    val rawGtin: String?,
    val productName: String?,
    val price: Money?,
    val storeScope: PracticalShoppingStoreIdentityScope?,
    val observedAtEpochMillis: Long?,
    val confirmationId: String?,
    val confirmedAtEpochMillis: Long?
) {

    fun withArtifactReference(
        artifactId: String,
        proofType: UserProvidedPriceProofType
    ): UserObservedPriceConfirmationDraft =
        copy(
            artifactId = artifactId,
            proofType = proofType
        )

    fun withObservationReference(
        observationId: String
    ): UserObservedPriceConfirmationDraft =
        copy(observationId = observationId)

    fun withProductIdentity(
        rawGtin: String,
        productName: String
    ): UserObservedPriceConfirmationDraft =
        copy(
            rawGtin = rawGtin,
            productName = productName
        )

    /**
     * Initializes the unanswered product/store identity bundle without replacing existing edits.
     *
     * Prefill is deliberately all-or-nothing: once any of GTIN, product name, or store scope has
     * been answered, this operation leaves the draft unchanged. Observation identity remains a
     * separate caller-owned field and is never invented here.
     */
    fun withIdentityPrefill(
        prefill: UserObservedPriceConfirmationDraftIdentityPrefill
    ): UserObservedPriceConfirmationDraft {
        if (rawGtin != null || productName != null || storeScope != null) return this

        return copy(
            rawGtin = prefill.rawGtin,
            productName = prefill.productName,
            storeScope = prefill.storeScope
        )
    }

    fun withProduct(
        observationId: String,
        rawGtin: String,
        productName: String
    ): UserObservedPriceConfirmationDraft =
        copy(
            observationId = observationId,
            rawGtin = rawGtin,
            productName = productName
        )

    fun withPrice(price: Money): UserObservedPriceConfirmationDraft =
        copy(price = price)

    fun withStoreScope(
        storeScope: PracticalShoppingStoreIdentityScope
    ): UserObservedPriceConfirmationDraft =
        copy(storeScope = storeScope)

    fun withObservedAtEpochMillis(
        observedAtEpochMillis: Long
    ): UserObservedPriceConfirmationDraft =
        copy(observedAtEpochMillis = observedAtEpochMillis)

    fun withConfirmation(
        confirmationId: String,
        confirmedAtEpochMillis: Long
    ): UserObservedPriceConfirmationDraft =
        copy(
            confirmationId = confirmationId,
            confirmedAtEpochMillis = confirmedAtEpochMillis
        )

    private fun copy(
        artifactId: String? = this.artifactId,
        proofType: UserProvidedPriceProofType? = this.proofType,
        observationId: String? = this.observationId,
        rawGtin: String? = this.rawGtin,
        productName: String? = this.productName,
        price: Money? = this.price,
        storeScope: PracticalShoppingStoreIdentityScope? = this.storeScope,
        observedAtEpochMillis: Long? = this.observedAtEpochMillis,
        confirmationId: String? = this.confirmationId,
        confirmedAtEpochMillis: Long? = this.confirmedAtEpochMillis
    ): UserObservedPriceConfirmationDraft =
        UserObservedPriceConfirmationDraft(
            artifactId = artifactId,
            proofType = proofType,
            observationId = observationId,
            rawGtin = rawGtin,
            productName = productName,
            price = price,
            storeScope = storeScope,
            observedAtEpochMillis = observedAtEpochMillis,
            confirmationId = confirmationId,
            confirmedAtEpochMillis = confirmedAtEpochMillis
        )

    companion object {
        fun start(): UserObservedPriceConfirmationDraft =
            UserObservedPriceConfirmationDraft(
                artifactId = null,
                proofType = null,
                observationId = null,
                rawGtin = null,
                productName = null,
                price = null,
                storeScope = null,
                observedAtEpochMillis = null,
                confirmationId = null,
                confirmedAtEpochMillis = null
            )
    }
}

/** Completeness check only; semantic validation remains downstream and unchanged. */
internal object UserObservedPriceConfirmationDraftFinalizer {

    fun finalize(
        draft: UserObservedPriceConfirmationDraft
    ): UserObservedPriceConfirmationDraftFinalization {
        val missing = linkedSetOf<UserObservedPriceConfirmationDraftMissingField>()

        if (draft.artifactId == null) missing += UserObservedPriceConfirmationDraftMissingField.ARTIFACT_ID
        if (draft.proofType == null) missing += UserObservedPriceConfirmationDraftMissingField.PROOF_TYPE
        if (draft.observationId == null) missing += UserObservedPriceConfirmationDraftMissingField.OBSERVATION_ID
        if (draft.rawGtin == null) missing += UserObservedPriceConfirmationDraftMissingField.GTIN
        if (draft.productName == null) missing += UserObservedPriceConfirmationDraftMissingField.PRODUCT_NAME
        if (draft.price == null) missing += UserObservedPriceConfirmationDraftMissingField.PRICE
        if (draft.storeScope == null) missing += UserObservedPriceConfirmationDraftMissingField.STORE_SCOPE
        if (draft.observedAtEpochMillis == null) missing += UserObservedPriceConfirmationDraftMissingField.OBSERVED_AT
        if (draft.confirmationId == null) missing += UserObservedPriceConfirmationDraftMissingField.CONFIRMATION_ID
        if (draft.confirmedAtEpochMillis == null) missing += UserObservedPriceConfirmationDraftMissingField.CONFIRMED_AT

        if (missing.isNotEmpty()) {
            return UserObservedPriceConfirmationDraftFinalization(
                submission = null,
                missingFields = missing
            )
        }

        return UserObservedPriceConfirmationDraftFinalization(
            submission =
                UserObservedPriceConfirmationDraftSubmission(
                    artifactId = requireNotNull(draft.artifactId),
                    proofType = requireNotNull(draft.proofType),
                    fields =
                        UserObservedPriceConfirmationFields(
                            observationId = requireNotNull(draft.observationId),
                            rawGtin = requireNotNull(draft.rawGtin),
                            productName = requireNotNull(draft.productName),
                            price = requireNotNull(draft.price),
                            storeScope = requireNotNull(draft.storeScope),
                            observedAtEpochMillis = requireNotNull(draft.observedAtEpochMillis),
                            confirmationId = requireNotNull(draft.confirmationId),
                            confirmedAtEpochMillis = requireNotNull(draft.confirmedAtEpochMillis)
                        )
                ),
            missingFields = emptySet()
        )
    }
}
