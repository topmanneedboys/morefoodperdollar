package com.valuepilot.app

import com.valuepilot.core.StapleWatchBasketEconomicDecision
import com.valuepilot.core.StapleWatchEconomicEvaluator
import com.valuepilot.core.StapleWatchPolicy

enum class StapleWatchEconomicDecisionCoordinationBlocker {
    BASELINE_INPUT_NOT_ASSEMBLED,
    POLICY_MONEY_SPEC_DIFFERS_FROM_BASELINE
}

/**
 * One exact Watch economic decision composition result.
 *
 * The exact verified baseline and alternative assemblies are retained so diagnostic per-store
 * outcomes remain available without becoming economic inputs themselves. A decision exists only
 * when the baseline is assembled and the explicit policy uses the same money specification.
 *
 * Construction is private so callers cannot pair a detached decision with different assemblies.
 * A worthwhile shared-core result remains only an economic decision; this type is not notification
 * authorization and owns no persistence or background-work authority.
 */
class StapleWatchEconomicDecisionCoordination private constructor(
    val baselineAssembly: StapleWatchUsualStoreEconomicInputAssembly,
    val alternativeAssembly: StapleWatchAlternativeEconomicInputAssembly,
    val policy: StapleWatchPolicy,
    val decision: StapleWatchBasketEconomicDecision?,
    val blocker: StapleWatchEconomicDecisionCoordinationBlocker?
) {
    init {
        require(alternativeAssembly.baselineAssembly === baselineAssembly) {
            "Staple-watch economic decision must retain the exact baseline assembly"
        }
        require(alternativeAssembly.preconditions === baselineAssembly.preconditions) {
            "Staple-watch economic decision assemblies must retain the same exact preconditions"
        }
        require((decision != null) == (blocker == null)) {
            "Staple-watch economic decision coordination must be either evaluated or blocked"
        }
        decision?.let { evaluated ->
            require(evaluated.baseline === baselineAssembly.candidate) {
                "Staple-watch economic decision must retain the exact assembled baseline candidate"
            }
        }
    }

    val evaluated: Boolean
        get() = decision != null

    companion object {
        internal fun blocked(
            baselineAssembly: StapleWatchUsualStoreEconomicInputAssembly,
            alternativeAssembly: StapleWatchAlternativeEconomicInputAssembly,
            policy: StapleWatchPolicy,
            blocker: StapleWatchEconomicDecisionCoordinationBlocker
        ): StapleWatchEconomicDecisionCoordination =
            StapleWatchEconomicDecisionCoordination(
                baselineAssembly = baselineAssembly,
                alternativeAssembly = alternativeAssembly,
                policy = policy,
                decision = null,
                blocker = blocker
            )

        internal fun evaluated(
            baselineAssembly: StapleWatchUsualStoreEconomicInputAssembly,
            alternativeAssembly: StapleWatchAlternativeEconomicInputAssembly,
            policy: StapleWatchPolicy,
            decision: StapleWatchBasketEconomicDecision
        ): StapleWatchEconomicDecisionCoordination =
            StapleWatchEconomicDecisionCoordination(
                baselineAssembly = baselineAssembly,
                alternativeAssembly = alternativeAssembly,
                policy = policy,
                decision = decision,
                blocker = null
            )
    }
}

/**
 * Pure application-layer delegation from verified Watch assemblies to shared-core economics.
 *
 * This boundary performs no savings arithmetic, route-cap interpretation, alternative ranking,
 * evidence acquisition/currentness work, provider choice, persistence, scheduling, UI projection,
 * or notification delivery. All exact economics stay in [StapleWatchEconomicEvaluator].
 */
object StapleWatchEconomicDecisionCoordinator {

    fun evaluate(
        baselineAssembly: StapleWatchUsualStoreEconomicInputAssembly,
        alternativeAssembly: StapleWatchAlternativeEconomicInputAssembly,
        policy: StapleWatchPolicy
    ): StapleWatchEconomicDecisionCoordination {
        require(alternativeAssembly.baselineAssembly === baselineAssembly) {
            "Staple-watch economic decision requires the exact alternative assembly baseline"
        }
        require(alternativeAssembly.preconditions === baselineAssembly.preconditions) {
            "Staple-watch economic decision requires one exact evidence-precondition object"
        }

        val baseline =
            baselineAssembly.candidate
                ?: return StapleWatchEconomicDecisionCoordination.blocked(
                    baselineAssembly = baselineAssembly,
                    alternativeAssembly = alternativeAssembly,
                    policy = policy,
                    blocker =
                        StapleWatchEconomicDecisionCoordinationBlocker
                            .BASELINE_INPUT_NOT_ASSEMBLED
                )
        require(alternativeAssembly.blocker == null) {
            "Assembled staple-watch baseline cannot be paired with a blocked alternative assembly"
        }

        if (
            policy.minimumSwitchSavings.currencyCode != baseline.knownBasketCost.currencyCode ||
            policy.minimumSwitchSavings.fractionDigits != baseline.knownBasketCost.fractionDigits
        ) {
            return StapleWatchEconomicDecisionCoordination.blocked(
                baselineAssembly = baselineAssembly,
                alternativeAssembly = alternativeAssembly,
                policy = policy,
                blocker =
                    StapleWatchEconomicDecisionCoordinationBlocker
                        .POLICY_MONEY_SPEC_DIFFERS_FROM_BASELINE
            )
        }

        val decision =
            StapleWatchEconomicEvaluator.evaluate(
                request = baselineAssembly.preconditions.intent.request,
                baseline = baseline,
                alternatives = alternativeAssembly.assembledCandidates,
                policy = policy
            )
        return StapleWatchEconomicDecisionCoordination.evaluated(
            baselineAssembly = baselineAssembly,
            alternativeAssembly = alternativeAssembly,
            policy = policy,
            decision = decision
        )
    }
}
