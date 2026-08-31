package com.valuepilot.app

import com.valuepilot.core.Money
import com.valuepilot.core.StapleWatchPolicy

private const val MIN_STAPLE_WATCH_POLICY_ITEM_COUNT = 2
private const val MAX_STAPLE_WATCH_POLICY_ITEM_COUNT = 128

/**
 * Explicit distance-cap choice for an in-progress Staple Watch policy.
 *
 * [Unanswered] means the user/product owner has not made a choice yet. [Unlimited] is an explicit
 * choice to apply no distance cap and is therefore deliberately distinct from unanswered state.
 * [AtMostMetres] preserves an explicit non-negative cap, including zero.
 */
sealed interface StapleWatchPolicyDistanceLimitDraft {
    data object Unanswered : StapleWatchPolicyDistanceLimitDraft
    data object Unlimited : StapleWatchPolicyDistanceLimitDraft

    data class AtMostMetres(val metres: Long) : StapleWatchPolicyDistanceLimitDraft {
        init {
            require(metres >= 0L) { "Staple-watch policy distance cap cannot be negative" }
        }
    }
}

/**
 * Memory-only explicit draft for the economic choices that become [StapleWatchPolicy].
 *
 * The draft starts with every choice unanswered. There are no savings, travel, distance or staple
 * count defaults. Savings are entered as exact minor units under the already-verified baseline
 * money specification, so neither device locale nor presentation text can choose the currency or
 * precision. Explicit zero values remain distinguishable from unanswered values.
 *
 * This class owns no text parsing, UI, persistence, fact acquisition, provider/network access,
 * evaluation, background work or notification authorization.
 */
class StapleWatchPolicyDraft private constructor(
    val moneySpec: StapleWatchPolicyBaselineMoneySpec,
    val minimumSwitchSavingsMinorUnits: Long?,
    val maxAdditionalTravelSeconds: Long?,
    val distanceLimit: StapleWatchPolicyDistanceLimitDraft,
    val minimumStapleItemCount: Int?
) {
    init {
        require(minimumSwitchSavingsMinorUnits == null || minimumSwitchSavingsMinorUnits >= 0L) {
            "Staple-watch minimum switch savings cannot be negative"
        }
        require(maxAdditionalTravelSeconds == null || maxAdditionalTravelSeconds >= 0L) {
            "Staple-watch additional travel cap cannot be negative"
        }
        require(
            minimumStapleItemCount == null ||
                minimumStapleItemCount in
                    MIN_STAPLE_WATCH_POLICY_ITEM_COUNT..MAX_STAPLE_WATCH_POLICY_ITEM_COUNT
        ) {
            "Staple-watch minimum item count is outside the supported Watch range"
        }
    }

    fun withMinimumSwitchSavingsMinorUnits(minorUnits: Long): StapleWatchPolicyDraft =
        StapleWatchPolicyDraft(
            moneySpec = moneySpec,
            minimumSwitchSavingsMinorUnits = minorUnits,
            maxAdditionalTravelSeconds = maxAdditionalTravelSeconds,
            distanceLimit = distanceLimit,
            minimumStapleItemCount = minimumStapleItemCount
        )

    fun withMaxAdditionalTravelSeconds(seconds: Long): StapleWatchPolicyDraft =
        StapleWatchPolicyDraft(
            moneySpec = moneySpec,
            minimumSwitchSavingsMinorUnits = minimumSwitchSavingsMinorUnits,
            maxAdditionalTravelSeconds = seconds,
            distanceLimit = distanceLimit,
            minimumStapleItemCount = minimumStapleItemCount
        )

    fun withDistanceLimit(limit: StapleWatchPolicyDistanceLimitDraft): StapleWatchPolicyDraft =
        StapleWatchPolicyDraft(
            moneySpec = moneySpec,
            minimumSwitchSavingsMinorUnits = minimumSwitchSavingsMinorUnits,
            maxAdditionalTravelSeconds = maxAdditionalTravelSeconds,
            distanceLimit = limit,
            minimumStapleItemCount = minimumStapleItemCount
        )

    fun withMinimumStapleItemCount(count: Int): StapleWatchPolicyDraft =
        StapleWatchPolicyDraft(
            moneySpec = moneySpec,
            minimumSwitchSavingsMinorUnits = minimumSwitchSavingsMinorUnits,
            maxAdditionalTravelSeconds = maxAdditionalTravelSeconds,
            distanceLimit = distanceLimit,
            minimumStapleItemCount = count
        )

    companion object {
        fun start(moneySpec: StapleWatchPolicyBaselineMoneySpec): StapleWatchPolicyDraft =
            StapleWatchPolicyDraft(
                moneySpec = moneySpec,
                minimumSwitchSavingsMinorUnits = null,
                maxAdditionalTravelSeconds = null,
                distanceLimit = StapleWatchPolicyDistanceLimitDraft.Unanswered,
                minimumStapleItemCount = null
            )
    }
}

