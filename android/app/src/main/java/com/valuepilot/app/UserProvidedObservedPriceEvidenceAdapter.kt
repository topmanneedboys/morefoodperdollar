package com.valuepilot.app

import com.valuepilot.core.AvailabilityEvidence
import com.valuepilot.core.EvidenceAuthorityClass
import com.valuepilot.core.EvidenceChannel
import com.valuepilot.core.EvidenceClaim
import com.valuepilot.core.EvidenceClaimDomain
import com.valuepilot.core.EvidenceClaimKind
import com.valuepilot.core.EvidenceClaimScope
import com.valuepilot.core.EvidenceDatasetNamespace
import com.valuepilot.core.EvidenceEnvironment
import com.valuepilot.core.EvidenceFingerprints
import com.valuepilot.core.EvidenceProvider
import com.valuepilot.core.EvidenceProviderId
import com.valuepilot.core.EvidenceStorageBoundary
import com.valuepilot.core.GtinValidation
import com.valuepilot.core.Money
import com.valuepilot.core.PracticalShoppingStoreIdentityScope
import com.valuepilot.core.ProductObservation
import com.valuepilot.core.ProductObservationId
import com.valuepilot.core.ProductionProductEvidenceKey
import com.valuepilot.core.ProductionProductEvidenceKeyResolver
import com.valuepilot.core.ShoppingEvidence
import com.valuepilot.core.ShoppingSource
import com.valuepilot.core.ShoppingSourceId
import com.valuepilot.core.SourceProductIdentity

/** Local proof kinds whose visible artifact can directly support an observed-price fact. */
enum class UserProvidedPriceProofType {
    RECEIPT,
    PRICE_TAG
}

/**
 * Immutable reference to a locally retained proof artifact and its explicit confirmation event.
 *
 * This type carries no file bytes and performs no I/O. [artifactSha256] is supplied by the local
 * capture/storage boundary so later persistence or deletion work can retain an auditable link to
 * the exact artifact without putting a file path, credential, or user text into factual authority.
 */
data class UserProvidedPriceProofReference(
    val proofId: String,
    val proofType: UserProvidedPriceProofType,
    val artifactSha256: String,
    val confirmationId: String
)

/**
 * Exact facts supplied after the user has confirmed them while the retained proof is visible.
 *
 * Product identity is intentionally GTIN-only at this cross-source boundary. Provider-scoped SKU
 * or item ids are not accepted because a local observation must not impersonate another provider.
 * [storeScope] must already be an exact merchant/location/channel scope established upstream; this
 * adapter performs no store discovery, name matching, geocoding, routing, or scope inference.
 *
 * [price] is already exact deterministic money. Parsing arbitrary UI text is deliberately outside
 * this authority boundary.
 */
data class UserProvidedObservedPriceInput(
    val observationId: String,
    val rawGtin: String,
    val productName: String,
    val price: Money,
    val storeScope: PracticalShoppingStoreIdentityScope,
    val observedAtEpochMillis: Long,
    val proof: UserProvidedPriceProofReference
)

enum class UserProvidedObservedPriceFailure {
    INVALID_OBSERVATION_ID,
    INVALID_GTIN,
    INVALID_PRODUCT_NAME,
    NON_POSITIVE_PRICE,
    INVALID_OBSERVATION_TIME,
    INVALID_PROOF_ID,
    INVALID_PROOF_DIGEST,
    INVALID_CONFIRMATION_ID,
    PRODUCT_KEY_UNAVAILABLE
}

/**
 * Provenance-preserving wrapper around a local proof-backed observed-price fact.
 *
 * The constructor is private so callers cannot manufacture this wrapper without the adapter's
 * fail-closed validation. This is OBSERVED_PRICE evidence only. It is not a merchant current-offer
 * claim, not availability evidence, not a production lifecycle token, and not rankability.
 */
