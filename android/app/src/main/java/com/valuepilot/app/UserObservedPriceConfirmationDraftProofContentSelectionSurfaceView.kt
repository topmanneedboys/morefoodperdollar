package com.valuepilot.app

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Replaceable physical surface for explicit foreground proof-content selection.
 *
 * It renders only immutable status and emits a select request. It never receives a URI or raw bytes
 * and owns no reader, draft/session, artifact identity, persistence, submission, evidence, clock,
 * identifiers, ranking, or networking authority.
 */
internal class UserObservedPriceConfirmationDraftProofContentSelectionSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr),
    UserObservedPriceConfirmationDraftProofContentSelectionObserver {

    private val status = TextView(context)
    private val selectButton = Button(context)

    var onSelectRequested: (() -> Unit)? = null
        set(value) {
            field = value
            selectButton.isEnabled = value != null
        }

    init {
        orientation = VERTICAL
        visibility = View.GONE
        isSaveEnabled = false

        addView(
            TextView(context).apply {
                text = "Attach proof content"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                setTextColor(Color.parseColor("#111827"))
                setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            }
        )
        addView(
            TextView(context).apply {
                text =
                    "Choose the actual receipt or price-tag image/PDF. The selected content stays " +
                        "temporary on this screen and is not evidence by itself."
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setTextColor(Color.parseColor("#6B7280"))
                setPadding(0, dp(5), 0, 0)
            }
        )

        status.apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(Color.parseColor("#6B7280"))
            setPadding(0, dp(8), 0, 0)
        }
        addView(status)

        selectButton.apply {
            text = "Choose proof image or PDF"
            setAllCaps(false)
            isSaveEnabled = false
            isEnabled = false
            setOnClickListener { onSelectRequested?.invoke() }
            layoutParams =
                LayoutParams(
                    LayoutParams.MATCH_PARENT,
                    LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(8) }
        }
        addView(selectButton)

        render(
            UserObservedPriceConfirmationDraftProofContentSelectionPresentation.Inactive
        )
    }

    override fun onPresentation(
        presentation: UserObservedPriceConfirmationDraftProofContentSelectionPresentation
    ) {
        render(presentation)
    }

    fun render(
        presentation: UserObservedPriceConfirmationDraftProofContentSelectionPresentation
    ) {
        when (presentation) {
            UserObservedPriceConfirmationDraftProofContentSelectionPresentation.Inactive -> {
                status.text = ""
                status.setTextColor(Color.parseColor("#6B7280"))
            }

            UserObservedPriceConfirmationDraftProofContentSelectionPresentation.AwaitingSelection -> {
                status.text = "No proof image or PDF selected yet."
                status.setTextColor(Color.parseColor("#6B7280"))
            }

            is UserObservedPriceConfirmationDraftProofContentSelectionPresentation.Ready -> {
                status.text =
                    "Proof content selected (${presentation.byteLength} bytes). " +
                        "It remains temporary until a later explicit confirmation step."
                status.setTextColor(Color.parseColor("#047857"))
            }

            is UserObservedPriceConfirmationDraftProofContentSelectionPresentation.Rejected -> {
                status.text = rejectionMessage(presentation.issue)
                status.setTextColor(Color.parseColor("#B42318"))
            }
        }
    }

    private fun rejectionMessage(issue: UserObservedPriceProofContentReadIssue): String =
        when (issue) {
            UserObservedPriceProofContentReadIssue.UNSUPPORTED_URI ->
                "That selection is not a supported document source."
            UserObservedPriceProofContentReadIssue.SOURCE_UNAVAILABLE ->
                "The selected proof could not be opened."
            UserObservedPriceProofContentReadIssue.EMPTY_CONTENT ->
                "The selected proof is empty."
            UserObservedPriceProofContentReadIssue.INPUT_TOO_LARGE ->
                "The selected proof is larger than 16 MiB."
            UserObservedPriceProofContentReadIssue.READ_FAILED ->
                "The selected proof could not be read safely."
        }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