enum class StapleWatchPolicyDraftRequirement {
    MINIMUM_SWITCH_SAVINGS,
    MAX_ADDITIONAL_TRAVEL,
    DISTANCE_LIMIT_CHOICE,
    MINIMUM_STAPLE_ITEM_COUNT
}

/**
 * Fail-closed finalization result. Policy exists only when every explicit draft choice is present.
 */
class StapleWatchPolicyDraftFinalization private constructor(
    val draft: StapleWatchPolicyDraft,
    val missingRequirements: List<StapleWatchPolicyDraftRequirement>,
    val policy: StapleWatchPolicy?
) {
    init {
        require(missingRequirements.distinct().size == missingRequirements.size) {
            "Staple-watch policy draft requirements must be unique"
        }
        require((policy != null) == missingRequirements.isEmpty()) {
            "Staple-watch policy can exist only for a complete explicit draft"
        }
    }

    val finalized: Boolean
        get() = policy != null

    companion object {
        internal fun create(
            draft: StapleWatchPolicyDraft,
            missingRequirements: List<StapleWatchPolicyDraftRequirement>,
            policy: StapleWatchPolicy?
        ): StapleWatchPolicyDraftFinalization =
            StapleWatchPolicyDraftFinalization(
                draft = draft,
                missingRequirements = missingRequirements,
                policy = policy
            )
    }
}

/** Pure conversion from a complete explicit draft to the shared-core economic policy contract. */
object StapleWatchPolicyDraftFinalizer {

    fun finalize(draft: StapleWatchPolicyDraft): StapleWatchPolicyDraftFinalization {
        val missing = buildList {
            if (draft.minimumSwitchSavingsMinorUnits == null) {
                add(StapleWatchPolicyDraftRequirement.MINIMUM_SWITCH_SAVINGS)
            }
            if (draft.maxAdditionalTravelSeconds == null) {
                add(StapleWatchPolicyDraftRequirement.MAX_ADDITIONAL_TRAVEL)
            }
            if (draft.distanceLimit == StapleWatchPolicyDistanceLimitDraft.Unanswered) {
                add(StapleWatchPolicyDraftRequirement.DISTANCE_LIMIT_CHOICE)
            }
            if (draft.minimumStapleItemCount == null) {
                add(StapleWatchPolicyDraftRequirement.MINIMUM_STAPLE_ITEM_COUNT)
            }
        }
        if (missing.isNotEmpty()) {
            return StapleWatchPolicyDraftFinalization.create(
                draft = draft,
                missingRequirements = missing,
                policy = null
            )
        }

        val distanceMetres =
            when (val distance = draft.distanceLimit) {
                StapleWatchPolicyDistanceLimitDraft.Unanswered ->
                    error("Complete staple-watch policy draft retained unanswered distance")
                StapleWatchPolicyDistanceLimitDraft.Unlimited -> null
                is StapleWatchPolicyDistanceLimitDraft.AtMostMetres -> distance.metres
            }
        val policy =
            StapleWatchPolicy(
                minimumSwitchSavings =
                    Money(
                        minorUnits = requireNotNull(draft.minimumSwitchSavingsMinorUnits),
                        currencyCode = draft.moneySpec.currencyCode,
                        fractionDigits = draft.moneySpec.fractionDigits
                    ),
                maxAdditionalTravelSeconds = requireNotNull(draft.maxAdditionalTravelSeconds),
                maxAdditionalDistanceMetres = distanceMetres,
                minimumStapleItemCount = requireNotNull(draft.minimumStapleItemCount)
            )

        return StapleWatchPolicyDraftFinalization.create(
            draft = draft,
            missingRequirements = emptyList(),
            policy = policy
        )
    }
}
