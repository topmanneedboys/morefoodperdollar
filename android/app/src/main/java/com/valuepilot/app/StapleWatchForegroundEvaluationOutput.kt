package com.valuepilot.app

/**
 * Typed foreground-output lifecycle for one Staple Watch evaluation input host.
 *
 * [Cleared] means the host accepted new evidence and any previously presented evaluation must no
 * longer be treated as current. [Evaluated] carries the immutable foreground evaluation produced
 * from one exact evidence session plus explicit policy and display metadata.
 *
 * This boundary does not project, render, persist, schedule work, or authorize notifications.
 */
internal sealed interface StapleWatchForegroundEvaluationOutput {
    object Cleared : StapleWatchForegroundEvaluationOutput

    class Evaluated(
        val evaluation: StapleWatchForegroundEvaluation
    ) : StapleWatchForegroundEvaluationOutput
}

/** Narrow observer for foreground evaluation lifecycle changes. */
internal fun interface StapleWatchForegroundEvaluationOutputObserver {
    fun onOutputChanged(output: StapleWatchForegroundEvaluationOutput)
}
