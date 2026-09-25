package com.valuepilot.app

import java.util.Locale

internal enum class ShareToValuePilotImageInputIssue {
    EMPTY,
    TOO_LONG,
    UNSUPPORTED_SCHEME,
    CONTROL_CHARACTER
}

internal data class ShareToValuePilotImageInputResult(
    val uri: String?,
    val issue: ShareToValuePilotImageInputIssue? = null
) {
    init {
        require((uri != null) != (issue != null))
    }

    val accepted: Boolean
        get() = uri != null
}

/**
 * Bounds a user-intentionally shared image URI before it reaches the existing local OCR route.
 * Only content URIs are accepted because the sender must grant transient read access; file paths
 * and other schemes are never treated as an image source.
 */
internal object ShareToValuePilotImageInput {
    const val MAX_URI_CHARS: Int = 4_096

    fun validate(rawUri: String?): ShareToValuePilotImageInputResult {
        val uri = rawUri?.trim()
        if (uri.isNullOrBlank()) {
            return ShareToValuePilotImageInputResult(
                uri = null,
                issue = ShareToValuePilotImageInputIssue.EMPTY
            )
        }
        if (uri.length > MAX_URI_CHARS) {
            return ShareToValuePilotImageInputResult(
                uri = null,
                issue = ShareToValuePilotImageInputIssue.TOO_LONG
            )
        }
        if (uri.any { character -> character <= '\u001F' || character == '\u007F' }) {
            return ShareToValuePilotImageInputResult(
                uri = null,
                issue = ShareToValuePilotImageInputIssue.CONTROL_CHARACTER
            )
        }

        val scheme = uri.substringBefore(':', missingDelimiterValue = "")
        if (!scheme.equals("content", ignoreCase = true)) {
            return ShareToValuePilotImageInputResult(
                uri = null,
                issue = ShareToValuePilotImageInputIssue.UNSUPPORTED_SCHEME
            )
        }

        // This is intentionally only a shape check. ContentResolver remains the authority for
        // whether the sender's transient grant can actually be opened.
        val remainder = uri.substringAfter(':', missingDelimiterValue = "")
        if (!remainder.startsWith("//") || remainder.removePrefix("//").isBlank()) {
            return ShareToValuePilotImageInputResult(
                uri = null,
                issue = ShareToValuePilotImageInputIssue.UNSUPPORTED_SCHEME
            )
        }

        return ShareToValuePilotImageInputResult(uri = uri)
    }

    fun schemeOf(uri: String): String =
        uri.substringBefore(':', missingDelimiterValue = "").lowercase(Locale.ROOT)
}
