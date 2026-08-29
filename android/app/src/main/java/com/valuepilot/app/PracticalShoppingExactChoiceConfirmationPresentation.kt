package com.valuepilot.app

import com.valuepilot.core.PracticalShoppingProductIdentityCandidate
import com.valuepilot.core.PracticalShoppingProductIntentRelationship
import com.valuepilot.core.PracticalShoppingStoreIdentityCandidate
import com.valuepilot.core.PracticalShoppingStoreIdentityRelationship
import com.valuepilot.core.ShoppingItemKey
import com.valuepilot.core.ShoppingStoreKey

private const val MAX_EXACT_CHOICE_PRESENTATION_OPTIONS = 32
private const val MAX_EXACT_CHOICE_CONSUMER_LABEL_LENGTH = 160
private const val SOURCE_REVALIDATION_CANDIDATE_ID = "exact-choice-presentation-validation"

data class PracticalShoppingOpenFoodFactsConfirmationOption(
    val candidate: PracticalShoppingProductIdentityCandidate,
    val row: OpenFoodFactsImportedProduct
)

data class PracticalShoppingOpenStreetMapConfirmationOption(
    val candidate: PracticalShoppingStoreIdentityCandidate,
    val row: OpenStreetMapPracticalShoppingStoreDisplayRecord
)

enum class PracticalShoppingExactChoicePresentationIssue {
    LOGICAL_KEY_MISMATCH,
    RELATIONSHIP_NOT_SELECTABLE,
    SOURCE_REVALIDATION_FAILED,
    SOURCE_IDENTITY_MISMATCH,
    DISPLAY_NAME_UNAVAILABLE
}

internal data class PracticalShoppingExactChoiceRejectedOption(
    val candidateId: String,
    val issues: Set<PracticalShoppingExactChoicePresentationIssue>
) {
    init {
        require(candidateId.isNotBlank())
        require(issues.isNotEmpty())
    }
}

data class PracticalShoppingSelectProductChoiceAction(
    val presentationGeneration: Long,
    val optionId: Int
) {
    init {
        require(presentationGeneration > 0L)
        require(optionId in 1..MAX_EXACT_CHOICE_PRESENTATION_OPTIONS)
    }
}

data class PracticalShoppingSelectStoreChoiceAction(
    val presentationGeneration: Long,
    val optionId: Int
) {
    init {
        require(presentationGeneration > 0L)
        require(optionId in 1..MAX_EXACT_CHOICE_PRESENTATION_OPTIONS)
    }
}

data class PracticalShoppingProductConfirmationUiRow(
    val title: String,
    val supportingText: String,
    val action: PracticalShoppingSelectProductChoiceAction
) {
    init {
        requireConsumerLabel(title)
        require(supportingText.isNotBlank())
    }
}

data class PracticalShoppingStoreConfirmationUiRow(
    val title: String,
    val supportingText: String,
    val action: PracticalShoppingSelectStoreChoiceAction
) {
    init {
        requireConsumerLabel(title)
        require(supportingText.isNotBlank())
    }
}

data class PracticalShoppingProductConfirmationUiState(
    val headline: String,
    val guidance: String,
    val rows: List<PracticalShoppingProductConfirmationUiRow>,
    val omittedChoiceCount: Int,
    val notice: String?,
    val emptyMessage: String?
) {
    init {
        require(headline.isNotBlank())
        require(guidance.isNotBlank())
        require(rows.size <= MAX_EXACT_CHOICE_PRESENTATION_OPTIONS)
        require(omittedChoiceCount >= 0)
        require(notice == null || notice.isNotBlank())
        require((emptyMessage != null) == rows.isEmpty())
    }
}