class UserProvidedObservedPriceEvidence private constructor(
    val evidence: ShoppingEvidence,
    val priceClaim: EvidenceClaim,
    val dataset: EvidenceDatasetNamespace,
    val proof: UserProvidedPriceProofReference,
    val storeScope: PracticalShoppingStoreIdentityScope,
    val productKey: ProductionProductEvidenceKey
) {
    init {
        require(evidence.environment == EvidenceEnvironment.REAL_WORLD)
        require(evidence.channel == EvidenceChannel.USER_PROVIDED)
        require(evidence.observationClaimKind == EvidenceClaimKind.DIRECT_OBSERVATION)
        require(priceClaim.domain == EvidenceClaimDomain.OBSERVED_PRICE)
        require(priceClaim.authority == EvidenceAuthorityClass.PROOF_BACKED_DIRECT_OBSERVATION)
        require(priceClaim.scope.productKey == productKey.value)
        require(priceClaim.scope.merchantKey == storeScope.merchantKey)
        require(priceClaim.scope.locationKey == storeScope.locationKey)
        require(priceClaim.scope.commerceChannelKey == storeScope.commerceChannelKey)
        require(dataset.storageBoundary == EvidenceStorageBoundary.USER_CONTROLLED)
    }

    companion object {
        internal fun create(
            evidence: ShoppingEvidence,
            priceClaim: EvidenceClaim,
            dataset: EvidenceDatasetNamespace,
            proof: UserProvidedPriceProofReference,
            storeScope: PracticalShoppingStoreIdentityScope,
            productKey: ProductionProductEvidenceKey
        ): UserProvidedObservedPriceEvidence =
            UserProvidedObservedPriceEvidence(
                evidence = evidence,
                priceClaim = priceClaim,
                dataset = dataset,
                proof = proof,
                storeScope = storeScope,
                productKey = productKey
            )
    }
}

data class UserProvidedObservedPriceResult(
    val acceptedEvidence: UserProvidedObservedPriceEvidence?,
    val failures: Set<UserProvidedObservedPriceFailure>
) {
    init {
        require((acceptedEvidence != null) == failures.isEmpty())
    }

    val accepted: Boolean
        get() = acceptedEvidence != null
}

/**
 * Network-free adapter for locally retained receipt/price-tag evidence.
 *
 * Permanent boundaries:
 * - requires a checksum-valid GTIN; names/prices never create product identity;
 * - preserves an exact already-established store scope without deriving it;
 * - requires a local proof artifact digest and explicit proof-confirmation id;
 * - preserves the caller-supplied observation time and never reads a clock;
 * - records unknown availability and no promotion because proof of price is not proof of stock;
 * - creates OBSERVED_PRICE only and never upgrades the observation into a current merchant offer;
 * - performs no persistence, filesystem, OCR, camera, network, ranking, or economic work.
 */
object UserProvidedObservedPriceEvidenceAdapter {

    private val provider =
        EvidenceProvider(
            id = EvidenceProviderId("local-user-price-proof"),
            displayName = "User-provided price proof"
        )

    private val source =
        ShoppingSource(
            id = ShoppingSourceId("local-user-price-proof"),
            displayName = "User-provided price proof"
        )

    private val dataset =
        EvidenceDatasetNamespace(
            id = "user-provided-price-proof",
            displayName = "User-provided price proof",
            licenseId = "user-controlled",
            storageBoundary = EvidenceStorageBoundary.USER_CONTROLLED
        )

