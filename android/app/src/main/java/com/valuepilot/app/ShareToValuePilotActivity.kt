package com.valuepilot.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * Explicit Android share-target review surface.
 *
 * The activity accepts only bounded text intentionally shared by another app. It displays that
 * text as untrusted input and forwards it to the existing Compare Here editor only after the
 * shopper taps the action. It performs no parsing, network access, product matching, ranking,
 * persistence, or evidence promotion.
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
        uiState = ShareToValuePilotUiProjector.project(rawText)
        render(uiState)

        openComparisonButton.setOnClickListener {
            val sharedText = uiState.sharedText ?: return@setOnClickListener
            startActivity(
                Intent(this, ComparisonActivity::class.java).putExtra(
                    ComparisonActivity.EXTRA_SHARED_TEXT,
                    sharedText
                )
            )
            finish()
        }
        findViewById<Button>(R.id.shareToValuePilotCancel).setOnClickListener {
            finish()
        }
    }

    private fun render(state: ShareToValuePilotUiState) {
        when (state.status) {
            ShareToValuePilotStatus.READY -> {
                title.setText(R.string.share_to_valuepilot_ready_title)
                guidance.setText(R.string.share_to_valuepilot_ready_guidance)
                previewLabel.visibility = View.VISIBLE
                preview.visibility = View.VISIBLE
                preview.text = requireNotNull(state.sharedText)
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
        }

        openComparisonButton.setText(R.string.share_to_valuepilot_open_comparison)
        openComparisonButton.isEnabled = state.openComparisonEnabled
    }
}
