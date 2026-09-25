package com.valuepilot.app

import com.valuepilot.core.StapleWatchPolicy

/**
 * Pure foreground ownership of the explicit inputs required for one completed Watch evidence set.
 *
 * A session starts from one exact [StapleWatchEconomicEvidencePreconditions] object and never
 * changes that evidence identity. Policy and consumer-safe display metadata must each be supplied
 * explicitly for this session. No default policy, inherited metadata, provider lookup, clock,
 * persistence, renderer, background scheduling, delivery or notification authority exists here.
 *
 * Once both explicit inputs are present, evaluation is delegated unchanged to the verified
 * [StapleWatchForegroundEvaluationCoordinator]. Replacing either explicit input creates a new
 * immutable session and recomputes from the same exact evidence preconditions. A later evidence
 * set must start a new session, so policy or metadata cannot silently carry across requests.
 */
internal class StapleWatchForegroundEvaluationInputSession private constructor(
    val preconditions: StapleWatchEconomicEvidencePreconditions,
    val policy: StapleWatchPolicy?,
    val displayMetadata: StapleWatchStoreDisplayMetadata?,
    val evaluation: StapleWatchForegroundEvaluation?
) {
    init {
        require((evaluation != null) == (policy != null && displayMetadata != null)) {
            "Staple-watch foreground evaluation must exist exactly when both explicit inputs exist"
        }
        evaluation?.let { evaluated ->
            require(evaluated.preconditions === preconditions) {
                "Staple-watch foreground evaluation must retain the exact session preconditions"
            }
            require(evaluated.decisionCoordination.policy === policy) {
                "Staple-watch foreground evaluation must retain the exact session policy"
            }
        }
    }

    val readyForEvaluation: Boolean
        get() = policy != null && displayMetadata != null

    fun withPolicy(policy: StapleWatchPolicy): StapleWatchForegroundEvaluationInputSession =
        if (this.policy === policy) {
            this
        } else {
            create(
                preconditions = preconditions,
                policy = policy,
                displayMetadata = displayMetadata
            )
        }

    fun withDisplayMetadata(
        displayMetadata: StapleWatchStoreDisplayMetadata
    ): StapleWatchForegroundEvaluationInputSession =
        if (this.displayMetadata === displayMetadata) {
            this
        } else {
            create(
                preconditions = preconditions,
                policy = policy,
                displayMetadata = displayMetadata
            )
        }

    companion object {
        fun start(
            preconditions: StapleWatchEconomicEvidencePreconditions
        ): StapleWatchForegroundEvaluationInputSession =
            create(
                preconditions = preconditions,
                policy = null,
                displayMetadata = null
            )

        private fun create(
            preconditions: StapleWatchEconomicEvidencePreconditions,
            policy: StapleWatchPolicy?,
            displayMetadata: StapleWatchStoreDisplayMetadata?
        ): StapleWatchForegroundEvaluationInputSession {
            val evaluation =
                if (policy != null && displayMetadata != null) {
                    StapleWatchForegroundEvaluationCoordinator.evaluate(
                        preconditions = preconditions,
                        policy = policy,
                        metadata = displayMetadata
                    )
                } else {
                    null
                }

            return StapleWatchForegroundEvaluationInputSession(
                preconditions = preconditions,
                policy = policy,
                displayMetadata = displayMetadata,
                evaluation = evaluation
            )
        }
    }
}
