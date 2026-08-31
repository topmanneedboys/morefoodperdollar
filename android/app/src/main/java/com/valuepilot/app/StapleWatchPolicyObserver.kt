package com.valuepilot.app

import com.valuepilot.core.StapleWatchPolicy

/** Receives one explicit foreground Staple Watch economic policy. */
internal fun interface StapleWatchPolicyObserver {
    fun onPolicy(policy: StapleWatchPolicy)
}
