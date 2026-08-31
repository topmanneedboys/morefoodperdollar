package com.valuepilot.core

/**
 * Machine-enforced production activation conditions for a provider dataset.
 *
 * These gates intentionally combine rights/permission checkpoints with factual
 * readiness checkpoints that must be cleared before a dataset can be activated
 * for a particular production use. A satisfied gate is an engineering record
 * that supporting evidence exists; it is not itself a legal opinion.
 *
 * Access to a feed, successful parsing, recent retrieval, or advertiser
 * partnership can never satisfy unrelated gates implicitly.
 */
enum class ProductionAuthorizationGate {
    DATA_ACCESS_AUTHORIZED,
    CONSUMER_DISPLAY_AUTHORIZED,
    CACHE_AUTHORIZED,
    INDEX_AUTHORIZED,
    MOBILE_APP_AUTHORIZED,
    RETENTION_DELETION_POLICY_DEFINED,
    OFFER_GEOGRAPHY_VALIDATED,
    PRICE_SEMANTICS_VALIDATED,
    DATASET_RECENCY_POLICY_DEFINED,
    OFFER_FRESHNESS_POLICY_DEFINED,
    AFFILIATE_LINK_USE_AUTHORIZED,
    INSTALLED_SOFTWARE_NETWORK_APPROVED,
    ADVERTISER_DISTRIBUTION_APPROVED,
    TRACKING_PRIVACY_READY
}

/**
 * Explicit workflow state for one production gate.
 *
 * UNKNOWN and PENDING fail closed. NOT_REQUIRED is valid only for gates that
 * are not required by the selected activation profile; it never satisfies a
 * required gate.
 */
enum class ProductionAuthorizationState {
    SATISFIED,
    PENDING,
    DENIED,
    UNKNOWN,
    NOT_REQUIRED
}

/**
 * One auditable gate assessment.
 *
 * basisId is deliberately an opaque non-secret reference such as a policy,
 * support-case, contract-review, test, or internal decision identifier. It must
 * never contain credentials or provider secrets.
 */
data class ProductionGateAssessment(
    val gate: ProductionAuthorizationGate,
    val state: ProductionAuthorizationState,
    val basisId: String? = null
) {
    init {
        basisId?.let {
            require(it.isNotBlank())
            require(it.length <= 240)
        }

        if (
            state == ProductionAuthorizationState.SATISFIED ||
            state == ProductionAuthorizationState.DENIED ||
            state == ProductionAuthorizationState.NOT_REQUIRED
        ) {
            require(!basisId.isNullOrBlank()) {
                "Satisfied, denied, and not-required gates require an auditable basis"
            }
        }
    }
}

/**
 * Defines the exact gates required for one production use case.
 *
 * Profiles are explicit so a capability that is irrelevant to one use case
 * (for example affiliate network links in a catalog-only build) cannot block
 * that use case, while the same capability becomes mandatory when enabled.
 */
data class ProductionActivationProfile(
    val id: String,
    val requiredGates: Set<ProductionAuthorizationGate>
) {
    init {
        require(id.matches(Regex("[a-z0-9][a-z0-9._-]{0,95}")))
        require(requiredGates.isNotEmpty())
    }
}

/**
 * ValuePilot's provider-neutral mobile catalog activation profiles.
 *
 * These profiles contain no provider names and grant no authorization by
 * themselves. They only declare what must already be satisfied.
 *
 * Metadata-only catalog activation is intentionally separate from price-bearing
 * catalog activation. Product names, descriptions, images and other non-price
 * catalog content still require an explicit dataset-recency policy, but they do
 * not require current-price semantics or per-offer price freshness. The metadata
 * profile grants no price, promotion, availability, stock, ranking, affiliate-link
 * or networking authority.
 */
object ProductionActivationProfiles {

    val CONSUMER_MOBILE_CATALOG_METADATA =
        ProductionActivationProfile(
            id = "consumer-mobile-catalog-metadata",
            requiredGates =
                setOf(
                    ProductionAuthorizationGate.DATA_ACCESS_AUTHORIZED,
                    ProductionAuthorizationGate.CONSUMER_DISPLAY_AUTHORIZED,
                    ProductionAuthorizationGate.CACHE_AUTHORIZED,
                    ProductionAuthorizationGate.INDEX_AUTHORIZED,
                    ProductionAuthorizationGate.MOBILE_APP_AUTHORIZED,
                    ProductionAuthorizationGate.RETENTION_DELETION_POLICY_DEFINED,
                    ProductionAuthorizationGate.OFFER_GEOGRAPHY_VALIDATED,
                    ProductionAuthorizationGate.DATASET_RECENCY_POLICY_DEFINED
                )
        )

