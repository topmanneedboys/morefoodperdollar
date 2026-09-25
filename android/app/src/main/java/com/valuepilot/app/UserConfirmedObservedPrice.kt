package com.valuepilot.app

import com.valuepilot.core.GtinValidation
import com.valuepilot.core.Money
import com.valuepilot.core.PracticalShoppingStoreIdentityScope
import com.valuepilot.core.ProductionProductEvidenceKey
import com.valuepilot.core.ProductionProductEvidenceKeyResolver
import com.valuepilot.core.SourceProductIdentity
import java.security.MessageDigest

/** Visible local artifact kind the user can explicitly confirm as supporting an observed price. */
enum class UserProvidedPriceProofType {
    RECEIPT,
    PRICE_TAG
}

enum class UserProvidedPriceArtifactFailure {
    INVALID_ARTIFACT_ID,
    EMPTY_ARTIFACT,
    ARTIFACT_TOO_LARGE
}

/**
 * Cryptographic fingerprint of actual local artifact bytes presented to this boundary.
 *
 * This is deliberately not evidence authority. The raw bytes are hashed immediately and are not
 * retained by this value, so a later durable user-controlled artifact-store slice must establish
 * that the proof remains retrievable before any caller may mint proof-backed evidence.
 *
 * The private constructor prevents callers from inventing a digest/length tuple. The only public
 * creation path hashes the supplied bytes itself under an explicit memory/work bound.
 */
class UserProvidedPriceProofArtifact private constructor(
    val artifactId: String,
    val proofType: UserProvidedPriceProofType,
    val sha256: String,
    val byteLength: Int
) {
    companion object {
        const val MAX_ARTIFACT_BYTES: Int = 16 * 1024 * 1024

        fun fingerprint(
            artifactId: String,
            proofType: UserProvidedPriceProofType,
            artifactBytes: ByteArray
        ): UserProvidedPriceArtifactResult {
            val failures = linkedSetOf<UserProvidedPriceArtifactFailure>()
            val safeArtifactId = artifactId.trim()

            if (!OPAQUE_ID.matches(safeArtifactId)) {
                failures += UserProvidedPriceArtifactFailure.INVALID_ARTIFACT_ID
            }
            if (artifactBytes.isEmpty()) {
                failures += UserProvidedPriceArtifactFailure.EMPTY_ARTIFACT
            }
            if (artifactBytes.size > MAX_ARTIFACT_BYTES) {
                failures += UserProvidedPriceArtifactFailure.ARTIFACT_TOO_LARGE
            }

            if (failures.isNotEmpty()) {
                return UserProvidedPriceArtifactResult(
                    artifact = null,
                    failures = failures
                )
            }

            val digest =
                MessageDigest
                    .getInstance("SHA-256")
                    .digest(artifactBytes)
                    .joinToString(separator = "") { byte -> "%02x".format(byte) }

            return UserProvidedPriceArtifactResult(
                artifact =
                    UserProvidedPriceProofArtifact(
                        artifactId = safeArtifactId,
                        proofType = proofType,
                        sha256 = digest,
                        byteLength = artifactBytes.size
                    ),
                failures = emptySet()
            )
        }

        private val OPAQUE_ID = Regex("[A-Za-z0-9._:-]{1,160}")
    }
}

data class UserProvidedPriceArtifactResult(
    val artifact: UserProvidedPriceProofArtifact?,
    val failures: Set<UserProvidedPriceArtifactFailure>
) {
    init {
        require((artifact != null) == failures.isEmpty())
    }

    val accepted: Boolean
        get() = artifact != null
}

/** Exact fields the user is being asked to confirm while the proof artifact is visible. */
data class UserObservedPriceConfirmationInput(
    val artifact: UserProvidedPriceProofArtifact,
    val observationId: String,
    val rawGtin: String,
    val productName: String,
    val price: Money,
    val storeScope: PracticalShoppingStoreIdentityScope,
    val observedAtEpochMillis: Long,
    val confirmationId: String,
    val confirmedAtEpochMillis: Long
)

enum class UserObservedPriceConfirmationFailure {
    INVALID_OBSERVATION_ID,
    INVALID_GTIN,
    INVALID_PRODUCT_NAME,
    NON_POSITIVE_PRICE,
    INVALID_OBSERVATION_TIME,
    INVALID_CONFIRMATION_ID,
    INVALID_CONFIRMATION_TIME,
    CONFIRMATION_PRECEDES_OBSERVATION,
    PRODUCT_KEY_UNAVAILABLE
}

