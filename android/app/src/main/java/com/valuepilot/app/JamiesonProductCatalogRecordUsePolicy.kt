package com.valuepilot.app

import com.valuepilot.core.ProductionDatasetDispositionState
import com.valuepilot.core.ProviderDatasetCountryMatchStatus

/**
 * Why one already-parsed Jamieson Product Catalog record cannot currently be used.
 *
 * This is a catalog-use boundary only. Price semantics, dataset recency and per-offer
 * freshness remain separate factual gates and are surfaced as ranking blockers rather
 * than being guessed from advertiser permission, CAD, or the existence of a feed row.
 */
enum class JamiesonProductCatalogRecordUseBlocker {
    DATASET_NAMESPACE_MISMATCH,
    TARGET_MARKET_MISMATCH,
    SOURCE_CURRENCY_MISMATCH,
    PARTNERSHIP_TERMINATED,
    PRICE_SEMANTICS_UNVERIFIED,
    DATASET_RECENCY_UNVERIFIED,
    OFFER_FRESHNESS_UNVERIFIED
}

data class JamiesonProductCatalogRecordUseDecision(
    val displayAllowed: Boolean,
    val searchAndCatalogComparisonAllowed: Boolean,
    val cacheAndIndexAllowed: Boolean,
    val priceRankingAllowed: Boolean,
    val requiredNamespaceDisposition: ProductionDatasetDispositionState,
    val deletionDeadlineEpochMillis: Long?,
    val deletionOverdue: Boolean,
    val blockers: Set<JamiesonProductCatalogRecordUseBlocker>
) {
    init {
        if (displayAllowed || searchAndCatalogComparisonAllowed || cacheAndIndexAllowed) {
            require(requiredNamespaceDisposition == ProductionDatasetDispositionState.RETAINED)
            require(JamiesonProductCatalogRecordUseBlocker.PARTNERSHIP_TERMINATED !in blockers)
        }
        if (priceRankingAllowed) {
            require(displayAllowed && searchAndCatalogComparisonAllowed)
            require(
                JamiesonProductCatalogRecordUseBlocker.PRICE_SEMANTICS_UNVERIFIED !in blockers &&
                    JamiesonProductCatalogRecordUseBlocker.DATASET_RECENCY_UNVERIFIED !in blockers &&
                    JamiesonProductCatalogRecordUseBlocker.OFFER_FRESHNESS_UNVERIFIED !in blockers
            )
        }
        if (requiredNamespaceDisposition == ProductionDatasetDispositionState.WITHDRAWAL_REQUIRED) {
            require(!displayAllowed && !searchAndCatalogComparisonAllowed && !cacheAndIndexAllowed)
            require(deletionDeadlineEpochMillis != null)
        }
        require(!deletionOverdue || requiredNamespaceDisposition == ProductionDatasetDispositionState.WITHDRAWAL_REQUIRED)
    }
}

/**
 * Applies the verified Jamieson partner contract to one already-parsed catalog record.
 *
 * The caller supplies the exact dataset namespace, source currency, consumer target market,
 * partnership termination state and evaluation time. No field names from a Rakuten feed are
 * assumed here. This policy performs no parsing, I/O, networking, clock reads, persistence,
 * ranking, evidence creation or offer construction.
 *
 * Catalog display/search/cache rights are independent from current-price rankability. The
 * advertiser has authorized the former for the Canadian CAD catalog, while current-price
 * semantics, dataset recency and per-offer freshness remain unresolved. Consequently this
 * boundary intentionally cannot authorize price ranking yet.
 */
object JamiesonProductCatalogRecordUsePolicy {

    fun evaluate(
        datasetNamespaceId: String,
        sourceCurrencyCode: String?,
        targetCountryCode: String,
        partnershipTerminationAtEpochMillis: Long?,
        evaluatedAtEpochMillis: Long
    ): JamiesonProductCatalogRecordUseDecision {
        require(datasetNamespaceId.isNotBlank())
        require(targetCountryCode.matches(Regex("[A-Z]{2}")))
        require(evaluatedAtEpochMillis > 0L)

        val contract = JamiesonProductCatalogProductionContract
        val termination =
            contract.evaluateTermination(
                partnershipTerminationAtEpochMillis = partnershipTerminationAtEpochMillis,
                evaluatedAtEpochMillis = evaluatedAtEpochMillis
            )
        val geography = contract.geographyAssessment(targetCountryCode)

        val blockers = linkedSetOf<JamiesonProductCatalogRecordUseBlocker>()
        if (datasetNamespaceId != contract.DATASET_NAMESPACE_ID) {
            blockers += JamiesonProductCatalogRecordUseBlocker.DATASET_NAMESPACE_MISMATCH
        }
        if (geography.status != ProviderDatasetCountryMatchStatus.MATCH) {
            blockers += JamiesonProductCatalogRecordUseBlocker.TARGET_MARKET_MISMATCH
        }
        if (!contract.matchesDeclaredFeedCurrency(sourceCurrencyCode)) {
            blockers += JamiesonProductCatalogRecordUseBlocker.SOURCE_CURRENCY_MISMATCH
        }
        if (!termination.productionUseAllowed) {
            blockers += JamiesonProductCatalogRecordUseBlocker.PARTNERSHIP_TERMINATED
        }

        val catalogScopeAllowed =
            JamiesonProductCatalogRecordUseBlocker.DATASET_NAMESPACE_MISMATCH !in blockers &&
                JamiesonProductCatalogRecordUseBlocker.TARGET_MARKET_MISMATCH !in blockers &&
                JamiesonProductCatalogRecordUseBlocker.SOURCE_CURRENCY_MISMATCH !in blockers &&
                JamiesonProductCatalogRecordUseBlocker.PARTNERSHIP_TERMINATED !in blockers

        // These three gates remain unresolved by the written partner confirmation.
        blockers += JamiesonProductCatalogRecordUseBlocker.PRICE_SEMANTICS_UNVERIFIED
        blockers += JamiesonProductCatalogRecordUseBlocker.DATASET_RECENCY_UNVERIFIED
        blockers += JamiesonProductCatalogRecordUseBlocker.OFFER_FRESHNESS_UNVERIFIED

        return JamiesonProductCatalogRecordUseDecision(
            displayAllowed = catalogScopeAllowed,
            searchAndCatalogComparisonAllowed = catalogScopeAllowed,
            cacheAndIndexAllowed = catalogScopeAllowed,
            priceRankingAllowed = false,
            requiredNamespaceDisposition = termination.requiredNamespaceDisposition,
            deletionDeadlineEpochMillis = termination.deletionDeadlineEpochMillis,
            deletionOverdue = termination.deletionOverdue,
            blockers = blockers
        )
    }
}