    val CONSUMER_MOBILE_CATALOG =
        ProductionActivationProfile(
            id = "consumer-mobile-catalog",
            requiredGates =
                CONSUMER_MOBILE_CATALOG_METADATA.requiredGates +
                    setOf(
                        ProductionAuthorizationGate.PRICE_SEMANTICS_VALIDATED,
                        ProductionAuthorizationGate.OFFER_FRESHNESS_POLICY_DEFINED
                    )
        )

    val CONSUMER_MOBILE_CATALOG_WITH_NETWORK_LINKS =
        ProductionActivationProfile(
            id = "consumer-mobile-catalog-with-network-links",
            requiredGates =
                CONSUMER_MOBILE_CATALOG.requiredGates +
                    setOf(
                        ProductionAuthorizationGate.AFFILIATE_LINK_USE_AUTHORIZED,
                        ProductionAuthorizationGate.INSTALLED_SOFTWARE_NETWORK_APPROVED,
                        ProductionAuthorizationGate.ADVERTISER_DISTRIBUTION_APPROVED,
                        ProductionAuthorizationGate.TRACKING_PRIVACY_READY
                    )
        )
}

/**
 * Gate evidence for one provider + isolated dataset namespace.
 *
 * Keeping this scoped to the dataset prevents one advertiser/feed approval from
 * silently authorizing another provider dataset.
 */
data class ProviderProductionAuthorizationAssessment(
    val providerId: EvidenceProviderId,
    val datasetNamespaceId: String,
    val gates: List<ProductionGateAssessment>
) {
    init {
        require(datasetNamespaceId.matches(Regex("[a-z0-9][a-z0-9._-]{0,95}")))

        val gateKinds = gates.map { it.gate }
        require(gateKinds.size == gateKinds.toSet().size) {
            "Each production gate may be assessed at most once"
        }
    }

    fun assessmentFor(
        gate: ProductionAuthorizationGate
    ): ProductionGateAssessment? =
        gates.firstOrNull { it.gate == gate }
}

enum class ProductionActivationDecisionStatus {
    AUTHORIZED,
    BLOCKED
}

/**
 * Deterministic activation result. Blocking categories are kept separate for
 * operator UX and auditability rather than collapsed into one boolean.
 */
data class ProductionActivationDecision(
    val status: ProductionActivationDecisionStatus,
    val satisfiedGates: Set<ProductionAuthorizationGate>,
    val missingGates: Set<ProductionAuthorizationGate>,
    val pendingGates: Set<ProductionAuthorizationGate>,
    val deniedGates: Set<ProductionAuthorizationGate>,
    val unknownGates: Set<ProductionAuthorizationGate>,
    val incorrectlyNotRequiredGates: Set<ProductionAuthorizationGate>
) {
    val authorized: Boolean
        get() = status == ProductionActivationDecisionStatus.AUTHORIZED

    val blockingGates: Set<ProductionAuthorizationGate>
        get() =
            missingGates +
                pendingGates +
                deniedGates +
                unknownGates +
                incorrectlyNotRequiredGates
}

/**
 * Fail-closed production activation evaluator.
 *
 * It performs no I/O, reads no clock, parses no provider data, and never infers
 * a permission from another permission. Every gate required by the selected
 * profile must be explicitly SATISFIED.
 */
object ProductionAuthorizationEvaluator {

    fun evaluate(
        assessment: ProviderProductionAuthorizationAssessment,
        profile: ProductionActivationProfile
    ): ProductionActivationDecision {
        val satisfied = linkedSetOf<ProductionAuthorizationGate>()
        val missing = linkedSetOf<ProductionAuthorizationGate>()
        val pending = linkedSetOf<ProductionAuthorizationGate>()
        val denied = linkedSetOf<ProductionAuthorizationGate>()
        val unknown = linkedSetOf<ProductionAuthorizationGate>()
        val incorrectlyNotRequired = linkedSetOf<ProductionAuthorizationGate>()

        profile.requiredGates
            .sortedBy { it.ordinal }
            .forEach { gate ->
                when (assessment.assessmentFor(gate)?.state) {
                    null -> missing += gate
                    ProductionAuthorizationState.SATISFIED -> satisfied += gate
                    ProductionAuthorizationState.PENDING -> pending += gate
                    ProductionAuthorizationState.DENIED -> denied += gate
                    ProductionAuthorizationState.UNKNOWN -> unknown += gate
                    ProductionAuthorizationState.NOT_REQUIRED ->
                        incorrectlyNotRequired += gate
                }
            }

        val blocking =
            missing + pending + denied + unknown + incorrectlyNotRequired

        return ProductionActivationDecision(
            status =
                if (blocking.isEmpty()) {
                    ProductionActivationDecisionStatus.AUTHORIZED
                } else {
                    ProductionActivationDecisionStatus.BLOCKED
                },
            satisfiedGates = satisfied,
            missingGates = missing,
            pendingGates = pending,
            deniedGates = denied,
            unknownGates = unknown,
            incorrectlyNotRequiredGates = incorrectlyNotRequired
        )
    }
}
