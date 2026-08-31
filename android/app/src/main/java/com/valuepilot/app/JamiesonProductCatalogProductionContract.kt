package com.valuepilot.app

import com.valuepilot.core.EvidenceDatasetNamespace
import com.valuepilot.core.EvidenceProviderId
import com.valuepilot.core.EvidenceStorageBoundary
import com.valuepilot.core.ImportedOfferCountryBasis
import com.valuepilot.core.ProductionAuthorizationGate
import com.valuepilot.core.ProductionAuthorizationState
import com.valuepilot.core.ProductionDatasetDispositionState
import com.valuepilot.core.ProductionGateAssessment
import com.valuepilot.core.ProviderDatasetCountryAssessment
import com.valuepilot.core.ProviderDatasetOfferGeography
import com.valuepilot.core.ProviderDatasetOfferGeographyEvaluator
import com.valuepilot.core.ProviderProductionAuthorizationAssessment

private const val JAMIESON_TERMINATION_DELETION_MILLIS = 5_184_000_000L
private const val JAMIESON_PARTNER_TERMS_BASIS_ID =
    "jamieson-written-product-catalog-terms-2026-08-31"
private const val RAKUTEN_FEED_ACCESS_BASIS_ID =
    "rakuten-support-jamieson-feed-approval-2026-08-28"
private const val JAMIESON_LINK_USE_BASIS_ID =
    "rakuten-support-plus-jamieson-link-approval-2026-08-31"
private const val JAMIESON_GEOGRAPHY_BASIS_ID =
    "jamieson-documented-canadian-feed-market-2026-08-31"
private const val JAMIESON_RETENTION_BASIS_ID =
    "jamieson-60-day-post-termination-deletion-2026-08-31"

/**
 * Uses expressly covered by the advertiser's written Product Catalog confirmation.
 *
 * This provider-specific list is audit metadata. Production activation still runs through
 * the provider-neutral authorization evaluator and does not infer any factual price or
 * freshness semantics from these rights.
 */
enum class JamiesonProductCatalogApprovedUse {
    MOBILE_DISPLAY,
    SEARCH_AND_COMPARISON,
    CACHE,
    INDEX,
    PRODUCT_OR_AFFILIATE_LINKS
}

/**
 * Deterministic interpretation of the advertiser's post-termination deletion requirement.
 *
 * Production use stops at partnership termination. The 60-day period is only the maximum
 * physical-removal window for Jamieson Product Catalog data; it is never a grace period for
 * continued production display, search, ranking, or link use.
 */
data class JamiesonProductCatalogTerminationDecision(
    val productionUseAllowed: Boolean,
    val requiredNamespaceDisposition: ProductionDatasetDispositionState,
    val deletionDeadlineEpochMillis: Long?,
    val deletionOverdue: Boolean
) {
    init {
        require(
            requiredNamespaceDisposition == ProductionDatasetDispositionState.RETAINED ||
                requiredNamespaceDisposition ==
                    ProductionDatasetDispositionState.WITHDRAWAL_REQUIRED
        )
        if (productionUseAllowed) {
            require(requiredNamespaceDisposition == ProductionDatasetDispositionState.RETAINED)
            require(deletionDeadlineEpochMillis == null)
            require(!deletionOverdue)
        } else {
            require(
                requiredNamespaceDisposition ==
                    ProductionDatasetDispositionState.WITHDRAWAL_REQUIRED
            )
            require(deletionDeadlineEpochMillis != null && deletionDeadlineEpochMillis > 0L)
        }
        require(!deletionOverdue || !productionUseAllowed)
    }
}

/**
 * Jamieson Product Catalog partner/source contract at the ValuePilot application edge.
 *
 * Written advertiser confirmation dated 2026-08-31 establishes the allowed mobile uses,
 * Canadian consumer market, CAD-only feed expectation, and the requirement to remove feed
 * data within 60 days after partnership termination. Rakuten support separately confirmed
 * Product Catalog access for this advertiser.
 *
 * This contract deliberately does NOT establish:
 * - which Rakuten price field is the production current price;
 * - per-offer freshness or a dataset recency policy;
 * - package quantity authority;
 * - installed-software networking approval; or
 * - tracking/privacy readiness.
 *
 * It performs no I/O, reads no clock, contains no provider credentials, and grants no
 * ranking or current-price authority by itself.
 */