data class PracticalShoppingStoreConfirmationUiState(
    val headline: String,
    val guidance: String,
    val rows: List<PracticalShoppingStoreConfirmationUiRow>,
    val omittedChoiceCount: Int,
    val notice: String?,
    val emptyMessage: String?
) {
    init {
        require(headline.isNotBlank())
        require(guidance.isNotBlank())
        require(rows.size <= MAX_EXACT_CHOICE_PRESENTATION_OPTIONS)
        require(omittedChoiceCount >= 0)
        require(notice == null || notice.isNotBlank())
        require((emptyMessage != null) == rows.isEmpty())
    }
}

enum class PracticalShoppingExactChoiceSelectionIssue {
    STALE_OR_UNKNOWN_ACTION,
    INVALID_CONFIRMATION_CANDIDATE_ID,
    CONFIRMATION_REJECTED
}

data class PracticalShoppingConfirmedProductChoice(
    val confirmedCandidate: PracticalShoppingProductIdentityCandidate,
    val rememberRequest: PracticalShoppingRememberConfirmedChoiceRequest.OpenFoodFactsProduct
)

data class PracticalShoppingConfirmedStoreChoice(
    val confirmedCandidate: PracticalShoppingStoreIdentityCandidate,
    val rememberRequest: PracticalShoppingRememberConfirmedChoiceRequest.OpenStreetMapStore
)

data class PracticalShoppingProductChoiceSelectionResult(
    val selection: PracticalShoppingConfirmedProductChoice?,
    val issue: PracticalShoppingExactChoiceSelectionIssue? = null,
    val confirmationFailures: Set<PracticalShoppingExactProductConfirmationFailure> = emptySet()
) {
    init {
        require((selection != null) == (issue == null))
        require(
            issue == PracticalShoppingExactChoiceSelectionIssue.CONFIRMATION_REJECTED ||
                confirmationFailures.isEmpty()
        )
        require(
            issue != PracticalShoppingExactChoiceSelectionIssue.CONFIRMATION_REJECTED ||
                confirmationFailures.isNotEmpty()
        )
    }
}

data class PracticalShoppingStoreChoiceSelectionResult(
    val selection: PracticalShoppingConfirmedStoreChoice?,
    val issue: PracticalShoppingExactChoiceSelectionIssue? = null,
    val confirmationFailures: Set<PracticalShoppingExactStoreConfirmationFailure> = emptySet()
) {
    init {
        require((selection != null) == (issue == null))
        require(
            issue == PracticalShoppingExactChoiceSelectionIssue.CONFIRMATION_REJECTED ||
                confirmationFailures.isEmpty()
        )
        require(
            issue != PracticalShoppingExactChoiceSelectionIssue.CONFIRMATION_REJECTED ||
                confirmationFailures.isNotEmpty()
        )
    }
}

internal data class BoundProductConfirmationOption(
    val candidate: PracticalShoppingProductIdentityCandidate,
    val row: OpenFoodFactsImportedProduct
)

internal data class BoundStoreConfirmationOption(
    val candidate: PracticalShoppingStoreIdentityCandidate,
    val row: OpenStreetMapPracticalShoppingStoreDisplayRecord
)

/**
 * Consumer state plus private exact suggestion lookup.
 *
 * A renderer receives [state] only. Its action contains only a numeric presentation generation
 * and bounded option number. Selection resolves that opaque pair through this projection and
 * delegates to the already-verified exact-product confirmation adapter. Product identity is
 * never reconstructed from title text.
 */
