package com.valuepilot.core

/**
 * Structural rule declared by a provider adapter after the provider's price
 * semantics have been separately reviewed and the PRICE_SEMANTICS_VALIDATED
 * production gate has been satisfied.
 *
 * The shared core never derives these roles from field names.
 */
enum class ProductionPriceRelationshipRule {
    NONE,
    CURRENT_MUST_NOT_EXCEED_REFERENCE
}

data class ProductionPriceFieldRoles(
    val currentPriceFieldName: String,
    val referencePriceFieldName: String? = null,
    val relationshipRule: ProductionPriceRelationshipRule =
        ProductionPriceRelationshipRule.NONE
) {
    init {
        require(currentPriceFieldName.isNotBlank())
        require(currentPriceFieldName.length <= 96)
        referencePriceFieldName?.let {
            require(it.isNotBlank())
            require(it.length <= 96)
            require(!it.equals(currentPriceFieldName, ignoreCase = true)) {
                "Current and reference price fields must be distinct"
            }
        }
        if (
            relationshipRule ==
            ProductionPriceRelationshipRule.CURRENT_MUST_NOT_EXCEED_REFERENCE
        ) {
            require(referencePriceFieldName != null) {
                "A reference price field is required for an ordering rule"
            }
        }
    }
}

/**
 * A production-capable provider row after authorization, geography, price and
 * per-offer freshness gates have passed.
 *
 * This is intentionally NOT [Offer] and exposes no rankable flag. It is a
 * staging boundary only. Quantity, unit value, promotion arithmetic and Best
 * Value participation remain downstream evidence-gated decisions.
 */
data class StagedProductionOfferCandidate(
    val provider: EvidenceProvider,
    val source: ShoppingSource,
    val dataset: EvidenceDatasetNamespace,
    val sourceProductIdentity: SourceProductIdentity,
    val productName: String,
    val currentPrice: Money,
    val currentPriceSourceFieldName: String,
    val referencePrice: Money? = null,
    val referencePriceSourceFieldName: String? = null,
    val priceObservedAtEpochMillis: Long,
    val freshness: EvidenceFreshness,
    val offerCountryCode: String,
    val activationProfileId: String,
    val availability: AvailabilityEvidence,
    val productUrl: String? = null,
    val imageUrl: String? = null
) {
    init {
        require(currentPrice.minorUnits > 0L)
        require(priceObservedAtEpochMillis > 0L)
        require(offerCountryCode.matches(Regex("[A-Z]{2}")))
        require(activationProfileId.isNotBlank())
        require(
            freshness == EvidenceFreshness.FRESH ||
                freshness == EvidenceFreshness.AGING
        ) {
            "A staged production candidate requires fresh or aging per-offer evidence"
        }
        referencePrice?.let {
            require(it.minorUnits > 0L)
            require(it.currencyCode == currentPrice.currencyCode)
            require(it.fractionDigits == currentPrice.fractionDigits)
        }
        require(
            (referencePrice == null) ==
                (referencePriceSourceFieldName == null)
        ) {
            "Reference price and source field must be present together"
        }
    }
}

enum class ProductionOfferCandidateBlocker {
    INSUFFICIENT_ACTIVATION_PROFILE,
    AUTHORIZATION_SCOPE_MISMATCH,
    GEOGRAPHY_SCOPE_MISMATCH,
    PRODUCTION_AUTHORIZATION_BLOCKED,
    OFFER_GEOGRAPHY_UNRESOLVED,
    OFFER_GEOGRAPHY_MISMATCH,
    NOT_REAL_WORLD,
    UNKNOWN_ACQUISITION_CHANNEL,
    WEAK_SOURCE_CLAIM,
    NO_VALIDATED_SOURCE_IDENTITY,
    CURRENT_PRICE_UNAVAILABLE,
    REFERENCE_PRICE_UNAVAILABLE,
    PRICE_MONEY_INCOMPARABLE,
    PRICE_SEMANTIC_CONFLICT,
    OFFER_TIMESTAMP_MISSING,
    OFFER_FRESHNESS_UNKNOWN,
    OFFER_FUTURE_DATED,
    OFFER_STALE
}

data class ProductionOfferCandidateResult(
    val candidate: StagedProductionOfferCandidate?,
    val blockers: Set<ProductionOfferCandidateBlocker>,
    val authorizationDecision: ProductionActivationDecision?,
    val countryAssessment: ProviderDatasetCountryAssessment?,
    val freshness: EvidenceFreshness?
) {
    init {
        require((candidate != null) == blockers.isEmpty()) {
            "Candidate exists if and only if there are no blockers"
        }
    }

    val accepted: Boolean
        get() = candidate != null
}

