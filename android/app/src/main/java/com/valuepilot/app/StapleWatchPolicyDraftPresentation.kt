package com.valuepilot.app

/** Consumer-only readiness for an explicit Staple Watch economic-policy draft. */
enum class StapleWatchPolicyDraftUiStatus {
    NEEDS_POLICY_INPUT,
    READY_FOR_POLICY_HANDOFF
}

/** Presentation-only shape of the explicit distance-cap choice. */
enum class StapleWatchPolicyDistanceLimitUiMode {
    UNANSWERED,
    UNLIMITED,
    AT_MOST_METRES
}

/**
 * Typed consumer edit intents. A later route-local owner may map these to immutable draft updates.
 *
 * These actions deliberately carry already-parsed exact values. This presentation boundary does
 * not parse text, choose defaults, infer locale/currency, mutate a draft, or authorize economics.
 */
sealed interface StapleWatchPolicyDraftUiAction {
    data class SetMinimumSwitchSavingsMinorUnits(
        val minorUnits: Long
    ) : StapleWatchPolicyDraftUiAction

    data class SetMaxAdditionalTravelSeconds(
        val seconds: Long
    ) : StapleWatchPolicyDraftUiAction

    data object SetDistanceUnlimited : StapleWatchPolicyDraftUiAction

    data class SetMaxAdditionalDistanceMetres(
        val metres: Long
    ) : StapleWatchPolicyDraftUiAction

    data class SetMinimumStapleItemCount(
        val count: Int
    ) : StapleWatchPolicyDraftUiAction
}

/** Identity-free request to hand an already-complete explicit policy to a later composition owner. */
sealed interface StapleWatchPolicyHandoffUiAction {
    data object Request : StapleWatchPolicyHandoffUiAction
}

/**
 * Immutable consumer-ready projection of one explicit policy draft.
 *
 * Monetary values remain exact minor units accompanied by the verified baseline currency code and
 * fraction digits. Presentation never converts them through device locale or a floating-point type.
 * Numeric edit actions carry exact typed values but do not themselves mutate or validate the draft.
 *
 * [missingRequirements] comes from the already-produced finalization result. The paired
 * [missingRequirementLabels] list is a fixed consumer copy of the same order for progressive
 * guidance; it never exposes the enum to a renderer. The projector does not re-run finalization
 * or construct policy. A continuation marker is exposed only when that retained finalization is
 * already complete; it carries no policy payload and grants no evaluation, persistence,
 * background, or notification authority.
 */
data class StapleWatchPolicyDraftUiState(
    val status: StapleWatchPolicyDraftUiStatus,
    val headline: String,
    val guidance: String,
    val currencyCode: String,
    val currencyFractionDigits: Int,
    val minimumSwitchSavingsLabel: String,
    val minimumSwitchSavingsMinorUnits: Long?,
    val minimumSwitchSavingsUnitLabel: String,
    val maxAdditionalTravelLabel: String,
    val maxAdditionalTravelSeconds: Long?,
    val maxAdditionalTravelUnitLabel: String,
    val distanceLimitLabel: String,
    val distanceLimitMode: StapleWatchPolicyDistanceLimitUiMode,
    val maxAdditionalDistanceMetres: Long?,
    val maxAdditionalDistanceUnitLabel: String,
    val minimumStapleItemCountLabel: String,
    val minimumStapleItemCount: Int?,
    val missingRequirements: List<StapleWatchPolicyDraftRequirement>,
    val missingRequirementLabels: List<String>,
    val notice: String?,
    val continueAction: StapleWatchPolicyHandoffUiAction?,
    val continueActionLabel: String?
) {
    init {
        require(headline.isNotBlank())
        require(guidance.isNotBlank())
        require(currencyCode.matches(Regex("[A-Z]{3}")))
        require(currencyFractionDigits in 0..6)
        require(minimumSwitchSavingsLabel.isNotBlank())
        require(minimumSwitchSavingsUnitLabel.isNotBlank())
        require(maxAdditionalTravelLabel.isNotBlank())
        require(maxAdditionalTravelUnitLabel.isNotBlank())
        require(distanceLimitLabel.isNotBlank())
        require(maxAdditionalDistanceUnitLabel.isNotBlank())
        require(minimumStapleItemCountLabel.isNotBlank())
        require(missingRequirements.distinct().size == missingRequirements.size)
        require(missingRequirementLabels.size == missingRequirements.size)
        require(missingRequirementLabels.distinct().size == missingRequirementLabels.size)
        require(missingRequirementLabels.none(String::isBlank))
        require(notice == null || notice.isNotBlank())
        require((continueAction != null) == (continueActionLabel != null))
        require(continueActionLabel == null || continueActionLabel.isNotBlank())
        require(
            (status == StapleWatchPolicyDraftUiStatus.READY_FOR_POLICY_HANDOFF) ==
                missingRequirements.isEmpty()
        )
        require(
            (status == StapleWatchPolicyDraftUiStatus.READY_FOR_POLICY_HANDOFF) ==
                (continueAction != null)
        )
        require(
            continueAction == null || continueAction == StapleWatchPolicyHandoffUiAction.Request
        )
        require(
            (StapleWatchPolicyDraftRequirement.MINIMUM_SWITCH_SAVINGS in missingRequirements) ==
                (minimumSwitchSavingsMinorUnits == null)
        )
        require(
            (StapleWatchPolicyDraftRequirement.MAX_ADDITIONAL_TRAVEL in missingRequirements) ==
                (maxAdditionalTravelSeconds == null)
        )
        require(
            (StapleWatchPolicyDraftRequirement.MINIMUM_STAPLE_ITEM_COUNT in missingRequirements) ==
                (minimumStapleItemCount == null)
        )
        require(
            (StapleWatchPolicyDraftRequirement.DISTANCE_LIMIT_CHOICE in missingRequirements) ==
                (distanceLimitMode == StapleWatchPolicyDistanceLimitUiMode.UNANSWERED)
        )
        when (distanceLimitMode) {
            StapleWatchPolicyDistanceLimitUiMode.UNANSWERED,
            StapleWatchPolicyDistanceLimitUiMode.UNLIMITED -> {
                require(maxAdditionalDistanceMetres == null)
            }
            StapleWatchPolicyDistanceLimitUiMode.AT_MOST_METRES -> {
                require(maxAdditionalDistanceMetres != null)
            }
        }
    }
}

