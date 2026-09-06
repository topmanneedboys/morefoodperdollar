package com.valuepilot.app

import android.content.ClipData
import android.content.Intent
import android.net.Uri

/**
 * Bounded extraction of one explicitly shared image reference from an Android SEND intent.
 *
 * Senders commonly provide [Intent.EXTRA_STREAM], but some provide only a single URI in
 * [Intent.getClipData]. Prefer the explicit extra when present and accept a ClipData fallback only
 * when it contains exactly one URI. Multi-item shares are rejected rather than silently dropping
 * pages or making an arbitrary choice. This helper carries no bytes, parsing, OCR, persistence,
 * evidence, ranking, or network authority; [ShareToValuePilotImageInput] validates the URI shape
 * before the existing local OCR route receives it.
 */
internal object ShareToValuePilotIntentInput {
    fun rawImageUri(intent: Intent?): String? {
        val extraUri =
            runCatching {
                when (val value = intent?.extras?.get(Intent.EXTRA_STREAM)) {
                    is Uri -> value.toString()
                    is String -> value
                    else -> null
                }
            }.getOrNull()
        val clipData = intent?.clipData
        return chooseSingleUri(
            extraUri = extraUri,
            clipItemCount = clipData?.itemCount ?: 0,
            clipUri = clipData?.let { data ->
                if (data.itemCount == 1) data.getItemAt(0).uri?.toString() else null
            }
        )
    }

    /** Pure selection rule kept separate so deterministic tests do not depend on Android stubs. */
    internal fun chooseSingleUri(
        extraUri: String?,
        clipItemCount: Int,
        clipUri: String?
    ): String? {
        if (!extraUri.isNullOrBlank()) return extraUri
        if (clipItemCount != 1) return null
        return clipUri?.takeIf(String::isNotBlank)
    }
}