/**
 * Immutable exact user confirmation bound to the fingerprinted artifact and exact shopping scope.
 *
 * This class intentionally has a private constructor and is not a data class, so callers cannot
 * use generated copy semantics to detach a confirmation from the artifact, GTIN, price, store, or
 * timestamps that were confirmed together.
 *
 * It is still not a shopping-evidence object and carries no factual authority. A later local
 * retention boundary must prove that the referenced artifact is durably user-controlled and
 * retrievable before this confirmation can be promoted to proof-backed observed-price evidence.
 */
class UserConfirmedObservedPrice private constructor(
    val artifact: UserProvidedPriceProofArtifact,
    val observationId: String,
    val gtin: String,
    val productName: String,
    val price: Money,
    val storeScope: PracticalShoppingStoreIdentityScope,
    val observedAtEpochMillis: Long,
    val confirmationId: String,
    val confirmedAtEpochMillis: Long,
    val productKey: ProductionProductEvidenceKey
) {
    companion object {
        fun confirm(
            input: UserObservedPriceConfirmationInput
        ): UserObservedPriceConfirmationResult {
            val failures = linkedSetOf<UserObservedPriceConfirmationFailure>()

            val observationId = input.observationId.trim()
            if (!OPAQUE_ID.matches(observationId)) {
                failures += UserObservedPriceConfirmationFailure.INVALID_OBSERVATION_ID
            }

            val gtin = input.rawGtin.trim()
            if (!GtinValidation.isValid(gtin)) {
                failures += UserObservedPriceConfirmationFailure.INVALID_GTIN
            }

            val productName = singleLine(input.productName, 160)
            if (productName == null || productName.length < 2) {
                failures += UserObservedPriceConfirmationFailure.INVALID_PRODUCT_NAME
            }

            if (input.price.minorUnits <= 0L) {
                failures += UserObservedPriceConfirmationFailure.NON_POSITIVE_PRICE
            }

            if (input.observedAtEpochMillis <= 0L) {
                failures += UserObservedPriceConfirmationFailure.INVALID_OBSERVATION_TIME
            }

            val confirmationId = input.confirmationId.trim()
            if (!OPAQUE_ID.matches(confirmationId)) {
                failures += UserObservedPriceConfirmationFailure.INVALID_CONFIRMATION_ID
            }

            if (input.confirmedAtEpochMillis <= 0L) {
                failures += UserObservedPriceConfirmationFailure.INVALID_CONFIRMATION_TIME
            } else if (
                input.observedAtEpochMillis > 0L &&
                input.confirmedAtEpochMillis < input.observedAtEpochMillis
            ) {
                failures += UserObservedPriceConfirmationFailure.CONFIRMATION_PRECEDES_OBSERVATION
            }

            val productKey =
                if (UserObservedPriceConfirmationFailure.INVALID_GTIN !in failures) {
                    ProductionProductEvidenceKeyResolver.resolve(
                        providerId = LOCAL_USER_PROVIDER_ID,
                        identity = SourceProductIdentity(gtin = gtin)
                    )
                } else {
                    null
                }
            if (
                UserObservedPriceConfirmationFailure.INVALID_GTIN !in failures &&
                productKey == null
            ) {
                failures += UserObservedPriceConfirmationFailure.PRODUCT_KEY_UNAVAILABLE
            }

            if (failures.isNotEmpty()) {
                return UserObservedPriceConfirmationResult(
                    confirmation = null,
                    failures = failures
                )
            }

            return UserObservedPriceConfirmationResult(
                confirmation =
                    UserConfirmedObservedPrice(
                        artifact = input.artifact,
                        observationId = observationId,
                        gtin = gtin,
                        productName = requireNotNull(productName),
                        price = input.price,
                        storeScope = input.storeScope,
                        observedAtEpochMillis = input.observedAtEpochMillis,
                        confirmationId = confirmationId,
                        confirmedAtEpochMillis = input.confirmedAtEpochMillis,
                        productKey = requireNotNull(productKey)
                    ),
                failures = emptySet()
            )
        }

        private fun singleLine(value: String, maxLength: Int): String? {
            val trimmed = value.trim()
            return trimmed.takeIf {
                it.isNotBlank() &&
                    it.length <= maxLength &&
                    it.none { character -> character == '\n' || character == '\r' }
            }
        }

        private val OPAQUE_ID = Regex("[A-Za-z0-9._:-]{1,160}")
        private val LOCAL_USER_PROVIDER_ID = com.valuepilot.core.EvidenceProviderId("local-user-price-proof")
    }
}

data class UserObservedPriceConfirmationResult(
    val confirmation: UserConfirmedObservedPrice?,
    val failures: Set<UserObservedPriceConfirmationFailure>
) {
    init {
        require((confirmation != null) == failures.isEmpty())
    }

    val accepted: Boolean
        get() = confirmation != null
}
