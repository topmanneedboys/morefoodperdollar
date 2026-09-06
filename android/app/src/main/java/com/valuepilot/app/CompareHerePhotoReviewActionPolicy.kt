package com.valuepilot.app

/**
 * Chooses which explicit OCR-review action should be primary.
 *
 * Complete detected details are still ordinary editable text and remain unconfirmed. They may
 * become the primary convenience action only when every presented row has a safe draft; mixed or
 * incomplete recognition keeps the conservative names-only action primary.
 */
internal object CompareHerePhotoReviewActionPolicy {
    fun primaryUsesDetectedDetails(
        presentations: List<CompareHerePhotoSuggestionPresentation>
    ): Boolean =
        presentations.isNotEmpty() && presentations.all { it.editorPrefill != null }

    fun hasDetectedDetailsAlternative(
        presentations: List<CompareHerePhotoSuggestionPresentation>
    ): Boolean = presentations.any { it.editorPrefill != null }

    /**
     * A complete detected-details commit only needs visual review before the shopper chooses the
     * existing comparison action, so opening the keyboard would cover useful controls. Raw or
     * mixed commits still need immediate editing and keep the existing keyboard affordance.
     */
    fun shouldOpenKeyboardAfterCommit(
        presentations: List<CompareHerePhotoSuggestionPresentation>,
        selected: BooleanArray,
        useDetectedDetails: Boolean
    ): Boolean {
        if (!useDetectedDetails || presentations.size != selected.size) return true
        return presentations.indices.any { index ->
            selected[index] && presentations[index].editorPrefill == null
        }
    }
}