class PracticalShoppingProductConfirmationProjection internal constructor(
    val state: PracticalShoppingProductConfirmationUiState,
    internal val rejectedOptions: List<PracticalShoppingExactChoiceRejectedOption>,
    private val presentationGeneration: Long,
    private val itemKey: ShoppingItemKey,
    private val boundOptions: Map<Int, BoundProductConfirmationOption>
) {
    fun confirm(
        action: PracticalShoppingSelectProductChoiceAction,
        confirmedCandidateId: String
    ): PracticalShoppingProductChoiceSelectionResult {
        if (!validConfirmationCandidateId(confirmedCandidateId)) {
            return PracticalShoppingProductChoiceSelectionResult(
                selection = null,
                issue = PracticalShoppingExactChoiceSelectionIssue.INVALID_CONFIRMATION_CANDIDATE_ID
            )
        }
        if (action.presentationGeneration != presentationGeneration) {
            return staleProductSelection()
        }
        val bound = boundOptions[action.optionId] ?: return staleProductSelection()

        val confirmed =
            PracticalShoppingExactProductConfirmationAdapter.confirmSelection(
                itemKey = itemKey,
                selectedCandidate = bound.candidate,
                candidateId = confirmedCandidateId
            )
        val confirmedCandidate = confirmed.candidate
            ?: return PracticalShoppingProductChoiceSelectionResult(
                selection = null,
                issue = PracticalShoppingExactChoiceSelectionIssue.CONFIRMATION_REJECTED,
                confirmationFailures = confirmed.failures
            )

        return PracticalShoppingProductChoiceSelectionResult(
            selection =
                PracticalShoppingConfirmedProductChoice(
                    confirmedCandidate = confirmedCandidate,
                    rememberRequest =
                        PracticalShoppingRememberConfirmedChoiceRequest.OpenFoodFactsProduct(
                            confirmedCandidate = confirmedCandidate,
                            row = bound.row
                        )
                )
        )
    }
}

/** Same opaque-selection boundary for one logical store suggestion set. */
class PracticalShoppingStoreConfirmationProjection internal constructor(
    val state: PracticalShoppingStoreConfirmationUiState,
    internal val rejectedOptions: List<PracticalShoppingExactChoiceRejectedOption>,
    private val presentationGeneration: Long,
    private val storeKey: ShoppingStoreKey,
    private val boundOptions: Map<Int, BoundStoreConfirmationOption>
) {
    fun confirm(
        action: PracticalShoppingSelectStoreChoiceAction,
        confirmedCandidateId: String
    ): PracticalShoppingStoreChoiceSelectionResult {
        if (!validConfirmationCandidateId(confirmedCandidateId)) {
            return PracticalShoppingStoreChoiceSelectionResult(
                selection = null,
                issue = PracticalShoppingExactChoiceSelectionIssue.INVALID_CONFIRMATION_CANDIDATE_ID
            )
        }
        if (action.presentationGeneration != presentationGeneration) {
            return staleStoreSelection()
        }
        val bound = boundOptions[action.optionId] ?: return staleStoreSelection()

        val confirmed =
            PracticalShoppingExactStoreConfirmationAdapter.confirmSelection(
                storeKey = storeKey,
                selectedCandidate = bound.candidate,
                candidateId = confirmedCandidateId
            )
        val confirmedCandidate = confirmed.candidate
            ?: return PracticalShoppingStoreChoiceSelectionResult(
                selection = null,
                issue = PracticalShoppingExactChoiceSelectionIssue.CONFIRMATION_REJECTED,
                confirmationFailures = confirmed.failures
            )

        return PracticalShoppingStoreChoiceSelectionResult(
            selection =
                PracticalShoppingConfirmedStoreChoice(
                    confirmedCandidate = confirmedCandidate,
                    rememberRequest =
                        PracticalShoppingRememberConfirmedChoiceRequest.OpenStreetMapStore(
                            confirmedCandidate = confirmedCandidate,
                            row = bound.row
                        )
                )
        )
    }
}

/**
 * Pure source-backed recognition projector for explicit exact-choice confirmation.
 *
 * This object does not discover products/stores. It accepts candidate + source-row pairs that
 * already exist, re-runs the corresponding verified source adapter, and exposes a human label
 * only when the revalidated source candidate is the same suggestion (ignoring only candidateId).
 * Names remain recognition metadata: they never establish or modify product/store identity.
 */
object PracticalShoppingExactChoiceConfirmationProjector {

