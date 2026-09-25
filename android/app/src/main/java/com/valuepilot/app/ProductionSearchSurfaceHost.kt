package com.valuepilot.app

import com.valuepilot.core.EvidenceAcceptancePolicy
import com.valuepilot.core.ProductPackageQuantityEvidenceCandidate
import com.valuepilot.core.ProductionBestValueCandidate
import com.valuepilot.core.ProductionBestValuePresentationEvaluator
import com.valuepilot.core.ProductionCurrentPriceEligibilityRequest
import com.valuepilot.core.ProductionDatasetDispositionRegistry
import com.valuepilot.core.ProductionDatasetLifecycleRegistry

/**
 * Raw, bounded inputs required to re-evaluate production Search at display time.
 *
 * This is not an authorization token. The host passes these inputs back through
 * the verified shared-core presentation evaluator on every accepted generation.
 * Lifecycle/disposition registries are deliberately retained by reference so the
 * evaluator observes their current state when the submission is processed.
 */
data class ProductionSearchSurfaceEvaluationRequest(
    val priceRequests: List<ProductionCurrentPriceEligibilityRequest>,
    val candidates: List<ProductionBestValueCandidate>,
    val lifecycleRegistry: ProductionDatasetLifecycleRegistry,
    val dispositionRegistry: ProductionDatasetDispositionRegistry,
    val evaluatedAtEpochMillis: Long,
    val acceptancePolicy: EvidenceAcceptancePolicy,
    val quantityCandidates: List<ProductPackageQuantityEvidenceCandidate>
) {
    init {
        require(evaluatedAtEpochMillis > 0L)
    }
}

/**
 * Narrow display boundary for future production Search surfaces.
 *
 * The renderer can receive only [ProductionSearchUiState], never exact lookup
 * maps, provider URLs, internal scope identifiers, blocker diagnostics, or raw
 * production evidence retained by [ProductionSearchUiProjection].
 *
 * Public callers cannot submit a detached ProductionBestValuePresentationSnapshot
 * as display authority. Each accepted generation starts from raw production inputs
 * and re-runs [ProductionBestValuePresentationEvaluator] against the current
 * lifecycle/disposition registries at the caller-supplied decision instant.
 *
 * This class performs no I/O, owns no clock, and does not activate any provider.
 */
fun interface ProductionSearchSurfaceRenderer {
    fun render(state: ProductionSearchUiState?)
}

class ProductionSearchSurfaceHost(
    private val renderer: ProductionSearchSurfaceRenderer
) {
    private var refreshState = ProductionSearchRefreshState()

    fun evaluateAndApply(
        generation: Long,
        request: ProductionSearchSurfaceEvaluationRequest
    ): ProductionSearchRefreshDisposition {
        require(generation >= 0L)

        val currentGeneration = refreshState.latestGeneration
        if (currentGeneration != null && generation < currentGeneration) {
            return ProductionSearchRefreshDisposition.STALE
        }

        val presentation =
            ProductionBestValuePresentationEvaluator.evaluate(
                priceRequests = request.priceRequests,
                candidates = request.candidates,
                lifecycleRegistry = request.lifecycleRegistry,
                dispositionRegistry = request.dispositionRegistry,
                evaluatedAtEpochMillis = request.evaluatedAtEpochMillis,
                acceptancePolicy = request.acceptancePolicy,
                quantityCandidates = request.quantityCandidates
            )

        val decision =
            ProductionSearchRefreshGate.applySnapshot(
                current = refreshState,
                incomingGeneration = generation,
                snapshot = presentation.snapshot
            )

        applyDecision(decision)
        return decision.disposition
    }

    fun clear(generation: Long): ProductionSearchRefreshDisposition {
        val decision =
            ProductionSearchRefreshGate.clear(
                current = refreshState,
                incomingGeneration = generation
            )

        applyDecision(decision)
        return decision.disposition
    }

    private fun applyDecision(decision: ProductionSearchRefreshDecision) {
        if (decision.disposition != ProductionSearchRefreshDisposition.APPLIED) return

        val nextState = decision.state
        renderer.render(nextState.projection?.state)
        refreshState = nextState
    }
}
