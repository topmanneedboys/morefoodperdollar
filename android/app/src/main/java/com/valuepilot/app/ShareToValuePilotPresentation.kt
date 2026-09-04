package com.valuepilot.app

internal enum class ShareToValuePilotInputIssue {
    EMPTY,
    TOO_LONG
}

internal data class ShareToValuePilotInputResult(
    val text: String?,
    val issue: ShareToValuePilotInputIssue? = null
) {
    init {
        require((text != null) != (issue != null))
    }

    val accepted: Boolean
        get() = text != null
}

/**
 * Bounds and trims text received from another app without interpreting it. A share is an input
 * convenience only; exact parsing and user confirmation remain in Compare Here.
 */
internal object ShareToValuePilotInput {
    const val MAX_CHARS: Int = CompareHereManualProductDraft.MAX_BLOCK_CHARS

    fun validate(rawText: String?): ShareToValuePilotInputResult {
        val text = rawText?.trim()
        if (text.isNullOrBlank()) {
            return ShareToValuePilotInputResult(
                text = null,
                issue = ShareToValuePilotInputIssue.EMPTY
            )
        }
        if (text.length > MAX_CHARS) {
            return ShareToValuePilotInputResult(
                text = null,
                issue = ShareToValuePilotInputIssue.TOO_LONG
            )
        }
        return ShareToValuePilotInputResult(text = text)
    }
}

internal enum class ShareToValuePilotStatus {
    READY,
    EMPTY,
    TOO_LARGE
}

internal data class ShareToValuePilotUiState(
    val status: ShareToValuePilotStatus,
    val sharedText: String?,
    val openComparisonEnabled: Boolean
) {
    init {
        require((status == ShareToValuePilotStatus.READY) == (sharedText != null))
        require(openComparisonEnabled == (status == ShareToValuePilotStatus.READY))
    }
}

internal object ShareToValuePilotUiProjector {
    fun project(rawText: String?): ShareToValuePilotUiState {
        val input = ShareToValuePilotInput.validate(rawText)
        return when (input.issue) {
            null ->
                ShareToValuePilotUiState(
                    status = ShareToValuePilotStatus.READY,
                    sharedText = requireNotNull(input.text),
                    openComparisonEnabled = true
                )

            ShareToValuePilotInputIssue.EMPTY ->
                ShareToValuePilotUiState(
                    status = ShareToValuePilotStatus.EMPTY,
                    sharedText = null,
                    openComparisonEnabled = false
                )

            ShareToValuePilotInputIssue.TOO_LONG ->
                ShareToValuePilotUiState(
                    status = ShareToValuePilotStatus.TOO_LARGE,
                    sharedText = null,
                    openComparisonEnabled = false
                )
        }
    }
}