    fun adapt(input: UserProvidedObservedPriceInput): UserProvidedObservedPriceResult {
        val failures = linkedSetOf<UserProvidedObservedPriceFailure>()

        if (!OPAQUE_ID.matches(input.observationId)) {
            failures += UserProvidedObservedPriceFailure.INVALID_OBSERVATION_ID
        }

        val gtin = input.rawGtin.trim()
        if (!GtinValidation.isValid(gtin)) {
            failures += UserProvidedObservedPriceFailure.INVALID_GTIN
        }

        val productName = singleLine(input.productName, 160)
        if (productName == null || productName.length < 2) {
            failures += UserProvidedObservedPriceFailure.INVALID_PRODUCT_NAME
        }

        if (input.price.minorUnits <= 0L) {
            failures += UserProvidedObservedPriceFailure.NON_POSITIVE_PRICE
        }

        if (input.observedAtEpochMillis <= 0L) {
            failures += UserProvidedObservedPriceFailure.INVALID_OBSERVATION_TIME
        }

        if (!OPAQUE_ID.matches(input.proof.proofId)) {
            failures += UserProvidedObservedPriceFailure.INVALID_PROOF_ID
        }

        if (!SHA256.matches(input.proof.artifactSha256)) {
            failures += UserProvidedObservedPriceFailure.INVALID_PROOF_DIGEST
        }

        if (!OPAQUE_ID.matches(input.proof.confirmationId)) {
            failures += UserProvidedObservedPriceFailure.INVALID_CONFIRMATION_ID
        }

        val sourceIdentity =
            if (UserProvidedObservedPriceFailure.INVALID_GTIN !in failures) {
                SourceProductIdentity(gtin = gtin)
            } else {
                null
            }

        val productKey =
            sourceIdentity?.let {
                ProductionProductEvidenceKeyResolver.resolve(
                    providerId = provider.id,
                    identity = it
                )
            }
        if (sourceIdentity != null && productKey == null) {
            failures += UserProvidedObservedPriceFailure.PRODUCT_KEY_UNAVAILABLE
        }

        if (failures.isNotEmpty()) {
            return UserProvidedObservedPriceResult(
                acceptedEvidence = null,
                failures = failures
            )
        }

        val safeProductName = requireNotNull(productName)
        val safeIdentity = requireNotNull(sourceIdentity)
        val safeProductKey = requireNotNull(productKey)
        val normalizedProof =
            UserProvidedPriceProofReference(
                proofId = input.proof.proofId,
                proofType = input.proof.proofType,
                artifactSha256 = input.proof.artifactSha256,
                confirmationId = input.proof.confirmationId
            )

        val evidence =
            ShoppingEvidence(
                observation =
                    ProductObservation(
                        id = ProductObservationId(input.observationId),
                        sourceId = source.id.value,
                        rawText = normalizedObservationText(safeProductName, gtin, input.price),
                        observedAtEpochMillis = input.observedAtEpochMillis
                    ),
                provider = provider,
                source = source,
                environment = EvidenceEnvironment.REAL_WORLD,
                channel = EvidenceChannel.USER_PROVIDED,
                observationClaimKind = EvidenceClaimKind.DIRECT_OBSERVATION,
                sourceProductIdentity = safeIdentity,
                availability = AvailabilityEvidence()
            )

        val claim =
            EvidenceClaim(
                claimId = "user-proof:${input.observationId}:observed-price",
                domain = EvidenceClaimDomain.OBSERVED_PRICE,
                valueFingerprint = EvidenceFingerprints.money(input.price),
                authority = EvidenceAuthorityClass.PROOF_BACKED_DIRECT_OBSERVATION,
                scope =
                    EvidenceClaimScope(
                        productKey = safeProductKey.value,
                        merchantKey = input.storeScope.merchantKey,
                        locationKey = input.storeScope.locationKey,
                        commerceChannelKey = input.storeScope.commerceChannelKey,
                        currencyCode = input.price.currencyCode
                    ),
                observedAtEpochMillis = input.observedAtEpochMillis
            )

        return UserProvidedObservedPriceResult(
            acceptedEvidence =
                UserProvidedObservedPriceEvidence.create(
                    evidence = evidence,
                    priceClaim = claim,
                    dataset = dataset,
                    proof = normalizedProof,
                    storeScope = input.storeScope,
                    productKey = safeProductKey
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

    private fun normalizedObservationText(
        productName: String,
        gtin: String,
        price: Money
    ): String =
        buildString {
            append(productName)
            append('\n')
            append("gtin=")
            append(gtin)
            append('\n')
            append("priceMinorUnits=")
            append(price.minorUnits)
            append(';')
            append("fractionDigits=")
            append(price.fractionDigits)
            append(';')
            append("currency=")
            append(price.currencyCode)
        }

    private val OPAQUE_ID = Regex("[A-Za-z0-9._:-]{1,160}")
    private val SHA256 = Regex("[0-9a-f]{64}")
}
