package com.valuepilot.app

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.InputFilter
import android.text.InputType
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView

/**
 * Replaceable physical adapter for the non-byte proof reference of one observed-price draft.
 *
 * The user must explicitly type an artifact reference and explicitly choose Receipt or Price tag.
 * No proof type or identifier is defaulted, inferred, generated, trimmed, fingerprinted, persisted,
 * or interpreted here. This surface deliberately does not pick/read a document or photo; raw proof
 * bytes remain a separate later foreground adapter and never enter the draft itself.
 *
 * Semantic artifact-ID validation remains downstream in [UserProvidedPriceProofArtifact]. This view
 * owns only basic unanswered-field feedback and emits the exact raw reference text plus the explicit
 * typed proof choice. It has no route/session, clock, storage, evidence, ranking, or networking
 * authority.
 */
class UserObservedPriceConfirmationDraftProofReferenceInputSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val artifactIdEditor = EditText(context)
    private val proofTypeGroup = RadioGroup(context)
    private val receiptOption = RadioButton(context)
    private val priceTagOption = RadioButton(context)
    private val proofTypeError = TextView(context)
    private val applyButton = Button(context)
    private val ownerBoundControls = mutableListOf<View>()

    var onCommit: ((String, UserProvidedPriceProofType) -> Unit)? = null
        set(value) {
            field = value
            ownerBoundControls.forEach { control ->
                control.isEnabled = value != null
            }
        }

    init {
        orientation = VERTICAL
        visibility = View.GONE
        isSaveEnabled = false

        addView(heading("Describe your proof"))
        addView(
            helperText(
                "Enter a reference label you choose for this proof and select what kind of proof it is. " +
                    "This step does not attach a photo or document yet."
            )
        )

        artifactIdEditor.apply {
            ownerBoundControls += this
            hint = "Proof reference, for example receipt-sept-1"
            isSingleLine = true
            isSaveEnabled = false
            isEnabled = false
            inputType = InputType.TYPE_CLASS_TEXT
            filters = arrayOf<InputFilter>(InputFilter.LengthFilter(160))
            layoutParams =
                LayoutParams(
                    LayoutParams.MATCH_PARENT,
                    LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(12) }
        }
        addView(artifactIdEditor)

        proofTypeGroup.apply {
            ownerBoundControls += this
            orientation = HORIZONTAL
            isSaveEnabled = false
            isEnabled = false
            layoutParams =
                LayoutParams(
                    LayoutParams.MATCH_PARENT,
                    LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(8) }
        }
        receiptOption.apply {
            ownerBoundControls += this
            id = View.generateViewId()
            text = "Receipt"
            isSaveEnabled = false
            isEnabled = false
        }
        priceTagOption.apply {
            ownerBoundControls += this
            id = View.generateViewId()
            text = "Price tag"
            isSaveEnabled = false
            isEnabled = false
        }
        proofTypeGroup.addView(receiptOption)
        proofTypeGroup.addView(priceTagOption)
        addView(proofTypeGroup)

        proofTypeError.apply {
            visibility = View.GONE
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(Color.parseColor("#B42318"))
            setPadding(0, dp(4), 0, 0)
        }
        addView(proofTypeError)

        addView(
            helperText(
                "The reference and type remain draft fields only. They do not prove a price or create evidence."
            )
        )

        applyButton.apply {
            ownerBoundControls += this
            text = "Set proof reference"
            setAllCaps(false)
            isSaveEnabled = false
            isEnabled = false
            setOnClickListener { commitInput() }
            layoutParams =
                LayoutParams(
                    LayoutParams.MATCH_PARENT,
                    LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(10) }
        }
        addView(applyButton)
    }

    fun clearInput() {
        artifactIdEditor.setText("")
        artifactIdEditor.error = null
        proofTypeGroup.clearCheck()
        proofTypeError.text = ""
        proofTypeError.visibility = View.GONE
    }

    private fun commitInput() {
        artifactIdEditor.error = null
        proofTypeError.text = ""
        proofTypeError.visibility = View.GONE

        val artifactId = artifactIdEditor.text.toString()
        if (artifactId.isBlank()) {
            artifactIdEditor.error = "Enter a proof reference."
            return
        }

        val proofType =
            when {
                receiptOption.isChecked -> UserProvidedPriceProofType.RECEIPT
                priceTagOption.isChecked -> UserProvidedPriceProofType.PRICE_TAG
                else -> null
            }
        if (proofType == null) {
            proofTypeError.text = "Choose Receipt or Price tag."
            proofTypeError.visibility = View.VISIBLE
            return
        }

        onCommit?.invoke(artifactId, proofType)
    }

    private fun heading(value: String): TextView =
        TextView(context).apply {
            text = value
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTextColor(Color.parseColor("#111827"))
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        }

    private fun helperText(value: String): TextView =
        TextView(context).apply {
            text = value
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(Color.parseColor("#6B7280"))
            setPadding(0, dp(5), 0, 0)
        }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