    fun projectOpenFoodFactsProducts(
        presentationGeneration: Long,
        itemKey: ShoppingItemKey,
        options: List<PracticalShoppingOpenFoodFactsConfirmationOption>
    ): PracticalShoppingProductConfirmationProjection {
        require(presentationGeneration > 0L)
        require(options.size <= MAX_EXACT_CHOICE_PRESENTATION_OPTIONS)
        require(options.map { it.candidate.candidateId }.distinct().size == options.size) {
            "Product confirmation candidate ids must be unique"
        }

        val rejected = mutableListOf<PracticalShoppingExactChoiceRejectedOption>()
        val bound = linkedMapOf<Int, BoundProductConfirmationOption>()
        val rows = mutableListOf<PracticalShoppingProductConfirmationUiRow>()

        options.forEach { option ->
            val issues = productIssues(itemKey, option)
            val label =
                if (issues.isEmpty()) {
                    safeProductLabel(option.row.productName, option.candidate)
                } else {
                    null
                }
            val finalIssues =
                if (issues.isEmpty() && label == null) {
                    setOf(PracticalShoppingExactChoicePresentationIssue.DISPLAY_NAME_UNAVAILABLE)
                } else {
                    issues
                }

            if (finalIssues.isNotEmpty()) {
                rejected +=
                    PracticalShoppingExactChoiceRejectedOption(
                        candidateId = option.candidate.candidateId,
                        issues = finalIssues
                    )
            } else {
                val optionId = rows.size + 1
                bound[optionId] = BoundProductConfirmationOption(option.candidate, option.row)
                rows +=
                    PracticalShoppingProductConfirmationUiRow(
                        title = requireNotNull(label),
                        supportingText = "Catalog suggestion — confirm the exact product you mean",
                        action =
                            PracticalShoppingSelectProductChoiceAction(
                                presentationGeneration = presentationGeneration,
                                optionId = optionId
                            )
                    )
            }
        }

        return PracticalShoppingProductConfirmationProjection(
            state =
                PracticalShoppingProductConfirmationUiState(
                    headline = "Choose exact product",
                    guidance = "Select only if this is the exact packaged product you mean.",
                    rows = rows.toList(),
                    omittedChoiceCount = rejected.size,
                    notice = omittedNotice(rejected.size),
                    emptyMessage =
                        if (rows.isEmpty()) {
                            "No product choices can be shown safely yet."
                        } else {
                            null
                        }
                ),
            rejectedOptions = rejected.toList(),
            presentationGeneration = presentationGeneration,
            itemKey = itemKey,
            boundOptions = bound.toMap()
        )
    }

    fun projectOpenStreetMapStores(
        presentationGeneration: Long,
        storeKey: ShoppingStoreKey,
        options: List<PracticalShoppingOpenStreetMapConfirmationOption>
    ): PracticalShoppingStoreConfirmationProjection {
        require(presentationGeneration > 0L)
        require(options.size <= MAX_EXACT_CHOICE_PRESENTATION_OPTIONS)
        require(options.map { it.candidate.candidateId }.distinct().size == options.size) {
            "Store confirmation candidate ids must be unique"
        }

        val rejected = mutableListOf<PracticalShoppingExactChoiceRejectedOption>()
        val bound = linkedMapOf<Int, BoundStoreConfirmationOption>()
        val rows = mutableListOf<PracticalShoppingStoreConfirmationUiRow>()

        options.forEach { option ->
            val issues = storeIssues(storeKey, option)
            val label =
                if (issues.isEmpty()) {
                    safeStoreLabel(option.row.name, option.candidate)
                } else {
                    null
                }
            val finalIssues =
                if (issues.isEmpty() && label == null) {
                    setOf(PracticalShoppingExactChoicePresentationIssue.DISPLAY_NAME_UNAVAILABLE)
                } else {
                    issues
                }

            if (finalIssues.isNotEmpty()) {
                rejected +=
                    PracticalShoppingExactChoiceRejectedOption(
                        candidateId = option.candidate.candidateId,
                        issues = finalIssues
                    )
            } else {
                val optionId = rows.size + 1
                bound[optionId] = BoundStoreConfirmationOption(option.candidate, option.row)
                rows +=
                    PracticalShoppingStoreConfirmationUiRow(
                        title = requireNotNull(label),
                        supportingText = "Store suggestion — confirm the exact location you mean",
                        action =
                            PracticalShoppingSelectStoreChoiceAction(
                                presentationGeneration = presentationGeneration,
                                optionId = optionId
                            )
                    )
            }
        }

        return PracticalShoppingStoreConfirmationProjection(
            state =
                PracticalShoppingStoreConfirmationUiState(
                    headline = "Choose exact store",
                    guidance = "Select only if this is the exact store location you mean.",
                    rows = rows.toList(),
                    omittedChoiceCount = rejected.size,
                    notice = omittedNotice(rejected.size),
                    emptyMessage =
                        if (rows.isEmpty()) {
                            "No store choices can be shown safely yet."
                        } else {
                            null
                        }
                ),
            rejectedOptions = rejected.toList(),
            presentationGeneration = presentationGeneration,
            storeKey = storeKey,
            boundOptions = bound.toMap()
        )
    }

