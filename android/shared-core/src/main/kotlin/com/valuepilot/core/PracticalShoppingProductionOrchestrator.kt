package com.valuepilot.core

private const val MAX_ORCHESTRATION_STORES = 64
private const val MAX_ORCHESTRATION_STORE_PAIRS = 128
private const val MAX_ORCHESTRATION_PRICE_BINDINGS = 128
private const val MAX_ORCHESTRATION_PRICE_REQUESTS = 128

/**
 * Immutable adapter-facing input for one point-in-time production Practical
 * Shopping evaluation.
 *
 * This object intentionally does not carry lifecycle/disposition registries.
 * Those registries are supplied at execution so callers cannot freeze current
 * production authority inside a reusable request object.
 *
 * Product resolution, retailer discovery, route acquisition and provider I/O
 * happen outside this contract. Every identity/travel/evidence input here must
 * already have been established by its owning adapter/orchestration layer.
 */
data class PracticalShoppingProductionOrchestrationRequest(
    val shoppingRequest: ShoppingRequest,
    val stores: List<PracticalShoppingProductionStoreScope>,
    val storePairs: List<PracticalShoppingProductionStorePairScope>,
    val priceBindings: List<PracticalShoppingProductionPriceBinding>,
    val priceRequests: List<ProductionCurrentPriceEligibilityRequest>,
    val evaluatedAtEpochMillis: Long,
    val acceptancePolicy: EvidenceAcceptancePolicy,
    val planningPolicy: PracticalShoppingPolicy
) {
    init {
        require(evaluatedAtEpochMillis > 0L)
    }
}

enum class PracticalShoppingProductionOrchestrationIssue {
    TOO_MANY_STORES,
    TOO_MANY_STORE_PAIRS,
    TOO_MANY_PRICE_BINDINGS,
    TOO_MANY_PRICE_REQUESTS,
    DUPLICATE_STORE_KEY,
    DUPLICATE_STORE_PAIR,
    DUPLICATE_PRICE_REQUEST_ID,
    DUPLICATE_ITEM_STORE_BINDING,
    DUPLICATE_BOUND_PRICE_REQUEST_ID,
    DUPLICATE_STORE_PRODUCT_BINDING,
    BINDING_ITEM_NOT_REQUESTED,
    BINDING_STORE_NOT_DECLARED,
    BINDING_PRICE_REQUEST_NOT_SUPPLIED,
    PAIR_BASE_STORE_NOT_DECLARED,
    PAIR_ADDED_STORE_NOT_DECLARED
}

data class PracticalShoppingProductionOrchestrationValidation(
    val issues: Set<PracticalShoppingProductionOrchestrationIssue>
) {
    val valid: Boolean
        get() = issues.isEmpty()
}

data class PracticalShoppingProductionOrchestrationResult(
    val validation: PracticalShoppingProductionOrchestrationValidation,
    val decisionResult: PracticalShoppingProductionDecisionResult?
) {
    init {
        require((decisionResult != null) == validation.valid) {
            "A production shopping decision exists if and only if orchestration references are valid"
        }
    }
}

/**
 * Fail-closed assembly boundary immediately before production Practical Shopping
 * evidence evaluation.
 *
 * Validation distinguishes an orchestration/reference defect from legitimate
 * incomplete market evidence. It therefore does NOT require every requested item
 * to have a price, every store to cover every item, or every raw price request to
 * be directly bound. Extra raw requests are intentionally allowed because an
 * unbound same-product claim may still be required by downstream factual conflict
 * resolution.
 *
 * When validation passes, [evaluate] supplies the then-current lifecycle and
 * namespace-disposition registries to [PracticalShoppingProductionDecisionEvaluator].
 * No detached eligibility result or previous shopping decision is accepted.
 */
object PracticalShoppingProductionOrchestrator {