/**
 * Pure consumer projection from an already-finalized explicit policy draft.
 *
 * Finalization remains the only completeness/policy-construction owner. This projector reads its
 * retained draft and typed missing requirements only. It owns no text parsing, draft mutation,
 * economic evaluation, fact acquisition, persistence, Android lifecycle, or delivery behavior.
 */
object StapleWatchPolicyDraftUiProjector {

    fun project(
        finalization: StapleWatchPolicyDraftFinalization
    ): StapleWatchPolicyDraftUiState {
        val draft = finalization.draft
        val distance =
            when (val value = draft.distanceLimit) {
                StapleWatchPolicyDistanceLimitDraft.Unanswered ->
                    StapleWatchPolicyDistanceLimitUiMode.UNANSWERED to null
                StapleWatchPolicyDistanceLimitDraft.Unlimited ->
                    StapleWatchPolicyDistanceLimitUiMode.UNLIMITED to null
                is StapleWatchPolicyDistanceLimitDraft.AtMostMetres ->
                    StapleWatchPolicyDistanceLimitUiMode.AT_MOST_METRES to value.metres
            }
        val ready = finalization.finalized

        return StapleWatchPolicyDraftUiState(
            status =
                if (ready) {
                    StapleWatchPolicyDraftUiStatus.READY_FOR_POLICY_HANDOFF
                } else {
                    StapleWatchPolicyDraftUiStatus.NEEDS_POLICY_INPUT
                },
            headline = "Switch preferences",
            guidance =
                if (ready) {
                    "Review your switch rules, then continue."
                } else {
                    "Choose every switch rule before continuing."
                },
            currencyCode = draft.moneySpec.currencyCode,
            currencyFractionDigits = draft.moneySpec.fractionDigits,
            minimumSwitchSavingsLabel = "Minimum savings",
            minimumSwitchSavingsMinorUnits = draft.minimumSwitchSavingsMinorUnits,
            minimumSwitchSavingsUnitLabel = draft.moneySpec.currencyCode,
            maxAdditionalTravelLabel = "Maximum extra travel time",
            maxAdditionalTravelSeconds = draft.maxAdditionalTravelSeconds,
            maxAdditionalTravelUnitLabel = "seconds",
            distanceLimitLabel = "Maximum extra distance",
            distanceLimitMode = distance.first,
            maxAdditionalDistanceMetres = distance.second,
            maxAdditionalDistanceUnitLabel = "metres",
            minimumStapleItemCountLabel = "Minimum watched staples",
            minimumStapleItemCount = draft.minimumStapleItemCount,
            missingRequirements = finalization.missingRequirements.toList(),
            missingRequirementLabels =
                finalization.missingRequirements.map(::requirementLabel),
            notice =
                if (ready) {
                    null
                } else {
                    "Complete all switch preferences to continue."
                },
            continueAction =
                if (ready) StapleWatchPolicyHandoffUiAction.Request else null,
            continueActionLabel = if (ready) "Continue" else null
        )
    }

    private fun requirementLabel(requirement: StapleWatchPolicyDraftRequirement): String =
        when (requirement) {
            StapleWatchPolicyDraftRequirement.MINIMUM_SWITCH_SAVINGS ->
                "Minimum savings"
            StapleWatchPolicyDraftRequirement.MAX_ADDITIONAL_TRAVEL ->
                "Maximum extra travel time"
            StapleWatchPolicyDraftRequirement.DISTANCE_LIMIT_CHOICE ->
                "Maximum extra distance (or no limit)"
            StapleWatchPolicyDraftRequirement.MINIMUM_STAPLE_ITEM_COUNT ->
                "Minimum watched staples"
        }
}

/** Narrow target for any replaceable physical Staple Watch policy renderer. */
fun interface StapleWatchPolicyDraftSurfaceRenderer {
    fun render(state: StapleWatchPolicyDraftUiState)
}

/** Presents already-finalized policy-draft state without exposing the policy object itself. */
class StapleWatchPolicyDraftSurfacePresenter(
    private val renderer: StapleWatchPolicyDraftSurfaceRenderer
) {
    fun render(finalization: StapleWatchPolicyDraftFinalization) {
        renderer.render(StapleWatchPolicyDraftUiProjector.project(finalization))
    }
}
