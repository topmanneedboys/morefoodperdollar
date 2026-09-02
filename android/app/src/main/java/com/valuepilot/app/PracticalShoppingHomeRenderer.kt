package com.valuepilot.app

import com.valuepilot.core.ShoppingItemKey
import com.valuepilot.core.ShoppingRequestDetails

/**
 * Android-facing immutable presentation for the Practical Shopping Home proof.
 *
 * This layer may decide only presentation mechanics such as whether the submit
 * control is enabled or which message emphasis to use. Shopping resolution,
 * store ranking, basket arithmetic and second-stop decisions stay upstream in
 * the verified controller/shared-core/projector boundary.
 */
enum class PracticalShoppingHomeMessageTone {
    NEUTRAL,
    ACTION_REQUIRED,
    ERROR
}

data class PracticalShoppingHomeItemRenderState(
    val key: ShoppingItemKey,
    val name: String,
    val detail: String,
    val requestDetailsSummary: String,
    val requestDetailsNotice: String?,
    val requestDetailsActionLabel: String,
    val storeAssignment: String? = null
) {
    init {
        require(key.value.isNotBlank())
        require(name.isNotBlank())
        require(detail.isNotBlank())
        require(storeAssignment == null || storeAssignment.isNotBlank())
        require(requestDetailsSummary.isNotBlank())
        require(requestDetailsNotice == null || requestDetailsNotice.isNotBlank())
        require(requestDetailsActionLabel.isNotBlank())
    }
}

data class PracticalShoppingHomeChickenChoiceRenderState(
    val choice: LocalSamplePracticalShoppingDemo.ChickenChoice,
    val label: String
) {
    init {
        require(label.isNotBlank())
    }
}

data class PracticalShoppingHomeRefinementRenderState(
    val prompt: String,
    val choices: List<PracticalShoppingHomeChickenChoiceRenderState>
) {
    init {
        require(prompt.isNotBlank())
        require(choices.isNotEmpty())
    }
}

data class PracticalShoppingHomeExtraStopSavingsChoiceRenderState(
    val choice: LocalSamplePracticalShoppingDemo.ExtraStopMinimumSavingsChoice,
    val label: String,
    val selected: Boolean
) {
    init {
        require(label.isNotBlank())
    }
}

data class PracticalShoppingHomeExtraStopSettingsRenderState(
    val visible: Boolean,
    val summary: String,
    val prompt: String,
    val choices: List<PracticalShoppingHomeExtraStopSavingsChoiceRenderState>
) {
    init {
        require(summary.isNotBlank())
        require(prompt.isNotBlank())
        require(choices.isNotEmpty())
        require(choices.count { it.selected } == 1)
    }
}

data class PracticalShoppingHomeRenderState(
    val query: String,
    val queryCharacterLimit: Int,
    val submitEnabled: Boolean,
    val message: String?,
    val messageTone: PracticalShoppingHomeMessageTone,
    val items: List<PracticalShoppingHomeItemRenderState>,
    val refinement: PracticalShoppingHomeRefinementRenderState?,
    val unknownItems: List<String>,
    val result: PracticalShoppingUiState?,
    val extraStopSettings: PracticalShoppingHomeExtraStopSettingsRenderState,
    val sampleNotice: String
) {
    init {
        require(queryCharacterLimit > 0)
        require(query.length <= queryCharacterLimit + 1)
        require(message == null || message.isNotBlank())
        require(unknownItems.none(String::isBlank))
        require(sampleNotice.isNotBlank())
    }
}

object PracticalShoppingHomeRenderer {

    fun render(source: LocalSamplePracticalShoppingDemo.UiState): PracticalShoppingHomeRenderState =
        render(source, requestDetails = null)

    fun render(
        source: LocalSamplePracticalShoppingDemo.UiState,
        requestDetails: ShoppingRequestDetails?
    ): PracticalShoppingHomeRenderState {
        val storeAssignments =
            source.result
                ?.itemStoreAssignments
                ?.associate { assignment -> assignment.itemKey to assignment.storeName }

        return PracticalShoppingHomeRenderState(
            query = source.query,
            queryCharacterLimit = LocalSamplePracticalShoppingDemo.MAX_QUERY_CHARACTERS,
            submitEnabled =
                source.query.isNotBlank() &&
                    source.status != LocalSamplePracticalShoppingDemo.Status.QUERY_TOO_LONG,
            message = source.message,
            messageTone =
                when (source.status) {
                    LocalSamplePracticalShoppingDemo.Status.IDLE ->
                        PracticalShoppingHomeMessageTone.NEUTRAL

                    LocalSamplePracticalShoppingDemo.Status.QUERY_TOO_LONG ->
                        PracticalShoppingHomeMessageTone.ERROR

                    LocalSamplePracticalShoppingDemo.Status.NEEDS_REFINEMENT ->
                        PracticalShoppingHomeMessageTone.ACTION_REQUIRED

                    LocalSamplePracticalShoppingDemo.Status.RESULT ->
                        PracticalShoppingHomeMessageTone.NEUTRAL
                },
            items =
                source.items.map { item ->
                    val itemDetails = requestDetails?.detailFor(item.key)
                    PracticalShoppingHomeItemRenderState(
                        key = item.key,
                        name = item.name,
                        detail = item.detail,
                        storeAssignment = storeAssignments?.get(item.key),
                        requestDetailsSummary =
                            PracticalShoppingHomeItemDetailsPresentation.summary(itemDetails),
                        requestDetailsNotice =
                            itemDetails?.let {
                                "Preference only — not applied to this sample plan."
                            },
                        requestDetailsActionLabel =
                            PracticalShoppingHomeItemDetailsPresentation.actionLabel(itemDetails)
                    )
                },
            refinement =
                source.chickenClarification?.let { refinement ->
                    PracticalShoppingHomeRefinementRenderState(
                        prompt = refinement.prompt,
                        choices =
                            refinement.choices.map { choice ->
                                PracticalShoppingHomeChickenChoiceRenderState(
                                    choice = choice,
                                    label = choice.label
                                )
                            }
                    )
                },
            unknownItems = source.unknownItems.toList(),
            // Already-projected shopping decision is passed through unchanged.
            result = source.result,
            extraStopSettings =
                PracticalShoppingHomeExtraStopSettingsRenderState(
                    visible = source.result != null,
                    summary =
                        "Extra-stop rule · Save at least " +
                            source.extraStopMinimumSavingsChoice.label,
                    prompt = "Minimum savings before adding another store",
                    choices =
                        LocalSamplePracticalShoppingDemo.ExtraStopMinimumSavingsChoice.entries
                            .map { choice ->
                                PracticalShoppingHomeExtraStopSavingsChoiceRenderState(
                                    choice = choice,
                                    label = choice.label,
                                    selected = choice == source.extraStopMinimumSavingsChoice
                                )
                            }
                ),
            sampleNotice = source.sampleNotice
        )
    }
}
