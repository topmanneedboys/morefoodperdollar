package com.valuepilot.app

import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

/**
 * Foreground Android system-picker adapter for one user-selected observed-price proof.
 *
 * The picker accepts image or PDF documents, requests no persistable URI permission, and hands the
 * selected content URI immediately to the existing bounded read-only content source. Cancellation
 * is a no-op. The resulting transient bytes are passed only as a typed read result to the caller.
 *
 * This adapter owns no draft/session state, artifact identity/type, fingerprinting, local storage,
 * identifiers, timestamps, submission, evidence, ranking, OCR, camera capture, or network access.
 */
internal class AndroidUserObservedPriceProofContentPicker(
    activity: AppCompatActivity,
    contentSource: AndroidUserObservedPriceProofContentSource,
    private val onReadResult: (UserObservedPriceProofContentReadResult) -> Unit
) : AutoCloseable {

    private var closed = false

    private val launcher: ActivityResultLauncher<Array<String>> =
        activity.registerForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri ->
            if (closed || uri == null) return@registerForActivityResult
            onReadResult(contentSource.read(uri))
        }

    fun launch() {
        if (closed) return
        launcher.launch(arrayOf("image/*", "application/pdf"))
    }

    override fun close() {
        if (closed) return
        closed = true
        launcher.unregister()
    }
}
