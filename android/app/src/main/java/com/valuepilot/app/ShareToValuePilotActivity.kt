package com.valuepilot.app

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * Explicit Android share-target review surface.
 *
 * The activity accepts only bounded text or a content URI intentionally shared by another app.
 * Text remains visible as untrusted input; an image is handed to the existing on-device OCR
 * review path only after the shopper taps the action. It performs no parsing, network access,
 * product matching, ranking, persistence, or evidence promotion.
 */
class ShareToValuePilotActivity : AppCompatActivity() {
    private lateinit var title: TextView
    private lateinit var guidance: TextView
    private lateinit var previewLabel: TextView
    private lateinit var preview: TextView
    private lateinit var openComparisonButton: Button

    private var uiState = ShareToValuePilotUiState(
        status = ShareToValuePilotStatus.EMPTY,
        sharedText = null,
        openComparisonEnabled = false
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_share_to_valuepilot)

        title = findViewById(R.id.shareToValuePilotTitle)
        guidance = findViewById(R.id.shareToValuePilotGuidance)
        previewLabel = findViewById(R.id.shareToValuePilotPreviewLabel)
        preview = findViewById(R.id.shareToValuePilotPreview)
        openComparisonButton = findViewById(R.id.shareToValuePilotOpenComparison)

        val rawText =
            runCatching {
                intent?.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
            }.getOrNull()
        val rawImageUri =
            runCatching {
                when (val value = intent?.extras?.get(Intent.EXTRA_STREAM)) {
                    is Uri -> value.toString()
                    is String -> value
                    else -> null
                }
            }.getOrNull()
        uiState =
            if (rawImageUri != null) {
                ShareToValuePilotUiProjector.projectImage(rawImageUri)
            } else {
                ShareToValuePilotUiProjector.project(rawText)
            }
        render(uiState)

        openComparisonButton.setOnClickListener {
            val handoff = Intent(this, ComparisonActivity::class.java)
            when {
                uiState.sharedText != null ->
                    handoff.putExtra(
                        ComparisonActivity.EXTRA_SHARED_TEXT,
                        uiState.sharedText
                    )

                uiState.sharedImageUri != null -> {
                    val uri = runCatching { Uri.parse(uiState.sharedImageUri) }.getOrNull()
                        ?: return@setOnClickListener
                    handoff.putExtra(
                        ComparisonActivity.EXTRA_SHARED_IMAGE_URI,
                        uiState.sharedImageUri
                    )
                    handoff.data = uri
                    handoff.clipData = ClipData.newRawUri("ValuePilot shared image", uri)
                    handoff.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                else -> return@setOnClickListener
            }
            startActivity(handoff)
            finish()
        }
        findViewById<Button>(R.id.shareToValuePilotCancel).setOnClickListener {
            finish()
        }
    }

    private fun render(state: ShareToValuePilotUiState) {
        when (state.status) {
            ShareToValuePilotStatus.READY -> {
                if (state.sharedImageUri != null) {
                    title.setText(R.string.share_to_valuepilot_image_ready_title)
                    guidance.setText(R.string.share_to_valuepilot_image_ready_guidance)
                    previewLabel.setText(R.string.share_to_valuepilot_image_preview_label)
                    preview.text = getString(R.string.share_to_valuepilot_image_preview)
                } else {
                    title.setText(R.string.share_to_valuepilot_ready_title)
                    guidance.setText(R.string.share_to_valuepilot_ready_guidance)
                    previewLabel.setText(R.string.share_to_valuepilot_preview_label)
                    preview.text = requireNotNull(state.sharedText)
                }
                previewLabel.visibility = View.VISIBLE
                preview.visibility = View.VISIBLE
            }

            ShareToValuePilotStatus.EMPTY -> {
                title.setText(R.string.share_to_valuepilot_empty_title)
                guidance.setText(R.string.share_to_valuepilot_empty_guidance)
                previewLabel.visibility = View.GONE
                preview.visibility = View.GONE
                preview.text = ""
            }

            ShareToValuePilotStatus.TOO_LARGE -> {
                title.setText(R.string.share_to_valuepilot_too_large_title)
                guidance.text = getString(
                    R.string.share_to_valuepilot_too_large_guidance,
                    ShareToValuePilotInput.MAX_CHARS
                )
                previewLabel.visibility = View.GONE
                preview.visibility = View.GONE
                preview.text = ""
            }

            ShareToValuePilotStatus.UNSUPPORTED_IMAGE -> {
                title.setText(R.string.share_to_valuepilot_image_unsupported_title)
                guidance.setText(R.string.share_to_valuepilot_image_unsupported_guidance)
                previewLabel.visibility = View.GONE
                preview.visibility = View.GONE
                preview.text = ""
            }
        }

        openComparisonButton.setText(R.string.share_to_valuepilot_open_comparison)
        openComparisonButton.isEnabled = state.openComparisonEnabled
    }
}