/**
 * Provider-neutral bridge from a raw staged provider row into a production
 * offer candidate.
 *
 * Important boundaries:
 * - no I/O and no hidden clock;
 * - no provider-specific field names;
 * - no current price inferred from a discounted/reference relationship;
 * - no dataset timestamp substituted for per-offer freshness;
 * - no currency or advertiser context substituted for country scope;
 * - no authorization decision reused across a different provider/dataset;
 * - no [Offer], ranking, promotion, quantity or unit-value object is created.
 */
object ProductionOfferCandidateEvaluator {

    fun evaluate(
        record: ProviderOfferImportRecord,
        priceRoles: ProductionPriceFieldRoles,
        authorizationAssessment: ProviderProductionAuthorizationAssessment,
        activationProfile: ProductionActivationProfile,
        geography: ProviderDatasetOfferGeography,
        targetCountryCode: String,
        evaluatedAtEpochMillis: Long,
        offerFreshnessPolicy: EvidenceFreshnessPolicy
    ): ProductionOfferCandidateResult {
        require(evaluatedAtEpochMillis > 0L)
        require(targetCountryCode.matches(Regex("[A-Z]{2}")))

        val blockers = linkedSetOf<ProductionOfferCandidateBlocker>()

        val baseProfile = ProductionActivationProfiles.CONSUMER_MOBILE_CATALOG
        if (!activationProfile.requiredGates.containsAll(baseProfile.requiredGates)) {
            blockers += ProductionOfferCandidateBlocker.INSUFFICIENT_ACTIVATION_PROFILE
        }

        val authorizationScopeMatches =
            authorizationAssessment.providerId == record.provider.id &&
                authorizationAssessment.datasetNamespaceId == record.dataset.id
        if (!authorizationScopeMatches) {
            blockers += ProductionOfferCandidateBlocker.AUTHORIZATION_SCOPE_MISMATCH
        }

        val geographyScopeMatches =
            geography.providerId == record.provider.id &&
                geography.datasetNamespaceId == record.dataset.id
        if (!geographyScopeMatches) {
            blockers += ProductionOfferCandidateBlocker.GEOGRAPHY_SCOPE_MISMATCH
        }

        if (record.environment != EvidenceEnvironment.REAL_WORLD) {
            blockers += ProductionOfferCandidateBlocker.NOT_REAL_WORLD
        }
        if (
            record.channel == EvidenceChannel.UNKNOWN ||
            record.channel == EvidenceChannel.FIXTURE
        ) {
            blockers += ProductionOfferCandidateBlocker.UNKNOWN_ACQUISITION_CHANNEL
        }
        if (
            record.claimKind == EvidenceClaimKind.UNKNOWN ||
            record.claimKind == EvidenceClaimKind.INFERRED
        ) {
            blockers += ProductionOfferCandidateBlocker.WEAK_SOURCE_CLAIM
        }

        val sourceIdentity = record.identity.validatedSourceProductIdentity()
        if (sourceIdentity == null) {
            blockers += ProductionOfferCandidateBlocker.NO_VALIDATED_SOURCE_IDENTITY
        }

        val countryAssessment =
            if (geographyScopeMatches) {
                ProviderDatasetOfferGeographyEvaluator.evaluate(
                    geography = geography,
                    targetCountryCode = targetCountryCode
                )
            } else {
                null
            }

        when (countryAssessment?.status) {
            ProviderDatasetCountryMatchStatus.UNRESOLVED ->
                blockers += ProductionOfferCandidateBlocker.OFFER_GEOGRAPHY_UNRESOLVED
            ProviderDatasetCountryMatchStatus.MISMATCH ->
                blockers += ProductionOfferCandidateBlocker.OFFER_GEOGRAPHY_MISMATCH
            ProviderDatasetCountryMatchStatus.MATCH,
            null -> Unit
        }

        val effectiveAuthorization =
            if (authorizationScopeMatches && countryAssessment != null) {
                authorizationAssessment.copy(
                    gates =
                        authorizationAssessment.gates
                            .filterNot {
                                it.gate ==
                                    ProductionAuthorizationGate.OFFER_GEOGRAPHY_VALIDATED
                            } +
                            countryAssessment.toProductionGateAssessment()
                )
            } else {
                null
            }

        val authorizationDecision =
            effectiveAuthorization?.let {
                ProductionAuthorizationEvaluator.evaluate(
                    assessment = it,
                    profile = activationProfile
                )
            }

        if (authorizationDecision?.authorized != true) {
            blockers += ProductionOfferCandidateBlocker.PRODUCTION_AUTHORIZATION_BLOCKED
        }

        val currentPrice =
            record.priceField(priceRoles.currentPriceFieldName)?.parsedAmount
        if (currentPrice == null || currentPrice.minorUnits <= 0L) {
            blockers += ProductionOfferCandidateBlocker.CURRENT_PRICE_UNAVAILABLE
        }

        val referencePrice =
            priceRoles.referencePriceFieldName?.let { fieldName ->
                record.priceField(fieldName)?.parsedAmount
            }
        if (
            priceRoles.referencePriceFieldName != null &&
            (referencePrice == null || referencePrice.minorUnits <= 0L)
        ) {
            blockers += ProductionOfferCandidateBlocker.REFERENCE_PRICE_UNAVAILABLE
        }

        if (
            currentPrice != null &&
            currentPrice.minorUnits > 0L &&
            referencePrice != null &&
            referencePrice.minorUnits > 0L &&
            (
                currentPrice.currencyCode != referencePrice.currencyCode ||
                    currentPrice.fractionDigits != referencePrice.fractionDigits
            )
        ) {
            blockers += ProductionOfferCandidateBlocker.PRICE_MONEY_INCOMPARABLE
        }

        if (
            priceRoles.relationshipRule ==
            ProductionPriceRelationshipRule.CURRENT_MUST_NOT_EXCEED_REFERENCE &&
            currentPrice != null &&
            currentPrice.minorUnits > 0L &&
            referencePrice != null &&
            referencePrice.minorUnits > 0L &&
            currentPrice.currencyCode == referencePrice.currencyCode &&
            currentPrice.fractionDigits == referencePrice.fractionDigits &&
            currentPrice.minorUnits > referencePrice.minorUnits
        ) {
            blockers += ProductionOfferCandidateBlocker.PRICE_SEMANTIC_CONFLICT
        }

        val observedAt = record.priceObservedAtEpochMillis
        val freshness =
            if (observedAt == null) {
                blockers += ProductionOfferCandidateBlocker.OFFER_TIMESTAMP_MISSING
                null
            } else {
                EvidenceFreshnessEvaluator.classify(
                    observedAtEpochMillis = observedAt,
                    evaluatedAtEpochMillis = evaluatedAtEpochMillis,
                    policy = offerFreshnessPolicy
                )
            }

        when (freshness) {
            EvidenceFreshness.UNKNOWN ->
                blockers += ProductionOfferCandidateBlocker.OFFER_FRESHNESS_UNKNOWN
            EvidenceFreshness.FUTURE_DATED ->
                blockers += ProductionOfferCandidateBlocker.OFFER_FUTURE_DATED
            EvidenceFreshness.STALE ->
                blockers += ProductionOfferCandidateBlocker.OFFER_STALE
            EvidenceFreshness.FRESH,
            EvidenceFreshness.AGING,
            null -> Unit
        }

        if (blockers.isNotEmpty()) {
            return ProductionOfferCandidateResult(
                candidate = null,
                blockers = blockers,
                authorizationDecision = authorizationDecision,
                countryAssessment = countryAssessment,
                freshness = freshness
            )
        }

        return ProductionOfferCandidateResult(
            candidate =
                StagedProductionOfferCandidate(
                    provider = record.provider,
                    source = record.source,
                    dataset = record.dataset,
                    sourceProductIdentity = requireNotNull(sourceIdentity),
                    productName = record.productName,
                    currentPrice = requireNotNull(currentPrice),
                    currentPriceSourceFieldName = priceRoles.currentPriceFieldName,
                    referencePrice = referencePrice,
                    referencePriceSourceFieldName = priceRoles.referencePriceFieldName,
                    priceObservedAtEpochMillis = requireNotNull(observedAt),
                    freshness = requireNotNull(freshness),
                    offerCountryCode = targetCountryCode,
                    activationProfileId = activationProfile.id,
                    availability = record.availability,
                    productUrl = record.productUrl,
                    imageUrl = record.imageUrl
                ),
            blockers = emptySet(),
            authorizationDecision = authorizationDecision,
            countryAssessment = countryAssessment,
            freshness = freshness
        )
    }
}
