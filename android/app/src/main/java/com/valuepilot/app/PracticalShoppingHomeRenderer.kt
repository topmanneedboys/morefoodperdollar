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

/**
 * Describes whether Home can safely read the shopper's device-only comparison history.
 *
 * This is deliberately not the storage-layer issue enum. Home only needs the smallest
 * presentation fact and must not expose persistence details or turn a read failure into a
 * shopping decision.
 */
enum class PracticalShoppingHomePrivateMemoryStatus {
    AVAILABLE,
    UNAVAILABLE
}

data class PracticalShoppingHomeItemRenderState(
    val key: ShoppingItemKey,
    val name: String,
    val detail: String,
    val requestDetailsSummary: String,
    val requestDetailsNotice: String?,
    val requestDetailsActionLabel: String,
    val storeAssignment: String? = null,
    val priceCoverageNotice: String? = null,
    val personalHistoryNotice: String? = null
) {
    init {
        require(key.value.isNotBlank())
        require(name.isNotBlank())
        require(detail.isNotBlank())
        require(storeAssignment == null || storeAssignment.isNotBlank())
        require(priceCoverageNotice == null || priceCoverageNotice.isNotBlank())
        require(personalHistoryNotice == null || personalHistoryNotice.isNotBlank())
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
    val choices: List<PracticalShoppingHomeExtraStopSavingsChoiceRenderState>,
    /** Explains when the saved rule is not yet evaluated for the current result. */
    val notice: String? = null
) {
    init {
        require(summary.isNotBlank())
        require(prompt.isNotBlank())
        require(choices.isNotEmpty())
        require(choices.count { it.selected } == 1)
        require(notice == null || notice.isNotBlank())
        require(visible || notice == null)
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
    val sampleNotice: String,
    val privateMemorySummary: String? = null,
    /** Whether Home may expose the contextual route into the private history screen. */
    val privateMemoryReviewActionVisible: Boolean = false,
    val privateMemoryStatus: PracticalShoppingHomePrivateMemoryStatus =
        PracticalShoppingHomePrivateMemoryStatus.AVAILABLE,
    /**
     * Renderer-only aggregate for a no-coverage result. A projected primary plan already
     * carries its own exact coverage text, so this remains null whenever one exists.
     */
    val noCoverageSummary: String? = null
) {
    init {
        require(queryCharacterLimit > 0)
        require(query.length <= queryCharacterLimit + 1)
        require(message == null || message.isNotBlank())
        require(unknownItems.none(String::isBlank))
        require(sampleNotice.isNotBlank())
        require(privateMemorySummary == null || privateMemorySummary.isNotBlank())
        require(noCoverageSummary == null || noCoverageSummary.isNotBlank())
        require(
            !privateMemoryReviewActionVisible ||
                privateMemoryStatus == PracticalShoppingHomePrivateMemoryStatus.UNAVAILABLE ||
                (privateMemoryStatus == PracticalShoppingHomePrivateMemoryStatus.AVAILABLE &&
                    privateMemorySummary != null)
        )
    }
}

object PracticalShoppingHomeRenderer {

    fun render(source: LocalSamplePracticalShoppingDemo.UiState): PracticalShoppingHomeRenderState =
        render(source, requestDetails = null, privateMemory = null)

    internal fun render(
        source: LocalSamplePracticalShoppingDemo.UiState,
        requestDetails: ShoppingRequestDetails?,
        privateMemory: CompareHerePrivatePriceMemoryState? = null,
        privateMemoryStatus: PracticalShoppingHomePrivateMemoryStatus =
            PracticalShoppingHomePrivateMemoryStatus.AVAILABLE
    ): PracticalShoppingHomeRenderState {
        // A failed read is not permission to display stale or caller-provided history. Keep the
        // status visible while suppressing every history-derived row notice until recovery.
        val usablePrivateMemory =
            if (privateMemoryStatus == PracticalShoppingHomePrivateMemoryStatus.AVAILABLE) {
                privateMemory
            } else {
                null
            }
        val storeAssignments =
            source.result
                ?.itemStoreAssignments
                ?.associate { assignment -> assignment.itemKey to assignment.storeName }
        val privateMemorySummary =
            usablePrivateMemory?.let { memory ->
                PracticalShoppingHomePersonalHistory.summaryFor(
                    memory = memory,
                    requestedItemNames = source.items.map { item -> item.name }
                )
            }
        val extraStopSettingsNotice =
            source.result?.primary
                ?.missingItemsText
                ?.let {
                    "Another stop is not evaluated until every requested item has a usable price."
                }

        return PracticalShoppingHomeRenderState(
            query = source.query,
            queryCharacterLimit = LocalSamplePracticalShoppingDemo.MAX_QUERY_CHARACTERS,
            submitEnabled =
                source.query.isNotBlank() &&
                    source.status !in setOf(
                        LocalSamplePracticalShoppingDemo.Status.QUERY_TOO_LONG,
                        LocalSamplePracticalShoppingDemo.Status.NEEDS_REFINEMENT
                    ),
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
                    val storeAssignment = storeAssignments?.get(item.key)
                    PracticalShoppingHomeItemRenderState(
                        key = item.key,
                        name = item.name,
                        detail = item.detail,
                        storeAssignment = storeAssignment,
                        priceCoverageNotice =
                            if (source.result != null && storeAssignment == null) {
                                "No usable price yet — not included in this plan."
                            } else {
                                null
                            },
                        personalHistoryNotice =
                            usablePrivateMemory?.let { memory ->
                                PracticalShoppingHomePersonalHistory.noticeFor(
                                    itemDisplayName = item.name,
                                    memory = memory
                                )
                            },
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
                    // A no-coverage decision has a result headline but no store plan. The
                    // extra-stop rule is meaningful only once a primary recommendation exists.
                    visible = source.result?.primary != null,
                    summary =
                        "Extra-stop rule · Save at least " +
                            source.extraStopMinimumSavingsChoice.label +
                            if (extraStopSettingsNotice == null) {
                                ""
                            } else {
                                " · Not evaluated yet"
                            },
                    prompt = "Minimum savings before adding another store",
                    choices =
                        LocalSamplePracticalShoppingDemo.ExtraStopMinimumSavingsChoice.entries
                            .map { choice ->
                                PracticalShoppingHomeExtraStopSavingsChoiceRenderState(
                                    choice = choice,
                                    label = choice.label,
                                    selected = choice == source.extraStopMinimumSavingsChoice
                                )
                            },
                    notice = extraStopSettingsNotice
                ),
            sampleNotice = source.sampleNotice,
            privateMemorySummary = privateMemorySummary,
            // A readable nonempty history gets a review action. An unreadable
            // store gets the same route so the shopper can open Compare Here
            // and use its existing clear/recovery controls; no unreadable rows
            // or history-derived facts are exposed on Home.
            privateMemoryReviewActionVisible =
                privateMemoryStatus == PracticalShoppingHomePrivateMemoryStatus.UNAVAILABLE ||
                    privateMemorySummary != null,
            privateMemoryStatus = privateMemoryStatus,
            noCoverageSummary = noCoverageSummary(source)
        )
    }

    /**
     * The shared projector has no primary plan when no candidate covers any item. In that one
     * state, give Home a compact aggregate that complements (rather than replaces) the per-item
     * unknown-price notices. This is presentation only: the no-coverage decision is already
     * encoded by [source.result] and no price is inferred here.
     */
    private fun noCoverageSummary(
        source: LocalSamplePracticalShoppingDemo.UiState
    ): String? {
        if (source.result == null || source.result.primary != null || source.items.isEmpty()) {
            return null
        }
        val itemCount = source.items.size
        return "0 of $itemCount ${if (itemCount == 1) "item" else "items"} priced yet."
    }
}