object JamiesonProductCatalogProductionContract {
    val providerId = EvidenceProviderId("rakuten-advertising")

    const val DATASET_NAMESPACE_ID = "rakuten.jamieson-product-catalog"
    const val EXPECTED_COUNTRY_CODE = "CA"
    const val EXPECTED_CURRENCY_CODE = "CAD"
    const val TERMINATION_DELETION_DAYS = 60L

    val datasetNamespace =
        EvidenceDatasetNamespace(
            id = DATASET_NAMESPACE_ID,
            displayName = "Jamieson Product Catalog via Rakuten",
            licenseId = "partner-written-terms-2026-08-31",
            storageBoundary = EvidenceStorageBoundary.PROPRIETARY_RESTRICTED
        )

    val approvedUses: Set<JamiesonProductCatalogApprovedUse> =
        setOf(
            JamiesonProductCatalogApprovedUse.MOBILE_DISPLAY,
            JamiesonProductCatalogApprovedUse.SEARCH_AND_COMPARISON,
            JamiesonProductCatalogApprovedUse.CACHE,
            JamiesonProductCatalogApprovedUse.INDEX,
            JamiesonProductCatalogApprovedUse.PRODUCT_OR_AFFILIATE_LINKS
        )

    /**
     * Explicit Canadian-market evidence supplied by the advertiser.
     * CAD is intentionally not used to infer geography.
     */
    fun documentedGeography(): ProviderDatasetOfferGeography =
        ProviderDatasetOfferGeography(
            providerId = providerId,
            datasetNamespaceId = DATASET_NAMESPACE_ID,
            countryCode = EXPECTED_COUNTRY_CODE,
            basis = ImportedOfferCountryBasis.DOCUMENTED_DATASET_MARKET,
            basisId = JAMIESON_GEOGRAPHY_BASIS_ID
        )

    fun geographyAssessment(
        targetCountryCode: String = EXPECTED_COUNTRY_CODE
    ): ProviderDatasetCountryAssessment =
        ProviderDatasetOfferGeographyEvaluator.evaluate(
            geography = documentedGeography(),
            targetCountryCode = targetCountryCode
        )

    /**
     * Exact currency assertion from the advertiser's written feed description.
     * Callers must pass a canonical ISO-style source currency; null and non-CAD fail closed.
     */
    fun matchesDeclaredFeedCurrency(currencyCode: String?): Boolean =
        currencyCode == EXPECTED_CURRENCY_CODE