    private fun productIssues(
        itemKey: ShoppingItemKey,
        option: PracticalShoppingOpenFoodFactsConfirmationOption
    ): Set<PracticalShoppingExactChoicePresentationIssue> {
        val issues = linkedSetOf<PracticalShoppingExactChoicePresentationIssue>()
        if (option.candidate.itemKey != itemKey) {
            issues += PracticalShoppingExactChoicePresentationIssue.LOGICAL_KEY_MISMATCH
        }
        if (option.candidate.relationship != PracticalShoppingProductIntentRelationship.CATALOG_SUGGESTION) {
            issues += PracticalShoppingExactChoicePresentationIssue.RELATIONSHIP_NOT_SELECTABLE
        }

        val source =
            OpenFoodFactsPracticalShoppingIdentityAdapter.catalogSuggestion(
                itemKey = itemKey,
                row = option.row,
                candidateId = SOURCE_REVALIDATION_CANDIDATE_ID
            ).candidate
        if (source == null) {
            issues += PracticalShoppingExactChoicePresentationIssue.SOURCE_REVALIDATION_FAILED
        } else if (!sameProductSuggestion(option.candidate, source)) {
            issues += PracticalShoppingExactChoicePresentationIssue.SOURCE_IDENTITY_MISMATCH
        }
        return issues
    }

    private fun storeIssues(
        storeKey: ShoppingStoreKey,
        option: PracticalShoppingOpenStreetMapConfirmationOption
    ): Set<PracticalShoppingExactChoicePresentationIssue> {
        val issues = linkedSetOf<PracticalShoppingExactChoicePresentationIssue>()
        if (option.candidate.storeKey != storeKey) {
            issues += PracticalShoppingExactChoicePresentationIssue.LOGICAL_KEY_MISMATCH
        }
        if (option.candidate.relationship != PracticalShoppingStoreIdentityRelationship.SOURCE_LOCATION_SUGGESTION) {
            issues += PracticalShoppingExactChoicePresentationIssue.RELATIONSHIP_NOT_SELECTABLE
        }

        val source =
            OpenStreetMapPracticalShoppingStoreSuggestionAdapter.locationSuggestion(
                storeKey = storeKey,
                row = option.row.identity,
                candidateId = SOURCE_REVALIDATION_CANDIDATE_ID
            ).candidate
        if (source == null) {
            issues += PracticalShoppingExactChoicePresentationIssue.SOURCE_REVALIDATION_FAILED
        } else if (!sameStoreSuggestion(option.candidate, source)) {
            issues += PracticalShoppingExactChoicePresentationIssue.SOURCE_IDENTITY_MISMATCH
        }
        return issues
    }