    fun validate(
        request: PracticalShoppingProductionOrchestrationRequest
    ): PracticalShoppingProductionOrchestrationValidation {
        val issues = linkedSetOf<PracticalShoppingProductionOrchestrationIssue>()

        if (request.stores.size > MAX_ORCHESTRATION_STORES) {
            issues += PracticalShoppingProductionOrchestrationIssue.TOO_MANY_STORES
        }
        if (request.storePairs.size > MAX_ORCHESTRATION_STORE_PAIRS) {
            issues += PracticalShoppingProductionOrchestrationIssue.TOO_MANY_STORE_PAIRS
        }
        if (request.priceBindings.size > MAX_ORCHESTRATION_PRICE_BINDINGS) {
            issues += PracticalShoppingProductionOrchestrationIssue.TOO_MANY_PRICE_BINDINGS
        }
        if (request.priceRequests.size > MAX_ORCHESTRATION_PRICE_REQUESTS) {
            issues += PracticalShoppingProductionOrchestrationIssue.TOO_MANY_PRICE_REQUESTS
        }

        val storeKeys = request.stores.map { it.storeKey }
        if (storeKeys.size != storeKeys.toSet().size) {
            issues += PracticalShoppingProductionOrchestrationIssue.DUPLICATE_STORE_KEY
        }
        val declaredStoreKeys = storeKeys.toSet()

        val pairKeys = request.storePairs.map { it.baseStoreKey to it.addedStoreKey }
        if (pairKeys.size != pairKeys.toSet().size) {
            issues += PracticalShoppingProductionOrchestrationIssue.DUPLICATE_STORE_PAIR
        }

        val priceRequestIds = request.priceRequests.map { it.requestId }
        if (priceRequestIds.size != priceRequestIds.toSet().size) {
            issues += PracticalShoppingProductionOrchestrationIssue.DUPLICATE_PRICE_REQUEST_ID
        }
        val suppliedPriceRequestIds = priceRequestIds.toSet()

        val itemStoreKeys = request.priceBindings.map { it.itemKey to it.storeKey }
        if (itemStoreKeys.size != itemStoreKeys.toSet().size) {
            issues += PracticalShoppingProductionOrchestrationIssue.DUPLICATE_ITEM_STORE_BINDING
        }

        val boundPriceRequestIds = request.priceBindings.map { it.currentPriceRequestId }
        if (boundPriceRequestIds.size != boundPriceRequestIds.toSet().size) {
            issues += PracticalShoppingProductionOrchestrationIssue.DUPLICATE_BOUND_PRICE_REQUEST_ID
        }

        val storeProductKeys = request.priceBindings.map { it.storeKey to it.productKey }
        if (storeProductKeys.size != storeProductKeys.toSet().size) {
            issues += PracticalShoppingProductionOrchestrationIssue.DUPLICATE_STORE_PRODUCT_BINDING
        }

        val requestedItemKeys = request.shoppingRequest.itemKeys.toSet()
        request.priceBindings.forEach { binding ->
            if (binding.itemKey !in requestedItemKeys) {
                issues += PracticalShoppingProductionOrchestrationIssue.BINDING_ITEM_NOT_REQUESTED
            }
            if (binding.storeKey !in declaredStoreKeys) {
                issues += PracticalShoppingProductionOrchestrationIssue.BINDING_STORE_NOT_DECLARED
            }
            if (binding.currentPriceRequestId !in suppliedPriceRequestIds) {
                issues += PracticalShoppingProductionOrchestrationIssue.BINDING_PRICE_REQUEST_NOT_SUPPLIED
            }
        }

        request.storePairs.forEach { pair ->
            if (pair.baseStoreKey !in declaredStoreKeys) {
                issues += PracticalShoppingProductionOrchestrationIssue.PAIR_BASE_STORE_NOT_DECLARED
            }
            if (pair.addedStoreKey !in declaredStoreKeys) {
                issues += PracticalShoppingProductionOrchestrationIssue.PAIR_ADDED_STORE_NOT_DECLARED
            }
        }

        return PracticalShoppingProductionOrchestrationValidation(issues = issues)
    }

    fun evaluate(
        request: PracticalShoppingProductionOrchestrationRequest,
        lifecycleRegistry: ProductionDatasetLifecycleRegistry,
        dispositionRegistry: ProductionDatasetDispositionRegistry
    ): PracticalShoppingProductionOrchestrationResult {
        val validation = validate(request)
        if (!validation.valid) {
            return PracticalShoppingProductionOrchestrationResult(
                validation = validation,
                decisionResult = null
            )
        }

        val decisionResult =
            PracticalShoppingProductionDecisionEvaluator.evaluate(
                request = request.shoppingRequest,
                stores = request.stores,
                storePairs = request.storePairs,
                priceBindings = request.priceBindings,
                priceRequests = request.priceRequests,
                lifecycleRegistry = lifecycleRegistry,
                dispositionRegistry = dispositionRegistry,
                evaluatedAtEpochMillis = request.evaluatedAtEpochMillis,
                acceptancePolicy = request.acceptancePolicy,
                planningPolicy = request.planningPolicy
            )

        return PracticalShoppingProductionOrchestrationResult(
            validation = validation,
            decisionResult = decisionResult
        )
    }
}
