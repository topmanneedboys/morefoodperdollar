package com.valuepilot.app

import com.valuepilot.core.StapleWatchPolicy

/**
 * One foreground Watch evaluation retaining the exact verified composition chain.
 *
 * A projection exists only when the verified economic coordinator produced a native Watch decision.
 * Blocked baseline/alternative/policy diagnostics remain available through the retained assemblies
 * and coordination result instead of being collapsed into presentation text. Display metadata may
 * still fail closed inside a valid projection without changing the economic result.
 *
 * This result owns no fact acquisition, clock, persistence, background work, renderer, delivery or
 * notification authority.
 */
class StapleWatchForegroundEvaluation private constructor(
    val preconditions: StapleWatchEconomicEvidencePreconditions,
    val baselineAssembly: StapleWatchUsualStoreEconomicInputAssembly,
    val alternativeAssembly: StapleWatchAlternativeEconomicInputAssembly,
    val decisionCoordination: StapleWatchEconomicDecisionCoordination,
    val projection: StapleWatchBasketUiProjection?
) {
    init {
        require(baselineAssembly.preconditions === preconditions) {
            "Staple-watch foreground evaluation must retain the exact evidence preconditions"
        }
        require(alternativeAssembly.preconditions === preconditions) {
            "Staple-watch foreground alternatives must retain the exact evidence preconditions"
        }
        require(alternativeAssembly.baselineAssembly === baselineAssembly) {
            "Staple-watch foreground alternatives must retain the exact baseline assembly"
        }
        require(decisionCoordination.baselineAssembly === baselineAssembly) {
            "Staple-watch foreground decision must retain the exact baseline assembly"
        }
        require(decisionCoordination.alternativeAssembly === alternativeAssembly) {
            "Staple-watch foreground decision must retain the exact alternative assembly"
        }
        require((projection != null) == decisionCoordination.evaluated) {
            "Staple-watch foreground projection must exist exactly when economics were evaluated"
        }
        projection?.let { projected ->
            require(projected.exactDecision === decisionCoordination.decision) {
                "Staple-watch foreground projection must retain the exact economic decision"
            }
        }
    }

    val evaluated: Boolean
        get() = projection != null

    companion object {
        internal fun create(
            preconditions: StapleWatchEconomicEvidencePreconditions,
            baselineAssembly: StapleWatchUsualStoreEconomicInputAssembly,
            alternativeAssembly: StapleWatchAlternativeEconomicInputAssembly,
            decisionCoordination: StapleWatchEconomicDecisionCoordination,
            projection: StapleWatchBasketUiProjection?
        ): StapleWatchForegroundEvaluation =
            StapleWatchForegroundEvaluation(
                preconditions = preconditions,
                baselineAssembly = baselineAssembly,
                alternativeAssembly = alternativeAssembly,
                decisionCoordination = decisionCoordination,
                projection = projection
            )
    }
}

/**
 * Pure foreground composition of already-bound Watch evidence into consumer-safe projected state.
 *
 * The input preconditions already bind the exact five authoritative Watch fact objects. This
 * coordinator does not re-bind or retrieve them. It delegates basket construction, alternative
 * diagnostics, exact economics and UI projection to their verified owners in order, preserving the
 * same object-identity chain throughout.
 *
 * A physical renderer remains a separate boundary. Economic eligibility never becomes permission
 * to schedule background work or deliver a notification here.
 */
object StapleWatchForegroundEvaluationCoordinator {

    fun evaluate(
        preconditions: StapleWatchEconomicEvidencePreconditions,
        policy: StapleWatchPolicy,
        metadata: StapleWatchStoreDisplayMetadata
    ): StapleWatchForegroundEvaluation {
        val baselineAssembly =
            StapleWatchUsualStoreEconomicInputAssembler.assemble(preconditions)
        val alternativeAssembly =
            StapleWatchAlternativeEconomicInputAssembler.assemble(
                preconditions = preconditions,
                baselineAssembly = baselineAssembly
            )
        val decisionCoordination =
            StapleWatchEconomicDecisionCoordinator.evaluate(
                baselineAssembly = baselineAssembly,
                alternativeAssembly = alternativeAssembly,
                policy = policy
            )
        val projection =
            decisionCoordination.decision?.let { decision ->
                StapleWatchUiProjector.project(
                    decision = decision,
                    metadata = metadata
                )
            }

        return StapleWatchForegroundEvaluation.create(
            preconditions = preconditions,
            baselineAssembly = baselineAssembly,
            alternativeAssembly = alternativeAssembly,
            decisionCoordination = decisionCoordination,
            projection = projection
        )
    }
}
