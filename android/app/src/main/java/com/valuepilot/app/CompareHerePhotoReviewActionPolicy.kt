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
}