    /**
     * Current authorization record supported by the two written parties.
     *
     * Rights/geography gates that are actually evidenced are SATISFIED. Factual production
     * gates not answered by either email remain explicitly UNKNOWN. Link use is rights-approved,
     * but installed-app networking and tracking/privacy are still PENDING, so the network-link
     * activation profile remains fail-closed.
     */
    fun partnerAuthorizationAssessment(): ProviderProductionAuthorizationAssessment =
        ProviderProductionAuthorizationAssessment(
            providerId = providerId,
            datasetNamespaceId = DATASET_NAMESPACE_ID,
            gates =
                listOf(
                    satisfied(
                        ProductionAuthorizationGate.DATA_ACCESS_AUTHORIZED,
                        RAKUTEN_FEED_ACCESS_BASIS_ID
                    ),
                    satisfied(
                        ProductionAuthorizationGate.CONSUMER_DISPLAY_AUTHORIZED,
                        JAMIESON_PARTNER_TERMS_BASIS_ID
                    ),
                    satisfied(
                        ProductionAuthorizationGate.CACHE_AUTHORIZED,
                        JAMIESON_PARTNER_TERMS_BASIS_ID
                    ),
                    satisfied(
                        ProductionAuthorizationGate.INDEX_AUTHORIZED,
                        JAMIESON_PARTNER_TERMS_BASIS_ID
                    ),
                    satisfied(
                        ProductionAuthorizationGate.MOBILE_APP_AUTHORIZED,
                        JAMIESON_PARTNER_TERMS_BASIS_ID
                    ),
                    satisfied(
                        ProductionAuthorizationGate.RETENTION_DELETION_POLICY_DEFINED,
                        JAMIESON_RETENTION_BASIS_ID
                    ),
                    geographyAssessment().toProductionGateAssessment(),
                    unknown(
                        ProductionAuthorizationGate.PRICE_SEMANTICS_VALIDATED,
                        "rakuten-sale-retail-current-price-semantics-unresolved"
                    ),
                    unknown(
                        ProductionAuthorizationGate.DATASET_RECENCY_POLICY_DEFINED,
                        "jamieson-dataset-recency-policy-unresolved"
                    ),
                    unknown(
                        ProductionAuthorizationGate.OFFER_FRESHNESS_POLICY_DEFINED,
                        "jamieson-per-offer-freshness-policy-unresolved"
                    ),
                    satisfied(
                        ProductionAuthorizationGate.AFFILIATE_LINK_USE_AUTHORIZED,
                        JAMIESON_LINK_USE_BASIS_ID
                    ),
                    pending(
                        ProductionAuthorizationGate.INSTALLED_SOFTWARE_NETWORK_APPROVED,
                        "installed-application-network-review-unresolved"
                    ),
                    satisfied(
                        ProductionAuthorizationGate.ADVERTISER_DISTRIBUTION_APPROVED,
                        JAMIESON_PARTNER_TERMS_BASIS_ID
                    ),
                    pending(
                        ProductionAuthorizationGate.TRACKING_PRIVACY_READY,
                        "network-link-tracking-privacy-not-enabled"
                    )
                )
        )

    /**
     * Translate an actual partnership termination into immediate production withdrawal plus
     * the advertiser's 60-day physical-deletion deadline. The caller supplies time explicitly.
     */
    fun evaluateTermination(
        partnershipTerminationAtEpochMillis: Long?,
        evaluatedAtEpochMillis: Long
    ): JamiesonProductCatalogTerminationDecision {
        require(evaluatedAtEpochMillis > 0L)

        if (partnershipTerminationAtEpochMillis == null) {
            return JamiesonProductCatalogTerminationDecision(
                productionUseAllowed = true,
                requiredNamespaceDisposition = ProductionDatasetDispositionState.RETAINED,
                deletionDeadlineEpochMillis = null,
                deletionOverdue = false
            )
        }

        require(partnershipTerminationAtEpochMillis > 0L)
        require(partnershipTerminationAtEpochMillis <= evaluatedAtEpochMillis) {
            "Partnership termination cannot be future-dated for a termination decision"
        }

        val deletionDeadline =
            Math.addExact(
                partnershipTerminationAtEpochMillis,
                JAMIESON_TERMINATION_DELETION_MILLIS
            )

        return JamiesonProductCatalogTerminationDecision(
            productionUseAllowed = false,
            requiredNamespaceDisposition =
                ProductionDatasetDispositionState.WITHDRAWAL_REQUIRED,
            deletionDeadlineEpochMillis = deletionDeadline,
            deletionOverdue = evaluatedAtEpochMillis > deletionDeadline
        )
    }

    private fun satisfied(
        gate: ProductionAuthorizationGate,
        basisId: String
    ): ProductionGateAssessment =
        ProductionGateAssessment(
            gate = gate,
            state = ProductionAuthorizationState.SATISFIED,
            basisId = basisId
        )

    private fun unknown(
        gate: ProductionAuthorizationGate,
        basisId: String
    ): ProductionGateAssessment =
        ProductionGateAssessment(
            gate = gate,
            state = ProductionAuthorizationState.UNKNOWN,
            basisId = basisId
        )

    private fun pending(
        gate: ProductionAuthorizationGate,
        basisId: String
    ): ProductionGateAssessment =
        ProductionGateAssessment(
            gate = gate,
            state = ProductionAuthorizationState.PENDING,
            basisId = basisId
        )
}