    private fun sameProductSuggestion(
        left: PracticalShoppingProductIdentityCandidate,
        right: PracticalShoppingProductIdentityCandidate
    ): Boolean =
        left.itemKey == right.itemKey &&
            left.providerId == right.providerId &&
            left.sourceIdentity == right.sourceIdentity &&
            left.relationship == right.relationship &&
            left.dataset == right.dataset

    private fun sameStoreSuggestion(
        left: PracticalShoppingStoreIdentityCandidate,
        right: PracticalShoppingStoreIdentityCandidate
    ): Boolean =
        left.storeKey == right.storeKey &&
            left.scope == right.scope &&
            left.relationship == right.relationship &&
            left.providerId == right.providerId &&
            left.dataset == right.dataset

    private fun safeProductLabel(
        raw: String?,
        candidate: PracticalShoppingProductIdentityCandidate
    ): String? =
        safeLabel(
            raw = raw,
            forbiddenIdentifiers =
                listOfNotNull(
                    candidate.providerId.value,
                    candidate.sourceIdentity.providerItemId,
                    candidate.sourceIdentity.sku,
                    candidate.sourceIdentity.gtin,
                    candidate.dataset?.id
                )
        )

    private fun safeStoreLabel(
        raw: String?,
        candidate: PracticalShoppingStoreIdentityCandidate
    ): String? =
        safeLabel(
            raw = raw,
            forbiddenIdentifiers =
                listOfNotNull(
                    candidate.scope.merchantKey,
                    candidate.scope.locationKey,
                    candidate.scope.commerceChannelKey,
                    candidate.providerId?.value,
                    candidate.dataset?.id
                ) + prefixedIdentitySuffixes(candidate.scope.merchantKey, candidate.scope.locationKey)
        )

    private fun safeLabel(
        raw: String?,
        forbiddenIdentifiers: List<String>
    ): String? {
        val value = raw?.trim() ?: return null
        if (value.isBlank() || value.length > MAX_EXACT_CHOICE_CONSUMER_LABEL_LENGTH) return null
        if (value.any { character -> Character.isISOControl(character.code) }) return null

        val forbidden =
            forbiddenIdentifiers
                .map { identifier -> identifier.trim() }
                .filter { identifier -> identifier.isNotBlank() }
                .distinct()
        if (
            forbidden.any { identifier ->
                value.equals(identifier, ignoreCase = true) ||
                    (identifier.length >= 6 && value.contains(identifier, ignoreCase = true))
            }
        ) {
            return null
        }
        return value
    }

    private fun prefixedIdentitySuffixes(vararg values: String?): List<String> =
        values
            .filterNotNull()
            .mapNotNull { value ->
                value.substringAfterLast(':', missingDelimiterValue = "")
                    .takeIf { suffix -> suffix.length >= 6 }
            }

    private fun omittedNotice(count: Int): String? =
        when (count) {
            0 -> null
            1 -> "1 choice could not be shown safely."
            else -> "$count choices could not be shown safely."
        }
}

private fun staleProductSelection(): PracticalShoppingProductChoiceSelectionResult =
    PracticalShoppingProductChoiceSelectionResult(
        selection = null,
        issue = PracticalShoppingExactChoiceSelectionIssue.STALE_OR_UNKNOWN_ACTION
    )

private fun staleStoreSelection(): PracticalShoppingStoreChoiceSelectionResult =
    PracticalShoppingStoreChoiceSelectionResult(
        selection = null,
        issue = PracticalShoppingExactChoiceSelectionIssue.STALE_OR_UNKNOWN_ACTION
    )

private fun validConfirmationCandidateId(value: String): Boolean =
    value.isNotBlank() && value.length <= 240

private fun requireConsumerLabel(value: String) {
    require(value.isNotBlank())
    require(value.length <= MAX_EXACT_CHOICE_CONSUMER_LABEL_LENGTH)
    require(value.none { character -> Character.isISOControl(character.code) })
}
